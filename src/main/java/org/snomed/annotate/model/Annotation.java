package org.snomed.annotate.model;

public class Annotation {

	private AnnotationType type;
	private String text;
	private String normalisedText;
	private boolean negated;
	private Subject subject;
	private Laterality laterality;
	private Context context;
	private String dose;
	private String frequency;
	private String route;
	private String doseForm;
	private String conceptCode;
	private String conceptDisplay;

	public AnnotationType getType() {
		return type;
	}

	public void setType(AnnotationType type) {
		this.type = type;
	}

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

	public String getDose() {
		return dose;
	}

	public void setDose(String dose) {
		this.dose = dose;
	}

	public String getFrequency() {
		return frequency;
	}

	public void setFrequency(String frequency) {
		this.frequency = frequency;
	}

	public String getRoute() {
		return route;
	}

	public void setRoute(String route) {
		this.route = route;
	}

	public String getDoseForm() {
		return doseForm;
	}

	public void setDoseForm(String doseForm) {
		this.doseForm = doseForm;
	}

	public String getConceptCode() {
		return conceptCode;
	}

	public void setConceptCode(String conceptCode) {
		this.conceptCode = conceptCode;
	}

	public String getConceptDisplay() {
		return conceptDisplay;
	}

	public void setConceptDisplay(String conceptDisplay) {
		this.conceptDisplay = conceptDisplay;
	}
}
