package com.jtspringproject.JtSpringProject.controller.api;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.ai.service.EmbeddingJobService;
import com.jtspringproject.JtSpringProject.catalogue.CatalogueImportService;
import com.jtspringproject.JtSpringProject.catalogue.SuggestionService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Catalogue operations for administrators: bulk import and the embedding job.
 *
 * <p>Import takes the CSV as the raw request body rather than a multipart form,
 * so it streams straight into the database and works the same from curl and
 * from the admin page:
 *
 * <pre>
 * curl -X POST http://host/api/admin/catalogue/import?embed=true \
 *      -H "Authorization: Bearer $TOKEN" --data-binary @catalogue.csv.gz
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/catalogue")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogueApiController {

	private final CatalogueImportService importService;
	private final EmbeddingJobService embeddingJobService;
	private final SuggestionService suggestionService;

	public AdminCatalogueApiController(CatalogueImportService importService,
			EmbeddingJobService embeddingJobService, SuggestionService suggestionService) {
		this.importService = importService;
		this.embeddingJobService = embeddingJobService;
		this.suggestionService = suggestionService;
	}

	/**
	 * @param embed start embedding the new products as soon as the import commits
	 */
	@PostMapping("/import")
	public ResponseEntity<ApiResponse<CatalogueImportService.ImportResult>> importCatalogue(
			HttpServletRequest request, @RequestParam(defaultValue = "false") boolean embed) throws IOException {
		CatalogueImportService.ImportResult result = importService.importCsv(request.getInputStream());
		if (embed && result.awaitingEmbedding() > 0) {
			embeddingJobService.start(false);
		}
		return ResponseEntity.ok(ApiResponse.success(
				"Imported " + (result.inserted() + result.updated()) + " products", result));
	}

	@GetMapping("/embeddings")
	public ResponseEntity<ApiResponse<EmbeddingJobService.Status>> embeddingStatus() {
		return ResponseEntity.ok(ApiResponse.success(embeddingJobService.status()));
	}

	@PostMapping("/embeddings")
	public ResponseEntity<ApiResponse<EmbeddingJobService.Status>> startEmbedding(
			@RequestParam(defaultValue = "false") boolean full) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success("Embedding started", embeddingJobService.start(full)));
	}

	@DeleteMapping("/embeddings")
	public ResponseEntity<ApiResponse<EmbeddingJobService.Status>> stopEmbedding() {
		return ResponseEntity.ok(ApiResponse.success("Stopping after the current batch",
				embeddingJobService.stop()));
	}

	@PostMapping("/suggestions/refresh")
	public ResponseEntity<ApiResponse<Void>> refreshSuggestions() {
		suggestionService.refresh();
		return ResponseEntity.ok(ApiResponse.success("Search suggestions refreshed", null));
	}
}
