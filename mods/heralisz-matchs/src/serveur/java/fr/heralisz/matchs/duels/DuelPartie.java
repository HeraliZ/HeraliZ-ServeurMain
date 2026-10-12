package fr.heralisz.matchs.duels;

import fr.heralisz.matchs.duels.killcam.KillcamLogique;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.armes.Armurerie;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.armes.TirsTacz;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.donnees.DuelsStore;
import fr.heralisz.matchs.duels.killcam.Enregistreur;
import fr.heralisz.matchs.duels.killcam.Killcam;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Un duel (MATCHS_SPEC §3) : deux equipes (A = maison jaune, B = maison verte de Nuketown) en 1v1 ou 2v2, mode
 * <b>recherche et destruction</b> (une vie par manche, charge, sites A/B, premier a 4 manches, changement de cote apres
 * 3 manches, killcam de la derniere elimination en fin de manche) ou <b>match a mort</b> (reapparition apres 3 s loin des
 * ennemis, premier a 20 / 30 eliminations ou 10 min, killcam a chaque mort). Classes personnalisees choisies en debut de
 * manche ou a chaque reapparition (5 s). Fin : tableau des scores, statistiques de duel, Coffre rendu.
 * <p>Phases ({@link Partie.Phase}) : ATTENTE (pose de l'instance) -> CHOIX (gel, choix de classe) -> COMBAT ->
 * PREPARATION (entre deux manches de R&D : annonces, killcam) -> ... -> FIN (scores) -> TERMINEE.
 * <p>Fil serveur ; pas d'allocation par tick hors evenements (listes tampons reutilisees).
 */
public class DuelPartie extends Partie {
    /**
     * Nom de l'equipe et de la cible du mode entrainement : une cle de traduction (aucun pseudo ne contient de point),
     * traduite chez le joueur (tableau des scores, fil des eliminations).
     */
    static final String NOM_CIBLE = "heralisz_matchs.duel.cible_nom";
    /** Fiche d'un participant (joueur ou cible d'entrainement). */
    static final class Fiche {
        final UUID id;
        final String nom;
        Equipe equipe;
        final boolean bot;
        int elims, morts, tetes;
        /** Classe choisie (0..4). */
        int classe;
        /** Match a mort : ticks avant reapparition (-1 = vivant). */
        int reapparition = -1;
        /** Tick de la derniere apparition (grace du changement de classe). */
        long apparition;
        /** Protection d'apparition (match a mort) jusqu'a ce tick. */
        long protegeJusque;
        /** Pose de gel (phase CHOIX) ou d'apparition. */
        @Nullable
        Pose pose;
        /** Parti (deconnexion, abandon) : plus compte. */
        boolean parti;

        Fiche(UUID id, String nom, Equipe equipe, boolean bot) {
            this.id = id;
            this.nom = nom;
            this.equipe = equipe;
            this.bot = bot;
        }
    }

    private final boolean rd;
    private final String modeId, format, carteId;
    private final boolean entrainement;
    private final Equipe a, b;
    private final Map<UUID, Fiche> fiches = new LinkedHashMap<>();
    private final List<ServerPlayer> tampon = new ArrayList<>();
    private final List<String> manches = new ArrayList<>();
    @Nullable
    private CibleEntrainement cible;
    private int dernierHud = Integer.MIN_VALUE;
    private boolean annulee;
    private boolean finApresIntermanche;
    @Nullable
    private Equipe gagnanteManche;
    private boolean killcamFinManche;
    @Nullable
    private Enregistreur enregistreur;

    // ------------------------------------------------------------------ recherche et destruction
    private int manche;
    private Equipe attaquants, defenseurs;
    private int etatCharge = Vues.CHARGE_AUCUNE;
    @Nullable
    private UUID porteur;
    private double cx, cy, cz;
    private String siteCharge = "";
    private int minuterie;
    private int progression;
    @Nullable
    private UUID acteur;
    @Nullable
    private Entity affichageCharge;
    @Nullable
    private AABB siteA, siteB;
    private final Map<UUID, Integer> signatures = new LinkedHashMap<>();

    /**
     * @param id           identifiant unique ("duel-4f2a")
     * @param modeId       {@link Vues#MODE_RD} ou {@link Vues#MODE_MAM}
     * @param format       {@link Vues#FORMAT_1V1} ou {@link Vues#FORMAT_2V2}
     * @param entrainement duel contre la cible d'entrainement (pas de statistiques)
     */
    public DuelPartie(String id, MinecraftServer serveur, String modeId, String format, String carteId, boolean entrainement,
                      List<ServerPlayer> campA, List<ServerPlayer> campB) {
        super(id, "duel", serveur);
        this.rd = Vues.MODE_RD.equals(modeId);
        this.modeId = modeId;
        this.format = format;
        this.carteId = carteId;
        this.entrainement = entrainement;
        this.a = new Equipe("a", nomCamp(campA, "A"), DuelsConfig.COULEUR_A);
        this.b = new Equipe("b", entrainement ? NOM_CIBLE : nomCamp(campB, "B"), DuelsConfig.COULEUR_B);
        ajouterEquipe(a);
        ajouterEquipe(b);
        for (ServerPlayer p : campA) inscrire(p, a);
        for (ServerPlayer p : campB) inscrire(p, b);
        this.attaquants = a;
        this.defenseurs = b;
    }

    private static String nomCamp(List<ServerPlayer> camp, String defaut) {
        if (camp.isEmpty()) return defaut;
        StringBuilder sb = new StringBuilder();
        for (ServerPlayer p : camp) {
            if (sb.length() > 0) sb.append(" & ");
            sb.append(p.getGameProfile().getName());
        }
        return sb.toString();
    }

    private void inscrire(ServerPlayer p, Equipe e) {
        e.ajouter(p.getUUID());
        Fiche f = new Fiche(p.getUUID(), p.getGameProfile().getName(), e, false);
        f.classe = DuelsStore.derniere(p.getUUID());
        fiches.put(p.getUUID(), f);
    }

    // ------------------------------------------------------------------ informations
    public boolean rechercheDestruction() {
        return rd;
    }

    public String modeId() {
        return modeId;
    }

    public String format() {
        return format;
    }

    public boolean entrainement() {
        return entrainement;
    }

    /** Objectif : manches gagnantes (R&D) ou eliminations (match a mort). */
    public int objectif() {
        if (rd) return DuelsConfig.RD_MANCHES_GAGNANTES;
        return Vues.FORMAT_2V2.equals(format) ? DuelsConfig.MAM_OBJECTIF_2V2 : DuelsConfig.MAM_OBJECTIF_1V1;
    }

    /** Joueurs connectes de la partie (liste tampon reutilisee : a lire tout de suite). */
    private List<ServerPlayer> enLigne() {
        tampon.clear();
        for (Fiche f : fiches.values()) {
            if (f.bot || f.parti) continue;
            ServerPlayer p = joueur(f.id);
            if (p != null) tampon.add(p);
        }
        return tampon;
    }

    /** Copie des joueurs connectes (pour les envois qui gardent la liste). */
    private List<ServerPlayer> enLigneCopie() {
        return new ArrayList<>(enLigne());
    }

    private Equipe autre(Equipe e) {
        return e == a ? b : a;
    }

    // ------------------------------------------------------------------ demarrage
    /** Instance prete : premiere mise en place (appele par {@link Defis} via {@link Instance#quandPrete}). */
    public void demarrer() {
        if (phase() != Phase.ATTENTE || instance() == null) return;
        enregistreur = KillcamLogique.enregistreur(this);
        lireSites();
        if (entrainement) poserCible();
        if (rd) {
            manche = 0;
            nouvelleManche();
        } else {
            placerTous(a, "spawn_a");
            placerTous(b, "spawn_b");
            ouvrirChoix(Component.translatable("duel.mode.match_a_mort"));
            Annonces.grande(enLigneCopie(), new Annonces.Annonce(Component.translatable("duel.annonce.mam"),
                    Component.translatable("duel.annonce.mam_sous", objectif()), Ui.BLANC, Registres.SON_MANCHE, 70, Annonces.Style.MANCHE));
        }
        envoyerHud(true);
    }

    private void lireSites() {
        Instance inst = instance();
        if (inst == null) return;
        JsonObject meta = inst.carte().meta();
        if (meta != null && meta.has("zones") && meta.get("zones").isJsonObject()) {
            JsonObject z = meta.getAsJsonObject("zones");
            siteA = boite(z.get("site_a"), inst);
            siteB = boite(z.get("site_b"), inst);
        }
        // a defaut de zone : 3 blocs autour du point site_a / site_b
        if (siteA == null && inst.point("site_a") != null) siteA = autour(Objects.requireNonNull(inst.point("site_a")), 3);
        if (siteB == null && inst.point("site_b") != null) siteB = autour(Objects.requireNonNull(inst.point("site_b")), 3);
    }

    @Nullable
    private static AABB boite(@Nullable JsonElement e, Instance inst) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray t = e.getAsJsonArray();
        if (t.size() < 6) return null;
        double ox = inst.origine().getX(), oy = inst.origine().getY(), oz = inst.origine().getZ();
        return new AABB(ox + t.get(0).getAsDouble(), oy + t.get(1).getAsDouble(), oz + t.get(2).getAsDouble(),
                ox + t.get(3).getAsDouble() + 1, oy + t.get(4).getAsDouble() + 1, oz + t.get(5).getAsDouble() + 1);
    }

    private static AABB autour(Pose p, double r) {
        return new AABB(p.x() - r, p.y() - 1, p.z() - r, p.x() + r, p.y() + 3, p.z() + r);
    }

    private void poserCible() {
        Instance inst = instance();
        if (inst == null) return;
        CibleEntrainement c = Registres.CIBLE.get().create(inst.niveau());
        if (c == null) return;
        c.partie(id());
        Pose p = inst.point("spawn_b");
        if (p == null) p = Pose.ZERO.decaler(inst.origine());
        c.moveTo(p.x(), p.y(), p.z(), p.yaw(), 0);
        c.setYHeadRot(p.yaw());
        c.setCustomName(Component.translatable("duel.cible"));
        // troncon charge avant l'ajout (sinon la cible part sur le disque avec le troncon et la partie la perd)
        inst.niveau().getChunk(net.minecraft.core.BlockPos.containing(p.x(), p.y(), p.z()));
        inst.niveau().addFreshEntity(c);
        cible = c;
        b.ajouter(c.getUUID());
        fiches.put(c.getUUID(), new Fiche(c.getUUID(), NOM_CIBLE, b, true));
        if (enregistreur != null) enregistreur.suivre(c);
    }

    // ------------------------------------------------------------------ placement
    /** Place les membres d'une equipe sur les poses d'un point (index = rang dans l'equipe), gel compris. */
    private void placerTous(Equipe e, String point) {
        Instance inst = instance();
        if (inst == null) return;
        List<Pose> poses = inst.points(point);
        int i = 0;
        for (UUID u : e.joueurs()) {
            Fiche f = fiches.get(u);
            if (f == null || f.parti) continue;
            Pose p = poses.isEmpty() ? inst.point("spectateur") : poses.get(i % poses.size());
            i++;
            if (p == null) continue;
            f.pose = p;
            e.ranimer(u);
            if (f.bot) {
                placerCible(p);
                continue;
            }
            ServerPlayer sp = joueur(u);
            if (sp == null) continue;
            Spectateurs.arreter(sp, false);
            if (enregistreur != null) KillcamLogique.arreter(sp);
            p.teleporter(sp, inst.niveau());
            preparerCorps(sp);
        }
    }

    private void placerCible(Pose p) {
        if (cible == null || cible.isRemoved()) return;
        cible.setInvisible(false);
        cible.setHealth(cible.getMaxHealth());
        cible.teleportTo(p.x(), p.y(), p.z());
        cible.setYRot(p.yaw());
        cible.setYHeadRot(p.yaw());
        cible.setYBodyRot(p.yaw());
    }

    /** Cible eliminee : mise a l'ecart (au point spectateur, invisible) jusqu'a sa reapparition. */
    private void ecarterCible() {
        if (cible == null || cible.isRemoved() || instance() == null) return;
        Pose s = instance().point("spectateur");
        cible.setInvisible(true);
        if (s != null) cible.teleportTo(s.x(), s.y() + 6, s.z());
    }

    private static void preparerCorps(ServerPlayer sp) {
        sp.setGameMode(GameType.ADVENTURE);
        sp.removeAllEffects();
        sp.clearFire();
        sp.fallDistance = 0;
        sp.setHealth(sp.getMaxHealth());
        figerFaim(sp);
    }

    // ------------------------------------------------------------------ faim figee
    /**
     * Faim imposee pendant tout le duel (retour des proprietaires : « ne pas perdre de nourriture en duel ») : barre
     * pleine, saturation 5, aucune fatigue, minuteur de regeneration a zero. Relue a chaque tick
     * ({@link #figerFaimTous}), elle ne baisse jamais (courir, sauter, encaisser ne coute rien) et la regeneration
     * naturelle de Minecraft ne se declenche pas (les soins de la classe soignent toujours). Avant, la faim s'usait
     * pendant le combat et la vie remontait un peu tant qu'elle restait haute. La faim d'avant le duel est dans le
     * Coffre ({@code Coffre.deposer} : FoodData complete, inventaire compris) et rendue a la fin, quelle qu'elle soit
     * (victoire, defaite, abandon, annulation ; deconnexion : a la reconnexion).
     */
    private static final CompoundTag FAIM_DUEL = faimDuel();

    private static CompoundTag faimDuel() {
        CompoundTag t = new CompoundTag();
        t.putInt("foodLevel", 20);
        t.putInt("foodTickTimer", 0);
        t.putFloat("foodSaturationLevel", 5f);
        t.putFloat("foodExhaustionLevel", 0f);
        return t;
    }

    private static void figerFaim(ServerPlayer sp) {
        // memes cles que FoodData.addAdditionalSaveData : le minuteur (prive) n'a pas d'autre acces
        sp.getFoodData().readAdditionalSaveData(FAIM_DUEL);
    }

    /** Faim figee de chaque joueur encore dans le duel (pas les partis : leur Coffre les a rendus ou les rendra). */
    private void figerFaimTous() {
        for (Fiche f : fiches.values()) {
            if (f.bot || f.parti) continue;
            ServerPlayer p = joueur(f.id);
            if (p != null) figerFaim(p);
        }
    }

    // ------------------------------------------------------------------ classes
    /** Ouvre le choix de classe (5 s) chez tous les joueurs connectes et passe en phase CHOIX. */
    private void ouvrirChoix(Component contexte) {
        phase(Phase.CHOIX);
        Catalogue cat = Armurerie.catalogue();
        for (ServerPlayer p : enLigneCopie()) envoyerChoix(p, contexte, cat);
    }

    private void envoyerChoix(ServerPlayer p, Component contexte, Catalogue cat) {
        Fiche f = fiches.get(p.getUUID());
        if (f == null) return;
        DuelsLogique.assurerCatalogue(p);
        MatchsNet.versJoueur(p, new PaquetsDuels.ChoixS2C(DuelsConfig.CHOIX_TICKS / 20, DuelsStore.classes(p.getUUID(), cat), f.classe, contexte,
                f.equipe.couleur(), phase() == Phase.COMBAT));
    }

    /** Choix d'une classe (paquet du client, revalide). */
    public void choisirClasse(ServerPlayer p, int n) {
        Fiche f = fiches.get(p.getUUID());
        if (f == null || f.parti || n < 0 || n >= ClassePerso.EMPLACEMENTS) return;
        if (phase() == Phase.FIN || phase() == Phase.TERMINEE) return;
        f.classe = n;
        DuelsStore.derniere(p.getUUID(), n);
        // match a mort : changement juste apres la reapparition = re-equipement immediat, fin de la protection
        if (!rd && phase() == Phase.COMBAT && f.equipe.estVivant(f.id) && f.reapparition < 0 && ticks() - f.apparition <= DuelsConfig.GRACE_CLASSE_TICKS) {
            equiper(p, f);
            f.protegeJusque = 0;
        }
    }

    /** Donne la classe choisie (equipement prete du match, sans aucun skin). */
    private void equiper(ServerPlayer p, Fiche f) {
        Catalogue cat = Armurerie.catalogue();
        ClassePerso c = DuelsStore.classe(p.getUUID(), f.classe, cat);
        Kit kit = c.versKit(p.getUUID(), f.classe, cat);
        Equipement.retirer(p);
        Equipement.donner(p, kit, id());
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (id().equals(Equipement.matchDe(s))) n += Armurerie.sansSkin(s);
        }
        if (n > 0) HeraliszMatchs.LOG.info("[duels] {} : {} marque(s) de skin retiree(s)", f.nom, n);
        // l'arme principale (case 1 de la barre) en main, quelle que soit la case choisie avant le duel
        p.getInventory().selected = 0;
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        p.inventoryMenu.broadcastChanges();
        p.setHealth(p.getMaxHealth());
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        // cible dechargee puis rechargee avec son troncon : nouvel objet, meme UUID (on le reprend, enregistrement compris)
        if (cible != null && cible.isRemoved() && cible.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK && instance() != null) {
            if (instance().niveau().getEntity(cible.getUUID()) instanceof CibleEntrainement c && !c.isRemoved()) {
                cible = c;
                if (enregistreur != null) enregistreur.suivre(c);
            }
        }
        // avant les phases : un joueur rendu dans ce tick (fin, annulation) est deja marque parti au tick suivant
        if (phase() != Phase.TERMINEE) figerFaimTous();
        switch (phase()) {
            case ATTENTE -> {
                if (ticksPhase() > DuelsConfig.ATTENTE_MAX_TICKS) annuler(Component.translatable("duel.annule.arene"));
            }
            case CHOIX -> tickChoix();
            case COMBAT -> {
                if (rd) tickRd();
                else tickMam();
            }
            case PREPARATION -> tickIntermanche();
            case FIN -> tickFin();
            default -> {
            }
        }
        if (phase() != Phase.TERMINEE && ticks() % 20 == 0) {
            envoyerHud(false);
            DuelsStore.sauverSiBesoin(false);
        }
    }

    private void tickChoix() {
        // gel : chacun reste sur sa pose (la tete tourne librement)
        for (Fiche f : fiches.values()) {
            if (f.bot || f.parti || f.pose == null) continue;
            ServerPlayer p = joueur(f.id);
            if (p == null) continue;
            double dx = p.getX() - f.pose.x(), dz = p.getZ() - f.pose.z(), dy = p.getY() - f.pose.y();
            if (dx * dx + dz * dz > 0.04 || dy > 0.6 || dy < -0.6) p.connection.teleport(f.pose.x(), f.pose.y(), f.pose.z(), p.getYRot(), p.getXRot());
        }
        if (ticksPhase() < DuelsConfig.CHOIX_TICKS) return;
        for (Fiche f : fiches.values()) {
            if (f.bot || f.parti) continue;
            ServerPlayer p = joueur(f.id);
            if (p == null) continue;
            equiper(p, f);
            f.apparition = ticks();
            MatchsNet.versJoueur(p, new PaquetsDuels.ChoixS2C(0, List.of(), f.classe, Component.empty(), 0, true));
        }
        phase(Phase.COMBAT);
        Annonces.grande(enLigneCopie(), new Annonces.Annonce(Component.translatable("duel.annonce.combat"), Component.empty(), Ui.BLANC, "", 30,
                Annonces.Style.NORMALE));
        if (rd) synchroniserRd(true);
        envoyerHud(true);
    }

    private void tickFin() {
        if (ticksPhase() == 50 && !annulee) {
            Vues.Tableau t = tableau();
            MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.ScoresS2C(t));
        }
        if (ticksPhase() >= DuelsConfig.FIN_TICKS) {
            rendreTous();
            fermer();
        }
    }

    // ------------------------------------------------------------------ elimination
    @Override
    public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        Fiche f = fiches.get(victime.getUUID());
        if (f == null || f.parti) return;
        if (phase() != Phase.COMBAT) {
            // chute ou degats hors combat : on remet le joueur sur sa pose, sans compter
            victime.setHealth(victime.getMaxHealth());
            if (f.pose != null && instance() != null && !victime.isSpectator()) f.pose.teleporter(victime, instance().niveau());
            return;
        }
        // deja elimine (spectateur sorti des bornes, double appel) : rien a compter
        if (!f.equipe.estVivant(f.id)) return;
        compter(f, tueur, victime);
        if (enregistreur != null) KillcamLogique.surElimination(this, victime, tueur, !rd);
        f.equipe.eliminer(f.id);
        victime.setHealth(victime.getMaxHealth());
        victime.clearFire();
        Equipement.retirer(victime);
        if (rd) {
            lacherChargeSiPorteur(f.id, victime.getX(), victime.getY(), victime.getZ());
            Spectateurs.suivre(victime, this);
            verifierFinManche();
        } else {
            Spectateurs.suivre(victime, this);
            f.reapparition = DuelsConfig.MAM_REAPPARITION_TICKS;
            marquerMam(f, tueur);
        }
        envoyerHud(true);
    }

    /** Elimination de la cible d'entrainement (appelee par l'entite a « 0 PV »). */
    public void eliminerCible(CibleEntrainement c, @Nullable LivingEntity tueur, DamageSource source) {
        Fiche f = fiches.get(c.getUUID());
        if (f == null || phase() != Phase.COMBAT || !f.equipe.estVivant(f.id)) return;
        compter(f, tueur, c);
        // R&D : la derniere elimination (meme celle de la cible) est rejouee en fin de manche
        if (enregistreur != null && rd) KillcamLogique.garderElimination(this, c, tueur);
        f.equipe.eliminer(f.id);
        ecarterCible();
        if (rd) {
            verifierFinManche();
        } else {
            f.reapparition = DuelsConfig.MAM_REAPPARITION_TICKS;
            marquerMam(f, tueur);
        }
        envoyerHud(true);
    }

    /** Vrai si la cible d'entrainement peut prendre des degats (combat, vivante). */
    public boolean cibleVulnerable() {
        return phase() == Phase.COMBAT && cible != null && b.estVivant(cible.getUUID());
    }

    /** Statistiques, fil des eliminations. */
    private void compter(Fiche victime, @Nullable LivingEntity tueur, Entity entiteVictime) {
        TirsTacz.Tir tir = TirsTacz.consommer(entiteVictime);
        boolean tete = tir != null && tir.tete();
        String arme = tir != null ? tir.arme() : TirsTacz.armeEnMain(tueur);
        victime.morts++;
        Fiche ft = tueur == null ? null : fiches.get(tueur.getUUID());
        boolean adverse = ft != null && ft.equipe != victime.equipe;
        if (adverse) {
            ft.elims++;
            if (tete) ft.tetes++;
        }
        KillFeed.ajouter(enLigneCopie(), new KillFeed.Ligne(ft == null || ft == victime ? "" : ft.nom, ft == null ? 0xFFFFFF : ft.equipe.couleur(),
                victime.nom, victime.equipe.couleur(), ft == null ? "" : TirsTacz.nomCourt(arme), adverse && tete));
    }

    private void marquerMam(Fiche victime, @Nullable LivingEntity tueur) {
        Fiche ft = tueur == null ? null : fiches.get(tueur.getUUID());
        if (ft == null || ft.equipe == victime.equipe) return;
        ft.equipe.ajouterScore(1);
        if (ft.equipe.score() >= objectif()) terminer(ft.equipe);
    }

    @Override
    public float surDegats(ServerPlayer victime, @Nullable LivingEntity attaquant, DamageSource source, float montant) {
        if (phase() != Phase.COMBAT) return 0f;
        Fiche fv = fiches.get(victime.getUUID());
        if (fv == null || fv.parti || !fv.equipe.estVivant(fv.id)) return 0f;
        if (fv.protegeJusque > ticks()) return 0f;
        if (attaquant != null && attaquant != victime) {
            Fiche fa = fiches.get(attaquant.getUUID());
            if (fa != null && fa.equipe == fv.equipe) return 0f;
            if (fa != null && fa.protegeJusque > ticks()) fa.protegeJusque = 0;
        }
        return montant;
    }

    // ------------------------------------------------------------------ match a mort
    private void tickMam() {
        for (Fiche f : fiches.values()) {
            if (f.parti) continue;
            if (f.reapparition > 0) f.reapparition--;
            if (f.reapparition == 0 && (f.bot || !KillcamLogique.enCours(f.id))) reapparaitre(f);
            // la protection d'apparition tombe des que le joueur s'eloigne de sa pose
            if (!f.bot && f.protegeJusque > ticks() && f.pose != null) {
                ServerPlayer p = joueur(f.id);
                if (p != null && f.pose.distanceCarre(p.getX(), p.getY(), p.getZ()) > 2.25) f.protegeJusque = 0;
            }
        }
        if (phase() == Phase.COMBAT && ticksPhase() >= DuelsConfig.MAM_DUREE_TICKS) {
            terminer(a.score() == b.score() ? null : a.score() > b.score() ? a : b);
        }
    }

    /** Reapparition loin des ennemis (point qui maximise la distance au plus proche ennemi vivant). */
    private void reapparaitre(Fiche f) {
        Instance inst = instance();
        if (inst == null) return;
        f.reapparition = -1;
        Pose p = pointLoin(f.equipe, inst);
        if (p == null) return;
        f.pose = p;
        f.apparition = ticks();
        f.equipe.ranimer(f.id);
        if (f.bot) {
            placerCible(p);
            return;
        }
        ServerPlayer sp = joueur(f.id);
        if (sp == null) return;
        Spectateurs.arreter(sp, false);
        KillcamLogique.arreter(sp);
        p.teleporter(sp, inst.niveau());
        preparerCorps(sp);
        equiper(sp, f);
        f.protegeJusque = ticks() + DuelsConfig.CHOIX_TICKS;
        envoyerChoix(sp, Component.translatable("duel.choix.reapparition"), Armurerie.catalogue());
    }

    @Nullable
    private Pose pointLoin(Equipe equipe, Instance inst) {
        Pose meilleur = null;
        double score = -1;
        for (String nom : new String[]{"respawn_a", "respawn_b"}) {
            for (Pose p : inst.points(nom)) {
                double min = Double.MAX_VALUE;
                Equipe ennemie = autre(equipe);
                for (UUID u : ennemie.vivants()) {
                    Entity e = entite(u);
                    if (e != null) min = Math.min(min, p.distanceCarre(e.getX(), e.getY(), e.getZ()));
                }
                // leger bruit pour varier entre points equivalents
                double s = (min == Double.MAX_VALUE ? 1e6 : min) * (0.9 + 0.2 * Math.random());
                if (s > score) {
                    score = s;
                    meilleur = p;
                }
            }
        }
        if (meilleur == null) meilleur = inst.point(equipe == a ? "spawn_a" : "spawn_b");
        return meilleur;
    }

    @Nullable
    private Entity entite(UUID u) {
        if (cible != null && cible.getUUID().equals(u)) return cible.isRemoved() ? null : cible;
        return joueur(u);
    }

    // ------------------------------------------------------------------ recherche et destruction
    private void nouvelleManche() {
        manche++;
        if (manche == DuelsConfig.RD_CHANGEMENT_COTE + 1) {
            Equipe t = attaquants;
            attaquants = defenseurs;
            defenseurs = t;
            Annonces.grande(enLigneCopie(), new Annonces.Annonce(Component.translatable("duel.annonce.changement"),
                    Component.translatable("duel.annonce.changement_sous"), Ui.OR, Registres.SON_MANCHE, 60, Annonces.Style.NORMALE));
        }
        retirerAffichageCharge();
        // la killcam de fin de manche ne montre qu'une elimination de cette manche
        if (enregistreur != null) enregistreur.oublierDerniere();
        progression = 0;
        acteur = null;
        siteCharge = "";
        minuterie = 0;
        placerTous(attaquants, "spawn_a");
        placerTous(defenseurs, "spawn_b");
        // la charge va a un attaquant connecte au hasard, sinon elle attend au point « charge »
        List<UUID> candidats = new ArrayList<>();
        for (UUID u : attaquants.joueurs()) {
            Fiche f = fiches.get(u);
            if (f != null && !f.bot && !f.parti && joueur(u) != null) candidats.add(u);
        }
        if (!candidats.isEmpty()) {
            porteur = candidats.get((int) (Math.random() * candidats.size()));
            etatCharge = Vues.CHARGE_PORTEE;
        } else {
            porteur = null;
            Pose c = instance() == null ? null : instance().point("charge");
            if (c != null) poserAuSol(c.x(), c.y(), c.z());
            else etatCharge = Vues.CHARGE_AUCUNE;
        }
        for (ServerPlayer p : enLigneCopie()) {
            Fiche f = fiches.get(p.getUUID());
            boolean att = f != null && f.equipe == attaquants;
            Annonces.grande(p, new Annonces.Annonce(Component.translatable("duel.annonce.manche", manche),
                    Component.translatable(att ? "duel.annonce.attaque" : "duel.annonce.defense"), f == null ? Ui.BLANC : f.equipe.couleur(),
                    Registres.SON_MANCHE, 70, Annonces.Style.MANCHE));
        }
        ouvrirChoixRd();
        synchroniserRd(true);
    }

    private void ouvrirChoixRd() {
        phase(Phase.CHOIX);
        Catalogue cat = Armurerie.catalogue();
        for (ServerPlayer p : enLigneCopie()) {
            Fiche f = fiches.get(p.getUUID());
            boolean att = f != null && f.equipe == attaquants;
            envoyerChoix(p, Component.translatable("duel.choix.manche", manche, Component.translatable(att ? "duel.role.attaque" : "duel.role.defense")), cat);
        }
    }

    private void tickRd() {
        if (etatCharge != Vues.CHARGE_POSEE && ticksPhase() >= DuelsConfig.RD_MANCHE_TICKS) {
            finManche(defenseurs, "duel.manche.temps");
            return;
        }
        switch (etatCharge) {
            case Vues.CHARGE_PORTEE -> tickPorteur();
            case Vues.CHARGE_AU_SOL -> tickAuSol();
            case Vues.CHARGE_POSEE -> tickPosee();
            default -> {
            }
        }
        if (phase() == Phase.COMBAT && (ticks() % 5 == 0)) synchroniserRd(ticks() % 100 == 0);
    }

    private void tickPorteur() {
        ServerPlayer p = porteur == null ? null : joueur(porteur);
        Fiche f = porteur == null ? null : fiches.get(porteur);
        if (p == null || f == null || f.parti || !f.equipe.estVivant(f.id)) {
            if (p != null) lacherChargeSiPorteur(p.getUUID(), p.getX(), p.getY(), p.getZ());
            else {
                Pose c = instance() == null ? null : instance().point("charge");
                if (c != null) poserAuSol(c.x(), c.y(), c.z());
            }
            return;
        }
        String site = siteDe(p);
        boolean pose = !site.isEmpty() && p.isShiftKeyDown() && p.onGround();
        if (pose) {
            if (progression == 0) {
                acteur = p.getUUID();
                synchroniserRd(true);
            }
            progression++;
            if (progression >= DuelsConfig.RD_POSE_TICKS) poser(p, site);
        } else if (progression > 0) {
            progression = 0;
            acteur = null;
            synchroniserRd(true);
        }
    }

    private String siteDe(Entity e) {
        if (siteA != null && siteA.contains(e.getX(), e.getY() + 0.1, e.getZ())) return "A";
        if (siteB != null && siteB.contains(e.getX(), e.getY() + 0.1, e.getZ())) return "B";
        return "";
    }

    private void poser(ServerPlayer p, String site) {
        etatCharge = Vues.CHARGE_POSEE;
        siteCharge = site;
        porteur = null;
        progression = 0;
        acteur = null;
        minuterie = DuelsConfig.RD_MINUTERIE_TICKS;
        cx = p.getX();
        cy = p.getY();
        cz = p.getZ();
        afficherCharge(cx, cy, cz);
        MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.POSEE, cx, cy, cz));
        for (ServerPlayer q : enLigneCopie()) {
            Fiche f = fiches.get(q.getUUID());
            boolean att = f != null && f.equipe == attaquants;
            Annonces.grande(q, new Annonces.Annonce(Component.translatable("duel.annonce.posee", site),
                    Component.translatable(att ? "duel.annonce.posee_att" : "duel.annonce.posee_def"), Ui.DANGER, Registres.SON_POSE, 50,
                    Annonces.Style.NORMALE));
        }
        synchroniserRd(true);
        envoyerHud(true);
    }

    private void tickAuSol() {
        for (UUID u : attaquants.vivants()) {
            Fiche f = fiches.get(u);
            if (f == null || f.bot || f.parti) continue;
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= DuelsConfig.RD_RAMASSAGE * DuelsConfig.RD_RAMASSAGE) {
                porteur = u;
                etatCharge = Vues.CHARGE_PORTEE;
                retirerAffichageCharge();
                son(p, Registres.RAMASSE.get(), 0.8f);
                synchroniserRd(true);
                return;
            }
        }
    }

    private void tickPosee() {
        minuterie--;
        if (minuterie <= 0) {
            exploser();
            return;
        }
        // desamorcage : un defenseur vivant, accroupi, a portee ; un seul a la fois
        ServerPlayer d = acteur == null ? null : joueur(acteur);
        if (d != null && !peutDesamorcer(d)) d = null;
        if (d == null) {
            for (UUID u : defenseurs.vivants()) {
                ServerPlayer p = joueur(u);
                if (p != null && peutDesamorcer(p)) {
                    d = p;
                    break;
                }
            }
        }
        if (d == null) {
            if (progression > 0) {
                progression = 0;
                acteur = null;
                synchroniserRd(true);
            }
            return;
        }
        if (acteur == null || !acteur.equals(d.getUUID())) {
            acteur = d.getUUID();
            progression = 0;
            synchroniserRd(true);
        }
        progression++;
        if (progression >= DuelsConfig.RD_DESAMORCAGE_TICKS) {
            etatCharge = Vues.CHARGE_DESAMORCEE;
            progression = 0;
            acteur = null;
            MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.DESAMORCEE, cx, cy, cz));
            finManche(defenseurs, "duel.manche.desamorcee");
        }
    }

    private boolean peutDesamorcer(ServerPlayer p) {
        Fiche f = fiches.get(p.getUUID());
        if (f == null || f.parti || f.equipe != defenseurs || !f.equipe.estVivant(f.id) || p.isSpectator()) return false;
        double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
        return p.isShiftKeyDown() && dx * dx + dy * dy + dz * dz <= DuelsConfig.RD_DESAMORCAGE_PORTEE * DuelsConfig.RD_DESAMORCAGE_PORTEE;
    }

    private void exploser() {
        etatCharge = Vues.CHARGE_EXPLOSEE;
        retirerAffichageCharge();
        MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.EXPLOSION, cx, cy, cz));
        finManche(attaquants, "duel.manche.explosee");
    }

    private void lacherChargeSiPorteur(UUID u, double x, double y, double z) {
        if (etatCharge != Vues.CHARGE_PORTEE || !u.equals(porteur)) return;
        Instance inst = instance();
        porteur = null;
        progression = 0;
        acteur = null;
        if (inst == null || !inst.contient(x, y, z)) {
            Pose c = inst == null ? null : inst.point("charge");
            if (c != null) poserAuSol(c.x(), c.y(), c.z());
            return;
        }
        poserAuSol(x, y, z);
    }

    private void poserAuSol(double x, double y, double z) {
        etatCharge = Vues.CHARGE_AU_SOL;
        cx = x;
        cy = y;
        cz = z;
        afficherCharge(x, y, z);
        synchroniserRd(true);
    }

    private void verifierFinManche() {
        if (phase() != Phase.COMBAT) return;
        boolean defVivants = vivantsActifs(defenseurs), attVivants = vivantsActifs(attaquants);
        if (!defVivants) finManche(attaquants, "duel.manche.eliminee");
        else if (!attVivants && etatCharge != Vues.CHARGE_POSEE) finManche(defenseurs, "duel.manche.eliminee");
    }

    private boolean vivantsActifs(Equipe e) {
        for (UUID u : e.vivants()) {
            Fiche f = fiches.get(u);
            if (f != null && !f.parti) return true;
        }
        return false;
    }

    private void finManche(Equipe gagnante, String raison) {
        if (phase() != Phase.COMBAT) return;
        gagnante.ajouterScore(1);
        manches.add(gagnante.id());
        gagnanteManche = gagnante;
        progression = 0;
        acteur = null;
        for (ServerPlayer p : enLigneCopie()) {
            Fiche f = fiches.get(p.getUUID());
            boolean gagne = f != null && f.equipe == gagnante;
            Annonces.grande(p, new Annonces.Annonce(Component.translatable(gagne ? "duel.annonce.manche_gagnee" : "duel.annonce.manche_perdue"),
                    Component.translatable(raison), gagne ? gagnante.couleur() : Ui.DANGER, gagne ? Registres.SON_VICTOIRE : Registres.SON_DEFAITE, 70,
                    gagne ? Annonces.Style.VICTOIRE : Annonces.Style.DEFAITE));
        }
        finApresIntermanche = gagnante.score() >= DuelsConfig.RD_MANCHES_GAGNANTES;
        killcamFinManche = false;
        phase(Phase.PREPARATION);
        synchroniserRd(true);
        envoyerHud(true);
    }

    private void tickIntermanche() {
        int t = ticksPhase();
        if (t == 40 && enregistreur != null) {
            killcamFinManche = KillcamLogique.rejouerDerniere(this, enLigneCopie()) > 0;
        }
        if (t < DuelsConfig.RD_INTERMANCHE_TICKS) return;
        if (killcamFinManche && t < DuelsConfig.RD_INTERMANCHE_TICKS + 100) {
            for (ServerPlayer p : enLigne()) if (KillcamLogique.enCours(p.getUUID())) return;
        }
        if (finApresIntermanche && gagnanteManche != null) terminer(gagnanteManche);
        else nouvelleManche();
    }

    // ------------------------------------------------------------------ charge : affichage
    private void afficherCharge(double x, double y, double z) {
        retirerAffichageCharge();
        Instance inst = instance();
        if (inst == null) return;
        ServerLevel niveau = inst.niveau();
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:item_display");
        CompoundTag item = new CompoundTag();
        item.putString("id", HeraliszMatchs.MODID + ":duel_charge");
        item.putByte("Count", (byte) 1);
        t.put("item", item);
        t.putString("item_display", "fixed");
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf("heralisz_duel_charge"));
        t.put("Tags", tags);
        Entity e = EntityType.loadEntityRecursive(t, niveau, ent -> {
            ent.moveTo(x, y + 0.12, z, 0f, 0f);
            return ent;
        });
        if (e != null && niveau.addFreshEntity(e)) affichageCharge = e;
    }

    private void retirerAffichageCharge() {
        if (affichageCharge != null) {
            affichageCharge.discard();
            affichageCharge = null;
        }
    }

    // ------------------------------------------------------------------ synchronisation R&D
    /** Envoie a chacun l'etat de la charge s'il a change pour lui (force = renvoi). */
    private void synchroniserRd(boolean force) {
        if (!rd) return;
        for (ServerPlayer p : enLigne()) {
            Fiche f = fiches.get(p.getUUID());
            if (f == null) continue;
            boolean att = f.equipe == attaquants;
            boolean moi = p.getUUID().equals(porteur);
            int action = Vues.ACTION_AUCUNE;
            if (acteur != null && acteur.equals(p.getUUID()) && progression > 0) {
                action = etatCharge == Vues.CHARGE_POSEE ? Vues.ACTION_DESAMORCAGE : Vues.ACTION_POSE;
            }
            String invite = "";
            if (phase() == Phase.COMBAT && f.equipe.estVivant(f.id) && action == Vues.ACTION_AUCUNE) {
                if (moi && !siteDe(p).isEmpty()) invite = "poser";
                else if (!att && etatCharge == Vues.CHARGE_POSEE) {
                    double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
                    if (dx * dx + dy * dy + dz * dz <= DuelsConfig.RD_DESAMORCAGE_PORTEE * DuelsConfig.RD_DESAMORCAGE_PORTEE) invite = "desamorcer";
                }
            }
            // signature sans allocation (pas de boxing) : renvoi seulement si elle change
            int code = invite.isEmpty() ? 0 : invite.equals("poser") ? 1 : 2;
            int sig = (att ? 1 : 0) | etatCharge << 1 | (moi ? 1 : 0) << 4 | action << 5 | code << 7 | phase().ordinal() << 9 | manche << 12;
            sig = sig * 31 + (porteur == null ? 0 : porteur.hashCode());
            sig = sig * 31 + siteCharge.hashCode();
            Integer avant = signatures.get(p.getUUID());
            if (!force && avant != null && avant == sig) continue;
            signatures.put(p.getUUID(), sig);
            boolean voitCharge = att || etatCharge == Vues.CHARGE_POSEE;
            Fiche fp = porteur == null ? null : fiches.get(porteur);
            int total = action == Vues.ACTION_DESAMORCAGE ? DuelsConfig.RD_DESAMORCAGE_TICKS : DuelsConfig.RD_POSE_TICKS;
            double[] sites = new double[6];
            if (siteA != null) centre(siteA, sites, 0);
            if (siteB != null) centre(siteB, sites, 3);
            Vues.RdVue v = new Vues.RdVue(att, phase() == Phase.COMBAT || phase() == Phase.CHOIX ? etatCharge : Vues.CHARGE_AUCUNE,
                    att && fp != null ? fp.nom : "", moi, siteCharge, voitCharge ? cx : 0, voitCharge ? cy : 0, voitCharge ? cz : 0,
                    etatCharge == Vues.CHARGE_POSEE ? minuterie : 0, action, progression, total, invite, sites);
            MatchsNet.versJoueur(p, new PaquetsDuels.RdS2C(v));
        }
    }

    private static void centre(AABB b, double[] d, int i) {
        d[i] = (b.minX + b.maxX) / 2;
        d[i + 1] = b.minY + 0.5;
        d[i + 2] = (b.minZ + b.maxZ) / 2;
    }

    // ------------------------------------------------------------------ HUD
    private void envoyerHud(boolean force) {
        int secondes;
        if (phase() == Phase.CHOIX) secondes = Math.max(0, (DuelsConfig.CHOIX_TICKS - ticksPhase()) / 20);
        else if (phase() == Phase.COMBAT && rd && etatCharge != Vues.CHARGE_POSEE) secondes = Math.max(0, (DuelsConfig.RD_MANCHE_TICKS - ticksPhase()) / 20);
        else if (phase() == Phase.COMBAT && !rd) secondes = Math.max(0, (DuelsConfig.MAM_DUREE_TICKS - ticksPhase()) / 20);
        else secondes = -1;
        int sig = Objects.hash(phase(), secondes < 0, a.score(), b.score(), a.nbVivants(), b.nbVivants(), manche, etatCharge == Vues.CHARGE_POSEE);
        // le temps est decompte par le client : on ne renvoie qu'aux changements (et toutes les 15 s pour recaler)
        if (!force && sig == dernierHud && ticks() % 300 != 0) return;
        dernierHud = sig;
        Component titre = rd ? Component.translatable("duel.hud.rd", manche) : Component.translatable("duel.hud.mam", objectif());
        Component score = Component.literal(a.score() + "  —  " + b.score());
        Hud.envoyer(enLigneCopie(), Hud.bandeau(this, titre, secondes, score));
    }

    // ------------------------------------------------------------------ fin
    @Override
    protected void surFin(@Nullable Equipe gagnante) {
        retirerAffichageCharge();
        etatCharge = Vues.CHARGE_AUCUNE;
        if (annulee) return;
        // statistiques globales (jamais les stats de clan)
        if (!entrainement) {
            for (Fiche f : fiches.values()) {
                if (f.bot) continue;
                DuelsStore.Stats s = DuelsStore.stats(f.id);
                s.nom = f.nom;
                s.parties++;
                if (gagnante != null) {
                    if (f.equipe == gagnante) s.victoires++;
                    else s.defaites++;
                }
                s.eliminations += f.elims;
                s.morts += f.morts;
                s.tetes += f.tetes;
                if (rd) for (String m : manches) if (m.equals(f.equipe.id())) s.manches++;
            }
            DuelsStore.statsModifiees();
            DuelsStore.sauverSiBesoin(true);
        }
        for (ServerPlayer p : enLigneCopie()) {
            Fiche f = fiches.get(p.getUUID());
            Spectateurs.arreter(p, false);
            Equipement.retirer(p);
            p.setGameMode(GameType.ADVENTURE);
            p.setHealth(p.getMaxHealth());
            MatchsNet.versJoueur(p, new PaquetsDuels.RdS2C(null));
            boolean gagne = gagnante != null && f != null && f.equipe == gagnante;
            Component titre = Component.translatable(gagnante == null ? "duel.annonce.egalite" : gagne ? "duel.annonce.victoire" : "duel.annonce.defaite");
            Component sous = Component.literal(a.score() + "  —  " + b.score());
            Annonces.grande(p, new Annonces.Annonce(titre, sous, gagnante == null ? Ui.BLANC : gagne ? Ui.OR : Ui.DANGER,
                    gagne ? Registres.SON_VICTOIRE : Registres.SON_DEFAITE, 60, gagne ? Annonces.Style.VICTOIRE : Annonces.Style.DEFAITE));
        }
        if (cible != null) ecarterCible();
        envoyerHud(true);
    }

    /** Tableau des scores final. */
    public Vues.Tableau tableau() {
        List<Vues.EquipeScore> eq = List.of(new Vues.EquipeScore(a.id(), a.nom(), a.couleur(), a.score()),
                new Vues.EquipeScore(b.id(), b.nom(), b.couleur(), b.score()));
        List<Vues.JoueurScore> js = new ArrayList<>();
        for (Fiche f : fiches.values()) js.add(new Vues.JoueurScore(f.id, f.nom, f.equipe.id(), f.elims, f.morts, f.tetes, f.bot));
        Equipe g = gagnante();
        Instance inst = instance();
        String carte = inst != null ? inst.carte().nomFr() : carteId;
        return new Vues.Tableau(modeId, format, carte, g == null ? "" : g.id(), eq, js, List.copyOf(manches), objectif(),
                (int) ((System.currentTimeMillis() - debutMs()) / 1000), entrainement);
    }

    /** Rend tout le monde (Coffre, drapeau, HUD) ; la cible d'entrainement disparait. */
    private void rendreTous() {
        for (Fiche f : fiches.values()) {
            if (f.bot || f.parti) continue;
            ServerPlayer p = joueur(f.id);
            if (p != null) rendre(p);
            else MatchApi.quitter(f.id);
            f.parti = true;
        }
        if (cible != null && !cible.isRemoved()) cible.discard();
        cible = null;
        retirerAffichageCharge();
    }

    private void rendre(ServerPlayer p) {
        Spectateurs.arreter(p, false);
        KillcamLogique.arreter(p);
        Equipement.retirer(p);
        Hud.effacer(p);
        MatchsNet.versJoueur(p, new PaquetsDuels.RdS2C(null));
        MatchsNet.versJoueur(p, new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.FIN, 0, 0, 0));
        boolean ok = Coffre.rendre(p);
        if (!ok) {
            // depot introuvable ou illisible (garde par le moteur pour enquete) : jamais coince en spectateur
            HeraliszMatchs.LOG.warn("[duels] {} : Coffre de {} non rendu", id(), p.getGameProfile().getName());
            if (p.isSpectator() || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
        }
        MatchApi.quitter(p);
    }

    /** Annule le duel (arene indisponible, erreur) : personne ne gagne, pas de statistiques, Coffres rendus. */
    public void annuler(Component raison) {
        if (phase() == Phase.TERMINEE) return;
        annulee = true;
        for (ServerPlayer p : enLigneCopie()) Annonces.chat(p, raison);
        terminer(null);
        rendreTous();
        fermer();
    }

    // ------------------------------------------------------------------ deconnexion, abandon
    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        retirerJoueur(joueur, false);
    }

    /** Abandon volontaire ({@code /duel quitter}) : le joueur est rendu tout de suite. */
    public void abandonner(ServerPlayer joueur) {
        retirerJoueur(joueur, true);
    }

    private void retirerJoueur(ServerPlayer joueur, boolean rendreMaintenant) {
        Fiche f = fiches.get(joueur.getUUID());
        if (f == null || f.parti) return;
        if (phase() == Phase.FIN || phase() == Phase.TERMINEE) {
            if (rendreMaintenant) {
                f.parti = true;
                rendre(joueur);
            }
            return;
        }
        f.parti = true;
        lacherChargeSiPorteur(f.id, joueur.getX(), joueur.getY(), joueur.getZ());
        f.equipe.eliminer(f.id);
        if (acteur != null && acteur.equals(f.id)) {
            acteur = null;
            progression = 0;
        }
        if (rendreMaintenant) rendre(joueur);
        else {
            Spectateurs.arreter(joueur, false);
            MatchApi.quitter(joueur.getUUID());
        }
        for (ServerPlayer p : enLigneCopie()) Annonces.chat(p, Component.translatable("duel.chat.parti", f.nom));
        // equipe vide = forfait
        boolean reste = false;
        for (UUID u : f.equipe.joueurs()) {
            Fiche x = fiches.get(u);
            if (x != null && !x.parti && (x.bot || joueur(u) != null)) reste = true;
        }
        if (!reste) {
            if (phase() == Phase.ATTENTE) annuler(Component.translatable("duel.annule.forfait"));
            else terminer(autre(f.equipe));
        } else if (rd) {
            verifierFinManche();
        }
        envoyerHud(true);
    }

    @Override
    public boolean surReconnexion(ServerPlayer joueur) {
        return false;
    }

    // ------------------------------------------------------------------ aides
    private static void son(ServerPlayer p, SoundEvent s, float volume) {
        p.playNotifySound(s, SoundSource.PLAYERS, volume, 1f);
    }

    /** Nombre de joueurs reels (hors cible). */
    public int nbJoueurs() {
        int n = 0;
        for (Fiche f : fiches.values()) if (!f.bot) n++;
        return n;
    }

    /** Participants (UUID, y compris partis), pour les commandes et les journaux. */
    public Collection<UUID> participants() {
        return fiches.keySet();
    }

    /** Couleurs fixes des annonces. */
    static final class Ui {
        static final int BLANC = 0xECEEF2, OR = 0xE8C36A, DANGER = 0xFF6B6B;
    }

    @Override
    public String toString() {
        return "DuelPartie[" + id() + " " + modeId + " " + format + " " + phase() + " " + a.score() + "-" + b.score()
                + (rd ? " manche " + manche + " charge " + etatCharge : "") + "]";
    }
}
