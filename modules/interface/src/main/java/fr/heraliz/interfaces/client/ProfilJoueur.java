package fr.heraliz.interfaces.client;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce que montre la carte du joueur du menu Echap, lu chez nos autres mods par reflexion (le menu n'en depend pas) :
 * solde en herals (heraliz_clansetbases), clan et zones tenues (clans), extractions ouvertes (heraliz_extraction).
 * Un mod absent ou une lecture qui echoue rend « rien » : la carte cache simplement la ligne.
 */
final class ProfilJoueur {
    private ProfilJoueur() {
    }

    /** Le clan du joueur : tag, nom, couleur, grade (texte), zones tenues (noms). */
    record Clan(String tag, String nom, int accent, String grade, List<String> zones) {
    }

    /** Une extraction ouverte : nom affiche et secondes avant fermeture (-1 : toujours ouverte). */
    record Extraction(String nom, int secondes) {
    }

    private static Method soldeConnu, solde, snapshot, ouvertes;
    private static boolean economieCherchee, clansCherches, extractionCherchee;

    /** Solde du joueur en herals, null si l'economie est absente ou n'a encore rien envoye. */
    @Nullable
    static Long solde() {
        if (!economieCherchee) {
            economieCherchee = true;
            try {
                Class<?> c = Class.forName("fr.heralisz.economie.client.ClientSolde");
                soldeConnu = c.getMethod("connu");
                solde = c.getMethod("solde");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                solde = null;
            }
        }
        if (solde == null) return null;
        try {
            if (!(Boolean) soldeConnu.invoke(null)) return null;
            return ((Number) solde.invoke(null)).longValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Le clan du joueur, null s'il n'en a pas (ou si le mod des clans est absent). */
    @Nullable
    static Clan clan() {
        if (!clansCherches) {
            clansCherches = true;
            try {
                snapshot = Class.forName("fr.heralisz.clans.client.ClientClans").getMethod("snapshot");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                snapshot = null;
            }
        }
        if (snapshot == null) return null;
        try {
            Object s = snapshot.invoke(null);
            if (s == null) return null;
            Class<?> c = s.getClass();
            String tag = String.valueOf(c.getMethod("tag").invoke(s));
            String nom = String.valueOf(c.getMethod("nom").invoke(s));
            int accent = ((Number) c.getMethod("accent").invoke(s)).intValue() & 0xFFFFFF;
            String grade = "";
            Object g = c.getMethod("monGrade").invoke(s);
            if (g != null) grade = Component.translatable(String.valueOf(g.getClass().getMethod("libelle").invoke(g))).getString();
            List<String> zones = new ArrayList<>();
            Object t = c.getMethod("territoires").invoke(s);
            if (t instanceof List<?> l) {
                for (Object z : l) {
                    if (z != null) zones.add(String.valueOf(z.getClass().getMethod("nom").invoke(z)));
                }
            }
            return new Clan(tag, nom, accent, grade, zones);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Extractions ouvertes (liste vide si aucune ou si le mod est absent). */
    static List<Extraction> extractions(long heureDeJeu) {
        if (!extractionCherchee) {
            extractionCherchee = true;
            try {
                ouvertes = Class.forName("com.heraliz.extraction.api.ExtractionApi").getMethod("ouvertes");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                ouvertes = null;
            }
        }
        if (ouvertes == null) return List.of();
        try {
            Object o = ouvertes.invoke(null);
            List<Extraction> out = new ArrayList<>();
            if (o instanceof List<?> l) {
                for (Object e : l) {
                    if (e == null) continue;
                    Class<?> c = e.getClass();
                    String nom = String.valueOf(c.getMethod("libelle").invoke(e));
                    int s = ((Number) c.getMethod("secondesRestantes", long.class).invoke(e, heureDeJeu)).intValue();
                    out.add(new Extraction(nom, s));
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }
}
