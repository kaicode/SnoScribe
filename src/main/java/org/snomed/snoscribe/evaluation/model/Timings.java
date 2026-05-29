package org.snomed.snoscribe.evaluation.model;

public class Timings {

	private double totalSeconds;
	private double llmSeconds;
	/** Wall-clock duration of the parallel terminology enrichment phase. */
	private double enrichmentWallSeconds;
	/** Summed duration of FHIR terminology HTTP calls ($expand, ConceptMap $translate). */
	private double fhirSeconds;
	/** Summed duration of Infinity /rerank HTTP calls. */
	private double rerankSeconds;

	public Timings() {}

	public Timings(double totalSeconds, double llmSeconds, double enrichmentWallSeconds,
			double fhirSeconds, double rerankSeconds) {
		this.totalSeconds = totalSeconds;
		this.llmSeconds = llmSeconds;
		this.enrichmentWallSeconds = enrichmentWallSeconds;
		this.fhirSeconds = fhirSeconds;
		this.rerankSeconds = rerankSeconds;
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

	public double getEnrichmentWallSeconds() {
		return enrichmentWallSeconds;
	}

	public void setEnrichmentWallSeconds(double enrichmentWallSeconds) {
		this.enrichmentWallSeconds = enrichmentWallSeconds;
	}

	public double getFhirSeconds() {
		return fhirSeconds;
	}

	public void setFhirSeconds(double fhirSeconds) {
		this.fhirSeconds = fhirSeconds;
	}

	public double getRerankSeconds() {
		return rerankSeconds;
	}

	public void setRerankSeconds(double rerankSeconds) {
		this.rerankSeconds = rerankSeconds;
	}
}
