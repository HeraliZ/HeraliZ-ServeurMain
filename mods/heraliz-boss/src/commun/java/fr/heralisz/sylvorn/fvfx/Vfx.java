package fr.heralisz.sylvorn.fvfx;

import fr.heraliz.boss.pont.Crochet;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Effets visuels du boss : le serveur décide (où, quand, quelle taille), les clients proches dessinent.
 * Un seul paquet générique : type + position + direction + 2 tailles + durée + couleur + paramètre libre.
 */
public final class Vfx {
   public enum Type {
      /** Cercle d'alerte au sol qui se remplit jusqu'à l'impact (a = rayon). */
      CIRCLE,
      /** Couloir d'alerte au sol (charge, fissure, rayon) : a = longueur, b = largeur. */
      LINE,
      /** Cône d'alerte au sol : a = portée, b = angle total (degrés). */
      CONE,
      /** Onde de choc : mur d'énergie annulaire qui s'élargit jusqu'à a. */
      SHOCKWAVE,
      /** Colonne d'énergie toxique (geyser, éruption) : a = rayon, b = hauteur. */
      COLUMN,
      /** Sphère d'explosion radioactive : a = rayon final. */
      NOVA,
      /** Brûlure toxique au sol qui s'estompe : a = rayon. */
      SCORCH,
      /** Traînée au sol d'une charge : a = longueur, b = largeur. */
      TRAIL,
      /** Spirale d'aspiration au sol : a = rayon. */
      VORTEX,
      /** Fissure lumineuse au sol : a = longueur, b = largeur. */
      CRACK,
      /** Arc de griffure : a = rayon, b = angle (degrés), extra = sens (1 / -1). */
      SLASH,
      /** Éclair lumineux face caméra : a = taille. */
      FLASH,
      /** Débris 3D : a = rayon, b = force, color = nombre, extra = id d'état de bloc, direction = poussée. */
      DEBRIS,
      /** Givre qui s'étend au sol : a = rayon. */
      FROST,
      /** Pics de cristal qui percent le sol puis éclatent : a = taille. */
      SPIKES,
      /** Stalactite qui tombe du ciel sur la position : a = taille. */
      ICICLE,
      /** Tremblement de caméra : a = intensité, b = portée. */
      SHAKE,
      /** Cinématique : extra = id de l'entité, b = type (0 apparition, 1 phase 2, 2 phase 3, 3 mort). */
      CINEMATIC,
      /** Grappe de racines qui jaillissent du sol, restent puis se rétractent : a = taille. */
      ROOTS,
      /** Racine géante qui sort du sol et s'enroule autour d'une entité : extra = id de l'entité. */
      ROOTGRAB,
      /** Épicéa entier qui surgit puis s'abat dans la direction donnée : a = hauteur. */
      TREEFALL,
      /** Alerte de Sylvorn : tache de pourriture veinée qui s'étend jusqu'à l'impact (a = rayon). */
      ROTCIRCLE,
      /** Idem en couloir : a = longueur, b = largeur. */
      ROTLINE,
      /** Idem en cône : a = portée, b = angle (degrés). */
      ROTCONE,
      /** Marque du chasseur (Sylvorn) : rune de bois qui suit l'entité extra ; b = 1 : marque verrouillée (rouge, fixe). a = rayon. */
      MARK,
      /** Hurlement (Sylvorn) : ondes sonores qui partent en cône depuis la gueule : a = portée, b = angle (degrés). */
      HOWL,
      /** Sillon de charge (Sylvorn) : mottes de terre arrachées le long du segment : a = longueur. */
      FURROW,
      /** Cratère de réception (Sylvorn) : plaques de sol soulevées en anneau puis retombées : a = rayon. */
      CRATER,
      /** Empreinte de sabot fendu (Sylvorn invisible) : direction = sens de marche, a = taille. */
      PRINT,
      /** Plaque de toile gluante au sol (Mère des Ruines) : a = rayon. */
      WEB,
      /** Fil de soie : de pos vers l'entité extra (ou pos + direction si extra = 0) ; b = 0 visée (rouge), 1 fil tendu, 2 fil du ciel. */
      SILK,
      /** Couvée : des dizaines de petites araignées accourent et tissent le cocon (a = rayon). */
      SWARM,
      /** Jet de flammes (Vulkhar) : de pos selon direction, a = portée, b = ouverture (degrés). */
      FLAME,
      /** Météore (Vulkhar) : rocher en fusion qui tombe de pos jusqu'à pos + direction pendant la durée ; a = taille. */
      METEOR,
      /** Nappe de gaz de peste qui stagne au sol (Porteur de Peste) : a = rayon, color = teinte. */
      GAS,
      /** Jet de bile (Porteur de Peste) : de pos selon direction, a = portée, b = ouverture (degrés). */
      BILE,
      /** Chaîne du crochet (Porteur de Peste) : extra = id du boss ; b = 0 visée (vers l'entité color), 1 lancer vers pos + direction,
       * 2 accroché à l'entité color, 3 planté en pos + direction. */
      CHAIN,
      /** Cosse de spores qui tombe du ciel (Porteur de Peste) : de pos jusqu'à pos + direction pendant la durée ; a = taille. */
      SPOREPOD,
      /** Flaque de liquide radioactif (Sujet N-07) : a = rayon. */
      POOL,
      /** Arc radioactif (Sujet N-07) : éclair brisé de pos à pos + direction. */
      ARC,
      /** Criticité (Sujet N-07) : sphère lumineuse qui enfle jusqu'au rayon a puis se dissipe. */
      MELTDOWN,
      /** Cercle de feu (Chevalier Sans Visage) : a = rayon ; b = 0 annonce, 1 mur de flammes. */
      FIRERING,
      /** Rituel d'invocation d'un donjon : pos = où se tient le joueur, direction = vers le centre du sanctuaire, a = ticks de marche,
       * b = type de donjon, durée totale, color = id du joueur, extra = 1 : rituel interrompu. */
      RITUAL,
      /** Voile plein écran pour le joueur extra (ScreenFx) : b = genre (0 encre, 1 givre, 2 soie, 3 éclair) ; a = opacité (0..1). */
      INKSPLAT
   }

   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeraliszSylvorn.MODID, "fvfx"), () -> "1", "1"::equals, "1"::equals);

   /** Dessin de l'effet reçu : branché par la partie client (VfxManager::receive) ; neutre sur un serveur dédié. */
   public static final Crochet<Packet> RECEPTION_CLIENT = new Crochet<>();

   public static void register() {
      CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle,
         java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));   // serveur -> joueurs seulement
   }

   public record Packet(Type type, double x, double y, double z, float dx, float dy, float dz, float a, float b, int duration, int color, int extra) {
      void encode(FriendlyByteBuf buf) {
         buf.writeByte(type.ordinal());
         buf.writeDouble(x);
         buf.writeDouble(y);
         buf.writeDouble(z);
         buf.writeFloat(dx);
         buf.writeFloat(dy);
         buf.writeFloat(dz);
         buf.writeFloat(a);
         buf.writeFloat(b);
         buf.writeVarInt(duration);
         buf.writeInt(color);
         buf.writeVarInt(extra);
      }

      static Packet decode(FriendlyByteBuf buf) {
         return new Packet(Type.values()[buf.readByte()], buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readInt(), buf.readVarInt());
      }

      void handle(Supplier<NetworkEvent.Context> ctx) {
         ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(this));
         ctx.get().setPacketHandled(true);
      }

      public Vec3 pos() {
         return new Vec3(x, y, z);
      }

      public Vec3 dir() {
         return new Vec3(dx, dy, dz);
      }
   }

   // ------------------------------------------------------------------ côté serveur
   public static final int WARN = 0xFFFFD23A, TOXIC = 0xFF7CFF3A, DANGER = 0xFFFF4A2A;

   public static void send(ServerLevel level, Type t, Vec3 pos, Vec3 dir, float a, float b, int duration, int color, int extra) {
      Packet p = new Packet(t, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, a, b, duration, color, extra);
      CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, 96, level.dimension())), p);
   }

   public static void circle(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.CIRCLE, pos, Vec3.ZERO, r, 0, dur, WARN, 0);
   }

   public static void line(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.LINE, from, dir, len, width, dur, WARN, 0);
   }

   public static void cone(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.CONE, from, dir, range, angle, dur, WARN, 0);
   }

   public static void shockwave(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.SHOCKWAVE, pos, Vec3.ZERO, r, 0.9F, dur, TOXIC, 0);
   }

   public static void column(ServerLevel l, Vec3 pos, float r, float h, int dur) {
      send(l, Type.COLUMN, pos, Vec3.ZERO, r, h, dur, TOXIC, 0);
   }

   public static void column(ServerLevel l, Vec3 pos, float r, float h, int dur, int color) {
      send(l, Type.COLUMN, pos, Vec3.ZERO, r, h, dur, 0xFF000000 | color, 0);
   }

   public static void nova(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.NOVA, pos, Vec3.ZERO, r, 0, dur, TOXIC, 0);
   }

   public static void nova(ServerLevel l, Vec3 pos, float r, int dur, int color) {
      send(l, Type.NOVA, pos, Vec3.ZERO, r, 0, dur, 0xFF000000 | color, 0);
   }

   public static void scorch(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.SCORCH, pos, Vec3.ZERO, r, 0, dur, TOXIC, 0);
   }

   /** Marque au sol teintée (texture neutre × couleur) : brûlure, tache d'encre, givre... */
   public static void scorch(ServerLevel l, Vec3 pos, float r, int dur, int color) {
      send(l, Type.SCORCH, pos, Vec3.ZERO, r, 0, dur, 0xFF000000 | color, 0);
   }

   public static void trail(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.TRAIL, from, dir, len, width, dur, TOXIC, 0);
   }

   public static void trail(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur, int color) {
      send(l, Type.TRAIL, from, dir, len, width, dur, 0xFF000000 | color, 0);
   }

   public static void vortex(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.VORTEX, pos, Vec3.ZERO, r, 0, dur, TOXIC, 0);
   }

   public static void vortex(ServerLevel l, Vec3 pos, float r, int dur, int color) {
      send(l, Type.VORTEX, pos, Vec3.ZERO, r, 0, dur, 0xFF000000 | color, 0);
   }

   public static void crack(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.CRACK, from, dir, len, width, dur, TOXIC, 0);
   }

   public static void slash(ServerLevel l, Vec3 pos, Vec3 dir, float r, float angle, int side) {
      send(l, Type.SLASH, pos, dir, r, angle, 7, TOXIC, side);
   }

   public static void slash(ServerLevel l, Vec3 pos, Vec3 dir, float r, float angle, int side, int color) {
      send(l, Type.SLASH, pos, dir, r, angle, 7, 0xFF000000 | color, side);
   }

   public static void flash(ServerLevel l, Vec3 pos, float size, int dur, int color) {
      send(l, Type.FLASH, pos, Vec3.ZERO, size, 0, dur, color, 0);
   }

   /** Morceaux du sol (état de bloc réel) projetés : rayon, force, nombre, poussée directionnelle optionnelle. */
   public static void debris(ServerLevel l, Vec3 pos, float radius, float force, int count, net.minecraft.world.level.block.state.BlockState state, Vec3 push) {
      send(l, Type.DEBRIS, pos, push, radius, force, 1, count, net.minecraft.world.level.block.Block.getId(state));
   }

   /** Gerbe d'éclats maison (roche, bois, chair, verre, os...) : remplace les morceaux de blocs vanilla qui volaient. */
   public static void chips(ServerLevel l, Vec3 pos, float radius, float force, int count, net.minecraft.core.particles.ParticleOptions type) {
      l.sendParticles(type, pos.x, pos.y, pos.z, count, radius * 0.5, 0.3, radius * 0.5, 0.05 + force * 0.035);
   }

   public static void frost(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.FROST, pos, Vec3.ZERO, r, 0, dur, 0xFFFFFFFF, 0);
   }

   public static void spikes(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.SPIKES, pos, Vec3.ZERO, size, 0, dur, 0xFFFFFFFF, 0);
   }

   public static void icicle(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.ICICLE, pos, Vec3.ZERO, size, 0, dur, 0xFFFFFFFF, 0);
   }

   public static void crack(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur, int color) {
      send(l, Type.CRACK, from, dir, len, width, dur, 0xFF000000 | color, 0);
   }

   public static void shockwave(ServerLevel l, Vec3 pos, float r, int dur, int color) {
      send(l, Type.SHOCKWAVE, pos, Vec3.ZERO, r, 0.9F, dur, 0xFF000000 | color, 0);
   }

   /** Tache d'encre sur l'écran du joueur (remplace la cécité vanilla). */
   public static void inkSplat(ServerLevel l, net.minecraft.world.entity.player.Player p, float strength, int dur) {
      screen(l, p, 0, strength, dur);
   }

   /** Voile plein écran propre au boss sur l'écran du joueur : 0 encre, 1 givre, 2 soie, 3 éclair (remplace la cécité vanilla). */
   public static void screen(ServerLevel l, net.minecraft.world.entity.player.Player p, int kind, float strength, int dur) {
      send(l, Type.INKSPLAT, p.position(), Vec3.ZERO, strength, kind, dur, 0, p.getId());
   }

   public static void shake(ServerLevel l, Vec3 pos, float intensity, float range, int dur) {
      send(l, Type.SHAKE, pos, Vec3.ZERO, intensity, range, dur, 0, 0);
   }

   public static void cinematic(ServerLevel l, Vec3 pos, int entityId, int kind, int dur) {
      send(l, Type.CINEMATIC, pos, Vec3.ZERO, 0, kind, dur, 0, entityId);
   }

   public static void roots(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.ROOTS, pos, Vec3.ZERO, size, 0, dur, 0, 0);
   }

   public static void rootGrab(ServerLevel l, Vec3 anchor, int entityId, int dur) {
      send(l, Type.ROOTGRAB, anchor, Vec3.ZERO, 1, 0, dur, 0, entityId);
   }

   public static void treefall(ServerLevel l, Vec3 base, Vec3 dir, float height, int dur) {
      send(l, Type.TREEFALL, base, dir, height, 0, dur, 0, 0);
   }

   public static void rotCircle(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.ROTCIRCLE, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void rotLine(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.ROTLINE, from, dir, len, width, dur, 0, 0);
   }

   public static void rotCone(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.ROTCONE, from, dir, range, angle, dur, 0, 0);
   }

   public static void mark(ServerLevel l, Vec3 pos, int entityId, float r, int dur, boolean locked) {
      send(l, Type.MARK, pos, Vec3.ZERO, r, locked ? 1 : 0, dur, 0, entityId);
   }

   public static void howl(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.HOWL, from, dir, range, angle, dur, 0, 0);
   }

   public static void furrow(ServerLevel l, Vec3 from, Vec3 dir, float len, int dur) {
      send(l, Type.FURROW, from, dir, len, 0, dur, 0, 0);
   }

   public static void crater(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.CRATER, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void print(ServerLevel l, Vec3 pos, Vec3 dir, float size, int dur) {
      send(l, Type.PRINT, pos, dir, size, 0, dur, 0, 0);
   }

   public static void web(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.WEB, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void silk(ServerLevel l, Vec3 from, int entityId, int mode, int dur) {
      send(l, Type.SILK, from, Vec3.ZERO, 0, mode, dur, 0, entityId);
   }

   public static void silk(ServerLevel l, Vec3 from, int entityId, int mode, int dur, Vec3 to) {
      send(l, Type.SILK, from, to.subtract(from), 0, mode, dur, 0, entityId);
   }

   public static void swarm(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.SWARM, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void flame(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.FLAME, from, dir, range, angle, dur, 0, 0);
   }

   public static void meteor(ServerLevel l, Vec3 from, Vec3 to, float size, int dur) {
      send(l, Type.METEOR, from, to.subtract(from), size, 0, dur, 0, 0);
   }

   public static void gas(ServerLevel l, Vec3 pos, float r, int dur, int color) {
      send(l, Type.GAS, pos, Vec3.ZERO, r, 0, dur, 0xFF000000 | color, 0);
   }

   public static void bile(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.BILE, from, dir, range, angle, dur, 0, 0);
   }

   /** Chaîne du crochet tendue entre la main du boss (bossId) et une entité (visée, accroché). */
   public static void chain(ServerLevel l, Vec3 from, int bossId, int mode, int dur, int entityId) {
      send(l, Type.CHAIN, from, Vec3.ZERO, 0, mode, dur, entityId, bossId);
   }

   /** Chaîne du crochet vers un point (lancer, planté). */
   public static void chain(ServerLevel l, Vec3 from, int bossId, int mode, int dur, Vec3 to) {
      send(l, Type.CHAIN, from, to.subtract(from), 0, mode, dur, 0, bossId);
   }

   public static void sporePod(ServerLevel l, Vec3 from, Vec3 to, float size, int dur) {
      send(l, Type.SPOREPOD, from, to.subtract(from), size, 0, dur, 0, 0);
   }

   /** Rituel : le joueur (playerId) marche jusqu'à stand, puis présente l'objet ; les clients proches voient son geste, lui a la caméra. */
   public static void ritual(ServerLevel l, Vec3 stand, Vec3 shrine, int walk, int kind, int total, int playerId, boolean abort) {
      send(l, Type.RITUAL, stand, shrine.subtract(stand), walk, kind, total, playerId, abort ? 1 : 0);
   }

   private Vfx() {}
}
