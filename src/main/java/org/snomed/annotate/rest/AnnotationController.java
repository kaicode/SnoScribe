package org.snomed.annotate.rest;

import org.snomed.annotate.exception.ServiceException;
import org.snomed.annotate.model.Annotation;
import org.snomed.annotate.service.OllamaProcessorService;
import org.snomed.annotate.service.SnomedTerminologyService;
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

	private final OllamaProcessorService ollamaProcessorService;
	private final SnomedTerminologyService snomedTerminologyService;

	public AnnotationController(OllamaProcessorService ollamaProcessorService,
			SnomedTerminologyService snomedTerminologyService) {
		this.ollamaProcessorService = ollamaProcessorService;
		this.snomedTerminologyService = snomedTerminologyService;
	}

	@PostMapping("/annotate")
	public List<Annotation> processDocument(@RequestBody DocumentRequest request) throws ServiceException {
		List<Annotation> annotations = ollamaProcessorService.processDocument(request.getDocument());

		// Enrich all annotations with SNOMED CT concepts in parallel
		List<CompletableFuture<Void>> futures = annotations.stream()
				.map(ann -> CompletableFuture.runAsync(() -> snomedTerminologyService.enrichAnnotation(ann)))
				.collect(Collectors.toList());
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
