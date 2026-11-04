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
        if (!visible(p, corps)) {
            p.displayClientMessage(Component.translatableWithFallback("message.heraliz_corps.cache",
                    "Tu ne vois pas ce corps d'ici."), true);
            return;
        }
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

    /**
     * 04/11 (retour du propriétaire : on fouillait les corps à travers les murs) : une partie du corps doit être visible des
     * yeux du joueur, sans bloc entre les deux (mur, porte fermée, vitre, plancher). Au plus 6 rayons, seulement à
     * l'ouverture : le milieu du dos, puis les 4 coins du dessus un peu rentrés, puis le milieu du corps.
     */
    static boolean visible(ServerPlayer p, CorpsEntityServeur corps) {
        net.minecraft.world.phys.Vec3 oeil = p.getEyePosition();
        net.minecraft.world.phys.AABB b = corps.getBoundingBox().deflate(0.1, 0.02, 0.1);
        double haut = b.maxY;
        net.minecraft.world.phys.Vec3[] points = {
                new net.minecraft.world.phys.Vec3((b.minX + b.maxX) / 2, haut, (b.minZ + b.maxZ) / 2),
                new net.minecraft.world.phys.Vec3(b.minX, haut, b.minZ), new net.minecraft.world.phys.Vec3(b.maxX, haut, b.minZ),
                new net.minecraft.world.phys.Vec3(b.minX, haut, b.maxZ), new net.minecraft.world.phys.Vec3(b.maxX, haut, b.maxZ),
                b.getCenter()};
        for (net.minecraft.world.phys.Vec3 v : points) {
            if (corps.level().clip(new net.minecraft.world.level.ClipContext(oeil, v, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }
}
