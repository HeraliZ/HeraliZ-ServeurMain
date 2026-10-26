package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * IA des zombies HeraliZ (choix du propriétaire, 24/10 : « IA des zombies allégée », « de loin il dort », « une belle
 * variété, assez résistants »). Serveur seulement.
 * <ul>
 *     <li>À l'arrivée de chaque {@link ZombieSurvie} : retiré de l'IA du zombie de Minecraft ce qui ne sert à rien sur
 *     HeraliZ et coûte cher (recherche d'un village la nuit {@link MoveThroughVillageGoal}, recherche d'oeufs de tortue
 *     {@link RemoveBlockGoal}, cibles villageois, golems et tortues : priorités 3 et 5 de Minecraft ; restent les joueurs,
 *     ce qui le frappe et les objectifs d'autres mods comme Zombie Awareness) ; variété tirée une fois
 *     ({@link #variete}).</li>
 *     <li>Chaque seconde, niveau d'IA selon le joueur le plus proche : proche, IA complète ; moyen, objectifs revus tous
 *     les {@code ia_rythme} ticks et chemins plus courts ; loin, objectifs tous les 20 ticks et chemins quatre fois plus
 *     courts. Au-delà de 24 blocs, Spigot ne les fait presque plus bouger, et {@link Peuplement} les retire au-delà de
 *     96 blocs.</li>
 *     <li>Recherches de chemin limitées par tick et partagées entre voisins : {@link CheminsZombies}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class IaZombies {
    private IaZombies() {
    }

    /** Clé de la variété dans les données de l'entité (gardée avec elle). */
    public static final String VARIETE = "HeralizVariete";
    /** Part de costauds en plus voulue par la zone d'apparition (données de l'entité, posée par {@link Peuplement}). */
    public static final String BONUS_COSTAUDS = "HeralizBonusCostauds";

    private static final Set<ZombieSurvie> SUIVIS = Collections.newSetFromMap(new WeakHashMap<>());
    /** Zombies par niveau d'IA au dernier passage : proche, moyen, loin, endormi (pour /zombies stats). */
    private static final int[] NIVEAUX = new int[4], COMPTE = new int[4];

    /** Priorité basse : après les autres mods qui ajoutent leurs objectifs à l'arrivée. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void arrivee(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ZombieSurvie z)) return;
        SUIVIS.add(z);
        variete(z);
        if (!ZombiesConfig.IA_ALLEGEE.get()) return;
        z.goalSelector.getAvailableGoals().removeIf(w -> !w.isRunning()
                && (w.getGoal() instanceof MoveThroughVillageGoal || w.getGoal() instanceof RemoveBlockGoal));
        z.targetSelector.getAvailableGoals().removeIf(w -> !w.isRunning()
                && w.getGoal().getClass() == NearestAttackableTargetGoal.class && w.getPriority() != 2);
        niveau(z, ZombiesConfig.IA_RYTHME.get(), ZombiesConfig.IA_CHEMINS.get().floatValue());
    }

    @SubscribeEvent
    public static void depart(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof ZombieSurvie z) SUIVIS.remove(z);
    }

    /**
     * Niveaux d'IA (25/10 : réparti sur 4 passages par seconde, un quart des zombies à chaque fois, au lieu de tous dans le
     * même tick que le peuplement ; niveau proche au rythme de Minecraft (3 ticks, avant 2) ; endormi au-delà de
     * {@code lod_sommeil} : objectifs toutes les 2 s et plus de chemin tant qu'il n'a pas de cible).
     */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        int t = e.getServer().getTickCount();
        if (e.phase != TickEvent.Phase.END || t % 5 != 2 || !ZombiesConfig.IA_ALLEGEE.get()) return;
        int quart = (t / 5) & 3;
        double proche = sq(ZombiesConfig.LOD_PROCHE.get()), moyen = sq(ZombiesConfig.LOD_MOYEN.get()), sommeil = sq(ZombiesConfig.LOD_SOMMEIL.get());
        int rythme = ZombiesConfig.IA_RYTHME.get();
        float chemins = ZombiesConfig.IA_CHEMINS.get().floatValue();
        if (quart == 0) {
            System.arraycopy(COMPTE, 0, NIVEAUX, 0, 4);
            java.util.Arrays.fill(COMPTE, 0);
        }
        for (ZombieSurvie z : SUIVIS.toArray(new ZombieSurvie[0])) {
            if ((z.getId() & 3) != quart || z.isRemoved() || !(z.level() instanceof ServerLevel lv)) continue;
            Player p = lv.getNearestPlayer(z, -1.0);
            double d = p == null ? Double.MAX_VALUE : p.distanceToSqr(z);
            if (d <= proche) {
                niveau(z, 3, 1.0F);
                COMPTE[0]++;
            } else if (d <= moyen) {
                niveau(z, rythme, chemins);
                COMPTE[1]++;
            } else if (d <= sommeil) {
                niveau(z, 20, chemins * 0.5F);
                COMPTE[2]++;
            } else {
                niveau(z, 40, chemins * 0.25F);
                if (z.getTarget() == null && !z.getNavigation().isDone()) z.getNavigation().stop();
                COMPTE[3]++;
            }
        }
    }

    /** Zombies suivis par niveau d'IA (proche, moyen, loin, endormi), relevé de la dernière seconde complète. */
    static int[] niveaux() {
        return NIVEAUX.clone();
    }

    static int suivis() {
        return SUIVIS.size();
    }

    private static double sq(int v) {
        return (double) v * v;
    }

    private static void niveau(ZombieSurvie z, int rythme, float chemins) {
        z.goalSelector.setNewGoalRate(rythme);
        z.targetSelector.setNewGoalRate(rythme);
        z.getNavigation().setMaxVisitedNodesMultiplier(chemins);
    }

    /**
     * Variété tirée à la première arrivée et gardée (données {@value #VARIETE}) : coureur, costaud ou marcheur, avec sa
     * vie, sa vitesse et ses dégâts (section {@code [varietes]} de {@code heraliz_zombies-server.toml}). Un zombie qui
     * en a déjà une n'est pas touché (ses valeurs sont gardées par Minecraft avec lui).
     */
    private static void variete(ZombieSurvie z) {
        if (!ZombiesConfig.VARIETES.get() || z.getPersistentData().contains(VARIETE)) return;
        double r = z.getRandom().nextDouble();
        double costauds = Math.min(1.0, ZombiesConfig.PART_COSTAUDS.get() + z.getPersistentData().getDouble(BONUS_COSTAUDS));
        String v = r < costauds ? "costaud" : r < costauds + ZombiesConfig.PART_COUREURS.get() ? "coureur" : "marcheur";
        z.getPersistentData().putString(VARIETE, v);
        double vie = switch (v) {
            case "costaud" -> ZombiesConfig.VIE_COSTAUD.get();
            case "coureur" -> ZombiesConfig.VIE_COUREUR.get();
            default -> ZombiesConfig.VIE_MARCHEUR.get();
        };
        double vitesse = switch (v) {
            case "costaud" -> ZombiesConfig.VITESSE_COSTAUD.get();
            case "coureur" -> ZombiesConfig.VITESSE_COUREUR.get();
            default -> ZombiesConfig.VITESSE_MARCHEUR.get();
        };
        base(z.getAttribute(Attributes.MAX_HEALTH), vie);
        base(z.getAttribute(Attributes.MOVEMENT_SPEED), vitesse);
        if (v.equals("costaud")) {
            AttributeInstance degats = z.getAttribute(Attributes.ATTACK_DAMAGE);
            if (degats != null) degats.setBaseValue(degats.getBaseValue() + ZombiesConfig.DEGATS_COSTAUD.get());
            base(z.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 0.6);
        }
        z.setHealth(z.getMaxHealth());
    }

    private static void base(AttributeInstance a, double v) {
        if (a != null) a.setBaseValue(v);
    }
}
