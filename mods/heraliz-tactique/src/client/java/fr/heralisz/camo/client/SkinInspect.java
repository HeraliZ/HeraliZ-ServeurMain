package fr.heralisz.camo.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.fx.V;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspections propres à un skin : quand on inspecte (touche de TaCZ) une arme portant un skin qui a sa propre animation pour ce
 * modèle d'arme, TaCZ joue cette animation à la place de la sienne (AnimationControllerMixin), avec tout son système : mains,
 * caméra, fondus, sons de manipulation. Les animations sont au format Bedrock de TaCZ :
 * assets/heralisz_camo/inspects/&lt;skin&gt;/&lt;espace&gt;/&lt;arme&gt;.animation.json (générées par tools/skin_inspects.py) ; un
 * champ « heralisz_cues » (ignoré par TaCZ) y liste les sons de la scène : [temps, son, volume, hauteur].
 * <p>Ici : le temps de la scène (lu sur l'animation en cours, donc toujours synchrone et interrompu en même temps qu'elle), les
 * sons de la scène, la main gauche (position lue au rendu) pour les effets.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class SkinInspect {
   private static final Logger LOG = LogUtils.getLogger();
   public static final String PREFIX = "heralisz_inspect_";
   /** Banc d'essai (CamoHarness) : trace des animations lancées par TaCZ. */
   private static final boolean DEBUG = System.getProperty("heralisz.camo.harness") != null;

   /** Scène chargée : l'animation et ses sons. */
   private record Scene(ObjectAnimation anim, List<Cue> cues) {
   }

   private record Cue(float t, ResourceLocation sound, float vol, float pitch) {
   }

   private static final Map<String, Scene> CACHE = new HashMap<>();
   private static final Scene NONE = new Scene(null, List.of());
   private static Scene scene;
   private static Camo camo;
   private static String playing;
   private static AnimationController controller;
   private static float length;
   private static int cueStep;
   private static float lastT;
   /** Paume de la main gauche (repère caméra), lue au rendu de la main par TaCZ ; utilisée par les effets. */
   public static volatile V handPos;
   /** Poignet gauche (même repère), pour ce qui s'enroule autour du bras. */
   public static volatile V wristPos;
   /** Abscisse de la main gauche à l'écran (0 = bord gauche, 1 = bord droit), -1 si inconnue. */
   public static volatile float handScreenX = -1;

   private SkinInspect() {
   }

   /** L'arme a-t-elle une scène d'inspection propre à son skin (pour ce modèle d'arme) ? */
   public static boolean hasScene(ItemStack held) {
      IGun iGun = IGun.getIGunOrNull(held);
      if (iGun == null) return false;
      Camo c = CamoData.get(held);
      return c != null && load(c, iGun.getGunId(held)).anim() != null;
   }

   /**
    * Appelé quand TaCZ lance une animation : une inspection de l'arme tenue, avec un skin qui a la sienne pour ce modèle, est
    * remplacée par celle du skin.
    */
   public static String redirect(AnimationController ctl, String name) {
      if (DEBUG) LOG.info("[camo-inspect] runAnimation {} (tick {})", name, Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : -1);
      if (name == null || !name.startsWith("inspect")) return name;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return name;
      ItemStack held = mc.player.getMainHandItem();
      IGun iGun = IGun.getIGunOrNull(held);
      if (iGun == null) return name;
      Camo c = CamoData.get(held);
      if (c == null) return name;
      boolean mine = TimelessAPI.getGunDisplay(held).map(d -> d.getAnimationStateMachine() != null && d.getAnimationStateMachine().getAnimationController() == ctl)
         .orElse(false);
      if (!mine) return name;
      Scene sc = load(c, iGun.getGunId(held));
      if (sc.anim() == null) return name;
      String our = PREFIX + c.id;
      ctl.providePrototypeIfAbsent(our, sc::anim);
      scene = sc;
      camo = c;
      playing = our;
      controller = ctl;
      length = sc.anim().getMaxEndTimeS();
      cueStep = 0;
      lastT = 0;
      handPos = null;
      wristPos = null;
      handScreenX = -1;
      return our;
   }

   private static Scene load(Camo c, ResourceLocation gun) {
      String key = c.id + "|" + gun;
      return CACHE.computeIfAbsent(key, k -> {
         ResourceLocation rl = new ResourceLocation(HeraliszCamo.MODID, "inspects/" + c.id + "/" + gun.getNamespace() + "/" + gun.getPath() + ".animation.json");
         java.util.Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
         if (res.isEmpty()) return NONE;
         try (Reader r = res.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            BedrockAnimationFile file = ClientAssetsManager.GSON.fromJson(json, BedrockAnimationFile.class);
            // l'animation porte déjà son nom de scène (heralisz_inspect_<skin>) : c'est la clé dans le contrôleur de TaCZ
            ObjectAnimation anim = Animations.createAnimationFromBedrock(file).stream().filter(a -> a.name.equals(PREFIX + c.id)).findFirst().orElse(null);
            List<Cue> cues = new ArrayList<>();
            if (json.has("heralisz_cues")) {
               for (JsonElement e : json.getAsJsonArray("heralisz_cues")) {
                  JsonArray a = e.getAsJsonArray();
                  cues.add(new Cue(a.get(0).getAsFloat(), new ResourceLocation(a.get(1).getAsString()), a.size() > 2 ? a.get(2).getAsFloat() : 1,
                     a.size() > 3 ? a.get(3).getAsFloat() : 1));
               }
               cues.sort((x, y) -> Float.compare(x.t(), y.t()));
            }
            return new Scene(anim, cues);
         } catch (Exception e) {
            LOG.warn("Inspection du skin {} pour {} illisible", c.id, gun, e);
            return NONE;
         }
      });
   }

   /** Temps de la scène (s), lu sur l'animation en cours ; -1 si elle ne joue pas (ou plus). */
   public static float time() {
      if (playing == null || controller == null) return -1;
      for (int track = 0; track < 16; track++) {
         ObjectAnimationRunner r = controller.getAnimation(track);
         if (r == null) continue;
         ObjectAnimationRunner cur = r.isTransitioning() && r.getTransitionTo() != null ? r.getTransitionTo() : r;
         if (cur.getAnimation() != null && playing.equals(cur.getAnimation().name)) {
            float t = cur.getProgressNs() / 1e9F;
            return t <= length ? t : -1;
         }
      }
      return -1;
   }

   /** Une scène se joue-t-elle (effet montré en entier) ? */
   public static boolean showcase() {
      return time() >= 0;
   }

   /** Skin de la scène en cours, null s'il n'y en a pas. */
   public static Camo playingSkin() {
      return showcase() ? camo : null;
   }

   public static float progress() {
      float t = time();
      return t < 0 || length <= 0 ? 1 : Math.min(1, t / length);
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END || playing == null) return;
      float t = time();
      if (t < 0) {
         playing = null;
         handPos = null;
         return;
      }
      if (t < lastT - 0.05F) cueStep = 0;   // relancée
      if (DEBUG && (int) (t * 20) / 10 != (int) (lastT * 20) / 10) LOG.info("[camo-inspect] {} t={}", playing, t);
      lastT = t;
      List<Cue> cues = scene.cues();
      Minecraft mc = Minecraft.getInstance();
      while (cueStep < cues.size() && t >= cues.get(cueStep).t()) {
         Cue q = cues.get(cueStep++);
         SoundEvent s = BuiltInRegistries.SOUND_EVENT.get(q.sound());
         if (s != null) mc.getSoundManager().play(SimpleSoundInstance.forUI(s, q.pitch(), q.vol()));
      }
   }
}
