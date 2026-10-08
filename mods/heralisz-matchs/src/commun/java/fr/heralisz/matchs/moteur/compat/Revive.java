package fr.heralisz.matchs.moteur.compat;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Etat « a terre » par reflexion tolerante (jamais de dependance dure ; mod absent = jamais a terre) :
 * <ul>
 *     <li><b>Reanimation</b> (mod {@code reanimation}, {@code fr.heraliz.reanimation}, remplace PlayerRevive le 02/10) :
 *     l'etat vit en memoire dans {@code core.DownedManager} (ni capacite ni evenement Forge). Lu par
 *     {@code core.Downed.isDowned(Entity)} (les deux cotes : serveur = etat, client = drapeaux synchronises) ;
 *     achevement par {@code DownedManager.kill(DownedState, ResourceKey<DamageType>)} (vraie mort, le kill est credite a
 *     celui qui a mis a terre via {@code setLastHurtByPlayer}, corps du mod corpse) ; relever sans effet par
 *     {@code DownedManager.reviveNow(LivingEntity)}. Types de degats {@code reanimation:bleed_out|give_up|disconnect}
 *     (sans auteur) et {@code reanimation:execution} (auteur = le bourreau), tous dans {@code bypasses_invulnerability}.
 *     Reanimation met a terre sur {@code LivingDeathEvent} priorite HIGH (evenements deja annules ignores) : le filet du
 *     moteur (HIGHEST, {@code CombatEvents}) passe avant, il n'y a donc jamais de mise a terre en match.</li>
 *     <li><b>PlayerRevive</b> (ancien, garde pour la periode de transition) : {@code PlayerReviveServer.isBleeding(Player)}
 *     et {@code kill(Player)}.</li>
 * </ul>
 * Fil serveur, sauf {@link #enregistrer()} (les deux cotes).
 */
public final class Revive {
    private Revive() {
    }

    /** Identifiant du mod Reanimation. */
    public static final String REANIMATION = "reanimation";
    private static final String R_DOWNED = "fr.heraliz.reanimation.core.Downed";
    private static final String R_MANAGER = "fr.heraliz.reanimation.core.DownedManager";
    private static final String R_ETAT = "fr.heraliz.reanimation.core.DownedState";
    private static final String PR = "team.creative.playerrevive.server.PlayerReviveServer";

    private static boolean pretR;
    @Nullable
    private static Method rATerre, rEtat, rKill, rRelever;
    private static boolean pretPR, pretPRKill;
    @Nullable
    private static Method saigne, achever;

    // ------------------------------------------------------------------ Reanimation
    private static void resoudreReanimation() {
        if (pretR) return;
        pretR = true;
        try {
            Class<?> downed = Class.forName(R_DOWNED);
            Class<?> manager = Class.forName(R_MANAGER);
            Class<?> etat = Class.forName(R_ETAT);
            rATerre = downed.getMethod("isDowned", Entity.class);
            rEtat = manager.getMethod("get", Entity.class);
            rKill = manager.getMethod("kill", etat, ResourceKey.class);
            rRelever = manager.getMethod("reviveNow", LivingEntity.class);
            HeraliszMatchs.LOG.info("[matchs] Reanimation detecte : etat a terre, achevement et tirs TaCZ a terre geres par le moteur");
        } catch (ClassNotFoundException e) {
            rATerre = rEtat = rKill = rRelever = null;
        } catch (ReflectiveOperationException | LinkageError e) {
            rATerre = rEtat = rKill = rRelever = null;
            HeraliszMatchs.LOG.error("[matchs] Reanimation present mais API inattendue : etat a terre ignore", e);
        }
    }

    /** Vrai si le mod Reanimation est charge et son API trouvee. */
    public static boolean reanimation() {
        resoudreReanimation();
        return rATerre != null;
    }

    /** Vrai si l'entite est a terre au sens de Reanimation (les deux cotes). */
    public static boolean aTerreReanimation(@Nullable Entity e) {
        resoudreReanimation();
        if (rATerre == null || e == null) return false;
        try {
            return Boolean.TRUE.equals(rATerre.invoke(null, e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    @Nullable
    private static Object etatReanimation(Player p) {
        resoudreReanimation();
        if (rEtat == null) return null;
        try {
            return rEtat.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ PlayerRevive (ancien)
    /** Vrai si le joueur saigne au sens de PlayerRevive (ancien mod ; faux s'il est absent). */
    public static boolean aTerrePlayerRevive(Player p) {
        if (!pretPR) {
            pretPR = true;
            try {
                saigne = Class.forName(PR).getMethod("isBleeding", Player.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                saigne = null;
            }
        }
        if (saigne == null) return p.getPersistentData().getBoolean("playerrevive:bleeding");
        try {
            return (Boolean) saigne.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ facade
    /** Vrai si le joueur est a terre (Reanimation, ou PlayerRevive s'il est encore installe). */
    public static boolean aTerre(Player p) {
        if (p == null) return false;
        return aTerreReanimation(p) || aTerrePlayerRevive(p);
    }

    /**
     * Acheve un joueur a terre : vraie mort, corps compris, une seule fois. Reanimation : {@code DownedManager.kill} avec le
     * type {@code type} s'il est enregistre (sinon {@code reanimation:disconnect}) ; le kill revient a celui qui l'a mis a
     * terre. PlayerRevive : {@code PlayerReviveServer.kill}. Rend faux si personne ne l'a acheve (pas a terre, mod absent,
     * erreur).
     */
    public static boolean achever(Player p, @Nullable ResourceKey<DamageType> type) {
        Object etat = etatReanimation(p);
        if (etat != null && rKill != null) {
            ResourceKey<DamageType> t = type;
            if (t != null && p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(t).isEmpty()) t = null;
            if (t == null) t = ResourceKey.create(Registries.DAMAGE_TYPE, new net.minecraft.resources.ResourceLocation(REANIMATION, "disconnect"));
            try {
                rKill.invoke(null, etat, t);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                HeraliszMatchs.LOG.error("[matchs] Reanimation : achevement de {} impossible", p.getGameProfile().getName(), e);
            }
        }
        if (!aTerrePlayerRevive(p)) return false;
        if (!pretPRKill) {
            pretPRKill = true;
            try {
                achever = Class.forName(PR).getMethod("kill", Player.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                achever = null;
            }
        }
        if (achever == null) return false;
        try {
            achever.invoke(null, p);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeraliszMatchs.LOG.error("[matchs] PlayerRevive : achevement de {} impossible", p.getGameProfile().getName(), e);
            return false;
        }
    }

    /**
     * Releve tout de suite un joueur a terre de Reanimation (sans soin ni effet de faiblesse ; vie = {@code reviveHealth}).
     * Filet du moteur pour un joueur en match. Rend vrai s'il etait a terre.
     */
    public static boolean relever(Player p) {
        resoudreReanimation();
        if (rRelever == null || p == null) return false;
        try {
            return Boolean.TRUE.equals(rRelever.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeraliszMatchs.LOG.error("[matchs] Reanimation : relever {} impossible", p.getGameProfile().getName(), e);
            return false;
        }
    }

    // ------------------------------------------------------------------ TaCZ : pas de tir a terre
    /**
     * Branche (les deux cotes, a la construction du mod) le refus des tirs et des coups de crosse TaCZ d'un joueur a terre
     * de Reanimation ({@code GunShootEvent} / {@code GunMeleeEvent}, annulables, emis cote client ET serveur). Remplace
     * {@code playerrevive_compat} (qui le faisait pour PlayerRevive). Sans TaCZ ou sans Reanimation : rien.
     */
    public static void enregistrer() {
        ModList ml = ModList.get();
        if (ml == null || !ml.isLoaded("tacz") || !ml.isLoaded(REANIMATION)) return;
        ecouterTacz("com.tacz.guns.api.event.common.GunShootEvent");
        ecouterTacz("com.tacz.guns.api.event.common.GunMeleeEvent");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event> void ecouterTacz(String classe) {
        try {
            Class<T> c = (Class<T>) Class.forName(classe).asSubclass(Event.class);
            Method tireur = c.getMethod("getShooter");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, c, e -> {
                try {
                    if (tireur.invoke(e) instanceof Player p && aTerreReanimation(p)) e.setCanceled(true);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // evenement TaCZ inattendu : on laisse passer
                }
            });
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            HeraliszMatchs.LOG.warn("[matchs] TaCZ : {} introuvable, tir a terre non bloque ({})", classe, e.toString());
        }
    }
}
