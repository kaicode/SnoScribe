package org.snomed.snoscribe.model;

import java.util.List;

public class LlmProcessResult {

	private final List<Annotation> annotations;
	private final double llmSeconds;

	public LlmProcessResult(List<Annotation> annotations, double llmSeconds) {
		this.annotations = annotations;
		this.llmSeconds = llmSeconds;
	}

	public List<Annotation> getAnnotations() {
		return annotations;
	}

	public double getLlmSeconds() {
		return llmSeconds;
	}
}
