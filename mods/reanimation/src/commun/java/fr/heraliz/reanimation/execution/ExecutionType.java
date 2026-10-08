package fr.heraliz.reanimation.execution;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public enum ExecutionType {
   COUP_DE_GRACE("coup_de_grace", 0.32F, 0.7F, false, null, null, "jugement"),
   KARAMBIT("karambit", 1.0F, 0.45F, true, id -> id.contains("karambit"), "heralisz_skins:karambit__doppler_ruby", "griffe", "tigre", "danse"),
   SEA_GLEAM("sea_gleam", 0.85F, -0.05F, true, id -> id.contains("sea_gleam"), "melee:heralisz_melee:sea_gleam", "couperet", "decapitation"),
   BUTTERFLY("butterfly", 0.85F, 0.5F, true, id -> id.contains("butterfly"), "heralisz_skins:butterfly__fade", "papillon", "balisong"),
   M9("m9", 0.85F, 0.15F, true, id -> id.equals("m9") || id.startsWith("m9_"), "heralisz_skins:m9__lore", "bayonet", "baionnette", "coeur", "lancer"),
   HOME_RUN("home_run", 1.7F, 0.1F, true, id -> id.contains("baseball_bat"), "melee:heralisz_melee:baseball_bat", "baseball", "batte"),
   FRAPPE_ORBITALE("frappe_orbitale", 1.2F, 3.5F, 0.0F, 0.0F, 0, "orbitale", "laser"),
   CHUTE_METEORE("chute_meteore", 0.75F, 0.5F, 0.0F, 180.0F, 11, "meteore", "meteor"),
   COMBO_AERIEN("combo_aerien", 0.75F, 0.9F, 0.0F, 180.0F, 11, "combo", "aerien"),
   ARRACHE_AME("arrache_ame", 0.75F, 0.6F, 0.0F, 180.0F, 0, "ame", "soul"),
   MILLE_COUPES("mille_coupes", 0.95F, -1.0F, 0.0F, 180.0F, 2, "katana", "coupes"),
   SINGULARITE("singularite", 1.6F, 1.6F, 0.0F, 180.0F, 3, "trou_noir", "blackhole"),
   COURROUX_CIEL("courroux_ciel", 1.4F, 1.4F, 0.0F, 180.0F, 3, "foudre", "thunder"),
   SOUFFLE_DRAGON("souffle_dragon", 1.6F, 1.5F, 0.0F, 180.0F, 3, "dragon", "feu"),
   ZERO_ABSOLU("zero_absolu", 1.3F, 1.0F, 0.0F, 180.0F, 3, "glace", "ice"),
   DANSE_OMBRES("danse_ombres", 1.2F, 1.1F, 0.0F, 180.0F, 4, "ombres", "clones"),
   MAIN_JOKER("main_joker", 1.8F, 1.8F, 0.0F, 180.0F, 2, "joker", "cartes"),
   FAILLE("faille", 1.4F, 1.4F, 0.0F, 180.0F, 6, "portail", "portal"),
   ONDE_CELESTE("onde_celeste", 2.4F, 2.4F, 0.0F, 180.0F, 2, "kamehameha", "onde"),
   APESANTEUR("apesanteur", 2.6F, 2.6F, 0.0F, 180.0F, 4, "gravite", "gravity"),
   MARIONNETTISTE("marionnettiste", 1.6F, 1.9F, 0.0F, 180.0F, 3, "marionnette", "fils"),
   FREESTYLE_COMBO("freestyle_combo", 0.9F, 0.3F, 0.0F, 180.0F, 3, "freestyle", "boxe"),
   TEMPETE_SAKURA("tempete_sakura", 1.3F, 1.4F, 0.0F, 180.0F, 3, "sakura", "petales"),
   TAG_FINAL("tag_final", 1.5F, 2.4F, 0.0F, 180.0F, 3, "graffiti", "tag"),
   JUGEMENT_RUNIQUE("jugement_runique", 1.6F, 1.8F, 0.0F, 180.0F, 6, "runes", "epee"),
   CADEAU_SURPRISE("cadeau_surprise", 1.4F, 1.6F, 0.0F, 180.0F, 3, "cadeau", "gift"),
   SUCRE_D_ORGE("sucre_d_orge", 1.0F, 1.8F, 0.0F, 180.0F, 3, "sucre", "candy"),
   BONHOMME_DE_NEIGE("bonhomme_de_neige", 1.6F, 1.0F, 0.0F, 180.0F, 3, "bonhomme", "neige"),
   LAMES_FANTOMES("lames_fantomes", 0.95F, -1.0F, 0.0F, 180.0F, 2, true, ExecutionType::isKnife, "heralisz_skins:karambit__doppler_ruby", "fantome", "lames"),
   PANTIN("pantin", 1.9F, 1.9F, 0.0F, 180.0F, 6, true, ExecutionType::isKnife, "heralisz_skins:m9__lore", "marionnette_couteau");

   public static final ExecutionType[] KNIFE_VARIANTS = new ExecutionType[]{LAMES_FANTOMES, PANTIN};
   public static final int DURATION = 118;
   public static final int COMMIT = 58;
   public static final int STRIKE = 96;
   public final String id;
   public final int duration = 118;
   public final int commit = 58;
   public final int strike = 96;
   public final float reach;
   public final float endFwd;
   public final float endSide;
   public final float endYaw;
   public final int headroom;
   public final boolean headOnPlusSide;
   @Nullable
   public final Predicate<String> weapon;
   @Nullable
   public final String defaultWeapon;
   private final String[] aliases;

   ExecutionType(
      String id, float endFwd, float endSide, boolean headOnPlusSide, @Nullable Predicate<String> weapon, @Nullable String defaultWeapon, String... aliases
   ) {
      this(id, 0.75F, endFwd, endSide, 180.0F, 0, headOnPlusSide, weapon, defaultWeapon, aliases);
   }

   ExecutionType(String id, float reach, float endFwd, float endSide, float endYaw, int headroom, String... aliases) {
      this(id, reach, endFwd, endSide, endYaw, headroom, false, null, null, aliases);
   }

   ExecutionType(
      String id,
      float reach,
      float endFwd,
      float endSide,
      float endYaw,
      int headroom,
      boolean headOnPlusSide,
      @Nullable Predicate<String> weapon,
      @Nullable String defaultWeapon,
      String... aliases
   ) {
      this.id = id;
      this.reach = reach;
      this.endFwd = endFwd;
      this.endSide = endSide;
      this.endYaw = endYaw;
      this.headroom = headroom;
      this.headOnPlusSide = headOnPlusSide;
      this.weapon = weapon;
      this.defaultWeapon = defaultWeapon;
      this.aliases = aliases;
   }

   public boolean selectable() {
      return this.weapon == null;
   }

   public ExecutionType.Group group() {
      return switch (this) {
         case CADEAU_SURPRISE, SUCRE_D_ORGE, BONHOMME_DE_NEIGE -> ExecutionType.Group.NOEL;
         default -> this.selectable() && this.ordinal() >= MILLE_COUPES.ordinal() ? ExecutionType.Group.FINISHER : ExecutionType.Group.CLASSIC;
      };
   }

   public boolean knife() {
      return this == KARAMBIT || this == BUTTERFLY || this == M9;
   }

   public boolean variant() {
      return this == LAMES_FANTOMES || this == PANTIN;
   }

   private static boolean isKnife(String id) {
      return id.contains("karambit") || id.contains("butterfly") || id.equals("m9") || id.startsWith("m9_");
   }

   public Component description() {
      return Component.translatable("execution.reanimation." + this.id + ".desc");
   }

   public boolean matches(String name) {
      if (this.id.equals(name)) {
         return true;
      }

      for (String alias : this.aliases) {
         if (alias.equals(name)) {
            return true;
         }
      }

      return false;
   }

   public Component displayName() {
      return Component.translatable("execution.reanimation." + this.id);
   }

   @Nullable
   public static ExecutionType byId(String id) {
      for (ExecutionType t : values()) {
         if (t.matches(id)) {
            return t;
         }
      }

      return null;
   }

   public static ExecutionType byIndex(int index) {
      ExecutionType[] all = values();
      return all[Math.floorMod(index, all.length)];
   }

   public int sceneEnd() {
      return 118 + 18;
   }

   public int sceneEndStruck() {
      return 118 + 98;
   }

   public enum Group {
      CLASSIC,
      FINISHER,
      NOEL;
   }
}
