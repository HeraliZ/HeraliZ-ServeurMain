package fr.heraliz.tactique.client.portes;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import fr.heralisz.tactical.api.item.ICustomItem;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.noyau.api.Portes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.lwjgl.glfw.GLFW;

/**
 * Ouvrir ou fermer une porte avec une arme en main (31/10, demande du propriétaire). Une arme de TaCZ prend le clic droit
 * pour viser (TaCZ : {@code ClientPreventGunClick} annule tout clic « Utiliser », {@code AimKey} vise sur le même bouton) ;
 * une arme de mêlée, un bouclier flash ({@link ICustomItem#shouldBlockUse()}) le prennent pour leur coup ou leur action :
 * le clic n'arrivait jamais à la porte. Ici, viseur sur une porte ({@link Portes} : Minecraft, trappes et portes des bases,
 * portes du bunker) à portée de main, le clic droit va d'abord à la porte, exactement comme avec une main vide
 * ({@code MultiPlayerGameMode.useItemOn} : prédiction chez le joueur, paquet ordinaire au serveur, qui décide de tout :
 * droit « Portes » du clan, porte à carte, joueur à terre, plugins...). Si la porte prend le clic, l'appui s'arrête là
 * (ni visée, ni coup) ; sinon (porte en fer, trappe de fer...), la visée ou le coup se font comme avant.
 * <p>Inchangés : déjà en train de viser (l'appui sert à arrêter la visée), accroupi (règle de Minecraft : un objet en
 * main ne touche pas au bloc), à terre, regard prêté à une caméra (cinématique), écran ouvert ; tous les autres objets
 * (nourriture, soins, épées de Minecraft : le clic va déjà au bloc d'abord). Touche « Utiliser » mise sur le clavier :
 * même chose, quand l'arme a annulé le clic.</p>
 */
public final class PortesEnMain {
   private PortesEnMain() {
   }

   public static void brancher() {
      // avant TaCZ (visée), les coups de mêlée et la fouille avec un objet en main (qui ne vise pas les portes)
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseButton.Pre.class, PortesEnMain::souris);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, InputEvent.InteractionKeyMappingTriggered.class, PortesEnMain::touche);
   }

   private static void souris(InputEvent.MouseButton.Pre e) {
      if (e.getAction() != GLFW.GLFW_PRESS) return;
      Minecraft mc = Minecraft.getInstance();
      if (!mc.options.keyUse.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) return;
      if (porte(mc)) e.setCanceled(true);
   }

   /**
    * Touche « Utiliser » au clavier, clic annulé par l'arme : rendu à la porte. Seulement pour une touche du clavier : à la
    * souris, l'appui est déjà pris plus haut, et un bouton maintenu pour viser ne doit pas ouvrir les portes balayées.
    */
   private static void touche(InputEvent.InteractionKeyMappingTriggered e) {
      if (!e.isUseItem() || !e.isCanceled() || e.getHand() != InteractionHand.MAIN_HAND) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.keyUse.getKey().getType() != InputConstants.Type.KEYSYM) return;
      if (porte(mc)) e.setSwingHand(false);
   }

   /** Clic droit envoyé à la porte visée ; vrai si elle l'a pris (le reste du jeu ne le voit plus). */
   private static boolean porte(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null || mc.gameMode == null || mc.screen != null || mc.getOverlay() != null) return false;
      if (p.isSpectator() || p.isSecondaryUseActive() || p.isHandsBusy() || mc.getCameraEntity() != p) return false;
      if (!prendLeClic(p, p.getMainHandItem()) || ATerre.aTerreReanimation(p)) return false;
      if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
      if (!Portes.visee(mc.level, hit)) return false;
      InteractionResult r = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
      if (!r.consumesAction()) return false;
      if (r.shouldSwing()) p.swing(InteractionHand.MAIN_HAND);
      return true;
   }

   /** Objet qui prend le clic droit pour lui (arme à feu hors visée, mêlée, bouclier flash). */
   private static boolean prendLeClic(LocalPlayer p, ItemStack s) {
      if (s.isEmpty()) return false;
      if (IGun.getIGunOrNull(s) != null) return !IClientPlayerGunOperator.fromLocalPlayer(p).isAim();
      return s.getItem() instanceof ICustomItem c && c.shouldBlockUse();
   }
}
