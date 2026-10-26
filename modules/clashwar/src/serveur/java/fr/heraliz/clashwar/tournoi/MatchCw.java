package fr.heraliz.clashwar.tournoi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.Annonces;
import fr.heraliz.clashwar.moteur.api.Arenes;
import fr.heraliz.clashwar.moteur.api.Carte;
import fr.heraliz.clashwar.moteur.api.ChoixClasse;
import fr.heraliz.clashwar.moteur.api.Equipe;
import fr.heraliz.clashwar.moteur.api.Equipement;
import fr.heraliz.clashwar.moteur.api.Hud;
import fr.heraliz.clashwar.moteur.api.Instance;
import fr.heraliz.clashwar.moteur.api.KillFeed;
import fr.heraliz.clashwar.moteur.api.Kit;
import fr.heraliz.clashwar.moteur.api.Kits;
import fr.heraliz.clashwar.moteur.api.MatchApi;
import fr.heraliz.clashwar.moteur.api.Partie;
import fr.heraliz.clashwar.moteur.api.Pose;
import fr.heraliz.clashwar.moteur.api.Spectateur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import fr.heraliz.clashwar.tournoi.net.CwPaquets;
import fr.heraliz.noyau.api.CombatTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un match du tableau du ClashWar : <b>élimination au meilleur des 5 manches</b> (premier à
 * {@link CwConfig#manchesGagnantes}). Chaque manche : choix de classe (écran du moteur, mêmes classes pour les deux
 * équipes, limites sniper / pompe par équipe), préparation derrière la barrière de son camp, combat de
 * {@link CwConfig#combatSecondes} puis mort subite (zone qui se resserre autour de {@code zone_centre}, dégâts hors
 * zone). La dernière équipe avec un vivant gagne la manche ; à égalité au bout du temps, le plus de vivants, puis le plus
 * de dégâts infligés. Côtés échangés aux manches 3 et 5 ; une vie par manche, tout le monde revient à la manche suivante.
 * <p>Points de la carte : {@code spawn_a}, {@code spawn_b}, {@code barriere_a}, {@code barriere_b} (deux coins chacune),
 * {@code zone_centre} (ou méta {@code zone}), {@code spectateur}. Avant le jeu, le match attend son arène et l'appel : un
 * joueur tagué en combat, absent ou déconnecté rate le match ; une équipe sans aucun joueur présent perd par forfait.
 * Éliminés = spectateurs de leur partie jusqu'à la manche suivante ; à la fin, tout le monde retourne en salle d'attente,
 * kit retiré. Les joueurs de la salle peuvent regarder le match ({@link #regarder}). Fil serveur, aucune allocation par
 * tick hors événements.
 */
public final class MatchCw extends Partie {
    /** Pause entre deux manches (annonce du résultat), en ticks. */
    private static final int PAUSE_MANCHE = 100;

    private final Tournoi tournoi;
    private final Tableau.Rencontre rencontre;
    private final Tableau.EquipeT ta, tb;
    private final Equipe ea, eb;
    private final Component nomTour;
    @Nullable
    private final Carte carte;
    /** Appel fait (joueurs entrés) ; en phase ATTENTE ensuite = pause entre deux manches. */
    private boolean appele, kitsDonnes, retourFait;
    /** Annulation demandée par le tournoi (pas de résultat à rendre). */
    private boolean parTournoi;
    private int manche;
    /** Gagnant de chaque manche jouée : "a" ou "b". */
    private final List<String> manches = new ArrayList<>();
    private final Map<UUID, Pose> spawns = new HashMap<>();
    @Nullable
    private AABB barriereA, barriereB;
    private float degatsA, degatsB;
    private double zoneX, zoneZ, zoneY;
    private float rayonDepart, rayonMin;
    /** Éliminations et morts de chaque joueur sur tout le match (panneau de fin, classement des clans). */
    private final Map<UUID, int[]> stats = new LinkedHashMap<>();
    private final Map<UUID, String> noms = new HashMap<>();
    /** Tampon réutilisé (pas d'allocation par tick). */
    private final List<ServerPlayer> tampon = new ArrayList<>();

    private MatchCw(Tournoi tournoi, Tableau.Rencontre rencontre, Tableau.EquipeT ta, Tableau.EquipeT tb, Component nomTour,
                    @Nullable Carte carte, Equipe ea, Equipe eb) {
        super("clashwar-" + tournoi.edition + "-t" + rencontre.tour + "-m" + rencontre.index, "clashwar", tournoi.serveur());
        this.tournoi = tournoi;
        this.rencontre = rencontre;
        this.ta = ta;
        this.tb = tb;
        this.ea = ea;
        this.eb = eb;
        this.nomTour = nomTour;
        this.carte = carte;
        ea.clan(ta.clan);
        eb.clan(tb.clan);
        ajouterEquipe(ea);
        ajouterEquipe(eb);
        reserver();
    }

    static MatchCw creer(Tournoi tournoi, Tableau.Rencontre rencontre, Tableau.EquipeT ta, Tableau.EquipeT tb, Component nomTour) {
        List<Carte> cartes = Arenes.cartesDeType(tournoi.format.typeCarte);
        if (cartes.isEmpty()) cartes = Arenes.cartesDeType(Carte.TYPE_CLASHWAR_RD);
        Carte c = cartes.isEmpty() ? null : cartes.get(tournoi.rnd.nextInt(cartes.size()));
        if (c == null) HeralizClashWar.LOG.warn("[clashwar] aucune carte de type {}", tournoi.format.typeCarte);
        Equipe a = new Equipe("a", ta.libelle(), ta.couleur);
        Equipe b = new Equipe("b", tb.libelle(), tb.couleur == ta.couleur ? Tournois.OR : tb.couleur);
        return new MatchCw(tournoi, rencontre, ta, tb, nomTour, c, a, b);
    }

    private void reserver() {
        if (carte == null || instance() != null) return;
        Instance i = Arenes.reserver(carte.id());
        if (i != null) {
            instance(i);
            lireCarte(i);
        }
    }

    private void lireCarte(Instance i) {
        barriereA = boite(i.points("barriere_a"));
        barriereB = boite(i.points("barriere_b"));
        Pose c = i.point("zone_centre");
        AABB b = i.bornes();
        zoneX = c != null ? c.x() : b.getCenter().x;
        zoneZ = c != null ? c.z() : b.getCenter().z;
        zoneY = c != null ? c.y() : b.minY + 1;
        rayonDepart = (float) Math.max(b.getXsize(), b.getZsize()) * 0.75f;
        rayonMin = 4f;
        JsonObject m = i.carte().meta();
        if (m.has("zone") && m.get("zone").isJsonObject()) {
            JsonObject z = m.getAsJsonObject("zone");
            if (z.has("centre")) {
                JsonArray a = z.getAsJsonArray("centre");
                zoneX = i.origine().getX() + a.get(0).getAsDouble();
                zoneY = i.origine().getY() + a.get(1).getAsDouble();
                zoneZ = i.origine().getZ() + a.get(2).getAsDouble();
            }
            if (z.has("rayon_depart")) rayonDepart = z.get("rayon_depart").getAsFloat();
            if (z.has("rayon_min")) rayonMin = z.get("rayon_min").getAsFloat();
        }
    }

    /** Boîte de deux coins (blocs inclus, 3 blocs de haut au-dessus du plus haut), ou null. */
    @Nullable
    private static AABB boite(List<Pose> coins) {
        if (coins.size() < 2) return null;
        Pose p = coins.get(0), q = coins.get(1);
        double x0 = Math.floor(Math.min(p.x(), q.x())), y0 = Math.floor(Math.min(p.y(), q.y())), z0 = Math.floor(Math.min(p.z(), q.z()));
        double x1 = Math.floor(Math.max(p.x(), q.x())) + 1, y1 = Math.floor(Math.max(p.y(), q.y())) + 3, z1 = Math.floor(Math.max(p.z(), q.z())) + 1;
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    Tableau.Rencontre rencontre() {
        return rencontre;
    }

    /** Libellé « [A] contre [B] » (tableau, chat). */
    Component contre() {
        return Component.translatable("clashwar.annonce.contre", ta.libelle(), tb.libelle());
    }

    // ================================================================== déroulé
    @Override
    public void tick() {
        CwConfig cfg = CwConfig.get();
        switch (phase()) {
            case ATTENTE -> {
                if (appele) {
                    // pause entre deux manches
                    if (ticksPhase() >= PAUSE_MANCHE) debutManche();
                    return;
                }
                Instance i = instance();
                if (i == null) {
                    if (ticksPhase() % 100 == 0) reserver();
                    if (ticksPhase() > cfg.attenteArene * 20) forfaitTechnique();
                } else if (i.prete() && ticksPhase() >= delaiAppel()) {
                    appeler();
                } else if (ticksPhase() > cfg.attenteArene * 20) {
                    forfaitTechnique();
                }
            }
            case CHOIX -> {
                retenir();
                // filet : l'écran du moteur aurait dû rendre la main
                if (ticksPhase() >= (cfg.choixSecondes + 5) * 20) {
                    ChoixClasse.annuler(this);
                    finChoix(Map.of());
                }
            }
            case PREPARATION -> {
                retenir();
                if (ticksPhase() >= cfg.preparationSecondes * 20) debutCombat();
            }
            case COMBAT -> {
                if (ticksPhase() >= cfg.combatSecondes * 20) mortSubite();
            }
            case PROLONGATION -> {
                if (ticksPhase() % 20 == 0) degatsZone();
                if (ticksPhase() >= (cfg.mortSubiteSecondes + cfg.limiteSecondes) * 20) departager();
            }
            case FIN -> {
                if (!retourFait && ticksPhase() >= cfg.retourSecondes * 20) {
                    retourFait = true;
                    retourSalle();
                    Equipe g = gagnante();
                    tournoi.resultat(this, rencontre, g == eb ? rencontre.b : rencontre.a, false);
                    fermer();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Ticks minimum entre le lancement du tour et l'appel : le temps de l'annonce du tour (3 s), de la finale annoncée à
     * tout le serveur (6 s).
     */
    private int delaiAppel() {
        Tableau tab = tournoi.tableau;
        return tab != null && rencontre.tour == tab.nbTours() - 1 ? 120 : 60;
    }

    /** L'arène n'a pas pu être posée à temps : la rencontre est tranchée au hasard (signalé). */
    private void forfaitTechnique() {
        HeralizClashWar.LOG.warn("[clashwar] {} : arene {} indisponible, rencontre tranchee au hasard", id(), carte == null ? "?" : carte.id());
        Annonces.chat(serveur(), Component.translatable("clashwar.chat.arene_indisponible").withStyle(ChatFormatting.GRAY));
        terminerSansJeu(tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b, true);
    }

    /** Rencontre tranchée sans jouer : personne n'a été rattaché à ce match. */
    private void terminerSansJeu(int gagnant, boolean forfait) {
        appele = true;
        phase(Phase.TERMINEE);
        MatchApi.retirer(this);
        if (instance() != null) Arenes.liberer(instance());
        instance(null);
        tournoi.resultat(this, rencontre, gagnant, forfait);
    }

    /** Appel du match : joueurs présents rattachés au match, première manche. */
    private void appeler() {
        appele = true;
        List<ServerPlayer> pa = presents(ta), pb = presents(tb);
        if (pa.isEmpty() || pb.isEmpty()) {
            int gagnant = pa.isEmpty() && pb.isEmpty() ? (tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b) : pa.isEmpty() ? rencontre.b : rencontre.a;
            terminerSansJeu(gagnant, true);
            return;
        }
        for (ServerPlayer p : pa) entrer(p, ea);
        for (ServerPlayer p : pb) entrer(p, eb);
        rencontre.vivantsA = rencontre.vivantsB = 0;
        rencontre.totalA = rencontre.totalB = CwConfig.get().manchesGagnantes;
        tournoi.diffuserRencontre(rencontre);
        Annonces.grande(joueursEnLigne(), new Annonces.Annonce(nomTour, contre(), Tournois.ACCENT, CwSons.id(CwSons.APPEL), 60,
                Annonces.Style.NORMALE));
        tournoi.bandeauSalle();
        debutManche();
    }

    /** Joueurs de l'équipe présents en salle (pas en train de regarder un autre match), connectés et non tagués. */
    private List<ServerPlayer> presents(Tableau.EquipeT t) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : t.joueurs) {
            if (!tournoi.participant(u)) continue;
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            // il regardait un autre match : ramené en salle d'abord
            if (MatchApi.partieDe(p) instanceof MatchCw autre && autre != this && autre.equipeDe(u) == null) autre.arreterRegarder(p);
            if (MatchApi.partieDe(p) != tournoi.salle) continue;
            if (CombatTag.estTague(p)) {
                Annonces.chat(p, Component.translatable("clashwar.chat.rate_combat").withStyle(ChatFormatting.RED));
                continue;
            }
            l.add(p);
        }
        return l;
    }

    private void entrer(ServerPlayer p, Equipe e) {
        e.ajouter(p.getUUID());
        MatchApi.rejoindre(p, this);
        stats.put(p.getUUID(), new int[2]);
        noms.put(p.getUUID(), p.getGameProfile().getName());
        p.setCamera(p);
    }

    /** Côtés échangés aux manches 3 et 5 (manches 1-2 et 5 du côté d'origine, 3-4 de l'autre). */
    private boolean cotesEchanges() {
        return ((manche - 1) / 2) % 2 == 1;
    }

    /** Nouvelle manche : tout le monde revient en vie à son point d'apparition, ancien kit retiré, choix de classe. */
    private void debutManche() {
        Instance i = instance();
        if (i == null) {
            annuler(Component.translatable("clashwar.chat.arene_indisponible").withStyle(ChatFormatting.GRAY));
            return;
        }
        manche++;
        kitsDonnes = false;
        degatsA = degatsB = 0;
        spawns.clear();
        MatchsNet.versJoueurs(joueursEnLigne(), CwPaquets.ZoneS2C.AUCUNE);
        boolean echange = cotesEchanges();
        placer(i, ea, i.points(echange ? "spawn_b" : "spawn_a"));
        placer(i, eb, i.points(echange ? "spawn_a" : "spawn_b"));
        // une équipe dont plus aucun joueur n'est connecté perd le match par forfait
        if (!ea.enVie() || !eb.enVie()) {
            terminer(ea.enVie() ? ea : eb);
            return;
        }
        phase(Phase.CHOIX);
        List<ServerPlayer> joueurs = joueursEnLigne();
        Annonces.grande(joueurs, new Annonces.Annonce(Component.translatable("clashwar.annonce.manche", manche), contre(), Tournois.ACCENT,
                CwSons.id(CwSons.CHOIX), 40, Annonces.Style.NORMALE));
        // mêmes classes pour les deux équipes ; limites (sniper, pompe) vérifiées par équipe côté serveur
        List<Kit> kits = new ArrayList<>();
        for (String k : CwConfig.get().kits) {
            Kit kit = Kits.get(k);
            if (kit != null) kits.add(kit);
            else HeralizClashWar.LOG.warn("[clashwar] kit {} introuvable (data/heraliz_clashwar/kits)", k);
        }
        Kit defaut = Kits.get(CwConfig.get().kitDefaut);
        if (defaut != null && kits.remove(defaut)) kits.add(0, defaut);
        int s = CwConfig.get().choixSecondes;
        bandeau(s, true);
        ChoixClasse.ouvrir(this, joueurs, kits, CwConfig.get().limites, s, Component.translatable("clashwar.choix.titre", nomTour), this::finChoix);
    }

    /** Remet en vie et place les joueurs connectés de l'équipe sur ses points d'apparition. */
    private void placer(Instance i, Equipe e, List<Pose> points) {
        e.ranimerTous();
        int k = 0;
        for (UUID u : e.joueurs()) {
            ServerPlayer p = joueur(u);
            if (p == null || MatchApi.partieDe(p) != this) {
                // déconnecté, ou revenu après une déconnexion (il attend en salle, voir surReconnexion) : absent de la
                // manche ; sinon il était renvoyé dans l'arène, rattaché à la salle donc intouchable, et comptait vivant
                e.eliminer(u);
                continue;
            }
            Pose pose = points.isEmpty() ? new Pose(i.bornes().getCenter().x, i.bornes().minY + 1, i.bornes().getCenter().z, 0)
                    : points.get(k % points.size());
            k++;
            spawns.put(u, pose);
            Spectateur.arreter(p, false);
            Equipement.retirer(p);
            p.setGameMode(GameType.SURVIVAL);
            p.removeAllEffects();
            p.clearFire();
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.fallDistance = 0;
            pose.teleporter(p, i.niveau());
        }
    }

    /** Pendant le choix et la préparation : chaque joueur reste derrière la barrière de son camp. */
    private void retenir() {
        Instance i = instance();
        if (i == null) return;
        boolean echange = cotesEchanges();
        retenir(ea, echange ? barriereB : barriereA, i);
        retenir(eb, echange ? barriereA : barriereB, i);
    }

    private void retenir(Equipe e, @Nullable AABB barriere, Instance i) {
        for (UUID u : e.vivants()) {
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            Pose s = spawns.get(u);
            if (s == null) continue;
            boolean dehors = barriere != null ? !barriere.contains(p.getX(), p.getY(), p.getZ()) : s.distanceCarre(p.getX(), p.getY(), p.getZ()) > 2.25;
            if (dehors) p.teleportTo(i.niveau(), s.x(), s.y(), s.z(), p.getYRot(), p.getXRot());
        }
    }

    /** Fin du choix (écran du moteur) : kits donnés (kit par défaut à qui n'a rien choisi), préparation. */
    private void finChoix(Map<UUID, Kit> choix) {
        if (phase() != Phase.CHOIX || kitsDonnes) return;
        kitsDonnes = true;
        Kit defaut = Kits.get(CwConfig.get().kitDefaut);
        for (Equipe eq : equipes) {
            for (UUID u : eq.vivants()) {
                ServerPlayer p = joueur(u);
                if (p == null) continue;
                Kit k = choix.getOrDefault(u, defaut);
                if (k != null && !Equipement.donner(p, k, id())) {
                    HeralizClashWar.LOG.warn("[clashwar] {} : kit {} non donne a {}", id(), k.id(), p.getGameProfile().getName());
                }
            }
        }
        phase(Phase.PREPARATION);
        int s = CwConfig.get().preparationSecondes;
        Annonces.compteARebours(joueursEnLigne(), s, Component.translatable("clashwar.annonce.preparation"));
        bandeau(s, false);
    }

    private void debutCombat() {
        phase(Phase.COMBAT);
        for (ServerPlayer p : joueursEnLigne()) p.playNotifySound(CwSons.COMBAT.get(), SoundSource.MASTER, 1f, 1f);
        bandeau(CwConfig.get().combatSecondes, false);
    }

    /** Fin du temps : la zone commence à se resserrer. */
    private void mortSubite() {
        phase(Phase.PROLONGATION);
        List<ServerPlayer> joueurs = joueursEnLigne();
        MatchsNet.versJoueurs(joueurs, new CwPaquets.ZoneS2C(true, zoneX, zoneZ, zoneY, rayonDepart, rayonMin, CwConfig.get().mortSubiteSecondes * 20));
        Annonces.grande(joueurs, new Annonces.Annonce(Component.translatable("clashwar.annonce.mort_subite"),
                Component.translatable("clashwar.annonce.mort_subite.sous"), 0xFF6B6B, CwSons.id(CwSons.ZONE), 60, Annonces.Style.DEFAITE));
        bandeau(-1, false);
    }

    /** Rayon courant de la zone. */
    private float rayon() {
        float t = Math.min(1f, ticksPhase() / (CwConfig.get().mortSubiteSecondes * 20f));
        return rayonDepart + (rayonMin - rayonDepart) * t;
    }

    private void degatsZone() {
        float r = rayon(), r2 = r * r;
        float degats = CwConfig.get().degatsZone;
        for (int k = 0; k < equipes.size(); k++) {
            tampon.clear();
            for (UUID u : equipes.get(k).vivants()) {
                ServerPlayer p = joueur(u);
                if (p == null) continue;
                double dx = p.getX() - zoneX, dz = p.getZ() - zoneZ;
                if (dx * dx + dz * dz > r2) tampon.add(p);
            }
            // dégâts hors de la boucle sur les vivants (une élimination modifie l'ensemble)
            for (int i = 0; i < tampon.size(); i++) tampon.get(i).hurt(tampon.get(i).damageSources().outOfBorder(), degats);
            tampon.clear();
        }
    }

    /** Temps vraiment écoulé : le plus de vivants gagne la manche, puis le plus de dégâts infligés. */
    private void departager() {
        if (ea.nbVivants() != eb.nbVivants()) finManche(ea.nbVivants() > eb.nbVivants() ? ea : eb);
        else finManche(degatsA >= degatsB ? ea : eb);
    }

    /** Manche gagnée : point marqué ; match fini à {@link CwConfig#manchesGagnantes}, sinon pause puis manche suivante. */
    private void finManche(Equipe gagnante) {
        if (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION) return;
        gagnante.ajouterScore(1);
        manches.add(gagnante.id());
        rencontre.vivantsA = ea.score();
        rencontre.vivantsB = eb.score();
        tournoi.diffuserRencontre(rencontre);
        MatchsNet.versJoueurs(joueursEnLigne(), CwPaquets.ZoneS2C.AUCUNE);
        HeralizClashWar.LOG.info("[clashwar] {} : manche {} pour {} ({}-{})", id(), manche, gagnante.nom(), ea.score(), eb.score());
        if (gagnante.score() >= CwConfig.get().manchesGagnantes) {
            terminer(gagnante);
            return;
        }
        Component score = Component.translatable("clashwar.hud.contre", ea.score(), eb.score());
        for (ServerPlayer p : joueursEnLigne()) {
            boolean gagne = equipeDe(p.getUUID()) == gagnante;
            Annonces.grande(p, new Annonces.Annonce(Component.translatable(gagne ? "clashwar.annonce.manche_gagnee" : "clashwar.annonce.manche_perdue"),
                    score, gagne ? Tournois.OR : 0xFF6B6B, CwSons.id(gagne ? CwSons.VICTOIRE : CwSons.DEFAITE), 60,
                    gagne ? Annonces.Style.VICTOIRE : Annonces.Style.DEFAITE));
        }
        phase(Phase.ATTENTE);
        bandeau(PAUSE_MANCHE / 20, false);
    }

    // ================================================================== événements du moteur
    @Override
    public float surDegats(ServerPlayer victime, @Nullable LivingEntity attaquant, DamageSource source, float montant) {
        if (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION) return 0f;
        Equipe ev = equipeDe(victime.getUUID());
        if (ev == null || !ev.estVivant(victime.getUUID())) return 0f;
        if (attaquant instanceof ServerPlayer ap) {
            Equipe et = equipeDe(ap.getUUID());
            if (et == ev) return 0f;
            float utile = Math.min(montant, victime.getHealth() + victime.getAbsorptionAmount());
            if (et == ea) degatsA += utile;
            else if (et == eb) degatsB += utile;
        }
        return montant;
    }

    @Override
    public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        UUID u = victime.getUUID();
        Equipe e = equipeDe(u);
        victime.setHealth(victime.getMaxHealth());
        victime.clearFire();
        Instance i = instance();
        if (e == null || !e.estVivant(u) || (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION)) {
            // pas de vraie élimination (choix, préparation, pause, spectateur) : retour à sa place
            Pose s = spawns.get(u);
            if (i != null && e != null && e.estVivant(u) && s != null) s.teleporter(victime, i.niveau());
            return;
        }
        String arme = "", nomTueur = "";
        int couleurTueur = 0xFFFFFF;
        if (tueur != null) {
            nomTueur = tueur.getName().getString();
            ItemStack main = tueur.getMainHandItem();
            if (!main.isEmpty()) arme = main.getHoverName().getString();
            if (tueur instanceof ServerPlayer tp) {
                Equipe et = equipeDe(tp.getUUID());
                if (et != null) couleurTueur = et.couleur();
                if (et != null && et != e) compter(tp.getUUID(), 0);
            }
        }
        compter(u, 1);
        KillFeed.ajouter(this, new KillFeed.Ligne(nomTueur, couleurTueur, victime.getGameProfile().getName(), e.couleur(), arme, false));
        e.eliminer(u);
        victime.removeAllEffects();
        victime.playNotifySound(CwSons.ELIMINATION.get(), SoundSource.MASTER, 1f, 1f);
        Spectateur.suivre(victime, this);
        verifierFinManche();
    }

    private void compter(UUID joueur, int colonne) {
        int[] s = stats.get(joueur);
        if (s != null) s[colonne]++;
    }

    private void verifierFinManche() {
        if (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION) return;
        boolean a = ea.enVie(), b = eb.enVie();
        if (a && b) {
            bandeau(phase() == Phase.COMBAT ? Math.max(0, CwConfig.get().combatSecondes - ticksPhase() / 20) : -1, false);
            return;
        }
        if (a) finManche(ea);
        else if (b) finManche(eb);
        else finManche(degatsA >= degatsB ? ea : eb);
    }

    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        UUID u = joueur.getUUID();
        Equipe e = equipeDe(u);
        if (e == null) {
            // un joueur de la salle qui regardait : il garde sa place en salle
            Spectateur.arreter(joueur, false);
            spectateurs.remove(u);
            if (tournoi.actif() && tournoi.salle != null) MatchApi.rejoindre(joueur, tournoi.salle);
            else MatchApi.quitter(u);
            return;
        }
        if (phase() == Phase.FIN || phase() == Phase.TERMINEE) return;
        if (e.estVivant(u)) {
            KillFeed.ajouter(this, new KillFeed.Ligne("", 0xFFFFFF, joueur.getGameProfile().getName(), e.couleur(), "", false));
            e.eliminer(u);
        }
        // une équipe sans plus aucun joueur connecté perd le match par forfait
        if (appele && joueursConnectes(e, u) == 0) {
            terminer(e == ea ? eb : ea);
            return;
        }
        verifierFinManche();
    }

    /**
     * Joueurs de l'équipe encore connectés et dans ce match, sans compter {@code sauf} (qui se déconnecte) : un joueur
     * revenu après une déconnexion attend en salle, il ne compte plus.
     */
    private int joueursConnectes(Equipe e, UUID sauf) {
        int n = 0;
        for (UUID u : e.joueurs()) {
            if (u.equals(sauf)) continue;
            ServerPlayer p = joueur(u);
            if (p != null && MatchApi.partieDe(p) == this) n++;
        }
        return n;
    }

    /**
     * Joueurs des deux équipes connectés ET rattachés à ce match : un joueur revenu après une déconnexion attend en salle
     * ({@link #surReconnexion}) ; il ne reçoit plus le choix de classe, les annonces, la zone ni le bandeau du match.
     */
    @Override
    public List<ServerPlayer> joueursEnLigne() {
        List<ServerPlayer> l = super.joueursEnLigne();
        l.removeIf(p -> MatchApi.partieDe(p) != this);
        return l;
    }

    @Override
    public boolean surReconnexion(ServerPlayer joueur) {
        // revenu pendant le match : il ne le rejoue pas, il attend en salle
        if (!tournoi.actif() || tournoi.salle == null || !tournoi.participant(joueur.getUUID())) return false;
        Spectateur.arreter(joueur, false);
        Equipement.retirer(joueur);
        tournoi.placerEnSalle(joueur);
        return true;
    }

    @Override
    public void surSortieBornes(ServerPlayer joueur) {
        UUID u = joueur.getUUID();
        Equipe e = equipeDe(u);
        Instance i = instance();
        if (e == null || !e.estVivant(u)) {
            Pose s = i == null ? null : i.point("spectateur");
            if (s != null) s.teleporter(joueur, i.niveau());
            return;
        }
        if (phase() == Phase.COMBAT || phase() == Phase.PROLONGATION) {
            surElimination(joueur, null, joueur.damageSources().fellOutOfWorld());
        } else if (i != null && spawns.containsKey(u)) {
            spawns.get(u).teleporter(joueur, i.niveau());
        }
    }

    // ================================================================== fin
    @Override
    protected void surFin(@Nullable Equipe gagnante) {
        MatchsNet.versJoueurs(joueursEnLigne(), CwPaquets.ZoneS2C.AUCUNE);
        if (parTournoi) return;
        int ka = 0, ma = 0, kb = 0, mb = 0;
        for (Map.Entry<UUID, int[]> s : stats.entrySet()) {
            if (ea.contient(s.getKey())) {
                ka += s.getValue()[0];
                ma += s.getValue()[1];
            } else {
                kb += s.getValue()[0];
                mb += s.getValue()[1];
            }
        }
        CwClassement.match(ta, ka, ma, gagnante == ea);
        CwClassement.match(tb, kb, mb, gagnante == eb);
        CwClassement.sauver(serveur());
        Tournois.diffuserClassement(serveur());
        envoyerFin(gagnante);
        HeralizClashWar.LOG.info("[clashwar] {} : {} gagne {}-{} (elims {} / {})", id(), gagnante != null ? gagnante.nom() : "?", ea.score(), eb.score(), ka, kb);
    }

    /** Panneau de fin de match à chaque joueur et spectateur : issue, score, manches, joueurs, suite. */
    private void envoyerFin(@Nullable Equipe gagnante) {
        List<Integer> l = new ArrayList<>();
        for (String m : manches) l.add("b".equals(m) ? 1 : 0);
        List<CwPaquets.JoueurFin> joueurs = new ArrayList<>();
        for (Map.Entry<UUID, int[]> s : stats.entrySet()) {
            joueurs.add(new CwPaquets.JoueurFin(noms.getOrDefault(s.getKey(), "?"), eb.contient(s.getKey()) ? 1 : 0, s.getValue()[0], s.getValue()[1]));
        }
        // meilleurs d'abord dans chaque équipe
        joueurs.sort((x, y) -> x.equipe() != y.equipe() ? Integer.compare(x.equipe(), y.equipe()) : Integer.compare(y.eliminations(), x.eliminations()));
        int secondes = CwConfig.get().retourSecondes;
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            Equipe e = equipeDe(u);
            int issue = e == null || gagnante == null ? CwPaquets.FIN_NEUTRE : e == gagnante ? CwPaquets.FIN_VICTOIRE : CwPaquets.FIN_DEFAITE;
            MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
            MatchsNet.versJoueur(p, new CwPaquets.FinMatchS2C(nomTour.getString(), issue, ta.libelle(), ea.couleur(), ea.score(), tb.libelle(),
                    eb.couleur(), eb.score(), CwConfig.get().manchesGagnantes, l, joueurs, suite(e, gagnante), secondes));
            p.playNotifySound(issue == CwPaquets.FIN_DEFAITE ? CwSons.DEFAITE.get() : CwSons.VICTOIRE.get(), SoundSource.MASTER, 1f, 1f);
        }
    }

    /** Ligne de suite du panneau de fin : prochain match, match pour la 3e place, élimination, fin du tournoi. */
    private Component suite(@Nullable Equipe e, @Nullable Equipe gagnante) {
        Tableau tab = tournoi.tableau;
        if (tab == null || gagnante == null) return Component.translatable("clashwar.fin.suite.salle");
        int dernier = tab.nbTours() - 1;
        if (rencontre.tour == dernier) return Component.translatable("clashwar.fin.suite.podium");
        if (e == null) return Component.translatable("clashwar.fin.suite.qualifie", gagnante.nom());
        if (e != gagnante) {
            boolean demi = rencontre.tour == dernier - 1 && tab.troisieme() != null;
            return Component.translatable(demi ? "clashwar.fin.suite.troisieme" : "clashwar.fin.suite.elimine");
        }
        Tableau.Rencontre suivante = tab.tour(rencontre.tour + 1).get(rencontre.index / 2);
        int moi = e == ea ? rencontre.a : rencontre.b;
        int adverse = rencontre.index % 2 == 0 ? suivante.b : suivante.a;
        Component nom = Component.translatable("clashwar.tour." + tab.cleRencontre(suivante));
        if (adverse < 0 || adverse == moi) return Component.translatable("clashwar.fin.suite.prochain_inconnu", nom);
        return Component.translatable("clashwar.fin.suite.prochain", nom, tab.equipe(adverse).libelle());
    }

    /** Tout le monde retourne en salle d'attente (ou est rendu si l'édition n'a plus de salle) : kit retiré. */
    private void retourSalle() {
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p == null) {
                // déconnecté pendant le match : il garde sa place en salle (sinon, à son retour, la fermeture du match
                // l'avait détaché de tout : son Coffre lui était rendu et il sortait du tournoi sans le savoir)
                if (equipeDe(u) != null && tournoi.actif() && tournoi.salle != null && tournoi.participant(u)) {
                    MatchApi.rattacher(u, tournoi.salle);
                }
                continue;
            }
            MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
            Spectateur.arreter(p, false);
            Equipement.retirer(p);
            if (tournoi.actif() && tournoi.salle != null && tournoi.participant(u)) tournoi.placerEnSalle(p);
            else tournoi.rendreJoueur(p);
        }
        spectateurs.clear();
    }

    /** Annulation de l'édition : tout le monde retourne en salle (le tournoi rendra les Coffres), aucun résultat. */
    void annuler() {
        if (phase() == Phase.TERMINEE) return;
        parTournoi = true;
        ChoixClasse.annuler(this);
        retourSalle();
        fermer();
    }

    /** Annulation en cours de match (arène perdue) : rencontre tranchée au hasard. */
    private void annuler(Component raison) {
        if (phase() == Phase.TERMINEE) return;
        Annonces.chat(serveur(), raison);
        ChoixClasse.annuler(this);
        retourSalle();
        fermer();
        if (tournoi.actif()) tournoi.resultat(this, rencontre, tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b, true);
    }

    // ================================================================== spectateurs de la salle
    /** Vrai si on peut regarder ce match (en jeu, pas encore fini). */
    boolean regardable() {
        return appele && phase() != Phase.FIN && phase() != Phase.TERMINEE;
    }

    /**
     * Un joueur de la salle (libre, pas dans ce match) regarde le match : rattaché à la partie comme spectateur sans
     * équipe, caméra sur un joueur vivant (flèches gauche / droite pour changer), bornes de l'arène. Il revient en salle
     * par {@code /clashwar salle}, à la fin du match, ou quand son propre match l'appelle.
     */
    boolean regarder(ServerPlayer p) {
        if (!regardable() || equipeDe(p.getUUID()) != null) return false;
        spectateurs.add(p.getUUID());
        MatchApi.rejoindre(p, this);
        Spectateur.suivre(p, this);
        bandeau(-1, false);
        return true;
    }

    /** Fin du visionnage : retour en salle d'attente. */
    void arreterRegarder(ServerPlayer p) {
        if (equipeDe(p.getUUID()) != null) return;
        MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
        Spectateur.arreter(p, false);
        spectateurs.remove(p.getUUID());
        tournoi.placerEnSalle(p);
    }

    /** Spectateurs de la salle encore là quand le match se ferme : rendus à la salle. */
    @Override
    public void fermer() {
        for (UUID u : new ArrayList<>(spectateurs)) {
            if (equipeDe(u) != null) continue;
            ServerPlayer p = joueur(u);
            if (p != null && MatchApi.partieDe(p) == this) arreterRegarder(p);
        }
        super.fermer();
    }

    // ================================================================== affichage
    /**
     * Bandeau du ClashWar (pastilles des manches, temps) à chaque joueur et spectateur du match ; le bandeau du moteur
     * est envoyé aussi (le joueur le masque quand celui du ClashWar est actif). {@code secondes} = -1 : pas de temps.
     */
    private void bandeau(int secondes, boolean nouvelleManche) {
        Hud.envoyer(this, Component.translatable("clashwar.hud.manche", nomTour, Math.max(1, manche)), secondes,
                Component.translatable("clashwar.hud.contre", ta.libelle(), tb.libelle()));
        String tour = nomTour.getString();
        int gagnantes = CwConfig.get().manchesGagnantes;
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            MatchsNet.versJoueur(p, new CwPaquets.MatchHudS2C(true, tour, ta.libelle(), ea.couleur(), ea.score(), tb.libelle(), eb.couleur(),
                    eb.score(), gagnantes, Math.max(1, manche), CwPaquets.ROLE_AUCUN, secondes, nouvelleManche));
        }
    }
}
