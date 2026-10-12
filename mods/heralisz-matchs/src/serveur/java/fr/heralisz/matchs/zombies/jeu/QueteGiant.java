package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import fr.heralisz.matchs.zombies.jeu.api.PlanGiant;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.TypeZombie;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatPad;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Regles et quete de « The Giant » (ZOMBIES_V3_SPEC §2 et §5, D-051), branchees sur {@link PartieZombies} quand la carte a
 * les champs v3 ({@link PlanGiant}). Tout est evenementiel : aucune recherche dans le monde par tick, une mort de zombie
 * coute au plus 3 calculs de distance (les 3 cuves), les paquets ne partent qu'aux changements.
 * <ul>
 *     <li><b>Acte I, le courant</b> : 3 fusibles tires parmi les cachettes (6 sur The Giant), visibles par un eclat ; un
 *     joueur les ramasse (equipe), les pose dans l'armoire, puis abaisse le levier : courant, portes de la cour, passerelle,
 *     machines.</li>
 *     <li><b>Acte II, les teleporteurs</b> : chaque zombie tue pres d'une cuve (courant mis) y envoie son ame (~20 en solo,
 *     +25 % par joueur) ; cuve pleine = noyau de 115 charge. Liaison facon CoD : maintenir F sur le pad (30 s), puis maintenir
 *     F au pupitre du mainframe ; un bonus gratuit apparait devant le mainframe. 3 liaisons : le rideau du Pack-a-Punch
 *     monte. Se teleporter : 1500, du pad vers le mainframe, recharge 30 s, tue les zombies du pad, parfois un bonus, apres
 *     la manche 25 parfois des chiens.</li>
 *     <li><b>Pieges electriques</b> (1000, courant requis), <b>Bowie Knife</b> (3000, un coup jusqu'a la manche 10).</li>
 *     <li><b>Acte III, le Fly Trap</b> : tirer avec une arme amelioree sur le boitier, puis sur les 3 objets caches (singe,
 *     deux nounours) ; l'Annihilator (revolver TaCZ de remplacement, regle {@code the_giant.annihilator}) attend dans le
 *     fourneau du garage.</li>
 *     <li><b>Bonus</b> : 3 bocaux a moelle (maintenir F) = musique {@code beauty_of_annihilation} ; machine secrete :
 *     se teleporter depuis les 3 pads dans la meme manche (a la place des singes de CoD), puis appuyer au pupitre du
 *     mainframe : la tete du robot tire, le tas de neige fond, la 6e machine apparait.</li>
 *     <li><b>0.2.3</b> : cachettes tirees au hasard a chaque partie (fusibles, bocaux, objets du Fly Trap), etapes cachees
 *     (3 relais 935 hors de la zone de jeu a abattre avant la carte d'acces, une grenade bonus), guidage (sous-etapes de
 *     l'objectif, repere de boussole, lignes de radio de Maxis).</li>
 *     <li><b>Acte IV</b> ({@link ActeQuatre}, D-057) : carte d'acces de Maxis (apres le Fly Trap et les relais), puis la porte de confinement lance l'intro
 *     du boss N-07 (mod {@code heralisz_n07}, monte-charge vers le hall du Secteur 7 sous l'usine), le combat, le retour
 *     par le teleporteur 935 ; la partie continue. Sans le mod du boss, la carte d'acces acheve la quete a la porte.</li>
 * </ul>
 */
final class QueteGiant {
    // genres d'interaction (code = genre << 8 | index)
    static final int FUSIBLE = 1, ARMOIRE = 2, PAD = 3, CONSOLE = 4, PIEGE = 5, BOWIE = 6, BOCAL = 7, CARTE = 8, ARME_FLY = 9,
            CONFINEMENT = 10;
    /** Duree de maintien de F (ticks) : lancer une liaison, la finir au mainframe, un bocal. */
    private static final int MAINTIEN_LIAISON = 20, MAINTIEN_BOCAL = 30;
    /** Charge du pad avant la teleportation (ticks). */
    private static final int TRANSPORT_TICKS = 30;
    /** Ouverture du rideau du Pack-a-Punch (ticks). */
    private static final int RIDEAU_TICKS = 60;

    private final PartieZombies partie;
    final PlanGiant plan;
    // acte I
    private final boolean[] fusibleIci;
    private int fusiblesPortes;
    private int masqueFusibles;
    // acte II
    private final int n;
    private final EtatPad[] etats;
    private final int[] ames, requises;
    private final long[] finEtat;
    private int lies;
    private int padsUtilisesManche;
    private long rideauOuvertureTick = -1;
    private boolean rideauOuvert;
    // pieges
    private final long[] piegeFin, piegeRecharge;
    // quete
    private boolean flyTrapActif, flyTrapFini;
    private final boolean[] flyTrapTouche;
    private final List<java.util.UUID> annihilatorPris = new ArrayList<>(4);
    private final boolean[] bocaux;
    private boolean musiqueJouee;
    private boolean carteAcces, bossLance, secretRevele;
    private long rayonTick = -1;
    private final String armeAnnihilator;
    // 0.2.3 : tirages de la partie (bocaux parmi les candidats, position de chaque objet du Fly Trap)
    private final List<PlanGiant.Objet> bocauxTires;
    private final List<PlanGiant.Objet> objetsFly;
    // 0.2.3 : etapes cachees (relais 935 a abattre avant la carte d'acces, grenade bonus)
    private final boolean[] cibleFaite;
    private final int relaisRequis;
    private int relaisAbattus;

    QueteGiant(PartieZombies partie, PlanGiant plan) {
        this.partie = partie;
        this.plan = plan;
        this.fusibleIci = new boolean[plan.cachettes.size()];
        this.n = plan.teleporteurs.size();
        this.etats = new EtatPad[n];
        this.ames = new int[n];
        this.requises = new int[n];
        this.finEtat = new long[n];
        this.piegeFin = new long[plan.pieges.size()];
        this.piegeRecharge = new long[plan.pieges.size()];
        this.flyTrapTouche = new boolean[plan.objetsFlyTrap.size()];
        java.util.Random r = new java.util.Random(partie.alea().nextLong());
        // bocaux : a_tirer parmi les candidats, au hasard a chaque partie (ancien format : tous)
        List<PlanGiant.Objet> b = new ArrayList<>(plan.bocaux);
        if (plan.bocauxATirer < b.size()) Collections.shuffle(b, r);
        this.bocauxTires = new ArrayList<>();
        for (int i = 0; i < Math.min(plan.bocauxATirer, b.size()); i++) {
            PlanGiant.Objet o = b.get(i);
            bocauxTires.add(o.index(i));
        }
        this.bocaux = new boolean[bocauxTires.size()];
        // objets du Fly Trap : un candidat par objet, jamais deux objets dans la meme case
        this.objetsFly = new ArrayList<>();
        java.util.Set<BlockPos> pris = new java.util.HashSet<>();
        for (int i = 0; i < plan.objetsFlyTrap.size(); i++) {
            List<PlanGiant.Objet> c = new ArrayList<>(i < plan.candidatsFlyTrap.size() ? plan.candidatsFlyTrap.get(i) : List.of(plan.objetsFlyTrap.get(i)));
            Collections.shuffle(c, r);
            PlanGiant.Objet choisi = plan.objetsFlyTrap.get(i);
            for (PlanGiant.Objet o : c) {
                if (pris.add(o.pos())) {
                    choisi = o;
                    break;
                }
            }
            objetsFly.add(choisi.index(i));
        }
        this.cibleFaite = new boolean[plan.ciblesCachees.size()];
        int nr = 0;
        for (PlanGiant.CibleCachee c : plan.ciblesCachees) if (c.tir()) nr++;
        this.relaisRequis = nr;
        this.armeAnnihilator = ConfigZombies.texte("the_giant", "annihilator", "arkaniaz:python");
    }

    // ------------------------------------------------------------------ installation
    /** Debut de partie (apres l'installation du decor) : tirage des fusibles, etats de depart des machines. */
    void installer(boolean courant) {
        long t0 = System.nanoTime();
        ZombiesDecor d = ZombiesApi.decor();
        Instance inst = partie.instance();
        // tirage : a_tirer cachettes au hasard (au plus le nombre de cachettes)
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < fusibleIci.length; i++) idx.add(i);
        Collections.shuffle(idx, new java.util.Random(partie.alea().nextLong()));
        int k = Math.min(plan.fusiblesATirer, idx.size());
        for (int i = 0; i < k; i++) fusibleIci[idx.get(i)] = true;
        int joueurs = Math.max(1, partie.joueursConnectes());
        for (int i = 0; i < n; i++) {
            etats[i] = courant ? EtatPad.ALIMENTE : EtatPad.ETEINT;
            requises[i] = Math.max(1, Math.round(plan.teleporteurs.get(i).ames() * (1 + 0.25F * (joueurs - 1))));
        }
        try {
            for (PlanGiant.Cachette c : plan.cachettes) {
                d.objetQuete(inst, c.pos(), c.face(), ZombiesDecor.OBJET_FUSIBLE, fusibleIci[c.index()] ? ZombiesDecor.OBJET_VISIBLE : ZombiesDecor.OBJET_CACHE, "");
            }
            if (plan.courant != null) d.armoire(inst, 0, false);
            for (int i = 0; i < n; i++) {
                d.pad(inst, plan.teleporteurs.get(i), etats[i], 0);
                d.cuveAmes(inst, plan.teleporteurs.get(i), 0);
            }
            d.mainframe(inst, courant, 0, 0);
            if (plan.rideau != null) d.rideauPap(inst, 0);
            if (plan.panneauFlyTrap != null) d.objetQuete(inst, plan.panneauFlyTrap.pos(), plan.panneauFlyTrap.face(), ZombiesDecor.OBJET_BOITIER,
                    ZombiesDecor.OBJET_VISIBLE, "");
            // les candidats non tires sont vides (une partie precedente a pu y laisser un objet)
            for (int i = 0; i < plan.candidatsFlyTrap.size(); i++) {
                for (PlanGiant.Objet o : plan.candidatsFlyTrap.get(i)) {
                    if (i < objetsFly.size() && o.pos().equals(objetsFly.get(i).pos())) continue;
                    d.objetQueteAncre(inst, o.pos(), o.face(), genreObjet(o.type()), ZombiesDecor.OBJET_CACHE, "", o.ancre());
                }
            }
            for (PlanGiant.Objet o : objetsFly) d.objetQueteAncre(inst, o.pos(), o.face(), genreObjet(o.type()), ZombiesDecor.OBJET_VISIBLE, "", o.ancre());
            // bocaux : seuls les tires sont poses (les autres cachettes restent vides)
            for (PlanGiant.Objet o : plan.bocaux) {
                boolean tire = false;
                for (PlanGiant.Objet t : bocauxTires) tire |= t.pos().equals(o.pos());
                d.bocal(inst, o.pos(), o.face(), tire);
            }
            if (plan.recompenseFlyTrap != null) d.objetQueteAncre(inst, plan.recompenseFlyTrap.pos(), plan.recompenseFlyTrap.face(), ZombiesDecor.OBJET_ARME,
                    ZombiesDecor.OBJET_CACHE, armeAnnihilator, plan.recompenseFlyTrap.ancre());
            if (plan.carteAcces != null) d.objetQuete(inst, plan.carteAcces.pos(), plan.carteAcces.face(), ZombiesDecor.OBJET_CARTE, ZombiesDecor.OBJET_VISIBLE, "");
            d.courantGiant(inst, courant);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor de The Giant en erreur", ex);
        }
        HeraliszMatchs.LOG.info("[zombies] The Giant : fusibles dans les cachettes {} sur {} ({} a poser), ames requises {}, {} pieges, Fly Trap {} objets en {}, "
                        + "bocaux {} sur {} candidats en {}, {} etape(s) cachee(s)", idsFusibles(), plan.cachettes.size(), plan.fusiblesRequis,
                java.util.Arrays.toString(requises), plan.pieges.size(), objetsFly.size(), positions(objetsFly), bocauxTires.size(), plan.bocaux.size(),
                positions(bocauxTires), plan.ciblesCachees.size());
        HeraliszMatchs.LOG.debug("[zombies] The Giant installe en {} us", (System.nanoTime() - t0) / 1000);
    }

    private static String positions(List<PlanGiant.Objet> l) {
        StringBuilder b = new StringBuilder();
        for (PlanGiant.Objet o : l) b.append(b.length() == 0 ? "" : " ").append(o.pos().toShortString());
        return b.toString();
    }

    private String idsFusibles() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < fusibleIci.length; i++) if (fusibleIci[i]) b.append(b.length() == 0 ? "" : ",").append(i);
        return b.toString();
    }

    private static int genreObjet(String type) {
        return switch (type) {
            case "singe" -> ZombiesDecor.OBJET_SINGE;
            case "nounours" -> ZombiesDecor.OBJET_NOUNOURS;
            default -> ZombiesDecor.OBJET_NOUNOURS;
        };
    }

    /** Vrai si la carte a l'armoire a fusibles (le levier v1 n'est alors pas utilise). */
    boolean aArmoire() {
        return plan.courant != null;
    }

    /** Fusibles poses dans l'armoire. */
    private int poses() {
        return Integer.bitCount(masqueFusibles);
    }

    // ------------------------------------------------------------------ courant
    /** Courant retabli (le levier vient d'etre abaisse) : passerelle, pads, mainframe, armoire. */
    void courant() {
        long t0 = System.nanoTime();
        ServerLevel l = partie.niveau();
        Instance inst = partie.instance();
        if (!plan.passerelleRelevee.isEmpty() || !plan.passerelleAbaissee.isEmpty()) {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (PlanGiant.BlocCarte b : plan.passerelleRelevee) l.setBlock(b.pos(), air, Block.UPDATE_CLIENTS);
            for (PlanGiant.BlocCarte b : plan.passerelleAbaissee) l.setBlock(b.pos(), b.etat(), Block.UPDATE_CLIENTS);
        }
        for (int i = 0; i < n; i++) if (etats[i] == EtatPad.ETEINT) etats[i] = ames[i] >= requises[i] ? EtatPad.CHARGE : EtatPad.ALIMENTE;
        try {
            if (plan.courant != null) ZombiesApi.decor().armoire(inst, masqueFusibles, true);
            ZombiesApi.decor().passerelle(inst, true);
            for (int i = 0; i < n; i++) ZombiesApi.decor().pad(inst, plan.teleporteurs.get(i), etats[i], 0);
            ZombiesApi.decor().mainframe(inst, true, lies, enLiaison());
            ZombiesApi.decor().courantGiant(inst, true);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor du courant v3", ex);
        }
        if (plan.courant != null) {
            Vec3 c = Vec3.atCenterOf(plan.courant.pos());
            partie.son(ZombiesApi.Sons.LEVIER_COURANT, c.x, c.y, c.z, 1.2f);
        }
        if (n > 0) radio("courant");
        HeraliszMatchs.LOG.debug("[zombies] courant v3 : passerelle {} + {} blocs, {} us", plan.passerelleRelevee.size(), plan.passerelleAbaissee.size(),
                (System.nanoTime() - t0) / 1000);
    }

    private int enLiaison() {
        int m = 0;
        for (int i = 0; i < n; i++) if (etats[i] == EtatPad.LIAISON) m |= 1 << i;
        return m;
    }

    // ------------------------------------------------------------------ tick
    void tick(long t) {
        Instance inst = partie.instance();
        // teleporteurs : fins de liaison, de transport, de recharge
        for (int i = 0; i < n; i++) {
            EtatPad e = etats[i];
            if (e == EtatPad.LIAISON) {
                long reste = finEtat[i] - t;
                if (reste <= 0) {
                    etats[i] = EtatPad.CHARGE;
                    padChange(i, 0);
                    partie.messageTous("zombies.giant.liaison_perdue");
                    partie.envoyerObjectifProgres();
                } else if (reste % 20 == 0) {
                    PlanGiant.Mainframe mf = plan.mainframe;
                    if (mf != null && mf.console() != null) {
                        Vec3 c = Vec3.atCenterOf(mf.console().pos());
                        partie.son(ZombiesApi.Sons.LIAISON_TIC, c.x, c.y, c.z, 0.8f);
                    }
                    Vec3 p = Vec3.atCenterOf(plan.teleporteurs.get(i).pad());
                    partie.son(ZombiesApi.Sons.LIAISON_TIC, p.x, p.y, p.z, 0.6f);
                    partie.envoyerObjectifProgres();
                }
            } else if (e == EtatPad.TRANSPORT && t >= finEtat[i]) {
                teleporter(i);
            } else if (e == EtatPad.RECHARGE && t >= finEtat[i]) {
                etats[i] = EtatPad.LIE;
                padChange(i, 0);
            }
        }
        // rideau du Pack-a-Punch : fin de l'ouverture
        if (rideauOuvertureTick >= 0 && t >= rideauOuvertureTick + RIDEAU_TICKS && !rideauOuvert) {
            rideauOuvert = true;
            try {
                ZombiesApi.decor().rideauPap(inst, 2);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.rideauPap", ex);
            }
            partie.ouvrirZonePublic(plan.zoneRideau);
        }
        // pieges allumes : toutes les 4 ticks (24 zombies au plus)
        if (t % 4 == 0) for (int i = 0; i < piegeFin.length; i++) if (piegeFin[i] > t) tickPiege(i, t);
        for (int i = 0; i < piegeFin.length; i++) {
            if (piegeFin[i] != 0 && piegeFin[i] <= t) {
                piegeFin[i] = 0;
                try {
                    ZombiesApi.decor().piege(inst, plan.pieges.get(i), false);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[zombies] decor.piege", ex);
                }
            }
        }
        // rayon de la tete du robot : la neige fond 2 s apres le tir
        if (rayonTick >= 0 && t >= rayonTick + 40) {
            rayonTick = -1;
            fondreNeige();
        }
        // relais 935 caches : une lueur bleue de temps en temps (visible de loin), tant qu'ils ne sont pas abattus
        if (t % 30 == 0 && relaisAbattus < relaisRequis) {
            for (PlanGiant.CibleCachee c : plan.ciblesCachees) {
                if (!c.tir() || cibleFaite[c.index()]) continue;
                Vec3 v = Vec3.atCenterOf(c.pos());
                if (partie.distanceJoueurDeboutPublic(v.x, v.y, v.z) < 96 * 96) effet(v, "relais");
            }
        }
        // grenade cachee : une grenade lancee dans la cible (cheminee...) donne le bonus une seule fois
        if (t % 5 == 0) for (PlanGiant.CibleCachee c : plan.ciblesCachees) if (!c.tir() && !cibleFaite[c.index()]) verifierGrenade(c);
        // bocaux : lueur verte de temps en temps (rien si personne n'est pres)
        if (t % 40 == 0 && !musiqueJouee) {
            for (int i = 0; i < bocaux.length; i++) {
                if (bocaux[i]) continue;
                Vec3 c = Vec3.atCenterOf(bocauxTires.get(i).pos());
                if (partie.distanceJoueurDeboutPublic(c.x, c.y, c.z) < 14 * 14) {
                    try {
                        ZombiesApi.decor().effet(inst, c, "bocal_lueur");
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ morts : ames
    /** Zombie tue (par n'importe qui) : son ame part dans la cuve la plus proche (courant mis, cuve non pleine, 10 blocs). */
    void mort(LivingEntity mort) {
        if (!partie.courant()) return;
        double x = mort.getX(), y = mort.getY(), z = mort.getZ();
        int meilleur = -1;
        double md = 10 * 10;
        for (int i = 0; i < n; i++) {
            PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
            if (tp.cuve() == null || ames[i] >= requises[i]) continue;
            BlockPos c = tp.cuve().pos();
            double dx = c.getX() + 0.5 - x, dy = c.getY() + 0.5 - y, dz = c.getZ() + 0.5 - z;
            if (Math.abs(dy) > 5) continue;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < md) {
                md = d;
                meilleur = i;
            }
        }
        if (meilleur < 0) return;
        int i = meilleur;
        PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
        ames[i]++;
        Instance inst = partie.instance();
        try {
            ZombiesApi.decor().ame(inst, new Vec3(x, y + 1.0, z), tp);
            ZombiesApi.decor().cuveAmes(inst, tp, ames[i] / (float) requises[i]);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.ame", ex);
        }
        if (ames[i] >= requises[i]) {
            Vec3 c = Vec3.atCenterOf(tp.cuve().pos());
            partie.son(ZombiesApi.Sons.CUVE_PLEINE, c.x, c.y, c.z, 1.1f);
            if (etats[i] == EtatPad.ALIMENTE) {
                etats[i] = EtatPad.CHARGE;
                padChange(i, 0);
            }
            partie.annonceChat(Component.translatable("zombies.giant.cuve_pleine", String.valueOf(tp.lettre())).withStyle(s -> s.withColor(0x7DFFC8)));
            HeraliszMatchs.LOG.info("[zombies] The Giant : cuve {} pleine ({} ames)", tp.lettre(), ames[i]);
        }
        partie.envoyerObjectifProgres();
    }

    // ------------------------------------------------------------------ interaction
    /**
     * Cible d'interaction de The Giant devant le joueur (si plus proche que {@code meilleur}) : rend la nouvelle meilleure
     * distance (penalisee comme dans {@link PartieZombies}) et place {@code j.cible} / {@code j.cibleIndex}.
     */
    double cible(ServerPlayer p, JoueurZombies j, Vec3 oeil, Vec3 regard, double meilleur) {
        boolean courant = partie.courant();
        // fusibles (eclat dans la cachette)
        for (PlanGiant.Cachette c : plan.cachettes) {
            if (!fusibleIci[c.index()]) continue;
            meilleur = essai(j, oeil, regard, Vec3.atCenterOf(c.pos()), 2.4, 0.6, meilleur, FUSIBLE, c.index());
        }
        // armoire du courant
        if (plan.courant != null && !courant) meilleur = essai(j, oeil, regard, Vec3.atCenterOf(plan.courant.pos()).add(0, 0.5, 0), 2.8, 0.2, meilleur, ARMOIRE, 0);
        if (courant) {
            // pads : debout dessus, ou en le regardant de pres
            for (int i = 0; i < n; i++) {
                PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
                EtatPad e = etats[i];
                if (e == EtatPad.ETEINT) continue;
                if (tp.emprise().inflate(0.3, 1, 0.3).contains(p.position())) {
                    double d = 0.5;
                    if (d < meilleur) {
                        meilleur = d;
                        j.cible = JoueurZombies.Cible.GIANT;
                        j.cibleIndex = code(PAD, i);
                    }
                } else {
                    meilleur = essai(j, oeil, regard, Vec3.atCenterOf(tp.pad()).add(0, 0.8, 0), 3.4, 0.6, meilleur, PAD, i);
                }
            }
            // pupitre du mainframe
            PlanGiant.Mainframe mf = plan.mainframe;
            if (mf != null && mf.console() != null) meilleur = essai(j, oeil, regard, Vec3.atCenterOf(mf.console().pos()).add(0, 0.4, 0), 2.6, 0.3, meilleur, CONSOLE, 0);
            // pieges
            for (PlanGiant.Piege pg : plan.pieges) {
                for (PlanGiant.Machine pan : pg.panneaux()) meilleur = essai(j, oeil, regard, Vec3.atCenterOf(pan.pos()), 2.2, 0.4, meilleur, PIEGE, pg.index());
            }
        }
        if (plan.bowie != null && !j.bowie) meilleur = essai(j, oeil, regard, Vec3.atCenterOf(plan.bowie.pos()), 2.0, 0.4, meilleur, BOWIE, 0);
        if (!musiqueJouee) {
            for (int i = 0; i < bocaux.length; i++) {
                if (!bocaux[i]) meilleur = essai(j, oeil, regard, Vec3.atCenterOf(bocauxTires.get(i).pos()).add(0, 0.3, 0), 2.2, 0.7, meilleur, BOCAL, i);
            }
        }
        if (flyTrapFini && plan.recompenseFlyTrap != null && !annihilatorPris.contains(j.id)) {
            Vec3 ar = plan.recompenseFlyTrap.ancre() != null ? plan.recompenseFlyTrap.ancre() : Vec3.atCenterOf(plan.recompenseFlyTrap.pos());
            meilleur = essai(j, oeil, regard, ar, 2.8, 0.3, meilleur, ARME_FLY, 0);
        }
        if (carteDisponible() && !carteAcces) meilleur = essai(j, oeil, regard, Vec3.atCenterOf(plan.carteAcces.pos()), 2.4, 0.5, meilleur, CARTE, 0);
        if (carteAcces && plan.porteConfinement != null && !bossLance) {
            meilleur = essai(j, oeil, regard, Vec3.atCenterOf(plan.porteConfinement.pos()).add(0, 1, 0), 3.0, 0.3, meilleur, CONFINEMENT, 0);
        }
        return meilleur;
    }

    private static double essai(JoueurZombies j, Vec3 oeil, Vec3 regard, Vec3 c, double portee, double vise, double meilleur, int genre, int index) {
        double d = c.distanceToSqr(oeil);
        if (d > portee * portee || !PartieZombies.regardePublic(oeil, regard, c, vise)) return meilleur;
        d = PartieZombies.penalitePublic(d, oeil, regard, c);
        if (d >= meilleur) return meilleur;
        j.cible = JoueurZombies.Cible.GIANT;
        j.cibleIndex = code(genre, index);
        return d;
    }

    static int code(int genre, int index) {
        return genre << 8 | (index & 255);
    }

    static int genre(int code) {
        return code >> 8;
    }

    static int index(int code) {
        return code & 255;
    }

    /** Ticks de maintien de F pour agir (0 = un simple appui). */
    int maintien(int code) {
        int g = genre(code), i = index(code);
        return switch (g) {
            case PAD -> i < n && etats[i] == EtatPad.CHARGE ? MAINTIEN_LIAISON : 0;
            case CONSOLE -> enLiaison() != 0 ? MAINTIEN_LIAISON : 0;
            case BOCAL -> MAINTIEN_BOCAL;
            default -> 0;
        };
    }

    /** Invite affichee pour cette cible : texte, cout (0 = aucun), abordable. */
    record Invite(@Nullable Component texte, int cout, boolean abordable) {
    }

    Invite invite(ServerPlayer p, JoueurZombies j, int code) {
        int g = genre(code), i = index(code);
        return switch (g) {
            case FUSIBLE -> new Invite(Component.translatable("zombies.giant.invite.fusible"), 0, true);
            case ARMOIRE -> {
                if (poses() >= plan.fusiblesRequis) yield new Invite(Component.translatable("zombies.giant.invite.levier"), 0, true);
                if (fusiblesPortes > 0) yield new Invite(Component.translatable("zombies.giant.invite.poser_fusible", poses(), plan.fusiblesRequis), 0, true);
                yield new Invite(Component.translatable("zombies.giant.invite.manque_fusibles", poses(), plan.fusiblesRequis), 0, false);
            }
            case PAD -> {
                PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
                String l = String.valueOf(tp.lettre());
                yield switch (etats[i]) {
                    case CHARGE -> new Invite(Component.translatable("zombies.giant.invite.lier", l), 0, true);
                    case LIAISON -> new Invite(Component.translatable("zombies.giant.invite.courir", l), 0, false);
                    case LIE -> new Invite(Component.translatable("zombies.giant.invite.teleporter", l), tp.cout(), j.points >= tp.cout());
                    case TRANSPORT -> new Invite(Component.translatable("zombies.giant.invite.transport"), 0, false);
                    case RECHARGE -> new Invite(Component.translatable("zombies.giant.invite.recharge"), 0, false);
                    default -> new Invite(Component.translatable("zombies.giant.invite.ames", l), 0, false);
                };
            }
            case CONSOLE -> {
                if (enLiaison() != 0) yield new Invite(Component.translatable("zombies.giant.invite.console_lier"), 0, true);
                if (secretPret()) yield new Invite(Component.translatable("zombies.giant.invite.secret"), 0, true);
                yield new Invite(Component.translatable("zombies.giant.invite.console", Integer.bitCount(lies)), 0, false);
            }
            case PIEGE -> {
                PlanGiant.Piege pg = plan.pieges.get(i);
                long t = partie.ticks();
                if (piegeFin[i] > t) yield new Invite(Component.translatable("zombies.giant.invite.piege_actif"), 0, false);
                if (piegeRecharge[i] > t) yield new Invite(Component.translatable("zombies.giant.invite.piege_recharge"), 0, false);
                yield new Invite(Component.translatable("zombies.giant.invite.piege"), pg.cout(), j.points >= pg.cout());
            }
            case BOWIE -> new Invite(Component.translatable("zombies.giant.invite.bowie"), plan.coutBowie, j.points >= plan.coutBowie);
            case BOCAL -> new Invite(Component.translatable("zombies.giant.invite.bocal"), 0, true);
            case ARME_FLY -> new Invite(Component.translatable("zombies.giant.invite.annihilator"), 0, true);
            case CARTE -> new Invite(Component.translatable("zombies.giant.invite.carte"), 0, true);
            case CONFINEMENT -> new Invite(Component.translatable("zombies.giant.invite.confinement"), 0, true);
            default -> new Invite(null, 0, false);
        };
    }

    /** Action sur la cible (appui, ou maintien termine). */
    void agir(ServerPlayer p, JoueurZombies j, int code) {
        int g = genre(code), i = index(code);
        Instance inst = partie.instance();
        switch (g) {
            case FUSIBLE -> {
                if (i >= fusibleIci.length || !fusibleIci[i]) return;
                fusibleIci[i] = false;
                fusiblesPortes++;
                PlanGiant.Cachette c = plan.cachettes.get(i);
                decorObjet(c.pos(), c.face(), ZombiesDecor.OBJET_FUSIBLE, ZombiesDecor.OBJET_PRIS);
                Vec3 v = Vec3.atCenterOf(c.pos());
                partie.son(ZombiesApi.Sons.FUSIBLE_RAMASSE, v.x, v.y, v.z, 1f);
                partie.annonceChat(Component.translatable("zombies.giant.fusible_trouve", j.nom, poses() + fusiblesPortes, plan.fusiblesRequis)
                        .withStyle(s -> s.withColor(0xFFD27A)));
                partie.envoyerObjectifProgres();
                partie.hudSale();
                // 0.2.3 (guidage) : tous les fusibles trouves -> Maxis indique l'armoire
                if (poses() + fusiblesPortes == plan.fusiblesRequis) radio("fusibles");
                HeraliszMatchs.LOG.info("[zombies] The Giant : {} ramasse le fusible de la cachette {}", j.nom, i);
            }
            case ARMOIRE -> {
                if (partie.courant()) return;
                if (poses() >= plan.fusiblesRequis) {
                    partie.retablirCourantPublic(p);
                    return;
                }
                if (fusiblesPortes <= 0) {
                    partie.refusPublic(p, "zombies.giant.refus.fusibles");
                    return;
                }
                fusiblesPortes--;
                for (int b = 0; b < 3; b++) {
                    if ((masqueFusibles & (1 << b)) == 0) {
                        masqueFusibles |= 1 << b;
                        break;
                    }
                }
                try {
                    ZombiesApi.decor().armoire(inst, masqueFusibles, false);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[zombies] decor.armoire", ex);
                }
                Vec3 v = Vec3.atCenterOf(plan.courant.pos());
                partie.son(ZombiesApi.Sons.FUSIBLE, v.x, v.y, v.z, 1f);
                if (poses() >= plan.fusiblesRequis) partie.objectifRempliPublic("fusibles");
                else partie.envoyerObjectifProgres();
                partie.hudSale();
                HeraliszMatchs.LOG.info("[zombies] The Giant : {} pose un fusible ({}/{})", j.nom, poses(), plan.fusiblesRequis);
            }
            case PAD -> agirPad(p, j, i);
            case CONSOLE -> {
                if (enLiaison() != 0) lier(p, j);
                else if (secretPret()) tirerTeteRobot(p);
            }
            case PIEGE -> allumerPiege(p, j, i);
            case BOWIE -> {
                if (j.bowie || !partie.payerPublic(p, j, plan.coutBowie, Component.translatable("zombies.giant.achat.bowie"))) return;
                j.bowie = true;
                partie.donnerBowie(p);
                Vec3 v = Vec3.atCenterOf(plan.bowie.pos());
                partie.son(ZombiesApi.Sons.BOWIE, v.x, v.y, v.z, 1f);
                effet(v, "bowie");
            }
            case BOCAL -> {
                if (i >= bocaux.length || bocaux[i]) return;
                bocaux[i] = true;
                Vec3 v = Vec3.atCenterOf(bocauxTires.get(i).pos());
                partie.son(ZombiesApi.Sons.BOCAL, v.x, v.y, v.z, 1f);
                effet(v, "bocal");
                boolean tous = true;
                for (boolean b : bocaux) tous &= b;
                if (tous && !musiqueJouee && bocaux.length > 0) {
                    musiqueJouee = true;
                    partie.musiqueAccentPublic(MomentMusique.BEAUTY_OF_ANNIHILATION);
                    HeraliszMatchs.LOG.info("[zombies] The Giant : les {} bocaux sont actives (musique)", bocaux.length);
                }
            }
            case ARME_FLY -> {
                if (!flyTrapFini || annihilatorPris.contains(j.id)) return;
                annihilatorPris.add(j.id);
                donnerAnnihilator(p);
            }
            case CARTE -> {
                if (carteAcces || !carteDisponible()) return;
                carteAcces = true;
                decorObjet(plan.carteAcces.pos(), plan.carteAcces.face(), ZombiesDecor.OBJET_CARTE, ZombiesDecor.OBJET_PRIS);
                partie.annonceChat(Component.translatable("zombies.giant.carte", j.nom).withStyle(s -> s.withColor(0xFFB327)));
                Vec3 v = Vec3.atCenterOf(plan.carteAcces.pos());
                partie.son(ZombiesApi.Sons.EASTER_EGG_ETAPE, v.x, v.y, v.z, 1f);
                partie.envoyerObjectifProgres();
                radio("carte");
            }
            case CONFINEMENT -> lancerBoss(p);
            default -> {
            }
        }
        for (JoueurZombies o : partie.joueursZ()) o.inviteSale();
    }

    // ------------------------------------------------------------------ teleporteurs
    private void agirPad(ServerPlayer p, JoueurZombies j, int i) {
        if (i >= n) return;
        PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
        switch (etats[i]) {
            case CHARGE -> {
                etats[i] = EtatPad.LIAISON;
                finEtat[i] = partie.ticks() + tp.liaisonTicks();
                padChange(i, tp.liaisonTicks());
                Vec3 c = Vec3.atCenterOf(tp.pad());
                partie.son(ZombiesApi.Sons.LIAISON_DEBUT, c.x, c.y, c.z, 1.2f);
                partie.annonceChat(Component.translatable("zombies.giant.liaison", String.valueOf(tp.lettre()), tp.liaisonTicks() / 20)
                        .withStyle(s -> s.withColor(0x9FD8FF)));
                partie.envoyerObjectifProgres();
                HeraliszMatchs.LOG.info("[zombies] The Giant : {} lance la liaison du pad {}", j.nom, tp.lettre());
            }
            case LIE -> {
                if (!tp.emprise().inflate(0.3, 1, 0.3).contains(p.position())) {
                    partie.refusPublic(p, "zombies.giant.refus.sur_le_pad");
                    return;
                }
                if (!partie.payerPublic(p, j, tp.cout(), Component.translatable("zombies.giant.achat.teleporteur", String.valueOf(tp.lettre())))) return;
                etats[i] = EtatPad.TRANSPORT;
                finEtat[i] = partie.ticks() + TRANSPORT_TICKS;
                padChange(i, TRANSPORT_TICKS);
                Vec3 c = Vec3.atCenterOf(tp.pad());
                partie.son(ZombiesApi.Sons.TELEPORTEUR, c.x, c.y, c.z, 1.3f);
            }
            default -> {
            }
        }
    }

    /** Le joueur maintient F au pupitre du mainframe : tous les pads en liaison sont lies. */
    private void lier(ServerPlayer p, JoueurZombies j) {
        int m = enLiaison();
        if (m == 0) return;
        for (int i = 0; i < n; i++) {
            if ((m & (1 << i)) == 0) continue;
            etats[i] = EtatPad.LIE;
            lies |= 1 << i;
            padChange(i, 0);
            HeraliszMatchs.LOG.info("[zombies] The Giant : pad {} lie par {} ({} / {})", plan.teleporteurs.get(i).lettre(), j.nom, Integer.bitCount(lies), n);
        }
        PlanGiant.Mainframe mf = plan.mainframe;
        Instance inst = partie.instance();
        try {
            ZombiesApi.decor().mainframe(inst, true, lies, enLiaison());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.mainframe", ex);
        }
        if (mf != null && mf.console() != null) {
            Vec3 c = Vec3.atCenterOf(mf.console().pos());
            partie.son(ZombiesApi.Sons.LIAISON_OK, c.x, c.y, c.z, 1.2f);
        }
        // bonus gratuit devant le mainframe, comme dans CoD
        if (mf != null) {
            TypeBonus[] v = TypeBonus.values();
            partie.poserBonusPublic(v[partie.alea().nextInt(v.length)], mf.bonusGratuit().x, mf.bonusGratuit().y, mf.bonusGratuit().z);
        }
        partie.annonceChat(Component.translatable("zombies.giant.lie", Integer.bitCount(lies), n).withStyle(s -> s.withColor(0x9FD8FF)));
        if (Integer.bitCount(lies) >= n && n > 0) tousLies();
        else partie.envoyerObjectifProgres();
    }

    /** Les 3 pads sont lies : la tour lance des eclairs, le rideau du Pack-a-Punch monte. */
    private void tousLies() {
        Instance inst = partie.instance();
        rideauOuvertureTick = partie.ticks();
        try {
            if (plan.rideau != null) ZombiesApi.decor().rideauPap(inst, 1);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.rideauPap", ex);
        }
        PlanGiant.Mainframe mf = plan.mainframe;
        if (mf != null && mf.tour() != null) {
            Vec3 c = Vec3.atCenterOf(mf.tour().pos()).add(0, 6, 0);
            partie.son(ZombiesApi.Sons.TOUR_ECLAIRS, c.x, c.y, c.z, 2f);
        }
        if (plan.rideau != null) {
            Vec3 c = PlanZombiesOutils.centre(plan.rideau);
            partie.son(ZombiesApi.Sons.RIDEAU, c.x, c.y, c.z, 1.3f);
        }
        partie.objectifRempliPublic("teleporteurs");
        radio("pap");
        HeraliszMatchs.LOG.info("[zombies] The Giant : 3 teleporteurs lies a la manche {} : le Pack-a-Punch s'ouvre", partie.manche());
    }

    /** Fin de la charge du pad : les joueurs debout dessus arrivent au mainframe. */
    private void teleporter(int i) {
        PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
        PlanGiant.Mainframe mf = plan.mainframe;
        ServerLevel l = partie.niveau();
        AABB pad = tp.emprise().inflate(0.3, 1, 0.3);
        // zombies sur le pad : morts (comme dans CoD)
        List<ZombieSuivi> zs = partie.zombiesSuivis();
        for (int k = zs.size() - 1; k >= 0; k--) {
            if (k >= zs.size()) continue;
            ZombieSuivi s = zs.get(k);
            if (s.mob.isAlive() && pad.contains(s.mob.position())) s.mob.kill();
        }
        int partis = 0;
        Vec3 arrivee = mf == null ? null : Vec3.atBottomCenterOf(mf.pad());
        if (arrivee != null) {
            List<ServerPlayer> en = partie.enLigneCourant();
            for (int k = 0; k < en.size(); k++) {
                ServerPlayer p = en.get(k);
                JoueurZombies j = partie.joueurZ(p.getUUID());
                if (j == null || !j.debout() || p.level() != l || !pad.contains(p.position())) continue;
                double a = partis * 1.7, r = partis == 0 ? 0 : 1.2;
                p.teleportTo(l, arrivee.x + Math.cos(a) * r, arrivee.y + 0.05, arrivee.z + Math.sin(a) * r, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
                MatchsNet.versJoueur(p, PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.TELEPORT, p.getUUID(), 0, 0, ""));
                partis++;
            }
        }
        padsUtilisesManche |= 1 << i;
        etats[i] = EtatPad.RECHARGE;
        int recharge = ConfigZombies.entier("the_giant", "recharge_teleporteur_s", 30) * 20;
        finEtat[i] = partie.ticks() + recharge;
        padChange(i, recharge);
        if (arrivee != null) {
            try {
                ZombiesApi.decor().arrivee(partie.instance(), arrivee);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.arrivee", ex);
            }
            partie.son(ZombiesApi.Sons.TELEPORTEUR_ARRIVEE, arrivee.x, arrivee.y + 1, arrivee.z, 1.3f);
            // parfois un bonus, apres la manche 25 parfois des chiens
            if (partie.alea().nextFloat() < ConfigZombies.reel("the_giant", "chance_bonus_teleporteur", 0.12)) {
                TypeBonus[] v = TypeBonus.values();
                partie.poserBonusPublic(v[partie.alea().nextInt(v.length)], arrivee.x + 2, arrivee.y, arrivee.z);
            }
            if (partie.manche() > 25 && partie.alea().nextFloat() < ConfigZombies.reel("the_giant", "chance_chiens_teleporteur", 0.25)) {
                int nc = 2 + partie.alea().nextInt(3);
                for (int c = 0; c < nc; c++) {
                    double a = partie.alea().nextDouble() * Math.PI * 2;
                    partie.apparaitreChien(new Vec3(arrivee.x + Math.cos(a) * 3, arrivee.y, arrivee.z + Math.sin(a) * 3));
                }
            }
        }
        HeraliszMatchs.LOG.info("[zombies] The Giant : teleportation depuis {} ({} joueur(s))", tp.lettre(), partis);
    }

    private void padChange(int i, int duree) {
        try {
            ZombiesApi.decor().pad(partie.instance(), plan.teleporteurs.get(i), etats[i], duree);
            ZombiesApi.decor().mainframe(partie.instance(), partie.courant(), lies, enLiaison());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.pad", ex);
        }
        for (JoueurZombies o : partie.joueursZ()) o.inviteSale();
    }

    /** Debut de manche : remise a zero des pads utilises (machine secrete). */
    void nouvelleManche() {
        padsUtilisesManche = 0;
    }

    // ------------------------------------------------------------------ machine secrete (tete du robot)
    private boolean secretPret() {
        return !secretRevele && rayonTick < 0 && n > 0 && Integer.bitCount(lies) >= n && Integer.bitCount(padsUtilisesManche) >= n
                && !plan.tasDeNeige.isEmpty();
    }

    private void tirerTeteRobot(ServerPlayer p) {
        rayonTick = partie.ticks();
        Vec3 vers = plan.tasDeNeigeVolume != null ? PlanZombiesOutils.centre(plan.tasDeNeigeVolume) : Vec3.atCenterOf(plan.tasDeNeige.get(0).pos());
        Vec3 depuis = plan.teteRobot != null ? plan.teteRobot : vers.add(-30, 20, 15);
        try {
            ZombiesApi.decor().rayonRobot(partie.instance(), depuis, vers);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.rayonRobot", ex);
        }
        partie.son(ZombiesApi.Sons.TOUR_ECLAIRS, vers.x, vers.y, vers.z, 2f);
        partie.annonceChat(Component.translatable("zombies.giant.tete_robot").withStyle(s -> s.withColor(0x9FD8FF)));
        HeraliszMatchs.LOG.info("[zombies] The Giant : {} active la tete du robot (machine secrete)", p.getGameProfile().getName());
    }

    private void fondreNeige() {
        ServerLevel l = partie.niveau();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (PlanGiant.BlocCarte b : plan.tasDeNeige) l.setBlock(b.pos(), air, Block.UPDATE_CLIENTS);
        secretRevele = true;
        Vec3 c = plan.tasDeNeigeVolume != null ? PlanZombiesOutils.centre(plan.tasDeNeigeVolume) : Vec3.atCenterOf(plan.tasDeNeige.get(0).pos());
        effet(c, "neige");
        partie.revelerAtoutSecret();
    }

    // ------------------------------------------------------------------ pieges
    private void allumerPiege(ServerPlayer p, JoueurZombies j, int i) {
        if (i >= piegeFin.length) return;
        PlanGiant.Piege pg = plan.pieges.get(i);
        long t = partie.ticks();
        if (!partie.courant()) {
            partie.refusPublic(p, "zombies.refus.courant");
            return;
        }
        if (piegeFin[i] > t || piegeRecharge[i] > t) {
            partie.refusPublic(p, "zombies.giant.refus.piege");
            return;
        }
        if (!partie.payerPublic(p, j, pg.cout(), Component.translatable("zombies.giant.achat.piege"))) return;
        piegeFin[i] = t + pg.dureeTicks();
        piegeRecharge[i] = t + pg.dureeTicks() + pg.rechargeTicks();
        try {
            ZombiesApi.decor().piege(partie.instance(), pg, true);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.piege", ex);
        }
        Vec3 c = pg.volume().getCenter();
        partie.son(ZombiesApi.Sons.PIEGE, c.x, c.y, c.z, 1.3f);
        HeraliszMatchs.LOG.info("[zombies] The Giant : {} allume le piege {} ({} s)", j.nom, pg.id(), pg.dureeTicks() / 20);
    }

    /** Piege allume : zombies du volume tues (sans points), joueurs touches durement (un joueur sans Blindage tombe vite). */
    private void tickPiege(int i, long t) {
        PlanGiant.Piege pg = plan.pieges.get(i);
        AABB v = pg.volume().inflate(0.1, 0.5, 0.1);
        Vec3 tue = null;
        List<ZombieSuivi> zs = partie.zombiesSuivis();
        for (int k = zs.size() - 1; k >= 0; k--) {
            if (k >= zs.size()) continue;
            ZombieSuivi s = zs.get(k);
            if (!s.mob.isAlive() || !v.intersects(s.mob.getBoundingBox())) continue;
            tue = s.mob.position();
            s.mob.kill();
        }
        if (tue != null) partie.son(ZombiesApi.Sons.PIEGE_MORT, tue.x, tue.y + 1, tue.z, 1f);
        if (t % 12 == 0) {
            List<ServerPlayer> en = partie.enLigneCourant();
            for (int k = 0; k < en.size(); k++) {
                ServerPlayer p = en.get(k);
                JoueurZombies j = partie.joueurZ(p.getUUID());
                if (j == null || !j.debout() || !v.intersects(p.getBoundingBox())) continue;
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().lightningBolt(), (float) ConfigZombies.reel("the_giant", "degats_piege", 10));
            }
        }
        if (t % 20 == 0) {
            Vec3 c = pg.volume().getCenter();
            partie.son(ZombiesApi.Sons.PIEGE, c.x, c.y, c.z, 0.9f);
        }
        try {
            ZombiesApi.decor().piegeTick(partie.instance(), pg, tue);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.piegeTick", ex);
        }
    }

    // ------------------------------------------------------------------ Fly Trap (tirs)
    /** Une balle touche un bloc : boitier du Fly Trap (arme amelioree), puis les 3 objets caches. */
    void impact(BlockPos pos, @Nullable Entity tireur) {
        if (relaisAbattus < relaisRequis) impactRelais(pos, tireur);
        if (flyTrapFini || plan.panneauFlyTrap == null) return;
        if (!flyTrapActif) {
            if (!proche(pos, plan.panneauFlyTrap.pos())) return;
            if (!(tireur instanceof ServerPlayer sp) || !ZombiesApi.estAmelioree(sp.getMainHandItem())) return;
            flyTrapActif = true;
            decorObjet(plan.panneauFlyTrap.pos(), plan.panneauFlyTrap.face(), ZombiesDecor.OBJET_BOITIER, ZombiesDecor.OBJET_ACTIF);
            for (PlanGiant.Objet o : objetsFly) decorObjet(o, genreObjet(o.type()), ZombiesDecor.OBJET_ACTIF);
            Vec3 v = Vec3.atCenterOf(pos);
            partie.son(ZombiesApi.Sons.EASTER_EGG_ETAPE, v.x, v.y, v.z, 1.2f);
            effet(v, "fly_trap");
            partie.annonceChat(Component.translatable("zombies.giant.fly_trap").withStyle(s -> s.withColor(0xE59BFF)));
            partie.envoyerObjectifProgres();
            HeraliszMatchs.LOG.info("[zombies] The Giant : Fly Trap active par {}", sp.getGameProfile().getName());
            return;
        }
        for (PlanGiant.Objet o : objetsFly) {
            if (flyTrapTouche[o.index()] || !proche(pos, o.pos())) continue;
            flyTrapTouche[o.index()] = true;
            decorObjet(o, genreObjet(o.type()), ZombiesDecor.OBJET_PRIS);
            Vec3 v = Vec3.atCenterOf(o.pos());
            partie.son(ZombiesApi.Sons.QUETE_OBJET, v.x, v.y, v.z, 1.2f);
            effet(v, "objet_touche");
            int nb = 0;
            for (boolean b : flyTrapTouche) if (b) nb++;
            HeraliszMatchs.LOG.info("[zombies] The Giant : objet du Fly Trap {} touche ({}/{})", o.type(), nb, flyTrapTouche.length);
            if (nb >= flyTrapTouche.length) finirFlyTrap();
            else partie.envoyerObjectifProgres();
            return;
        }
    }

    private static boolean proche(BlockPos touche, BlockPos cible) {
        return Math.abs(touche.getX() - cible.getX()) <= 1 && Math.abs(touche.getY() - cible.getY()) <= 1 && Math.abs(touche.getZ() - cible.getZ()) <= 1;
    }

    private void finirFlyTrap() {
        flyTrapFini = true;
        decorObjet(plan.panneauFlyTrap.pos(), plan.panneauFlyTrap.face(), ZombiesDecor.OBJET_BOITIER, ZombiesDecor.OBJET_VISIBLE);
        if (plan.recompenseFlyTrap != null) {
            decorObjet(plan.recompenseFlyTrap, ZombiesDecor.OBJET_ARME, ZombiesDecor.OBJET_ACTIF);
            effet(Vec3.atCenterOf(plan.recompenseFlyTrap.pos()), "phare");
        }
        effet(Vec3.atCenterOf(plan.panneauFlyTrap.pos()), "phare");
        partie.sonTousPublic(ZombiesApi.Sons.PHARE);
        partie.annonceChat(Component.translatable("zombies.giant.phare").withStyle(s -> s.withColor(0xE59BFF)));
        partie.objectifRempliPublic("phare");
        if (relaisRequis > relaisAbattus) radio("relais_indice");
        HeraliszMatchs.LOG.info("[zombies] The Giant : Fly Trap termine a la manche {}", partie.manche());
    }

    private void donnerAnnihilator(ServerPlayer p) {
        String g = armeAnnihilator;
        if (!Tacz.existe(g)) {
            partie.refusPublic(p, "zombies.refus.arme");
            HeraliszMatchs.LOG.warn("[zombies] The Giant : arme de l'Annihilator {} introuvable (regle the_giant.annihilator)", g);
            return;
        }
        partie.donnerTirage(p, new TableBoite.Tirage(g, Map.of(), Rarete.MYTHIQUE, 1));
        ItemStack s = p.getMainHandItem();
        if (g.equals(Tacz.gunId(s))) {
            s.setHoverName(Component.translatable("zombies.giant.annihilator").withStyle(st -> st.withColor(Rarete.MYTHIQUE.couleur).withItalic(false)));
            p.getInventory().setChanged();
        }
        Vec3 v = Vec3.atCenterOf(plan.recompenseFlyTrap.pos());
        partie.son(ZombiesApi.Sons.ARME_PRISE, v.x, v.y, v.z, 1f);
        HeraliszMatchs.LOG.info("[zombies] The Giant : {} prend l'Annihilator ({})", p.getGameProfile().getName(), g);
    }

    // ------------------------------------------------------------------ etapes cachees (0.2.3)
    /** La carte d'acces de Maxis n'est prenable qu'apres le Fly Trap ET les relais 935 caches (s'il y en a). */
    boolean carteDisponible() {
        return plan.carteAcces != null && flyTrapFini && relaisAbattus >= relaisRequis;
    }

    /** Une balle touche un bloc : relais 935 cache (hors de la zone de jeu) a 1 bloc pres, n'importe quelle arme. */
    private void impactRelais(BlockPos pos, @Nullable Entity tireur) {
        for (PlanGiant.CibleCachee c : plan.ciblesCachees) {
            if (!c.tir() || cibleFaite[c.index()] || !proche(pos, c.pos())) continue;
            cibleFaite[c.index()] = true;
            relaisAbattus++;
            Vec3 v = Vec3.atCenterOf(c.pos());
            effet(v, "relais_touche");
            partie.son(ZombiesApi.Sons.QUETE_OBJET, v.x, v.y, v.z, 2f);
            String nom = tireur instanceof ServerPlayer sp ? sp.getGameProfile().getName() : "?";
            HeraliszMatchs.LOG.info("[zombies] The Giant : relais 935 {} abattu par {} ({}/{})", c.id(), nom, relaisAbattus, relaisRequis);
            if (relaisAbattus >= relaisRequis) {
                radio("relais_fin");
                partie.sonTousPublic(ZombiesApi.Sons.EASTER_EGG_ETAPE);
            } else {
                radio("relais." + Math.min(3, relaisAbattus));
            }
            partie.envoyerObjectifProgres();
            for (JoueurZombies o : partie.joueursZ()) o.inviteSale();
            return;
        }
    }

    /**
     * Cible « grenade » : une grenade (entite dont l'identifiant contient « grenade ») dans 1,5 bloc de la cible donne une
     * fois le bonus : une bouteille d'atout gratuite (Mule Kick exclu) au lanceur s'il lui en manque une, sinon un Max Ammo
     * pose a {@code recompense_pos} ou pres du lanceur. Recherche dans une petite boite, toutes les 5 ticks.
     */
    private void verifierGrenade(PlanGiant.CibleCachee c) {
        ServerLevel l = partie.niveau();
        if (l == null) return;
        AABB zone = new AABB(c.pos()).inflate(1.5);
        List<Entity> es = l.getEntities((Entity) null, zone, e -> {
            if (e instanceof LivingEntity) return false;
            net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            return id != null && id.getPath().contains("grenade");
        });
        if (es.isEmpty()) return;
        cibleFaite[c.index()] = true;
        Entity g = es.get(0);
        ServerPlayer lanceur = g instanceof net.minecraft.world.entity.projectile.Projectile pr && pr.getOwner() instanceof ServerPlayer sp ? sp : null;
        Vec3 v = Vec3.atCenterOf(c.pos());
        effet(v, "grenade_secret");
        partie.son(ZombiesApi.Sons.EASTER_EGG_ETAPE, v.x, v.y, v.z, 1.5f);
        boolean atout = lanceur != null && partie.atoutGratuit(lanceur);
        if (!atout) {
            Vec3 b = c.recompense() != null ? Vec3.atBottomCenterOf(c.recompense()) : lanceur != null ? lanceur.position() : v;
            partie.poserBonusPublic(TypeBonus.MUNITIONS_MAX, b.x, b.y, b.z);
        }
        radio(atout ? "grenade_atout" : "grenade_munitions");
        HeraliszMatchs.LOG.info("[zombies] The Giant : grenade dans la cible cachee {} ({}) : {}", c.id(),
                lanceur == null ? "?" : lanceur.getGameProfile().getName(), atout ? "atout gratuit" : "Max Ammo");
    }

    /** Ligne de radio de Maxis dans le chat de tous ({@code zombies.giant.radio.<cle>}), en gris-bleu italique. */
    void radio(String cle) {
        partie.annonceChat(Component.translatable("zombies.giant.radio.prefixe").withStyle(s -> s.withColor(0x7FB8D8).withBold(true))
                .append(Component.translatable("zombies.giant.radio." + cle).withStyle(s -> s.withColor(0xB8D4E6).withItalic(true).withBold(false))));
    }

    // ------------------------------------------------------------------ acte IV
    private void lancerBoss(ServerPlayer p) {
        ActeQuatre acte = partie.acte();
        if (plan.porteConfinement == null || acte == null) {
            partie.refusPublic(p, "zombies.giant.refus.n07");
            return;
        }
        boolean avecBoss = acte.avecBoss();
        if (!acte.lancer(p)) {
            partie.refusPublic(p, "zombies.giant.refus.n07");
            return;
        }
        bossLance = true;
        for (JoueurZombies o : partie.joueursZ()) o.inviteSale();
        if (avecBoss) {
            // la porte s'ouvre dans l'intro (crochet « porte_ouverture »), le monte-charge descend vers le hall du Secteur 7
            partie.annonceChat(Component.translatable("zombies.giant.n07").withStyle(s -> s.withColor(0xC8102E).withBold(true)));
            HeraliszMatchs.LOG.info("[zombies] The Giant : porte de confinement ouverte par {} : combat contre N-07", p.getGameProfile().getName());
        } else {
            // sans le mod du boss : la carte d'acces scelle le confinement, la quete est terminee (la porte reste fermee)
            Vec3 v = Vec3.atBottomCenterOf(plan.porteConfinement.pos());
            partie.son(ZombiesApi.Sons.EASTER_EGG_ETAPE, v.x, v.y + 1, v.z, 1.2f);
            partie.annonceChat(Component.translatable("zombies.giant.n07.scelle").withStyle(s -> s.withColor(0xFFB327).withBold(true)));
            HeraliszMatchs.LOG.info("[zombies] The Giant : confinement scelle par {} (pas de boss installe) : quete terminee", p.getGameProfile().getName());
        }
    }

    /** Le combat contre N-07 a ete interrompu (boss perdu) : la porte de confinement peut resservir. */
    void bossRelance() {
        bossLance = false;
        for (JoueurZombies o : partie.joueursZ()) o.inviteSale();
    }

    // ------------------------------------------------------------------ objectifs
    /**
     * Guidage (0.2.3) : sous-etape affichee pour l'objectif de la chaine, cle {@code zombies.jeu.objectif.giant.<sous-etape>}
     * (« Trouvez les fusibles » -> « Placez les fusibles dans l'armoire electrique (cour) » -> « Abaissez le levier »...).
     */
    String sousEtape(String objectif) {
        switch (objectif) {
            case "fusibles" -> {
                if (poses() + fusiblesPortes >= plan.fusiblesRequis) return poses() >= plan.fusiblesRequis ? "courant" : "fusibles_poser";
                return "fusibles";
            }
            case "teleporteurs" -> {
                if (enLiaison() != 0) return "teleporteurs_courir";
                for (int i = 0; i < n; i++) if (etats[i] == EtatPad.CHARGE) return "teleporteurs_lier";
                return "teleporteurs";
            }
            case "phare" -> {
                return flyTrapActif ? "phare_objets" : "phare";
            }
            case "n07" -> {
                if (carteAcces) return "n07_porte";
                if (flyTrapFini && relaisAbattus < relaisRequis) return "n07_relais";
                return carteDisponible() ? "n07_carte" : "n07";
            }
            default -> {
                return objectif;
            }
        }
    }

    /** Zone (id) ou se trouve la cible de la sous-etape, pour le texte « (cour) », vide sinon. */
    String zoneCible(String sousEtape) {
        return switch (sousEtape) {
            case "fusibles_poser", "courant" -> plan.courant == null ? "" : zoneDe(plan.courant.pos());
            case "teleporteurs_lier" -> {
                for (int i = 0; i < n; i++) if (etats[i] == EtatPad.CHARGE) yield plan.teleporteurs.get(i).zone();
                yield "";
            }
            case "n07_carte" -> plan.carteAcces == null ? "" : plan.carteAcces.zone();
            case "n07_porte" -> plan.porteConfinement == null ? "" : zoneDe(plan.porteConfinement.pos());
            default -> "";
        };
    }

    /** Lettre du teleporteur concerne par la sous-etape, ou vide. */
    String lettreCible(String sousEtape) {
        if ("teleporteurs_lier".equals(sousEtape)) {
            for (int i = 0; i < n; i++) if (etats[i] == EtatPad.CHARGE) return String.valueOf(plan.teleporteurs.get(i).lettre());
        }
        return "";
    }

    private String zoneDe(BlockPos p) {
        fr.heralisz.matchs.zombies.jeu.api.PlanZombies.Zone z = partie.plan().zoneA(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        return z == null ? "" : z.id();
    }

    /**
     * Point vise par la boussole pour la sous-etape (armoire, pad a lier, pupitre du mainframe, Pack-a-Punch, carte, porte
     * de confinement), ou null : jamais pour une recherche (fusibles, objets du Fly Trap, relais caches, bocaux).
     */
    @Nullable
    Vec3 cible(String sousEtape) {
        switch (sousEtape) {
            case "fusibles_poser", "courant" -> {
                return plan.courant == null ? null : Vec3.atCenterOf(plan.courant.pos());
            }
            case "teleporteurs_courir" -> {
                PlanGiant.Mainframe mf = plan.mainframe;
                return mf == null ? null : Vec3.atCenterOf(mf.console() != null ? mf.console().pos() : mf.pad());
            }
            case "teleporteurs_lier" -> {
                for (int i = 0; i < n; i++) if (etats[i] == EtatPad.CHARGE) return Vec3.atCenterOf(plan.teleporteurs.get(i).pad());
                return null;
            }
            case "teleporteurs" -> {
                // cuve du premier teleporteur pas encore charge
                for (int i = 0; i < n; i++) {
                    PlanGiant.Teleporteur tp = plan.teleporteurs.get(i);
                    if (etats[i] == EtatPad.ALIMENTE && tp.cuve() != null) return Vec3.atCenterOf(tp.cuve().pos());
                }
                return null;
            }
            case "pack_a_punch" -> {
                if (partie.plan().packAPunch() != null) return Vec3.atCenterOf(partie.plan().packAPunch().pos());
                return plan.rideau == null ? null : PlanZombiesOutils.centre(plan.rideau);
            }
            case "n07_carte" -> {
                return plan.carteAcces == null ? null : Vec3.atCenterOf(plan.carteAcces.pos());
            }
            case "n07_porte" -> {
                return plan.porteConfinement == null ? null : Vec3.atCenterOf(plan.porteConfinement.pos()).add(0, 1, 0);
            }
            default -> {
                return null;
            }
        }
    }

    /** Progression affichee apres le texte de l'objectif ({@code (2/3)}, {@code A 0:24}), vide sinon. */
    String progres(String objectif, long t) {
        switch (objectif) {
            case "fusibles" -> {
                if (poses() + fusiblesPortes >= plan.fusiblesRequis && poses() < plan.fusiblesRequis)
                    return "(" + poses() + "/" + plan.fusiblesRequis + ")";
                if (poses() >= plan.fusiblesRequis) return "";
                return "(" + Math.min(plan.fusiblesRequis, poses() + fusiblesPortes) + "/" + plan.fusiblesRequis + ")";
            }
            case "teleporteurs" -> {
                for (int i = 0; i < n; i++) {
                    if (etats[i] == EtatPad.LIAISON) {
                        long s = Math.max(0, (finEtat[i] - t + 19) / 20);
                        return plan.teleporteurs.get(i).lettre() + " " + s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
                    }
                }
                return "(" + Integer.bitCount(lies) + "/" + n + ")";
            }
            case "phare" -> {
                if (!flyTrapActif) return "";
                int nb = 0;
                for (boolean b : flyTrapTouche) if (b) nb++;
                return "(" + nb + "/" + flyTrapTouche.length + ")";
            }
            case "n07" -> {
                if (!carteAcces && flyTrapFini && relaisAbattus < relaisRequis) return "(" + relaisAbattus + "/" + relaisRequis + ")";
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    /** Vrai si l'objectif a un objet sur la carte. */
    boolean disponible(String objectif) {
        return switch (objectif) {
            case "fusibles" -> plan.courant != null && plan.fusiblesRequis > 0 && !plan.cachettes.isEmpty();
            case "teleporteurs" -> n > 0;
            case "phare" -> plan.panneauFlyTrap != null && !plan.objetsFlyTrap.isEmpty();
            case "n07" -> plan.porteConfinement != null;
            // acte IV avec le boss : abattre N-07, puis remonter par le teleporteur 935 (sans le mod du boss : sautes)
            case "n07_boss", "n07_retour" -> plan.porteConfinement != null && partie.acte() != null && partie.acte().avecBoss();
            default -> true;
        };
    }

    // ------------------------------------------------------------------ outils
    /** Objet de quete a son ancre (dans un decor) s'il en a une. */
    private void decorObjet(PlanGiant.Objet o, int genre, int etat) {
        try {
            ZombiesApi.decor().objetQueteAncre(partie.instance(), o.pos(), o.face(), genre, etat, genre == ZombiesDecor.OBJET_ARME ? armeAnnihilator : "", o.ancre());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.objetQuete", ex);
        }
    }

    private void decorObjet(BlockPos pos, net.minecraft.core.Direction face, int genre, int etat) {
        try {
            ZombiesApi.decor().objetQuete(partie.instance(), pos, face, genre, etat, genre == ZombiesDecor.OBJET_ARME ? armeAnnihilator : "");
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.objetQuete", ex);
        }
    }

    private void effet(Vec3 v, String type) {
        try {
            ZombiesApi.decor().effet(partie.instance(), v, type);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.effet", ex);
        }
    }

    /** Resume pour {@code /zombies dev etat}. */
    String resume() {
        StringBuilder b = new StringBuilder();
        b.append("fusibles=").append(poses()).append('+').append(fusiblesPortes).append('/').append(plan.fusiblesRequis);
        b.append(" pads=");
        for (int i = 0; i < n; i++) b.append(plan.teleporteurs.get(i).lettre()).append(':').append(etats[i]).append('(').append(ames[i]).append('/')
                .append(requises[i]).append(") ");
        b.append("lies=").append(Integer.bitCount(lies)).append(" rideau=").append(rideauOuvert).append(" flytrap=").append(flyTrapActif)
                .append(flyTrapFini ? "/fini" : "").append(" bocaux=").append(musiqueJouee).append(" secret=").append(secretRevele)
                .append(" carte=").append(carteAcces).append(" relais=").append(relaisAbattus).append('/').append(relaisRequis);
        return b.toString();
    }

    // ------------------------------------------------------------------ banc d'essai
    /** {@code /zombies dev giant ...} : fusibles, ames pleines, tout lier, Fly Trap fini, boss (finale tout de suite). */
    void dev(String action) {
        switch (action) {
            case "fusibles" -> {
                for (int i = 0; i < fusibleIci.length; i++) fusibleIci[i] = false;
                fusiblesPortes = 0;
                masqueFusibles = (1 << Math.min(3, plan.fusiblesRequis)) - 1;
                try {
                    ZombiesApi.decor().armoire(partie.instance(), masqueFusibles, partie.courant());
                    for (PlanGiant.Cachette c : plan.cachettes) ZombiesApi.decor().objetQuete(partie.instance(), c.pos(), c.face(), ZombiesDecor.OBJET_FUSIBLE,
                            ZombiesDecor.OBJET_CACHE, "");
                } catch (RuntimeException ignored) {
                }
                partie.objectifRempliPublic("fusibles");
            }
            case "ames" -> {
                for (int i = 0; i < n; i++) {
                    ames[i] = requises[i];
                    ZombiesApi.decor().cuveAmes(partie.instance(), plan.teleporteurs.get(i), 1f);
                    if (etats[i] == EtatPad.ALIMENTE) {
                        etats[i] = EtatPad.CHARGE;
                        padChange(i, 0);
                    }
                }
            }
            case "lier" -> {
                for (int i = 0; i < n; i++) if (etats[i] == EtatPad.CHARGE || etats[i] == EtatPad.ALIMENTE) {
                    etats[i] = EtatPad.LIAISON;
                    finEtat[i] = partie.ticks() + 600;
                }
                List<ServerPlayer> en = partie.enLigneCourant();
                if (!en.isEmpty()) {
                    JoueurZombies j = partie.joueurZ(en.get(0).getUUID());
                    if (j != null) lier(en.get(0), j);
                }
            }
            case "relais" -> {
                // 0.2.3 : les relais 935 caches sont tous abattus (la carte d'acces devient prenable apres le Fly Trap)
                java.util.Arrays.fill(cibleFaite, true);
                relaisAbattus = relaisRequis;
                partie.envoyerObjectifProgres();
            }
            case "flytrap" -> {
                flyTrapActif = true;
                java.util.Arrays.fill(flyTrapTouche, true);
                finirFlyTrap();
            }
            case "boss" -> {
                // finale tout de suite : carte d'acces prise, porte de confinement ouverte par le premier joueur
                if (bossLance) return;
                flyTrapFini = true;
                carteAcces = true;
                List<ServerPlayer> en = partie.enLigneCourant();
                if (!en.isEmpty()) lancerBoss(en.get(0));
            }
            default -> {
            }
        }
    }

    /** Petits calculs de volumes. */
    static final class PlanZombiesOutils {
        private PlanZombiesOutils() {
        }

        static Vec3 centre(net.minecraft.world.level.levelgen.structure.BoundingBox b) {
            return new Vec3((b.minX() + b.maxX() + 1) / 2.0, b.minY(), (b.minZ() + b.maxZ() + 1) / 2.0);
        }
    }

    /** Vrai si un zombie suivi est de ce type (chiens melanges). */
    static boolean estChien(ZombieSuivi s) {
        return s.type == TypeZombie.RODEUR;
    }

    /** Etat HUD d'un joueur debout (raccourci). */
    static boolean debout(JoueurZombies j) {
        return j.etat == HudZombies.DEBOUT;
    }

    /** Type d'atout secret a reveler (alternatives de la carte non deja prises par une machine normale). */
    TypeAtout atoutSecret(List<TypeAtout> normaux) {
        for (String s : plan.alternativesSecret) {
            TypeAtout t = TypeAtout.parId(s);
            if (t != null && !normaux.contains(t)) return t;
        }
        TypeAtout t = TypeAtout.parId(plan.atoutSecret);
        return t != null ? t : TypeAtout.DEADSHOT;
    }
}
