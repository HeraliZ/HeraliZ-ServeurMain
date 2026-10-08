package fr.heraliz.usure;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import fr.heraliz.usure.regles.Categorie;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Regles;
import fr.heraliz.usure.regles.Surcharge;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fichier de reglages serveur {@code serverconfig/heraliz_usure-server.toml} du monde (TOML commente en francais). Il
 * se modifie a la main (relu tout seul par {@link ReglesServeur}) ou en jeu avec {@code /usure config}.
 * <p>Lu et ecrit par nos soins (et non par ForgeConfigSpec) : une seule ecriture atomique par enregistrement (fichier
 * temporaire puis remplacement), jamais de relecture d'un fichier a moitie ecrit, aucune valeur remise par defaut en
 * cours de route. Une valeur absente prend sa valeur par defaut ; une valeur hors bornes est corrigee.
 */
public final class UsureConfig {
    private UsureConfig() {
    }

    public static final String NOM = "heraliz_usure-server.toml";
    static final String[] KITS = {"kit_fortune", "kit_standard", "kit_armurier"};

    /** Regles lues et indicateur « fichier a reecrire » : des cles manquaient, ou il reste des cles de la 1.0.0 (enrayage). */
    public record Lecture(Regles regles, boolean incomplet) {
    }

    // ---------------------------------------------------------------- lecture
    public static Lecture lire(Path fichier) throws IOException {
        String texte = Files.readString(fichier, StandardCharsets.UTF_8);
        CommentedConfig c = TomlFormat.instance().createParser().parse(new StringReader(texte));
        Regles d = Regles.defauts();
        Regles r = new Regles();
        boolean[] manque = {false};
        Lecteur l = new Lecteur(c, manque);
        r.actif = l.bool("general.actif", d.actif);
        r.usureCreatif = l.bool("general.usure_creatif", d.usureCreatif);
        r.sansUsureEnMatch = l.bool("general.sans_usure_en_match", d.sansUsureEnMatch);
        r.hudAutorise = l.bool("general.hud_autorise", d.hudAutorise);
        r.dimensionsSansUsure = l.liste("general.dimensions_sans_usure", d.dimensionsSansUsure);
        r.munitionsMelee = l.liste("general.munitions_corps_a_corps", d.munitionsMelee);
        r.seuilNeuf = l.nombre("etats.seuil_neuf", d.seuilNeuf);
        r.seuilBon = l.nombre("etats.seuil_bon", d.seuilBon);
        r.seuilUse = l.nombre("etats.seuil_use", d.seuilUse);
        // cles d'enrayage de la 1.0.0 (retire en 1.0.1) : ignorees, le fichier est reecrit sans elles
        for (int i = 0; i < 4; i++) if (c.contains("etats.enrayage_" + Etat.TOUS[i].cle)) manque[0] = true;
        for (Categorie k : Categorie.TOUTES) {
            int i = k.ordinal();
            String p = "categories." + k.cle + ".";
            r.usure[i] = l.nombre(p + "usure_par_tir", d.usure[i]);
            r.pluie[i] = l.nombre(p + "pluie", d.pluie[i]);
            r.eau[i] = l.nombre(p + "eau", d.eau[i]);
            r.silencieux[i] = l.nombre(p + "silencieux", d.silencieux[i]);
            if (c.contains(p + "enrayage") || c.contains(p + "desenrayage")) manque[0] = true;
        }
        r.perteMaxParReparation = l.nombre("reparation.perte_max_par_reparation", d.perteMaxParReparation);
        r.maxMinimum = l.nombre("reparation.max_minimum", d.maxMinimum);
        for (int i = 0; i < Regles.NB_KITS; i++) {
            String p = "reparation." + KITS[i] + ".";
            r.kitGain[i] = l.nombre(p + "gain", d.kitGain[i]);
            r.kitPlafond[i] = l.nombre(p + "plafond", d.kitPlafond[i]);
            r.kitUtilisations[i] = Math.round(l.nombre(p + "utilisations", d.kitUtilisations[i]));
            r.kitRuinee[i] = l.bool(p + "arme_ruinee", d.kitRuinee[i]);
        }
        List<String> defautSurcharges = new ArrayList<>();
        d.surcharges.forEach(s -> defautSurcharges.add(s.ecrire()));
        for (String s : l.liste("armes.surcharges", defautSurcharges)) {
            if (s.contains("enrayage")) manque[0] = true;
            Surcharge su = Surcharge.lire(s);
            if (su != null) r.surcharges.add(su);
        }
        for (String s : l.liste("armes.accessoires", List.of())) {
            int eg = s.indexOf('=');
            if (eg <= 0) continue;
            try {
                r.accessoires.put(s.substring(0, eg).trim().toLowerCase(Locale.ROOT), Float.parseFloat(s.substring(eg + 1).trim().replace(',', '.')));
            } catch (NumberFormatException ignored) {
                // ligne illisible : ignoree
            }
        }
        return new Lecture(r.corriger(), manque[0]);
    }

    /** Lecture tolerante : valeur absente ou du mauvais type = defaut (et le fichier sera complete). */
    private record Lecteur(Config c, boolean[] manque) {
        Object brut(String chemin) {
            Object o = c.get(chemin);
            if (o == null) manque[0] = true;
            return o;
        }

        boolean bool(String chemin, boolean defaut) {
            return brut(chemin) instanceof Boolean b ? b : defaut;
        }

        float nombre(String chemin, float defaut) {
            return brut(chemin) instanceof Number n ? n.floatValue() : defaut;
        }

        List<String> liste(String chemin, List<String> defaut) {
            if (!(brut(chemin) instanceof List<?> l)) return new ArrayList<>(defaut);
            List<String> r = new ArrayList<>();
            for (Object o : l) if (o != null && !o.toString().isBlank()) r.add(o.toString().trim());
            return r;
        }
    }

    // ---------------------------------------------------------------- ecriture
    /** Ecrit le fichier d'un coup (fichier temporaire puis remplacement atomique). */
    public static void ecrire(Path fichier, Regles r) throws IOException {
        CommentedConfig c = TomlFormat.newConfig(LinkedHashMap::new);
        Ecrivain e = new Ecrivain(c);
        e.table("general", "HeraliZ Usure des armes : reglages du serveur. Ce fichier se modifie a la main (il est relu tout seul",
                "quelques secondes apres l'enregistrement) ou en jeu avec /usure config (op 2), qui le reecrit.");
        e.val("general.actif", r.actif, "false = aucune usure nulle part.");
        e.val("general.usure_creatif", r.usureCreatif, "Les armes s'usent aussi en mode creatif.");
        e.val("general.sans_usure_en_match", r.sansUsureEnMatch,
                "Aucune usure pendant un match (duels, ClashWar, zombies : armes pretees).");
        e.val("general.hud_autorise", r.hudAutorise, "Petite pastille d'etat sous les munitions (chaque joueur peut aussi la couper).");
        e.val("general.dimensions_sans_usure", new ArrayList<>(r.dimensionsSansUsure), "Dimensions ou les armes ne s'usent pas.");
        e.val("general.munitions_corps_a_corps", new ArrayList<>(r.munitionsMelee),
                "Munitions des armes de corps a corps (couteaux faits en armes TaCZ) : categorie melee.");

        e.table("etats", "Etats (en % d'etat) : Neuf a partir de seuil_neuf, Bon a partir de seuil_bon, Use a partir de seuil_use,",
                "Abime en dessous, Ruine a 0 % (l'arme ne tire plus avant d'etre reparee). Aucun enrayage, aucun malus :",
                "l'usure ne change ni les degats ni la precision.");
        e.val("etats.seuil_neuf", r.seuilNeuf, null);
        e.val("etats.seuil_bon", r.seuilBon, null);
        e.val("etats.seuil_use", r.seuilUse, null);

        e.table("categories", "Valeurs par categorie d'arme.",
                "usure_par_tir : % d'etat perdu a chaque tir (0.1 = 1000 tirs de 100 % a 0 %).",
                "pluie / eau / silencieux : usure en plus (%) en tirant sous la pluie, dans l'eau, avec un silencieux.",
                "Categories : pistolet, pm (pistolet-mitrailleur), fusil (d'assaut), dmr, precision, pompe, mitrailleuse,",
                "lanceur, melee (corps a corps).");
        for (Categorie k : Categorie.TOUTES) {
            int i = k.ordinal();
            String p = "categories." + k.cle + ".";
            e.val(p + "usure_par_tir", r.usure[i], null);
            e.val(p + "pluie", r.pluie[i], null);
            e.val(p + "eau", r.eau[i], null);
            e.val(p + "silencieux", r.silencieux[i], null);
        }

        e.table("reparation", "Reparation avec les kits (dans l'inventaire : prendre le kit, puis clic droit sur l'arme).",
                "perte_max_par_reparation : l'etat maximal de l'arme baisse d'autant (points de %) a chaque reparation (0 = jamais).",
                "max_minimum : il ne descend jamais en dessous.",
                "Par kit : gain = % d'etat rendu ; plafond = le kit ne monte pas l'arme au-dessus ; utilisations = reparations",
                "par kit ; arme_ruinee = le kit peut reparer une arme ruinee.");
        e.val("reparation.perte_max_par_reparation", r.perteMaxParReparation, null);
        e.val("reparation.max_minimum", r.maxMinimum, null);
        for (int i = 0; i < Regles.NB_KITS; i++) {
            String p = "reparation." + KITS[i] + ".";
            e.val(p + "gain", r.kitGain[i], null);
            e.val(p + "plafond", r.kitPlafond[i], null);
            e.val(p + "utilisations", r.kitUtilisations[i], null);
            e.val(p + "arme_ruinee", r.kitRuinee[i], null);
        }

        e.table("armes", "surcharges : reglages par arme (ou groupe d'armes avec *). Le premier qui correspond s'applique (un nom",
                "exact passe avant un groupe). Forme : \"<id d'arme> = categorie:dmr ; usure:0.2 ; pluie:30 ; eau:100 ;",
                "silencieux:20\", ou \"<id> = exclue\" (aucune usure). Valeur absente = celle de la",
                "categorie.",
                "accessoires : usure en plus (%) quand l'accessoire TaCZ est monte : \"<id d'accessoire> = 20\".");
        List<String> s = new ArrayList<>();
        r.surcharges.forEach(x -> s.add(x.ecrire()));
        e.val("armes.surcharges", s, null);
        List<String> a = new ArrayList<>();
        for (Map.Entry<String, Float> en : r.accessoires.entrySet()) a.add(en.getKey() + " = " + Regles.texte(en.getValue()));
        e.val("armes.accessoires", a, null);

        TomlWriter w = TomlFormat.instance().createWriter();
        w.setIndent("    ");
        w.setIndentArrayElementsPredicate(l -> !l.isEmpty());
        String texte = w.writeToString(c);
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        Files.writeString(tmp, texte, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Ecrivain(CommentedConfig c) {
        void table(String nom, String... commentaire) {
            c.set(nom, TomlFormat.newConfig(LinkedHashMap::new));
            c.setComment(nom, " " + String.join("\n ", commentaire));
        }

        void val(String chemin, Object valeur, String commentaire) {
            Object v = valeur instanceof Float f ? arrondi(f) : valeur;
            c.set(chemin, v);
            if (commentaire != null) c.setComment(chemin, " " + commentaire);
        }
    }

    /** Valeur arrondie au millieme (0.08 et non 0.07999999821186066) ; entier si rond. */
    private static Object arrondi(float v) {
        double d = Math.round(v * 1000.0) / 1000.0;
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return (long) d;
        return d;
    }
}
