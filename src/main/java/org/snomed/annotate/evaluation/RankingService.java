package org.snomed.annotate.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.annotate.evaluation.model.*;
import org.snomed.annotate.model.Annotation;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stage 2: loads human-expert and model-output JSON files, matches annotations
 * using a two-pass greedy strategy, computes precision/recall/F1 and field
 * accuracy metrics, then writes {@code ranking.json} and prints a summary table.
 *
 * <p>Not a Spring bean — instantiated directly by {@link EvaluationRunner}.
 *
 * <h3>Matching strategy (per note):</h3>
 * <ol>
 *   <li>Pass 1 — match remaining expert annotations to model annotations with the
 *       same non-null {@code conceptCode} (greedy, first-match wins).</li>
 *   <li>Pass 2 — match remaining annotations by {@code normalisedText}
 *       (case-insensitive).</li>
 * </ol>
 */
public class RankingService {

	private static final DateTimeFormatter TIMESTAMP_FMT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	private final ObjectMapper mapper;
	private final String outputDir;
	private final String expertDir;

	public RankingService(ObjectMapper mapper, String outputDir, String expertDir) {
		this.mapper = mapper;
		this.outputDir = outputDir;
		this.expertDir = expertDir;
	}

	public void rank(List<String> models, List<Path> noteFiles) throws Exception {
		List<ModelRanking> rankings = new ArrayList<>();

		for (String model : models) {
			String sanitisedModel = model.replace(":", "_");
			List<NoteScore> noteScores = new ArrayList<>();

			int totalTp = 0, totalExpert = 0, totalModel = 0;
			double sumSubjectAcc = 0, sumContextAcc = 0, sumLateralityAcc = 0;
			int scoredNotes = 0;

			for (Path notePath : noteFiles) {
				String noteFileName = notePath.getFileName().toString();
				String jsonFileName = noteFileName.replaceFirst("\\.txt$", ".json");

				// Load expert annotations
				Path expertFile = Paths.get(expertDir, jsonFileName);
				if (!Files.exists(expertFile)) {
					System.out.printf("  [WARN] No expert file for %s – skipping note.%n", noteFileName);
					continue;
				}
				List<Annotation> expertAnnotations = mapper.readValue(
						expertFile.toFile(), new TypeReference<>() {});

				// Load model annotations from BenchmarkResult wrapper
				Path modelFile = Paths.get(outputDir, sanitisedModel, jsonFileName);
				if (!Files.exists(modelFile)) {
					System.out.printf("  [WARN] No model output for %s / %s – skipping note.%n",
							model, noteFileName);
					continue;
				}
				BenchmarkResult result = mapper.readValue(modelFile.toFile(), BenchmarkResult.class);
				List<Annotation> modelAnnotations = result.getAnnotations() != null
						? result.getAnnotations() : List.of();

				NoteScore score = scoreNote(noteFileName, expertAnnotations, modelAnnotations);
				noteScores.add(score);

				totalTp     += score.getTruePositives();
				totalExpert += score.getExpertCount();
				totalModel  += score.getModelCount();
				sumSubjectAcc    += score.getSubjectAccuracy();
				sumContextAcc    += score.getContextAccuracy();
				sumLateralityAcc += score.getLateralityAccuracy();
				scoredNotes++;
			}

			// Aggregate micro-averaged P/R/F1
			double aggPrecision = totalModel > 0 ? (double) totalTp / totalModel : 0.0;
			double aggRecall    = totalExpert > 0 ? (double) totalTp / totalExpert : 0.0;
			double aggF1 = (aggPrecision + aggRecall) > 0
					? 2 * aggPrecision * aggRecall / (aggPrecision + aggRecall) : 0.0;

			double avgSubject    = scoredNotes > 0 ? sumSubjectAcc / scoredNotes : 0.0;
			double avgContext    = scoredNotes > 0 ? sumContextAcc / scoredNotes : 0.0;
			double avgLaterality = scoredNotes > 0 ? sumLateralityAcc / scoredNotes : 0.0;

			ModelRanking ranking = new ModelRanking();
			ranking.setModel(model);
			ranking.setAggregateF1(round4dp(aggF1));
			ranking.setAggregatePrecision(round4dp(aggPrecision));
			ranking.setAggregateRecall(round4dp(aggRecall));
			ranking.setAvgSubjectAccuracy(round4dp(avgSubject));
			ranking.setAvgContextAccuracy(round4dp(avgContext));
			ranking.setAvgLateralityAccuracy(round4dp(avgLaterality));
			ranking.setNoteScores(noteScores);
			rankings.add(ranking);
		}

		// Sort by aggregateF1 descending, assign ranks
		rankings.sort(Comparator.comparingDouble(ModelRanking::getAggregateF1).reversed());
		for (int i = 0; i < rankings.size(); i++) {
			rankings.get(i).setRank(i + 1);
		}

		// Build note file list (names only)
		List<String> noteFileNames = noteFiles.stream()
				.map(p -> p.getFileName().toString())
				.toList();

		RankingReport report = new RankingReport(
				LocalDateTime.now().format(TIMESTAMP_FMT),
				noteFileNames,
				rankings);

		// Write ranking.json
		File rankingFile = Paths.get(outputDir, "ranking.json").toFile();
		mapper.writeValue(rankingFile, report);
		System.out.printf("Ranking written to: %s%n%n", rankingFile.getPath());

		// Print summary table
		printSummaryTable(rankings);
	}

	// ── Per-note scoring ──────────────────────────────────────────────────────

	private NoteScore scoreNote(String noteFileName,
			List<Annotation> expertAnnotations,
			List<Annotation> modelAnnotations) {

		List<Annotation> unmatchedExpert = new ArrayList<>(expertAnnotations);
		List<Annotation> unmatchedModel  = new ArrayList<>(modelAnnotations);

		List<MatchedPair> matches = new ArrayList<>();

		// Pass 1: match by conceptCode (non-null on both sides)
		for (Annotation expert : new ArrayList<>(unmatchedExpert)) {
			if (expert.getConceptCode() == null || expert.getConceptCode().isBlank()) {
				continue;
			}
			Annotation matched = unmatchedModel.stream()
					.filter(m -> expert.getConceptCode().equals(m.getConceptCode()))
					.findFirst()
					.orElse(null);
			if (matched != null) {
				unmatchedExpert.remove(expert);
				unmatchedModel.remove(matched);
				matches.add(new MatchedPair(expert, matched));
			}
		}

		// Pass 2: match remaining by normalisedText (case-insensitive)
		for (Annotation expert : new ArrayList<>(unmatchedExpert)) {
			if (expert.getNormalisedText() == null || expert.getNormalisedText().isBlank()) {
				continue;
			}
			Annotation matched = unmatchedModel.stream()
					.filter(m -> m.getNormalisedText() != null
							&& expert.getNormalisedText().equalsIgnoreCase(m.getNormalisedText()))
					.findFirst()
					.orElse(null);
			if (matched != null) {
				unmatchedExpert.remove(expert);
				unmatchedModel.remove(matched);
				matches.add(new MatchedPair(expert, matched));
			}
		}

		int tp          = matches.size();
		int expertCount = expertAnnotations.size();
		int modelCount  = modelAnnotations.size();

		double precision = modelCount  > 0 ? (double) tp / modelCount  : 0.0;
		double recall    = expertCount > 0 ? (double) tp / expertCount : 0.0;
		double f1 = (precision + recall) > 0
				? 2 * precision * recall / (precision + recall) : 0.0;

		// Field accuracy: scored only over matched pairs
		double subjectAcc    = fieldAccuracy(matches, (e, m) ->
				Objects.equals(e.getSubject(), m.getSubject()));
		double contextAcc    = fieldAccuracy(matches, (e, m) ->
				Objects.equals(e.getContext(), m.getContext()));
		double lateralityAcc = fieldAccuracy(matches, (e, m) ->
				Objects.equals(e.getLaterality(), m.getLaterality()));

		NoteScore score = new NoteScore();
		score.setNoteFile(noteFileName);
		score.setExpertCount(expertCount);
		score.setModelCount(modelCount);
		score.setTruePositives(tp);
		score.setPrecision(round4dp(precision));
		score.setRecall(round4dp(recall));
		score.setF1(round4dp(f1));
		score.setSubjectAccuracy(round4dp(subjectAcc));
		score.setContextAccuracy(round4dp(contextAcc));
		score.setLateralityAccuracy(round4dp(lateralityAcc));
		return score;
	}

	@FunctionalInterface
	private interface FieldMatcher {
		boolean matches(Annotation expert, Annotation model);
	}

	private static double fieldAccuracy(List<MatchedPair> pairs, FieldMatcher matcher) {
		if (pairs.isEmpty()) return 1.0; // vacuously perfect when nothing to score
		long correct = pairs.stream()
				.filter(p -> matcher.matches(p.expert, p.model))
				.count();
		return (double) correct / pairs.size();
	}

	private record MatchedPair(Annotation expert, Annotation model) {}

	// ── Output ────────────────────────────────────────────────────────────────

	private void printSummaryTable(List<ModelRanking> rankings) {
		System.out.println("┌──────┬────────────────────────────────┬───────────┬───────────┬───────────┐");
		System.out.println("│ Rank │ Model                          │ Precision │  Recall   │    F1     │");
		System.out.println("├──────┼────────────────────────────────┼───────────┼───────────┼───────────┤");
		for (ModelRanking r : rankings) {
			System.out.printf("│ %-4d │ %-30s │  %-7.4f  │  %-7.4f  │  %-7.4f  │%n",
					r.getRank(),
					truncate(r.getModel(), 30),
					r.getAggregatePrecision(),
					r.getAggregateRecall(),
					r.getAggregateF1());
		}
		System.out.println("└──────┴────────────────────────────────┴───────────┴───────────┴───────────┘");
		System.out.println();
		System.out.println("┌──────┬────────────────────────────────┬───────────┬───────────┬───────────┐");
		System.out.println("│ Rank │ Model                          │  Subject  │  Context  │ Laterality│");
		System.out.println("├──────┼────────────────────────────────┼───────────┼───────────┼───────────┤");
		for (ModelRanking r : rankings) {
			System.out.printf("│ %-4d │ %-30s │  %-7.4f  │  %-7.4f  │  %-7.4f  │%n",
					r.getRank(),
					truncate(r.getModel(), 30),
					r.getAvgSubjectAccuracy(),
					r.getAvgContextAccuracy(),
					r.getAvgLateralityAccuracy());
		}
		System.out.println("└──────┴────────────────────────────────┴───────────┴───────────┴───────────┘");
	}

	private static String truncate(String s, int maxLen) {
		return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
	}

	private static double round4dp(double value) {
		return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
	}
}
