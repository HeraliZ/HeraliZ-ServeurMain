package fr.heraliz.noyau.serveur.perf;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * « Minecraft allégé » (01/11, banc de charge à 80 joueurs : le gros du tick venait de Minecraft lui-même, pas de nos mods).
 * Mixins facultatifs de {@code heraliz_noyau_allege.mixins.json} (jar de Minecraft et de Mohist intacts), chacun coupé par
 * un réglage de {@code [perf]} dans {@code heraliz_noyau-server.toml}, chacun avec le même résultat que Minecraft. Ici :
 * les compteurs, pour voir dans {@code /diag etat} (et chaque rapport du diagnostic) que chaque optimisation tourne
 * vraiment sur ce serveur (un mixin qui ne trouve pas sa cible ne fait rien, sans erreur), et la fin de tick des index.
 * Fil serveur seulement.
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
   /** Point 4 : paquets de tronçon redonnés au lieu d'être refabriqués (même tronçon, même tick). */
   public static long paquetsPartages;

   // étape 2
   /** Paquets de mouvement dont le suivi des entités a été fait par l'index (au lieu de toutes les entités du monde). */
   public static long suiviMouvements;
   /** Entités jugées pour ces paquets (proches ou déjà vues). */
   public static long suiviCandidats;
   /** {@code updatePlayers(liste)} faits sur les seuls joueurs à portée ou qui voient l'entité. */
   public static long suiviListes;
   /** Joueurs qui ont changé de section, jugés par leurs seules entités. */
   public static long suiviSections;
   /** Paires rejugées par Minecraft pour contrôle (même décision attendue). */
   public static long suiviEssais;
   /** Index du suivi refaits (écart de nombre, changement hors du fil serveur, contrôle). */
   public static long suiviIndexRefaits;
   /** Contrôles complets du suivi (toutes les 60 s). */
   public static long suiviControles;
   /** Tests de Bukkit (canSee) répondus par le champ de l'entité. */
   public static long voirRaccourcis;
   /** Tronçons complets rendus sans ticket UNKNOWN. */
   public static long tronconsSansTicket;
   /** Ticks où {@code tickChunks} a parcouru la liste des tronçons qui peuvent tourner. */
   public static long tronconsActifsTicks;
   /** Tronçons de ces listes (somme). */
   public static long tronconsActifsParcourus;
   /** Listes refaites et contrôlées. */
   public static long tronconsActifsRefaits;
   public static long tronconsActifsControles;
   /** Deuxièmes comparaisons de cases de menu évitées. */
   public static long menusComparaisonsEvitees;
   /** Relevés de hauteur à la lecture d'un tronçon : rien à refaire (parcours sauté) et vraiment refaits (ou génération). */
   public static long hauteursSautees;
   public static long hauteursRefaites;
   /** Sauvegarde au fil de l'eau étalée : tranches (une par tick et par monde) et tronçons regardés. */
   public static long sauvegardesTranches;
   public static long sauvegardesRegardes;
   /** Mélanges de la liste des tronçons qui tournent sautés (monde sans apparitions naturelles). */
   public static long melangesEvites;

   /** Une fois, par la partie serveur : début et arrêt du serveur, fin de tick, monde déchargé. */
   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> {
         SuiviParTroncon.fil(Thread.currentThread());
         TronconsActifs.fil(Thread.currentThread());
      });
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END) {
            EntitesParTroncon.finDuTick();
            PaquetsTroncons.finDuTick();
            int tick = e.getServer().getTickCount();
            SuiviParTroncon.finDuTick(tick);
            TronconsActifs.finDuTick(tick);
         }
      });
      MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
         if (e.getLevel() instanceof ServerLevel niveau) {
            SuiviParTroncon.oublierCarte(niveau.getChunkSource().chunkMap);
            TronconsActifs.oublierCarte(niveau.getChunkSource().chunkMap);
         }
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
         EntitesParTroncon.vider();
         PaquetsTroncons.finDuTick();
         SuiviParTroncon.vider();
         TronconsActifs.vider();
      });
   }

   /** Une ligne pour {@code /diag etat} et les rapports. */
   public static void etat(Consumer<String> ligne) {
      ligne.accept(String.format(Locale.ROOT, "Minecraft allégé (depuis le démarrage) : joueurs proches par grille %d tronçons (%d sans joueur) ;"
                  + " portées de suivi sans flux %d ; tronçons envoyés par index d'entités %d (%d index) ; paquets de tronçon redonnés %d%n",
            prochesGrille, prochesSansJoueur, porteeRapide, entitesParIndex, indexFaits, paquetsPartages));
      StringBuilder s = new StringBuilder("Minecraft allégé, étape 2 :");
      s.append(String.format(Locale.ROOT, " suivi par tronçon %d mouvements (%d entités jugées, %.1f par mouvement), %d listes de joueurs,"
                  + " %d changements de section, %d contrôles de paires, %d contrôles complets, %d index refaits (%s) ; canSee lu sur l'entité %d",
            suiviMouvements, suiviCandidats, suiviMouvements == 0 ? 0.0 : (double) suiviCandidats / suiviMouvements, suiviListes,
            suiviSections, suiviEssais, suiviControles, suiviIndexRefaits, SuiviParTroncon.etat(), voirRaccourcis));
      s.append(String.format(Locale.ROOT, " ; tronçons sans ticket %d", tronconsSansTicket));
      s.append(String.format(Locale.ROOT, " ; tronçons qui tournent par liste %d ticks (%.0f par tick, %d refaites, %d contrôles, %s)",
            tronconsActifsTicks, tronconsActifsTicks == 0 ? 0.0 : (double) tronconsActifsParcourus / tronconsActifsTicks,
            tronconsActifsRefaits, tronconsActifsControles, TronconsActifs.etat()));
      s.append(String.format(Locale.ROOT, " ; comparaisons de menu évitées %d", menusComparaisonsEvitees));
      s.append(String.format(Locale.ROOT, " ; relevés de hauteur : %d sans parcours, %d refaits", hauteursSautees, hauteursRefaites));
      s.append(String.format(Locale.ROOT, " ; sauvegardes étalées : %d tranches (%.0f tronçons regardés par tranche) ; mélanges évités %d",
            sauvegardesTranches, sauvegardesTranches == 0 ? 0.0 : (double) sauvegardesRegardes / sauvegardesTranches, melangesEvites));
      ligne.accept(s.append(String.format("%n")).toString());
   }
}
