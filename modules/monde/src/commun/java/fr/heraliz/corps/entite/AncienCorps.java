package fr.heraliz.corps.entite;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Convertisseur des corps de l'ancien mod « corpse » ({@code corpse:corpse}), enregistre seulement quand ce mod n'est plus
 * installe : au premier tick serveur, l'ancien corps devient un corps HeraliZ (meme place, memes objets, minuteur neuf)
 * puis disparait, dans le meme tick (jamais les deux a la fois dans une sauvegarde). Jamais envoye aux clients.
 * <p>Format lu (ancien mod) : {@code Death} = {@code MainInventory} (36), {@code ArmorInventory} (4),
 * {@code OffHandInventory} (1), listes {@code {Slot, objet}} ; {@code Items} = objets en plus (accessoires, sac) ;
 * {@code PlayerUuid}, {@code PlayerName}, {@code Timestamp}. La liste {@code Equipment} n'est qu'une copie pour
 * l'affichage : ignoree (sinon les objets seraient doubles).
 */
public class AncienCorps extends Entity {
    public AncienCorps(EntityType<? extends AncienCorps> type, Level level) {
        super(type, level);
    }

    /** La conversion (premier tick serveur) est dans AncienCorpsServeur ; neutre ici (jar sans partie serveur). */
    @Override
    public void tick() {
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer joueur) {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    /** Sauvegarde (liste « Death » de l'ancien mod) : AncienCorpsServeur. */
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
    }
}
