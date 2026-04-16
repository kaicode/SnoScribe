package org.snomed.snoscribe.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.model.AnnotationType;
import org.snomed.snoscribe.model.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SnomedTerminologyService {

	private static final String CONDITION_ECL  = "<404684003 |Clinical finding|";
	private static final String PROCEDURE_ECL  = "<71388002 |Procedure|";
	private static final String MEDICATION_ECL = "<763158003 |Medicinal product (product)|";

	private static final String SNOMED_SYSTEM_URI = "http://snomed.info/sct";
	private static final String ICD_10_TARGET_SYSTEM_URI = "http://hl7.org/fhir/sid/icd-10";

	/** Matches a run of digits immediately followed by letters, e.g. "5mg" → "5 mg". */
	private static final Pattern DOSE_SPACING = Pattern.compile("(\\d+)([a-zA-Z])");

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final RestTemplate restTemplate;
	private final InfinityRerankService infinityRerankService;
	private final TerminologySynonymService terminologySynonymService;
	private final String fhirTxUrl;
	private final boolean infinityRerankEnabled;

	public SnomedTerminologyService(RestTemplateBuilder builder,
			InfinityRerankService infinityRerankService,
			TerminologySynonymService terminologySynonymService,
			@Value("${fhir.tx.url}") String fhirTxUrl,
			@Value("${infinity.rerank.enabled:true}") boolean infinityRerankEnabled) {
		this.restTemplate = builder
				.connectTimeout(Duration.ofSeconds(10))
				.readTimeout(Duration.ofSeconds(10))
				.build();
		this.infinityRerankService = infinityRerankService;
		this.terminologySynonymService = terminologySynonymService;
		this.fhirTxUrl = fhirTxUrl;
		this.infinityRerankEnabled = infinityRerankEnabled;
	}

	/**
	 * Looks up the best-matching SNOMED CT concept for the annotation and sets
	 * {@code conceptCode} and {@code conceptDisplay} if an exact case-insensitive
	 * match is found. If the terminology server returns no expansion rows, retries
	 * with {@code ~} appended to the filter for fuzzy search. If fuzzy still returns
	 * nothing, asks the LLM for one or two synonyms and searches again with strict
	 * filter only (no fuzzy on those synonyms). Non-exact expansion rows are passed
	 * through the reranker.
	 * When the reranker assigns a concept, the winning expansion term is stored in
	 * {@link Annotation#setTerminologyMatchedTerm}.
	 * Failures are logged and silently swallowed so that the annotation is still
	 * returned without a concept.
	 */
	public void enrichAnnotation(Annotation annotation) {
		try {
			String ecl;
			String filter;

			if (annotation.getType() == AnnotationType.MEDICATION) {
				ecl    = MEDICATION_ECL;
				filter = buildMedicationFilter(annotation);
			} else if (annotation.getType() == AnnotationType.PROCEDURE) {
				ecl    = PROCEDURE_ECL;
				filter = annotation.getNormalisedText();
			} else {
				ecl    = CONDITION_ECL;
				filter = annotation.getNormalisedText();
			}

			if (filter == null || filter.isBlank()) {
				return;
			}

			// Search for concept (strict, then fuzzy ~)
			List<FhirConcept> concepts = callFhirExpand(ecl, filter);
			if (concepts.isEmpty()) {
				String fuzzy = fuzzyFilter(filter);
				if (!fuzzy.equals(filter)) {
					concepts = callFhirExpand(ecl, fuzzy);
				}
			}

			String synonymUsedForExpand = null;
			if (concepts.isEmpty()) {
				for (String syn : terminologySynonymService.suggestSynonyms(filter, annotation.getType())) {
					List<FhirConcept> synHits = callFhirExpand(ecl, syn);
					if (!synHits.isEmpty()) {
						concepts = synHits;
						synonymUsedForExpand = syn;
						break;
					}
				}
			}

			// Does term exactly match any result PT or synonyms?
			final String synForWhole = synonymUsedForExpand;
			boolean wholeMatched = concepts.stream()
					.filter(c -> c.wholeTermFilter(filter) || (synForWhole != null && c.wholeTermFilter(synForWhole)))
					.findFirst()
					.map(c -> {
						annotation.setConceptCode(c.code);
						annotation.setConceptDisplay(c.display);
						return true;
					})
					.orElse(false);

			// Use reranker to find result with same meaning
			if (!wholeMatched && infinityRerankEnabled && !concepts.isEmpty()) {
				infinityRerankService.tryRerankBestConcept(annotation, filter, concepts);
			}

			mapIcd10ForPatientCondition(annotation);

		} catch (Exception e) {
			logger.warn("SNOMED CT lookup failed for '{}': {}", annotation.getNormalisedText(), e.getMessage());
		}
	}

	/**
	 * For patient conditions with a resolved SNOMED code, maps to ICD-10 via FHIR
	 * {@code ConceptMap/$translate} when the terminology server provides a match.
	 */
	private void mapIcd10ForPatientCondition(Annotation annotation) {
		if (annotation.getType() != AnnotationType.CONDITION) {
			return;
		}
		Subject subject = annotation.getSubject();
		if (subject != null && subject != Subject.PATIENT) {
			return;
		}
		String snomedCode = annotation.getConceptCode();
		if (snomedCode == null || snomedCode.isBlank()) {
			return;
		}
		try {
			Icd10Mapping mapping = callConceptMapTranslateToIcd10(snomedCode);
			if (mapping != null && mapping.code != null && !mapping.code.isBlank()) {
				annotation.setIcd10Code(mapping.code.trim());
				if (mapping.display != null && !mapping.display.isBlank()) {
					annotation.setIcd10Display(mapping.display.trim());
				}
			}
		} catch (Exception e) {
			logger.debug("ICD-10 ConceptMap translate failed for SNOMED {}: {}", snomedCode, e.getMessage());
		}
	}

	private Icd10Mapping callConceptMapTranslateToIcd10(String snomedCode) {
		String base = fhirTxUrl.endsWith("/") ? fhirTxUrl.substring(0, fhirTxUrl.length() - 1) : fhirTxUrl;
		String url = base + "/ConceptMap/$translate"
				+ "?_format=json"
				+ "&code=" + encode(snomedCode)
				+ "&system=" + SNOMED_SYSTEM_URI
				+ "&targetsystem=" + ICD_10_TARGET_SYSTEM_URI;

		logger.debug("FHIR ConceptMap translate: {}", url);

		FhirParametersResponse response = restTemplate.getForObject(url, FhirParametersResponse.class);
		if (response == null || response.parameter == null) {
			return null;
		}
		for (FhirParameter param : response.parameter) {
			if (!"match".equals(param.name) || param.part == null) {
				continue;
			}
			for (FhirParameterPart part : param.part) {
				if (!"concept".equals(part.name) || part.valueCoding == null) {
					continue;
				}
				String sys = part.valueCoding.system;
				String code = part.valueCoding.code;
				if (code == null || code.isBlank()) {
					continue;
				}
				if (sys != null && sys.startsWith("http://hl7.org/fhir/sid/icd-10")) {
					return new Icd10Mapping(code, part.valueCoding.display);
				}
			}
		}
		return null;
	}

	private record Icd10Mapping(String code, String display) {}

	/** Appends {@code ~} for terminology servers that treat it as a fuzzy match hint (avoids {@code ~~}). */
	private static String fuzzyFilter(String filter) {
		if (filter.endsWith("~")) {
			return filter;
		}
		return filter + "~";
	}

	/**
	 * Builds a SNOMED-style medication filter string, e.g. "Ramipril 5 mg oral tablet".
	 * Parts that are null or blank are omitted.
	 */
	private String buildMedicationFilter(Annotation annotation) {
		StringBuilder sb = new StringBuilder();

		String name = annotation.getNormalisedText();
		if (name != null && !name.isBlank()) {
			// Capitalize first letter only
			sb.append(Character.toUpperCase(name.charAt(0))).append(name.substring(1));
		}

		String dose = annotation.getDose();
		if (dose != null && !dose.isBlank()) {
			// Insert space between digits and unit, e.g. "5mg" → "5 mg"
			dose = DOSE_SPACING.matcher(dose).replaceAll("$1 $2");
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(dose);
		}

		String route = annotation.getRoute();
		if (route != null && !route.isBlank()) {
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(route);
		}

		String doseForm = annotation.getDoseForm();
		if (doseForm != null && !doseForm.isBlank()) {
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(doseForm);
		}

		return sb.toString();
	}

	/**
	 * Calls the FHIR ValueSet/$expand endpoint and returns the list of concepts
	 * from the expansion. Returns an empty list on any error.
	 */
	private List<FhirConcept> callFhirExpand(String ecl, String filter) {
		// Construct the URL manually to avoid double-encoding of the ECL expression.
		// The `url` query param contains its own `?` and special chars that must
		// be left as-is for the FHIR server to parse correctly.
		String url = fhirTxUrl + "/ValueSet/$expand"
				+ "?_format=json"
				+ "&includeDesignations=true"
				+ "&url=" + encode("http://snomed.info/sct?fhir_vs=ecl/" + ecl)
				+ "&filter=" + encode(filter).replace("%7E", "~");

		logger.debug("FHIR expand: {}", url);

		FhirValueSetResponse response = restTemplate.getForObject(url, FhirValueSetResponse.class);
		if (response == null || response.expansion == null || response.expansion.contains == null) {
			logger.info("FHIR expand with 0 results: {}", url);
			return Collections.emptyList();
		}
		logger.info("FHIR expand with {} results: {}", response.expansion.contains.size(), url);
		return response.expansion.contains;
	}

	/** Percent-encodes a string for use in a URL query parameter value. */
	private static String encode(String value) {
		try {
			return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			return value;
		}
	}

	// ── Minimal FHIR response POJOs ──────────────────────────────────────────

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirValueSetResponse {
		public FhirExpansion expansion;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirExpansion {
		public List<FhirConcept> contains;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirConcept {
		public String code;
		public String display;
		public List<FhirDesignation> designation;

		List<String> getTerms() {
			List<String> terms = new ArrayList<>();
			terms.add(display);
			if (designation != null) {
				for (FhirDesignation fhirDesignation : designation) {
					terms.add(fhirDesignation.value);
				}
			}
			return terms;
		}

		/** Returns true if filter matches the display term or any synonym (case-insensitive). */
		boolean wholeTermFilter(String filter) {
			for (String term : getTerms()) {
				if (filter.equalsIgnoreCase(term)) {
					return true;
				}
			}
			return false;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirDesignation {
		public String value;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirParametersResponse {
		public List<FhirParameter> parameter;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirParameter {
		public String name;
		public Boolean valueBoolean;
		public String valueString;
		public List<FhirParameterPart> part;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirParameterPart {
		public String name;
		public FhirValueCoding valueCoding;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class FhirValueCoding {
		public String system;
		public String code;
		public String display;
	}
}
