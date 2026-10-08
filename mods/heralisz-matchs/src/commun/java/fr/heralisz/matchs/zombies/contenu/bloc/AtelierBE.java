package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Etabli de fabrication (case maitresse de {@link BlocAtelier}) : plateau de bois, etau, caisses, panneau d'outils,
 * plan bleu et lampe d'atelier (modele {@code atelier}). {@link #fabriquer()} marque une fabrication (la lampe vacille,
 * etincelles et son joues par le decor et le jeu). Aucune regle ici : les recettes et l'interface sont celles du jeu.
 */
public class AtelierBE extends ZbBE {
    private String recette = "";

    public AtelierBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.ATELIER_BE.get(), pos, state);
    }

    /** Serveur : un joueur vient de fabriquer {@code r} (animation de la lampe). */
    public void fabriquer(String r) {
        recette = r == null ? "" : r;
        marquer();
        synchroniser();
    }

    /** Serveur : remise a neuf (installation). */
    public void reinitialiser() {
        recette = "";
        depuis = -100000L;
        synchroniser();
    }

    public String recette() {
        return recette;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putString("Recette", recette);
    }

    @Override
    protected void lire(CompoundTag tag) {
        recette = tag.getString("Recette");
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 1.6, 0);
    }
}
