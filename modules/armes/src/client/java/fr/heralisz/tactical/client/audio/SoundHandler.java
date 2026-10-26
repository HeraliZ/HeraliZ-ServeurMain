package fr.heralisz.tactical.client.audio;

import com.mojang.logging.LogUtils;
import com.tacz.guns.client.sound.GunSoundInstance;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import fr.heralisz.tactical.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * Assourdissement de la grenade flash (effet « deafened ») chez le joueur : sifflement, sons en cours baissés, nouveaux sons
 * joués plus bas tant que l'effet dure.
 * <p>
 * HeraliZ (2.12.0) : le jeu du joueur plantait juste après l'explosion d'une flash. Dès qu'il était assourdi, chaque
 * nouveau son passait par {@link #lowerInitialVolume}, qui lisait le son de l'événement sans vérifier qu'il existait
 * encore. Or un autre mod peut l'avoir annulé avant (PlaySoundEvent.setSound(null)) : HeraliZ Barre d'objets le fait, en
 * priorité haute, pour les bruits de MAtmos des autres joueurs à plus de 8 blocs (vent de course, changement d'objet),
 * et HeraliZ Emote pour la musique des emotes coupée. Le premier de ces sons après l'explosion faisait planter le jeu
 * (NullPointerException). Désormais : un son annulé est laissé tel quel ; le champ du moteur de son est cherché par son
 * type (et non plus seulement par son nom réobfusqué) et vérifié avant usage ; toute erreur imprévue coupe l'effet sonore
 * (une ligne dans le journal) au lieu de faire planter le jeu ; la fin de l'effet rend toujours leur volume aux sons.
 */
public class SoundHandler {
   private static final Logger LOG = LogUtils.getLogger();
   private static final ResourceLocation EXPLOSION = new ResourceLocation("minecraft", "entity.generic.explode");
   /** Son de la flash elle-même (heralisz_tactical ou heralisz_grenade) : jamais baissé. */
   private static final String EXPLOSION_FLASH = "entity.grenade.flash.explode";
   /** Sifflement qui n'a pas pu démarrer (volume général à 0, son introuvable) : nouvel essai au plus toutes les secondes. */
   private static final int ATTENTE_SIFFLEMENT = 20;
   private static SoundHandler instance;
   /** Volume d'origine des sons baissés, pour le leur rendre à la fin de l'effet. */
   private final Map<SoundInstance, Float> soundVolumes = new ConcurrentHashMap<>();
   private boolean isDeafened;
   /** Erreur imprévue : l'assourdissement par le son est coupé jusqu'au redémarrage du jeu (l'effet reste, le jeu aussi). */
   private boolean horsService;
   @Nullable
   private final Field playingSounds;
   private SoundEngine soundEngine;
   private StunRingingSound ringing;
   private int attenteSifflement;

   public static SoundHandler get() {
      if (instance == null) {
         instance = new SoundHandler();
      }

      return instance;
   }

   private SoundHandler() {
      this.playingSounds = trouverCanaux();
      if (this.playingSounds == null) {
         LOG.warn("[heralisz_tactical] Flash : sons en cours du moteur de son introuvables ; seuls les nouveaux sons seront assourdis");
      }
   }

   /**
    * Le champ « son en cours -> canal » du moteur de son (instanceToChannel de Minecraft). Cherché d'abord par son type
    * (Map&lt;SoundInstance, ChannelHandle&gt;, le seul de ce genre) : ne dépend d'aucun nom ; sinon par son nom réobfusqué
    * d'origine, accepté seulement si c'est bien une Map. Null si rien ne convient (les sons déjà en cours ne sont alors
    * pas baissés).
    */
   @Nullable
   private static Field trouverCanaux() {
      try {
         for (Field f : SoundEngine.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) {
               continue;
            }
            if (f.getGenericType() instanceof ParameterizedType type) {
               Type[] args = type.getActualTypeArguments();
               if (args.length == 2 && brut(args[0]) == SoundInstance.class && brut(args[1]) == ChannelHandle.class) {
                  f.setAccessible(true);
                  return f;
               }
            }
         }
      } catch (RuntimeException e) {
         // accès refusé ou types illisibles : on tente le nom d'origine
      }
      try {
         Field f = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
         return Map.class.isAssignableFrom(f.getType()) ? f : null;
      } catch (RuntimeException e) {
         return null;
      }
   }

   @Nullable
   private static Class<?> brut(Type type) {
      if (type instanceof Class<?> classe) {
         return classe;
      }
      return type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> brute ? brute : null;
   }

   @SubscribeEvent
   public void deafenPlayer(ClientTickEvent event) {
      if (event.phase == Phase.START || this.horsService) {
         return;
      }

      try {
         this.tickSourd();
      } catch (RuntimeException e) {
         this.mettreHorsService(e);
      }
   }

   private void tickSourd() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      MobEffectInstance effect = player == null ? null : player.getEffect((MobEffect)ModEffects.DEAFENED.get());
      if (effect == null) {
         // fin de l'effet (ou plus de joueur) : les sons baissés retrouvent leur volume, quoi qu'il soit arrivé au sifflement
         if (this.isDeafened) {
            this.isDeafened = false;
            this.restaurer();
         }

         return;
      }

      SoundManager sons = mc.getSoundManager();
      if (this.ringing == null || this.ringing.isStopped() || !sons.isActive(this.ringing) && --this.attenteSifflement <= 0) {
         this.ringing = new StunRingingSound();
         sons.play(this.ringing);
         this.attenteSifflement = ATTENTE_SIFFLEMENT;
      }

      this.isDeafened = true;
      Map<?, ?> enCours = this.sonsEnCours();
      if (enCours == null) {
         return;
      }

      int duree = effect.getDuration();
      // copie : le moteur ne touche à sa table que sur ce même fil, mais on ne parcourt jamais une table vivante
      for (Entry<?, ?> entree : new ArrayList<>(enCours.entrySet())) {
         if (entree.getKey() instanceof SoundInstance son && !(son instanceof TickableSoundInstance) && entree.getValue() instanceof ChannelHandle canal) {
            ResourceLocation loc = son.getLocation();
            if (loc != null && !estExplosion(loc)) {
               float base = son instanceof SoundHandler.SoundMuted muet ? muet.getVolumeInitial() : son.getVolume();
               this.soundVolumes.put(son, base);
               float gain = gain(son, getMutedVolume(duree, base));
               canal.execute(source -> source.setVolume(gain));
            }
         }
      }
   }

   /** Fin de l'effet : chaque son baissé encore en cours retrouve son volume. */
   private void restaurer() {
      Map<?, ?> enCours = this.sonsEnCours();
      if (enCours != null) {
         for (Entry<SoundInstance, Float> entree : this.soundVolumes.entrySet()) {
            if (enCours.get(entree.getKey()) instanceof ChannelHandle canal) {
               float gain = gain(entree.getKey(), entree.getValue());
               canal.execute(source -> source.setVolume(gain));
            }
         }
      }

      this.soundVolumes.clear();
   }

   /** La table des sons en cours du moteur de son, ou null (champ introuvable, moteur pas encore vu, autre type). */
   @Nullable
   private Map<?, ?> sonsEnCours() {
      if (this.playingSounds == null || this.soundEngine == null) {
         return null;
      }

      try {
         return this.playingSounds.get(this.soundEngine) instanceof Map<?, ?> table ? table : null;
      } catch (IllegalAccessException | RuntimeException e) {
         return null;
      }
   }

   /** Volume donné au canal, comme le calcule le moteur de son : volume du son x volume de sa catégorie, entre 0 et 1. */
   private static float gain(SoundInstance son, float volume) {
      SoundSource source = son.getSource();
      float categorie = source == null || source == SoundSource.MASTER ? 1.0F : Minecraft.getInstance().options.getSoundSourceVolume(source);
      return Mth.clamp(volume * categorie, 0.0F, 1.0F);
   }

   private static boolean estExplosion(ResourceLocation loc) {
      return EXPLOSION.equals(loc) || EXPLOSION_FLASH.equals(loc.getPath());
   }

   private void mettreHorsService(RuntimeException e) {
      if (!this.horsService) {
         LOG.error("[heralisz_tactical] Flash : assourdissement coupé jusqu'au redémarrage du jeu après une erreur imprévue", e);
      }

      this.horsService = true;
      if (this.isDeafened) {
         this.isDeafened = false;
         try {
            this.restaurer();
         } catch (RuntimeException ignoree) {
            this.soundVolumes.clear();
         }
      }
   }

   @SubscribeEvent
   public void lowerInitialVolume(PlaySoundEvent event) {
      if (this.soundEngine == null) {
         this.soundEngine = event.getEngine();
      }

      // son déjà annulé par un autre mod (setSound(null)) : il ne sera pas joué, rien à baisser
      SoundInstance son = event.getSound();
      if (!this.isDeafened || this.horsService || son == null) {
         return;
      }

      try {
         this.baisser(event, son);
      } catch (RuntimeException e) {
         this.mettreHorsService(e);
      }
   }

   private void baisser(PlaySoundEvent event, SoundInstance son) {
      if (son instanceof SoundHandler.SoundMuted || son instanceof TickableSoundInstance && !(son instanceof GunSoundInstance)) {
         return;
      }

      ResourceLocation loc = son.getLocation();
      if (loc == null || estExplosion(loc)) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      MobEffectInstance effect = player == null ? null : player.getEffect((MobEffect)ModEffects.DEAFENED.get());
      int duration = effect != null ? effect.getDuration() : 0;
      // son inconnu : le moteur ne le jouera pas (et son volume n'est pas lisible)
      if (duration != 0 && son.resolve(mc.getSoundManager()) != null) {
         event.setSound(new SoundHandler.SoundMuted(son, duration, false));
      }
   }

   private static float getMutedVolume(float duration, float volumeBase) {
      float volumeMin = 0.01F * volumeBase;
      float percent = Mth.clamp(duration / 100.0F, 0.0F, 1.0F);
      return volumeMin + (1.0F - percent) * (volumeBase - volumeMin);
   }

   public static class SoundMuted implements SoundInstance {
      private final SoundInstance parent;
      private final float volume;
      private float volumeInitial;

      public SoundMuted(SoundInstance parent, int duration, boolean isStunGrenade) {
         this.parent = parent;
         this.volumeInitial = Mth.clamp(parent.getVolume(), 0.0F, 1.0F);
         this.volume = SoundHandler.getMutedVolume(duration, this.volumeInitial);
         if (isStunGrenade) {
            this.volumeInitial = this.volume;
         }
      }

      public float getVolume() {
         return this.volume;
      }

      public float getVolumeInitial() {
         return this.volumeInitial;
      }

      public ResourceLocation getLocation() {
         return this.parent.getLocation();
      }

      @Nullable
      public WeighedSoundEvents resolve(SoundManager handler) {
         return this.parent.resolve(handler);
      }

      public Sound getSound() {
         return this.parent.getSound();
      }

      public SoundSource getSource() {
         return this.parent.getSource();
      }

      public boolean isLooping() {
         return this.parent.isLooping();
      }

      // HeraliZ : comme le son d'origine (avant : toujours faux, un son « collé au joueur » partait au point 0, 0, 0 du monde)
      public boolean isRelative() {
         return this.parent.isRelative();
      }

      // HeraliZ : un son que son auteur ne veut pas jouer (tireur silencieux de TaCZ...) ne l'est pas davantage une fois baissé
      public boolean canPlaySound() {
         return this.parent.canPlaySound();
      }

      public boolean canStartSilent() {
         return this.parent.canStartSilent();
      }

      public int getDelay() {
         return this.parent.getDelay();
      }

      public float getPitch() {
         return this.parent.getPitch();
      }

      public double getX() {
         return this.parent.getX();
      }

      public double getY() {
         return this.parent.getY();
      }

      public double getZ() {
         return this.parent.getZ();
      }

      public Attenuation getAttenuation() {
         return this.parent.getAttenuation();
      }
   }
}
