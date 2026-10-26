package fr.heralisz.camo.serveur;

import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.decor.DecorData;
import fr.heralisz.camo.decor.DecorItems;
import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.network.ApplyCamoPacket;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.DecorPacket;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import static fr.heralisz.camo.network.DecorPacket.ADD_STICKER;
import static fr.heralisz.camo.network.DecorPacket.MOVE_STATTRACK;
import static fr.heralisz.camo.network.DecorPacket.MOVE_STICKER;
import static fr.heralisz.camo.network.DecorPacket.SCRAPE_STICKER;
import static fr.heralisz.camo.network.DecorPacket.SET_STATTRACK;

/**
 * Partie SERVEUR des paquets client -> serveur de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §6) : appliquer un camo sur
 * l'arme tenue (définitif), poser / déplacer / gratter les stickers et le module StatTrack. Code d'origine des
 * méthodes handle des paquets (tout est revérifié ici), avec « p.champ » devenu « p.champ() ».
 */
public final class ReceptionServeur {
   private ReceptionServeur() {
   }

   // ApplyCamoPacket.handle (d'origine)
   static void appliquerCamo(CamoNetwork.Recu<ApplyCamoPacket> r) {
      ApplyCamoPacket p = r.paquet();
      ServerPlayer player = r.joueur();
      if (player == null || p.camoSlot() < 0 || p.camoSlot() >= player.getInventory().getContainerSize()) {
         return;
      }
      ItemStack gun = player.getMainHandItem();
      if (IGun.getIGunOrNull(gun) == null || KnifeSkins.of(gun) != null || fr.heraliz.tactique.onglets.Couteaux.estCouteau(gun)) {
         return; // pas une arme, ou un couteau (jamais modifié, 21/10)
      }
      if (CamoData.get(gun) != null && CamoData.owner(gun) == null) {
         return; // déjà un camo définitif (un camo à vie, lui, peut être remplacé)
      }
      ItemStack camoStack = player.getInventory().getItem(p.camoSlot());
      if (!(camoStack.getItem() instanceof CamoItem item)) {
         return;
      }
      if (fr.heraliz.tactique.avie.ObjetsAVie.estSkinAVie(item.camo())) {
         // skin à vie (21/10) : clic droit sur l'objet pour l'ajouter à sa collection, puis posé depuis la collection
         player.displayClientMessage(Component.translatableWithFallback("msg.heralisz_camo.skin_a_vie",
            "Skin à vie : clic droit sur l'objet pour l'ajouter à ta collection, puis pose-le depuis la collection.")
            .withStyle(ChatFormatting.GOLD), true);
         return;
      }
      if (!item.camo().fits(IGun.getIGunOrNull(gun).getGunId(gun))) {
         player.displayClientMessage(Component.translatable("msg.heralisz_camo.wrong_gun",
            Component.translatable("item.heralisz_camo.camo.gun." + item.camo().id)).withStyle(ChatFormatting.RED), true);
         return; // skin de boss réservé à une autre arme
      }
      CamosAVie.liberer(player, gun);
      CamoData.set(gun, item.camo());
      camoStack.shrink(1);
      player.getInventory().setChanged();
      player.inventoryMenu.broadcastChanges();
      player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
      player.displayClientMessage(Component.translatable("msg.heralisz_camo.applied",
         Component.translatable(item.camo().nameKey()).withStyle(item.camo().tier.color)).withStyle(ChatFormatting.GRAY), true);
   }

   // DecorPacket.handle (d'origine)
   static void decor(CamoNetwork.Recu<DecorPacket> r) {
      DecorPacket p = r.paquet();
      ServerPlayer player = r.joueur();
      if (player == null) return;
      ItemStack gun = player.getMainHandItem();
      if (IGun.getIGunOrNull(gun) == null) return;
      // un couteau ne se modifie pas (21/10, choix du propriétaire) : ni sticker, ni StatTrak
      if (KnifeSkins.of(gun) != null || fr.heraliz.tactique.onglets.Couteaux.estCouteau(gun)) return;
      DecorData.Placement at = p.at() != null ? p.at().sanitized() : null;
      ItemStack item = p.slot() >= 0 && p.slot() < player.getInventory().getContainerSize() ? player.getInventory().getItem(p.slot()) : ItemStack.EMPTY;
      List<DecorData.Sticker> stickers = DecorData.stickers(gun);
      boolean done = false;
      switch (p.action()) {
         case ADD_STICKER -> {
            if (at != null && item.getItem() instanceof DecorItems.StickerItem si && stickers.size() < DecorData.MAX_STICKERS) {
               stickers.add(new DecorData.Sticker(si.design.id(), si.finish.ordinal(), at));
               DecorData.setStickers(gun, stickers);
               item.shrink(1);
               done = true;
            }
         }
         case MOVE_STICKER -> {
            if (at != null && p.index() >= 0 && p.index() < stickers.size()) {
               DecorData.Sticker s = stickers.get(p.index());
               stickers.set(p.index(), new DecorData.Sticker(s.id(), s.finish(), at));
               DecorData.setStickers(gun, stickers);
               done = true;
            }
         }
         case SCRAPE_STICKER -> {
            if (p.index() >= 0 && p.index() < stickers.size()) {
               stickers.remove(p.index());
               DecorData.setStickers(gun, stickers);
               done = true;
            }
         }
         case SET_STATTRACK -> {
            if (at != null && item.getItem() instanceof DecorItems.StatTrakItem && !DecorData.hasStatTrak(gun)) {
               DecorData.setStatTrak(gun, at);
               item.shrink(1);
               done = true;
            }
         }
         case MOVE_STATTRACK -> {
            if (at != null && DecorData.hasStatTrak(gun)) {
               DecorData.setStatTrak(gun, at);
               done = true;
            }
         }
         default -> {
         }
      }
      if (done) {
         player.getInventory().setChanged();
         player.inventoryMenu.broadcastChanges();
         player.level().playSound(null, player.blockPosition(), p.action() == SCRAPE_STICKER ? SoundEvents.BRUSH_GENERIC : SoundEvents.ITEM_FRAME_ADD_ITEM,
            SoundSource.PLAYERS, 0.8F, 1.2F);
      }
   }
}
