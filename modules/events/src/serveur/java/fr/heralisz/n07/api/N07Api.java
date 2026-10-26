package fr.heralisz.n07.api;

import fr.heralisz.n07.api.scene.Scene;
import fr.heralisz.n07.api.scene.SceneHandle;
import fr.heralisz.n07.api.scene.SceneResult;
import fr.heralisz.n07.api.scene.SkipMode;
import fr.heralisz.n07.encounter.EncounterImpl;
import fr.heralisz.n07.encounter.EncounterManager;
import fr.heralisz.n07.hall.HallPlacer;
import fr.heralisz.n07.hall.HallRegistry;
import fr.heralisz.n07.scene.GiantScenes;
import fr.heralisz.n07.scene.SceneRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * API publique du mod du Sujet N-07 (fil SERVEUR sauf mention ; cote client : api.client.CinematiqueN07). Rien ici ne
 * connait le mode Zombies : le jeu (heralisz_matchs) s'y branche s'il trouve le mod (ModList) et si {@link #VERSION}
 * correspond. Contrat complet : work/zombies_v3/boss_n07/API.md.
 * <ul>
 *    <li>Salle : {@link #placeHall} / {@link #bindHall} -&gt; PlacedHall (reperes, etat, remise a zero, evacuation).</li>
 *    <li>Scenes : {@link #playScene} (donnees Scene : plans, textes, sons, actions serveur).</li>
 *    <li>Combat : {@link #prepare} (boss endormi dans sa cuve) / {@link #start} (reveil direct), EncounterSpec, N07Events.</li>
 *    <li>The Giant : {@link #playGiantIntro} (~75 s, passable), {@link #playGiantOutro} (~22 s, allume le teleporteur).</li>
 * </ul>
 */
public final class N07Api {
   /** Version du contrat : +1 a chaque changement incompatible. */
   public static final int VERSION = 1;
   /** Variante de salle du mode Zombies (structures n07_hall_zombies.nbt + n07_lift.nbt). */
   public static final String HALL_ZOMBIES = "n07_hall_zombies";

   /** Types de cinematiques du boss (N07Events.onCinematicStart / End). */
   public static final int CINE_SPAWN = 0, CINE_PHASE2 = 1, CINE_PHASE3 = 2, CINE_DEATH = 3;

   /** Ids des attaques du boss (DamageRemap) ; « irradiated » = effet Irradie (sans attaquant). */
   public static final List<String> ATTACKS = List.of("claw", "slam", "slam_column", "shell", "drop", "boulder", "charge", "pulse", "isotope",
      "geyser_fist", "geyser", "spray_hug", "beam", "arc", "meltdown", "pool", "aura", "p2_burst", "p3_nova", "irradiated");

   // ------------------------------------------------------------------ crochets (N07Events.onHook)
   /** Intro : la carte d'acces est acceptee (voyant vert de la porte), la porte s'ouvre, le monte-charge part / arrive. */
   public static final String HOOK_DOOR_KEY = "porte_cle", HOOK_DOOR_OPEN = "porte_ouverture", HOOK_LIFT_DOWN = "ascenseur_descente",
      HOOK_LIFT_ARRIVED = "ascenseur_arrivee", HOOK_PLAYERS_PLACED = "joueurs_places", HOOK_INTRO_END = "intro_fin";
   /** Combat : fin de l'apparition (le combat commence). */
   public static final String HOOK_FIGHT = "combat";
   /** Phases : debut (cinematique) et fin (Max Ammo, chiens...) ; coupure de courant de la phase 3. */
   public static final String HOOK_P2_START = "p2_debut", HOOK_P2_END = "p2_fin", HOOK_P3_START = "p3_debut", HOOK_P3_END = "p3_fin",
      HOOK_P3_BLACKOUT = "p3_coupure";
   /** Outro : le teleporteur 935 s'allume ; fin de l'outro. */
   public static final String HOOK_TELEPORTER_ON = "teleporteur_allume", HOOK_OUTRO_END = "outro_fin";

   private N07Api() {}

   // ------------------------------------------------------------------ salle
   /** Reperes d'une variante (coordonnees locales), ou null. */
   @Nullable
   public static HallLayout layout(String variant) {
      return HallLayout.get(variant);
   }

   /**
    * Pose la salle (hall + puits) a origin avec la rotation donnee (pivot 0,0,0 comme StructureTemplate), par tranches de
    * blocksPerTick (&lt;= 0 : d'un coup ; ~90 000 entrees : 6000/tick = 15 ticks). done recoit la salle liee, prete.
    * Rend la salle (liee tout de suite, utilisable a la fin de la pose) ou null si la variante est inconnue.
    */
   @Nullable
   public static PlacedHall placeHall(ServerLevel level, String variant, BlockPos origin, Rotation rot, int blocksPerTick, @Nullable Consumer<PlacedHall> done) {
      HallLayout l = HallLayout.get(variant);
      if (l == null) return null;
      PlacedHall h = bindHall(level, variant, origin, rot);
      String hall = l.structure.substring(l.structure.indexOf(':') + 1), lift = l.liftStructure.substring(l.liftStructure.indexOf(':') + 1);
      HallPlacer.place(level, hall, origin, rot, blocksPerTick, false,
         () -> HallPlacer.place(level, lift, h.world(l.liftOffset), rot, blocksPerTick, false, () -> {
            h.terminalReady();
            if (done != null) done.accept(h);
         }));
      return h;
   }

   /** Lie une salle deja posee (ex. par le generateur de carte avec les memes .nbt, origine et rotation). */
   @Nullable
   public static PlacedHall bindHall(ServerLevel level, String variant, BlockPos origin, Rotation rot) {
      HallLayout l = HallLayout.get(variant);
      if (l == null) return null;
      PlacedHall h = new PlacedHall(level, l, origin, rot);
      HallRegistry.add(h);
      return h;
   }

   /** Oublie la salle (instance detruite) : son combat eventuel est interrompu. */
   public static void unbindHall(PlacedHall h) {
      if (h.encounter() != null && h.encounter().active()) h.encounter().abort("salle oubliee");
      HallRegistry.remove(h);
   }

   /** Vide le volume de la salle (air), par tranches ; la salle est oubliee. */
   public static void clearHall(PlacedHall h, int blocksPerTick, @Nullable Runnable done) {
      unbindHall(h);
      h.clearEntities();
      HallLayout l = h.layout;
      HallPlacer.clear(h.level, l.structure.substring(l.structure.indexOf(':') + 1), h.origin, h.rotation, blocksPerTick,
         () -> HallPlacer.clear(h.level, l.liftStructure.substring(l.liftStructure.indexOf(':') + 1), h.world(l.liftOffset), h.rotation, blocksPerTick, done));
   }

   // ------------------------------------------------------------------ scenes
   /**
    * Joue une scene a ces joueurs (et a eux seuls) : camera, habillage, actions serveur ; ils sont proteges et figes le temps
    * de la scene. onEnd (fil serveur) : COMPLETED, SKIPPED (vote, actions essentielles appliquees) ou CANCELLED.
    */
   public static SceneHandle playScene(ServerLevel level, Scene scene, Collection<ServerPlayer> viewers, SkipMode skip, @Nullable Consumer<SceneResult> onEnd) {
      return SceneRunner.play(level, scene, viewers, skip, onEnd);
   }

   // ------------------------------------------------------------------ combat
   /** Cree le combat : boss endormi dans sa cuve (ou a spawnAt), en attente de {@link Encounter#wake()}. Null si impossible. */
   @Nullable
   public static Encounter prepare(EncounterSpec spec) {
      return EncounterManager.create(spec);
   }

   /** Cree le combat et reveille le boss tout de suite (cinematique d'apparition de 12 s). */
   @Nullable
   public static Encounter start(EncounterSpec spec) {
      Encounter e = prepare(spec);
      if (e != null) e.wake();
      return e;
   }

   public static List<Encounter> encounters() {
      return EncounterManager.all();
   }

   // ------------------------------------------------------------------ The Giant
   /**
    * Intro de The Giant (~62 s de scene + 12 s d'apparition du boss + raccord) : porte de confinement, monte-charge, hall,
    * buses, terminal, puis le boss se reveille. Le combat doit etre PREPARED, dans une salle. door : repere de la porte
    * (pieds au milieu, lacet du tunnel vers la porte) ; null = deduit du haut du monte-charge. onFightStarts : appele quand
    * le joueur reprend la main (resultat COMPLETED ou SKIPPED). Rend null si impossible.
    */
   @Nullable
   public static SceneHandle playGiantIntro(Encounter enc, @Nullable Scene.Frame door, SkipMode skip, @Nullable Consumer<SceneResult> onFightStarts) {
      if (!(enc instanceof EncounterImpl e) || enc.spec().hall == null || enc.state() != Encounter.State.PREPARED) return null;
      return GiantScenes.playIntro(e, door, skip, onFightStarts);
   }

   /**
    * Outro (~22 s) apres la victoire (appeler dans N07Events.onDeath) : orbite autour de la statue, buses coupees une a une,
    * radio, teleporteur 935 allume, carte de titre. Si la mort a ete passee, l'etat final est applique tout de suite.
    */
   @Nullable
   public static SceneHandle playGiantOutro(Encounter enc, SkipMode skip, @Nullable Consumer<SceneResult> onEnd) {
      if (!(enc instanceof EncounterImpl e) || enc.spec().hall == null) return null;
      return GiantScenes.playOutro(e, skip, onEnd);
   }
}
