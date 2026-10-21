package fr.heralisz.matchs.duels;

import fr.heralisz.matchs.duels.killcam.KillcamLogique;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.armes.Armurerie;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.armes.TirsTacz;
import fr.heralisz.matchs.duels.bombe.BombeDuel;
import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import fr.heralisz.matchs.duels.bombe.ReglesBombe;
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
import fr.heralisz.matchs.moteur.arenes.ArenesService;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 * <b>recherche et destruction</b> (une vie par manche, bombes des sites A/B amorcees avec l'ordinateur porte par un
 * attaquant, pirate par un defenseur ; premier a 4 manches, changement de cote apres 3 manches) ou <b>match a mort</b>
 * (reapparition apres 3 s loin des ennemis, premier a 20 / 30 eliminations ou 10 min). Plus de killcam depuis Matchs
 * 0.11.0 ({@link DuelsConfig#KILLCAM} : rien n'est enregistre, le mort passe tout de suite en spectateur).
 * <p>Matchs 0.11.0 : arene remise au propre a chaque debut de manche et a la fin ({@link #nettoyerArene} : grenades, C4,
 * fumigenes, feu au sol, objets au sol, balles ; feux eteints ; particules effacees chez les joueurs) ; le joueur qui
 * amorce ou pirate est immobilise, sans tir, visee, rechargement ni changement d'arme ({@link #majAction},
 * {@link ReglesBombe}). Classes choisies en debut de manche ou a
 * chaque reapparition (5 s) : <b>les 5 classes de l'hote</b> (le defiant, qui a lance le defi), les memes pour tous les
 * joueurs des deux equipes, copiees au lancement ({@link #classesHote}) ; en entrainement, chacun ses propres classes.
 * Fin : tableau des scores, statistiques de duel, Coffre rendu.
 * <p>Phases ({@link Partie.Phase}) : ATTENTE (pose de l'instance) -> CHOIX (gel, choix de classe) -> COMBAT ->
 * PREPARATION (entre deux manches de R&D : annonces) -> ... -> FIN (scores) -> TERMINEE.
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
    /**
     * Les 5 classes proposees a tous les joueurs : celles de l'hote (premier du camp A = le defiant), copiees a la
     * construction de la partie et jamais relues (l'hote peut modifier ses classes ou se deconnecter, rien ne change
     * pour ce duel). Null en entrainement : le joueur choisit dans ses propres classes, lues a jour.
     */
    @Nullable
    private final List<ClassePerso> classesHote;
    /** Hote du duel (proprietaire des {@link #classesHote}) et son pseudo, affiche a l'ecran de choix. */
    @Nullable
    private final UUID hote;
    private final String nomHote;
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
    /** Joueur immobilise en ce moment (amorce ou pirate), voir {@link #majAction}. */
    @Nullable
    private UUID bloque;

    // ------------------------------------------------------------------ recherche et destruction
    private int manche;
    private Equipe attaquants, defenseurs;
    /**
     * Etat de « la charge » ({@code Vues.CHARGE_*}) : l'ordinateur d'amorcage porte par un attaquant, au sol (porteur
     * tue), pose et relie a la bombe amorcee (POSEE), puis la bombe desamorcee ou explosee.
     */
    private int etatCharge = Vues.CHARGE_AUCUNE;
    @Nullable
    private UUID porteur;
    /** Position de l'ordinateur pose (au sol ferme, en amorcage, amorce, retombe apres un piratage) et son orientation. */
    private double cx, cy, cz;
    private float lacet;
    /** Site de la bombe en cours d'amorcage ou amorcee ("A", "B", vide). */
    private String siteCharge = "";
    private int minuterie;
    private int progression;
    /** Attaquant qui amorce (charge portee) ou defenseur qui pirate (charge posee). */
    @Nullable
    private UUID acteur;
    /** Temps du monde au debut de l'action en cours et a l'explosion prevue (lus par l'ecran de l'ordinateur). */
    private int debutAction, finMinuterie;
    /** Bombes posees sur les sites pendant le duel (points bombe_a / bombe_b) et ordinateur pose. */
    @Nullable
    private Pose poseBombeA, poseBombeB;
    @Nullable
    private BombeDuel bombeA, bombeB;
    @Nullable
    private OrdinateurDuel ordinateur;
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
        ServerPlayer h = campA.isEmpty() ? null : campA.get(0);
        this.hote = h == null ? null : h.getUUID();
        this.nomHote = h == null ? "" : h.getGameProfile().getName();
        this.classesHote = entrainement || h == null ? null : List.copyOf(DuelsStore.classes(h.getUUID(), Armurerie.catalogue()));
        this.a = new Equipe("a", nomCamp(campA, "A"), DuelsConfig.COULEUR_A);
        this.b = new Equipe("b", entrainement ? NOM_CIBLE : nomCamp(campB, "B"), DuelsConfig.COULEUR_B);
        ajouterEquipe(a);
        ajouterEquipe(b);
        for (ServerPlayer p : campA) inscrire(p, a);
        for (ServerPlayer p : campB) inscrire(p, b);
        this.attaquants = a;
        this.defenseurs = b;
    }

    /**
     * Partie de recherche et destruction d'un autre mode (ClashWar) : equipes deja construites (noms et couleurs du
     * mode), memes classes imposees a tous ({@code classes}, affichees « Classes de {@code nomClasses} »), joueurs
     * inscrits ensuite par {@link #inscrire}. Jamais d'entrainement ni de cible. Les points de reglage du mode sont les
     * methodes protegees ({@link #manchesGagnantes}, {@link #changerDeCote}, {@link #titreHud}, {@link #statistiques},
     * {@link #renvoyer}, {@link #detacherParti}, {@link #apresRendu}, {@link #observateurs}).
     */
    protected DuelPartie(String id, String mode, MinecraftServer serveur, String format, String carteId, List<ClassePerso> classes,
                         String nomClasses, Equipe a, Equipe b) {
        super(id, mode, serveur);
        this.rd = true;
        this.modeId = Vues.MODE_RD;
        this.format = format;
        this.carteId = carteId;
        this.entrainement = false;
        this.hote = null;
        this.nomHote = nomClasses;
        this.classesHote = List.copyOf(classes);
        this.a = a;
        this.b = b;
        ajouterEquipe(a);
        ajouterEquipe(b);
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

    protected void inscrire(ServerPlayer p, Equipe e) {
        e.ajouter(p.getUUID());
        Fiche f = new Fiche(p.getUUID(), p.getGameProfile().getName(), e, false);
        // classes de l'hote : tout le monde part de sa classe 1 (le dernier choix memorise ne vaut que pour ses propres
        // classes, en entrainement)
        f.classe = classesHote == null ? DuelsStore.derniere(p.getUUID()) : 0;
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
        if (rd) return manchesGagnantes();
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
        // killcam coupee (Matchs 0.11.0) : aucun enregistrement des pistes, rien a garder ni a envoyer
        enregistreur = DuelsConfig.KILLCAM ? KillcamLogique.enregistreur(this) : null;
        lireSites();
        if (entrainement) poserCible();
        if (rd) {
            lirePosesBombes();
            manche = 0;
            nouvelleManche();
        } else {
            nettoyerArene();
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
        MatchsNet.versJoueur(p, new PaquetsDuels.ChoixS2C(DuelsConfig.CHOIX_TICKS / 20, classesProposees(p.getUUID(), cat), f.classe, contexte,
                f.equipe.couleur(), phase() == Phase.COMBAT, classesHote == null ? "" : nomHote));
    }

    /** Les 5 classes proposees a ce joueur : celles de l'hote (assainies contre le catalogue courant), ou les siennes en entrainement. */
    private List<ClassePerso> classesProposees(UUID joueur, Catalogue cat) {
        if (classesHote == null) return DuelsStore.classes(joueur, cat);
        List<ClassePerso> l = new ArrayList<>(classesHote.size());
        for (ClassePerso c : classesHote) l.add(c.assainir(cat));
        return l;
    }

    /** Classe n (0..4) proposee a ce joueur, voir {@link #classesProposees}. */
    private ClassePerso classeProposee(UUID joueur, int n, Catalogue cat) {
        if (classesHote == null) return DuelsStore.classe(joueur, n, cat);
        int k = Math.max(0, Math.min(classesHote.size() - 1, n));
        return classesHote.get(k).assainir(cat);
    }

    /** Choix d'une classe (paquet du client, revalide). */
    public void choisirClasse(ServerPlayer p, int n) {
        Fiche f = fiches.get(p.getUUID());
        if (f == null || f.parti || n < 0 || n >= ClassePerso.EMPLACEMENTS) return;
        if (phase() == Phase.FIN || phase() == Phase.TERMINEE) return;
        f.classe = n;
        // dernier choix memorise : seulement pour ses propres classes (entrainement), jamais pour celles de l'hote
        if (classesHote == null) DuelsStore.derniere(p.getUUID(), n);
        // match a mort : changement juste apres la reapparition = re-equipement immediat, fin de la protection
        if (!rd && phase() == Phase.COMBAT && f.equipe.estVivant(f.id) && f.reapparition < 0 && ticks() - f.apparition <= DuelsConfig.GRACE_CLASSE_TICKS) {
            equiper(p, f);
            f.protegeJusque = 0;
        }
    }

    /** Donne la classe choisie (equipement prete du match, sans aucun skin). */
    private void equiper(ServerPlayer p, Fiche f) {
        Catalogue cat = Armurerie.catalogue();
        ClassePerso c = classeProposee(p.getUUID(), f.classe, cat);
        Kit kit = c.versKit(classesHote == null || hote == null ? p.getUUID() : hote, f.classe, cat);
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
        // joueur qui amorce ou pirate : immobilise, sans tir (ReglesBombe)
        majAction();
        if (phase() != Phase.TERMINEE && ticks() % 20 == 0) {
            envoyerHud(false);
            DuelsStore.sauverSiBesoin(false);
            // bombes et ordinateur : jamais sauvegardes avec le monde, reposes s'ils ont disparu (troncon decharge)
            if (rd && (phase() == Phase.CHOIX || phase() == Phase.COMBAT || phase() == Phase.PREPARATION)) {
                entretenirBombes();
                majOrdinateur();
            }
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
            // l'equipement vide l'inventaire : l'ordinateur du porteur est donne apres
            if (rd && f.id.equals(porteur)) donnerObjetCharge(p);
            f.apparition = ticks();
            MatchsNet.versJoueur(p, new PaquetsDuels.ChoixS2C(0, List.of(), f.classe, Component.empty(), 0, true, ""));
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
            apresRendu();
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
            // tue en plein piratage : l'ordinateur retombe a ses pieds, toujours relie, le piratage repart de zero
            if (etatCharge == Vues.CHARGE_POSEE && f.id.equals(acteur)) lacherOrdinateur(victime.getX(), victime.getY(), victime.getZ(), victime.getYRot());
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
        if (changerDeCote(manche)) {
            Equipe t = attaquants;
            attaquants = defenseurs;
            defenseurs = t;
            Annonces.grande(enLigneCopie(), new Annonces.Annonce(Component.translatable("duel.annonce.changement"),
                    Component.translatable("duel.annonce.changement_sous"), Ui.OR, Registres.SON_MANCHE, 60, Annonces.Style.NORMALE));
        }
        // la killcam de fin de manche ne montre qu'une elimination de cette manche
        if (enregistreur != null) enregistreur.oublierDerniere();
        progression = 0;
        acteur = null;
        siteCharge = "";
        minuterie = 0;
        majAction();
        // arene propre : rien de la manche precedente (grenades, C4, fumee, feu, objets au sol)
        nettoyerArene();
        placerTous(attaquants, "spawn_a");
        placerTous(defenseurs, "spawn_b");
        // l'ordinateur va a un attaquant connecte au hasard (dans son inventaire au debut du combat), sinon il attend
        // ferme au point « charge »
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
            if (c != null) poserAuSol(c.x(), c.y(), c.z(), c.yaw());
            else etatCharge = Vues.CHARGE_AUCUNE;
        }
        // bombes des sites remises en veille (posees au premier passage), ordinateur de la manche precedente retire
        entretenirBombes();
        majOrdinateur();
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

    /**
     * Le porteur s'accroupit (au sol) a moins de {@link DuelsConfig#RD_AMORCAGE_PORTEE} blocs d'une bombe : il ouvre
     * l'ordinateur au sol devant lui et tape les 3 codes ({@link DuelsConfig#RD_POSE_TICKS}). Touche lachee, trop loin de
     * l'ordinateur ouvert ou d'une autre bombe : l'ordinateur est range, la saisie repart de zero.
     */
    private void tickPorteur() {
        ServerPlayer p = porteur == null ? null : joueur(porteur);
        Fiche f = porteur == null ? null : fiches.get(porteur);
        if (p == null || f == null || f.parti || !f.equipe.estVivant(f.id)) {
            if (p != null) lacherChargeSiPorteur(p.getUUID(), p.getX(), p.getY(), p.getZ());
            else {
                porteur = null;
                progression = 0;
                acteur = null;
                siteCharge = "";
                Pose c = instance() == null ? null : instance().point("charge");
                if (c != null) poserAuSol(c.x(), c.y(), c.z(), c.yaw());
            }
            return;
        }
        if (ticks() % 20 == 0) donnerObjetCharge(p);
        String site = bombeProche(p);
        boolean amorce = !site.isEmpty() && p.isShiftKeyDown() && p.onGround() && !p.isSpectator();
        if (amorce && progression > 0) {
            // meme bombe, et l'attaquant reste devant l'ordinateur qu'il a ouvert
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            amorce = site.equals(siteCharge) && dx * dx + dz * dz <= DuelsConfig.RD_AMORCAGE_ECART * DuelsConfig.RD_AMORCAGE_ECART;
        }
        if (amorce) {
            if (progression == 0) {
                acteur = p.getUUID();
                siteCharge = site;
                debutAction = tempsMonde();
                placerOrdinateurAmorcage(p, site);
            }
            progression++;
            if (progression == 1) {
                majOrdinateur();
                synchroniserRd(true);
            }
            if (progression >= DuelsConfig.RD_POSE_TICKS) poser(p, site);
        } else if (progression > 0) {
            progression = 0;
            acteur = null;
            siteCharge = "";
            majOrdinateur();
            synchroniserRd(true);
        }
    }

    /** Site dont la bombe est a portee d'amorcage ("" si aucune) ; la plus proche si les deux le sont. */
    private String bombeProche(Entity e) {
        String site = "";
        double mieux = DuelsConfig.RD_AMORCAGE_PORTEE * DuelsConfig.RD_AMORCAGE_PORTEE;
        for (int i = 0; i < 2; i++) {
            Pose b = i == 0 ? poseBombeA : poseBombeB;
            if (b == null || Math.abs(e.getY() - b.y()) > DuelsConfig.RD_HAUTEUR) continue;
            double dx = e.getX() - b.x(), dz = e.getZ() - b.z(), d = dx * dx + dz * dz;
            if (d <= mieux) {
                mieux = d;
                site = i == 0 ? "A" : "B";
            }
        }
        return site;
    }

    /** Codes saisis : la bombe est amorcee, l'ordinateur reste pose, allume, relie a la bombe (40 s). */
    private void poser(ServerPlayer p, String site) {
        etatCharge = Vues.CHARGE_POSEE;
        siteCharge = site;
        porteur = null;
        progression = 0;
        acteur = null;
        minuterie = DuelsConfig.RD_MINUTERIE_TICKS;
        finMinuterie = tempsMonde() + minuterie;
        retirerObjetCharge(p);
        majBombes();
        majOrdinateur();
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
                majOrdinateur();
                donnerObjetCharge(p);
                son(p, Registres.RAMASSE.get(), 0.8f);
                synchroniserRd(true);
                return;
            }
        }
    }

    /**
     * Bombe amorcee : minuterie, et piratage. Un defenseur vivant, accroupi au sol a moins de
     * {@link DuelsConfig#RD_PIRATAGE_PORTEE} blocs de l'ordinateur le ramasse et le pirate en restant accroupi
     * ({@link DuelsConfig#RD_DESAMORCAGE_TICKS}) ; un seul a la fois. Touche lachee, tue, ou plus loin que le cable de la
     * bombe : l'ordinateur retombe a ses pieds, toujours relie, le piratage repart de zero.
     */
    private void tickPosee() {
        minuterie--;
        if (minuterie <= 0) {
            exploser();
            return;
        }
        ServerPlayer d = acteur == null ? null : joueur(acteur);
        if (acteur != null && (d == null || !peutPirater(d, false))) {
            if (d != null) lacherOrdinateur(d.getX(), d.getY(), d.getZ(), d.getYRot());
            else lacherOrdinateur(cx, cy, cz, lacet + 180f);
            d = null;
        }
        if (d == null) {
            for (UUID u : defenseurs.vivants()) {
                ServerPlayer p = joueur(u);
                if (p != null && peutPirater(p, true)) {
                    d = p;
                    break;
                }
            }
            if (d == null) return;
            acteur = d.getUUID();
            progression = 0;
            debutAction = tempsMonde();
            majOrdinateur();
            synchroniserRd(true);
        }
        // l'ordinateur suit le pirate (dessine dans ses mains chez les joueurs)
        if (ordinateur != null && !ordinateur.isRemoved()) ordinateur.setPos(d.getX(), d.getY(), d.getZ());
        progression++;
        if (progression >= DuelsConfig.RD_DESAMORCAGE_TICKS) {
            etatCharge = Vues.CHARGE_DESAMORCEE;
            progression = 0;
            acteur = null;
            poserA(d.getX(), d.getY(), d.getZ(), d.getYRot() + 180f);
            majBombes();
            majOrdinateur();
            MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.DESAMORCEE, cx, cy, cz));
            finManche(defenseurs, "duel.manche.desamorcee");
        }
    }

    /**
     * Vrai si ce joueur peut pirater : defenseur vivant, accroupi ; pour commencer, au sol a portee de l'ordinateur pose ;
     * en cours, pas plus loin de la bombe que le cable.
     */
    private boolean peutPirater(ServerPlayer p, boolean commencer) {
        Fiche f = fiches.get(p.getUUID());
        if (f == null || f.parti || f.equipe != defenseurs || !f.equipe.estVivant(f.id) || p.isSpectator() || !p.isShiftKeyDown()) return false;
        if (commencer) {
            if (!p.onGround() || Math.abs(p.getY() - cy) > DuelsConfig.RD_HAUTEUR) return false;
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            return dx * dx + dz * dz <= DuelsConfig.RD_PIRATAGE_PORTEE * DuelsConfig.RD_PIRATAGE_PORTEE;
        }
        Pose b = poseBombe(siteCharge);
        if (b == null) return true;
        double dx = p.getX() - b.x(), dz = p.getZ() - b.z();
        return dx * dx + dz * dz <= DuelsConfig.RD_CABLE * DuelsConfig.RD_CABLE;
    }

    /** Le pirate lache l'ordinateur (touche lachee, tue, trop loin, parti) : il retombe a ses pieds, toujours relie. */
    private void lacherOrdinateur(double x, double y, double z, float lacetJoueur) {
        acteur = null;
        progression = 0;
        poserA(x, y, z, lacetJoueur + 180f);
        majOrdinateur();
        synchroniserRd(true);
    }

    private void exploser() {
        etatCharge = Vues.CHARGE_EXPLOSEE;
        progression = 0;
        acteur = null;
        majBombes();
        majOrdinateur();
        Pose b = poseBombe(siteCharge);
        double x = b != null ? b.x() : cx, y = b != null ? b.y() + 0.5 : cy, z = b != null ? b.z() : cz;
        MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.EXPLOSION, x, y, z));
        finManche(attaquants, "duel.manche.explosee");
    }

    /** Le porteur meurt ou part : l'ordinateur tombe ferme a l'endroit (ou au point « charge » s'il est hors de la carte). */
    private void lacherChargeSiPorteur(UUID u, double x, double y, double z) {
        if (etatCharge != Vues.CHARGE_PORTEE || !u.equals(porteur)) return;
        Instance inst = instance();
        ServerPlayer p = joueur(u);
        if (p != null) retirerObjetCharge(p);
        float yaw = p != null ? p.getYRot() : 0f;
        porteur = null;
        progression = 0;
        acteur = null;
        siteCharge = "";
        if (inst == null || !inst.contient(x, y, z)) {
            Pose c = inst == null ? null : inst.point("charge");
            if (c != null) poserAuSol(c.x(), c.y(), c.z(), c.yaw());
            return;
        }
        poserAuSol(x, y, z, yaw);
    }

    private void poserAuSol(double x, double y, double z, float yaw) {
        etatCharge = Vues.CHARGE_AU_SOL;
        cx = x;
        cy = solSous(x, y, z);
        cz = z;
        lacet = yaw;
        majOrdinateur();
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
        // amorcage interrompu par la fin de la manche : l'ordinateur ouvert est range
        if (etatCharge == Vues.CHARGE_PORTEE) siteCharge = "";
        majBombes();
        majOrdinateur();
        for (ServerPlayer p : enLigneCopie()) {
            Fiche f = fiches.get(p.getUUID());
            boolean gagne = f != null && f.equipe == gagnante;
            Annonces.grande(p, new Annonces.Annonce(Component.translatable(gagne ? "duel.annonce.manche_gagnee" : "duel.annonce.manche_perdue"),
                    Component.translatable(raison), gagne ? gagnante.couleur() : Ui.DANGER, gagne ? Registres.SON_VICTOIRE : Registres.SON_DEFAITE, 70,
                    gagne ? Annonces.Style.VICTOIRE : Annonces.Style.DEFAITE));
        }
        finApresIntermanche = gagnante.score() >= manchesGagnantes();
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

    // ------------------------------------------------------------------ action en cours (amorcage, piratage)
    /**
     * Le joueur qui amorce (charge portee, codes en cours) ou qui pirate (charge posee, ordinateur en main) est
     * immobilise et ne peut ni tirer, ni viser, ni recharger, ni changer d'arme ({@link ReglesBombe}) ; les autres sont
     * liberes. Appele a chaque tick et a chaque changement de manche ; hors combat, personne n'est bloque.
     */
    private void majAction() {
        UUID u = phase() == Phase.COMBAT && rd && acteur != null && (etatCharge == Vues.CHARGE_POSEE || progression > 0) ? acteur : null;
        if (Objects.equals(u, bloque)) return;
        if (bloque != null) {
            ServerPlayer avant = joueur(bloque);
            if (avant != null) ReglesBombe.bloquer(avant, false);
            else ReglesBombe.oublier(bloque);
        }
        ServerPlayer p = u == null ? null : joueur(u);
        bloque = p == null ? null : u;
        if (p != null) ReglesBombe.bloquer(p, true);
    }

    // ------------------------------------------------------------------ arene propre
    /**
     * Remet l'arene de CE duel au propre (16/10, Matchs 0.11.0 : un C4 ou un fumigene jete en fin de manche restait a la
     * manche suivante) : au debut de chaque manche et a la fin du duel. Retire dans les bornes de l'instance (elargies de
     * 16 blocs) tout ce qui n'est ni un joueur, ni une entite permanente du duel (bombes, ordinateur, gere a part, cible
     * d'entrainement) : grenades, C4 colles, fumigenes, molotovs et leur feu au sol, nuages de gaz, balles et projectiles,
     * objets au sol ({@link ArenesService#nettoyer(Instance, java.util.function.Predicate)}) ; eteint les feux poses ; les
     * joueurs effacent les particules (nuages de fumee deja dessines, paquet NETTOYAGE). Les effets des joueurs
     * (aveuglement d'une flash, surdite...) sont retires a leur placement ({@link #preparerCorps}).
     */
    private void nettoyerArene() {
        Instance inst = instance();
        if (inst == null) return;
        ArenesService.nettoyer(inst, e -> e instanceof CibleEntrainement || e instanceof BombeDuel || e instanceof OrdinateurDuel);
        ArenesService.eteindreFeux(inst);
        MatchsNet.versJoueurs(enLigneCopie(), new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.NETTOYAGE, 0, 0, 0));
    }

    // ------------------------------------------------------------------ bombes et ordinateur (entites d'affichage)
    /** Poses des bombes : points bombe_a / bombe_b de la carte (a defaut site_a / site_b). */
    private void lirePosesBombes() {
        Instance inst = instance();
        if (inst == null) return;
        poseBombeA = inst.point("bombe_a");
        if (poseBombeA == null) poseBombeA = inst.point("site_a");
        poseBombeB = inst.point("bombe_b");
        if (poseBombeB == null) poseBombeB = inst.point("site_b");
    }

    @Nullable
    private Pose poseBombe(String site) {
        return "A".equals(site) ? poseBombeA : "B".equals(site) ? poseBombeB : null;
    }

    @Nullable
    private BombeDuel bombe(String site) {
        BombeDuel b = "A".equals(site) ? bombeA : "B".equals(site) ? bombeB : null;
        return b == null || b.isRemoved() ? null : b;
    }

    /**
     * Pose les bombes manquantes (premier passage, ou troncon decharge : jamais sauvegardees avec le monde) et leur donne
     * l'etat de la manche. Appele a chaque manche et toutes les secondes.
     */
    private void entretenirBombes() {
        if (!rd) return;
        Instance inst = instance();
        if (inst == null) return;
        if (bombeA == null || bombeA.isRemoved()) bombeA = creerBombe("A", poseBombeA, inst);
        if (bombeB == null || bombeB.isRemoved()) bombeB = creerBombe("B", poseBombeB, inst);
        majBombes();
    }

    @Nullable
    private BombeDuel creerBombe(String site, @Nullable Pose p, Instance inst) {
        if (p == null) return null;
        BombeDuel b = Registres.BOMBE.get().create(inst.niveau());
        if (b == null) return null;
        b.partie(id());
        b.site(site);
        b.moveTo(p.x(), p.y(), p.z(), p.yaw(), 0f);
        // troncon charge avant l'ajout (comme la cible d'entrainement)
        inst.niveau().getChunk(p.bloc());
        return inst.niveau().addFreshEntity(b) ? b : null;
    }

    private void majBombes() {
        majBombe(bombeA);
        majBombe(bombeB);
    }

    /** Seule la bombe du site amorce change d'etat (amorcee, desamorcee, explosee) ; l'autre reste en veille. */
    private void majBombe(@Nullable BombeDuel b) {
        if (b == null || b.isRemoved()) return;
        int e = BombeDuel.VEILLE;
        if (b.site().equals(siteCharge)) {
            if (etatCharge == Vues.CHARGE_POSEE) e = BombeDuel.ARMEE;
            else if (etatCharge == Vues.CHARGE_DESAMORCEE) e = BombeDuel.DESAMORCEE;
            else if (etatCharge == Vues.CHARGE_EXPLOSEE) e = BombeDuel.EXPLOSEE;
        }
        b.etat(e);
    }

    private void retirerBombes() {
        if (bombeA != null) bombeA.discard();
        if (bombeB != null) bombeB.discard();
        bombeA = null;
        bombeB = null;
    }

    /** Etat de l'ordinateur pose d'apres la manche, ou -1 s'il n'est pas dans le monde (porte, explose, aucun). */
    private int etatOrdinateur() {
        return switch (etatCharge) {
            case Vues.CHARGE_PORTEE -> acteur != null && progression > 0 ? OrdinateurDuel.AMORCAGE : -1;
            case Vues.CHARGE_AU_SOL -> OrdinateurDuel.FERME;
            case Vues.CHARGE_POSEE -> acteur != null ? OrdinateurDuel.PIRATAGE : OrdinateurDuel.AMORCEE;
            case Vues.CHARGE_DESAMORCEE -> OrdinateurDuel.DESAMORCEE;
            default -> -1;
        };
    }

    /** Met l'ordinateur du monde dans l'etat de la manche : le cree (ou le recree), le deplace, ou le retire. */
    private void majOrdinateur() {
        int e = etatOrdinateur();
        if (e < 0) {
            retirerOrdinateur();
            return;
        }
        Instance inst = instance();
        if (inst == null) return;
        OrdinateurDuel o = ordinateur;
        boolean nouveau = o == null || o.isRemoved();
        if (nouveau) {
            o = Registres.ORDINATEUR.get().create(inst.niveau());
            if (o == null) return;
            o.partie(id());
            o.moveTo(cx, cy, cz, lacet, 0f);
        }
        o.etat(e);
        o.site(siteCharge.isEmpty() ? "A" : siteCharge);
        o.bombe(e == OrdinateurDuel.FERME ? null : bombe(siteCharge));
        ServerPlayer u = acteur == null ? null : joueur(acteur);
        o.utilisateur(e == OrdinateurDuel.AMORCAGE || e == OrdinateurDuel.PIRATAGE ? u : null);
        o.debut(debutAction);
        o.fin(finMinuterie);
        if (e == OrdinateurDuel.PIRATAGE && u != null) {
            o.setPos(u.getX(), u.getY(), u.getZ());
        } else if (!nouveau) {
            o.setPos(cx, cy, cz);
            o.setYRot(lacet);
        }
        if (nouveau) {
            inst.niveau().getChunk(BlockPos.containing(o.getX(), o.getY(), o.getZ()));
            if (inst.niveau().addFreshEntity(o)) ordinateur = o;
        }
    }

    private void retirerOrdinateur() {
        if (ordinateur != null) ordinateur.discard();
        ordinateur = null;
    }

    /**
     * L'attaquant ouvre l'ordinateur au sol : un demi-bloc devant lui, vers la bombe, ecran tourne vers lui, jamais dans
     * l'emprise de la bombe.
     */
    private void placerOrdinateurAmorcage(ServerPlayer p, String site) {
        Pose b = poseBombe(site);
        double px = p.getX(), pz = p.getZ();
        double ux, uz;
        double dx = b == null ? 0 : b.x() - px, dz = b == null ? 0 : b.z() - pz, d = Math.sqrt(dx * dx + dz * dz);
        if (d > 1e-3) {
            ux = dx / d;
            uz = dz / d;
        } else {
            double r = Math.toRadians(p.getYRot());
            ux = -Math.sin(r);
            uz = Math.cos(r);
        }
        double[] q = horsBombe(site, px + ux * 0.55, pz + uz * 0.55);
        cx = q[0];
        cz = q[1];
        cy = solSous(cx, p.getY() + 0.5, cz);
        lacet = (float) Math.toDegrees(Math.atan2(-(px - cx), pz - cz));
    }

    /** Ordinateur pose a cet endroit (au sol, hors de l'emprise de la bombe) ; hors de la carte : il ne bouge pas. */
    private void poserA(double x, double y, double z, float yaw) {
        Instance inst = instance();
        if (inst != null && !inst.contient(x, y, z)) return;
        double[] q = horsBombe(siteCharge, x, z);
        cx = q[0];
        cz = q[1];
        cy = solSous(cx, y + 0.1, cz);
        lacet = yaw;
    }

    /**
     * Repousse un point (x, z) hors de l'emprise de la bombe du site : rectangle tourne avec elle (berceau et module, voir
     * outils/bombe/modeles.py), elargi de la taille de l'ordinateur.
     */
    private double[] horsBombe(String site, double x, double z) {
        Pose b = poseBombe(site);
        if (b == null) return new double[]{x, z};
        double psi = Math.toRadians(b.yaw());
        // axes du modele dans le monde (rendu : rotation de 180 - lacet autour de Y) : X = longueur (nez), Z = arriere
        double axX = -Math.cos(psi), axZ = -Math.sin(psi), azX = Math.sin(psi), azZ = -Math.cos(psi);
        double rx = x - b.x(), rz = z - b.z();
        double lx = rx * axX + rz * axZ, lz = rx * azX + rz * azZ;
        // emprise du modele d'origine (pixels / 16, elargie de l'ordinateur), a l'echelle du rendu
        double k = BombeDuel.ECHELLE, marge = 0.35;
        double x0 = (-1.12 + marge) * k - marge, x1 = (1.18 - marge) * k + marge, z0 = (-0.76 + marge) * k - marge, z1 = (0.64 - marge) * k + marge;
        if (lx > x0 && lx < x1 && lz > z0 && lz < z1) {
            double dx0 = lx - x0, dx1 = x1 - lx, dz0 = lz - z0, dz1 = z1 - lz;
            double m = Math.min(Math.min(dx0, dx1), Math.min(dz0, dz1));
            if (m == dx0) lx = x0;
            else if (m == dx1) lx = x1;
            else if (m == dz0) lz = z0;
            else lz = z1;
        }
        return new double[]{b.x() + lx * axX + lz * azX, b.z() + lx * axZ + lz * azZ};
    }

    /** Hauteur du sol sous un point (jusqu'a 4 blocs plus bas), pour qu'un ordinateur lache en l'air retombe au sol. */
    private double solSous(double x, double y, double z) {
        Instance inst = instance();
        if (inst == null) return y;
        for (int i = 0; i < 4; i++) {
            BlockPos pos = BlockPos.containing(x, y - 0.01 - i, z);
            VoxelShape forme = inst.niveau().getBlockState(pos).getCollisionShape(inst.niveau(), pos);
            if (!forme.isEmpty()) return Math.min(y, pos.getY() + forme.max(Direction.Axis.Y));
        }
        return y;
    }

    private int tempsMonde() {
        Instance inst = instance();
        return inst == null ? 0 : (int) inst.niveau().getGameTime();
    }

    // ------------------------------------------------------------------ objet porte (ordinateur ferme)
    /**
     * Donne l'ordinateur ferme au porteur (objet prete du match : jamais lache, retire a la fin) dans la derniere case de
     * la barre si elle est libre, sinon la premiere libre. Rien s'il l'a deja.
     */
    private void donnerObjetCharge(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (estCharge(inv.getItem(i))) return;
        if (estCharge(p.containerMenu.getCarried())) return;
        ItemStack s = Equipement.marquer(new ItemStack(Registres.CHARGE.get()), id());
        int libre = inv.getItem(8).isEmpty() ? 8 : -1;
        for (int i = 0; i < 9 && libre < 0; i++) if (inv.getItem(i).isEmpty()) libre = i;
        if (libre >= 0) inv.setItem(libre, s);
        else if (!inv.add(s)) HeraliszMatchs.LOG.warn("[duels] {} : inventaire plein, ordinateur non donne a {}", id(), p.getGameProfile().getName());
        p.inventoryMenu.broadcastChanges();
    }

    private static boolean estCharge(ItemStack s) {
        return !s.isEmpty() && s.is(Registres.CHARGE.get());
    }

    private static void retirerObjetCharge(ServerPlayer p) {
        Inventory inv = p.getInventory();
        boolean change = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (estCharge(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                change = true;
            }
        }
        if (estCharge(p.containerMenu.getCarried())) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
            change = true;
        }
        if (change) {
            p.inventoryMenu.broadcastChanges();
            p.containerMenu.broadcastChanges();
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
                if (moi && !bombeProche(p).isEmpty()) invite = "poser";
                else if (!att && etatCharge == Vues.CHARGE_POSEE && acteur == null) {
                    double dx = p.getX() - cx, dz = p.getZ() - cz, r = DuelsConfig.RD_PIRATAGE_PORTEE + 0.6;
                    if (dx * dx + dz * dz <= r * r && Math.abs(p.getY() - cy) <= DuelsConfig.RD_HAUTEUR) invite = "desamorcer";
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
            // reperes A / B : sur les bombes (a defaut, centre des zones des sites)
            double[] sites = new double[6];
            if (poseBombeA != null) bombeRepere(poseBombeA, sites, 0);
            else if (siteA != null) centre(siteA, sites, 0);
            if (poseBombeB != null) bombeRepere(poseBombeB, sites, 3);
            else if (siteB != null) centre(siteB, sites, 3);
            Vues.RdVue v = new Vues.RdVue(att, phase() == Phase.COMBAT || phase() == Phase.CHOIX ? etatCharge : Vues.CHARGE_AUCUNE,
                    att && fp != null ? fp.nom : "", moi, siteCharge, voitCharge ? cx : 0, voitCharge ? cy : 0, voitCharge ? cz : 0,
                    etatCharge == Vues.CHARGE_POSEE ? minuterie : 0, action, progression, total, invite, sites);
            MatchsNet.versJoueur(p, new PaquetsDuels.RdS2C(v));
        }
    }

    private static void bombeRepere(Pose p, double[] d, int i) {
        d[i] = p.x();
        d[i + 1] = p.y() + 0.5;
        d[i + 2] = p.z();
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
        Component titre = titreHud();
        Component score = Component.literal(a.score() + "  —  " + b.score());
        List<ServerPlayer> l = enLigneCopie();
        observateurs(l);
        Hud.envoyer(l, Hud.bandeau(this, titre, secondes, score));
    }

    // ------------------------------------------------------------------ fin
    @Override
    protected void surFin(@Nullable Equipe gagnante) {
        etatCharge = Vues.CHARGE_AUCUNE;
        acteur = null;
        progression = 0;
        majAction();
        retirerOrdinateur();
        retirerBombes();
        nettoyerArene();
        if (annulee) return;
        // statistiques globales (jamais les stats de clan)
        if (statistiques()) {
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
        retirerOrdinateur();
        retirerBombes();
    }

    private void rendre(ServerPlayer p) {
        ReglesBombe.bloquer(p, false);
        if (p.getUUID().equals(bloque)) bloque = null;
        Spectateurs.arreter(p, false);
        KillcamLogique.arreter(p);
        Equipement.retirer(p);
        Hud.effacer(p);
        MatchsNet.versJoueur(p, new PaquetsDuels.RdS2C(null));
        MatchsNet.versJoueur(p, new PaquetsDuels.EffetS2C(PaquetsDuels.EffetS2C.FIN, 0, 0, 0));
        renvoyer(p);
    }

    /** Fin du duel pour ce joueur (deja nettoye) : Coffre rendu et detache du moteur. ClashWar : retour en salle. */
    protected void renvoyer(ServerPlayer p) {
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
            // le pirate part : l'ordinateur retombe a ses pieds ; l'amorceur (porteur) l'a deja lache ferme
            if (etatCharge == Vues.CHARGE_POSEE) lacherOrdinateur(joueur.getX(), joueur.getY(), joueur.getZ(), joueur.getYRot());
            else {
                acteur = null;
                progression = 0;
                majOrdinateur();
            }
        }
        if (rendreMaintenant) rendre(joueur);
        else {
            Spectateurs.arreter(joueur, false);
            detacherParti(joueur);
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

    // ------------------------------------------------------------------ points de reglage (ClashWar)
    /** Manches gagnantes en recherche et destruction. */
    protected int manchesGagnantes() {
        return DuelsConfig.RD_MANCHES_GAGNANTES;
    }

    /** Vrai si les cotes s'echangent au debut de cette manche (1 = premiere). */
    protected boolean changerDeCote(int manche) {
        return manche == DuelsConfig.RD_CHANGEMENT_COTE + 1;
    }

    /** Titre du bandeau du moteur. */
    protected Component titreHud() {
        return rd ? Component.translatable("duel.hud.rd", manche) : Component.translatable("duel.hud.mam", objectif());
    }

    /** Numero de la manche en cours (1 = premiere, 0 avant). */
    protected int manche() {
        return manche;
    }

    /** Statistiques de duel comptees a la fin. */
    protected boolean statistiques() {
        return !entrainement;
    }

    /** Joueur deconnecte pendant la partie (deja elimine et sorti du mode spectateur) : detache du moteur. */
    protected void detacherParti(ServerPlayer joueur) {
        MatchApi.quitter(joueur.getUUID());
    }

    /** Apres le retour de tout le monde, juste avant {@link #fermer()} (fin normale seulement). */
    protected void apresRendu() {
    }

    /** Ajoute a la liste les joueurs qui regardent sans jouer (ils recoivent aussi le bandeau). Defaut : aucun. */
    protected void observateurs(List<ServerPlayer> sortie) {
    }

    /** Vrai si la partie a ete annulee (personne ne gagne). */
    protected boolean annulee() {
        return annulee;
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
