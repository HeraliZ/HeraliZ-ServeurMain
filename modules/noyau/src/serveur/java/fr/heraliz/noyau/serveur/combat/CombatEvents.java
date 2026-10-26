package fr.heraliz.noyau.serveur.combat;

import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.noyau.api.Matchs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Détection du combat entre joueurs (hors match) :
 * <ul>
 *    <li>{@code LivingDamageEvent} LOWEST (dégâts réellement appliqués) : tag des deux joueurs, voir {@link CombatService} ;</li>
 *    <li>{@code LivingHurtEvent} LOWEST, annulés compris : achèvement d'un joueur à terre avec l'ancien PlayerRevive ;</li>
 *    <li>{@code MobEffectEvent.Applicable} LOWEST : ancien tag d'ArkaniaZ {@code geckolib:combat_log} refusé partout, malus
 *    d'ArkaniaZ refusés aux joueurs en match.</li>
 * </ul>
 * Fil serveur.
 */
public final class CombatEvents {
   private CombatEvents() {}

   /** Effets d'ArkaniaZ (geckolib222) refusés en match. */
   static final Set<ResourceLocation> MALUS = Set.of(
         new ResourceLocation("geckolib", "bleeding"),
         new ResourceLocation("geckolib", "broken_bone"),
         new ResourceLocation("geckolib", "irradiated"),
         new ResourceLocation("geckolib", "parasitic_infection"),
         new ResourceLocation("geckolib", "bacterial_infection"),
         new ResourceLocation("geckolib", "food_poisoning"),
         CombatService.COMBAT_LOG);

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatEvents::tag);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, CombatEvents::achevement);
      // LOWEST : notre refus est le dernier mot (aucun autre mod ne peut le repasser à ALLOW après nous)
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatEvents::effet);
      CombatService.enregistrer();
   }

   /** Auteur réel (propriétaire du projectile ou de l'explosif) si c'est une entité vivante. */
   @Nullable
   public static LivingEntity auteur(DamageSource source) {
      Entity e = source.getEntity();
      return e instanceof LivingEntity l ? l : null;
   }

   /**
    * Tag de combat : dégâts réellement appliqués (LOWEST, non annulés, > 0) entre deux joueurs distincts, auteur réel de la
    * source (tireur d'une balle TaCZ, lanceur d'une grenade, propriétaire d'une explosion, porteur d'une arme de mêlée).
    * Jamais un mob, une chute, le feu ou le vide ; jamais en match ; ni un joueur en créatif ou en spectateur.
    */
   private static void tag(LivingDamageEvent e) {
      if (e.isCanceled() || e.getAmount() <= 0) return;
      if (!(e.getEntity() instanceof ServerPlayer victime)) return;
      if (!(auteur(e.getSource()) instanceof ServerPlayer attaquant) || attaquant == victime) return;
      if (Matchs.enMatch(victime) || Matchs.enMatch(attaquant)) return;
      if (horsJeu(attaquant) || horsJeu(victime)) return;
      CombatTag.marquer(attaquant, victime);
   }

   private static boolean horsJeu(ServerPlayer p) {
      return p.isCreative() || p.isSpectator();
   }

   /**
    * Achèvement d'un joueur à terre avec l'ancien PlayerRevive (il annule le coup et exécute la victime) : l'attaquant est
    * tagué comme pour un vrai coup. Avec Réanimation, le coup n'est pas annulé (il passe par {@link #tag}) et l'exécution
    * tague le bourreau à la mort ({@link CombatService}).
    */
   private static void achevement(LivingHurtEvent e) {
      if (!e.isCanceled() || !(e.getEntity() instanceof ServerPlayer victime)) return;
      if (!(auteur(e.getSource()) instanceof ServerPlayer attaquant) || attaquant == victime) return;
      if (Matchs.enMatch(victime) || Matchs.enMatch(attaquant) || horsJeu(attaquant) || !ATerre.aTerrePlayerRevive(victime)) return;
      CombatTag.marquer(attaquant);
   }

   private static void effet(MobEffectEvent.Applicable e) {
      LivingEntity l = e.getEntity();
      if (l.level().isClientSide) return;
      ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffectInstance().getEffect());
      if (id == null || !MALUS.contains(id)) return;
      if (CombatService.COMBAT_LOG.equals(id) || (l instanceof ServerPlayer p && Matchs.enMatch(p))) e.setResult(Event.Result.DENY);
   }
}
