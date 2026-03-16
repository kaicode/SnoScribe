package org.snomed.snoscribe;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.snomed.snoscribe.exception.ServiceException;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.service.OllamaProcessorService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Runs a quick smoke-test annotation on startup in non-evaluate profiles.
 * Mirrors the old {@code @PostConstruct} in {@code AnnotateApplication}.
 */
@Component
@Profile("!evaluate")
public class DevTestRunner {

	private final OllamaProcessorService ollamaProcessorService;
	private final ObjectMapper objectMapper;

	public DevTestRunner(OllamaProcessorService ollamaProcessorService, ObjectMapper objectMapper) {
		this.ollamaProcessorService = ollamaProcessorService;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void run() throws IOException, ServiceException {
		System.out.println("App started");
		System.out.println("Running test...");
		Date start = new Date();
		List<Annotation> response = ollamaProcessorService.processDocument("""
				* Patient presents for a new patient health check.  States generally feels well.
				* **PMHx:**  Osteoarthritis (right knee), diagnosed 2015.  Mild hypertension, diagnosed 2018. Denies any previous surgeries, hospital admissions, or significant illnesses.
				* **FHx:** Father – MI at 72. Mother – Osteoporosis. Denies any significant family history of cancer or other major illnesses.
				* **SHx:**  Non-smoker.  Social drinks 1-2 glasses of wine per week.  Retired school teacher.  Lives with husband.  Active in local gardening club.
				* **Medications:**
					* Ramipril 5mg OD
					* Paracetamol 500mg PRN for pain
				* **Allergies:** NKDA (No Known Drug Allergies)
				"""
		);
		System.out.println(objectMapper.writeValueAsString(response));
		System.out.printf("Test complete (%s seconds).%n", (new Date().getTime() - start.getTime()) / 1000);
	}
}
