package org.snomed.snoscribe.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.model.Annotation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.snomed.snoscribe.service.SnomedTerminologyService.FhirConcept;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Infinity embedding server's Cohere-style {@code /rerank} API to pick the best
 * SNOMED concept from an FHIR ValueSet expansion when there is no whole-term match.
 */
@Service
public class InfinityRerankService {

	/** Infinity rerank API limit (RerankInput.documents maxItems). */
	private static final int RERANK_MAX_DOCUMENTS = 2048;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final String baseUrl;
	private final String model;
	private final double minScore;

	public InfinityRerankService(ObjectMapper objectMapper,
			@Value("${infinity.rerank.base-url:http://localhost:7997}") String baseUrl,
			@Value("${infinity.rerank.model:reranker}") String model,
			@Value("${infinity.rerank.min-score:0.5}") double minScore) {
		this.objectMapper = objectMapper;
		// HTTP/1.1 only: Java's HttpClient tries h2c upgrade on http:// by default; uvicorn
		// (Infinity) mishandles that and the POST body can arrive empty → FastAPI 422 "body" required.
		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		this.baseUrl = stripTrailingSlash(baseUrl);
		this.model = model;
		this.minScore = minScore;
	}

	/**
	 * Reranks all expansion terms via Infinity, aggregates max score per concept, and
	 * assigns the best concept if its score meets the configured minimum.
	 */
	public void tryRerankBestConcept(Annotation annotation, String filter,
			List<FhirConcept> concepts) throws IOException, InterruptedException {
		List<String> documents = new ArrayList<>();
		List<FhirConcept> docOwner = new ArrayList<>();
		outer:
		for (FhirConcept c : concepts) {
			for (String term : c.getTerms()) {
				if (term != null && !term.isBlank()) {
					documents.add(term);
					docOwner.add(c);
					if (documents.size() >= RERANK_MAX_DOCUMENTS) {
						logger.debug("Rerank documents truncated to {} (Infinity max)", RERANK_MAX_DOCUMENTS);
						break outer;
					}
				}
			}
		}
		if (documents.isEmpty()) {
			return;
		}

		RerankRequest body = new RerankRequest();
		body.model = model;
		body.query = filter;
		body.documents = documents;

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/rerank"))
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(20))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
				.build();

		HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (httpResponse.statusCode() != 200) {
			throw new IOException("Infinity rerank returned HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
		}

		RerankResponse response = objectMapper.readValue(httpResponse.body(), RerankResponse.class);
		if (response == null || response.results == null || response.results.isEmpty()) {
			logger.debug("Infinity rerank returned no results for filter '{}'", filter);
			return;
		}

		Map<String, Double> maxScoreByCode = new HashMap<>();
		for (RerankResultItem item : response.results) {
			if (item == null || item.index < 0 || item.index >= docOwner.size()) {
				continue;
			}
			FhirConcept owner = docOwner.get(item.index);
			if (owner.code == null || owner.code.isBlank()) {
				continue;
			}
			maxScoreByCode.merge(owner.code, item.relevanceScore, Math::max);
		}

		FhirConcept best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (FhirConcept c : concepts) {
			if (c.code == null || c.code.isBlank()) {
				continue;
			}
			Double s = maxScoreByCode.get(c.code);
			if (s == null) {
				continue;
			}
			if (s > bestScore) {
				bestScore = s;
				best = c;
			}
		}

		if (best != null && bestScore >= minScore) {
			annotation.setConceptCode(best.code);
			annotation.setConceptDisplay(best.display);
			String matchedTerm = bestScoringDocumentForConcept(response.results, docOwner, documents, best);
			annotation.setTerminologyMatchedTerm(matchedTerm);
		}
	}

	/**
	 * Among rerank hits for the chosen concept, returns the expansion document string
	 * with the highest relevance score (preferred synonym / matched term).
	 */
	private static String bestScoringDocumentForConcept(List<RerankResultItem> results,
			List<FhirConcept> docOwner, List<String> documents, FhirConcept best) {
		if (best == null || best.code == null || best.code.isBlank()) {
			return null;
		}
		double top = Double.NEGATIVE_INFINITY;
		String term = null;
		for (RerankResultItem item : results) {
			if (item == null || item.index < 0 || item.index >= docOwner.size() || item.index >= documents.size()) {
				continue;
			}
			if (!best.code.equals(docOwner.get(item.index).code)) {
				continue;
			}
			if (item.relevanceScore > top) {
				top = item.relevanceScore;
				term = documents.get(item.index);
			}
		}
		return term != null && !term.isBlank() ? term : best.display;
	}

	private static String stripTrailingSlash(String url) {
		if (url == null || url.isEmpty()) {
			return url;
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	// ── Infinity /rerank (Cohere-style) ───────────────────────────────────────

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class RerankRequest {
		public String model;
		public String query;
		public List<String> documents;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class RerankResponse {
		public List<RerankResultItem> results;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class RerankResultItem {
		@JsonProperty("relevance_score")
		public double relevanceScore;
		public int index;
	}
}
