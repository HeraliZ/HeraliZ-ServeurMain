package fr.heralisz.n07.api.scene;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scene scriptee (donnees) : une suite de plans de camera relatifs a des reperes du monde, et des repliques horodatees
 * (titres, sous-titres, sons, fondus, secousses, decor qui bouge, particules) jouees cote client ; plus des actions serveur
 * horodatees (blocs, teleportations, crochets du jeu). Construite par {@link #builder(String)}, jouee par
 * {@code N07Api.playScene}.
 * <p>
 * Repere d'un plan : position + lacet (yaw Minecraft, 0 = +Z). Une image cle = {t (0..1 dans le plan), avant, droite,
 * hauteur de la camera, avant, droite, hauteur du point vise, champ de vision (0 = celui du joueur), roulis (degres)}.
 * « Avant » suit le lacet du repere, « droite » est a sa droite. Entre les cles : spline (Catmull-Rom), depart et arrivee
 * adoucis.
 */
public final class Scene {
   /** Garde-fou : 5 minutes au plus. */
   public static final int MAX_TICKS = 20 * 300;

   // types de repliques client
   public static final int TITLE = 0, SUBTITLE = 1, LABEL = 2, SOUND = 3, FADE = 4, FLASH = 5, SHAKE = 6, GHOST = 7, PARTICLES = 8;

   /** Repere du monde. */
   public record Frame(Vec3 pos, float yaw) {
      public Vec3 fwd() {
         return Vec3.directionFromRotation(0, yaw);
      }

      public Vec3 right() {
         Vec3 f = fwd();
         return new Vec3(-f.z, 0, f.x);
      }

      /** Point du monde a (avant, droite, hauteur) dans ce repere. */
      public Vec3 at(double f, double r, double h) {
         return pos.add(fwd().scale(f)).add(right().scale(r)).add(0, h, 0);
      }
   }

   /** Plan : repere, debut, duree, cles ; fondus au noir d'entree / sortie, bandes noires, raccord vers les yeux du joueur. */
   public static final class Shot {
      public final int frame, start, duration;
      public final List<float[]> keys = new ArrayList<>();
      public int fadeIn, fadeOut, blendIn;
      public boolean letterbox = true;
      /** La camera ne traverse pas les murs (tiree devant l'obstacle, comme les cinematiques du boss). */
      public boolean avoidWalls;
      /** Dernier plan : la camera glisse jusqu'aux yeux du joueur (les bandes s'ouvrent). */
      public boolean toPlayer;

      Shot(int frame, int start, int duration) {
         this.frame = frame;
         this.start = start;
         this.duration = duration;
      }
   }

   /** Replique client : instant, type, repere, nombres, textes. */
   public record Cue(int tick, int type, int frame, float[] f, String[] s) {}

   /** Action serveur : instant, action ; essential = rejouee si la scene est passee (etat des blocs, teleportation...). */
   public record ServerCue(int tick, Runnable action, boolean essential) {}

   public final String id;
   public final List<Frame> frames;
   public final List<Shot> shots;
   public final List<Cue> cues;
   public final List<ServerCue> serverCues;
   public final int length;
   /** Scene « habillage seul » : textes, sons, fondus par-dessus le jeu (ou une cinematique du boss), sans prendre la camera
    * ni figer le joueur, sans protection ni vote (ex. une replique radio en plein combat). */
   public final boolean overlayOnly;

   private Scene(String id, List<Frame> frames, List<Shot> shots, List<Cue> cues, List<ServerCue> serverCues, int length, boolean overlayOnly) {
      this.overlayOnly = overlayOnly;
      this.id = id;
      this.frames = Collections.unmodifiableList(frames);
      this.shots = Collections.unmodifiableList(shots);
      this.cues = Collections.unmodifiableList(cues);
      this.serverCues = Collections.unmodifiableList(serverCues);
      this.length = length;
   }

   public static Builder builder(String id) {
      return new Builder(id);
   }

   // ------------------------------------------------------------------ reseau (partie client seulement)
   public void write(FriendlyByteBuf b) {
      b.writeUtf(id);
      b.writeVarInt(length);
      b.writeByte((overlayOnly) ? 1 : 0);
      b.writeVarInt(frames.size());
      for (Frame f : frames) {
         b.writeDouble(f.pos.x);
         b.writeDouble(f.pos.y);
         b.writeDouble(f.pos.z);
         b.writeFloat(f.yaw);
      }
      b.writeVarInt(shots.size());
      for (Shot s : shots) {
         b.writeVarInt(s.frame);
         b.writeVarInt(s.start);
         b.writeVarInt(s.duration);
         b.writeVarInt(s.fadeIn);
         b.writeVarInt(s.fadeOut);
         b.writeVarInt(s.blendIn);
         b.writeByte((s.letterbox) ? 1 : 0);
         b.writeByte((s.toPlayer) ? 1 : 0);
         b.writeByte((s.avoidWalls) ? 1 : 0);
         b.writeVarInt(s.keys.size());
         for (float[] k : s.keys) for (float v : k) b.writeFloat(v);
      }
      b.writeVarInt(cues.size());
      for (Cue c : cues) {
         b.writeVarInt(c.tick);
         b.writeVarInt(c.type);
         b.writeVarInt(c.frame);
         b.writeVarInt(c.f.length);
         for (float v : c.f) b.writeFloat(v);
         b.writeVarInt(c.s.length);
         for (String v : c.s) b.writeUtf(v);
      }
   }

   public static Scene read(FriendlyByteBuf b) {
      String id = b.readUtf();
      int length = b.readVarInt();
      boolean overlay = (b.readByte() != 0);
      List<Frame> frames = new ArrayList<>();
      for (int i = b.readVarInt(); i > 0; i--) frames.add(new Frame(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat()));
      List<Shot> shots = new ArrayList<>();
      for (int i = b.readVarInt(); i > 0; i--) {
         Shot s = new Shot(b.readVarInt(), b.readVarInt(), b.readVarInt());
         s.fadeIn = b.readVarInt();
         s.fadeOut = b.readVarInt();
         s.blendIn = b.readVarInt();
         s.letterbox = (b.readByte() != 0);
         s.toPlayer = (b.readByte() != 0);
         s.avoidWalls = (b.readByte() != 0);
         for (int k = b.readVarInt(); k > 0; k--) {
            float[] key = new float[KEY_LEN];
            for (int j = 0; j < KEY_LEN; j++) key[j] = b.readFloat();
            s.keys.add(key);
         }
         shots.add(s);
      }
      List<Cue> cues = new ArrayList<>();
      for (int i = b.readVarInt(); i > 0; i--) {
         int tick = b.readVarInt(), type = b.readVarInt(), frame = b.readVarInt();
         float[] f = new float[b.readVarInt()];
         for (int j = 0; j < f.length; j++) f[j] = b.readFloat();
         String[] s = new String[b.readVarInt()];
         for (int j = 0; j < s.length; j++) s[j] = b.readUtf();
         cues.add(new Cue(tick, type, frame, f, s));
      }
      return new Scene(id, frames, shots, cues, new ArrayList<>(), length, overlay);
   }

   /** {t, avant, droite, hauteur, vise avant, vise droite, vise hauteur, fov, roulis}. */
   public static final int KEY_LEN = 9;

   // ------------------------------------------------------------------ construction
   public static final class Builder {
      private final String id;
      private final List<Frame> frames = new ArrayList<>();
      private final Map<String, Integer> names = new HashMap<>();
      private final List<Shot> shots = new ArrayList<>();
      private final List<Cue> cues = new ArrayList<>();
      private final List<ServerCue> server = new ArrayList<>();
      private Shot cur;
      private int cursor, overlayLength;
      private boolean overlay;

      Builder(String id) {
         this.id = id;
      }

      /** Habillage seul (pas de plan de camera) ; length = duree en ticks. */
      public Builder overlayOnly(int length) {
         overlay = true;
         overlayLength = length;
         return this;
      }

      /** Declare un repere nomme (position du monde, lacet). */
      public Builder frame(String name, Vec3 pos, float yaw) {
         names.put(name, frames.size());
         frames.add(new Frame(pos, yaw));
         return this;
      }

      private int frameIndex(String name) {
         Integer i = names.get(name);
         if (i == null) throw new IllegalArgumentException("repere inconnu : " + name);
         return i;
      }

      /** Ouvre un nouveau plan a la suite du precedent. */
      public Builder shot(String frame, int ticks) {
         cur = new Shot(frameIndex(frame), cursor, Math.max(1, ticks));
         shots.add(cur);
         cursor += cur.duration;
         return this;
      }

      /** Image cle du plan en cours : camera (avant, droite, hauteur), point vise (avant, droite, hauteur). */
      public Builder key(float t, float f, float r, float h, float lf, float lr, float lh) {
         cur.keys.add(new float[] {Mth.clamp(t, 0, 1), f, r, h, lf, lr, lh, 0, 0});
         return this;
      }

      /** Champ de vision de la derniere cle (degres ; 0 = celui du joueur). */
      public Builder fov(float fov) {
         cur.keys.get(cur.keys.size() - 1)[7] = fov;
         return this;
      }

      public Builder roll(float deg) {
         cur.keys.get(cur.keys.size() - 1)[8] = deg;
         return this;
      }

      public Builder fadeIn(int ticks) {
         cur.fadeIn = ticks;
         return this;
      }

      public Builder fadeOut(int ticks) {
         cur.fadeOut = ticks;
         return this;
      }

      /** Fondu enchaine depuis la camera du plan precedent (ticks). */
      public Builder blendIn(int ticks) {
         cur.blendIn = ticks;
         return this;
      }

      public Builder letterbox(boolean on) {
         cur.letterbox = on;
         return this;
      }

      /** La camera evite les murs du plan en cours (orbites autour d'un point libre, ex. la statue). */
      public Builder avoidWalls() {
         cur.avoidWalls = true;
         return this;
      }

      /** Le plan se termine dans les yeux du joueur (raccord vers le jeu). */
      public Builder toPlayer() {
         cur.toPlayer = true;
         return this;
      }

      /** Instant (ticks depuis le debut de la scene) du debut du plan en cours. */
      public int shotStart() {
         return cur == null ? 0 : cur.start;
      }

      public int length() {
         return cursor;
      }

      private Builder cue(int tick, int type, int frame, float[] f, String... s) {
         cues.add(new Cue(tick, type, frame, f, s));
         return this;
      }

      /** Carte de titre (cles de traduction) : bandeau, titre lettre par lettre, sous-titre. */
      public Builder title(int tick, int dur, String topKey, String titleKey, String subKey, int accent) {
         return cue(tick, TITLE, 0, new float[] {dur, accent}, topKey, titleKey, subKey);
      }

      /** Sous-titre (cles de traduction) : nom du locuteur (peut etre vide) et replique. */
      public Builder subtitle(int tick, int dur, String speakerKey, String textKey, int speakerColor) {
         return cue(tick, SUBTITLE, 0, new float[] {dur, speakerColor}, speakerKey, textKey);
      }

      /** Texte libre a l'ecran (cle de traduction), x / y en fraction d'ecran, ecrit a la machine (lettres par tick). */
      public Builder label(int tick, int dur, String key, float x, float y, float scale, float charsPerTick, int color) {
         return cue(tick, LABEL, 0, new float[] {dur, x, y, scale, charsPerTick, color}, key);
      }

      /** Son place dans le repere (avant, droite, hauteur). */
      public Builder sound(int tick, String soundId, float vol, float pitch, String frame, float f, float r, float h) {
         return cue(tick, SOUND, frameIndex(frame), new float[] {vol, pitch, f, r, h, 0}, soundId);
      }

      /** Son d'interface (non spatialise : radio, voix). */
      public Builder uiSound(int tick, String soundId, float vol, float pitch) {
         return cue(tick, SOUND, 0, new float[] {vol, pitch, 0, 0, 0, 1}, soundId);
      }

      /** Fondu au noir (ou couleur ARGB) : montee, maintien, descente (ticks). */
      public Builder fade(int tick, int in, int hold, int out, int argb) {
         return cue(tick, FADE, 0, new float[] {in, hold, out, argb});
      }

      public Builder flash(int tick, int dur, int rgb) {
         return cue(tick, FLASH, 0, new float[] {dur, rgb});
      }

      public Builder shake(int tick, float intensity, int dur) {
         return cue(tick, SHAKE, 0, new float[] {intensity, dur});
      }

      /**
       * Decor fantome : copie (cote client) des blocs de la boite [min, max] (coordonnees du monde), dessinee decalee de
       * from a to pendant dur ticks (ease : 0 lineaire, 1 adouci) ; ex. la cabine du monte-charge qui descend.
       */
      public Builder ghost(int tick, int dur, net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max, Vec3 from, Vec3 to, boolean ease) {
         return ghost(tick, 0, dur, min, max, from, to, ease);
      }

      /** Idem, immobile a from pendant hold ticks avant de partir. */
      public Builder ghost(int tick, int hold, int dur, net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max, Vec3 from, Vec3 to, boolean ease) {
         return cue(tick, GHOST, 0, new float[] {dur, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), (float) from.x,
            (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z, ease ? 1 : 0, hold});
      }

      /**
       * Particules client pendant dur ticks au point (avant, droite, hauteur) du repere, etalement (sx, sy, sz), n par tick,
       * vitesse ; followGhost &gt;= 0 : le point suit le decalage du fantome numero followGhost (ordre des appels ghost()).
       */
      public Builder particles(int tick, int dur, String particleId, String frame, float f, float r, float h, float sx, float sy, float sz, float perTick,
                               float speed, int followGhost) {
         return cue(tick, PARTICLES, frameIndex(frame), new float[] {dur, f, r, h, sx, sy, sz, perTick, speed, followGhost}, particleId);
      }

      /** Action serveur a l'instant donne ; essential : appliquee aussi si la scene est passee. */
      public Builder server(int tick, Runnable action, boolean essential) {
         server.add(new ServerCue(tick, action, essential));
         return this;
      }

      public Scene build() {
         if (overlay) {
            cues.sort((a, b) -> Integer.compare(a.tick, b.tick));
            server.sort((a, b) -> Integer.compare(a.tick, b.tick));
            return new Scene(id, frames, shots, cues, server, Math.min(MAX_TICKS, Math.max(1, overlayLength)), true);
         }
         if (shots.isEmpty()) throw new IllegalStateException("scene sans plan : " + id);
         for (Shot s : shots) {
            if (s.keys.isEmpty()) throw new IllegalStateException("plan sans cle dans " + id);
            if (s.keys.size() == 1) s.keys.add(new float[] {1, s.keys.get(0)[1], s.keys.get(0)[2], s.keys.get(0)[3], s.keys.get(0)[4], s.keys.get(0)[5],
               s.keys.get(0)[6], s.keys.get(0)[7], s.keys.get(0)[8]});
            s.keys.sort((a, b) -> Float.compare(a[0], b[0]));
         }
         cues.sort((a, b) -> Integer.compare(a.tick, b.tick));
         server.sort((a, b) -> Integer.compare(a.tick, b.tick));
         return new Scene(id, frames, shots, cues, server, Math.min(MAX_TICKS, cursor), false);
      }
   }
}
