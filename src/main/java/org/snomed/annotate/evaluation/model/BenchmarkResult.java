package org.snomed.annotate.evaluation.model;

import org.snomed.annotate.model.Annotation;

import java.util.List;

public class BenchmarkResult {

	private String model;
	private String noteFile;
	private Timings timings;
	private List<Annotation> annotations;
	private String error;

	public BenchmarkResult() {}

	public BenchmarkResult(String model, String noteFile, Timings timings, List<Annotation> annotations) {
		this.model = model;
		this.noteFile = noteFile;
		this.timings = timings;
		this.annotations = annotations;
	}

	public BenchmarkResult(String model, String noteFile, Timings timings, String error) {
		this.model = model;
		this.noteFile = noteFile;
		this.timings = timings;
		this.annotations = List.of();
		this.error = error;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getNoteFile() {
		return noteFile;
	}

	public void setNoteFile(String noteFile) {
		this.noteFile = noteFile;
	}

	public Timings getTimings() {
		return timings;
	}

	public void setTimings(Timings timings) {
		this.timings = timings;
	}

	public List<Annotation> getAnnotations() {
		return annotations;
	}

	public void setAnnotations(List<Annotation> annotations) {
		this.annotations = annotations;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}
}
