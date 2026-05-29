package org.snomed.snoscribe.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * When {@link #begin()} / {@link #finish()} bracket parallel terminology enrichment,
 * records aggregate time spent in FHIR HTTP calls vs Infinity rerank HTTP calls
 * (summed across threads; may exceed wall clock when work overlaps).
 */
@Component
public class TerminologyTimingRecorder {

	private final AtomicBoolean recording = new AtomicBoolean(false);
	private final LongAdder fhirNanos = new LongAdder();
	private final LongAdder rerankNanos = new LongAdder();

	public void begin() {
		fhirNanos.reset();
		rerankNanos.reset();
		recording.set(true);
	}

	public Snapshot finish() {
		recording.set(false);
		return new Snapshot(fhirNanos.sum(), rerankNanos.sum());
	}

	public void addFhirNanos(long deltaNanos) {
		if (recording.get()) {
			fhirNanos.add(deltaNanos);
		}
	}

	public void addRerankNanos(long deltaNanos) {
		if (recording.get()) {
			rerankNanos.add(deltaNanos);
		}
	}

	public record Snapshot(long fhirNanos, long rerankNanos) {
		public double fhirSeconds() {
			return fhirNanos / 1_000_000_000.0;
		}

		public double rerankSeconds() {
			return rerankNanos / 1_000_000_000.0;
		}
	}
}
