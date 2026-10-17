package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.entite.Pnj;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** PNJ côté serveur : il regarde les joueurs qui passent, ouvre son dialogue au clic droit, suit son fichier (nom). */
public class PnjServeur extends Pnj {
    private int verif;

    public PnjServeur(EntityType<? extends Pnj> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 1.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player joueur, InteractionHand main) {
        if (main == InteractionHand.MAIN_HAND && joueur instanceof ServerPlayer sp) Dialogues.parler(sp, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (++verif % 200 != 0) return;
        Defs.Pnj def = Catalogue.pnj(pnjId());
        if (def != null && !def.nom.vide() && !def.nom.toString().equals(entityData.get(NOM))) definirNom(def.nom);
    }
}
