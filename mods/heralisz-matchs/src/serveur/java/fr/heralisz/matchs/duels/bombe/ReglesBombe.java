package fr.heralisz.matchs.duels.bombe;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Règles serveur de la bombe des duels (16/10, Matchs 0.11.0), en plus de DuelPartie :
 * <ul>
 *     <li><b>Balles arrêtées</b> : une balle de TaCZ qui touche une bombe ({@link BombeDuel}) ou l'ordinateur posé
 *     ({@link OrdinateurDuel}) s'y arrête sans dégât ni rien derrière : coup annulé ({@code EntityHurtByGunEvent.Pre},
 *     annulable) et perforation remise à zéro (champ {@code pierce} de la balle : TaCZ la retire juste après).</li>
 *     <li><b>Joueur qui amorce ou pirate</b> ({@link #bloquer}, décidé par DuelPartie à chaque tick) : immobilisé (vitesse
 *     de déplacement à zéro par un modificateur d'attribut temporaire, rien de déplacé à la main : Spartan voit un joueur
 *     qui ne bouge pas), ni tir, ni rafale, ni rechargement, ni coup de crosse (événements annulables de TaCZ, côté
 *     serveur : ni balle, ni munition, ni son pour les autres), ni visée (visée de TaCZ coupée à chaque tick), ni
 *     changement d'arme (case de la barre remise à chaque tick), ni coup porté (attaque, couteaux : dégâts dont il est
 *     l'auteur direct annulés), ni objet utilisé (soins, grenades). Il peut regarder autour de lui ; relevé, l'action
 *     s'arrête comme avant et tout lui est rendu.</li>
 * </ul>
 * TaCZ par réflexion (absent : seules les règles de Minecraft s'appliquent). Le jeu du joueur bloque aussi le tir et la
 * visée chez lui (duels/client/ActionClient) pour éviter les faux tirs à l'écran ; seul le serveur fait foi. Fil serveur.
 */
public final class ReglesBombe {
    private ReglesBombe() {
    }

    /** Modificateur de vitesse (×0) du joueur qui amorce ou pirate ; temporaire : jamais sauvegardé avec le joueur. */
    private static final UUID IMMOBILE = UUID.fromString("5d0e6a3c-8b1f-4c27-9e44-2f7a91c3d6b8");
    private static final String NOM_IMMOBILE = "heralisz_matchs:duel_action";

    /** Joueurs immobilisés -> case de la barre gardée en main. */
    private static final Map<UUID, Integer> BLOQUES = new HashMap<>();
    private static boolean construit, taczBranche;
    @Nullable
    private static Method operateurDe, viser;

    // ------------------------------------------------------------------ branchement
    /** Écouteurs Forge (appelé une fois au chargement, partie serveur des duels). */
    public static void construire() {
        if (construit) return;
        construit = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ReglesBombe::attaque);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ReglesBombe::degats);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ReglesBombe::objet);
        MinecraftForge.EVENT_BUS.addListener(ReglesBombe::tick);
        MinecraftForge.EVENT_BUS.addListener(ReglesBombe::deconnexion);
    }

    /** Écouteurs TaCZ, par réflexion (une fois, au démarrage du serveur, si TaCZ est chargé). */
    @SuppressWarnings("unchecked")
    public static void brancherTacz() {
        if (taczBranche) return;
        taczBranche = true;
        if (ModList.get() == null || !ModList.get().isLoaded("tacz")) return;
        // tir, rafale, rechargement, coup de crosse : refusés au joueur immobilisé
        refuser("com.tacz.guns.api.event.common.GunShootEvent", "getShooter");
        refuser("com.tacz.guns.api.event.common.GunFireEvent", "getShooter");
        refuser("com.tacz.guns.api.event.common.GunReloadEvent", "getEntity");
        refuser("com.tacz.guns.api.event.common.GunMeleeEvent", "getShooter");
        // balle arrêtée par la bombe ou l'ordinateur
        try {
            Class<?> pre = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Method cible = pre.getMethod("getHurtEntity"), balle = pre.getMethod("getBullet"), cote = pre.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, (Class<Event>) pre, e -> {
                try {
                    if (!"SERVER".equals(String.valueOf(cote.invoke(e)))) return;
                    Object c = cible.invoke(e);
                    if (!(c instanceof BombeDuel) && !(c instanceof OrdinateurDuel)) return;
                    e.setCanceled(true);
                    if (balle.invoke(e) instanceof Entity b) arreter(b);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // coup laissé à TaCZ (la bombe ne prend jamais de dégâts)
                }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] balles TaCZ sur la bombe : evenement introuvable ({})", ex.toString());
        }
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            operateurDe = op.getMethod("fromLivingEntity", net.minecraft.world.entity.LivingEntity.class);
            viser = op.getMethod("aim", boolean.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] visee TaCZ introuvable ({}) : la visee ne sera pas coupee pendant l'amorcage", ex.toString());
        }
        HeraliszMatchs.LOG.info("[duels] regles de la bombe branchees sur TaCZ");
    }

    /** Annule un événement de TaCZ (annulable) dont l'auteur est un joueur immobilisé, côté serveur. */
    @SuppressWarnings("unchecked")
    private static void refuser(String classe, String auteur) {
        try {
            Class<?> c = Class.forName(classe);
            Method qui = c.getMethod(auteur), cote = c.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, (Class<Event>) c, e -> {
                try {
                    if (e.isCancelable() && "SERVER".equals(String.valueOf(cote.invoke(e))) && bloque(qui.invoke(e) instanceof Entity x ? x : null)) {
                        e.setCanceled(true);
                    }
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // laissé à TaCZ
                }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] evenement TaCZ {} introuvable ({})", classe, ex.toString());
        }
    }

    /** Champ « pierce » (perforations restantes) de la balle, cherché dans sa classe et ses parents ; mis à zéro. */
    private static void arreter(Entity balle) {
        for (Class<?> c = balle.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("pierce");
                f.setAccessible(true);
                f.setInt(balle, 0);
                return;
            } catch (NoSuchFieldException ex) {
                // classe parente
            } catch (ReflectiveOperationException | RuntimeException ex) {
                break;
            }
        }
        // sans le champ : la balle est retirée (elle ne touche plus rien aux ticks suivants)
        balle.discard();
    }

    // ------------------------------------------------------------------ API (DuelPartie)
    /** Immobilise (ou libère) le joueur qui amorce ou pirate. Sans effet si rien ne change. */
    public static void bloquer(ServerPlayer p, boolean oui) {
        AttributeInstance vitesse = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (oui) {
            if (BLOQUES.containsKey(p.getUUID())) return;
            BLOQUES.put(p.getUUID(), p.getInventory().selected);
            if (vitesse != null && vitesse.getModifier(IMMOBILE) == null) {
                vitesse.addTransientModifier(new AttributeModifier(IMMOBILE, NOM_IMMOBILE, -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            p.setSprinting(false);
            viser(p);
        } else {
            BLOQUES.remove(p.getUUID());
            if (vitesse != null && vitesse.getModifier(IMMOBILE) != null) vitesse.removeModifier(IMMOBILE);
        }
    }

    /** Oublie un joueur parti (son modificateur, temporaire, est parti avec lui). */
    public static void oublier(UUID joueur) {
        BLOQUES.remove(joueur);
    }

    /** Vrai si ce joueur amorce ou pirate (immobilisé). */
    public static boolean bloque(@Nullable Entity e) {
        return e instanceof ServerPlayer && BLOQUES.containsKey(e.getUUID());
    }

    /** Arrêt du serveur. */
    public static void vider() {
        BLOQUES.clear();
    }

    // ------------------------------------------------------------------ écouteurs
    /** Coup porté (main nue, arme de mêlée, couteau, crosse) : refusé. */
    private static void attaque(AttackEntityEvent e) {
        if (bloque(e.getEntity())) e.setCanceled(true);
    }

    /** Dégâts dont le joueur immobilisé est l'auteur direct (couteaux des mods, coups portés par un autre chemin) : annulés. */
    private static void degats(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (bloque(e.getSource().getDirectEntity())) e.setCanceled(true);
    }

    /** Objet utilisé (soin, grenade, nourriture) : refusé. */
    private static void objet(PlayerInteractEvent.RightClickItem e) {
        if (!e.getLevel().isClientSide && bloque(e.getEntity())) {
            e.setCancellationResult(InteractionResult.FAIL);
            e.setCanceled(true);
        }
    }

    /** Chaque tick : même case de la barre en main, visée de TaCZ coupée, pas de course. */
    private static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !(e.player instanceof ServerPlayer p)) return;
        Integer c = BLOQUES.get(p.getUUID());
        if (c == null) return;
        if (p.getInventory().selected != c) {
            p.getInventory().selected = c;
            p.connection.send(new ClientboundSetCarriedItemPacket(c));
        }
        if (p.isSprinting()) p.setSprinting(false);
        viser(p);
    }

    private static void viser(ServerPlayer p) {
        if (operateurDe == null || viser == null) return;
        try {
            Object op = operateurDe.invoke(null, p);
            if (op != null) viser.invoke(op, false);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            viser = null;
        }
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        BLOQUES.remove(e.getEntity().getUUID());
    }
}
