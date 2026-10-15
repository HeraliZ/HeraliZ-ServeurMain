package fr.heralisz.bunker.serveur.salle;

import fr.heralisz.bunker.keycard.KeycardNetwork;
import fr.heralisz.bunker.salle.BaguetteSalleItem;
import fr.heralisz.bunker.salle.ContoursPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sélections du staff (en mémoire seulement : oubliées à la déconnexion et au redémarrage) : deux coins, posés à la
 * baguette (clic gauche = coin 1, clic droit = coin 2) ou par /salle coin1|coin2 [x y z]. Le contour de la sélection et
 * des salles proches est envoyé au staff qui tient la baguette.
 */
final class Selections {
   /** Rayon (blocs) dans lequel les salles sont montrées au staff qui tient la baguette. */
   private static final double RAYON_SALLES = 128;
   private static final Map<UUID, Sel> SEL = new HashMap<>();

   record Sel(ResourceKey<Level> dim, @Nullable BlockPos c1, @Nullable BlockPos c2) {
      boolean complete() {
         return c1 != null && c2 != null;
      }

      BlockPos min() {
         return new BlockPos(Math.min(c1.getX(), c2.getX()), Math.min(c1.getY(), c2.getY()), Math.min(c1.getZ(), c2.getZ()));
      }

      BlockPos max() {
         return new BlockPos(Math.max(c1.getX(), c2.getX()), Math.max(c1.getY(), c2.getY()), Math.max(c1.getZ(), c2.getZ()));
      }
   }

   private Selections() {}

   static boolean staff(Player p) {
      return p.hasPermissions(2);
   }

   /** Sélection du joueur dans sa dimension actuelle (null : rien, ou sélection faite dans une autre dimension). */
   @Nullable
   static Sel de(ServerPlayer p) {
      Sel s = SEL.get(p.getUUID());
      return s != null && s.dim().equals(p.level().dimension()) ? s : null;
   }

   static void effacer(ServerPlayer p) {
      SEL.remove(p.getUUID());
      envoyer(p);
   }

   static void oublier(Player p) {
      SEL.remove(p.getUUID());
   }

   /** Pose un coin (1 ou 2). -> faux si rien n'a changé (même bloc). */
   static boolean coin(ServerPlayer p, BlockPos pos, int coin) {
      BlockPos b = pos.immutable();
      Sel s = de(p);
      BlockPos c1 = s == null ? null : s.c1(), c2 = s == null ? null : s.c2();
      if (coin == 1) {
         if (b.equals(c1)) return false;
         c1 = b;
      } else {
         if (b.equals(c2)) return false;
         c2 = b;
      }
      Sel n = new Sel(p.level().dimension(), c1, c2);
      SEL.put(p.getUUID(), n);
      p.sendSystemMessage(Component.translatableWithFallback("heralisz_bunker.salle.coin", "Coin %1$s : %2$s",
         String.valueOf(coin), coords(b)).withStyle(coin == 1 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
      if (n.complete()) p.sendSystemMessage(taille(n).copy().withStyle(ChatFormatting.GRAY));
      envoyer(p);
      return true;
   }

   static String coords(BlockPos b) {
      return b.getX() + " " + b.getY() + " " + b.getZ();
   }

   static Component taille(Sel s) {
      BlockPos a = s.min(), b = s.max();
      int x = b.getX() - a.getX() + 1, y = b.getY() - a.getY() + 1, z = b.getZ() - a.getZ() + 1;
      return Component.translatableWithFallback("heralisz_bunker.salle.selection.taille", "Sélection : %1$s × %2$s × %3$s blocs, de %4$s à %5$s",
         String.valueOf(x), String.valueOf(y), String.valueOf(z), coords(a), coords(b));
   }

   // ------------------------------------------------------------------------------------------ baguette
   /** Clic gauche sur un bloc avec la baguette (serveur) : coin 1, et le bloc n'est jamais cassé. */
   static void clicGauche(PlayerInteractEvent.LeftClickBlock e) {
      if (e.getLevel().isClientSide() || !(e.getItemStack().getItem() instanceof BaguetteSalleItem)) return;
      e.setCanceled(true);
      if (e.getEntity() instanceof ServerPlayer sp && staff(sp)) coin(sp, e.getPos(), 1);
   }

   /** Toutes les secondes : le staff qui tient la baguette reçoit les contours (sélection et salles proches). */
   static void baguette(ServerPlayer p) {
      if (!(p.getMainHandItem().getItem() instanceof BaguetteSalleItem) && !(p.getOffhandItem().getItem() instanceof BaguetteSalleItem)) return;
      if (staff(p)) envoyer(p);
   }

   /** Contours de la baguette : la sélection, ses coins, les salles à moins de RAYON_SALLES blocs. */
   static void envoyer(ServerPlayer p) {
      List<ContoursPacket.Boite> l = new ArrayList<>();
      Sel s = de(p);
      if (s != null) {
         if (s.complete()) l.add(new ContoursPacket.Boite(s.min(), s.max(), ContoursPacket.SELECTION));
         if (s.c1() != null) l.add(new ContoursPacket.Boite(s.c1(), s.c1(), ContoursPacket.COIN1));
         if (s.c2() != null) l.add(new ContoursPacket.Boite(s.c2(), s.c2(), ContoursPacket.COIN2));
      }
      if (p.level() instanceof ServerLevel sl) {
         for (SallesMonde.Salle x : SallesMonde.get(sl).salles.values()) {
            if (l.size() >= ContoursPacket.MAX) break;
            net.minecraft.world.phys.AABB a = x.boite();
            double dx = Math.max(0, Math.max(a.minX - p.getX(), p.getX() - a.maxX));
            double dy = Math.max(0, Math.max(a.minY - p.getY(), p.getY() - a.maxY));
            double dz = Math.max(0, Math.max(a.minZ - p.getZ(), p.getZ() - a.maxZ));
            if (dx * dx + dy * dy + dz * dz <= RAYON_SALLES * RAYON_SALLES) l.add(boite(x));
         }
      }
      KeycardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ContoursPacket(false, l));
   }

   static ContoursPacket.Boite boite(SallesMonde.Salle x) {
      byte g = switch (x.niveau) {
         case GREEN -> ContoursPacket.SALLE_VERTE;
         case BLUE -> ContoursPacket.SALLE_BLEUE;
         case RED -> ContoursPacket.SALLE_ROUGE;
      };
      return new ContoursPacket.Boite(x.min, x.max, g);
   }

   /** /salle voir : la salle montrée 30 s, même sans la baguette. */
   static void montrer(ServerPlayer p, SallesMonde.Salle x) {
      KeycardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ContoursPacket(true, List.of(boite(x))));
   }
}
