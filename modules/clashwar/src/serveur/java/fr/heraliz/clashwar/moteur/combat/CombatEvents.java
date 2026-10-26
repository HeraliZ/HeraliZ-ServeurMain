package fr.heraliz.clashwar.moteur.combat;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.MatchApi;
import fr.heraliz.clashwar.moteur.api.Partie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat en match (MATCHS_SPEC §1.2) :
 * <ul>
 *     <li>{@code LivingHurtEvent} HIGHEST : degats entre joueurs de parties differentes annules ; sinon
 *     {@link Partie#surDegats} fixe le montant (0 = annule) ;</li>
 *     <li>{@code LivingDamageEvent} HIGHEST : degats mortels (>= sante + absorption) annules puis
 *     {@link Partie#surElimination} avec le tueur reel ; garde de 10 ticks contre les eliminations multiples (plombs
 *     d'un fusil a pompe) pendant laquelle tout degat est annule ;</li>
 *     <li>{@code LivingDeathEvent} HIGHEST : filet, mort annulee et elimination ;</li>
 * </ul>
 * Le tag de combat hors match et les malus d'ArkaniaZ refuses en match sont dans le noyau HeraliZ.
 * Fil serveur.
 */
public final class CombatEvents {
    private CombatEvents() {
    }

    /** Ticks pendant lesquels un joueur tout juste elimine ne peut ni subir de degats ni etre re-elimine. */
    public static final int GARDE_TICKS = 10;
    /** Tick serveur de la derniere elimination par joueur. */
    private static final Map<UUID, Integer> DERNIERE = new HashMap<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CombatEvents::blesse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CombatEvents::degatsMortels);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, CombatEvents::mort);
    }

    /** Auteur reel (proprietaire du projectile ou de l'explosif) si c'est une entite vivante. */
    @Nullable
    public static LivingEntity auteur(DamageSource source) {
        Entity e = source.getEntity();
        return e instanceof LivingEntity l ? l : null;
    }

    private static int tickServeur(ServerPlayer p) {
        return p.getServer() == null ? 0 : p.getServer().getTickCount();
    }

    /** Vrai si le joueur vient d'etre elimine (garde de {@link #GARDE_TICKS}). */
    public static boolean sousGarde(ServerPlayer p) {
        Integer t = DERNIERE.get(p.getUUID());
        return t != null && tickServeur(p) - t < GARDE_TICKS;
    }

    private static void blesse(LivingHurtEvent e) {
        LivingEntity victime = e.getEntity();
        LivingEntity attaquant = auteur(e.getSource());
        Partie pv = victime instanceof ServerPlayer sp ? MatchApi.partieDe(sp) : null;
        Partie pa = attaquant instanceof ServerPlayer sa ? MatchApi.partieDe(sa) : null;
        // jamais de degats entre un joueur en match et un joueur d'ailleurs (ou d'une autre partie)
        if (attaquant instanceof ServerPlayer && victime instanceof ServerPlayer && attaquant != victime && pv != pa) {
            e.setCanceled(true);
            return;
        }
        if (pv == null) return;
        ServerPlayer sp = (ServerPlayer) victime;
        if (pv.phase() == Partie.Phase.FIN || pv.phase() == Partie.Phase.TERMINEE || pv.spectateurs().contains(sp.getUUID()) || sousGarde(sp)) {
            e.setCanceled(true);
            return;
        }
        float m;
        try {
            m = pv.surDegats(sp, attaquant, e.getSource(), e.getAmount());
        } catch (RuntimeException ex) {
            HeralizClashWar.LOG.error("[matchs] surDegats de {} en erreur", pv.id(), ex);
            m = e.getAmount();
        }
        if (!(m > 0)) e.setCanceled(true);
        else e.setAmount(m);
    }

    private static void degatsMortels(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Partie partie = MatchApi.partieDe(p);
        if (partie == null) return;
        if (sousGarde(p)) {
            e.setCanceled(true);
            return;
        }
        if (e.getAmount() >= p.getHealth() + p.getAbsorptionAmount()) {
            e.setCanceled(true);
            eliminer(p, partie, auteur(e.getSource()), e.getSource());
        }
    }

    private static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Partie partie = MatchApi.partieDe(p);
        if (partie == null) return;
        // filet : jamais de vraie mort en match (ni mise a terre Reanimation/PlayerRevive, ni corps, ni stats). HIGHEST et
        // annulation : Reanimation (HIGH, evenements annules ignores) ne voit jamais la mort d'un joueur en match
        e.setCanceled(true);
        if (p.getHealth() <= 0) p.setHealth(1f);
        if (!sousGarde(p)) eliminer(p, partie, auteur(e.getSource()), e.getSource());
    }

    /** Elimination unique (garde), exceptions du mode attrapees. */
    public static void eliminer(ServerPlayer p, Partie partie, @Nullable LivingEntity tueur, DamageSource source) {
        DERNIERE.put(p.getUUID(), tickServeur(p));
        p.fallDistance = 0;
        p.clearFire();
        try {
            partie.surElimination(p, tueur == p ? null : tueur, source);
        } catch (RuntimeException ex) {
            HeralizClashWar.LOG.error("[matchs] surElimination de {} en erreur", partie.id(), ex);
        }
    }

    /** Oubli d'un joueur deconnecte. */
    public static void oublier(UUID u) {
        DERNIERE.remove(u);
        RestrictionsMatch.oublier(u);
    }

    /** Arret du serveur. */
    public static void vider() {
        DERNIERE.clear();
        RestrictionsMatch.vider();
    }
}
