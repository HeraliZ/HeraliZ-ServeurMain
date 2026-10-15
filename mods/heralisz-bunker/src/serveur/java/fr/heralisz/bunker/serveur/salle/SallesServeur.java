package fr.heralisz.bunker.serveur.salle;

import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.interfaces.IMultiblockPart;
import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.CardReaderBlock;
import fr.heralisz.bunker.keycard.KeycardConfig;
import fr.heralisz.bunker.keycard.KeycardDoors;
import fr.heralisz.bunker.salle.SallesPont;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Règles des salles à carte branchées dans le code commun ({@link SallesPont}) et recherche des portes. */
final class SallesServeur implements SallesPont.Regles {
   @Override
   public boolean tenueOuverte(Level level, BlockPos porte) {
      return level instanceof ServerLevel sl && SallesMonde.get(sl).tenue(porte);
   }

   @Override
   public void accesAccorde(Level level, BlockPos lecteur, AccessLevel niveau) {
      if (!(level instanceof ServerLevel sl)) return;
      SallesMonde m = SallesMonde.get(sl);
      if (m.salles.isEmpty()) return;
      if (m.acces(sl, KeycardDoors.doorsNear(sl, lecteur, niveau)).isEmpty()) return;
      Component msg = Component.translatableWithFallback("heralisz_bunker.salle.entree", "Porte ouverte : %s s pour entrer",
         String.valueOf(KeycardConfig.entreeTicks() / 20)).withStyle(ChatFormatting.YELLOW);
      Vec3 c = Vec3.atCenterOf(lecteur);
      for (ServerPlayer p : sl.players()) {
         if (p.distanceToSqr(c) <= 64) p.displayClientMessage(msg, true);
      }
   }

   @Override
   public void coin(ServerPlayer joueur, BlockPos pos, int coin) {
      if (Selections.staff(joueur)) Selections.coin(joueur, pos, coin);
   }

   // ------------------------------------------------------------------------------------------ portes
   /** Contrôleur de la porte à laquelle appartient ce bloc (porte ou une de ses pièces), null sinon. */
   @Nullable
   static BlockPos controleur(ServerLevel l, BlockPos p) {
      BlockState s = l.getBlockState(p);
      if (s.getBlock() instanceof DoorBlock) return p.immutable();
      if (s.getBlock() instanceof UniversalMachinePartBlock && l.getBlockEntity(p) instanceof IMultiblockPart part && part.getControllerPos() != null) {
         return part.getControllerPos().immutable();
      }
      return null;
   }

   /** Couleur de la porte à carte dont c est le contrôleur (null : pas une porte à carte). */
   @Nullable
   static AccessLevel couleurPorte(ServerLevel l, BlockPos c) {
      return l.getBlockEntity(c) instanceof DoorBlockEntity d ? KeycardDoors.levelOf(d.getBlockState().getBlock()) : null;
   }

   /** Portes à carte (contrôleur -> couleur) dont une pièce est dans la boîte min..max agrandie de marge (secteurs chargés). */
   static Map<BlockPos, AccessLevel> portesAutour(ServerLevel l, BlockPos min, BlockPos max, int marge) {
      Map<BlockPos, AccessLevel> out = new LinkedHashMap<>();
      int x0 = min.getX() - marge, y0 = Math.max(l.getMinBuildHeight(), min.getY() - marge), z0 = min.getZ() - marge;
      int x1 = max.getX() + marge, y1 = Math.min(l.getMaxBuildHeight() - 1, max.getY() + marge), z1 = max.getZ() + marge;
      BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
      for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
         for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            LevelChunk ch = l.getChunkSource().getChunkNow(cx, cz);
            if (ch == null) continue;
            for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
               for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                  for (int y = y0; y <= y1; y++) {
                     BlockState s = ch.getBlockState(mp.set(x, y, z));
                     if (!(s.getBlock() instanceof DoorBlock) && !(s.getBlock() instanceof UniversalMachinePartBlock)) continue;
                     BlockPos c = controleur(l, mp);
                     if (c == null || out.containsKey(c)) continue;
                     AccessLevel niv = couleurPorte(l, c);
                     if (niv != null) out.put(c, niv);
                  }
               }
            }
         }
      }
      return out;
   }

   /** Porte à carte visée (porte, une de ses pièces, ou son lecteur) à 8 blocs au plus : son contrôleur, ou null. */
   @Nullable
   static BlockPos porteVisee(ServerPlayer p) {
      HitResult h = p.pick(8.0, 1.0F, false);
      if (h.getType() != HitResult.Type.BLOCK || !(h instanceof BlockHitResult bh)) return null;
      ServerLevel l = p.serverLevel();
      BlockPos b = bh.getBlockPos();
      if (l.getBlockState(b).getBlock() instanceof CardReaderBlock lecteur) {
         List<DoorBlockEntity> portes = KeycardDoors.doorsNear(l, b, lecteur.level);
         return portes.isEmpty() ? null : portes.get(0).getBlockPos().immutable();
      }
      BlockPos c = controleur(l, b);
      return c != null && couleurPorte(l, c) != null ? c : null;
   }

   /** Portes à carte de la couleur voulue autour de la boîte (3 blocs). */
   static List<BlockPos> portesDeCouleur(Map<BlockPos, AccessLevel> portes, AccessLevel niveau) {
      List<BlockPos> out = new ArrayList<>();
      portes.forEach((c, n) -> {
         if (n == niveau) out.add(c);
      });
      return out;
   }
}
