package fr.heralisz.n07.effect;

import net.minecraft.world.entity.LivingEntity;

/**
 * Partie SERVEUR de {@link IrradiatedEffect} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new IrradiatedEffect(...) ».
 */
public class IrradiatedEffectServeur extends IrradiatedEffect {
   public IrradiatedEffectServeur() {
      super();
   }


   @Override
   public void applyEffectTick(LivingEntity entity, int amplifier) {
      float d = 1.0F + amplifier * 0.5F;
      if (entity instanceof net.minecraft.server.level.ServerPlayer p) {
         // joueur fige dans une cinematique : rien ; combat pilote : le jeu peut remapper (ex. 1 PV par seconde au plus)
         if (fr.heralisz.n07.scene.CinematicGuard.isProtected(p)) return;
         fr.heralisz.n07.encounter.EncounterImpl enc = fr.heralisz.n07.encounter.EncounterManager.forPlayer(p);
         if (enc != null) d = enc.remapEffect(p, "irradiated", d);
         if (d <= 0) return;
      }
      entity.hurt(entity.damageSources().magic(), d);
   }
}
