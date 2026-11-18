package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heraliz.tactique.armes.DegatsJoueurs;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Balles des armes TaCZ contre les zombies (18/11, retour du propriétaire après un essai : « 4 tirs à la tête au sniper
 * légendaire et 2 à la tête au Dragunov pour tuer un zombie ; une tête au sniper doit tuer n'importe quel zombie, au corps
 * à corps ils doivent tanker, mais avec les armes il ne faut pas en mettre autant »). Trois causes :
 * <ul>
 * <li>les fusils .50 des gunpacks (M82A3, M95) font 0 dégât aux créatures : seuls les joueurs avaient leurs dégâts, réglés
 * par armes.json ({@link DegatsArmes}) ;</li>
 * <li>les gunpacks ne donnent que x1,2 à x1,75 pour un tir à la tête ;</li>
 * <li>l'armure du Zombie Brute des mutants (16 points) mange plus de la moitié d'une balle de fusil.</li>
 * </ul>
 * Sur chaque balle qui touche un zombie (tag {@link #ZOMBIES}), réglages de la section [zombies] de
 * {@code heralisz_tactical-server.toml} : dégâts de TaCZ (ou de armes.json pour une arme à 0) multipliés par
 * « multiplicateur » ; tête : au moins « tete » fois les dégâts ; tête au fusil de précision (type « sniper » de TaCZ) : de
 * quoi tuer ; armure du zombie compensée, pour les balles seulement. Le corps à corps ne passe pas par là.
 */
final class ZombiesArmes {
   /** Zombies pour les balles (données de ce module) : zombies HeraliZ et de Minecraft, mutants ; pas les boss. */
   static final TagKey<EntityType<?>> ZOMBIES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("heraliz_tactique", "zombies"));

   private ZombiesArmes() {
   }

   static boolean estZombie(@Nullable Entity e) {
      return e instanceof LivingEntity && e.getType().is(ZOMBIES);
   }

   static void touche(EntityHurtByGunEvent.Pre e, LivingEntity zombie) {
      if (!lire(ServerConfig.ZOMBIES_ACTIF, true)) return;
      ResourceLocation arme = e.getGunId();
      if (arme == null) return;
      CommonGunIndex index = TimelessAPI.getCommonGunIndex(arme).orElse(null);
      if (index == null) return;
      BulletData balle = index.getBulletData();
      float base = e.getBaseAmount();
      if (balle == null || balle.getDamageAmount() <= 0.01f) {
         // fusils .50 des gunpacks : 0 dégât aux créatures ; ils prennent leurs dégâts contre les joueurs (une balle)
         DegatsJoueurs.Reglage r = DegatsJoueurs.de(arme);
         if (r == null) return;
         base = r.degats();
      }
      base *= (float) lire(ServerConfig.ZOMBIES_MULTIPLICATEUR, 2.0);
      float mult = 1f;
      if (e.isHeadShot()) {
         mult = Math.max(e.getHeadshotMultiplier(), (float) lire(ServerConfig.ZOMBIES_TETE, 3.5));
         e.setHeadshotMultiplier(mult);
         if ("sniper".equals(index.getType()) && lire(ServerConfig.ZOMBIES_TETE_PRECISION, true)) {
            // de quoi tuer, même un Zombie Brute plein de vie (armure compensée juste après)
            base = Math.max(base, (zombie.getHealth() + zombie.getAbsorptionAmount() + 1f) / mult);
         }
      }
      if (lire(ServerConfig.ZOMBIES_ARMURE, true) && zombie.getArmorValue() > 0) {
         base *= compensation(base * mult, zombie, balle == null ? null : balle.getExtraDamage());
      }
      e.setBaseAmount(base);
   }

   /**
    * Facteur qui rend à une balle ce que l'armure du zombie lui prend. TaCZ partage le coup entre une part qui perce
    * l'armure (« armor_ignore » de l'arme) et une part freinée comme un coup de Minecraft ({@link CombatRules}) ; la baisse
    * dépend des dégâts, le facteur est donc cherché en trois pas (à 1 % près).
    */
   private static float compensation(float coup, LivingEntity zombie, @Nullable ExtraDamage extra) {
      float perce = extra == null ? 0f : Mth.clamp(extra.getArmorIgnore(), 0f, 1f);
      if (coup <= 0.01f || perce >= 0.999f) return 1f;
      float armure = zombie.getArmorValue();
      float durete = (float) zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
      float f = 1f;
      for (int i = 0; i < 3; i++) {
         float x = coup * f;
         float recu = x * perce + CombatRules.getDamageAfterAbsorb(x * (1f - perce), armure, durete);
         if (recu <= 0.01f) return 1f;
         f *= coup / recu;
      }
      return Mth.clamp(f, 1f, 10f);
   }

   private static boolean lire(@Nullable ForgeConfigSpec.BooleanValue valeur, boolean defaut) {
      try {
         return valeur == null ? defaut : valeur.get();
      } catch (IllegalStateException pasEncoreLu) {
         return defaut;
      }
   }

   private static double lire(@Nullable ForgeConfigSpec.DoubleValue valeur, double defaut) {
      try {
         return valeur == null ? defaut : valeur.get();
      } catch (IllegalStateException pasEncoreLu) {
         return defaut;
      }
   }
}
