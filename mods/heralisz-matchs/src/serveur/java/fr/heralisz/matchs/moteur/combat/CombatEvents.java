package fr.heralisz.matchs.moteur.combat;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.compat.Revive;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat en match et tag de combat (MATCHS_SPEC §1.2, §1.4) :
 * <ul>
 *     <li>{@code LivingHurtEvent} HIGHEST : degats entre joueurs de parties differentes annules ; sinon
 *     {@link Partie#surDegats} fixe le montant (0 = annule) ;</li>
 *     <li>{@code LivingDamageEvent} HIGHEST : degats mortels (>= sante + absorption) annules puis
 *     {@link Partie#surElimination} avec le tueur reel ; garde de 10 ticks contre les eliminations multiples (plombs
 *     d'un fusil a pompe) pendant laquelle tout degat est annule ;</li>
 *     <li>{@code LivingDeathEvent} HIGHEST : filet, mort annulee et elimination ;</li>
 *     <li>{@code LivingDamageEvent} LOWEST (degats reellement appliques) : tag de combat des deux joueurs hors match
 *     (PvP seulement), voir {@link CombatService} (indicateur, cle persistante, deconnexion, commandes) ;</li>
 *     <li>{@code MobEffectEvent.Applicable} : malus d'ArkaniaZ refuses en match, {@code geckolib:combat_log} refuse
 *     partout ({@link RestrictionsMatch}).</li>
 * </ul>
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
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatEvents::tag);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, CombatEvents::achevement);
        // LOWEST : notre refus est le dernier mot (aucun autre mod ne peut le repasser a ALLOW apres nous)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatEvents::effet);
        CombatService.enregistrer();
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
            HeraliszMatchs.LOG.error("[matchs] surDegats de {} en erreur", pv.id(), ex);
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
            HeraliszMatchs.LOG.error("[matchs] surElimination de {} en erreur", partie.id(), ex);
        }
    }

    /**
     * Tag de combat : degats reellement appliques (LOWEST, non annules, > 0) entre deux joueurs distincts, auteur reel
     * de la source ({@code DamageSource#getEntity} : tireur d'une balle TaCZ, lanceur d'une grenade ou d'un projectile,
     * proprietaire d'une explosion, porteur d'une arme de melee). Jamais un mob, une chute, le feu ou le vide ; jamais en
     * match ; ni un joueur en creatif ou en spectateur (actions d'administration).
     */
    private static void tag(LivingDamageEvent e) {
        if (e.isCanceled() || e.getAmount() <= 0) return;
        if (!(e.getEntity() instanceof ServerPlayer victime)) return;
        if (!(auteur(e.getSource()) instanceof ServerPlayer attaquant) || attaquant == victime) return;
        // en match, pas de tag (sinon un joueur sortirait d'un match « en combat »)
        if (MatchApi.enMatch(victime) || MatchApi.enMatch(attaquant)) return;
        if (horsJeu(attaquant) || horsJeu(victime)) return;
        // CombatTag.marquer pose la cle persistante et envoie l'indicateur (cadence limitee)
        CombatTag.marquer(attaquant, victime);
    }

    private static boolean horsJeu(ServerPlayer p) {
        return p.isCreative() || p.isSpectator();
    }

    /**
     * Achevement d'un joueur a terre avec l'ancien PlayerRevive (il annule le coup et execute la victime : aucun degat
     * n'est applique) : l'attaquant est tague comme pour un vrai coup ({@code LivingHurtEvent} LOWEST, annules compris).
     * Avec Reanimation, le coup sur un joueur a terre n'est pas annule (il passe par {@link #tag}) et l'execution tague le
     * bourreau a la mort ({@code CombatService}) : un coup annule par une protection ne doit pas taguer ici.
     */
    private static void achevement(LivingHurtEvent e) {
        if (!e.isCanceled() || !(e.getEntity() instanceof ServerPlayer victime)) return;
        if (!(auteur(e.getSource()) instanceof ServerPlayer attaquant) || attaquant == victime) return;
        if (MatchApi.enMatch(victime) || MatchApi.enMatch(attaquant) || horsJeu(attaquant) || !Revive.aTerrePlayerRevive(victime)) return;
        CombatTag.marquer(attaquant);
    }

    /** Envoie l'indicateur « EN COMBAT 15 s » (au plus une fois par seconde ; le client decompte seul). */
    public static void indicateur(ServerPlayer p) {
        CombatService.indicateur(p);
    }

    /** Malus d'ArkaniaZ refuses en match, ancien tag {@code geckolib:combat_log} refuse partout. */
    private static void effet(MobEffectEvent.Applicable e) {
        RestrictionsMatch.effet(e);
    }

    /** Oubli d'un joueur deconnecte. */
    public static void oublier(UUID u) {
        DERNIERE.remove(u);
        CombatService.oublier(u);
        RestrictionsMatch.oublier(u);
    }

    /** Arret du serveur. */
    public static void vider() {
        DERNIERE.clear();
        CombatService.vider();
        RestrictionsMatch.vider();
    }
}
