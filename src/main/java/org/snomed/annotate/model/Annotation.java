package org.snomed.annotate.model;

public class Annotation {

	private String text;
	private String normalisedText;
	private boolean negated;
	private Subject subject;
	private Laterality laterality;
	private Context context;

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getNormalisedText() {
		return normalisedText;
	}

	public void setNormalisedText(String normalisedText) {
		this.normalisedText = normalisedText;
	}

	public boolean isNegated() {
		return negated;
	}

	public void setNegated(boolean negated) {
		this.negated = negated;
	}

	public Subject getSubject() {
		return subject;
	}

	public void setSubject(Subject subject) {
		this.subject = subject;
	}

	public Laterality getLaterality() {
		return laterality;
	}

	public void setLaterality(Laterality laterality) {
		this.laterality = laterality;
	}

	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}
}
