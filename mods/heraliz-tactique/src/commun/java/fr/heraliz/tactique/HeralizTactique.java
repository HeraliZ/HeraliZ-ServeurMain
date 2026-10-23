package fr.heraliz.tactique;

import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.tactical.init.ModItems;
import fr.heraliz.tactique.allonge.ReseauAllonge;
import fr.heraliz.tactique.couteaux.ReseauCouteaux;
import fr.heraliz.tactique.onglet.OngletGroupe;
import fr.heraliz.tactique.onglets.Etablis;
import fr.heraliz.tactique.pont.Parties;
import fr.heraliz.tactique.son.ReseauSon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * HeraliZ Tactique : le mod « porteur » du jar fusionné (docs/METHODE_CLIENT_SERVEUR.md §2). Il ne contient aucun contenu
 * de jeu ; il porte ce qui est partagé par les cinq mods du jar (heralisz_tactical, heralisz_melee, heralisz_heal,
 * heralisz_grenade, heralisz_camo) : l'onglet créatif unique « HeraliZ · Tactique » (et son panneau de filtres, côté
 * client) et le pont client / serveur (paquet {@code fr.heraliz.tactique.pont}). Les mods gardent chacun leur modId et
 * leur classe @Mod.
 * <p>
 * Les groupes de l'onglet sont déclarés ICI, dans un ordre fixe (Mêlée, Soins, Grenades, Camos, Décorations) : FML
 * construit les mods en parallèle, l'ordre d'ajout depuis chaque @Mod ne serait pas stable. Le contenu de chaque groupe
 * reste fourni par son mod (ModItems pour le moteur, HeraliszCamo pour les camos), appelé seulement au remplissage.
 * Les anciens onglets (heralisz_tactical:throwable/consumable/melee, heralisz_camo:camos/skinx/decor) sont retirés.
 * <p>
 * Il porte aussi « s'allonger » (paquet {@code fr.heraliz.tactique.allonge}) : règles HeraliZ ajoutées au ramper de TaCZ
 * (un appui, délai pour se rallonger, pas de tir pendant qu'on se couche) ; les vitres cassées par les balles, qui
 * repoussent (paquet {@code fr.heraliz.tactique.vitres}) ; le retrait des établis inutiles de TaCZ des onglets créatifs
 * ({@code fr.heraliz.tactique.onglets.Etablis}) ; les coups des couteaux TaCZ, frappés comme les armes de mêlée
 * (paquet {@code fr.heraliz.tactique.couteaux}).
 */
@Mod(HeralizTactique.MODID)
public class HeralizTactique {
   public static final String MODID = "heraliz_tactique";

   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

   /** Groupes de l'onglet, dans l'ordre du panneau. */
   public static final OngletGroupe ONGLET = new OngletGroupe();

   public static final RegistryObject<CreativeModeTab> TAB = TABS.register("onglet",
      () -> ONGLET.construire(Component.translatable("itemGroup.heraliz_tactique"), HeralizTactique::icone));

   static {
      // armes de mêlée (contenu heralisz_melee, objet heralisz_tactical:melee)
      ONGLET.ajouter("melee", "onglet.heraliz_tactique.melee", () -> ModItems.getMeleeIcon(), out -> ModItems.remplirMelee(out));
      // soins (contenu heralisz_heal, objet heralisz_tactical:consumable)
      ONGLET.ajouter("soins", "onglet.heraliz_tactique.soins", () -> ModItems.getConsumableIcon(), out -> ModItems.remplirSoins(out));
      // lancers et détonateur (contenu heralisz_grenade, objets heralisz_tactical:throwable et :detonator)
      ONGLET.ajouter("grenades", "onglet.heraliz_tactique.grenades", () -> ModItems.getThrowableIcon(), out -> ModItems.remplirGrenades(out));
      // camos et skins X des armes TaCZ (heralisz_camo)
      ONGLET.ajouter("camos", "onglet.heraliz_tactique.camos", () -> HeraliszCamo.iconeCamos(), out -> HeraliszCamo.remplirCamos(out));
      // stickers et module StatTrack (heralisz_camo) : ~130 objets d'une autre nature que les camos
      ONGLET.ajouter("decor", "onglet.heraliz_tactique.decor", () -> HeraliszCamo.iconeDecor(), out -> HeraliszCamo.remplirDecor(out));
   }

   public HeralizTactique() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      TABS.register(bus);
      // établis génériques de TaCZ (workbench_a/b/c, sans nom chez nous) retirés de tous les onglets créatifs
      bus.addListener(Etablis::retirer);
      // s'allonger (touche « Ramper » de TaCZ, avec ou sans arme) et plongeon : canal réseau, règles du serveur, touche et
      // messages chez le joueur
      ReseauAllonge.init();
      // tirs silencieux entendus de loin, étouffés et d'un endroit flou (son.SonLointain)
      ReseauSon.init();
      // couteaux TaCZ frappés comme les armes de mêlée de LR (couteaux.CoupsCouteau) : demande de coup du joueur
      ReseauCouteaux.init();
      // dégâts des armes contre les joueurs (armes.DegatsJoueurs) : envoyés aux joueurs pour les chiffres affichés
      fr.heraliz.tactique.armes.ReseauArmes.init();
      // objets à vie (couteaux, skins à vie) : question « Jeter cet objet ? » et sa réponse
      fr.heraliz.tactique.avie.ObjetsAVie.init();
      // jambe cassée (attelle) : pas de saut
      fr.heraliz.tactique.jambe.JambeCassee.init();
      // Registre de l'armurier (op) : recettes de la Table d'armurier modifiées en jeu
      fr.heraliz.tactique.armurier.Armurier.init(bus);
      Parties.serveur(MODID, "fr.heraliz.tactique.serveur.ServeurTactique", bus);
      Parties.client(MODID, "fr.heraliz.tactique.client.ClientTactique", bus);
   }

   /** Icône de l'onglet : celle du premier groupe (la dague). */
   private static ItemStack icone() {
      var g = ONGLET.groupes();
      return g.isEmpty() ? new ItemStack(Items.IRON_SWORD) : g.get(0).icone().get();
   }
}
