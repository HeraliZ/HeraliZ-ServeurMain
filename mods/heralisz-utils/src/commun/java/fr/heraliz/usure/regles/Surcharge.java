package fr.heraliz.usure.regles;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Reglage propre a une arme (ou a un groupe d'armes avec « * ») : categorie imposee, arme exclue (aucune usure), et
 * valeurs qui remplacent celles de la categorie. Une valeur NaN = celle de la categorie.
 * <p>Forme texte (fichier de reglages) : {@code "tacz:m95 = categorie:precision ; usure:0.2 ; pluie:30 ; eau:100 ;
 * silencieux:20"} ou {@code "heralisz_skins:*__* = exclue"}. Les anciennes cles (enrayage de la 1.0.0) sont ignorees.
 */
public final class Surcharge {
    public String motif;
    @Nullable
    public Categorie categorie;
    public boolean exclue;
    public float usure = Float.NaN, pluie = Float.NaN, eau = Float.NaN, silencieux = Float.NaN;

    public Surcharge(String motif) {
        this.motif = motif == null ? "" : motif.trim();
    }

    public Surcharge copie() {
        Surcharge s = new Surcharge(motif);
        s.categorie = categorie;
        s.exclue = exclue;
        s.usure = usure;
        s.pluie = pluie;
        s.eau = eau;
        s.silencieux = silencieux;
        return s;
    }

    /** Vrai si le motif contient « * » (s'applique a plusieurs armes). */
    public boolean groupe() {
        return motif.indexOf('*') >= 0;
    }

    /** Le motif (avec « * » pour n'importe quelle suite de caracteres) couvre-t-il cet identifiant d'arme ? */
    public boolean couvre(String gunId) {
        return Motif.correspond(motif, gunId);
    }

    /** Lit une ligne du fichier ; null si elle est vide ou illisible. */
    @Nullable
    public static Surcharge lire(String ligne) {
        if (ligne == null) return null;
        int eg = ligne.indexOf('=');
        String motif = (eg < 0 ? ligne : ligne.substring(0, eg)).trim().toLowerCase(Locale.ROOT);
        if (motif.isEmpty()) return null;
        Surcharge s = new Surcharge(motif);
        if (eg < 0) return s;
        for (String morceau : ligne.substring(eg + 1).split(";|,(?=\\s*[a-zA-Z])")) {
            String m = morceau.trim();
            if (m.isEmpty()) continue;
            int sep = m.indexOf(':');
            if (sep < 0) sep = m.indexOf('=');
            String cle = (sep < 0 ? m : m.substring(0, sep)).trim().toLowerCase(Locale.ROOT);
            String val = sep < 0 ? "" : m.substring(sep + 1).trim();
            switch (cle) {
                case "exclue", "exclu", "aucune_usure", "excluded" -> s.exclue = val.isEmpty() || Boolean.parseBoolean(val) || val.equals("1") || val.equals("oui");
                case "categorie", "cat", "category" -> s.categorie = Categorie.parCle(val);
                case "usure", "wear" -> s.usure = nombre(val);
                case "pluie", "rain" -> s.pluie = nombre(val);
                case "eau", "water" -> s.eau = nombre(val);
                case "silencieux", "silence", "suppressor" -> s.silencieux = nombre(val);
                default -> {
                }
            }
        }
        return s;
    }

    private static float nombre(String v) {
        try {
            float f = Float.parseFloat(v.replace(',', '.'));
            return Float.isFinite(f) && f >= 0 ? f : Float.NaN;
        } catch (NumberFormatException ex) {
            return Float.NaN;
        }
    }

    /** Forme texte du fichier de reglages. */
    public String ecrire() {
        StringBuilder sb = new StringBuilder(motif).append(" =");
        boolean premier = true;
        if (exclue) {
            sb.append(" exclue");
            premier = false;
        }
        if (categorie != null) premier = ajouter(sb, premier, "categorie", categorie.cle);
        if (!Float.isNaN(usure)) premier = ajouter(sb, premier, "usure", Regles.texte(usure));
        if (!Float.isNaN(pluie)) premier = ajouter(sb, premier, "pluie", Regles.texte(pluie));
        if (!Float.isNaN(eau)) premier = ajouter(sb, premier, "eau", Regles.texte(eau));
        if (!Float.isNaN(silencieux)) ajouter(sb, premier, "silencieux", Regles.texte(silencieux));
        return sb.toString();
    }

    private static boolean ajouter(StringBuilder sb, boolean premier, String cle, String val) {
        sb.append(premier ? " " : " ; ").append(cle).append(':').append(val);
        return false;
    }
}
