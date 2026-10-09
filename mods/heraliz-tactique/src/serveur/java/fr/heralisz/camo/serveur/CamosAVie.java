package fr.heralisz.camo.serveur;

import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.KillCounter;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.CollectionCamoPacket;
import fr.heralisz.camo.network.CollectionSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Règles des camos à vie (côté serveur).
 * <ul>
 *    <li>Clic droit avec un objet camo : le camo entre dans la collection du joueur, pour toujours (l'objet est consommé).
 *    Les camos du Pack-a-Punch restent réservés au mode zombies.</li>
 *    <li>Onglet Skin : un camo de la collection se pose sur l'arme tenue. Il ne vaut que sur UNE arme à la fois (jeton de
 *    pose : le poser ailleurs l'enlève de l'ancienne arme) et seulement entre les mains de son propriétaire : une arme qui
 *    passe à un autre joueur (ramassée, volée, donnée) le perd dans la seconde.</li>
 *    <li>Mort avec l'arme (mort définitive, pas la mise à terre ni un match) : le camo quitte l'arme avant qu'elle tombe
 *    sur le corps, et le joueur doit attendre {@link #RECHARGE_MS} avant de pouvoir le remettre.</li>
 * </ul>
 * Un camo posé depuis un objet reste définitif sur l'arme (ReceptionServeur), comme avant.
 */
public final class CamosAVie {
   /** Délai après une mort avec l'arme avant de pouvoir remettre le camo (choix du propriétaire : 15 min). */
   static final long RECHARGE_MS = 15 * 60_000L;
   /** Vérification des armes des joueurs (ticks). */
   private static final int VERIFICATION_TICKS = 20;
   /** Profondeur de la recherche dans les objets qui en contiennent d'autres (sacs, boîtes). */
   private static final int PROFONDEUR = 3;

   private static int ticks;

   private CamosAVie() {
   }

   // ------------------------------------------------------------------ collection
   /** Clic droit avec un objet camo ({@code CamoItem.AJOUTER_COLLECTION}) ; vrai si le camo vient d'entrer dans la collection. */
   static boolean ajouter(ServerPlayer p, Camo camo) {
      if (camo.isPackAPunch()) {
         p.displayClientMessage(Component.translatable("msg.heralisz_camo.pap_only").withStyle(ChatFormatting.RED), true);
         return false;
      }
      CollectionCamos.Joueur j = CollectionCamos.get(p.getUUID());
      if (!j.camos.add(camo)) {
         p.displayClientMessage(Component.translatable("msg.heralisz_camo.already", nom(camo)).withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      CollectionCamos.changer();
      envoyer(p);
      p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
      p.displayClientMessage(Component.translatable("msg.heralisz_camo.collected", nom(camo)).withStyle(ChatFormatting.GRAY), true);
      return true;
   }

   /** Envoie au joueur sa collection et ses délais en cours. */
   static void envoyer(ServerPlayer p) {
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      List<String> ids = new ArrayList<>();
      Map<String, Long> recharges = new HashMap<>();
      if (j != null) {
         long now = System.currentTimeMillis();
         for (Camo c : j.camos) ids.add(c.id);
         j.recharges.forEach((c, fin) -> {
            if (fin > now) recharges.put(c.id, fin - now);
         });
      }
      CamoNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CollectionSyncPacket(ids, recharges));
   }

   // ------------------------------------------------------------------ onglet Skin
   static void recevoir(CamoNetwork.Recu<CollectionCamoPacket> r) {
      ServerPlayer p = r.joueur();
      if (p == null || !p.isAlive() || p.isSpectator()) return;
      CollectionCamoPacket m = r.paquet();
      if (m.action() == CollectionCamoPacket.APPLIQUER) {
         Camo camo = Camo.byId(m.camo());
         if (camo != null) appliquer(p, camo);
      } else if (m.action() == CollectionCamoPacket.RETIRER) {
         retirer(p);
      }
   }

   private static void appliquer(ServerPlayer p, Camo camo) {
      if (refuse(p)) return;
      ItemStack gun = p.getMainHandItem();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null || KnifeSkins.of(gun) != null || camo.isPackAPunch()) return;
      UUID u = p.getUUID();
      CollectionCamos.Joueur j = CollectionCamos.trouver(u);
      if (j == null || !j.camos.contains(camo)) return; // pas dans sa collection (client en retard ou trafiqué)
      if (!camo.fits(iGun.getGunId(gun))) {
         p.displayClientMessage(Component.translatable("msg.heralisz_camo.wrong_gun",
            Component.translatable("item.heralisz_camo.camo.gun." + camo.id)).withStyle(ChatFormatting.RED), true);
         return;
      }
      Camo actuel = CamoData.get(gun);
      UUID proprio = CamoData.owner(gun);
      if (actuel != null && proprio == null) {
         p.displayClientMessage(Component.translatable("msg.heralisz_camo.permanent_on").withStyle(ChatFormatting.RED), true);
         return; // camo définitif (posé depuis un objet)
      }
      long reste = j.recharges.getOrDefault(camo, 0L) - System.currentTimeMillis();
      if (reste > 0) {
         p.displayClientMessage(Component.translatable("msg.heralisz_camo.cooldown", nom(camo), minutes(reste)).withStyle(ChatFormatting.RED), true);
         return;
      }
      j.recharges.remove(camo);
      if (actuel == camo && u.equals(proprio) && valide(j, camo, CamoData.token(gun))) return; // déjà dessus
      if (actuel != null && u.equals(proprio) && valide(j, actuel, CamoData.token(gun))) {
         j.jetons.remove(actuel); // l'autre camo à vie de cette arme redevient libre
      }
      long jeton = nouveauJeton();
      j.jetons.put(camo, jeton);
      CamoData.setLifetime(gun, camo, u, jeton);
      verifier(p); // une autre arme de l'inventaire qui le portait le perd tout de suite
      CollectionCamos.changer();
      p.getInventory().setChanged();
      p.inventoryMenu.broadcastChanges();
      p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
      p.displayClientMessage(Component.translatable("msg.heralisz_camo.applied_life", nom(camo)).withStyle(ChatFormatting.GRAY), true);
   }

   private static void retirer(ServerPlayer p) {
      if (refuse(p)) return;
      ItemStack gun = p.getMainHandItem();
      Camo actuel = CamoData.get(gun);
      if (IGun.getIGunOrNull(gun) == null || actuel == null || !p.getUUID().equals(CamoData.owner(gun))) return;
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      if (j != null && valide(j, actuel, CamoData.token(gun))) {
         j.jetons.remove(actuel);
         CollectionCamos.changer();
      }
      CamoData.clear(gun);
      p.getInventory().setChanged();
      p.inventoryMenu.broadcastChanges();
      p.level().playSound(null, p.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.8F, 1.0F);
      p.displayClientMessage(Component.translatable("msg.heralisz_camo.removed", nom(actuel)).withStyle(ChatFormatting.GRAY), true);
   }

   /** Pas en match (armes prêtées) ni à terre (retirer le camo avant de mourir éviterait le délai). */
   private static boolean refuse(ServerPlayer p) {
      if (p.getPersistentData().contains(KillCounter.EN_MATCH)) {
         p.displayClientMessage(Component.translatable("msg.heralisz_camo.not_in_match").withStyle(ChatFormatting.RED), true);
         return true;
      }
      return Compat.aTerre(p);
   }

   /**
    * Un camo définitif (objet) remplace le camo à vie que l'arme portait : s'il était au joueur, son camo redevient libre.
    * Appelé par ReceptionServeur avant de poser le camo de l'objet.
    */
   static void liberer(ServerPlayer p, ItemStack gun) {
      Camo actuel = CamoData.get(gun);
      if (actuel == null || !p.getUUID().equals(CamoData.owner(gun))) return;
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      if (j != null && valide(j, actuel, CamoData.token(gun))) {
         j.jetons.remove(actuel);
         CollectionCamos.changer();
      }
   }

   // ------------------------------------------------------------------ mort
   /**
    * Mort définitive d'un joueur (LivingDropsEvent, HIGH : après les objets de Curios, avant les corps et les sacs) : ses
    * camos à vie quittent les armes qui tombent (y compris dans un sac porté) et ne peuvent pas être remis avant
    * {@link #RECHARGE_MS}. La mise à terre (Réanimation) annule la mort : rien ne se passe. Pas en match (armes prêtées).
    */
   static void chute(LivingDropsEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
      if (p.getPersistentData().contains(KillCounter.EN_MATCH)) return;
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      if (j == null) return;
      Set<Camo> perdus = EnumSet.noneOf(Camo.class);
      for (ItemEntity ie : e.getDrops()) {
         if (ie != null) depouiller(ie.getItem(), p.getUUID(), j, perdus);
      }
      // objet tenu au curseur et grille d'artisanat : ils tombent plus tard, hors de cette liste
      depouiller(p.containerMenu.getCarried(), p.getUUID(), j, perdus);
      Container grille = p.inventoryMenu.getCraftSlots();
      for (int i = 0; i < grille.getContainerSize(); i++) depouiller(grille.getItem(i), p.getUUID(), j, perdus);
      if (perdus.isEmpty()) return;
      long fin = System.currentTimeMillis() + RECHARGE_MS;
      MutableComponent noms = Component.empty();
      boolean premier = true;
      for (Camo c : perdus) {
         j.recharges.put(c, fin);
         j.jetons.remove(c);
         if (!premier) noms.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
         noms.append(nom(c));
         premier = false;
      }
      CollectionCamos.changer();
      envoyer(p);
      p.sendSystemMessage(Component.translatable("msg.heralisz_camo.lost", noms, RECHARGE_MS / 60_000L).withStyle(ChatFormatting.GRAY));
   }

   /** Retire les camos à vie de {@code joueur} de cet objet et des objets qu'il contient ; note ceux qui étaient valables. */
   private static void depouiller(ItemStack stack, UUID joueur, CollectionCamos.Joueur j, Set<Camo> perdus) {
      if (!stack.isEmpty() && stack.getTag() != null) depouiller(stack.getTag(), joueur, j, perdus, 0);
   }

   private static void depouiller(CompoundTag tag, UUID joueur, CollectionCamos.Joueur j, Set<Camo> perdus, int profondeur) {
      if (tag.contains(CamoData.KEY, Tag.TAG_STRING) && tag.hasUUID(CamoData.OWNER) && joueur.equals(tag.getUUID(CamoData.OWNER))) {
         Camo c = Camo.byId(tag.getString(CamoData.KEY));
         if (c != null && valide(j, c, tag.getLong(CamoData.TOKEN))) perdus.add(c);
         tag.remove(CamoData.KEY);
         tag.remove(CamoData.OWNER);
         tag.remove(CamoData.TOKEN);
      }
      if (profondeur >= PROFONDEUR) return;
      // contenu d'un sac (HeraliZ Backpack : « Items ») ou d'une boîte (« BlockEntityTag.Items »)
      contenu(tag.getList("Items", Tag.TAG_COMPOUND), joueur, j, perdus, profondeur);
      if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
         contenu(tag.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND), joueur, j, perdus, profondeur);
      }
   }

   private static void contenu(ListTag items, UUID joueur, CollectionCamos.Joueur j, Set<Camo> perdus, int profondeur) {
      for (int i = 0; i < items.size(); i++) {
         CompoundTag item = items.getCompound(i);
         if (item.contains("tag", Tag.TAG_COMPOUND)) depouiller(item.getCompound("tag"), joueur, j, perdus, profondeur + 1);
      }
   }

   // ------------------------------------------------------------------ vérification des armes
   /** Chaque seconde : les armes de chaque joueur ne gardent que les camos à vie qui sont les siens et encore valables. */
   static void tick(MinecraftServer server) {
      if (++ticks < VERIFICATION_TICKS) return;
      ticks = 0;
      for (ServerPlayer p : server.getPlayerList().getPlayers()) verifier(p);
   }

   /** Inventaire (mains, armure comprises) et objet tenu au curseur. */
   static void verifier(ServerPlayer p) {
      Inventory inv = p.getInventory();
      boolean change = false;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         change |= verifier(p, inv.getItem(i));
      }
      change |= verifier(p, p.containerMenu.getCarried());
      if (change) {
         inv.setChanged();
         p.inventoryMenu.broadcastChanges();
         p.containerMenu.broadcastChanges();
      }
   }

   private static boolean verifier(ServerPlayer p, ItemStack stack) {
      UUID proprio = CamoData.owner(stack);
      if (proprio == null) return false;
      Camo camo = CamoData.get(stack);
      if (proprio.equals(p.getUUID())) {
         CollectionCamos.Joueur j = CollectionCamos.trouver(proprio);
         if (camo != null && j != null && j.camos.contains(camo) && valide(j, camo, CamoData.token(stack))) return false;
         // posé depuis sur une autre arme, retiré de la collection, ou arme qui avait déjà quitté le joueur à sa mort
         CamoData.clear(stack);
         if (camo != null) {
            p.displayClientMessage(Component.translatable("msg.heralisz_camo.moved", nom(camo)).withStyle(ChatFormatting.GRAY), true);
         }
         return true;
      }
      CamoData.clear(stack);
      p.displayClientMessage(Component.translatable("msg.heralisz_camo.not_yours").withStyle(ChatFormatting.GRAY), true);
      return true;
   }

   // ------------------------------------------------------------------ outils
   private static boolean valide(CollectionCamos.Joueur j, Camo camo, long jeton) {
      Long actuel = j.jetons.get(camo);
      return actuel != null && actuel == jeton;
   }

   private static long nouveauJeton() {
      long t;
      do {
         t = ThreadLocalRandom.current().nextLong();
      } while (t == 0L);
      return t;
   }

   static Component nom(Camo camo) {
      return Component.translatable(camo.nameKey()).withStyle(camo.tier.color);
   }

   /** Minutes restantes, arrondies au-dessus (au moins 1). */
   static long minutes(long ms) {
      return Math.max(1L, (ms + 59_999L) / 60_000L);
   }
}
