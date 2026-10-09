package fr.heralisz.camo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Le camo est une simple étiquette NBT sur l'arme. Posé depuis un objet camo, il est définitif et suit l'arme (ramassée,
 * échangée, lootée). Posé depuis la collection à vie d'un joueur, il porte aussi son propriétaire et un jeton de pose : il
 * ne vaut que sur UNE arme de ce joueur à la fois, s'en va s'il meurt avec ou si l'arme passe à un autre joueur (règles
 * côté serveur : fr.heralisz.camo.serveur.CamosAVie).
 */
public final class CamoData {
   public static final String KEY = "HeraliszCamo";
   /** Camo à vie : joueur à qui il appartient. */
   public static final String OWNER = "HeraliszCamoProprio";
   /** Camo à vie : jeton de pose (le camo n'est valable que sur l'arme qui porte le jeton actuel de la collection). */
   public static final String TOKEN = "HeraliszCamoJeton";

   @Nullable
   public static Camo get(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      return tag != null && tag.contains(KEY) ? Camo.byId(tag.getString(KEY)) : null;
   }

   /** Camo définitif (posé depuis un objet camo) : remplace un éventuel camo à vie. */
   public static void set(ItemStack stack, Camo camo) {
      CompoundTag tag = stack.getOrCreateTag();
      tag.putString(KEY, camo.id);
      tag.remove(OWNER);
      tag.remove(TOKEN);
   }

   /** Camo de la collection à vie de {@code owner}, valable tant que la collection garde ce jeton. */
   public static void setLifetime(ItemStack stack, Camo camo, UUID owner, long token) {
      CompoundTag tag = stack.getOrCreateTag();
      tag.putString(KEY, camo.id);
      tag.putUUID(OWNER, owner);
      tag.putLong(TOKEN, token);
   }

   /** Propriétaire du camo à vie de l'arme ; null pour un camo définitif ou une arme sans camo. */
   @Nullable
   public static UUID owner(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      return tag != null && tag.contains(KEY) && tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
   }

   public static long token(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      return tag != null ? tag.getLong(TOKEN) : 0L;
   }

   /** Retire le camo de l'arme (camo à vie qui s'en va). */
   public static void clear(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      if (tag != null) {
         tag.remove(KEY);
         tag.remove(OWNER);
         tag.remove(TOKEN);
      }
   }

   /** Compteur de kills de l'arme (skins qui évoluent, compteur façon StatTrak). */
   public static final String KILLS = "HeraliszKills";

   public static int kills(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      return tag != null ? tag.getInt(KILLS) : 0;
   }

   public static void addKill(ItemStack stack) {
      CompoundTag tag = stack.getOrCreateTag();
      tag.putInt(KILLS, tag.getInt(KILLS) + 1);
   }

   private CamoData() {}
}
