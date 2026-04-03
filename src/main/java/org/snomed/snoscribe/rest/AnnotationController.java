package org.snomed.snoscribe.rest;

import org.snomed.snoscribe.exception.ServiceException;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.service.LlmProcessorService;
import org.snomed.snoscribe.service.SnomedTerminologyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AnnotationController {

	private final LlmProcessorService llmProcessorService;
	private final SnomedTerminologyService snomedTerminologyService;

	public AnnotationController(LlmProcessorService llmProcessorService,
			SnomedTerminologyService snomedTerminologyService) {
		this.llmProcessorService = llmProcessorService;
		this.snomedTerminologyService = snomedTerminologyService;
	}

	@PostMapping("/annotate")
	public List<Annotation> processDocument(@RequestBody DocumentRequest request) throws ServiceException {
		List<Annotation> annotations = llmProcessorService.processDocument(request.getDocument());

		// Enrich all annotations with SNOMED CT concepts in parallel
		List<CompletableFuture<Void>> futures = annotations.stream()
				.map(ann -> CompletableFuture.runAsync(() -> snomedTerminologyService.enrichAnnotation(ann)))
				.toList();
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		return annotations;
	}

	// Inner class for request body
	public static class DocumentRequest {

		private String document;

		public String getDocument() {
			return document;
		}

		public void setDocument(String document) {
			this.document = document;
		}
	}
}
