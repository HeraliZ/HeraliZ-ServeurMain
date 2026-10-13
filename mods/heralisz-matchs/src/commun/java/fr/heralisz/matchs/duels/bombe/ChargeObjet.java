package fr.heralisz.matchs.duels.bombe;

import fr.heralisz.matchs.pont.Crochet;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Objet {@code heralisz_matchs:duel_charge} (identifiant inchangé) : l'ordinateur d'amorçage fermé, que porte un
 * attaquant en recherche et destruction. Prêté par la partie (marque de match : jamais lâché, retiré à la fin), modèle
 * GeckoLib de l'ordinateur fermé en main (1re et 3e personne) ; aucun usage : l'amorçage se fait accroupi près d'une
 * bombe, que l'objet soit en main ou non.
 */
public class ChargeObjet extends Item implements GeoItem {
    /** Rendu en main / dans l'inventaire : branché par la partie client (duels/client/DuelsClient). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ChargeObjet() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
