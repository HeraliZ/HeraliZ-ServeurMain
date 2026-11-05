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
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Objets à vie (voir {@link ObjetsAVie}), côté serveur. Règle du propriétaire (03/11, remplace celle du 02/11) : un
 * objet à vie est protégé tant qu'il est dans l'inventaire de celui qui l'a (inventaire, barre, armure, Curios, curseur,
 * grille 2x2) ; il y est toujours soit dans un inventaire, soit dans les données du joueur (gardées et enregistrées avec
 * lui par Minecraft), soit dans sa boîte aux lettres hors du monde : jamais seulement en mémoire. Le sortir de
 * l'inventaire (contenant ou sol) demande une confirmation ; confirmé, il n'est plus à personne et ne revient plus seul.
 * <ul>
 *     <li>mort : ils ne tombent pas (ni au sol ni sur le corps de HeraliZ Utils, qui range les objets tombés plus tard,
 *     en LOWEST) : gardés dans les données persistantes du joueur ({@code PlayerPersisted.HeralizObjetsAVie}, copiées
 *     par Forge à la réapparition, donc aussi après un redémarrage) et rendus à la réapparition ou à la connexion. Ceux
 *     rangés (avec confirmation) dans un sac porté ou dans un sac de l'inventaire suivent le sac (03/11 ; avant : sortis
 *     du sac et gardés). Pas en match (drapeau {@code heralisz:en_match} : les kits des matchs sont gérés par le match) ;</li>
 *     <li>touche de jet (aucun menu ouvert) : rien n'est jeté, l'objet reste dans la main, sans message
 *     (03/11) ;</li>
 *     <li>jet depuis un inventaire ouvert : l'objet est mis de côté (dans les données du joueur, {@code
 *     HeralizObjetsAVieQuestion} : il survit à un plantage) et le joueur reçoit « Jeter cet objet ? ». Oui : il tombe,
 *     n'est plus à personne et disparaît au bout du temps normal des objets au sol (03/11 ; avant : rendu au bout de
 *     5 min) ; celui qui le ramasse l'a dans son inventaire, il est donc protégé pour lui. Non (ou écran fermé) : il
 *     revient dans l'inventaire ;</li>
 *     <li>rangé dans n'importe quel contenant (03/11 : coffre, sac à dos, caisse, atelier, établi... : tout écran autre
 *     que l'inventaire du joueur et l'écran de Curios), par n'importe quel clic (clic, glisser, Maj + clic, touches 1 à 9,
 *     double clic) : {@link #avantClic} / {@link #apresClic} (mixin commun {@code ObjetsAVieClicMixin}) le voient, l'en
 *     ressortent et le mettent de côté de même, avec « Mettre dans le sac ? » ou « Ranger cet objet ici ? ». Oui : il va
 *     dans sa case (sinon une case libre du même contenant, sinon rendu), marqué {@code HeralizSacOk} (« sorti avec
 *     confirmation » : il se déplace ensuite dans le contenant sans nouvelle question ; marque retirée dès qu'il revient
 *     dans un inventaire). Non : rendu. Caisses qui disparaissent avec leur contenu ({@link #CONTENANTS_TEMPORAIRES}) :
 *     refusé, rendu ;</li>
 *     <li>présentoirs (cadre, porte-armure, mannequin, allay) : un objet à vie ne s'y pose pas (pas de question possible) ;</li>
 *     <li>inventaire plein quand un objet revient : il est gardé (comme à la mort) et rendu dès qu'une case se libère
 *     (vérifié chaque seconde), jamais posé au sol ni échangé contre un autre objet ;</li>
 *     <li>déconnexion (02/11) : Minecraft enregistre le joueur PUIS jette ce qu'il tenait au curseur et ce qui était dans
 *     les cases de fabrication (inventaire de base, écran de Curios, établi, recycleur...). En dernier écouteur de la
 *     déconnexion (avant l'enregistrement), l'écran ouvert est fermé ici et les objets à vie du curseur et de la grille
 *     2x2 reviennent dans l'inventaire (sinon gardés) ; une question en cours aussi. Ce qui arriverait encore après part
 *     dans la boîte aux lettres ;</li>
 *     <li>pendant la mort ou la déconnexion, un objet à vie jeté par un écran qui se ferme est gardé, jamais posé ;</li>
 *     <li>au sol : invulnérable (feu, lave, explosions, cactus : jamais détruit par erreur), mais il disparaît au bout du
 *     temps normal, comme le reste (03/11 ; avant : rendu au bout de 5 min à celui qui l'avait jeté) ; un sac au sol
 *     aussi, avec ce qu'il contient ;</li>
 *     <li>boîte aux lettres ({@code heraliz_tactique/objets_a_vie/<uuid>.dat}, hors du monde) : objets sauvés pour un
 *     joueur hors ligne ({@link ObjetsAVie#neJamaisDetruire}). Lue à la connexion ; la copie lue n'est effacée qu'après
 *     l'enregistrement suivant du joueur (un plantage entre les deux ne perd ni ne double rien).</li>
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

   /**
    * Objet mis de côté en attendant la réponse du joueur. {@code menu} nul : question du jet au sol ; sinon question d'un
    * contenant (case {@code cas} du menu, conteneur {@code contenant}, {@code ou} : sac ou autre contenant).
    */
   private record Attente(ItemStack objet, @Nullable AbstractContainerMenu menu, int cas, @Nullable Container contenant, byte ou) {
   }

   private static final Map<UUID, Attente> EN_ATTENTE = new HashMap<>();
   /** Joueurs dont la déconnexion a déjà été traitée (vidé au tick suivant) : ce qui arrive après va dans leur boîte. */
   private static final Set<UUID> SORTIS = new HashSet<>();
   /** Boîtes aux lettres lues à la connexion : la copie lue est effacée après le prochain enregistrement du joueur. */
   private static final Set<UUID> BOITES_LUES = new HashSet<>();
   private static final String MENU_SAC = "com.mrcrayfish.backpacked.inventory.container.BackpackContainerMenu";
   /**
    * Marque « sorti de l'inventaire avec confirmation » d'un objet à vie rangé dans un contenant (nom d'origine : le sac à
    * dos, 02/11 ; ignorée par les échanges de HeraliZ Quêtes). Posée sur la confirmation et sur tout objet à vie déjà dans
    * un contenant quand un joueur y clique ; retirée dès que l'objet est dans un inventaire de joueur (il est à lui).
    */
   private static final String MARQUE = "HeralizSacOk";
   /**
    * Caisses qui disparaissent avec tout leur contenu au bout d'un temps (03/11) : un objet à vie n'y entre pas (il y
    * serait détruit) : caisse de ravitaillement des territoires (HeraliZ Clans, 15 min) et caisse de largage posée en bloc
    * (frappes aériennes de HeraliZ Utils). Les autres contenants qui se vident tout seuls refusent déjà tout dépôt
    * (conteneurs de loot, corps, caisse du largage public, recycleurs).
    */
   static final Set<String> CONTENANTS_TEMPORAIRES = Set.of(
      "fr.heralisz.clans.territory.CaisseRavitaillementBlockEntity",
      "fr.heraliz.airstrike.block.SupplyCrateBlockEntity");
   /** Écrans de Curios : comme l'inventaire, ils ne montrent que les affaires du joueur (jamais de question). */
   private static final ClassValue<Boolean> ECRAN_CURIOS = new ClassValue<>() {
      @Override
      protected Boolean computeValue(Class<?> type) {
         for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().startsWith("top.theillusivec4.curios.")) return true;
            for (Class<?> i : c.getInterfaces()) {
               if (i.getName().startsWith("top.theillusivec4.curios.")) return true;
            }
         }
         return false;
      }
   };
   /** Jet confirmé en cours (le serveur jette lui-même : l'évènement de jet ne doit pas le reprendre). */
   private static boolean jetConfirme;
   /** Clic en cours (fil du serveur, un clic à la fois) : joueur, écran, objets à vie non marqués de son côté avant. */
   @Nullable
   private static ServerPlayer clicJoueur;
   @Nullable
   private static AbstractContainerMenu clicMenu;
   private static int clicAvant;

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
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ObjetsAVieServeur::presentoir);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ObjetsAVieServeur::presentoirPrecis);
      MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
         for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) rendreAttente(p);
         EN_ATTENTE.clear();
      });
      ObjetsAVie.REPONSE_SERVEUR.brancher(ObjetsAVieServeur::reponse);
      ObjetsAVie.brancherSauvetage(ObjetsAVieServeur::sauver);
      ObjetsAVie.brancherClics(new ObjetsAVie.Clics() {
         @Override
         public void avant(AbstractContainerMenu menu, ServerPlayer joueur) {
            avantClic(menu, joueur);
         }

         @Override
         public void apres(AbstractContainerMenu menu, ServerPlayer joueur) {
            apresClic(menu, joueur);
         }
      });
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
         // touche de jet en jeu : jamais jeté, sans message (03/11, choix du propriétaire : on le jette seulement depuis
         // l'inventaire, avec la confirmation)
         rendre(p, garde, true);
         return;
      }
      rendreAttente(p);
      mettreDeCote(p, new Attente(garde, null, -1, null, ObjetsAVie.OU_SOL));
      ObjetsAVie.envoyer(p, new PaquetJeterQuestion(garde, ObjetsAVie.OU_SOL));
   }

   // ------------------------------------------------------------------ chaque seconde
   private static void tick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || !concerne(e.player)) return;
      ServerPlayer p = (ServerPlayer) e.player;
      // une fois par seconde, étalé sur les joueurs (avant : les 41 cases de chaque joueur à chaque tick) : un objet à
      // vie revenu dans l'inventaire (ramassé au sol, repris dans un coffre) est de nouveau à lui (marque retirée)
      if ((p.tickCount + p.getId()) % 20 == 0) {
         Inventory inv = p.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) demarquer(inv.getItem(i));
         if (p.isAlive() && !SORTIS.contains(p.getUUID()) && !enMatch(p)
            && p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(CLE)) rendreGardes(p);
      }
   }

   private static void tickServeur(TickEvent.ServerTickEvent e) {
      if (e.phase == TickEvent.Phase.END && !SORTIS.isEmpty()) SORTIS.clear();
   }

   private static boolean marque(ItemStack s) {
      return s.hasTag() && s.getTag().contains(MARQUE);
   }

   private static void demarquer(ItemStack s) {
      if (!s.isEmpty() && marque(s)) s.removeTagKey(MARQUE);
   }

   // ------------------------------------------------------------------ rangé dans un contenant (03/11)
   /** L'inventaire du joueur ou l'écran de Curios : seulement ses propres affaires, jamais de question. */
   private static boolean ecranDuJoueur(AbstractContainerMenu menu, ServerPlayer p) {
      return menu == p.inventoryMenu || ECRAN_CURIOS.get(menu.getClass());
   }

   /** Objets à vie NON marqués de son côté : son inventaire (41 cases) et son curseur. */
   private static int aVieDuJoueur(AbstractContainerMenu menu, Inventory inv) {
      int n = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (!s.isEmpty() && !marque(s) && ObjetsAVie.estAVie(s)) n += s.getCount();
      }
      ItemStack curseur = menu.getCarried();
      if (!curseur.isEmpty() && !marque(curseur) && ObjetsAVie.estAVie(curseur)) n += curseur.getCount();
      return n;
   }

   /**
    * Avant un clic dans l'écran d'un contenant (fil du serveur). Ce qui est dans l'inventaire du joueur est à lui (marque
    * retirée) ; s'il en a, ce qui est déjà dans les cases du contenant est marqué « déjà rangé » : après le clic, un objet
    * à vie non marqué dans une case du contenant ne peut venir que de son inventaire ou de son curseur. Rien d'autre :
    * un joueur sans objet à vie ne coûte qu'un tour de ses 41 cases par clic.
    */
   static void avantClic(AbstractContainerMenu menu, ServerPlayer p) {
      clicJoueur = null;
      clicMenu = null;
      if (!concerne(p) || ecranDuJoueur(menu, p)) return;
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) demarquer(inv.getItem(i));
      int avant = aVieDuJoueur(menu, inv);
      if (avant == 0) return;
      for (Slot slot : menu.slots) {
         if (slot.container == inv || slot.container instanceof ResultContainer) continue;
         ItemStack s = slot.getItem();
         if (!s.isEmpty() && !marque(s) && ObjetsAVie.estAVie(s)) s.getOrCreateTag().putBoolean(MARQUE, true);
      }
      clicJoueur = p;
      clicMenu = menu;
      clicAvant = avant;
   }

   /**
    * Après le clic : si des objets à vie ont quitté son côté (inventaire, curseur) et qu'un objet à vie non marqué est dans
    * une case du contenant où le joueur peut poser, c'est le sien : il en ressort, mis de côté avec la question (un seul ;
    * un deuxième, glissé dans la même passe, revient dans l'inventaire). Jet (Q, Ctrl + Q, clic hors de l'écran) : déjà
    * mis de côté par jet(), rien à faire ici. Cases de résultat (artisanat, enclume...) jamais prises : rien n'y est posé
    * par le joueur, et un objet fabriqué n'est pas à lui.
    */
   static void apresClic(AbstractContainerMenu menu, ServerPlayer p) {
      if (clicJoueur != p || clicMenu != menu) return;
      clicJoueur = null;
      clicMenu = null;
      Inventory inv = p.getInventory();
      int partis = clicAvant - aVieDuJoueur(menu, inv);
      if (partis <= 0) return;
      boolean question = false;
      boolean temporaire = false;
      for (Slot slot : menu.slots) {
         if (partis <= 0) break;
         if (slot.container == inv || slot.container instanceof ResultContainer) continue;
         ItemStack s = slot.getItem();
         if (s.isEmpty() || marque(s) || !ObjetsAVie.estAVie(s) || !slot.mayPlace(s)) continue;
         int avant = s.getCount();
         ItemStack objet = slot.remove(Math.min(partis, avant));
         ItemStack ici = slot.getItem();
         int reste = ici.isEmpty() || marque(ici) || !ObjetsAVie.estAVie(ici) ? 0 : ici.getCount();
         // contenant qui ne rend rien (ou rend une copie) : l'objet y reste, rien n'est copié
         if (objet.isEmpty() || reste >= avant) continue;
         partis -= objet.getCount();
         if (CONTENANTS_TEMPORAIRES.contains(slot.container.getClass().getName())) {
            rendre(p, objet, false);
            temporaire = true;
         } else if (!question) {
            question = true;
            byte ou = MENU_SAC.equals(menu.getClass().getName()) ? ObjetsAVie.OU_SAC : ObjetsAVie.OU_CONTENANT;
            rendreAttente(p);
            mettreDeCote(p, new Attente(objet, menu, slot.index, slot.container, ou));
            ObjetsAVie.envoyer(p, new PaquetJeterQuestion(objet.copy(), ou));
         } else {
            rendre(p, objet, false);
         }
      }
      if (temporaire) {
         p.displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.temporaire",
            "Objet à vie : pas dans cette caisse, elle disparaît avec son contenu."), true);
      }
   }

   /**
    * « Oui, le ranger » : dans sa case si elle est libre (ou tient déjà le même objet rangé), sinon une case libre du même
    * contenant, sinon rendu. Marqué : il n'est plus à personne et ne revient plus tout seul.
    */
   private static void ranger(ServerPlayer p, Attente a) {
      ItemStack objet = a.objet();
      objet.getOrCreateTag().putBoolean(MARQUE, true);
      AbstractContainerMenu menu = p.containerMenu;
      if (menu == a.menu() && menu.stillValid(p)) {
         Slot voulu = a.cas() >= 0 && a.cas() < menu.slots.size() ? menu.slots.get(a.cas()) : null;
         if (voulu != null && voulu.container == a.contenant()) objet = voulu.safeInsert(objet);
         for (Slot slot : menu.slots) {
            if (objet.isEmpty()) break;
            if (slot != voulu && slot.container == a.contenant() && slot.container != p.getInventory() && !slot.hasItem()) {
               objet = slot.safeInsert(objet);
            }
         }
         menu.broadcastChanges();
      }
      if (!objet.isEmpty()) rendre(p, objet, true);
   }

   private static void reponse(PaquetJeterReponse.Recue r) {
      ServerPlayer p = r.joueur();
      Attente a = retirerAttente(p);
      if (a == null || a.objet().isEmpty()) return;
      ItemStack objet = a.objet();
      if (a.menu() != null && r.jeter() && p.isAlive()) {
         ranger(p, a);
         return;
      }
      if (!r.jeter() || !p.isAlive()) {
         rendre(p, objet, true);
         return;
      }
      // au sol, il n'est plus à personne (03/11) : il disparaît au bout du temps normal, celui qui le ramasse le garde
      demarquer(objet);
      jetConfirme = true;
      try {
         ItemEntity tombe = p.drop(objet, false, true);
         if (tombe == null) rendre(p, objet, true);
      } finally {
         jetConfirme = false;
      }
      synchroniser(p);
   }

   // ------------------------------------------------------------------ présentoirs (03/11)
   /** Cadre, porte-armure, mannequin de l'Armurerie, allay : un objet à vie ne s'y pose pas (aucune question possible). */
   private static boolean refusePresentoir(Player joueur, Entity cible, ItemStack enMain) {
      if (!concerne(joueur) || !ObjetsAVie.estAVie(enMain)) return false;
      if (cible instanceof ItemFrame cadre) return cadre.getItem().isEmpty();
      if (cible instanceof Allay) return true;
      // porte-armure de Minecraft : l'objet s'y pose même accroupi ; mannequin (autre mod) : accroupi, il ouvre son écran
      if (cible instanceof ArmorStand) return cible.getClass() == ArmorStand.class || !joueur.isSecondaryUseActive();
      return false;
   }

   private static void refuserPresentoir(PlayerInteractEvent e) {
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.FAIL);
      e.getEntity().displayClientMessage(Component.translatableWithFallback("heraliz_tactique.a_vie.presentoir",
         "Objet à vie : il ne se pose pas sur un présentoir."), true);
   }

   private static void presentoir(PlayerInteractEvent.EntityInteract e) {
      if (!e.getLevel().isClientSide() && refusePresentoir(e.getEntity(), e.getTarget(), e.getItemStack())) refuserPresentoir(e);
   }

   private static void presentoirPrecis(PlayerInteractEvent.EntityInteractSpecific e) {
      if (!e.getLevel().isClientSide() && refusePresentoir(e.getEntity(), e.getTarget(), e.getItemStack())) refuserPresentoir(e);
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
      demarquer(objet); // de nouveau dans son inventaire (ou gardé pour lui) : à lui
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
      // inventaire, armure, Curios (déjà dans la liste, Curios en HIGHEST). Un objet à vie rangé dans un sac (porté ou
      // dans l'inventaire) suit le sac (03/11, règle du propriétaire : rangé avec confirmation, il n'est plus protégé)
      for (Iterator<ItemEntity> it = e.getDrops().iterator(); it.hasNext(); ) {
         ItemEntity ie = it.next();
         if (ie != null && ObjetsAVie.estAVie(ie.getItem())) {
            garde.add(ie.getItem().save(new CompoundTag()));
            it.remove();
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
      LOG.info("[a vie] {} mort : {} objet(s) à vie gardé(s) pour la réapparition", nom(p), garde.size());
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
   /**
    * Objet à vie (ou sac qui en contient) posé dans le monde : ni feu, ni lave, ni explosion (jamais détruit par erreur).
    * Il disparaît au bout du temps normal des objets au sol, comme le reste, et n'est plus rendu à personne (03/11, règle
    * du propriétaire ; avant : rendu au bout de 5 min à celui qui l'avait jeté). Celui qui le ramasse l'a dans son
    * inventaire : il est à lui, protégé.
    */
   private static void arriveeAuSol(EntityJoinLevelEvent e) {
      if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ItemEntity ie)) return;
      if (ObjetsAVie.contientAVie(ie.getItem())) ie.setInvulnerable(true);
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
            "Objet à vie rendu : %s.", fr.heraliz.noyau.api.NomsObjets.nom(sauves.get(0))), false);
      } else {
         List<CompoundTag> l = new ArrayList<>();
         for (ItemStack s : sauves) l.add(s.save(new CompoundTag()));
         boite(proprio, l, origine);
      }
      LOG.info("[a vie] {} objet(s) à vie de {} sauvé(s) ({}){}", sauves.size(), proprio, origine, p == null ? " : boîte aux lettres" : "");
      return lui;
   }

   /**
    * Wipe de la réouverture (05/11, HeraliZ Quêtes par réflexion : ne jamais renommer ni changer la signature) : tout ce
    * qui est gardé pour ce joueur part, sans rien rendre : objet mis de côté pendant une question, objets gardés dans ses
    * données (s'il est connecté) et boîte aux lettres hors du monde (et sa copie lue). Renvoie le nombre d'objets oubliés.
    * Fil du serveur.
    */
   public static int oublier(UUID u) {
      if (u == null) return 0;
      int n = EN_ATTENTE.remove(u) != null ? 1 : 0;
      BOITES_LUES.remove(u);
      for (boolean lue : new boolean[]{false, true}) {
         Path f = boite(u, lue);
         if (!Files.exists(f)) continue;
         try {
            n += lireListe(f).size();
         } catch (IOException ex) {
            // illisible : effacé quand même (wipe)
         }
         try {
            Files.deleteIfExists(f);
         } catch (IOException ex) {
            LOG.error("[a vie] wipe : boîte aux lettres {} impossible à effacer : {}", f, ex.toString());
         }
      }
      MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
      ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(u);
      if (p != null) {
         CompoundTag per = persiste(p);
         n += per.getList(CLE, Tag.TAG_COMPOUND).size();
         per.remove(CLE);
         per.remove(CLE_QUESTION);
      }
      if (n > 0) LOG.warn("[a vie] wipe de la réouverture : {} objet(s) à vie gardé(s) pour {} effacé(s)", n, u);
      return n;
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
