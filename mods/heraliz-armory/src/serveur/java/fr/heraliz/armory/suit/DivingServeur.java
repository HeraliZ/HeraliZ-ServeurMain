package fr.heraliz.armory.suit;

import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.network.ArmoryNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.network.PacketDistributor;

/**
 * Bouteille d'air de la tenue de plongee, cote serveur (code d'origine de Diving.onTick, tel quel) : tant que la tete est
 * sous l'eau, la bouteille se vide et l'air du joueur reste plein ; hors de l'eau elle se recharge ; reserve gardee dans les
 * donnees du joueur et envoyee a son client toutes les 4 ticks (HUD). Appelee par Diving.onTick (crochet BOUTEILLE_SERVEUR).
 */
public final class DivingServeur {
    private DivingServeur() {}

    public static void bouteille(ServerPlayer sp) {
        int max = Diving.maxTicks();
        int tank = Diving.tank(sp);
        if (sp.isEyeInFluid(FluidTags.WATER)) {
            if (tank > 0) {
                tank--;
                sp.setAirSupply(sp.getMaxAirSupply());
            }
        } else if (tank < max) {
            int refill = ArmorConfig.REFILL_SECONDS.get();
            tank = refill <= 0 ? max : Math.min(max, tank + Math.max(1, max / (refill * 20)));
        }
        sp.getPersistentData().putInt(Diving.KEY, tank);
        if (sp.tickCount % 4 == 0) {
            ArmoryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ArmoryNetwork.TankSync(tank, max));
        }
    }
}
