package fr.heralisz.camo.decor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Personnalisation d'une arme (NBT « HeraliszDecor », suit l'arme comme le camo) : stickers collés (5 au plus), un pendentif,
 * et le compteur StatTrack (le nombre de kills est dans CamoData.KILLS). Chaque élément est posé sur une pièce du modèle (os)
 * avec son point, sa normale et son « haut », dans le repère de la pièce (pixels) : il suit ainsi les animations (un sticker sur
 * le chargeur part avec lui au rechargement).
 */
public final class DecorData {
   public static final String KEY = "HeraliszDecor";
   public static final int MAX_STICKERS = 5;
   /** Compteur StatTrack : valeur affichée au plus. */
   public static final int MAX_SHOWN = 10000;

   /** Pose d'un élément sur une pièce de l'arme : point, normale (vers l'extérieur), haut, échelle. */
   public record Placement(String bone, float px, float py, float pz, float nx, float ny, float nz, float ux, float uy, float uz, float scale,
                           float rot) {
      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putString("B", this.bone);
         t.putFloat("px", this.px);
         t.putFloat("py", this.py);
         t.putFloat("pz", this.pz);
         t.putFloat("nx", this.nx);
         t.putFloat("ny", this.ny);
         t.putFloat("nz", this.nz);
         t.putFloat("ux", this.ux);
         t.putFloat("uy", this.uy);
         t.putFloat("uz", this.uz);
         t.putFloat("S", this.scale);
         t.putFloat("R", this.rot);
         return t;
      }

      static Placement load(CompoundTag t) {
         return new Placement(t.getString("B"), t.getFloat("px"), t.getFloat("py"), t.getFloat("pz"), t.getFloat("nx"), t.getFloat("ny"), t.getFloat("nz"),
            t.getFloat("ux"), t.getFloat("uy"), t.getFloat("uz"), t.contains("S") ? t.getFloat("S") : 1, t.getFloat("R"));
      }

      /** Valeurs venues du client : bornées, finies (sinon null). */
      @Nullable
      public Placement sanitized() {
         float[] v = {this.px, this.py, this.pz, this.nx, this.ny, this.nz, this.ux, this.uy, this.uz, this.scale, this.rot};
         for (float f : v) {
            if (!Float.isFinite(f)) return null;
         }
         if (this.bone == null || this.bone.isEmpty() || this.bone.length() > 64) return null;
         float nl = (float) Math.sqrt(this.nx * this.nx + this.ny * this.ny + this.nz * this.nz);
         float ul = (float) Math.sqrt(this.ux * this.ux + this.uy * this.uy + this.uz * this.uz);
         if (nl < 1e-3F || ul < 1e-3F) return null;
         return new Placement(this.bone, Mth.clamp(this.px, -96, 96), Mth.clamp(this.py, -96, 96), Mth.clamp(this.pz, -96, 96),
            this.nx / nl, this.ny / nl, this.nz / nl, this.ux / ul, this.uy / ul, this.uz / ul, Mth.clamp(this.scale, 0.2F, 2.5F),
            Mth.wrapDegrees(this.rot));
      }
   }

   public record Sticker(String id, int finish, Placement at) {
   }

   public record Charm(String id, Placement at) {
   }

   private DecorData() {
   }

   @Nullable
   private static CompoundTag tag(ItemStack gun) {
      CompoundTag t = gun.getTag();
      return t != null && t.contains(KEY, Tag.TAG_COMPOUND) ? t.getCompound(KEY) : null;
   }

   private static CompoundTag tagOrCreate(ItemStack gun) {
      CompoundTag root = gun.getOrCreateTag();
      if (!root.contains(KEY, Tag.TAG_COMPOUND)) root.put(KEY, new CompoundTag());
      return root.getCompound(KEY);
   }

   /** L'arme a-t-elle au moins un élément de personnalisation ? */
   public static boolean any(ItemStack gun) {
      CompoundTag t = tag(gun);
      return t != null && !t.isEmpty();
   }

   public static List<Sticker> stickers(ItemStack gun) {
      List<Sticker> out = new ArrayList<>();
      CompoundTag t = tag(gun);
      if (t == null) return out;
      ListTag list = t.getList("Stickers", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && i < MAX_STICKERS; i++) {
         CompoundTag s = list.getCompound(i);
         out.add(new Sticker(s.getString("Id"), s.getInt("F"), Placement.load(s.getCompound("At"))));
      }
      return out;
   }

   public static void setStickers(ItemStack gun, List<Sticker> stickers) {
      ListTag list = new ListTag();
      for (Sticker s : stickers) {
         CompoundTag c = new CompoundTag();
         c.putString("Id", s.id());
         c.putInt("F", s.finish());
         c.put("At", s.at().save());
         list.add(c);
      }
      tagOrCreate(gun).put("Stickers", list);
   }

   @Nullable
   public static Charm charm(ItemStack gun) {
      CompoundTag t = tag(gun);
      if (t == null || !t.contains("Charm", Tag.TAG_COMPOUND)) return null;
      CompoundTag c = t.getCompound("Charm");
      return new Charm(c.getString("Id"), Placement.load(c.getCompound("At")));
   }

   public static void setCharm(ItemStack gun, @Nullable Charm charm) {
      CompoundTag t = tagOrCreate(gun);
      if (charm == null) {
         t.remove("Charm");
         return;
      }
      CompoundTag c = new CompoundTag();
      c.putString("Id", charm.id());
      c.put("At", charm.at().save());
      t.put("Charm", c);
   }

   /** Pose du compteur StatTrack, null si l'arme n'en a pas. */
   @Nullable
   public static Placement statTrak(ItemStack gun) {
      CompoundTag t = tag(gun);
      return t != null && t.contains("StatTrack", Tag.TAG_COMPOUND) ? Placement.load(t.getCompound("StatTrack")) : null;
   }

   public static boolean hasStatTrak(ItemStack gun) {
      return statTrak(gun) != null;
   }

   public static void setStatTrak(ItemStack gun, Placement at) {
      tagOrCreate(gun).put("StatTrack", at.save());
   }
}
