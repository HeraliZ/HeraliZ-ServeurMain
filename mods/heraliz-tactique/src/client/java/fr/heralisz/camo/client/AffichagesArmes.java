package fr.heralisz.camo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import java.util.HashMap;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Affichage TaCZ d'une arme ({@code TimelessAPI.getGunDisplay}), demandé des dizaines de fois par image (rendus, animations
 * de chaque joueur, interface, nos camos) : à chaque appel TaCZ relit l'identifiant de l'arme et celui de son affichage
 * dans ses données et les analyse, et écrit une ligne dans le journal si l'affichage manque. Le résultat est gardé par
 * couple d'identifiants, sur le fil du jeu seulement, dans un monde seulement, et oublié à chaque tick et à chaque
 * changement de monde (rechargement des gunpacks compris).
 */
public final class AffichagesArmes {
   private static final HashMap<String, HashMap<String, Optional<GunDisplayInstance>>> GARDES = new HashMap<>();
   @Nullable
   private static ClientLevel monde;
   private static long tick = Long.MIN_VALUE;

   private AffichagesArmes() {
   }

   @Nullable
   public static Optional<GunDisplayInstance> lire(ItemStack stack) {
      CompoundTag tag = cle(stack);
      if (tag == null) {
         return null;
      }
      HashMap<String, Optional<GunDisplayInstance>> parAffichage = GARDES.get(tag.getString("GunId"));
      return parAffichage == null ? null : parAffichage.get(tag.getString("GunDisplayId"));
   }

   public static void garder(ItemStack stack, @Nullable Optional<GunDisplayInstance> affichage) {
      CompoundTag tag = affichage == null ? null : cle(stack);
      if (tag != null) {
         GARDES.computeIfAbsent(tag.getString("GunId"), k -> new HashMap<>()).put(tag.getString("GunDisplayId"), affichage);
      }
   }

   /** Données de l'arme si le résultat peut être gardé ; vide les gardes au changement de tick ou de monde. */
   @Nullable
   private static CompoundTag cle(ItemStack stack) {
      if (!(stack.getItem() instanceof IGun) || !RenderSystem.isOnRenderThread()) {
         return null;
      }
      CompoundTag tag = stack.getTag();
      ClientLevel level = Minecraft.getInstance().level;
      if (tag == null || level == null) {
         return null;
      }
      long t = level.getGameTime();
      if (level != monde || t != tick) {
         monde = level;
         tick = t;
         GARDES.clear();
      }
      return tag;
   }
}
