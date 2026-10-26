package fr.heraliz.usure;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Partie SERVEUR de {@link KitReparation} (docs/METHODE_CLIENT_SERVEUR.md §5.3) : clic droit avec le kit dans la main
 * principale = atelier de reparation ({@link MenuReparationServeur}, 26/10). Creee par l'usine Contenu.KIT_USINE des que la
 * partie serveur est dans le jar ; ne jamais faire « new KitReparation(...) ».
 */
public class KitReparationServeur extends KitReparation {
    public KitReparationServeur(int niveau, Rarity rarete) {
        super(niveau, rarete);
    }

    public KitReparationServeur(Params a) {
        super(a);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand main) {
        ItemStack kit = player.getItemInHand(main);
        if (main == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && !sp.isSpectator()) {
            int caseKit = sp.getInventory().selected;
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new MenuReparationServeur(id, inv, caseKit, niveau),
                    Component.translatableWithFallback("heraliz_usure.atelier.titre", "Réparation")), buf -> buf.writeVarInt(caseKit));
        }
        return InteractionResultHolder.sidedSuccess(kit, level.isClientSide);
    }
}
