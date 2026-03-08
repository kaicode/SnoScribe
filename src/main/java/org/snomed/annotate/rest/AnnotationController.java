package org.snomed.annotate.rest;

import org.snomed.annotate.exception.ServiceException;
import org.snomed.annotate.model.Annotation;
import org.snomed.annotate.service.OllamaProcessorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AnnotationController {

	private final OllamaProcessorService ollamaProcessorService;

	public AnnotationController(OllamaProcessorService ollamaProcessorService) {
		this.ollamaProcessorService = ollamaProcessorService;
	}

	@PostMapping("/annotate")
	public List<Annotation> processDocument(@RequestBody DocumentRequest request) throws ServiceException {
		return ollamaProcessorService.processDocument(request.getDocument());
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
