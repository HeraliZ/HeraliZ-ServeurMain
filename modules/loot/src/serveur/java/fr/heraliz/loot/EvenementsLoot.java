package fr.heraliz.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Ce qui se passe dans HeraliZ Loot, pour les autres mods (26/10, quêtes du jour de HeraliZ Quêtes, par réflexion) : ne
 * jamais renommer cette classe ni ses méthodes publiques. Fil serveur seulement.
 */
public final class EvenementsLoot {
    private EvenementsLoot() {
    }

    private static final List<BiConsumer<ServerPlayer, CompoundTag>> FOUILLES = new CopyOnWriteArrayList<>();

    /**
     * Appelé à la PREMIÈRE ouverture d'un conteneur rempli (rotation T1 à T6, familles, salles à carte) ou d'un coffre T7
     * (pas ceux d'un événement), par celui qui l'ouvre ; rouvrir ne rappelle rien. Données : {@code palier} (1 à 7, 0
     * sans palier), {@code pos} (BlockPos.asLong), {@code monde}, {@code salle} (verte / bleue / rouge, ou « »).
     */
    public static void ecouterFouille(BiConsumer<ServerPlayer, CompoundTag> f) {
        FOUILLES.add(f);
    }

    static void fouille(ServerPlayer p, ServerLevel l, BlockPos pos, int palier, String salle) {
        if (FOUILLES.isEmpty() || p.isCreative() || p.isSpectator()) return;
        CompoundTag t = new CompoundTag();
        t.putInt("palier", palier);
        t.putLong("pos", pos.asLong());
        t.putString("monde", l.dimension().location().toString());
        t.putString("salle", salle == null ? "" : salle);
        for (BiConsumer<ServerPlayer, CompoundTag> f : FOUILLES) {
            try {
                f.accept(p, t);
            } catch (RuntimeException ex) {
                HeralizLoot.LOG.error("Écouteur des fouilles en erreur", ex);
            }
        }
    }
}
