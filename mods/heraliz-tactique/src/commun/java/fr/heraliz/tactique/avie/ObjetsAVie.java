package fr.heraliz.tactique.avie;

import fr.heralisz.camo.Camo;
import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.tactical.item.MeleeItem;
import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.onglets.Couteaux;
import fr.heraliz.tactique.pont.Crochet;
import java.util.Optional;
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
 */
public final class ObjetsAVie {
   private static final String VERSION = "1";
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

   /** Serveur -> un joueur. */
   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }

   /** Joueur -> serveur (partie client seulement). */
   public static void envoyerAuServeur(Object paquet) {
      CANAL.sendToServer(paquet);
   }
}
