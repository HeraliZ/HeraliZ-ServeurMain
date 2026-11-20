package fr.heraliz.noyau.serveur.allege;

import net.minecraft.server.level.ChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.BitSet;

/**
 * Tronçons qui tournent « au repos » (20/11, {@link fr.heraliz.noyau.serveur.perf.TronconsRepos}) : ce qu'un support de
 * tronçon a encore à envoyer aux joueurs (blocs changés, lumière changée). Ce sont exactement les trois questions de
 * {@code ChunkHolder.broadcastChanges} : quand elles répondent toutes non, l'envoi ne fait rien. À part des autres accès :
 * un accès introuvable ici ne coupe que ce point (tous les tronçons sont alors parcourus, comme avant).
 */
@Mixin(ChunkHolder.class)
public interface ChangementsAcces {
   @Accessor("hasChangedSections")
   boolean heraliz$blocsChanges();

   @Accessor("skyChangedLightSectionFilter")
   BitSet heraliz$lumiereCiel();

   @Accessor("blockChangedLightSectionFilter")
   BitSet heraliz$lumiereBlocs();
}
