package fr.heraliz.tactique.avie;

import fr.heralisz.camo.Camo;
import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.tactical.item.MeleeItem;
import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.onglets.Couteaux;
import fr.heraliz.tactique.pont.Crochet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Objets « à vie » (21/10, choix du propriétaire) : tous les couteaux (couteaux TaCZ et armes de mêlée de LR, toutes
 * avec skin) et les skins à vie (objets « Skin X · Séraphin » et « Skin X · Chaînes de l'Enfer »). Gardés à la mort,
 * jamais jetés par la touche de jet, et jetés depuis un inventaire seulement après « Jeter cet objet ? » Oui. Règles
 * décidées par le serveur (ObjetsAVieServeur) ; le joueur n'affiche que la question (EcranJeter). Canal
 * heraliz_tactique:a_vie, même version exigée des deux côtés.
 * <p>Règle du propriétaire (02/11) : un objet à vie n'est JAMAIS perdu, sauf si un admin le supprime. Tout code qui va
 * détruire, effacer ou jeter au sol un objet (conteneur nettoyé, recycleur, caisse, atelier, objet qui ne rentre pas...)
 * appelle d'abord {@link #neJamaisDetruire} (directement, ou par réflexion depuis un autre mod :
 * {@code Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("neJamaisDetruire", UUID.class,
 * ItemStack.class, String.class)}). Ne jamais renommer cette classe ni ces méthodes (lues par réflexion).</p>
 */
public final class ObjetsAVie {
   private static final String VERSION = "2";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "a_vie"), () -> VERSION, VERSION::equals, VERSION::equals);

   /** Question « Jeter cet objet ? » reçue par le joueur : branchée par la partie client. */
   public static final Crochet<PaquetJeterQuestion> QUESTION_CLIENT = new Crochet<>();
   /** Réponse reçue par le serveur : branchée par la partie serveur. */
   public static final Crochet<PaquetJeterReponse.Recue> REPONSE_SERVEUR = new Crochet<>();

   private ObjetsAVie() {
   }

   /** Enregistre les paquets (constructeur du @Mod porteur). */
   public static void init() {
      CANAL.registerMessage(0, PaquetJeterQuestion.class, PaquetJeterQuestion::encode, PaquetJeterQuestion::decode,
         PaquetJeterQuestion::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CANAL.registerMessage(1, PaquetJeterReponse.class, PaquetJeterReponse::encode, PaquetJeterReponse::decode,
         PaquetJeterReponse::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   /** Vrai pour un couteau (TaCZ ou mêlée de LR) ou un objet de skin à vie. */
   public static boolean estAVie(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      if (stack.getItem() instanceof MeleeItem) return true;
      if (stack.getItem() instanceof CamoItem c) return estSkinAVie(c.camo());
      try {
         return Couteaux.estCouteau(stack);
      } catch (RuntimeException | LinkageError ex) {
         return false;
      }
   }

   /** Skins à vie (pour le moment : Séraphin et Chaînes de l'Enfer). */
   public static boolean estSkinAVie(Camo camo) {
      return camo == Camo.SERAPHIN || camo == Camo.CHAINES_ENFER;
   }

   // ------------------------------------------------------------------ « ne jamais détruire un objet à vie »
   /** Profondeur de la recherche dans les objets qui en contiennent d'autres (sac dans un sac, boîte). */
   private static final int PROFONDEUR = 3;

   /** Sauvetage d'un objet à vie, côté serveur (branché par ObjetsAVieServeur ; absent : rien n'est fait). */
   public interface Sauvetage {
      boolean sauver(UUID proprietaire, ItemStack objet, String origine);
   }

   @Nullable
   private static volatile Sauvetage sauvetage;

   /** Partie serveur seulement. */
   public static void brancherSauvetage(Sauvetage s) {
      sauvetage = s;
   }

   /**
    * « Ne jamais détruire un objet à vie » (audit du 02/11, point 8). À appeler sur le fil du serveur AVANT de détruire,
    * d'effacer ou de jeter au sol {@code objet} (conteneur nettoyé, recycleur, caisse, atelier, objet qui ne rentre
    * pas...). Si c'est un objet à vie, il est pris (la pile passée est vidée : jamais copiée) et rendu à
    * {@code proprietaire} : dans son inventaire, sinon gardé et rendu dès qu'une case se libère ; hors ligne, à sa
    * prochaine connexion ; mort ou en match, à sa réapparition ou à la fin du match. Renvoie vrai dans ce cas : l'appelant
    * n'a plus rien à faire de cette pile. Un sac (ou une boîte) qui contient des objets à vie : ils en sont retirés et
    * rendus de même, le sac suit son sort (faux). Sans la partie serveur de Tactique, ou pour tout autre objet : faux.
    *
    * @param origine quelques mots pour le journal du serveur (« recycleur », « conteneur de loot »...)
    */
   public static boolean neJamaisDetruire(UUID proprietaire, ItemStack objet, String origine) {
      Sauvetage s = sauvetage;
      return s != null && proprietaire != null && objet != null && !objet.isEmpty() && s.sauver(proprietaire, objet, origine);
   }

   /** Vrai pour un objet à vie, ou un sac / une boîte qui en contient (jusqu'à {@link #PROFONDEUR} niveaux). */
   public static boolean contientAVie(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      if (estAVie(stack)) return true;
      CompoundTag tag = stack.getTag();
      return tag != null && contientAVie(tag, 0);
   }

   private static boolean contientAVie(CompoundTag tag, int profondeur) {
      if (profondeur >= PROFONDEUR) return false;
      for (ListTag items : listes(tag)) {
         for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            if (estAVie(ItemStack.of(item))) return true;
            if (item.contains("tag", Tag.TAG_COMPOUND) && contientAVie(item.getCompound("tag"), profondeur + 1)) return true;
         }
      }
      return false;
   }

   /**
    * Retire d'un sac (HeraliZ Backpack : « Items ») ou d'une boîte (« BlockEntityTag.Items ») les objets à vie qu'il
    * contient, aussi dans un sac rangé dedans, et les rend (le sac est modifié ; l'objet lui-même n'est pas regardé).
    */
   public static List<ItemStack> retirerContenuAVie(ItemStack conteneur) {
      List<ItemStack> retires = new ArrayList<>();
      if (conteneur != null && !conteneur.isEmpty() && conteneur.getTag() != null) retirer(conteneur.getTag(), retires, 0);
      return retires;
   }

   private static void retirer(CompoundTag tag, List<ItemStack> retires, int profondeur) {
      if (profondeur >= PROFONDEUR) return;
      for (ListTag items : listes(tag)) {
         for (int i = items.size() - 1; i >= 0; i--) {
            CompoundTag item = items.getCompound(i);
            ItemStack s = ItemStack.of(item);
            if (estAVie(s)) {
               retires.add(s);
               items.remove(i);
            } else if (item.contains("tag", Tag.TAG_COMPOUND)) {
               retirer(item.getCompound("tag"), retires, profondeur + 1);
            }
         }
      }
   }

   /** Listes d'objets rangés dans ces données d'objet (sac, boîte) ; vide s'il n'y en a pas. */
   private static List<ListTag> listes(CompoundTag tag) {
      List<ListTag> l = new ArrayList<>(2);
      if (tag.contains("Items", Tag.TAG_LIST)) l.add(tag.getList("Items", Tag.TAG_COMPOUND));
      if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND) && tag.getCompound("BlockEntityTag").contains("Items", Tag.TAG_LIST)) {
         l.add(tag.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND));
      }
      return l;
   }

   /** Serveur -> un joueur. */
   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }

   /** Joueur -> serveur (partie client seulement). */
   public static void envoyerAuServeur(Object paquet) {
      CANAL.sendToServer(paquet);
   }
}
