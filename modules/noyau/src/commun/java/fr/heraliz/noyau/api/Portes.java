package fr.heraliz.noyau.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Portes qu'on ouvre et ferme au clic droit (31/10) : portes, trappes et portillons de Minecraft (et des mods qui
 * reprennent leurs classes : décors...), plus celles que d'autres mods déclarent ici (portes et trappes des bases de
 * CRac, portes animées du bunker). Lu par le jeu du joueur : avec une arme en main, le clic droit sur une porte l'ouvre
 * ou la ferme au lieu de viser (Tactique, {@code client/PortesEnMain}).
 * <p>Déclarer une porte, sans dépendre du noyau à la compilation (réflexion, comme les autres API du noyau) :
 * {@code Class.forName("fr.heraliz.noyau.api.Portes").getMethod("ajouter", BiPredicate.class).invoke(null, test)}, où
 * {@code test} reçoit le monde du joueur et le point visé. Ne jamais renommer cette classe ni {@link #ajouter}.</p>
 * <p>Une porte qui ne s'ouvre pas à la main (fer) peut répondre vrai : le bloc ne prend pas le clic et la visée se fait
 * comme avant. Une porte qui prend le clic sans s'ouvrir (porte à carte : « verrouillée ») ne doit pas être déclarée.</p>
 */
public final class Portes {
   private static final List<BiPredicate<Level, BlockHitResult>> AUTRES = new CopyOnWriteArrayList<>();

   private Portes() {
   }

   /** Déclare les portes d'un mod (appelé une fois, au chargement de sa partie client). */
   public static void ajouter(BiPredicate<Level, BlockHitResult> test) {
      if (test != null) AUTRES.add(test);
   }

   /** Vrai si le point visé est une porte (ou trappe, portillon) qui s'ouvre au clic droit. */
   public static boolean visee(Level level, BlockHitResult hit) {
      Block b = level.getBlockState(hit.getBlockPos()).getBlock();
      if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return true;
      for (BiPredicate<Level, BlockHitResult> t : AUTRES) {
         if (t.test(level, hit)) return true;
      }
      return false;
   }
}
