package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import fr.heralisz.matchs.zombies.jeu.api.PlanGiant;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Acte IV de The Giant (D-057) : la porte de confinement, l'intro du boss N-07, le combat dans le hall du Secteur 7 sous
 * l'usine, l'outro, le retour par le teleporteur 935, puis la partie continue (facon CoD : les manches reprennent, la quete
 * est enregistree « terminee en X manches », objectif « Survivez »). Le boss lui-meme est dans son mod
 * ({@code heralisz_n07}), joint par {@link ZombiesApi.SalleBoss} ; sans lui, la carte d'acces a la porte acheve la quete.
 * <ul>
 *     <li>Debut : joueurs a terre releves, morts ramenes, zombies de la manche retires (rendus a la file de la manche),
 *     manches suspendues, musique coupee ; tous les joueurs voient l'intro (passable a la majorite, ESPACE maintenu).</li>
 *     <li>Combat : joueurs proteges pendant chaque cinematique (et les renforts figes) ; quelques zombies sortent des 4
 *     coins du hall ; fin de phase 2 / 3 : Max Ammo au point de largage + 4 / 6 chiens dans un eclair ; degats du boss
 *     ramenes aux 20 / 50 PV (montant exact, jamais d'un coup sans passer par « a terre ») ; +10 par touche, bonus a tous a
 *     la victoire.</li>
 *     <li>Retour : le teleporteur 935 renvoie chaque joueur sur le pont du mainframe ; apres {@code retour_max_s} (90 s),
 *     ceux qui restent sont evacues ; puis les manches reprennent.</li>
 *     <li>Defaite (tous a terre) : fin de partie normale ; fin de partie, abandon : combat interrompu, salle remise a zero
 *     par tranches, personne ne reste coince (evacuation).</li>
 * </ul>
 * Rien n'est fait par tick hors de l'acte ; pendant l'acte : un test de volume par joueur toutes les 10 ticks au retour.
 */
final class ActeQuatre implements ZombiesApi.EvenementsBoss {
    enum Etat {
        /** Porte encore fermee. */
        AUCUN,
        /** Intro (porte, monte-charge, reveil). */
        INTRO,
        /** Combat contre N-07. */
        COMBAT,
        /** Mort du boss et outro. */
        OUTRO,
        /** Teleporteur 935 allume : les joueurs remontent. */
        RETOUR,
        /** Quete terminee (ou partie finie) : plus rien. */
        FINI
    }

    private final PartieZombies partie;
    private final PlanGiant plan;
    @Nullable
    private ZombiesApi.SalleBoss salle;
    private Etat etat = Etat.AUCUN;
    /** Cinematique du boss en cours (apparition, phase, mort, outro) : joueurs proteges, renforts figes. */
    private boolean cine;
    /** Le boss a ete lance pendant cette partie : salle a remettre a zero a la fin. */
    private boolean lance;
    private long debutRetour = -1;
    private long prochainRenfort;
    /** Chiens a faire arriver (un par eclair, toutes les {@link #CADENCE_CHIENS} ticks). */
    private int chiensRestants;
    private long prochainChien;
    private int arrivees;
    private final Set<UUID> descendus = new HashSet<>();
    private static final int CADENCE_CHIENS = 14;

    ActeQuatre(PartieZombies partie, PlanGiant plan) {
        this.partie = partie;
        this.plan = plan;
    }

    // ------------------------------------------------------------------ cycle de la partie
    /** Debut de partie : lie la salle du boss posee avec l'instance (et la remet a zero si besoin, par tranches). */
    void preparer() {
        ZombiesApi.BossFinal b = ZombiesApi.bossFinal();
        if (b == null || plan.salleBoss == null || partie.niveau() == null) return;
        try {
            salle = b.salle(partie.niveau(), plan.salleBoss);
        } catch (RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.error("[zombies] acte IV : salle du boss illisible, quete sans boss", ex);
            salle = null;
        }
        HeraliszMatchs.LOG.info("[zombies] acte IV : {}", salle != null ? "salle du boss N-07 liee en " + plan.salleBoss.origine().toShortString()
                : "sans boss (mod heralisz_n07 absent ou salle non posee)");
    }

    /** Fin de partie (ecran de fin) : combat interrompu tout de suite (camera rendue), salle remise a zero. */
    void finPartie() {
        etat = Etat.FINI;
        cine = false;
        chiensRestants = 0;
        if (salle != null && lance) {
            lance = false;
            try {
                salle.remettre();
            } catch (RuntimeException | LinkageError ex) {
                HeraliszMatchs.LOG.error("[zombies] acte IV : remise a zero de la salle", ex);
            }
        }
    }

    /** Instance rendue : salle oubliee (teleporteur, evenements). */
    void nettoyer() {
        finPartie();
        if (salle == null) return;
        try {
            salle.oublier();
        } catch (RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.error("[zombies] acte IV : salle oubliee", ex);
        }
        salle = null;
    }

    Etat etat() {
        return etat;
    }

    /** Le boss existe pour cette partie (mod present et salle posee). */
    boolean avecBoss() {
        return salle != null;
    }

    /** Les manches (apparitions, garde-fou) sont suspendues pendant tout l'acte. */
    boolean bloqueManches() {
        return etat == Etat.INTRO || etat == Etat.COMBAT || etat == Etat.OUTRO || etat == Etat.RETOUR;
    }

    /** Joueurs invulnerables : intro, outro, cinematiques du boss. */
    boolean protege() {
        return etat == Etat.INTRO || etat == Etat.OUTRO || cine;
    }

    /** Combat en cours (tirs sur le boss : points). */
    boolean combat() {
        return etat == Etat.COMBAT && !cine;
    }

    /** Volume de la salle (hall + puits), ou null. */
    @Nullable
    AABB volume() {
        return salle == null ? null : salle.volume();
    }

    @Nullable
    List<Vec3> renforts() {
        return salle == null ? null : salle.renforts();
    }

    // ------------------------------------------------------------------ porte de confinement
    /**
     * La porte de confinement est ouverte avec la carte d'acces. Rend vrai si l'acte commence (ou si la quete s'acheve ici,
     * sans boss) ; faux = refus (le jeu le dit au joueur).
     */
    boolean lancer(ServerPlayer par) {
        if (etat != Etat.AUCUN) return false;
        if (salle == null) {
            // pas de boss : la quete est terminee a la porte (records, « Survivez »)
            etat = Etat.FINI;
            partie.objectifRempliPublic("n07");
            partie.victoireQuete();
            return true;
        }
        partie.finaleDebut();
        List<ServerPlayer> js = partie.joueursFinale();
        if (js.isEmpty()) {
            partie.finaleFin();
            return false;
        }
        descendus.clear();
        for (ServerPlayer p : js) descendus.add(p.getUUID());
        ZombiesApi.ContexteBoss ctx = new ZombiesApi.ContexteBoss(partie.id(), partie.niveau(), partie::joueursFinale, js.size(), Math.max(1, partie.manche()),
                partie::cibleBoss, this::marquer, this);
        boolean ok;
        try {
            ok = salle.lancer(ctx);
        } catch (RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.error("[zombies] acte IV : lancement du boss en erreur", ex);
            ok = false;
        }
        if (!ok) {
            partie.finaleFin();
            return false;
        }
        etat = Etat.INTRO;
        lance = true;
        cine = true;
        arrivees = 0;
        partie.objectifRempliPublic("n07");
        HeraliszMatchs.LOG.info("[zombies] {} : acte IV lance par {} a la manche {} ({} joueur(s))", partie.id(), par.getGameProfile().getName(), partie.manche(),
                js.size());
        return true;
    }

    /** Marque chaque entite du combat (boss, projectiles, statue) : purge de fin de partie, tirs reconnus. */
    private void marquer(Entity e) {
        e.getPersistentData().putString(ZombiesApi.TAG_PARTIE, partie.id());
        e.getPersistentData().putBoolean(ZombiesApi.TAG_BOSS, true);
    }

    // ------------------------------------------------------------------ tick
    void tick(long t) {
        if (salle == null) return;
        switch (etat) {
            case INTRO, COMBAT -> {
                // filet : combat disparu sans evenement (ne doit pas arriver)
                if (t % 20 == 0 && !salle.enCombat()) {
                    abandon("combat introuvable");
                    return;
                }
                if (etat == Etat.COMBAT && !cine) {
                    tickChiens(t);
                    tickRenforts(t);
                }
            }
            case RETOUR -> {
                if (t % 10 == 0) verifierRetour(t);
            }
            default -> {
            }
        }
    }

    /** Quelques zombies sortent des coins du hall pendant le combat (hors manche, comme les chiens du teleporteur). */
    private void tickRenforts(long t) {
        if (t < prochainRenfort) return;
        int cadence = Math.max(2, ConfigZombies.entier("the_giant", "boss_renforts_cadence_s", 9)) * 20;
        prochainRenfort = t + cadence;
        int max = ConfigZombies.entier("the_giant", "boss_renforts_max", 3);
        List<Vec3> pts = renforts();
        if (max <= 0 || pts == null || pts.isEmpty() || partie.renfortsVivants() >= max) return;
        Vec3 p = pts.get(partie.alea().nextInt(pts.size()));
        partie.apparaitreRenfort(p, false, false);
    }

    private void tickChiens(long t) {
        if (chiensRestants <= 0 || t < prochainChien) return;
        List<Vec3> pts = renforts();
        if (pts == null || pts.isEmpty()) {
            chiensRestants = 0;
            return;
        }
        Vec3 c = pts.get(chiensRestants % pts.size());
        double a = partie.alea().nextDouble() * Math.PI * 2;
        partie.apparaitreRenfort(new Vec3(c.x + Math.cos(a) * 1.5, c.y, c.z + Math.sin(a) * 1.5), true, true);
        chiensRestants--;
        prochainChien = t + CADENCE_CHIENS;
    }

    // ------------------------------------------------------------------ retour par le teleporteur
    private void verifierRetour(long t) {
        AABB v = volume();
        if (v == null) {
            terminer();
            return;
        }
        AABB vv = v.inflate(1);
        boolean reste = false;
        for (ServerPlayer p : partie.joueursFinale()) {
            if (p.level() == partie.niveau() && vv.contains(p.position())) {
                reste = true;
                break;
            }
        }
        if (!reste) {
            terminer();
            return;
        }
        long max = ConfigZombies.entier("the_giant", "retour_max_s", 90) * 20L;
        if (debutRetour >= 0 && t - debutRetour >= max) {
            debutRetour = -1;
            HeraliszMatchs.LOG.info("[zombies] acte IV : delai de retour depasse, evacuation");
            try {
                salle.evacuer();
            } catch (RuntimeException | LinkageError ex) {
                HeraliszMatchs.LOG.error("[zombies] acte IV : evacuation", ex);
                evacuerNous();
            }
        }
    }

    /** Tous remontes : la quete est terminee, la partie continue (les manches reprennent). */
    private void terminer() {
        etat = Etat.FINI;
        cine = false;
        debutRetour = -1;
        descendus.clear();
        partie.objectifRempliPublic("n07_retour");
        partie.finaleFin();
        partie.victoireQuete();
    }

    /** Evacuation sans le mod du boss (filet) : chaque joueur encore dans la salle remonte. */
    private void evacuerNous() {
        AABB v = volume();
        if (v == null) return;
        AABB vv = v.inflate(1);
        for (ServerPlayer p : partie.enLigneCourant()) {
            if (p.level() == partie.niveau() && vv.contains(p.position())) retour(p, true);
        }
    }

    /** Teleporte le joueur sur le pont du mainframe (eclair d'arrivee des teleporteurs). */
    private void teleporterMainframe(ServerPlayer p) {
        PlanGiant.Mainframe mf = plan.mainframe;
        Vec3 arrivee;
        if (mf != null) arrivee = Vec3.atBottomCenterOf(mf.pad());
        else {
            partie.surSortieBornes(p);
            return;
        }
        int k = arrivees++ % 4;
        double a = k * 1.7, r = k == 0 ? 0 : 1.2;
        p.teleportTo(partie.niveau(), arrivee.x + Math.cos(a) * r, arrivee.y + 0.05, arrivee.z + Math.sin(a) * r, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        MatchsNet.versJoueur(p, PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.TELEPORT, p.getUUID(), 0, 0, ""));
        try {
            ZombiesApi.decor().arrivee(partie.instance(), arrivee);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.arrivee", ex);
        }
        partie.son(ZombiesApi.Sons.TELEPORTEUR_ARRIVEE, arrivee.x, arrivee.y + 1, arrivee.z, 1.3f);
    }

    // ------------------------------------------------------------------ evenements du boss
    @Override
    public void crochet(String id) {
        switch (id) {
            case "porte_ouverture" -> {
                try {
                    ZombiesApi.decor().porteConfinement(partie.instance(), true);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[zombies] decor.porteConfinement", ex);
                }
            }
            case "combat" -> debutCombat();
            case "p2_fin" -> transition(ConfigZombies.entier("the_giant", "boss_chiens_p2", 4));
            case "p3_fin" -> transition(ConfigZombies.entier("the_giant", "boss_chiens_p3", 6));
            default -> {
            }
        }
        HeraliszMatchs.LOG.debug("[zombies] acte IV : crochet {}", id);
    }

    /** Fin d'une cinematique de phase : Max Ammo au point de largage, chiens dans un eclair. */
    private void transition(int chiens) {
        if (salle == null || etat != Etat.COMBAT) return;
        Vec3 l = salle.largage();
        partie.poserBonusPublic(TypeBonus.MUNITIONS_MAX, l.x, l.y, l.z);
        chiensRestants += Math.max(0, chiens);
        prochainChien = partie.ticks() + 20;
    }

    private void debutCombat() {
        if (etat != Etat.INTRO) return;
        etat = Etat.COMBAT;
        cine = false;
        prochainRenfort = partie.ticks() + 20L * ConfigZombies.entier("the_giant", "boss_renforts_premier_s", 12);
        partie.musiquePublic(MomentMusique.BOSS_N07);
        partie.envoyerObjectifProgres();
        HeraliszMatchs.LOG.info("[zombies] acte IV : combat contre N-07");
    }

    @Override
    public void cinematique(boolean debut) {
        if (cine == debut) return;
        cine = debut;
        partie.gelerRenforts(debut);
    }

    @Override
    public void main() {
        debutCombat();
    }

    @Override
    public void degats(ServerPlayer joueur, float montant) {
        partie.degatsBossPrevus(joueur, montant);
    }

    @Override
    public void mortDebut() {
        cine = true;
        chiensRestants = 0;
        // les renforts meurent avec lui (sans points), comme dans CoD
        partie.retirerRenforts();
    }

    @Override
    public void victoire() {
        if (etat == Etat.FINI || etat == Etat.AUCUN) return;
        etat = Etat.OUTRO;
        cine = true;
        partie.releverTous();
        partie.objectifRempliPublic("n07_boss");
        partie.bonusTous(ConfigZombies.entier("the_giant", "boss_points", 2000));
        // l'outro a ses propres sons : silence de la musique du jeu jusqu'au retour
        partie.musiquePublic(MomentMusique.BOSS_INTRO);
        HeraliszMatchs.LOG.info("[zombies] {} : N-07 vaincu a la manche {}", partie.id(), partie.manche());
    }

    @Override
    public void outroFinie() {
        if (etat != Etat.OUTRO) return;
        etat = Etat.RETOUR;
        cine = false;
        debutRetour = partie.ticks();
        partie.musiquePublic(MomentMusique.BOSS_FIN);
        partie.annonceChat(Component.translatable("zombies.giant.n07.retour").withStyle(s -> s.withColor(0x9CE8FF)));
        partie.envoyerObjectifProgres();
    }

    @Override
    public void abandon(String raison) {
        if (etat != Etat.INTRO && etat != Etat.COMBAT && etat != Etat.OUTRO) return;
        HeraliszMatchs.LOG.warn("[zombies] {} : combat contre N-07 interrompu ({}) : evacuation, la porte pourra resservir", partie.id(), raison);
        etat = Etat.AUCUN;
        cine = false;
        chiensRestants = 0;
        evacuerNous();
        partie.retirerRenforts();
        partie.finaleFin();
        if (salle != null) {
            try {
                salle.remettre();
            } catch (RuntimeException | LinkageError ex) {
                HeraliszMatchs.LOG.error("[zombies] acte IV : remise a zero de la salle", ex);
            }
        }
        if (partie.giant() != null) partie.giant().bossRelance();
        partie.messageTous("zombies.giant.n07.interrompu");
    }

    @Override
    public void retour(ServerPlayer joueur, boolean evacuation) {
        JoueurZombies j = partie.joueurZ(joueur.getUUID());
        if (j == null || joueur.level() != partie.niveau()) return;
        teleporterMainframe(joueur);
        HeraliszMatchs.LOG.info("[zombies] acte IV : {} remonte ({})", j.nom, evacuation ? "evacuation" : "teleporteur 935");
    }

    /** Un joueur quitte la partie. */
    void deconnexion(UUID u) {
        descendus.remove(u);
    }

    /** Resume pour {@code /zombies dev etat}. */
    String resume() {
        String s = salle == null ? "sans boss" : salle.resume();
        return String.format(Locale.ROOT, "acteIV=%s cine=%b chiens=%d (%s)", etat, cine, chiensRestants, s);
    }

    /** {@code /zombies dev giant bossvictoire} : le boss tombe tout de suite. */
    void devVictoire() {
        if (salle != null) salle.devVictoire();
    }

    /** {@code /zombies dev giant bossevacuer} : tout le monde remonte, combat interrompu. */
    void devEvacuer() {
        if (salle == null) return;
        try {
            salle.evacuer();
        } catch (RuntimeException | LinkageError ex) {
            evacuerNous();
        }
    }
}
