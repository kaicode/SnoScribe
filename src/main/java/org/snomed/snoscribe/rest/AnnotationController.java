package org.snomed.snoscribe.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.exception.ServiceException;
import org.snomed.snoscribe.model.AnnotateResponse;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.model.LlmProcessResult;
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

	private static final Logger logger = LoggerFactory.getLogger(AnnotationController.class);

	private final LlmProcessorService llmProcessorService;
	private final SnomedTerminologyService snomedTerminologyService;

	public AnnotationController(LlmProcessorService llmProcessorService,
			SnomedTerminologyService snomedTerminologyService) {
		this.llmProcessorService = llmProcessorService;
		this.snomedTerminologyService = snomedTerminologyService;
	}

	@PostMapping("/annotate")
	public AnnotateResponse processDocument(@RequestBody DocumentRequest request) throws ServiceException {
		long totalStart = System.currentTimeMillis();
		LlmProcessResult llmResult = llmProcessorService.processDocument(request.getDocument());
		List<Annotation> annotations = llmResult.getAnnotations();
		logger.info("LLM returned {} annotations in {}s",
				annotations.size(), round2dp(llmResult.getLlmSeconds()));

		// Enrich all annotations with SNOMED CT concepts in parallel
		List<CompletableFuture<Void>> futures = annotations.stream()
				.map(ann -> CompletableFuture.runAsync(() -> snomedTerminologyService.enrichAnnotation(ann)))
				.toList();
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		double totalSeconds = round1dp((System.currentTimeMillis() - totalStart) / 1000.0);
		return new AnnotateResponse(annotations, totalSeconds, llmResult.getLlmSeconds());
	}

	private static double round1dp(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static double round2dp(double value) {
		return Math.round(value * 100.0) / 100.0;
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
