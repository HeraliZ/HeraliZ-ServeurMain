package com.Spartan.scan;

/**
 * Un indice relevé par l'analyse d'un rapport de contrôle.
 *
 * @param key  identifiant stable (pour ne pas re-signaler le même indice)
 * @param text explication lisible par le staff
 */
public record Finding(Severity severity, String key, String text) {
    public enum Severity {
        INFO("Info"),
        SUSPECT("Suspect"),
        STRONG("Indice fort");

        public final String label;

        Severity(String label) {
            this.label = label;
        }

        public Severity raise() {
            return this == INFO ? SUSPECT : STRONG;
        }
    }

    public Finding withSeverity(Severity s, String suffix) {
        return new Finding(s, key, text + suffix);
    }
}
