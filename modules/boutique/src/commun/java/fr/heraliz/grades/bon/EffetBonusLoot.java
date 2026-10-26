package fr.heraliz.grades.bon;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/**
 * Effet « Bonus de loot » (sans aucun effet de jeu) : il montre dans l'inventaire, comme une potion sans particules, le
 * bonus de loot du joueur (niveau = pourcentage - 1) et le temps qui reste. Posé et tenu à jour par le serveur d'après
 * {@code PlayerPersisted.HeralizBonusLoot} (le vrai bonus est appliqué par HeraliZ Loot).
 */
public final class EffetBonusLoot extends MobEffect {
    /** Affichage dans l'inventaire (« Bonus de loot +10 % »), posé par la partie client avant l'enregistrement. */
    public static volatile IClientMobEffectExtensions AFFICHAGE;

    public EffetBonusLoot() {
        super(MobEffectCategory.BENEFICIAL, 0xE8C36A);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        IClientMobEffectExtensions a = AFFICHAGE;
        if (a != null) consumer.accept(a);
    }
}
