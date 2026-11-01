package fr.heraliz.noyau.serveur.perf;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * « Minecraft allégé » (01/11, banc de charge à 80 joueurs : le gros du tick venait de Minecraft lui-même, pas de nos mods).
 * Mixins facultatifs de {@code heraliz_noyau_allege.mixins.json} (jar de Minecraft et de Mohist intacts), chacun coupé par
 * un réglage de {@code [perf]} dans {@code heraliz_noyau-server.toml}, chacun avec le même résultat que Minecraft. Ici :
 * les compteurs, pour voir dans {@code /diag etat} (et chaque rapport du diagnostic) que chaque optimisation tourne
 * vraiment sur ce serveur (un mixin qui ne trouve pas sa cible ne fait rien, sans erreur). Fil serveur seulement.
 */
public final class Allege {
   private Allege() {
   }

   /** Point 1 : tronçons jugés par la grille des joueurs proches. */
   public static long prochesGrille;
   /** Point 1 : dont ceux sans joueur dans la grille (Minecraft aurait parcouru tous les joueurs du monde pour rien). */
   public static long prochesSansJoueur;
   /** Point 2 : portées de suivi lues sans parcourir les passagers (aucun objet créé). */
   public static long porteeRapide;
   /** Point 4 : tronçons envoyés dont les entités ont été prises dans l'index (au lieu de toutes les entités du monde). */
   public static long entitesParIndex;
   /** Point 4 : index « tronçon → entités » faits. */
   public static long indexFaits;

   /** Une fois, par la partie serveur : fin de tick et arrêt du serveur. */
   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END) {
            EntitesParTroncon.finDuTick();
         }
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> EntitesParTroncon.vider());
   }

   /** Une ligne pour {@code /diag etat} et les rapports. */
   public static void etat(Consumer<String> ligne) {
      ligne.accept(String.format(Locale.ROOT, "Minecraft allégé (depuis le démarrage) : joueurs proches par grille %d tronçons (%d sans joueur) ;"
                  + " portées de suivi sans flux %d ; tronçons envoyés par index d'entités %d (%d index)%n",
            prochesGrille, prochesSansJoueur, porteeRapide, entitesParIndex, indexFaits));
   }
}
