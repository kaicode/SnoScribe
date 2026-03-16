package org.snomed.snoscribe.evaluation.model;

public class NoteScore {

	private String noteFile;
	private int expertCount;
	private int modelCount;
	private int truePositives;
	private double precision;
	private double recall;
	private double f1;
	private double subjectAccuracy;
	private double contextAccuracy;
	private double lateralityAccuracy;

	public NoteScore() {}

	public String getNoteFile() {
		return noteFile;
	}

	public void setNoteFile(String noteFile) {
		this.noteFile = noteFile;
	}

	public int getExpertCount() {
		return expertCount;
	}

	public void setExpertCount(int expertCount) {
		this.expertCount = expertCount;
	}

	public int getModelCount() {
		return modelCount;
	}

	public void setModelCount(int modelCount) {
		this.modelCount = modelCount;
	}

	public int getTruePositives() {
		return truePositives;
	}

	public void setTruePositives(int truePositives) {
		this.truePositives = truePositives;
	}

	public double getPrecision() {
		return precision;
	}

	public void setPrecision(double precision) {
		this.precision = precision;
	}

	public double getRecall() {
		return recall;
	}

	public void setRecall(double recall) {
		this.recall = recall;
	}

	public double getF1() {
		return f1;
	}

	public void setF1(double f1) {
		this.f1 = f1;
	}

	public double getSubjectAccuracy() {
		return subjectAccuracy;
	}

	public void setSubjectAccuracy(double subjectAccuracy) {
		this.subjectAccuracy = subjectAccuracy;
	}

	public double getContextAccuracy() {
		return contextAccuracy;
	}

	public void setContextAccuracy(double contextAccuracy) {
		this.contextAccuracy = contextAccuracy;
	}

	public double getLateralityAccuracy() {
		return lateralityAccuracy;
	}

	public void setLateralityAccuracy(double lateralityAccuracy) {
		this.lateralityAccuracy = lateralityAccuracy;
	}
}
