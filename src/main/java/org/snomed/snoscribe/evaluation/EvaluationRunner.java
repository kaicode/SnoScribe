package org.snomed.snoscribe.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.evaluation.model.BenchmarkResult;
import org.snomed.snoscribe.evaluation.model.Timings;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.service.LlmProcessorService;
import org.snomed.snoscribe.service.SnomedTerminologyService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates Stage 1 benchmarking: runs each model against all example notes,
 * records timing breakdowns, and writes per-note JSON output files.
 *
 * After Stage 1 completes, triggers Stage 2 (ranking) if a human-expert directory exists.
 *
 * Run with:
 * {@code mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=evaluate --eval.models=qwen3.5:9b,qwen2.5:3b-instruct"}
 */
@Component
@Profile("evaluate")
public class EvaluationRunner implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(EvaluationRunner.class);

	private final EvaluationConfig config;
	private final LlmProcessorService llmProcessorService;
	private final SnomedTerminologyService snomedTerminologyService;
	private final ObjectMapper objectMapper;

	public EvaluationRunner(EvaluationConfig config,
			LlmProcessorService llmProcessorService,
			SnomedTerminologyService snomedTerminologyService,
			ObjectMapper objectMapper) {
		this.config = config;
		this.llmProcessorService = llmProcessorService;
		this.snomedTerminologyService = snomedTerminologyService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<String> models = config.getModels();
		List<Path> noteFiles = loadNoteFiles();

		System.out.printf("%n=== Evaluation Stage 1: Benchmarking ===%n");
		System.out.printf("Models : %s%n", models);
		System.out.printf("Notes  : %d files in '%s'%n", noteFiles.size(), config.getNotesDir());
		System.out.printf("Output : %s%n%n", config.getOutputDir());

		ObjectMapper prettyMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);

		for (String model : models) {
			String sanitisedModel = model.replace(":", "_");
			Path modelOutputDir = Paths.get(config.getOutputDir(), sanitisedModel);
			Files.createDirectories(modelOutputDir);

			System.out.printf("--- Model: %s ---%n", model);

			for (Path noteFile : noteFiles) {
				String noteFileName = noteFile.getFileName().toString();
				String document = Files.readString(noteFile);

				System.out.printf("  Processing %s ... ", noteFileName);
				System.out.flush();

				long totalStart = System.currentTimeMillis();
				BenchmarkResult result;

				try {
					// LLM call
					List<Annotation> annotations = llmProcessorService.processDocument(document, model);
					long afterLlm = System.currentTimeMillis();

					// Parallel FHIR enrichment
					List<CompletableFuture<Void>> futures = annotations.stream()
							.map(ann -> CompletableFuture.runAsync(
									() -> snomedTerminologyService.enrichAnnotation(ann)))
							.collect(Collectors.toList());
					CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
					long afterFhir = System.currentTimeMillis();

					double totalSeconds = round1dp((afterFhir - totalStart) / 1000.0);
					double llmSeconds   = round1dp((afterLlm   - totalStart) / 1000.0);
					double fhirSeconds  = round1dp((afterFhir  - afterLlm)   / 1000.0);

					result = new BenchmarkResult(model, noteFileName,
							new Timings(totalSeconds, llmSeconds, fhirSeconds), annotations);

					System.out.printf("done (%ss total, %ss LLM, %ss FHIR, %d annotations)%n",
							totalSeconds, llmSeconds, fhirSeconds, annotations.size());
				} catch (Exception e) {
					long elapsed = System.currentTimeMillis() - totalStart;
					double totalSeconds = round1dp(elapsed / 1000.0);
					String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
					result = new BenchmarkResult(model, noteFileName,
							new Timings(totalSeconds, 0.0, 0.0), errorMsg);
					System.out.printf("ERROR (%ss): %s%n", totalSeconds, errorMsg);
				}

				// Write result (success or error) to output file
				String outputFileName = noteFileName.replaceFirst("\\.txt$", ".json");
				File outputFile = modelOutputDir.resolve(outputFileName).toFile();
				prettyMapper.writeValue(outputFile, result);
			}
			System.out.println();
		}

		// Stage 2: ranking (only if human-expert directory exists)
		Path expertDir = Paths.get(config.getExpertDir());
		if (Files.isDirectory(expertDir)) {
			System.out.println("=== Evaluation Stage 2: Ranking ===");
			RankingService rankingService = new RankingService(
					prettyMapper, config.getOutputDir(), config.getExpertDir());
			rankingService.rank(models, noteFiles);
		} else {
			System.out.printf("Skipping Stage 2 (no human-expert directory at '%s').%n", expertDir);
			System.out.println("Create human-expert/<noteFile>.json files to enable ranking.");
		}

		System.out.println("\nEvaluation complete.");
		System.exit(0);
	}

	/**
	 * Loads and sorts note files from the configured notes directory.
	 * Sorts numerically by filename stem (1 < 2 < 10, not lexicographic).
	 */
	private List<Path> loadNoteFiles() throws Exception {
		Path notesDir = Paths.get(config.getNotesDir());
		if (!Files.isDirectory(notesDir)) {
			throw new IllegalStateException("Notes directory not found: " + notesDir.toAbsolutePath());
		}

		List<Path> files = new ArrayList<>();
		try (var stream = Files.list(notesDir)) {
			stream.filter(p -> p.toString().endsWith(".txt"))
				  .forEach(files::add);
		}

		files.sort(Comparator.comparingInt(p -> {
			String stem = p.getFileName().toString().replaceFirst("\\.txt$", "");
			try {
				return Integer.parseInt(stem);
			} catch (NumberFormatException e) {
				return Integer.MAX_VALUE; // non-numeric names sort last
			}
		}));

		return files;
	}

	private static double round1dp(double value) {
		return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}
}
