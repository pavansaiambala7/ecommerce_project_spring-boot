package com.jtspringproject.JtSpringProject.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

class FallbackChatModelTest {

	private static final RuntimeException RETIRED = new RuntimeException(
			"NOT_FOUND (code 404) This model models/gemini-2.0-flash is no longer available.");

	private final ChatLanguageModel primary = mock(ChatLanguageModel.class);
	private final ChatLanguageModel secondary = mock(ChatLanguageModel.class);

	private final FallbackChatModel model = new FallbackChatModel(List.of(
			new FallbackChatModel.Candidate("primary", primary),
			new FallbackChatModel.Candidate("secondary", secondary)));

	@Test
	void shouldUseTheFallbackWhenThePrimaryModelIsRetired() {
		when(primary.generate(anyList())).thenThrow(RETIRED);
		when(secondary.generate(anyList())).thenReturn(Response.from(AiMessage.from("From the fallback")));

		String text = model.generate(List.of(UserMessage.from("hi"))).content().text();

		assertEquals("From the fallback", text);
	}

	@Test
	void shouldStopCallingARetiredModelForAWhile() {
		when(primary.generate(anyList())).thenThrow(RETIRED);
		when(secondary.generate(anyList())).thenReturn(Response.from(AiMessage.from("ok")));

		model.generate(List.of(UserMessage.from("one")));
		model.generate(List.of(UserMessage.from("two")));
		model.generate(List.of(UserMessage.from("three")));

		// Only the first message paid for discovering the primary was gone.
		verify(primary, times(1)).generate(anyList());
		verify(secondary, times(3)).generate(anyList());
	}

	/** Thinking models can exhaust their budget and return nothing visible. */
	@Test
	void shouldTreatAnEmptyReplyAsAFailure() {
		when(primary.generate(anyList())).thenReturn(Response.from(AiMessage.from("")));
		when(secondary.generate(anyList())).thenReturn(Response.from(AiMessage.from("A real answer")));

		assertEquals("A real answer", model.generate(List.of(UserMessage.from("hi"))).content().text());
	}

	@Test
	void shouldNotTouchTheFallbackWhenThePrimaryAnswers() {
		when(primary.generate(anyList())).thenReturn(Response.from(AiMessage.from("primary")));

		model.generate(List.of(UserMessage.from("hi")));

		verify(secondary, never()).generate(anyList());
	}

	@Test
	void shouldReportTheLastFailureWhenEveryModelFails() {
		when(primary.generate(anyList())).thenThrow(RETIRED);
		when(secondary.generate(anyList())).thenThrow(new RuntimeException("503 UNAVAILABLE high demand"));

		RuntimeException failure = assertThrows(RuntimeException.class,
				() -> model.generate(List.of(UserMessage.from("hi"))));
		assertEquals("503 UNAVAILABLE high demand", failure.getMessage());
	}

	@Test
	void shouldCoolDownLongerForRetirementThanForOverload() {
		assertEquals(FallbackChatModel.RETIRED_COOLDOWN, FallbackChatModel.cooldownFor(RETIRED));
		assertEquals(FallbackChatModel.OVERLOADED_COOLDOWN,
				FallbackChatModel.cooldownFor(new RuntimeException("UNAVAILABLE (code 503) high demand")));
		assertEquals(null, FallbackChatModel.cooldownFor(new RuntimeException("socket closed")));
	}
}
