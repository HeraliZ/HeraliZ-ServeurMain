package fr.heralisz.utils.combat;

import fr.heralisz.utils.HeraliszUtils;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Système de combat du serveur, global à tout le serveur (déplacé de HeraliZ Matchs le 24/10, quand les matchs ont été
 * retirés : même logique, mêmes clés, mêmes textes ; réglages dans {@code heraliz_combat-server.toml}) :
 * <ul>
 *     <li>tag seulement en PvP ({@code LivingDamageEvent} LOWEST : dégâts réellement appliqués entre deux joueurs, auteur
 *     réel de la source), 15 s (réglage), jamais par un monstre, une chute, etc. ; ni un joueur en créatif ou en
 *     spectateur ;</li>
 *     <li>indicateur discret « EN COMBAT 12 s » chez le joueur (paquet {@link CombatReseau.Indicateur}, au plus un envoi
 *     par seconde et par joueur, renvoi différé sinon ; le joueur décompte seul) ; aucun message dans le chat ;</li>
 *     <li>clé {@link CombatTag#CLE_JUSQUA} du {@code persistentData} tenue à jour (lue par les autres mods) ;</li>
 *     <li>déconnexion pendant le tag = mort (dégâts {@code heralisz_utils:deconnexion_combat}) : le corps reste sur place ;
 *     ligne dans le journal ; jamais à l'arrêt du serveur ni pour un joueur en créatif ou spectateur ; message unique à
 *     la reconnexion. Joueur à terre (Réanimation) : c'est Réanimation qui l'achève, avec notre type de dégâts (kill
 *     crédité à celui qui l'a mis à terre). Notre écouteur (HIGHEST) passe avant celui de Réanimation (« déconnexion à
 *     terre = mort ») : une seule mort, un seul corps ;</li>
 *     <li>déconnexion à terre SANS tag : Réanimation tue seul ({@code reanimation:disconnect}) ; message unique à la
 *     reconnexion ;</li>
 *     <li>toute commande hors de la liste permise refusée au joueur tagué ET au joueur à terre (ligne discrète au-dessus
 *     de la barre d'objets) ; sur la carte (overworld), hors staff, seules les commandes de la liste {@code [carte]}
 *     passent ;</li>
 *     <li>exécution (Réanimation, {@code reanimation:execution}) : le bourreau est tagué ;</li>
 *     <li>mort réelle = tag effacé ; à la connexion : clé périmée retirée, ancien effet {@code geckolib:combat_log}
 *     d'ArkaniaZ retiré (et refusé partout).</li>
 * </ul>
 * Fil serveur.
 */
public final class CombatServeur {
    private CombatServeur() {
    }

    /** Type de dégâts de la mort pour déconnexion en combat ({@code data/heralisz_utils/damage_type}). */
    public static final ResourceKey<DamageType> DECONNEXION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(HeraliszUtils.MODID, "deconnexion_combat"));
    /** Booléen du {@code persistentData} : tué pour déconnexion en combat, message à la reconnexion. */
    public static final String CLE_PUNI = "heralisz:combat_puni";
    /** Booléen du {@code persistentData} : tué par Réanimation pour déconnexion à terre (sans tag), message à la reconnexion. */
    public static final String CLE_PUNI_A_TERRE = "heralisz:a_terre_puni";
    static final ResourceLocation REANIMATION_EXECUTION = new ResourceLocation("reanimation", "execution");
    static final ResourceLocation REANIMATION_DECONNEXION = new ResourceLocation("reanimation", "disconnect");
    /** Ancien tag de combat d'ArkaniaZ (geckolib222) : refusé partout. */
    static final ResourceLocation COMBAT_LOG = new ResourceLocation("geckolib", "combat_log");

    /** Dernier envoi de l'indicateur (ms) par joueur. */
    private static final Map<UUID, Long> ENVOI = new HashMap<>();
    /** Joueurs dont l'indicateur doit être renvoyé (envoi limité par la cadence). */
    private static final Set<UUID> A_RENVOYER = new HashSet<>();
    private static volatile Set<String> commandes = racines(CombatConfig.COMMANDES_COMBAT_DEFAUT);
    private static volatile Set<String> commandesCarte = racines(CombatConfig.COMMANDES_CARTE_DEFAUT);
    private static boolean arret;

    public static void init(net.minecraftforge.eventbus.api.IEventBus modBus) {
        CombatConfig.enregistrer(modBus);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CombatServeur::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(CombatServeur::connexion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatServeur::mort);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatServeur::tag);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CombatServeur::commande);
        // LOWEST : notre refus est le dernier mot (aucun autre mod ne peut le repasser à ALLOW après nous)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatServeur::effet);
        MinecraftForge.EVENT_BUS.addListener(CombatServeur::tick);
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> arret = false);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> arret = true);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CombatTag.vider();
            ENVOI.clear();
            A_RENVOYER.clear();
        });
    }

    /** Réservé à la config : racines des seules commandes permises en combat et sur la carte. */
    static void commandesPermises(List<? extends String> combat, List<? extends String> carte) {
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

    /** Auteur réel (propriétaire du projectile ou de l'explosif) si c'est une entité vivante. */
    @Nullable
    static LivingEntity auteur(DamageSource source) {
        Entity e = source.getEntity();
        return e instanceof LivingEntity l ? l : null;
    }

    // ------------------------------------------------------------------ tag
    /**
     * Dégâts réellement appliqués (LOWEST, non annulés, > 0) entre deux joueurs distincts, auteur réel de la source
     * (tireur d'une balle TaCZ, lanceur d'une grenade ou d'un projectile, propriétaire d'une explosion, porteur d'une arme
     * de mêlée). Jamais un monstre, une chute, le feu ou le vide ; ni un joueur en créatif ou en spectateur.
     */
    private static void tag(LivingDamageEvent e) {
        if (e.isCanceled() || e.getAmount() <= 0) return;
        if (!(e.getEntity() instanceof ServerPlayer victime)) return;
        if (!(auteur(e.getSource()) instanceof ServerPlayer attaquant) || attaquant == victime) return;
        if (horsJeu(attaquant) || horsJeu(victime)) return;
        CombatTag.marquer(attaquant, victime);
    }

    private static boolean horsJeu(ServerPlayer p) {
        return p.isCreative() || p.isSpectator();
    }

    // ------------------------------------------------------------------ indicateur
    /** Envoie l'indicateur (secondes restantes) au plus une fois par seconde ; sinon renvoi au prochain passage. */
    static void indicateur(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long dernier = ENVOI.get(p.getUUID());
        if (dernier != null && now - dernier < 1000) {
            A_RENVOYER.add(p.getUUID());
            return;
        }
        A_RENVOYER.remove(p.getUUID());
        ENVOI.put(p.getUUID(), now);
        CombatReseau.envoyer(p, (int) Math.ceil(CombatTag.restantMs(p) / 1000.0));
    }

    /** Indicateur masqué tout de suite (tag effacé). */
    static void indicateurMasque(ServerPlayer p) {
        A_RENVOYER.remove(p.getUUID());
        ENVOI.remove(p.getUUID());
        CombatReseau.envoyer(p, 0);
    }

    /** Renvois différés (toutes les 5 ticks), fin des tags et clé retirée (toutes les 20 ticks). */
    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
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

    // ------------------------------------------------------------------ déconnexion = mort
    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) surDeconnexion(p);
    }

    /**
     * Traitement d'une déconnexion (HIGHEST, avant les autres mods et avant la sauvegarde du joueur faite par
     * {@code PlayerList.remove}) : joueur tagué -> tué (corps laissé) et journalisé, sauf arrêt du serveur, créatif ou
     * spectateur, mort déjà, ou règle désactivée. Rend vrai si le joueur a été tué.
     */
    static boolean surDeconnexion(ServerPlayer p) {
        UUID u = p.getUUID();
        long reste = CombatTag.restantMs(u);
        ENVOI.remove(u);
        A_RENVOYER.remove(u);
        if (p.getTags().contains("heraliz_bot") || reste <= 0) {   // bots de charge (/bots) : retirés sans mourir
            CombatTag.effacer(u);
            p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
            return false;
        }
        MinecraftServer s = p.getServer();
        String nom = p.getGameProfile().getName();
        boolean serveurArrete = arret || s == null || !s.isRunning();
        if (serveurArrete || !CombatConfig.vrai(CombatConfig.MORT_DECONNEXION, true) || p.isCreative() || p.isSpectator()
                || p.isDeadOrDying()) {
            HeraliszUtils.LOG.info("[combat] {} deconnecte en combat ({} s) sans sanction ({})", nom, (reste + 999) / 1000,
                    serveurArrete ? "arret du serveur" : p.isCreative() || p.isSpectator() ? "creatif/spectateur" : "regle");
            CombatTag.effacer(u);
            p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
            return false;
        }
        HeraliszUtils.LOG.warn("[combat] {} s'est deconnecte en combat ({} s restantes{}) : tue, corps laisse en {} {} {} {}",
                nom, (reste + 999) / 1000, ATerre.aTerre(p) ? ", a terre" : "", p.level().dimension().location(),
                (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
        // la clé doit disparaître AVANT la sauvegarde du joueur (faite juste après cet évènement par PlayerList.remove)
        CombatTag.effacer(u);
        p.getPersistentData().remove(CombatTag.CLE_JUSQUA);
        p.getPersistentData().putBoolean(CLE_PUNI, true);
        tuer(p);
        boolean mort = p.isDeadOrDying();
        if (!mort) HeraliszUtils.LOG.error("[combat] {} toujours en vie apres la sanction (un autre mod a refuse la mort)", nom);
        return mort;
    }

    /**
     * Tue le joueur pour déconnexion en combat. Déjà à terre (Réanimation) : achevé par Réanimation avec notre type de
     * dégâts (kill crédité à celui qui l'a mis à terre). Sinon dégâts {@code heralisz_utils:deconnexion_combat} (ignorent
     * armure, effets et invulnérabilité) ; mis à terre quand même -> achèvement ; dégâts refusés (zone protégée, autre
     * mod) -> mort directe.
     */
    static void tuer(ServerPlayer p) {
        if (ATerre.aTerre(p)) ATerre.achever(p, DECONNEXION);
        if (p.isDeadOrDying()) return;
        DamageSource src = source(p);
        p.hurt(src, Float.MAX_VALUE);
        if (!p.isDeadOrDying() && ATerre.aTerre(p)) ATerre.achever(p, DECONNEXION);
        if (!p.isDeadOrDying()) {
            p.setHealth(0f);
            p.die(src);
            if (ATerre.aTerre(p)) ATerre.achever(p, DECONNEXION);
        }
    }

    /** Source des dégâts de la sanction (type maison, sinon « /kill » si le pack de données du mod est absent). */
    static DamageSource source(ServerPlayer p) {
        Optional<Holder.Reference<DamageType>> h = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DECONNEXION);
        return h.<DamageSource>map(DamageSource::new).orElseGet(() -> p.damageSources().genericKill());
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        var d = p.getPersistentData();
        if (CombatTag.estTague(p)) {
            // tag gardé (mort à la déconnexion désactivée) : clé et indicateur rafraîchis
            d.putLong(CombatTag.CLE_JUSQUA, CombatTag.finMs(p.getUUID()));
            indicateur(p);
        } else if (d.contains(CombatTag.CLE_JUSQUA)) {
            d.remove(CombatTag.CLE_JUSQUA);
        }
        if (d.getBoolean(CLE_PUNI)) {
            d.remove(CLE_PUNI);
            d.remove(CLE_PUNI_A_TERRE);
            p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.combat.puni",
                    "Vous vous êtes déconnecté en plein combat : votre personnage est mort et son corps est resté sur place.")
                    .withStyle(ChatFormatting.RED));
        } else if (d.getBoolean(CLE_PUNI_A_TERRE)) {
            d.remove(CLE_PUNI_A_TERRE);
            p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.combat.puni_a_terre",
                    "Vous vous êtes déconnecté à terre : votre personnage est mort et son corps est resté sur place.")
                    .withStyle(ChatFormatting.RED));
        }
        // ancien tag d'ArkaniaZ (geckolib222) : effet resté sur un joueur sauvegardé avant le correctif
        MobEffect ancien = ForgeRegistries.MOB_EFFECTS.containsKey(COMBAT_LOG) ? ForgeRegistries.MOB_EFFECTS.getValue(COMBAT_LOG) : null;
        if (ancien != null && p.hasEffect(ancien)) p.removeEffect(ancien);
    }

    /** Ancien tag de combat d'ArkaniaZ ({@code geckolib:combat_log}) refusé partout. */
    private static void effet(MobEffectEvent.Applicable e) {
        if (e.getEntity().level().isClientSide) return;
        if (COMBAT_LOG.equals(ForgeRegistries.MOB_EFFECTS.getKey(e.getEffectInstance().getEffect()))) e.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ mort réelle
    /**
     * Mort réelle (non annulée, ni mise à terre de Réanimation) : fin du tag. Exécution de Réanimation : le bourreau est
     * tagué (ni créatif ni spectateur). Déconnexion à terre tuée par Réanimation : message à la reconnexion (clé posée
     * avant la sauvegarde du joueur, faite après l'évènement de déconnexion).
     */
    private static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getEntity().level().isClientSide) return;
        CombatTag.effacer(p.getUUID());
        ResourceLocation type = e.getSource().typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
        if (REANIMATION_EXECUTION.equals(type) && auteur(e.getSource()) instanceof ServerPlayer bourreau && bourreau != p
                && !horsJeu(bourreau)) {
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

    /** Combat ou à terre : toute commande hors de la liste permise est refusée. Rend vrai si la commande est refusée. */
    private static boolean refusCombat(ServerPlayer p, String racine, CommandEvent e) {
        if (!CombatConfig.vrai(CombatConfig.REFUS_COMMANDES, true)) return false;
        long reste = CombatTag.restantMs(p);
        // à terre (Réanimation) : pas de fuite ni de rangement pendant qu'on peut l'achever, le fouiller ou le relever
        boolean aTerre = reste <= 0 && ATerre.aTerre(p);
        if (reste <= 0 && !aTerre) return false;
        if (CombatConfig.vrai(CombatConfig.OPS_EXEMPTES, false) && p.hasPermissions(2)) return false;
        if (commandes.contains(racine)) return false;
        e.setCanceled(true);
        if (aTerre) {
            // Réanimation masque la barre d'action d'un joueur à terre (il ne garde que le chat et les titres)
            p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.combat.commande_refusee_a_terre",
                    "Impossible à terre.").withStyle(s -> s.withColor(0xFF8A8A)));
            return true;
        }
        p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.combat.commande_refusee", "Impossible en combat · %s s",
                (reste + 999) / 1000).withStyle(s -> s.withColor(0xFF8A8A)), true);
        return true;
    }

    /** Sur la carte (overworld), en survie ou aventure, hors staff (niveau 2) : toute commande hors de la liste est refusée. */
    private static void refusCarte(ServerPlayer p, String racine, CommandEvent e) {
        if (!CombatConfig.vrai(CombatConfig.CARTE_ACTIF, true)) return;
        if (p.level().dimension() != Level.OVERWORLD) return;
        if (p.isCreative() || p.isSpectator() || p.hasPermissions(2)) return;
        if (commandesCarte.contains(racine)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.combat.carte_commande_refusee",
                "Impossible sur la carte : seul /tpa marche ici.").withStyle(s -> s.withColor(0xFF8A8A)), true);
    }

    /** Racine d'une commande en minuscules, sans « / » ni espace de noms ({@code essentials:spawn} -> {@code spawn}). */
    static String racine(String brut) {
        String b = brut == null ? "" : brut.trim();
        if (b.startsWith("/")) b = b.substring(1);
        int sp = b.indexOf(' ');
        String r = (sp < 0 ? b : b.substring(0, sp)).toLowerCase(Locale.ROOT);
        int deux = r.indexOf(':');
        return deux >= 0 ? r.substring(deux + 1) : r;
    }

    /**
     * État « à terre » de Réanimation par réflexion (mod absent = jamais à terre) : {@code core.Downed.isDowned(Entity)},
     * achèvement par {@code core.DownedManager.kill(DownedState, ResourceKey<DamageType>)} (vraie mort, kill crédité à
     * celui qui a mis à terre, corps laissé).
     */
    static final class ATerre {
        private static boolean pret;
        @Nullable
        private static Method etat, kill;

        private ATerre() {
        }

        static boolean aTerre(Player p) {
            return TirATerre.aTerre(p);
        }

        private static void resoudre() {
            if (pret) return;
            pret = true;
            try {
                Class<?> manager = Class.forName("fr.heraliz.reanimation.core.DownedManager");
                Class<?> state = Class.forName("fr.heraliz.reanimation.core.DownedState");
                etat = manager.getMethod("get", Entity.class);
                kill = manager.getMethod("kill", state, ResourceKey.class);
            } catch (ClassNotFoundException e) {
                etat = kill = null;
            } catch (ReflectiveOperationException | LinkageError e) {
                etat = kill = null;
                HeraliszUtils.LOG.error("[combat] Reanimation present mais API inattendue : achevement ignore", e);
            }
        }

        /** Achève un joueur à terre (vraie mort, une seule fois) ; faux si personne ne l'a achevé. */
        static boolean achever(Player p, @Nullable ResourceKey<DamageType> type) {
            resoudre();
            if (etat == null || kill == null) return false;
            try {
                Object e = etat.invoke(null, p);
                if (e == null) return false;
                ResourceKey<DamageType> t = type;
                if (t != null && p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(t).isEmpty()) t = null;
                if (t == null) t = ResourceKey.create(Registries.DAMAGE_TYPE, REANIMATION_DECONNEXION);
                kill.invoke(null, e, t);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeraliszUtils.LOG.error("[combat] Reanimation : achevement de {} impossible", p.getGameProfile().getName(), ex);
                return false;
            }
        }
    }
}
