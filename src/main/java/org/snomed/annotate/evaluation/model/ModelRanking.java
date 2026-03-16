package org.snomed.annotate.evaluation.model;

import java.util.List;

public class ModelRanking {

	private int rank;
	private String model;
	private double aggregateF1;
	private double aggregatePrecision;
	private double aggregateRecall;
	private double avgSubjectAccuracy;
	private double avgContextAccuracy;
	private double avgLateralityAccuracy;
	private List<NoteScore> noteScores;

	public ModelRanking() {}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public double getAggregateF1() {
		return aggregateF1;
	}

	public void setAggregateF1(double aggregateF1) {
		this.aggregateF1 = aggregateF1;
	}

	public double getAggregatePrecision() {
		return aggregatePrecision;
	}

	public void setAggregatePrecision(double aggregatePrecision) {
		this.aggregatePrecision = aggregatePrecision;
	}

	public double getAggregateRecall() {
		return aggregateRecall;
	}

	public void setAggregateRecall(double aggregateRecall) {
		this.aggregateRecall = aggregateRecall;
	}

	public double getAvgSubjectAccuracy() {
		return avgSubjectAccuracy;
	}

	public void setAvgSubjectAccuracy(double avgSubjectAccuracy) {
		this.avgSubjectAccuracy = avgSubjectAccuracy;
	}

	public double getAvgContextAccuracy() {
		return avgContextAccuracy;
	}

	public void setAvgContextAccuracy(double avgContextAccuracy) {
		this.avgContextAccuracy = avgContextAccuracy;
	}

	public double getAvgLateralityAccuracy() {
		return avgLateralityAccuracy;
	}

	public void setAvgLateralityAccuracy(double avgLateralityAccuracy) {
		this.avgLateralityAccuracy = avgLateralityAccuracy;
	}

	public List<NoteScore> getNoteScores() {
		return noteScores;
	}

	public void setNoteScores(List<NoteScore> noteScores) {
		this.noteScores = noteScores;
	}
}
