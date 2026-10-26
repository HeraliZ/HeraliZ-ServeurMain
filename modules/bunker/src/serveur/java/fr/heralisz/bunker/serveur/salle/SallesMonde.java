package fr.heralisz.bunker.serveur.salle;

import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.mojang.logging.LogUtils;
import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.KeycardConfig;
import fr.heralisz.bunker.keycard.KeycardDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Salles à carte d'une dimension (fichier {@code data/heralisz_bunker_salles.dat} du monde, écrit par le jeu, jamais à
 * la main). Une salle : sa boîte (deux coins posés par le staff, blocs compris), sa couleur (délai du loot), ses portes à
 * carte liées.
 * <p>Chaque seconde ({@link #tick}) : qui est dedans (pieds d'un joueur vivant, pas spectateur, dans la boîte ; un joueur
 * déconnecté n'y est plus) ; porte tenue ouverte pendant la fenêtre d'entrée (temps d'ouverture + 10 s après l'accès
 * accordé), tant que quelqu'un est dedans ou dans l'encadrement de la porte, et encore quelques secondes après la sortie
 * du dernier ; loot : rien tant que quelqu'un est dedans, rechargé (HeraliZ Loot) quand la salle est vide depuis le
 * délai de sa couleur.</p>
 */
public final class SallesMonde extends SavedData {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String FICHIER = "heralisz_bunker_salles";
   /** Taille maximale d'une salle (blocs). */
   static final int MAX_LARGEUR = 128, MAX_HAUTEUR = 64;

   static final class Salle {
      final String nom;
      BlockPos min, max;
      AccessLevel niveau;
      /** Contrôleurs des portes à carte liées. */
      final List<BlockPos> portes = new ArrayList<>();
      /** Quelqu'un était dedans au dernier relevé (gardé : après un redémarrage, le délai part du premier relevé vide). */
      boolean occupee;
      /** Heure (ticks de jeu) où le loot réapparaîtra, -1 : rien d'attendu. */
      long rechargeA = -1;

      // ---- état du moment, non gardé
      long tenueJusqua = -1, derniereOccupation = -1;
      boolean tenue, verifiee, lootVu;
      int occupants;
      /** Encadrements des portes (pièces de la porte), calculés quand la porte est chargée. */
      @Nullable
      List<AABB> seuils;

      Salle(String nom, BlockPos a, BlockPos b, AccessLevel niveau) {
         this.nom = nom;
         this.niveau = niveau;
         boite(a, b);
      }

      void boite(BlockPos a, BlockPos b) {
         this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
         this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
      }

      AABB boite() {
         return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
      }

      int largeurX() {
         return max.getX() - min.getX() + 1;
      }

      int hauteur() {
         return max.getY() - min.getY() + 1;
      }

      int largeurZ() {
         return max.getZ() - min.getZ() + 1;
      }

      boolean touche(BlockPos a, BlockPos b) {
         return a.getX() <= max.getX() && b.getX() >= min.getX() && a.getY() <= max.getY() && b.getY() >= min.getY()
            && a.getZ() <= max.getZ() && b.getZ() >= min.getZ();
      }
   }

   final Map<String, Salle> salles = new TreeMap<>();

   public static SallesMonde get(ServerLevel l) {
      return l.getDataStorage().computeIfAbsent(SallesMonde::charger, SallesMonde::new, FICHIER);
   }

   // ------------------------------------------------------------------------------------------ sauvegarde
   static SallesMonde charger(CompoundTag t) {
      SallesMonde m = new SallesMonde();
      for (Tag x : t.getList("Salles", Tag.TAG_COMPOUND)) {
         CompoundTag c = (CompoundTag) x;
         AccessLevel niv;
         try {
            niv = AccessLevel.valueOf(c.getString("Niveau"));
         } catch (IllegalArgumentException ex) {
            niv = AccessLevel.GREEN;
         }
         Salle s = new Salle(c.getString("Nom"), BlockPos.of(c.getLong("Min")), BlockPos.of(c.getLong("Max")), niv);
         for (Tag p : c.getList("Portes", Tag.TAG_LONG)) s.portes.add(BlockPos.of(((LongTag) p).getAsLong()));
         s.occupee = c.getBoolean("Occupee");
         s.rechargeA = c.contains("RechargeA") ? c.getLong("RechargeA") : -1;
         m.salles.put(s.nom, s);
      }
      return m;
   }

   @Override
   public CompoundTag save(CompoundTag t) {
      ListTag l = new ListTag();
      for (Salle s : salles.values()) {
         CompoundTag c = new CompoundTag();
         c.putString("Nom", s.nom);
         c.putLong("Min", s.min.asLong());
         c.putLong("Max", s.max.asLong());
         c.putString("Niveau", s.niveau.name());
         ListTag p = new ListTag();
         for (BlockPos b : s.portes) p.add(LongTag.valueOf(b.asLong()));
         c.put("Portes", p);
         c.putBoolean("Occupee", s.occupee);
         c.putLong("RechargeA", s.rechargeA);
         l.add(c);
      }
      t.put("Salles", l);
      return t;
   }

   // ------------------------------------------------------------------------------------------ recherches
   @Nullable
   Salle salle(String nom) {
      return salles.get(nom);
   }

   /** Salle dont la boîte contient ce point (pieds d'un joueur). */
   @Nullable
   Salle salleA(double x, double y, double z) {
      for (Salle s : salles.values()) {
         if (s.boite().contains(x, y, z)) return s;
      }
      return null;
   }

   /** Une salle qui lie cette porte la tient-elle ouverte ? */
   boolean tenue(BlockPos porte) {
      for (Salle s : salles.values()) {
         if (s.tenue && s.portes.contains(porte)) return true;
      }
      return false;
   }

   /** Un lecteur vient d'accorder l'accès à ces portes : fenêtre d'entrée des salles qui les lient. -> salles concernées. */
   List<Salle> acces(ServerLevel l, List<DoorBlockEntity> portes) {
      List<Salle> out = new ArrayList<>();
      long now = l.getGameTime();
      for (Salle s : salles.values()) {
         for (DoorBlockEntity d : portes) {
            if (!s.portes.contains(d.getBlockPos())) continue;
            int ouverture = d.getDoorDecl() == null ? 60 : d.getDoorDecl().getOpenTime();
            s.tenueJusqua = Math.max(s.tenueJusqua, now + ouverture + KeycardConfig.entreeTicks());
            s.tenue = true;
            out.add(s);
            break;
         }
      }
      return out;
   }

   // ------------------------------------------------------------------------------------------ chaque seconde
   static boolean compte(ServerPlayer p) {
      return p.isAlive() && !p.isSpectator();
   }

   void tick(ServerLevel l) {
      if (salles.isEmpty()) return;
      long now = l.getGameTime();
      List<ServerPlayer> joueurs = l.players();
      boolean revoir = (now / 20) % 5 == 0;                       // toutes les 5 s, les portes sont revérifiées
      for (Salle s : salles.values()) {
         if (!s.lootVu) {
            s.lootVu = true;
            LootSalles.verifier(l, s);
         }
         maj(l, s, joueurs, now, revoir);
      }
   }

   /** Occupation, recharge du loot et portes d'une salle (chaque seconde, et tout de suite après une sortie forcée). */
   void maj(ServerLevel l, Salle s, List<ServerPlayer> joueurs, long now, boolean revoir) {
      AABB b = s.boite();
      int n = 0;
      for (ServerPlayer p : joueurs) {
         if (compte(p) && b.contains(p.getX(), p.getY(), p.getZ())) n++;
      }
      s.occupants = n;
      // L'encadrement ne compte que si la porte est déjà tenue ouverte (carte passée ou quelqu'un dedans) : se coller à
      // une porte fermée ne l'ouvre pas. Un joueur sur le seuil compte comme occupant : le loot attend qu'il parte.
      boolean seuil = s.tenue && dansUnSeuil(l, s, joueurs);
      boolean occ = n > 0 || seuil;
      if (occ) s.derniereOccupation = now;
      if (occ != s.occupee) {
         s.occupee = occ;
         s.rechargeA = occ ? -1 : now + KeycardConfig.rechargeTicks(s.niveau);   // le délai part de la sortie du dernier
         setDirty();
      }
      if (!occ && s.rechargeA >= 0 && now >= s.rechargeA) {
         s.rechargeA = -1;
         setDirty();
         int r = LootSalles.recharger(l, s.nom);
         if (r >= 0) LOG.info("[salles] salle {} : loot réapparu ({} conteneurs)", s.nom, r);
      }
      boolean tenue = occ || now < s.tenueJusqua
         || (s.derniereOccupation >= 0 && now - s.derniereOccupation < KeycardConfig.fermetureTicks());
      if (tenue != s.tenue || !s.verifiee || (revoir && !s.portes.isEmpty())) {
         s.tenue = tenue;
         s.verifiee = true;
         notifier(l, s);
      }
   }

   /**
    * Un joueur vient d'être sorti de la salle ({@link SortieSalles}) : si plus personne n'est dedans, la porte ne reste
    * pas ouverte « quelques secondes après la sortie du dernier » (il pourrait y retourner) ; la fenêtre d'entrée d'une
    * carte passée par un autre joueur est gardée. État et portes remis à jour tout de suite.
    */
   void apresSortie(ServerLevel l, Salle s) {
      AABB b = s.boite();
      boolean personne = true;
      for (ServerPlayer p : l.players()) {
         if (compte(p) && b.contains(p.getX(), p.getY(), p.getZ())) personne = false;
      }
      if (personne) s.derniereOccupation = -1;
      maj(l, s, l.players(), l.getGameTime(), true);
   }

   /**
    * Encadrements des portes de la salle (pièces de chaque porte, élargies de 0,3 bloc), calculés une fois quand toutes
    * ses portes sont chargées ; null s'il en manque une (recalculé plus tard).
    */
   @Nullable
   static List<AABB> seuils(ServerLevel l, Salle s) {
      if (s.seuils == null) {
         List<AABB> seuils = new ArrayList<>();
         for (BlockPos c : s.portes) {
            if (!l.isLoaded(c)) return null;                           // recalculé quand la porte sera chargée
            if (l.getBlockEntity(c) instanceof DoorBlockEntity d) {
               AABB a = null;
               for (BlockPos p : KeycardDoors.parts(d)) a = a == null ? new AABB(p) : a.minmax(new AABB(p));
               if (a != null) seuils.add(a.inflate(0.3, 0, 0.3));
            }
         }
         s.seuils = seuils;
      }
      return s.seuils;
   }

   /** Quelqu'un dans l'encadrement d'une porte de la salle (la porte ne se referme pas sur lui). */
   private static boolean dansUnSeuil(ServerLevel l, Salle s, List<ServerPlayer> joueurs) {
      if (s.portes.isEmpty() || joueurs.isEmpty()) return false;
      List<AABB> seuils = seuils(l, s);
      if (seuils == null) return false;
      for (ServerPlayer p : joueurs) {
         if (!compte(p)) continue;
         for (AABB a : seuils) {
            if (a.intersects(p.getBoundingBox())) return true;
         }
      }
      return false;
   }

   /** Les portes chargées de la salle réévaluent leur état (ouverte / fermée). */
   static void notifier(ServerLevel l, Salle s) {
      for (BlockPos c : s.portes) {
         if (l.isLoaded(c) && l.getBlockEntity(c) instanceof DoorBlockEntity d) d.checkRedstonePower();
      }
   }
}
