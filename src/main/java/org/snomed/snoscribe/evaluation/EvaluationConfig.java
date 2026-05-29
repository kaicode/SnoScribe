package org.snomed.snoscribe.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the {@code evaluate} Spring profile.
 * Model selection: use {@code spring.profiles.active=evaluate,&lt;profile&gt;} with
 * {@code llm.*.model} in that profile, or optionally override with comma-separated
 * {@code eval.models}. Use {@code eval.notesSubdir} to run on a subfolder of {@code eval.notesDir}
 * (e.g. {@code example_notes/llm_chain_project}).
 * Benchmark JSON and {@code ranking.json} are written under {@code eval.outputDir} plus that relative
 * notes path (e.g. {@code model-comparison/llm_chain_project/gemma4_e4b/}).
 */
@Component
@Profile("evaluate")
public class EvaluationConfig {

	/** Optional comma-separated model names; when blank, the active LLM profile's default model is used. */
	@Value("${eval.models:}")
	private String modelsRaw;

	/** Base directory for note .txt files (relative to the working directory). */
	@Value("${eval.notesDir:example_notes}")
	private String notesDir;

	/**
	 * Optional last path segment(s) appended to {@link #notesDir}, e.g. {@code llm_chain_project}
	 * for files under {@code example_notes/llm_chain_project}. You can instead set {@code eval.notesDir}
	 * to the full directory (such as {@code example_notes/llm_chain_project}) and leave this blank.
	 */
	@Value("${eval.notesSubdir:}")
	private String notesSubdir;

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
		String base = notesDir;
		if (notesSubdir == null || notesSubdir.isBlank()) {
			return base;
		}
		return Paths.get(base, notesSubdir.trim()).normalize().toString();
	}

	public String getOutputDir() {
		return outputDir;
	}

	/**
	 * Directory root for benchmark output: {@code eval.outputDir} with the same relative path that
	 * distinguishes the notes folder (see {@link #getNotesDir()}), so runs on different note sets do not clash.
	 * When notes are the configured base only (e.g. {@code example_notes} with no subfolder), this equals
	 * {@link #getOutputDir()}.
	 */
	public String getBenchmarkOutputDir() {
		Path extra = notesOutputRelativePath();
		if (extra == null) {
			return outputDir;
		}
		return Paths.get(outputDir).resolve(extra).normalize().toString();
	}

	/**
	 * Path from {@link #notesDir} property to the effective notes directory, or a single segment when the
	 * property already points at a nested folder.
	 */
	private Path notesOutputRelativePath() {
		Path base = Paths.get(notesDir).normalize();
		Path resolved = Paths.get(getNotesDir()).normalize();
		Path rel = base.relativize(resolved);
		if (rel.getNameCount() > 0 && !rel.isAbsolute()) {
			return rel;
		}
		if (resolved.getNameCount() >= 2) {
			return resolved.getFileName();
		}
		return null;
	}

	public String getExpertDir() {
		return expertDir;
	}
}
