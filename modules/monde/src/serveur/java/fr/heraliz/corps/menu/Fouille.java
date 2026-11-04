package fr.heraliz.corps.menu;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import fr.heraliz.corps.entite.CorpsEntityServeur;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/**
 * Ouverture de la fouille d'un corps (serveur) : regles d'acces, puis menu partage. Le joueur fouille de la ou il est,
 * comme un coffre : rien ne le deplace ; sont verifiees ici la distance (CorpsEntity.aPortee, des yeux jusqu'au corps) et,
 * depuis le 04/11, la vue (visible : pas de mur entre les deux), puis tant que la fouille reste ouverte
 * (CorpsMenu.stillValid : distance a chaque clic, vue toutes les 10 ticks).
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
     * yeux du joueur, sans bloc entre les deux (mur, porte fermée, vitre, plancher). Au plus 10 rayons, à l'ouverture puis
     * toutes les 10 ticks pendant la fouille (CorpsMenu.stillValid) : le milieu du dos, le milieu du corps, puis les 4 coins
     * du dessus un peu rentrés ; un coin ne compte que s'il est du même côté que le milieu du corps (sinon un corps couché en
     * travers d'une porte fermée se fouillait par son autre bout).
     */
    public static boolean visible(ServerPlayer p, fr.heraliz.corps.entite.CorpsEntity corps) {
        if (corps.level() != p.level()) return false;
        Vec3 oeil = p.getEyePosition();
        AABB b = corps.getBoundingBox().deflate(0.1, 0.02, 0.1);
        double haut = b.maxY;
        Vec3 milieu = b.getCenter();
        Vec3 dos = new Vec3(milieu.x, haut, milieu.z);
        if (vue(corps.level(), oeil, dos) || vue(corps.level(), oeil, milieu)) return true;
        Vec3[] coins = {new Vec3(b.minX, haut, b.minZ), new Vec3(b.maxX, haut, b.minZ), new Vec3(b.minX, haut, b.maxZ),
                new Vec3(b.maxX, haut, b.maxZ)};
        for (Vec3 c : coins) {
            if (vue(corps.level(), dos, c) && vue(corps.level(), oeil, c)) return true;
        }
        return false;
    }

    /**
     * Rien ne cache {@code a} vu de {@code de} : seuls comptent les blocs qui ont une collision (herbes, fleurs : on voit à
     * travers), avec leur forme visée (une barrière ou un muret arrête une balle à 1 bloc de haut, pas à 1,5 comme leur
     * collision : on fouille par-dessus comme on vise par-dessus).
     */
    private static boolean vue(Level level, Vec3 de, Vec3 a) {
        Boolean cache = BlockGetter.traverseBlocks(de, a, level, (lv, pos) -> {
            BlockState st = lv.getBlockState(pos);
            if (st.getCollisionShape(lv, pos).isEmpty()) return null;
            VoxelShape forme = st.getShape(lv, pos);
            return forme.clip(de, a, pos) != null ? Boolean.TRUE : null;
        }, lv -> Boolean.FALSE);
        return !Boolean.TRUE.equals(cache);
    }
}
