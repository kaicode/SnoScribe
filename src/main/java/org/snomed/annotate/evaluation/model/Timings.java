package org.snomed.annotate.evaluation.model;

public class Timings {

	private double totalSeconds;
	private double llmSeconds;
	private double fhirSeconds;

	public Timings() {}

	public Timings(double totalSeconds, double llmSeconds, double fhirSeconds) {
		this.totalSeconds = totalSeconds;
		this.llmSeconds = llmSeconds;
		this.fhirSeconds = fhirSeconds;
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

	public double getFhirSeconds() {
		return fhirSeconds;
	}

	public void setFhirSeconds(double fhirSeconds) {
		this.fhirSeconds = fhirSeconds;
	}
}
