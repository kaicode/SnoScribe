package org.snomed.snoscribe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.model.AnnotationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against a live Infinity rerank server (default {@code http://localhost:7997}).
 * <p>
 * Run from the project root:
 * <pre>
 * INFINITY_RERANK_IT=true mvn test -Dtest=InfinityRerankServiceIT
 * </pre>
 * Optional environment variables:
 * <ul>
 *   <li>{@code INFINITY_RERANK_BASE_URL} — override base URL (e.g. {@code http://127.0.0.1:7997})</li>
 *   <li>{@code INFINITY_RERANK_MODEL} — model name (default {@code reranker})</li>
 *   <li>{@code INFINITY_RERANK_MIN_SCORE} — minimum score to assign a concept (default {@code 0.01} for this test)</li>
 * </ul>
 * If {@code INFINITY_RERANK_IT} is not set to {@code true}, this class is skipped so CI and normal {@code mvn test} do not require Infinity.
 */
@SpringJUnitConfig(InfinityRerankServiceIT.MinimalContext.class)
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "INFINITY_RERANK_IT", matches = "true",
		disabledReason = "Set INFINITY_RERANK_IT=true and start Infinity rerank on INFINITY_RERANK_BASE_URL (default http://localhost:7997)")
class InfinityRerankServiceIT {

	@Autowired
	InfinityRerankService infinityRerankService;

	@Test
	@DisplayName("rerank assigns a concept when query matches expansion synonyms (live Infinity)")
	void rerank_assignsConcept_forLatentTuberculosisLikeExpansion() throws Exception {
		List<SnomedTerminologyService.FhirConcept> concepts = latentTuberculosisLikeExpansion();

		Annotation annotation = new Annotation();
		annotation.setType(AnnotationType.CONDITION);
		annotation.setNormalisedText("latent tuberculosis");

		infinityRerankService.tryRerankBestConcept(annotation, "latent tuberculosis", concepts);

		assertThat(annotation.getConceptCode())
				.as("Infinity should pick a concept whose terms best match the query")
				.isNotNull()
				.isNotBlank();
		assertThat(annotation.getConceptDisplay()).isNotBlank();
		assertThat(annotation.getTerminologyMatchedTerm())
				.as("rerank should record the expansion term that scored best")
				.isNotBlank();
		assertThat(List.of("111", "222", "333", "444", "555"))
				.contains(annotation.getConceptCode());
	}

	/**
	 * Mirrors a small FHIR $expand-style expansion (display + synonym designations) similar to the
	 * latent-TB case used when debugging h2c / body issues.
	 */
	private static List<SnomedTerminologyService.FhirConcept> latentTuberculosisLikeExpansion() {
		List<SnomedTerminologyService.FhirConcept> list = new ArrayList<>();
		list.add(concept("111", "Inactive tuberculosis", "Inactive tuberculosis"));
		list.add(concept("222", "Tuberculosis infection latent"));
		list.add(concept("333", "Inactive tuberculosis (finding)"));
		list.add(concept("444", "On treatment for latent tuberculosis", "On treatment for latent tuberculosis"));
		list.add(concept("555", "On chemoprophylaxis for inactive tuberculosis",
				"On chemoprophylaxis for inactive tuberculosis (finding)"));
		return list;
	}

	private static SnomedTerminologyService.FhirConcept concept(String code, String display, String... synonymValues) {
		SnomedTerminologyService.FhirConcept c = new SnomedTerminologyService.FhirConcept();
		c.code = code;
		c.display = display;
		if (synonymValues.length > 0) {
			c.designation = new ArrayList<>();
			for (String v : synonymValues) {
				SnomedTerminologyService.FhirDesignation d = new SnomedTerminologyService.FhirDesignation();
				d.value = v;
				c.designation.add(d);
			}
		}
		return c;
	}

	@Configuration
	static class MinimalContext {

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

		@Bean
		InfinityRerankService infinityRerankService(ObjectMapper objectMapper) {
			String baseUrl = System.getenv().getOrDefault("INFINITY_RERANK_BASE_URL", "http://localhost:7997");
			String model = System.getenv().getOrDefault("INFINITY_RERANK_MODEL", "reranker");
			double minScore = Double.parseDouble(System.getenv().getOrDefault("INFINITY_RERANK_MIN_SCORE", "0.01"));
			return new InfinityRerankService(objectMapper, baseUrl, model, minScore);
		}
	}
}
