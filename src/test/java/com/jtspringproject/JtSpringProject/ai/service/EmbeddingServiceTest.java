package com.jtspringproject.JtSpringProject.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.jtspringproject.JtSpringProject.models.Category;
import com.jtspringproject.JtSpringProject.models.Product;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

	@Mock
	private EmbeddingModel embeddingModel;

	@Mock
	private EmbeddingModel queryEmbeddingModel;

	@Mock
	private JdbcTemplate jdbc;

	private EmbeddingService embeddingService;

	@BeforeEach
	void setUp() {
		embeddingService = new EmbeddingService(embeddingModel, queryEmbeddingModel, jdbc);
	}

	private static Embedding vector() {
		return Embedding.from(new float[768]);
	}

	private static List<EmbeddingService.Row> rows(int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(i -> new EmbeddingService.Row(i, "Product: item " + i))
				.toList();
	}

	@Test
	void embedMissing_batchesInsteadOfOneCallPerProduct() {
		// 250 products must become 3 batched calls, not 250 individual ones -
		// this is the difference between a reindex taking minutes and hours.
		when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(rows(250));
		when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
			List<?> segments = invocation.getArgument(0);
			return Response.from(segments.stream().map(s -> vector()).toList());
		});

		int embedded = embeddingService.embedMissing();

		assertEquals(250, embedded);
		verify(embeddingModel, times(3)).embedAll(anyList());
		verify(embeddingModel, never()).embed(any(TextSegment.class));
	}

	@Test
	void embedMissing_survivesAFailedBatch() {
		when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(rows(150));
		when(embeddingModel.embedAll(anyList()))
				.thenThrow(new RuntimeException("quota exceeded"))
				.thenAnswer(invocation -> {
					List<?> segments = invocation.getArgument(0);
					return Response.from(segments.stream().map(s -> vector()).toList());
				});

		// The second batch still has to be attempted and counted: a resumable
		// reindex must not abandon everything because one batch was throttled.
		int embedded = embeddingService.embedMissing();

		assertEquals(50, embedded);
		verify(embeddingModel, times(2)).embedAll(anyList());
	}

	@Test
	void embedQuery_usesTheQuerySideModel() {
		// Queries and documents are embedded with different Gemini task types;
		// using the document model for a query costs retrieval quality.
		when(queryEmbeddingModel.embed("organic fruit")).thenReturn(Response.from(vector()));

		float[] result = embeddingService.embedQuery("organic fruit");

		assertEquals(768, result.length);
		verify(queryEmbeddingModel).embed("organic fruit");
		verify(embeddingModel, never()).embed(anyString());
	}

	@Test
	void embedProduct_writesVectorOntoTheProductRow() {
		Category category = new Category();
		category.setName("Fruits");
		Product product = new Product();
		product.setId(7);
		product.setName("Fresh Apple");
		product.setDescription("Juicy red apple");
		product.setBrand("Orchard Co");
		product.setPrice(new BigDecimal("3.00"));
		product.setQuantity(50);
		product.setCategory(category);

		when(embeddingModel.embed(any(TextSegment.class))).thenReturn(Response.from(vector()));

		embeddingService.embedProduct(product);

		ArgumentCaptor<TextSegment> segment = ArgumentCaptor.forClass(TextSegment.class);
		verify(embeddingModel).embed(segment.capture());
		String text = segment.getValue().text();
		assertTrue(text.contains("Fresh Apple"), "name belongs in the embedded text");
		assertTrue(text.contains("Orchard Co"), "brand is what shoppers search by");
		assertTrue(text.contains("Fruits"), "category gives the vector its neighbourhood");

		verify(jdbc).update(anyString(), anyString(), eq(7));
	}
}
