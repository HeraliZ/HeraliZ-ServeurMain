package fr.heraliz.tactique.serveur;

import fr.heraliz.tactique.avie.ObjetsAVie;
import fr.heraliz.tactique.avie.PaquetJeterQuestion;
import fr.heraliz.tactique.avie.PaquetJeterReponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Objets à vie (voir {@link ObjetsAVie}), côté serveur. Règle du propriétaire (02/11) : un objet à vie n'est JAMAIS
 * perdu, sauf si un admin le supprime. Il est toujours soit dans un inventaire, soit dans les données du joueur (gardées
 * et enregistrées avec lui par Minecraft), soit dans sa boîte aux lettres hors du monde : jamais seulement en mémoire.
 * <ul>
 *     <li>mort : ils ne tombent pas (ni au sol ni sur le corps de HeraliZ Utils, qui range les objets tombés plus tard,
 *     en LOWEST), ceux rangés dans un sac porté (ou dans un sac de l'inventaire) non plus : gardés dans les données
 *     persistantes du joueur ({@code PlayerPersisted.HeralizObjetsAVie}, copiées par Forge à la réapparition, donc aussi
 *     après un redémarrage) et rendus à la réapparition ou à la connexion. Pas en match (drapeau
 *     {@code heralisz:en_match} : les kits des matchs sont gérés par le match) ;</li>
 *     <li>touche de jet (aucun menu ouvert) : rien n'est jeté, l'objet reste dans la main (petit message) ;</li>
 *     <li>jet depuis un inventaire ouvert : l'objet est mis de côté (dans les données du joueur, {@code
 *     HeralizObjetsAVieQuestion} : il survit à un plantage) et le joueur reçoit « Jeter cet objet ? ». Oui : il tombe.
 *     Non (ou écran fermé) : il revient dans l'inventaire ;</li>
 *     <li>inventaire plein quand un objet revient : il est gardé (comme à la mort) et rendu dès qu'une case se libère
 *     (vérifié chaque seconde), jamais posé au sol ni échangé contre un autre objet ;</li>
 *     <li>déconnexion (02/11) : Minecraft enregistre le joueur PUIS jette ce qu'il tenait au curseur et ce qui était dans
 *     les cases de fabrication (inventaire de base, écran de Curios, établi, recycleur...). En dernier écouteur de la
 *     déconnexion (avant l'enregistrement), l'écran ouvert est fermé ici et les objets à vie du curseur et de la grille
 *     2x2 reviennent dans l'inventaire (sinon gardés) ; une question en cours aussi. Ce qui arriverait encore après part
 *     dans la boîte aux lettres ;</li>
 *     <li>pendant la mort ou la déconnexion, un objet à vie jeté par un écran qui se ferme est gardé, jamais posé ;</li>
 *     <li>au sol : invulnérable (feu, lave, explosions, cactus) ; s'il n'est ramassé par personne avant de disparaître
 *     (5 min), il revient à celui qui l'a jeté ; sans lanceur connu, il reste au sol. Un sac au sol qui disparaît rend
 *     de même les objets à vie qu'il contient ;</li>
 *     <li>posé dans un sac à dos (écran du sac de HeraliZ Backpack, clic ou Maj + clic) : mis de côté de même, avec
 *     « Mettre dans le sac ? ». Oui : il va dans le sac, marqué {@code HeralizSacOk} (marque retirée quand il revient
 *     dans l'inventaire, vérifié chaque seconde : la question revient la fois suivante). Non : rendu ;</li>
 *     <li>boîte aux lettres ({@code heraliz_tactique/objets_a_vie/<uuid>.dat}, hors du monde) : objets sauvés pour un
 *     joueur hors ligne ({@link ObjetsAVie#neJamaisDetruire}, objet resté au sol). Lue à la connexion ; la copie lue
 *     n'est effacée qu'après l'enregistrement suivant du joueur (un plantage entre les deux ne perd ni ne double rien).</li>
 * </ul>
 * Créatif et spectateur : rien ne change (staff). Objets toujours déplacés, jamais copiés.
 */
public final class ObjetsAVieServeur {
   private ObjetsAVieServeur() {
   }

   private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
   private static final String CLE = "HeralizObjetsAVie";
   private static final String CLE_QUESTION = "HeralizObjetsAVieQuestion";
   private static final String DRAPEAU_EN_MATCH = "heralisz:en_match";
   /** Données d'une entité « objet au sol » qui porte un objet à vie : à qui le rendre s'il n'est jamais ramassé. */
   private static final String PROPRIO_SOL = "HeralizAVieProprio";
   /** Vie en plus d'un objet à vie au sol sans lanceur connu, à chaque fois qu'il devrait disparaître (5 min). */
   private static final int VIE_EN_PLUS = 6000;

   /** Objet mis de côté en attendant la réponse du joueur ; {@code menu} non nul : question du sac (case {@code cas}). */
   private record Attente(ItemStack objet, @Nullable AbstractContainerMenu menu, int cas) {
   }

   private static final Map<UUID, Attente> EN_ATTENTE = new HashMap<>();
   /** Joueurs dont la déconnexion a déjà été traitée (vidé au tick suivant) : ce qui arrive après va dans leur boîte. */
   private static final Set<UUID> SORTIS = new HashSet<>();
   /** Boîtes aux lettres lues à la connexion : la copie lue est effacée après le prochain enregistrement du joueur. */
   private static final Set<UUID> BOITES_LUES = new HashSet<>();
   private static final String MENU_SAC = "com.mrcrayfish.backpacked.inventory.container.BackpackContainerMenu";
   private static final String MARQUE_SAC = "HeralizSacOk";
   /** Jet confirmé en cours (le serveur jette lui-même : l'évènement de jet ne doit pas le reprendre). */
   private static boolean jetConfirme;

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ObjetsAVieServeur::jet);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ObjetsAVieServeur::chute);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::reapparition);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::connexion);
      // en dernier : les autres mods rangent d'abord ce que leurs écrans tiennent (recycleur, atelier...)
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ObjetsAVieServeur::deconnexion);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::enregistre);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::tick);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::tickServeur);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::arriveeAuSol);
      MinecraftForge.EVENT_BUS.addListener(ObjetsAVieServeur::finAuSol);
      MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
         for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) rendreAttente(p);
         EN_ATTENTE.clear();
      });
      ObjetsAVie.REPONSE_SERVEUR.brancher(ObjetsAVieServeur::reponse);
      ObjetsAVie.brancherSauvetage(ObjetsAVieServeur::sauver);
   }

   private static boolean concerne(Player p) {
      return p instanceof ServerPlayer && !(p instanceof FakePlayer) && !p.isCreative() && !p.isSpectator();
   }

   private static boolean enMatch(ServerPlayer p) {
      return p.getPersistentData().contains(DRAPEAU_EN_MATCH);
   }

   private static String nom(ServerPlayer p) {
      return p.getGameProfile().getName();
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
      if (!p.isAlive() || p.hasDisconnected()) {
         // écran fermé par Minecraft à la réapparition ou à la déconnexion (cases de fabrication, curseur) : jamais au
         // sol, jamais de question (personne pour y répondre) ; dans l'inventaire si possible, sinon gardé
         rendre(p, garde, false);
         return;
      }
      if (p.containerMenu == p.inventoryMenu) {
         // touche de jet en jeu : jamais jeté
         rendre(p, garde, true);
         p.displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.touche",
            "Objet à vie : il ne se jette pas avec la touche de jet."), true);
         return;
      }
      rendreAttente(p);
      mettreDeCote(p, new Attente(garde, null, -1));
      ObjetsAVie.envoyer(p, new PaquetJeterQuestion(garde, false));
   }

   // ------------------------------------------------------------------ chaque seconde, sac à dos
   private static void tick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || !concerne(e.player)) return;
      ServerPlayer p = (ServerPlayer) e.player;
      // une fois par seconde, étalé sur les joueurs (avant : les 41 cases de chaque joueur à chaque tick)
      if ((p.tickCount + p.getId()) % 20 == 0) {
         Inventory inv = p.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.hasTag() && s.getTag().contains(MARQUE_SAC)) s.removeTagKey(MARQUE_SAC);
         }
         if (p.isAlive() && !SORTIS.contains(p.getUUID()) && !enMatch(p) && persiste(p).contains(CLE)) rendreGardes(p);
      }
      AbstractContainerMenu menu = p.containerMenu;
      if (menu == p.inventoryMenu || EN_ATTENTE.containsKey(p.getUUID()) || !MENU_SAC.equals(menu.getClass().getName())) return;
      Inventory inv = p.getInventory();
      for (Slot slot : menu.slots) {
         if (slot.container == inv) continue;
         ItemStack s = slot.getItem();
         if (!ObjetsAVie.estAVie(s) || (s.hasTag() && s.getTag().contains(MARQUE_SAC))) continue;
         ItemStack garde = s.copy();
         slot.set(ItemStack.EMPTY);
         mettreDeCote(p, new Attente(garde, menu, slot.index));
         menu.broadcastChanges();
         ObjetsAVie.envoyer(p, new PaquetJeterQuestion(garde, true));
         return;
      }
   }

   private static void tickServeur(TickEvent.ServerTickEvent e) {
      if (e.phase == TickEvent.Phase.END && !SORTIS.isEmpty()) SORTIS.clear();
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
      Attente a = retirerAttente(p);
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

   /** Met l'objet de côté pendant la question : en mémoire ET dans les données du joueur (un plantage ne le perd pas). */
   private static void mettreDeCote(ServerPlayer p, Attente a) {
      EN_ATTENTE.put(p.getUUID(), a);
      persiste(p).put(CLE_QUESTION, a.objet().save(new CompoundTag()));
   }

   @Nullable
   private static Attente retirerAttente(ServerPlayer p) {
      persiste(p).remove(CLE_QUESTION);
      return EN_ATTENTE.remove(p.getUUID());
   }

   private static void rendreAttente(ServerPlayer p) {
      Attente a = retirerAttente(p);
      if (a != null && !a.objet().isEmpty()) rendre(p, a.objet(), true);
   }

   /**
    * Remet un objet à vie dans l'inventaire (case choisie d'abord, puis n'importe quelle case libre). Plein, joueur mort ou
    * déjà sorti : gardé (rendu dès qu'une case se libère, à la réapparition ou à la connexion). Jamais au sol.
    */
   private static void rendre(ServerPlayer p, ItemStack objet, boolean synchro) {
      if (objet.isEmpty()) return;
      if (!p.isAlive() || SORTIS.contains(p.getUUID())) {
         garder(p, objet, p.isAlive() ? "après la déconnexion" : "mort");
         return;
      }
      Inventory inv = p.getInventory();
      if (inv.getSelected().isEmpty()) {
         inv.setItem(inv.selected, objet);
      } else {
         inv.add(objet);
         if (!objet.isEmpty()) {
            garder(p, objet, "inventaire plein");
            p.displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.garde",
               "Inventaire plein : ton objet à vie est gardé, il revient dès qu'une case se libère."), false);
         }
      }
      if (synchro) synchroniser(p);
   }

   /** Garde l'objet dans les données du joueur (enregistrées avec lui) ; après sa déconnexion : sa boîte aux lettres. */
   private static void garder(ServerPlayer p, ItemStack objet, String raison) {
      if (objet.isEmpty()) return;
      if (p.hasDisconnected() && SORTIS.contains(p.getUUID())) {
         boite(p.getUUID(), List.of(objet.save(new CompoundTag())), raison);
         return;
      }
      CompoundTag persiste = persiste(p);
      ListTag deja = persiste.getList(CLE, Tag.TAG_COMPOUND);
      deja.add(objet.save(new CompoundTag()));
      persiste.put(CLE, deja);
      LOG.info("[a vie] {} : {} gardé ({})", nom(p), objet.getHoverName().getString(), raison);
   }

   /** Le jeu du joueur a déjà retiré l'objet de sa case : on lui renvoie l'inventaire tel qu'il est au serveur. */
   private static void synchroniser(ServerPlayer p) {
      p.inventoryMenu.sendAllDataToRemote();
      if (p.containerMenu != p.inventoryMenu) p.containerMenu.sendAllDataToRemote();
   }

   // ------------------------------------------------------------------ mort
   private static void chute(LivingDropsEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
      if (enMatch(p)) return;
      ListTag garde = new ListTag();
      int dansSacs = 0;
      for (Iterator<ItemEntity> it = e.getDrops().iterator(); it.hasNext(); ) {
         ItemEntity ie = it.next();
         if (ie == null) continue;
         if (ObjetsAVie.estAVie(ie.getItem())) {
            garde.add(ie.getItem().save(new CompoundTag()));
            it.remove();
         } else {
            // sac porté (déjà dans la liste, Curios en HIGHEST) ou sac de l'inventaire : ses objets à vie restent au joueur
            for (ItemStack s : ObjetsAVie.retirerContenuAVie(ie.getItem())) {
               garde.add(s.save(new CompoundTag()));
               dansSacs++;
            }
         }
      }
      // objet tenu au curseur, grille d'artisanat, objet mis de côté (question en cours) ; les cases de fabrication d'un
      // autre écran ouvert (Curios, établi...) sont jetées à la réapparition : jet() les garde alors
      ItemStack curseur = p.containerMenu.getCarried();
      if (ObjetsAVie.estAVie(curseur)) {
         garde.add(curseur.save(new CompoundTag()));
         p.containerMenu.setCarried(ItemStack.EMPTY);
      }
      CraftingContainer grille = p.inventoryMenu.getCraftSlots();
      for (int i = 0; i < grille.getContainerSize(); i++) {
         if (ObjetsAVie.estAVie(grille.getItem(i))) garde.add(grille.removeItemNoUpdate(i).save(new CompoundTag()));
      }
      Attente attente = retirerAttente(p);
      if (attente != null && !attente.objet().isEmpty()) garde.add(attente.objet().save(new CompoundTag()));
      if (garde.isEmpty()) return;
      CompoundTag persiste = persiste(p);
      ListTag deja = persiste.getList(CLE, Tag.TAG_COMPOUND);
      deja.addAll(garde);
      persiste.put(CLE, deja);
      LOG.info("[a vie] {} mort : {} objet(s) à vie gardé(s) pour la réapparition (dont {} retiré(s) d'un sac)", nom(p), garde.size(), dansSacs);
   }

   private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && !e.isEndConquered()) rendreGardes(p);
   }

   // ------------------------------------------------------------------ connexion, déconnexion
   private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
      SORTIS.remove(p.getUUID());
      CompoundTag persiste = persiste(p);
      // question restée sans réponse (plantage du serveur) : l'objet revient
      if (persiste.contains(CLE_QUESTION, Tag.TAG_COMPOUND)) {
         ajouterGardes(persiste, List.of(persiste.getCompound(CLE_QUESTION)));
         persiste.remove(CLE_QUESTION);
         LOG.info("[a vie] {} : objet d'une question restée sans réponse rendu", nom(p));
      }
      // boîte aux lettres : objets sauvés pendant son absence
      List<CompoundTag> boite = lireBoite(p.getUUID());
      if (!boite.isEmpty()) {
         ajouterGardes(persiste, boite);
         BOITES_LUES.add(p.getUUID());
         LOG.info("[a vie] {} : {} objet(s) à vie de sa boîte aux lettres", nom(p), boite.size());
      }
      if (p.isAlive()) rendreGardes(p);
   }

   /**
    * En dernier écouteur de la déconnexion, AVANT que Minecraft n'enregistre le joueur : l'écran ouvert est fermé ici
    * (Mohist le fait juste après, Minecraft seul après l'enregistrement) pour que ses cases de fabrication et son curseur
    * passent par jet() ; les objets à vie du curseur et de la grille 2x2 de l'inventaire reviennent dans l'inventaire ; une
    * question en cours aussi.
    */
   private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
      try {
         if (p.containerMenu != p.inventoryMenu) p.closeContainer();
         if (concerne(p)) {
            ItemStack curseur = p.inventoryMenu.getCarried();
            if (ObjetsAVie.estAVie(curseur)) {
               p.inventoryMenu.setCarried(ItemStack.EMPTY);
               rendre(p, curseur, false);
            }
            CraftingContainer grille = p.inventoryMenu.getCraftSlots();
            for (int i = 0; i < grille.getContainerSize(); i++) {
               if (ObjetsAVie.estAVie(grille.getItem(i))) rendre(p, grille.removeItemNoUpdate(i), false);
            }
         }
         rendreAttente(p);
      } finally {
         SORTIS.add(p.getUUID());
      }
   }

   /** Le joueur vient d'être enregistré : la copie lue de sa boîte aux lettres peut partir (ses objets sont à lui). */
   private static void enregistre(PlayerEvent.SaveToFile e) {
      UUID u = e.getEntity().getUUID();
      if (!BOITES_LUES.remove(u)) return;
      try {
         Files.deleteIfExists(boite(u, true));
      } catch (IOException ex) {
         LOG.error("[a vie] boîte aux lettres lue de {} impossible à effacer (ses objets lui seraient rendus deux fois) : {}", u, ex.toString());
      }
   }

   /** Rend les objets gardés qui tiennent dans l'inventaire ; les autres restent gardés (jamais au sol). */
   private static void rendreGardes(ServerPlayer p) {
      CompoundTag persiste = persiste(p);
      if (!persiste.contains(CLE, Tag.TAG_LIST)) return;
      ListTag liste = persiste.getList(CLE, Tag.TAG_COMPOUND);
      ListTag reste = new ListTag();
      Inventory inv = p.getInventory();
      int n = 0;
      for (int i = 0; i < liste.size(); i++) {
         CompoundTag donnees = liste.getCompound(i);
         ItemStack s = ItemStack.of(donnees);
         if (s.isEmpty()) {
            // objet inconnu du jeu (mod retiré) : ses données restent gardées telles quelles
            reste.add(donnees);
            continue;
         }
         inv.add(s);
         if (s.isEmpty()) {
            n++;
         } else {
            reste.add(s.save(new CompoundTag()));
         }
      }
      if (reste.isEmpty()) persiste.remove(CLE);
      else persiste.put(CLE, reste);
      if (n > 0) {
         synchroniser(p);
         LOG.info("[a vie] {} : {} objet(s) à vie rendu(s){}", nom(p), n, reste.isEmpty() ? "" : ", " + reste.size() + " encore gardé(s)");
         p.displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.rendus",
            "Objets à vie rendus : %s.", String.valueOf(n)), false);
      }
   }

   private static void ajouterGardes(CompoundTag persiste, List<CompoundTag> objets) {
      ListTag deja = persiste.getList(CLE, Tag.TAG_COMPOUND);
      for (CompoundTag o : objets) deja.add(o.copy());
      persiste.put(CLE, deja);
   }

   private static CompoundTag persiste(ServerPlayer p) {
      CompoundTag racine = p.getPersistentData();
      if (!racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) racine.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
      return racine.getCompound(Player.PERSISTED_NBT_TAG);
   }

   // ------------------------------------------------------------------ au sol
   /** Objet à vie (ou sac qui en contient) posé dans le monde : ni feu, ni lave, ni explosion ; on note qui l'a jeté. */
   private static void arriveeAuSol(EntityJoinLevelEvent e) {
      if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ItemEntity ie)) return;
      if (!ObjetsAVie.contientAVie(ie.getItem())) return;
      ie.setInvulnerable(true);
      if (!ie.getPersistentData().hasUUID(PROPRIO_SOL)) {
         Entity lanceur = ie.getOwner();
         if (lanceur instanceof Player j) ie.getPersistentData().putUUID(PROPRIO_SOL, j.getUUID());
      }
   }

   /** Objet à vie jamais ramassé : rendu à celui qui l'a jeté ; sans lanceur connu, il reste au sol. */
   private static void finAuSol(ItemExpireEvent e) {
      ItemEntity ie = e.getEntity();
      if (ie.level().isClientSide()) return;
      ItemStack s = ie.getItem();
      if (!ObjetsAVie.contientAVie(s)) return;
      UUID proprio = ie.getPersistentData().hasUUID(PROPRIO_SOL) ? ie.getPersistentData().getUUID(PROPRIO_SOL) : null;
      if (proprio == null) {
         e.setExtraLife(VIE_EN_PLUS);
         e.setCanceled(true);
         return;
      }
      sauver(proprio, s, "jamais ramassé au sol");
   }

   // ------------------------------------------------------------------ « ne jamais détruire » (autres mods)
   /** Voir {@link ObjetsAVie#neJamaisDetruire}. Fil du serveur. */
   static boolean sauver(UUID proprio, ItemStack objet, String origine) {
      if (objet == null || objet.isEmpty() || proprio == null) return false;
      boolean lui = ObjetsAVie.estAVie(objet);
      List<ItemStack> sauves = new ArrayList<>();
      if (lui) {
         sauves.add(objet.copy());
         objet.setCount(0);
      } else {
         sauves.addAll(ObjetsAVie.retirerContenuAVie(objet));
      }
      if (sauves.isEmpty()) return false;
      MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
      ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(proprio);
      if (p != null && !SORTIS.contains(proprio)) {
         for (ItemStack s : sauves) {
            if (p.isAlive() && !enMatch(p)) rendre(p, s, false);
            else garder(p, s, origine);
         }
         synchroniser(p);
         p.displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.sauve",
            "Objet à vie rendu : %s.", sauves.get(0).getHoverName()), false);
      } else {
         List<CompoundTag> l = new ArrayList<>();
         for (ItemStack s : sauves) l.add(s.save(new CompoundTag()));
         boite(proprio, l, origine);
      }
      LOG.info("[a vie] {} objet(s) à vie de {} sauvé(s) ({}){}", sauves.size(), proprio, origine, p == null ? " : boîte aux lettres" : "");
      return lui;
   }

   // ------------------------------------------------------------------ boîte aux lettres (hors du monde)
   private static Path boite(UUID u, boolean lue) {
      return FMLPaths.GAMEDIR.get().resolve("heraliz_tactique").resolve("objets_a_vie").resolve(u + (lue ? ".lu.dat" : ".dat"));
   }

   /** Ajoute des objets à la boîte aux lettres d'un joueur (écrite tout de suite : fichier temporaire puis renommage). */
   private static void boite(UUID u, List<CompoundTag> objets, String origine) {
      Path f = boite(u, false);
      try {
         ListTag liste = lireListe(f);
         for (CompoundTag o : objets) liste.add(o.copy());
         ecrire(f, liste);
         LOG.info("[a vie] {} objet(s) à vie dans la boîte aux lettres de {} ({})", objets.size(), u, origine);
      } catch (IOException ex) {
         // dernier recours : rien ne doit disparaître sans trace (objets copiés dans le journal pour le staff)
         LOG.error("[a vie] boîte aux lettres de {} impossible à écrire ({}) : objets {} ; {}", u, origine, objets, ex.toString());
      }
   }

   /**
    * Lecture à la connexion : la boîte et une copie lue restée d'un plantage sont réunies dans la copie lue (effacée après
    * le prochain enregistrement du joueur), la boîte est vidée.
    */
   private static List<CompoundTag> lireBoite(UUID u) {
      Path f = boite(u, false);
      Path lue = boite(u, true);
      if (!Files.exists(f) && !Files.exists(lue)) return List.of();
      try {
         ListTag tout = lireListe(lue);
         tout.addAll(lireListe(f));
         ecrire(lue, tout);
         Files.deleteIfExists(f);
         List<CompoundTag> l = new ArrayList<>();
         for (int i = 0; i < tout.size(); i++) l.add(tout.getCompound(i));
         return l;
      } catch (IOException ex) {
         LOG.error("[a vie] boîte aux lettres de {} illisible, gardée telle quelle : {}", u, ex.toString());
         return List.of();
      }
   }

   private static ListTag lireListe(Path f) throws IOException {
      if (!Files.exists(f)) return new ListTag();
      CompoundTag racine = NbtIo.readCompressed(f.toFile());
      return racine.getList("Objets", Tag.TAG_COMPOUND);
   }

   private static void ecrire(Path f, ListTag liste) throws IOException {
      Files.createDirectories(f.getParent());
      CompoundTag racine = new CompoundTag();
      racine.put("Objets", liste);
      Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
      NbtIo.writeCompressed(racine, tmp.toFile());
      try {
         Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException atomique) {
         Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
      }
   }
}
