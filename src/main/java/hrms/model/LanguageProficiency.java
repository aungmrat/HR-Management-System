package hrms.model;

/**
 * Language proficiency levels.
 */
public enum LanguageProficiency {
    NATIVE("Native"),
    FLUENT("Fluent"),
    INTERMEDIATE("Intermediate"),
    BASIC("Basic"),
    NONE("None");

    private final String label;

    LanguageProficiency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static LanguageProficiency fromLabel(String label) {
        if (label == null) return NONE;
        for (LanguageProficiency p : values()) {
            if (p.label.equalsIgnoreCase(label.trim())) {
                return p;
            }
        }
        return NONE;
    }
}