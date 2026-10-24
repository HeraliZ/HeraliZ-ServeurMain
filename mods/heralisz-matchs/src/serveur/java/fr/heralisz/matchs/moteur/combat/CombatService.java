package fr.heralisz.matchs.moteur.combat;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.MoteurConfig;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.compat.Revive;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Systeme de combat du serveur HeraliZ (RETOURS du 01/10 §3), global a tout le serveur ; il remplace le tag d'ArkaniaZ
 * cache dans geckolib222 (neutralise par {@code tools/strip_geckolib_combatlog.py}) :
 * <ul>
 *     <li>tag seulement en PvP ({@link CombatEvents} : degats reellement appliques entre deux joueurs hors match, auteur
 *     reel de la source), 15 s (config), jamais par un mob, une chute, etc. ; jamais pendant un match ;</li>
 *     <li>indicateur HUD discret « EN COMBAT 12 s » avec une jauge fine (paquet {@code CombatS2C}, au plus un envoi par
 *     seconde et par joueur, renvoi differe sinon ; le client decompte seul) ; <b>aucun message dans le chat</b> ;</li>
 *     <li>cle {@link CombatTag#CLE_JUSQUA} du {@code persistentData} tenue a jour (lue par les autres mods) ;</li>
 *     <li>deconnexion pendant le tag = mort (degats {@code heralisz_matchs:deconnexion_combat}) : le corps du mod corpse
 *     reste sur place ; ligne dans le journal du serveur ; jamais a l'arret du serveur ni pour un joueur en
 *     creatif/spectateur ; message unique a la reconnexion. Joueur a terre (Reanimation) : c'est Reanimation qui l'acheve,
 *     avec notre type de degats (kill credite a celui qui l'a mis a terre). Notre ecouteur (HIGHEST) passe avant celui de
 *     Reanimation (« deconnexion a terre = mort ») : une seule mort, un seul corps ;</li>
 *     <li>deconnexion a terre SANS tag : Reanimation tue seul ({@code reanimation:disconnect}) ; message unique a la
 *     reconnexion ({@link #CLE_PUNI_A_TERRE}) ;</li>
 *     <li>toute commande hors de la liste permise (config : login, msg, quetes...) refusee au joueur tague ET au joueur
 *     a terre (Reanimation : pas de fuite ni de rangement pendant qu'on peut l'achever ou le fouiller), avec une ligne
 *     discrete au-dessus de la barre d'objets ; sur la carte (overworld), hors staff, seules les commandes de la liste
 *     {@code [carte]} passent (/tpa...) ;</li>
 *     <li>execution (Reanimation, {@code reanimation:execution}) : le bourreau est tague, meme si les degats ont ete
 *     court-circuites par une protection (Reanimation force alors la mort) ;</li>
 *     <li>mort reelle = tag efface ; a la connexion : cle perimee retiree, ancien effet {@code geckolib:combat_log}
 *     retire.</li>
 * </ul>
 * Fil serveur.
 */
public final class CombatService {
    private CombatService() {
    }

    /** Type de degats de la mort pour deconnexion en combat ({@code data/heralisz_matchs/damage_type}). */
    public static final ResourceKey<DamageType> DECONNEXION = ResourceKey.create(Registries.DAMAGE_TYPE, HeraliszMatchs.id("deconnexion_combat"));
    /** Booleen du {@code persistentData} : tue pour deconnexion en combat, message a la reconnexion. */
    public static final String CLE_PUNI = "heralisz:combat_puni";
    /** Booleen du {@code persistentData} : tue par Reanimation pour deconnexion a terre (sans tag), message a la reconnexion. */
    public static final String CLE_PUNI_A_TERRE = "heralisz:a_terre_puni";
    /** Type de degats de Reanimation : execution d'un joueur a terre (auteur = le bourreau). */
    public static final ResourceLocation REANIMATION_EXECUTION = new ResourceLocation("reanimation", "execution");
    /** Type de degats de Reanimation : deconnexion a terre. */
    public static final ResourceLocation REANIMATION_DECONNEXION = new ResourceLocation("reanimation", "disconnect");
    static final ResourceLocation COMBAT_LOG = new ResourceLocation("geckolib", "combat_log");

    /** Dernier envoi de l'indicateur (ms) par joueur. */
    private static final Map<UUID, Long> ENVOI = new HashMap<>();
    /** Joueurs dont l'indicateur doit etre renvoye (envoi limite par la cadence). */
    private static final Set<UUID> A_RENVOYER = new HashSet<>();
    /** Seules racines permises au joueur tague ou a terre. */
    private static volatile Set<String> commandes = Set.copyOf(MoteurConfig.COMMANDES_COMBAT_DEFAUT);
    /** Seules racines permises sur la carte (overworld), hors staff. */
    private static volatile Set<String> commandesCarte = Set.copyOf(MoteurConfig.COMMANDES_CARTE_DEFAUT);
    private static boolean arret;

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CombatService::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(CombatService::connexion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatService::mort);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CombatService::commande);
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> arret = false);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> arret = true);
    }

    /** Reserve a la config : racines des seules commandes permises en combat et sur la carte. */
    public static void commandesPermises(List<? extends String> combat, List<? extends String> carte) {
        commandes = racines(combat);
        commandesCarte = racines(carte);
    }

    private static Set<String> racines(List<? extends String> l) {
        Set<String> s = new HashSet<>();
        for (String c : l) {
            if (c == null) continue;
            String r = c.trim().toLowerCase(Locale.ROOT);
            if (r.startsWith("/")) r = r.substring(1);
            if (!r.isEmpty()) s.add(r);
        }
        return Set.copyOf(s);
    }

    // ------------------------------------------------------------------ indicateur HUD
    /** Appele par {@link CombatTag#marquer} : indicateur « EN COMBAT » (cadence limitee, renvoi differe). */
    public static void apresMarquage(ServerPlayer p) {
        indicateur(p);
    }

    /** Appele par {@link CombatTag#effacer} : indicateur masque tout de suite. */
    public static void apresEffacement(ServerPlayer p) {
        A_RENVOYER.remove(p.getUUID());
        ENVOI.remove(p.getUUID());
        Hud.combat(p, 0);
    }

    /** Envoie l'indicateur (secondes restantes) au plus une fois par seconde ; sinon renvoi au prochain passage. */
    public static void indicateur(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long dernier = ENVOI.get(p.getUUID());
        if (dernier != null && now - dernier < 1000) {
            A_RENVOYER.add(p.getUUID());
            return;
        }
        A_RENVOYER.remove(p.getUUID());
        ENVOI.put(p.getUUID(), now);
        Hud.combat(p, (int) Math.ceil(CombatTag.restantMs(p) / 1000.0));
    }

    /** Tick du moteur : renvois differes (toutes les 5 ticks), fin des tags et cle retiree (toutes les 20 ticks). */
    public static void tick(MinecraftServer s) {
        int t = s.getTickCount();
        if (!A_RENVOYER.isEmpty() && t % 5 == 0) {
            long now = System.currentTimeMillis();
            for (UUID u : A_RENVOYER.toArray(new UUID[0])) {
                Long dernier = ENVOI.get(u);
                if (dernier != null && now - dernier < 1000) continue;
                A_RENVOYER.remove(u);
                ServerPlayer p = s.getPlayerList().getPlayer(u);
                if (p != null && CombatTag.estTague(u)) indicateur(p);
            }
        }
        if (t % 20 == 0 && !CombatTag.aucun()) CombatTag.purger();
    }

    // ------------------------------------------------------------------ deconnexion = mort
    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) surDeconnexion(p);
    }

    /**
     * Traitement d'une deconnexion (HIGHEST, avant les autres mods et avant la sauvegarde du joueur faite par
     * {@code PlayerList.remove}) : joueur tague -> tue (corps laisse) et journalise, sauf arret du serveur, creatif ou
     * spectateur, match, mort deja, ou regle desactivee. Rend vrai si le joueur a ete tue. Public pour le banc d'essai.
     */
    public static boolean surDeconnexion(ServerPlayer p) {
        UUID u = p.getUUID();
        if (p.getTags().contains("heraliz_bot")) {   // bots de charge (/bots de HeraliZ Utils) : retirés sans mourir
            CombatTag.effacer(u);
            p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
            return false;
        }
        long reste = CombatTag.restantMs(u);
        ENVOI.remove(u);
        A_RENVOYER.remove(u);
        if (reste <= 0) {
            p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
            CombatTag.effacer(u);
            return false;
        }
        MinecraftServer s = p.getServer();
        String nom = p.getGameProfile().getName();
        boolean serveurArrete = arret || s == null || !s.isRunning();
        if (serveurArrete || !MoteurConfig.vrai(MoteurConfig.MORT_DECONNEXION, true) || MatchApi.enMatch(p) || p.isCreative()
                || p.isSpectator() || p.isDeadOrDying()) {
            HeraliszMatchs.LOG.info("[matchs] combat : {} deconnecte en combat ({} s) sans sanction ({})", nom, (reste + 999) / 1000,
                    serveurArrete ? "arret du serveur" : p.isCreative() || p.isSpectator() ? "creatif/spectateur" : "regle");
            CombatTag.effacer(u);
            p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
            return false;
        }
        HeraliszMatchs.LOG.warn("[matchs] combat : {} s'est deconnecte en combat ({} s restantes{}) : tue, corps laisse en {} {} {} {}",
                nom, (reste + 999) / 1000, Revive.aTerre(p) ? ", a terre" : "", p.level().dimension().location(), (int) Math.floor(p.getX()),
                (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
        // la cle doit disparaitre AVANT la sauvegarde du joueur (faite juste apres cet evenement par PlayerList.remove)
        CombatTag.effacer(u);
        p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
        p.getPersistentData().putBoolean(CLE_PUNI, true);
        tuer(p);
        boolean mort = p.isDeadOrDying();
        if (!mort) HeraliszMatchs.LOG.error("[matchs] combat : {} toujours en vie apres la sanction (un autre mod a refuse la mort)", nom);
        return mort;
    }

    /**
     * Tue le joueur pour deconnexion en combat. Deja a terre (Reanimation) : acheve par Reanimation avec notre type de
     * degats (kill credite a celui qui l'a mis a terre, etat a terre efface : Reanimation ne le retue pas ensuite). Sinon
     * degats {@code heralisz_matchs:deconnexion_combat} (ignorent armure, effets et invulnerabilite : Reanimation ne le met
     * pas a terre) ; mis a terre quand meme (PlayerRevive) -> achevement ; degats refuses (zone protegee, autre mod) -> mort
     * directe. Le mod corpse laisse le corps et son contenu sur place (une seule mort, donc un seul corps).
     */
    public static void tuer(ServerPlayer p) {
        if (Revive.aTerre(p)) Revive.achever(p, DECONNEXION);
        if (p.isDeadOrDying()) return;
        DamageSource src = source(p);
        p.hurt(src, Float.MAX_VALUE);
        if (!p.isDeadOrDying() && Revive.aTerre(p)) Revive.achever(p, DECONNEXION);
        if (!p.isDeadOrDying()) {
            p.setHealth(0f);
            p.die(src);
            if (Revive.aTerre(p)) Revive.achever(p, DECONNEXION);
        }
    }

    /** Source des degats de la sanction (type maison, sinon « /kill » si le pack de donnees du mod est absent). */
    public static DamageSource source(ServerPlayer p) {
        Optional<Holder.Reference<DamageType>> h = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DECONNEXION);
        return h.<DamageSource>map(DamageSource::new).orElseGet(() -> p.damageSources().genericKill());
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        var d = p.getPersistentData();
        if (CombatTag.estTague(p)) {
            // tag garde (mort a la deconnexion desactivee) : cle et indicateur rafraichis
            d.putLong(CombatTag.CLE_JUSQUA, CombatTag.finMs(p.getUUID()));
            indicateur(p);
        } else if (d.contains(CombatTag.CLE_JUSQUA)) {
            d.remove(CombatTag.CLE_JUSQUA);
        }
        if (d.getBoolean(CLE_PUNI)) {
            d.remove(CLE_PUNI);
            d.remove(CLE_PUNI_A_TERRE);
            p.sendSystemMessage(Component.translatable("matchs.combat.puni").withStyle(ChatFormatting.RED));
        } else if (d.getBoolean(CLE_PUNI_A_TERRE)) {
            d.remove(CLE_PUNI_A_TERRE);
            p.sendSystemMessage(Component.translatable("matchs.combat.puni_a_terre").withStyle(ChatFormatting.RED));
        }
        // ancien tag d'ArkaniaZ (geckolib222) : effet reste sur un joueur sauvegarde avant le correctif
        MobEffect ancien = ForgeRegistries.MOB_EFFECTS.containsKey(COMBAT_LOG) ? ForgeRegistries.MOB_EFFECTS.getValue(COMBAT_LOG) : null;
        if (ancien != null && p.hasEffect(ancien)) p.removeEffect(ancien);
    }

    // ------------------------------------------------------------------ mort reelle
    /**
     * Mort reelle (non annulee : ni mise a terre Reanimation/PlayerRevive, ni match) : fin du tag. Execution Reanimation :
     * le bourreau est tague (hors match, ni creatif ni spectateur). Deconnexion a terre tuee par Reanimation : message a la
     * reconnexion (cle posee avant la sauvegarde du joueur, faite apres l'evenement de deconnexion).
     */
    private static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getEntity().level().isClientSide) return;
        CombatTag.effacer(p.getUUID());
        ResourceLocation type = e.getSource().typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
        if (REANIMATION_EXECUTION.equals(type) && CombatEvents.auteur(e.getSource()) instanceof ServerPlayer bourreau && bourreau != p
                && !MatchApi.enMatch(bourreau) && !MatchApi.enMatch(p) && !bourreau.isCreative() && !bourreau.isSpectator()) {
            CombatTag.marquer(bourreau);
        }
        if (REANIMATION_DECONNEXION.equals(type)) p.getPersistentData().putBoolean(CLE_PUNI_A_TERRE, true);
    }

    // ------------------------------------------------------------------ commandes
    private static void commande(CommandEvent e) {
        var ctx = e.getParseResults().getContext();
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return;
        String racine = racine(e.getParseResults().getReader().getString());
        if (racine.isEmpty()) return;
        if (refusCombat(p, racine, e)) return;
        refusCarte(p, racine, e);
    }

    /** Combat ou a terre : toute commande hors de la liste permise est refusee. Rend vrai si la commande est refusee. */
    private static boolean refusCombat(ServerPlayer p, String racine, CommandEvent e) {
        if (!MoteurConfig.vrai(MoteurConfig.REFUS_COMMANDES, true)) return false;
        long reste = CombatTag.restantMs(p);
        // a terre (Reanimation) : pas de fuite ni de rangement pendant qu'on peut l'achever, le fouiller ou le relever
        boolean aTerre = reste <= 0 && Revive.aTerreReanimation(p);
        if (reste <= 0 && !aTerre) return false;
        if (MoteurConfig.vrai(MoteurConfig.OPS_EXEMPTES, false) && p.hasPermissions(2)) return false;
        if (commandes.contains(racine)) return false;
        e.setCanceled(true);
        if (aTerre) {
            // Reanimation masque la barre d'action d'un joueur a terre (il ne garde que le chat et les titres)
            p.sendSystemMessage(Component.translatableWithFallback("matchs.combat.commande_refusee_a_terre",
                    "Impossible à terre.").withStyle(s -> s.withColor(0xFF8A8A)));
            return true;
        }
        p.displayClientMessage(Component.translatableWithFallback("matchs.combat.commande_refusee", "Impossible en combat · %s s",
                (reste + 999) / 1000).withStyle(s -> s.withColor(0xFF8A8A)), true);
        return true;
    }

    /**
     * Sur la carte (overworld), en survie ou aventure, hors staff (niveau 2) : toute commande hors de la liste permise est
     * refusee (choix du proprietaire : sur la carte, seul /tpa ; on en sort par l'extraction).
     */
    private static void refusCarte(ServerPlayer p, String racine, CommandEvent e) {
        if (!MoteurConfig.vrai(MoteurConfig.CARTE_ACTIF, true)) return;
        if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (p.isCreative() || p.isSpectator() || p.hasPermissions(2)) return;
        if (commandesCarte.contains(racine)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatableWithFallback("matchs.carte.commande_refusee",
                "Impossible sur la carte : seul /tpa marche ici.").withStyle(s -> s.withColor(0xFF8A8A)), true);
    }

    /** Racine d'une commande en minuscules, sans « / » ni espace de noms ({@code essentials:spawn} -> {@code spawn}). */
    public static String racine(String brut) {
        String b = brut == null ? "" : brut.trim();
        if (b.startsWith("/")) b = b.substring(1);
        int sp = b.indexOf(' ');
        String r = (sp < 0 ? b : b.substring(0, sp)).toLowerCase(Locale.ROOT);
        int deux = r.indexOf(':');
        return deux >= 0 ? r.substring(deux + 1) : r;
    }

    /** Vrai si cette racine est refusee au joueur tague (banc d'essai). */
    public static boolean commandeBloquee(String racine) {
        return !commandes.contains(racine);
    }

    /** Oubli d'un joueur (deconnexion traitee). */
    public static void oublier(UUID u) {
        ENVOI.remove(u);
        A_RENVOYER.remove(u);
    }

    /** Arret du serveur. */
    public static void vider() {
        ENVOI.clear();
        A_RENVOYER.clear();
    }
}
