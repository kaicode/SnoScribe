package org.snomed.annotate.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the {@code evaluate} Spring profile.
 * Supply model names via the {@code eval.models} property (comma-separated).
 */
@Component
@Profile("evaluate")
public class EvaluationConfig {

	/** Comma-separated list of Ollama model names to benchmark. */
	@Value("${eval.models}")
	private String modelsRaw;

	/** Directory containing example note .txt files (relative to working directory). */
	@Value("${eval.notesDir:example_notes}")
	private String notesDir;

	/** Root directory for benchmark output sub-folders. */
	@Value("${eval.outputDir:model-comparison}")
	private String outputDir;

	/** Directory containing human-expert annotation JSON files for Stage 2 ranking. */
	@Value("${eval.expertDir:human-expert}")
	private String expertDir;

	public List<String> getModels() {
		return Arrays.stream(modelsRaw.split(","))
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.toList();
	}

	public String getNotesDir() {
		return notesDir;
	}

	public String getOutputDir() {
		return outputDir;
	}

	public String getExpertDir() {
		return expertDir;
	}
}
