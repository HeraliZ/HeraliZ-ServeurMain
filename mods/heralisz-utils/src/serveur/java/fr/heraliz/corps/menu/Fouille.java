package fr.heraliz.corps.menu;

import fr.heraliz.corps.entite.CorpsEntityServeur;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/**
 * Ouverture de la fouille d'un corps (serveur) : regles d'acces, puis menu partage. Le joueur fouille de la ou il est,
 * comme un coffre : rien ne le deplace ; seule la distance est verifiee ici (CorpsEntity.aPortee, des yeux jusqu'au
 * corps), puis a chaque clic tant que la fouille reste ouverte (CorpsMenu.stillValid).
 */
public final class Fouille {
    private Fouille() {
    }

    public static void ouvrir(ServerPlayer p, CorpsEntityServeur corps) {
        if (corps.isRemoved() || corps.contenu() == null || p.isSpectator() || !p.isAlive()) return;
        if (!corps.aPortee(p)) return;
        long reste = corps.protectionRestante(p);
        if (reste > 0) {
            p.displayClientMessage(Component.translatable("message.heraliz_corps.protege", (reste + 19) / 20), true);
            return;
        }
        if (p.containerMenu != p.inventoryMenu) p.closeContainer();
        Component titre = Component.translatable("gui.heraliz_corps.titre", corps.nom());
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, joueur) -> CorpsMenu.serveur(id, inv, corps), titre),
                b -> CorpsMenu.ecrire(b, corps));
        corps.level().playSound(null, corps.getX(), corps.getY(), corps.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.7F, 0.8F);
    }
}
