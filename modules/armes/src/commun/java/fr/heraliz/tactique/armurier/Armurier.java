package fr.heraliz.tactique.armurier;

import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.pont.Crochet;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre de l'armurier (23/10, maquette validée, maquettes/registre_armurier.png) : les op (permission 4, revérifiée
 * par le serveur à chaque paquet) modifient EN JEU les recettes de la Table d'armurier (recettes TaCZ
 * {@code tacz:gun_smith_table_crafting} du gunpack heraliz_crafts) : objet donné, quantités, composants demandés, onglet ;
 * ajouter, supprimer, revenir au fichier. Les changements sont gardés hors de {@code config/}
 * ({@code heraliz_tactique/recettes_jeu.json} à la racine du serveur, jamais écrasé par la mise en ligne), appliqués par
 * le serveur par-dessus les recettes lues par TaCZ (armurier.ArmurierServeur) et renvoyés tout de suite à tous les
 * joueurs. Canal heraliz_tactique:armurier, même version des deux côtés.
 */
public final class Armurier {
   private static final String VERSION = "1";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "armurier"), () -> VERSION, VERSION::equals, VERSION::equals);

   public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizTactique.MODID);
   public static final RegistryObject<Item> REGISTRE = OBJETS.register("registre_armurier",
      () -> new RegistreArmurier(new Item.Properties().stacksTo(1)));

   /** Onglets de la Table d'armurier, dans l'ordre de l'écran (groupes des recettes de heraliz_crafts). */
   public static final List<String> ONGLETS = List.of("munitions", "pistolets", "mitraillettes", "fusils", "pompes", "soins",
      "viseurs", "accessoires", "explosifs", "composants", "divers");
   public static final String NS_ONGLETS = "heraliz_crafts";

   /** Un op demande l'éditeur (clic droit avec le registre) : branché par la partie serveur. */
   public static final Crochet<ServerPlayer> OUVRIR_SERVEUR = new Crochet<>();
   /** Changement demandé depuis l'écran : branché par la partie serveur. */
   public static final Crochet<PaquetModif.Recue> MODIF_SERVEUR = new Crochet<>();
   /** Liste des recettes reçue par l'op : branchée par la partie client. */
   public static final Crochet<PaquetEdition> EDITION_CLIENT = new Crochet<>();

   private Armurier() {
   }

   /** Objet et paquets (constructeur du @Mod porteur). */
   public static void init(IEventBus busMod) {
      OBJETS.register(busMod);
      CANAL.registerMessage(0, PaquetEdition.class, PaquetEdition::encode, PaquetEdition::decode, PaquetEdition::handle,
         Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CANAL.registerMessage(1, PaquetModif.class, PaquetModif::encode, PaquetModif::decode, PaquetModif::handle,
         Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   /** Groupe TaCZ d'un onglet (« fusils » -> heraliz_crafts:fusils). */
   public static String groupe(String onglet) {
      return NS_ONGLETS + ":" + onglet;
   }

   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }

   public static void envoyerAuServeur(Object paquet) {
      CANAL.sendToServer(paquet);
   }
}
