package org.snomed.snoscribe.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.evaluation.model.BenchmarkResult;
import org.snomed.snoscribe.evaluation.model.Timings;
import org.snomed.snoscribe.config.LlmConfig;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.service.LlmProcessorService;
import org.snomed.snoscribe.service.SnomedTerminologyService;
import org.snomed.snoscribe.service.TerminologyTimingRecorder;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates Stage 1 benchmarking: runs each model against all example notes,
 * records timing breakdowns, and writes per-note JSON output files.
 *
 * After Stage 1 completes, triggers Stage 2 (ranking) if a human-expert directory exists.
 *
 * Run with one LLM profile at a time, e.g.:
 * {@code mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=evaluate,gemma4"}
 * (optional secondary property files must be on the Spring config path). Use
 * {@code --eval.notesSubdir=llm_chain_project} to read notes from a subfolder of {@code eval.notesDir}.
 * Multiple comma-separated models are still supported via {@code eval.models} when needed.
 */
@Component
@Profile("evaluate")
public class EvaluationRunner implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(EvaluationRunner.class);

	private final EvaluationConfig config;
	private final LlmConfig llmConfig;
	private final LlmProcessorService llmProcessorService;
	private final SnomedTerminologyService snomedTerminologyService;
	private final TerminologyTimingRecorder terminologyTimingRecorder;
	private final ObjectMapper objectMapper;

	public EvaluationRunner(EvaluationConfig config,
			LlmConfig llmConfig,
			LlmProcessorService llmProcessorService,
			SnomedTerminologyService snomedTerminologyService,
			TerminologyTimingRecorder terminologyTimingRecorder,
			ObjectMapper objectMapper) {
		this.config = config;
		this.llmConfig = llmConfig;
		this.llmProcessorService = llmProcessorService;
		this.snomedTerminologyService = snomedTerminologyService;
		this.terminologyTimingRecorder = terminologyTimingRecorder;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<String> configuredModels = config.getModels();
		List<String> models = configuredModels.isEmpty()
				? List.of(llmConfig.defaultModelName())
				: configuredModels;
		List<Path> noteFiles = loadNoteFiles();

		System.out.printf("%n=== Evaluation Stage 1: Benchmarking ===%n");
		System.out.printf("Models : %s%n", models);
		System.out.printf("Notes  : %d files in '%s'%n", noteFiles.size(), config.getNotesDir());
		System.out.printf("Output : %s%n%n", config.getBenchmarkOutputDir());

		ObjectMapper prettyMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);

		for (String model : models) {
			String sanitisedModel = model.replace(":", "_");
			Path modelOutputDir = Paths.get(config.getBenchmarkOutputDir(), sanitisedModel);
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

					// Parallel terminology enrichment (FHIR + optional rerank, synonym LLM, etc.)
					terminologyTimingRecorder.begin();
					TerminologyTimingRecorder.Snapshot timingSnap;
					try {
						List<CompletableFuture<Void>> futures = annotations.stream()
								.map(ann -> CompletableFuture.runAsync(
										() -> snomedTerminologyService.enrichAnnotation(ann)))
								.collect(Collectors.toList());
						CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
					} finally {
						timingSnap = terminologyTimingRecorder.finish();
					}
					long afterFhir = System.currentTimeMillis();

					double totalSeconds = round1dp((afterFhir - totalStart) / 1000.0);
					double llmSeconds = round1dp((afterLlm - totalStart) / 1000.0);
					double enrichWallSeconds = round1dp((afterFhir - afterLlm) / 1000.0);
					double fhirSumSeconds = round1dp(timingSnap.fhirSeconds());
					double rerankSumSeconds = round1dp(timingSnap.rerankSeconds());

					result = new BenchmarkResult(model, noteFileName,
							new Timings(totalSeconds, llmSeconds, enrichWallSeconds, fhirSumSeconds, rerankSumSeconds),
							annotations);

					System.out.printf("done (%ss total, %ss LLM, %ss enrich, %ss FHIR, %ss rerank, %d annotations)%n",
							totalSeconds, llmSeconds, enrichWallSeconds, fhirSumSeconds, rerankSumSeconds, annotations.size());
				} catch (Exception e) {
					long elapsed = System.currentTimeMillis() - totalStart;
					double totalSeconds = round1dp(elapsed / 1000.0);
					String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
					result = new BenchmarkResult(model, noteFileName,
							new Timings(totalSeconds, 0.0, 0.0, 0.0, 0.0), errorMsg);
					System.out.printf("ERROR (%ss): %s%n", totalSeconds, errorMsg);
				}

				// Write result (success or error) to output file
				String outputFileName = noteFileName.replaceFirst("\\.txt$", ".json");
				File outputFile = modelOutputDir.resolve(outputFileName).toFile();
				prettyMapper.writeValue(outputFile, result);
			}
			System.out.println();
		}

		// Stage 2: rank every model that has output under this notes' benchmark root (accumulates across runs)
		Path expertDir = Paths.get(config.getExpertDir());
		if (Files.isDirectory(expertDir)) {
			List<String> modelsForRanking = discoverModelsWithOutputs();
			if (modelsForRanking.isEmpty()) {
				System.out.println("Skipping Stage 2 (no benchmark JSON subdirectories under output root).");
			} else {
				System.out.println("=== Evaluation Stage 2: Ranking ===");
				System.out.printf("Models in ranking (from output dir): %s%n", modelsForRanking);
				RankingService rankingService = new RankingService(
						prettyMapper, config.getBenchmarkOutputDir(), config.getExpertDir());
				rankingService.rank(modelsForRanking, noteFiles);
			}
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

	/**
	 * Collects model ids from each subdirectory under the benchmark output root for this notes set
	 * (see {@link EvaluationConfig#getBenchmarkOutputDir()}) that contains benchmark JSON.
	 */
	private List<String> discoverModelsWithOutputs() throws Exception {
		Path root = Paths.get(config.getBenchmarkOutputDir());
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		Set<String> found = new LinkedHashSet<>();
		try (Stream<Path> children = Files.list(root)) {
			for (Path child : children.toList()) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				Optional<String> model = readModelFromBenchmarkFile(child);
				model.ifPresent(found::add);
			}
		}
		List<String> sorted = new ArrayList<>(found);
		sorted.sort(Comparator.naturalOrder());
		return sorted;
	}

	private Optional<String> readModelFromBenchmarkFile(Path modelDir) {
		try (Stream<Path> jsonFiles = Files.list(modelDir)) {
			return jsonFiles.filter(p -> p.toString().endsWith(".json")).findFirst().map(path -> {
				try {
					return objectMapper.readValue(path.toFile(), BenchmarkResult.class).getModel();
				} catch (Exception e) {
					logger.warn("Skip output dir {}: {}", modelDir.getFileName(), e.getMessage());
					return null;
				}
			}).filter(s -> s != null && !s.isBlank());
		} catch (Exception e) {
			logger.warn("Could not list {}: {}", modelDir, e.getMessage());
			return Optional.empty();
		}
	}
}
