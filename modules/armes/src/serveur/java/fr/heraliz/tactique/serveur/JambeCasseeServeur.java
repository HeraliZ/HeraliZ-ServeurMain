package fr.heraliz.tactique.serveur;

import fr.heraliz.tactique.jambe.JambeCassee;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Jambe cassée sans perte des coups (28/10, retour du propriétaire : « avec la jambe cassée, on ne peut plus frapper ni
 * les zombies ni les joueurs »).
 * <p>
 * Cause : l'effet {@code geckolib:broken_bone} de geckolib222 (contenu ArkaniaZ, jar d'un autre auteur, jamais modifié)
 * pose toutes les secondes, en plus de Lenteur III, <b>Faiblesse II</b> pour 5 000 ticks, sans particules ni icône.
 * Faiblesse II retire 8 points d'attaque : une arme de mêlée GeckoLib (batte, machette, hache… 6 à 9 points) ne fait plus
 * que 0 à 1 point, à mains nues rien. Et l'attelle retire la jambe cassée et la lenteur, pas la faiblesse : on restait
 * sans coups jusqu'à 4 min après s'être soigné.
 * <p>
 * Ici, seule cette Faiblesse-là est refusée (reconnue à sa forme : niveau II, 5 000 ticks, ambiante, sans particules ; nos
 * autres faiblesses sont de niveau I) quand le joueur a la jambe cassée. La lenteur et les règles de saut
 * ({@link JambeCassee}) ne changent pas. À la connexion, une faiblesse de cette forme restée sur le joueur est retirée.
 */
public final class JambeCasseeServeur {
   private static final int DUREE_GECKOLIB = 5000;
   private static final int NIVEAU_GECKOLIB = 1;

   private JambeCasseeServeur() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(JambeCasseeServeur::applicable);
      MinecraftForge.EVENT_BUS.addListener(JambeCasseeServeur::connexion);
   }

   /** La faiblesse posée par l'effet de jambe cassée de geckolib222 ? */
   private static boolean faiblesseJambe(MobEffectInstance i) {
      return i != null && i.getEffect() == MobEffects.WEAKNESS && i.getAmplifier() == NIVEAU_GECKOLIB && i.isAmbient()
            && !i.isVisible() && i.getDuration() >= DUREE_GECKOLIB - 40;
   }

   private static void applicable(MobEffectEvent.Applicable e) {
      MobEffectInstance i = e.getEffectInstance();
      if (i.getEffect() == MobEffects.WEAKNESS && e.getEntity() instanceof ServerPlayer p && faiblesseJambe(i)
            && JambeCassee.cassee(p)) {
         e.setResult(Event.Result.DENY);
      }
   }

   /** Joueur sauvegardé avec l'ancienne faiblesse (avant ce correctif, ou soigné à l'attelle) : retirée. */
   private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) return;
      MobEffectInstance i = p.getEffect(MobEffects.WEAKNESS);
      if (i != null && i.getAmplifier() == NIVEAU_GECKOLIB && i.isAmbient() && !i.isVisible()) {
         p.removeEffect(MobEffects.WEAKNESS);
      }
   }
}
