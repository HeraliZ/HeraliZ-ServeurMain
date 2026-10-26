package fr.heraliz.ping.server;

import fr.heraliz.ping.PingType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Type d'un appui bref (ping « automatique »), deduit de la cible par le serveur : un objet au sol ou un contenant
 * (coffre, tonneau, caisse) = Loot ; un monstre ou un joueur hors du cercle = Ennemi ; sinon On y va.
 */
final class Contexte {
    private Contexte() {
    }

    static PingType resoudre(ServerLevel niveau, @Nullable Entity cible, long bloc, List<ServerPlayer> cercle) {
        if (cible != null) {
            if (cible instanceof ItemEntity) return Regles.typeAuto(Regles.Cible.OBJET_AU_SOL, false);
            if (cible instanceof Player j) return Regles.typeAuto(Regles.Cible.JOUEUR, cercle.contains(j));
            if (cible instanceof Enemy) return Regles.typeAuto(Regles.Cible.MONSTRE, false);
            return Regles.typeAuto(Regles.Cible.AUTRE_ENTITE, false);
        }
        BlockPos pos = BlockPos.of(bloc);
        boolean contenant = false;
        if (niveau.isLoaded(pos)) {
            BlockEntity be = niveau.getBlockEntity(pos);
            contenant = be instanceof Container || (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent());
        }
        return Regles.typeAuto(contenant ? Regles.Cible.CONTENANT : Regles.Cible.BLOC, false);
    }
}
