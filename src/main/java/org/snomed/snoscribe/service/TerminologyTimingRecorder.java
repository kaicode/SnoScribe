package org.snomed.snoscribe.service;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-annotation wall-clock timing for terminology enrichment: FHIR HTTP calls and
 * Infinity rerank HTTP calls on the thread that runs {@code enrichAnnotation}.
 * Create one instance per annotation; sum snapshots across annotations for batch totals.
 */
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

	public static double sumFhirSeconds(Collection<Snapshot> snapshots) {
		long total = 0;
		for (Snapshot s : snapshots) {
			total += s.fhirNanos;
		}
		return total / 1_000_000_000.0;
	}

	public static double sumRerankSeconds(Collection<Snapshot> snapshots) {
		long total = 0;
		for (Snapshot s : snapshots) {
			total += s.rerankNanos;
		}
		return total / 1_000_000_000.0;
	}
}
