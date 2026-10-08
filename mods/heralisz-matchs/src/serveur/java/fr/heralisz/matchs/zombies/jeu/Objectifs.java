package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.zombies.jeu.api.PlanZombies;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guidage discret facon CoD (ZOMBIES_V2_SPEC §2) : chaine d'objectifs « Ouvrez une porte » -> « Trouvez la salle de
 * controle et activez le courant » -> « Ameliorez une arme au Pack-a-Punch » -> « Mystere... » (easter egg), puis
 * « Survivez ». Ordre lu dans {@code objectifs.json} ; un objectif sans objet sur la carte (pas de porte, pas de levier,
 * pas de Pack-a-Punch, pas d'easter egg) est saute, un objectif deja rempli aussi. Textes : ceux de la carte
 * ({@code objectifs[]}) sinon les cles {@code zombies.jeu.objectif.<id>}. Mis a jour a l'evenement
 * ({@link #rempli}), jamais par tick.
 * <p>Cartes v3 (The Giant, {@code version_format} >= 3) : la chaine est celle de la carte ({@code objectifs[]} dans
 * l'ordre : porte, fusibles, courant, teleporteurs, pack_a_punch, phare, n07, n07_boss, n07_retour), les textes sont les cles courtes
 * {@code zombies.jeu.objectif.giant.<id>} (identifiant envoye : {@code giant.<id>}), suivis d'une progression
 * ({@code (2/3)}, chrono de liaison) donnee par {@link QueteGiant#progres}.
 * <p>0.2.3 (guidage) : chaque objectif de The Giant est decoupe en sous-etapes ({@link QueteGiant#sousEtape} : apres 3/3
 * fusibles, « Placez les fusibles dans l'armoire electrique (cour) »...), avec le lieu de la cible dans le texte et un
 * repere sur la boussole du HUD ({@link QueteGiant#cible}) ; jamais de repere pour une recherche (fusibles, objets caches).
 */
final class Objectifs {
    private final PlanZombies plan;
    private final List<String> chaine;
    private final String apres;
    private final Set<String> faits = new HashSet<>();
    private int index = -1;
    private String courant = "";
    /** Carte v3 : chaine de la carte, textes courts du jeu. */
    private final boolean v3;
    @javax.annotation.Nullable
    private final QueteGiant giant;

    Objectifs(PlanZombies plan, boolean courantDejaLa) {
        this(plan, courantDejaLa, null);
    }

    Objectifs(PlanZombies plan, boolean courantDejaLa, @javax.annotation.Nullable QueteGiant giant) {
        this.plan = plan;
        this.giant = giant;
        this.v3 = plan.versionFormat() >= 3 && !plan.objectifs().isEmpty();
        if (v3) {
            List<String> l = new ArrayList<>();
            for (PlanZombies.Objectif o : plan.objectifs()) l.add(o.id());
            this.chaine = l;
        } else {
            this.chaine = new ArrayList<>(ConfigZombies.chaineObjectifs());
        }
        this.apres = ConfigZombies.objectifFinal();
        if (courantDejaLa) faits.add("courant");
    }

    /** Objectif courant (vide = aucun). */
    String courant() {
        return courant;
    }

    /** Vrai si l'objectif a un objet sur cette carte. */
    private boolean disponible(String id) {
        return switch (id) {
            case "porte" -> !plan.portes().isEmpty();
            case "courant" -> plan.courant() != null;
            case "pack_a_punch" -> plan.packAPunch() != null;
            case "mystere" -> plan.easterEgg() != null;
            case "atelier" -> !plan.ateliers().isEmpty();
            case "fusibles", "teleporteurs", "phare", "n07", "n07_boss", "n07_retour" -> giant != null && giant.disponible(id);
            default -> true;
        };
    }

    /** Place le premier objectif disponible et non rempli ; rend vrai si l'objectif a change. */
    boolean demarrer() {
        index = -1;
        return avancer();
    }

    /** Un evenement remplit l'objectif {@code id} (meme s'il n'est pas le courant) ; rend vrai si l'affichage change. */
    boolean rempli(String id) {
        if (!faits.add(id)) return false;
        return id.equals(courant) && avancer();
    }

    private boolean avancer() {
        String avant = courant;
        for (int i = index + 1; i < chaine.size(); i++) {
            String id = chaine.get(i);
            if (!disponible(id) || faits.contains(id)) continue;
            index = i;
            courant = id;
            return !courant.equals(avant);
        }
        index = chaine.size();
        courant = apres;
        return !courant.equals(avant);
    }

    /** Paquet d'affichage de l'objectif courant ({@code nouveau} : le precedent vient d'etre rempli ; {@code t} = tick de la partie). */
    PaquetsZombies.ObjectifS2C paquet(boolean nouveau, long t) {
        if (courant.isEmpty()) return new PaquetsZombies.ObjectifS2C("", "", "", nouveau);
        String progres = giant == null ? "" : giant.progres(courant, t);
        if (v3) {
            // 0.2.3 : sous-etape guidee (« Placez les fusibles dans l'armoire electrique (cour) »), lieu et repere de boussole
            if (giant == null) return new PaquetsZombies.ObjectifS2C("giant." + courant, "", "", nouveau, progres);
            String sous = giant.sousEtape(courant);
            PlanZombies.Zone z = plan.zone(giant.zoneCible(sous));
            String lieuFr = z == null ? "" : z.nom(), lieuEn = z == null ? "" : (z.nomEn().isEmpty() ? z.nom() : z.nomEn());
            net.minecraft.world.phys.Vec3 c = giant.cible(sous);
            return new PaquetsZombies.ObjectifS2C("giant." + sous, "", "", nouveau, progres, lieuFr, lieuEn, giant.lettreCible(sous), c);
        }
        PlanZombies.Objectif o = plan.objectif(courant);
        return new PaquetsZombies.ObjectifS2C(courant, o == null ? "" : o.texteFr(), o == null ? "" : o.texteEn(), nouveau, progres);
    }

    PaquetsZombies.ObjectifS2C paquet(boolean nouveau) {
        return paquet(nouveau, 0);
    }
}
