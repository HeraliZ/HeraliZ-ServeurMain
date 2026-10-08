package fr.heraliz.armory.suit;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.item.SuitItem;
import fr.heraliz.armory.pont.Crochet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tenue de plongee (0.9.0, reprise du mod heraliz_plongee du proprietaire, jamais installe) :
 * <ul>
 *   <li>bouteille d'air : tant que la tete est sous l'eau, la bouteille se vide (250 s par defaut) et l'air du joueur
 *   reste plein ; hors de l'eau elle se recharge (5 s pour un plein) ; reserve gardee dans les donnees du joueur et
 *   envoyee a son client (HUD au-dessus de la barre d'air) ;</li>
 *   <li>nage automatique en eau profonde (le client met le sprint quand on avance : DivingClient) ;</li>
 *   <li>palmes : nage +80 %, -6 % de vitesse a terre (attributs de SuitItem) ; animations (crawl, dauphin, surplace) :
 *   client/DivingPoses.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class Diving {
    private Diving() {}

    static final String KEY = "heraliz_armory.bouteille";

    /** Bouteille d'air (serveur) : branchee par la partie serveur (DivingServeur.bouteille), appelee a chaque tick du porteur. */
    public static final Crochet<ServerPlayer> BOUTEILLE_SERVEUR = new Crochet<>();

    /** L'entite porte la tenue de plongee. */
    public static boolean wears(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitItem s && s.def().diving();
    }

    public static int maxTicks() {
        return ArmorConfig.TANK_SECONDS.get() * 20;
    }

    /** Reserve de la bouteille en ticks (pleine si jamais utilisee). */
    public static int tank(Player player) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(KEY) ? Math.min(tag.getInt(KEY), maxTicks()) : maxTicks();
    }

    /** Eau profonde : le joueur est dans l'eau, tete dessous ou eau plus haute que 0,7 bloc. */
    public static boolean deepWater(Player player) {
        return player.isInWater() && (player.isUnderWater() || player.getFluidHeight(FluidTags.WATER) > 0.7);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (!wears(player)) return;
        if (!player.isSwimming() && player.isSprinting() && deepWater(player) && !player.isPassenger()) player.setSwimming(true);
        if (!(player instanceof ServerPlayer sp)) return;
        BOUTEILLE_SERVEUR.accept(sp);
    }
}
