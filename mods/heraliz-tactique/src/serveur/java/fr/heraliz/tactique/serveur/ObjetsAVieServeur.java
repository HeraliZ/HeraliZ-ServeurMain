package fr.heraliz.tactique.serveur;

import fr.heraliz.tactique.avie.ObjetsAVie;
import fr.heraliz.tactique.avie.PaquetJeterQuestion;
import fr.heraliz.tactique.avie.PaquetJeterReponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Objets à vie (voir {@link ObjetsAVie}), côté serveur :
 * <ul>
 *     <li>mort : ils ne tombent pas (ni au sol ni sur le corps de HeraliZ Utils, qui range les objets tombés plus tard,
 *     en LOWEST) ; gardés dans les données persistantes du joueur ({@code PlayerPersisted.HeralizObjetsAVie}, copiées
 *     par Forge à la réapparition, donc aussi après un redémarrage) et rendus à la réapparition ou à la connexion ;</li>
 *     <li>touche de jet (aucun menu ouvert) : rien n'est jeté, l'objet reste dans la main (petit message) ;</li>
 *     <li>jet depuis un inventaire ouvert : l'objet est mis de côté et le joueur reçoit « Jeter cet objet ? ». Oui : il
 *     tombe. Non (ou écran fermé) : il revient dans l'inventaire ; inventaire plein entre-temps : l'objet de la case 9 de
 *     la barre est jeté à sa place (la dernière case de la barre qui ne tient pas un objet à vie). Mis de côté et
 *     déconnexion, mort ou arrêt du serveur : rendu (ou gardé pour la réapparition) ;</li>
 *     <li>posé dans un sac à dos (écran du sac de HeraliZ Backpack, clic ou Maj + clic) : mis de côté de même, avec
 *     « Mettre dans le sac ? ». Oui : il va dans le sac, marqué {@code HeralizSacOk} (marque retirée dès qu'il revient
 *     dans l'inventaire : la question revient la fois suivante) ; perdu avec le sac ensuite, tant pis. Non : rendu.</li>
 * </ul>
 * Créatif et spectateur : rien ne change (staff). Objets toujours déplacés, jamais copiés.
 */
public final class ObjetsAVieServeur {
   private ObjetsAVieServeur() {
   }

   private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
   private static final String CLE = "HeralizObjetsAVie";
   /** Objet mis de côté en attendant la réponse du joueur ; {@code menu} non nul : question du sac (case {@code cas}). */
   private record Attente(ItemStack objet, @javax.annotation.Nullable AbstractContainerMenu menu, int cas) {
   }

   private static final Map<UUID, Attente> EN_ATTENTE = new HashMap<>();
   private static final String MENU_SAC = "com.mrcrayfish.backpacked.inventory.container.BackpackContainerMenu";
   private static final String MARQUE_SAC = "HeralizSacOk";
   /** Jet confirmé en cours (le serveur jette lui-même : l'évènement de jet ne doit pas le reprendre). */
   private static boolean jetConfirme;

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ObjetsAVieServeur::jet);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ObjetsAVieServeur::chute);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::reapparition);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::connexion);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::deconnexion);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::tick);
      MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
         for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) rendreAttente(p);
         EN_ATTENTE.clear();
      });
      ObjetsAVie.REPONSE_SERVEUR.brancher(ObjetsAVieServeur::reponse);
   }

   private static boolean concerne(Player p) {
      return p instanceof ServerPlayer && !(p instanceof FakePlayer) && !p.isCreative() && !p.isSpectator();
   }

   // ------------------------------------------------------------------ jet
   private static void jet(ItemTossEvent e) {
      if (jetConfirme || !concerne(e.getPlayer())) return;
      ServerPlayer p = (ServerPlayer) e.getPlayer();
      ItemEntity entite = e.getEntity();
      ItemStack objet = entite.getItem();
      if (!ObjetsAVie.estAVie(objet)) return;
      // annulé : l'objet n'apparaît pas dans le monde ; il a déjà quitté l'inventaire, on le garde
      e.setCanceled(true);
      ItemStack garde = objet.copy();
      if (p.containerMenu == p.inventoryMenu) {
         // touche de jet en jeu : jamais jeté
         rendre(p, garde, true);
         p.displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.touche",
            "Objet à vie : il ne se jette pas avec la touche de jet."), true);
         return;
      }
      rendreAttente(p);
      EN_ATTENTE.put(p.getUUID(), new Attente(garde, null, -1));
      ObjetsAVie.envoyer(p, new PaquetJeterQuestion(garde, false));
   }

   // ------------------------------------------------------------------ sac à dos
   private static void tick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || !concerne(e.player)) return;
      ServerPlayer p = (ServerPlayer) e.player;
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (!s.isEmpty() && s.hasTag() && s.getTag().contains(MARQUE_SAC)) s.removeTagKey(MARQUE_SAC);
      }
      AbstractContainerMenu menu = p.containerMenu;
      if (menu == p.inventoryMenu || EN_ATTENTE.containsKey(p.getUUID()) || !MENU_SAC.equals(menu.getClass().getName())) return;
      for (Slot slot : menu.slots) {
         if (slot.container == inv) continue;
         ItemStack s = slot.getItem();
         if (!ObjetsAVie.estAVie(s) || (s.hasTag() && s.getTag().contains(MARQUE_SAC))) continue;
         ItemStack garde = s.copy();
         slot.set(ItemStack.EMPTY);
         EN_ATTENTE.put(p.getUUID(), new Attente(garde, menu, slot.index));
         menu.broadcastChanges();
         ObjetsAVie.envoyer(p, new PaquetJeterQuestion(garde, true));
         return;
      }
   }

   /** « Oui, le ranger » : dans sa case du sac si elle est libre, sinon une autre case libre du sac, sinon rendu. */
   private static void rangerDansSac(ServerPlayer p, Attente a) {
      ItemStack objet = a.objet();
      objet.getOrCreateTag().putBoolean(MARQUE_SAC, true);
      AbstractContainerMenu menu = p.containerMenu;
      if (menu == a.menu()) {
         Slot voulu = a.cas() >= 0 && a.cas() < menu.slots.size() ? menu.slots.get(a.cas()) : null;
         if (voulu != null && !voulu.hasItem() && voulu.mayPlace(objet)) {
            voulu.set(objet);
            menu.broadcastChanges();
            return;
         }
         for (Slot slot : menu.slots) {
            if (slot.container != p.getInventory() && !slot.hasItem() && slot.mayPlace(objet)) {
               slot.set(objet);
               menu.broadcastChanges();
               return;
            }
         }
      }
      objet.removeTagKey(MARQUE_SAC);
      rendre(p, objet, true);
   }

   private static void reponse(PaquetJeterReponse.Recue r) {
      ServerPlayer p = r.joueur();
      Attente a = EN_ATTENTE.remove(p.getUUID());
      if (a == null || a.objet().isEmpty()) return;
      ItemStack objet = a.objet();
      if (a.menu() != null && r.jeter() && p.isAlive()) {
         rangerDansSac(p, a);
         return;
      }
      if (!r.jeter() || !p.isAlive()) {
         rendre(p, objet, true);
         return;
      }
      jetConfirme = true;
      try {
         ItemEntity tombe = p.drop(objet, false, true);
         if (tombe == null) rendre(p, objet, true);
      } finally {
         jetConfirme = false;
      }
      synchroniser(p);
   }

   private static void rendreAttente(ServerPlayer p) {
      Attente a = EN_ATTENTE.remove(p.getUUID());
      if (a != null && !a.objet().isEmpty()) rendre(p, a.objet(), true);
   }

   /**
    * Remet un objet à vie dans l'inventaire (case choisie d'abord, puis n'importe quelle case libre) ; plein : l'objet de
    * la case 9 de la barre (ou de la dernière case de la barre qui ne tient pas un objet à vie) est jeté et remplacé.
    */
   private static void rendre(ServerPlayer p, ItemStack objet, boolean synchro) {
      Inventory inv = p.getInventory();
      if (inv.getSelected().isEmpty()) {
         inv.setItem(inv.selected, objet);
      } else if (!inv.add(objet) || !objet.isEmpty()) {
         // la dernière case de la barre qui ne tient pas un objet à vie, sinon une case du sac du joueur
         int k = -1;
         for (int i = 8; i >= 0 && k < 0; i--) if (!ObjetsAVie.estAVie(inv.getItem(i))) k = i;
         for (int i = 35; i >= 9 && k < 0; i--) if (!ObjetsAVie.estAVie(inv.getItem(i))) k = i;
         if (k < 0) {
            // tout l'inventaire est fait d'objets à vie : posé aux pieds plutôt que perdu
            jetConfirme = true;
            try {
               p.drop(objet, false, true);
            } finally {
               jetConfirme = false;
            }
            LOG.info("[a vie] inventaire de {} plein d'objets à vie : {} posé à ses pieds", p.getGameProfile().getName(), objet.getHoverName().getString());
            if (synchro) synchroniser(p);
            return;
         }
         ItemStack ancien = inv.getItem(k);
         inv.setItem(k, objet);
         if (!ancien.isEmpty()) {
            jetConfirme = true;
            try {
               p.drop(ancien, false, true);
            } finally {
               jetConfirme = false;
            }
         }
         LOG.info("[a vie] inventaire de {} plein : case {} jetée pour rendre {}", p.getGameProfile().getName(), k + 1,
            objet.getHoverName().getString());
      }
      if (synchro) synchroniser(p);
   }

   /** Le jeu du joueur a déjà retiré l'objet de sa case : on lui renvoie l'inventaire tel qu'il est au serveur. */
   private static void synchroniser(ServerPlayer p) {
      p.inventoryMenu.sendAllDataToRemote();
      if (p.containerMenu != p.inventoryMenu) p.containerMenu.sendAllDataToRemote();
   }

   // ------------------------------------------------------------------ mort
   private static void chute(LivingDropsEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
      ListTag garde = new ListTag();
      for (Iterator<ItemEntity> it = e.getDrops().iterator(); it.hasNext(); ) {
         ItemEntity ie = it.next();
         if (ie != null && ObjetsAVie.estAVie(ie.getItem())) {
            garde.add(ie.getItem().save(new CompoundTag()));
            it.remove();
         }
      }
      // objet tenu au curseur, grille d'artisanat, objet mis de côté (question en cours)
      ItemStack curseur = p.containerMenu.getCarried();
      if (ObjetsAVie.estAVie(curseur)) {
         garde.add(curseur.save(new CompoundTag()));
         p.containerMenu.setCarried(ItemStack.EMPTY);
      }
      CraftingContainer grille = p.inventoryMenu.getCraftSlots();
      for (int i = 0; i < grille.getContainerSize(); i++) {
         if (ObjetsAVie.estAVie(grille.getItem(i))) garde.add(grille.removeItemNoUpdate(i).save(new CompoundTag()));
      }
      Attente attente = EN_ATTENTE.remove(p.getUUID());
      if (attente != null && !attente.objet().isEmpty()) garde.add(attente.objet().save(new CompoundTag()));
      if (garde.isEmpty()) return;
      CompoundTag persiste = persiste(p);
      ListTag deja = persiste.getList(CLE, Tag.TAG_COMPOUND);
      deja.addAll(garde);
      persiste.put(CLE, deja);
      LOG.info("[a vie] {} mort : {} objet(s) à vie gardé(s) pour la réapparition", p.getGameProfile().getName(), garde.size());
   }

   private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && !e.isEndConquered()) rendreGardes(p);
   }

   private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && p.isAlive()) rendreGardes(p);
   }

   private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         if (p.isAlive()) {
            rendreAttente(p);
         } else {
            Attente attente = EN_ATTENTE.remove(p.getUUID());
            if (attente != null && !attente.objet().isEmpty()) {
               CompoundTag persiste = persiste(p);
               ListTag deja = persiste.getList(CLE, Tag.TAG_COMPOUND);
               deja.add(attente.objet().save(new CompoundTag()));
               persiste.put(CLE, deja);
            }
         }
      }
   }

   private static void rendreGardes(ServerPlayer p) {
      CompoundTag persiste = persiste(p);
      if (!persiste.contains(CLE)) return;
      ListTag liste = persiste.getList(CLE, Tag.TAG_COMPOUND);
      persiste.remove(CLE);
      int n = 0;
      for (int i = 0; i < liste.size(); i++) {
         ItemStack s = ItemStack.of(liste.getCompound(i));
         if (s.isEmpty()) continue;
         if (!p.getInventory().add(s) || !s.isEmpty()) rendre(p, s, false);
         n++;
      }
      synchroniser(p);
      if (n > 0) LOG.info("[a vie] {} : {} objet(s) à vie rendu(s)", p.getGameProfile().getName(), n);
   }

   private static CompoundTag persiste(ServerPlayer p) {
      CompoundTag racine = p.getPersistentData();
      if (!racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) racine.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
      return racine.getCompound(Player.PERSISTED_NBT_TAG);
   }
}
