package org.snomed.snoscribe.model;

import java.util.List;

public class AnnotateResponse {

	private List<Annotation> annotations;
	private double totalSeconds;
	private double llmSeconds;

	public AnnotateResponse() {}

	public AnnotateResponse(List<Annotation> annotations, double totalSeconds, double llmSeconds) {
		this.annotations = annotations;
		this.totalSeconds = totalSeconds;
		this.llmSeconds = llmSeconds;
	}

	public List<Annotation> getAnnotations() {
		return annotations;
	}

	public void setAnnotations(List<Annotation> annotations) {
		this.annotations = annotations;
	}

	public double getTotalSeconds() {
		return totalSeconds;
	}

	public void setTotalSeconds(double totalSeconds) {
		this.totalSeconds = totalSeconds;
	}

	public double getLlmSeconds() {
		return llmSeconds;
	}

	public void setLlmSeconds(double llmSeconds) {
		this.llmSeconds = llmSeconds;
	}
}
