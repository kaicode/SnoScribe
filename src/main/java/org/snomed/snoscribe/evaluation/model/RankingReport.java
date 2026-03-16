package org.snomed.snoscribe.evaluation.model;

import java.util.List;

public class RankingReport {

	private String generatedAt;
	private List<String> noteFiles;
	private List<ModelRanking> rankings;

	public RankingReport() {}

	public RankingReport(String generatedAt, List<String> noteFiles, List<ModelRanking> rankings) {
		this.generatedAt = generatedAt;
		this.noteFiles = noteFiles;
		this.rankings = rankings;
	}

	public String getGeneratedAt() {
		return generatedAt;
	}

	public void setGeneratedAt(String generatedAt) {
		this.generatedAt = generatedAt;
	}

	public List<String> getNoteFiles() {
		return noteFiles;
	}

	public void setNoteFiles(List<String> noteFiles) {
		this.noteFiles = noteFiles;
	}

	public List<ModelRanking> getRankings() {
		return rankings;
	}

	public void setRankings(List<ModelRanking> rankings) {
		this.rankings = rankings;
	}
}
