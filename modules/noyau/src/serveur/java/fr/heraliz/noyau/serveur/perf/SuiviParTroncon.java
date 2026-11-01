package fr.heraliz.noyau.serveur.perf;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.allege.CarteAcces;
import fr.heraliz.noyau.serveur.allege.SuiviEntreeAcces;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Point 1 de l'étape 2 du chantier « Minecraft allégé » (01/11) : le suivi des entités par les joueurs.
 * <p><b>Le coût.</b> À chaque paquet de mouvement d'un joueur (~20 par seconde, même pour un simple coup d'œil),
 * Minecraft juge TOUTES les entités suivies du monde pour ce joueur ({@code ChunkMap.move} → {@code TrackedEntity
 * .updatePlayer}) : à portée ? montrée ? Banc du 01/11 : 14,6 ms par tick à 100 joueurs, 77,4 ms à 150 (joueurs × entités,
 * dont ~2 500 balles de TaCZ) ; la plus grosse part du tick.
 * <p><b>Ici, même résultat en ne jugeant que ce qui peut changer.</b> Pour une paire (entité, joueur), Minecraft ne fait
 * rien quand l'entité est à plus de {@code vue × 16} blocs du joueur (sa portée ne dépasse jamais ça) ET que le joueur ne
 * la voit pas déjà : elle reste non vue. Il suffit donc de juger, pour ce joueur :
 * <ul>
 *   <li>les entités des cases de 32 blocs à portée (index « case → entités », tenu à jour : entité suivie ou plus,
 *   entité qui change de tronçon, {@code Entity.setPosRaw}) ;</li>
 *   <li>les entités qu'il voit déjà (où qu'elles soient : téléportation, entité partie loin), d'après le « miroir » de
 *   chaque liste {@code seenBy} de Minecraft : la liste de chaque entrée du suivi est remplacée à sa création par
 *   {@link Vus}, la même liste (même ordre, mêmes réponses) qui note chaque ajout et chaque retrait dans
 *   {@link VusJoueur} de la connexion du joueur, quel que soit le code qui la change (Minecraft, Bukkit hide / show du
 *   vanish, anti-ESP de Spartan…).</li>
 * </ul>
 * Chaque paire retenue est jugée par {@link #juger}, la décision de Minecraft refaite à l'identique (même calcul de
 * distance, même portée {@code getEffectiveRange} passagers compris, {@code broadcastToPlayer} puis le test de Bukkit
 * de Mohist dans le même ordre, mêmes ajouts / retraits et mêmes paquets) ; le miroir évite seulement de chercher dans la
 * liste {@code seenBy} (une lecture froide de la mémoire par paire, 13 ms par tick à 150 joueurs). Même chose pour
 * {@code TrackedEntity.updatePlayers(liste)} (entité qui change de section, entité qui commence à être suivie) : seuls les
 * joueurs à portée ou qui la voient déjà, et pour les joueurs qui changent de section dans {@code ChunkMap.tick} : leurs
 * entités proches ou vues. Seul l'ordre des paquets entre deux entités différentes change (Minecraft les envoie dans
 * l'ordre de sa table, quelconque).
 * <p><b>Sécurité.</b> Jamais utilisé tant que les quatre crochets (listes {@link Vus}, entité suivie, entité plus suivie,
 * changement de tronçon) n'ont pas été vus en marche, tant que le nombre d'entrées de l'index n'est pas celui de Minecraft
 * (sinon index refait à la fin du tick), ni pour un joueur dont la connexion n'a pas de miroir. Une fois sur 8 192, la
 * paire jugée ici est rejugée par Minecraft lui-même : une seule différence coupe tout jusqu'au redémarrage (journal) ;
 * toutes les 60 s, l'index et les miroirs sont comparés en entier à Minecraft (refaits si besoin, coupé après 5 écarts).
 * Un changement venu d'un autre fil coupe aussi. Réglages : {@code [perf] suivi_par_troncon} (tout) et
 * {@code suivi_voir_rapide} ({@link VoirBukkit}) ; réglage coupé : les boucles de Minecraft, l'index et les miroirs restent
 * tenus (quelques opérations quand une entité change de tronçon ou qu'un joueur la voit ou ne la voit plus), sans effet sur
 * les décisions. Fil serveur seulement.
 * <p>Essai hors du jeu (deux mondes identiques, l'un avec les boucles de Minecraft, l'autre avec celles-ci : joueurs qui
 * bougent, tournent la tête, se téléportent, se connectent, partent, changent de section ; entités lentes et rapides,
 * téléportées, qui apparaissent, disparaissent, prennent un passager ; vanish ; décisions de {@code broadcastToPlayer} qui
 * changent ; vue de 2 à 10) : 69 064 états comparés, 470 746 paquets de mouvement faits par l'index, 0 différence dans « qui
 * voit quoi » ni dans les paquets envoyés ; avec une erreur volontaire, les différences sont vues et le contrôle par paire
 * coupe tout.
 */
public final class SuiviParTroncon {
   private static final Logger LOG = LogUtils.getLogger();
   /** Côté d'une case de l'index : 1 << 5 = 32 blocs (2 × 2 tronçons). */
   private static final int CASE = 5;

   private static boolean crochetVus;
   private static boolean crochetAjout;
   private static boolean crochetRetrait;
   private static boolean crochetTroncon;
   /** Faux : coupé jusqu'au redémarrage (écart avec Minecraft, changement venu d'un autre fil). */
   private static volatile boolean fiable = true;
   private static volatile Thread fil;
   private static long marque;
   /** Appels imbriqués (un écouteur appelé pendant un jugement) : faits par Minecraft. */
   private static int profondeur;
   private static int essais;
   private static int ecartsVerifies;
   private static Entree[] tampon = new Entree[256];
   private static ServerPlayerConnection[] connexions = new ServerPlayerConnection[16];
   private static final List<SuiviParTroncon> TOUS = new ArrayList<>();

   final ChunkMap carte;
   private final CarteAcces acces;
   private final Long2ObjectOpenHashMap<Case> cases = new Long2ObjectOpenHashMap<>();
   private final ArrayDeque<Case> libres = new ArrayDeque<>();
   private int nombre;
   private volatile boolean aRefaire;
   private boolean ecart;
   private boolean utilise;
   private int refaits;

   private SuiviParTroncon(ChunkMap carte, CarteAcces acces) {
      this.carte = carte;
      this.acces = acces;
      TOUS.add(this);
   }

   /** L'index d'une carte (mixin, à la première demande), ou null si l'accès à la carte manque (mixin pas appliqué). */
   public static SuiviParTroncon pour(ChunkMap carte) {
      return (Object) carte instanceof CarteAcces a ? new SuiviParTroncon(carte, a) : null;
   }

   // ------------------------------------------------------------------------------------------- entités et connexions

   /** Une entité ({@code Entity}, par mixin) et son entrée dans l'index. */
   public interface AvecEntree {
      Object heraliz$entree();

      void heraliz$entree(Object entree);
   }

   /** Une connexion de joueur ({@code ServerGamePacketListenerImpl}, par mixin) et son miroir. */
   public interface AvecVus {
      VusJoueur heraliz$vus();
   }

   /** La carte des tronçons d'un monde ({@code ChunkMap}, par mixin) et son index. */
   public interface AvecIndex {
      SuiviParTroncon heraliz$index();
   }

   /** Miroir d'une connexion : les listes {@link Vus} qui la contiennent (= les entités que ce joueur voit). */
   public static final class VusJoueur {
      final ReferenceOpenHashSet<Vus> suivis = new ReferenceOpenHashSet<>();
      /** Marques de {@link #joueurs} : dans la liste en cours, déjà jugé ; le joueur de la liste qui a cette connexion. */
      long liste;
      long fait;
      ServerPlayer joueur;
   }

   private static final class Case {
      long cle;
      Entree[] t = new Entree[8];
      int n;
   }

   private static final class Entree {
      final SuiviParTroncon index;
      final Vus vus;
      final Entity entite;
      Case cas;
      int rang;
      long marque;
      boolean morte;

      Entree(SuiviParTroncon index, Vus vus, Entity entite) {
         this.index = index;
         this.vus = vus;
         this.entite = entite;
      }
   }

   /**
    * La liste {@code seenBy} d'une entrée du suivi : la même que celle de Minecraft ({@code Sets.newIdentityHashSet()},
    * même ordre, mêmes réponses), qui note en plus chaque ajout et chaque retrait dans le miroir de la connexion.
    */
   public static final class Vus extends AbstractSet<ServerPlayerConnection> {
      private final Set<ServerPlayerConnection> base = Sets.newIdentityHashSet();
      final Object suivi;
      final SuiviEntreeAcces acces;
      SuiviParTroncon index;
      Entree entree;
      /** L'entrée n'est plus suivie (retirée de Minecraft) : plus rien n'est noté. */
      boolean mort;

      Vus(Object suivi) {
         this.suivi = suivi;
         this.acces = (SuiviEntreeAcces) suivi;
      }

      @Override
      public boolean add(ServerPlayerConnection c) {
         if (base.add(c)) {
            noter(c, true);
            return true;
         }
         return false;
      }

      @Override
      public boolean remove(Object o) {
         if (base.remove(o)) {
            if (o instanceof ServerPlayerConnection c) {
               noter(c, false);
            }
            return true;
         }
         return false;
      }

      @Override
      public boolean contains(Object o) {
         return base.contains(o);
      }

      @Override
      public int size() {
         return base.size();
      }

      @Override
      public boolean isEmpty() {
         return base.isEmpty();
      }

      @Override
      public void clear() {
         if (!base.isEmpty()) {
            for (ServerPlayerConnection c : base) {
               noter(c, false);
            }
            base.clear();
         }
      }

      @Override
      public Object[] toArray() {
         return base.toArray();
      }

      @Override
      public <T> T[] toArray(T[] a) {
         return base.toArray(a);
      }

      @Override
      public Iterator<ServerPlayerConnection> iterator() {
         Iterator<ServerPlayerConnection> it = base.iterator();
         return new Iterator<>() {
            private ServerPlayerConnection dernier;

            @Override
            public boolean hasNext() {
               return it.hasNext();
            }

            @Override
            public ServerPlayerConnection next() {
               dernier = it.next();
               return dernier;
            }

            @Override
            public void remove() {
               it.remove();
               noter(dernier, false);
            }
         };
      }

      private void noter(ServerPlayerConnection c, boolean ajout) {
         if (mort) {
            return;
         }
         Thread f = fil;
         if (f != null && Thread.currentThread() != f) {
            couper("liste des joueurs d'une entité changée depuis un autre fil que celui du serveur");
            return;
         }
         VusJoueur v = vusDe(c);
         if (v != null) {
            if (ajout) {
               v.suivis.add(this);
            } else {
               v.suivis.remove(this);
            }
         }
      }
   }

   /**
    * Création d'une entrée du suivi (mixin) : sa liste {@code seenBy}. Sans l'accès à l'entrée (mixin pas appliqué) : la liste
    * de Minecraft, et le suivi rapide n'est jamais utilisé pour cette entrée.
    */
   public static Set<ServerPlayerConnection> nouveauVus(Object suivi) {
      if (!(suivi instanceof SuiviEntreeAcces)) {
         return Sets.newIdentityHashSet();
      }
      crochetVus = true;
      return new Vus(suivi);
   }

   /** Le miroir d'une connexion (mixin, à la première demande). */
   public static VusJoueur nouveauVusJoueur() {
      return new VusJoueur();
   }

   static VusJoueur vusDe(Object connexion) {
      return connexion instanceof AvecVus a ? a.heraliz$vus() : null;
   }

   private static Entree entreeDe(Entity e) {
      return (Object) e instanceof AvecEntree a ? (Entree) a.heraliz$entree() : null;
   }

   private static long cle(ChunkPos p) {
      return ChunkPos.asLong(p.x >> 1, p.z >> 1);
   }

   // -------------------------------------------------------------------------------------------- tenue de l'index

   /** {@code ChunkMap.addEntity}, à la fin : l'entité est-elle maintenant suivie ? */
   public void ajoute(Entity e) {
      crochetAjout = true;
      if (!((Object) e instanceof AvecEntree)) {
         couper("entité sans entrée d'index (mixin d'Entity pas appliqué)");
         return;
      }
      Object t = acces.heraliz$suivis().get(e.getId());
      if (!(t instanceof SuiviEntreeAcces a) || a.heraliz$entiteSuivie() != e) {
         return;
      }
      if (!(a.heraliz$vus() instanceof Vus v)) {
         couper("une entrée du suivi sans la liste « vus » de HeraliZ (mixin de TrackedEntity pas appliqué ?)");
         return;
      }
      v.index = this;
      Entree ancienne = entreeDe(e);
      if (ancienne != null) {
         if (ancienne.index == this && ancienne.vus == v && !ancienne.morte) {
            return;
         }
         ancienne.index.oublier(ancienne);
      }
      inscrire(v, e);
   }

   private void inscrire(Vus v, Entity e) {
      Entree en = new Entree(this, v, e);
      v.entree = en;
      placer(en, cle(e.chunkPosition()));
      ((AvecEntree) e).heraliz$entree(en);
      nombre++;
   }

   /** {@code ChunkMap.removeEntity}, à la fin : l'entité n'est plus suivie. */
   public void retire(Entity e) {
      crochetRetrait = true;
      Entree en = entreeDe(e);
      if (en == null || en.index != this || en.morte) {
         return;
      }
      if (acces.heraliz$suivis().get(e.getId()) == en.vus.suivi) {
         return; // toujours suivie (retrait raté chez Minecraft) : rien ne change
      }
      oublier(en);
      Vus v = en.vus;
      v.mort = true;
      // Minecraft ne vide pas la liste d'une entrée retirée : elle sort des miroirs
      for (ServerPlayerConnection c : v.base) {
         VusJoueur vc = vusDe(c);
         if (vc != null) {
            vc.suivis.remove(v);
         }
      }
   }

   private void oublier(Entree en) {
      if (en.morte) {
         return;
      }
      en.morte = true;
      if (en.cas != null) {
         enleverDeCase(en);
      }
      nombre--;
      if (((AvecEntree) en.entite).heraliz$entree() == en) {
         ((AvecEntree) en.entite).heraliz$entree(null);
      }
      if (en.vus.entree == en) {
         en.vus.entree = null;
      }
   }

   /** {@code Entity.setPosRaw}, juste après un changement de tronçon de l'entité. */
   public static void changeDeTroncon(Entity e) {
      crochetTroncon = true;
      Entree en = entreeDe(e);
      if (en == null || en.morte) {
         return;
      }
      Thread f = fil;
      if (f != null && Thread.currentThread() != f) {
         en.index.aRefaire = true;
         return;
      }
      long k = cle(e.chunkPosition());
      if (en.cas == null || en.cas.cle != k) {
         if (en.cas != null) {
            en.index.enleverDeCase(en);
         }
         en.index.placer(en, k);
      }
   }

   private void placer(Entree en, long k) {
      Case c = cases.get(k);
      if (c == null) {
         c = libres.poll();
         if (c == null) {
            c = new Case();
         }
         c.cle = k;
         c.n = 0;
         cases.put(k, c);
      }
      if (c.n == c.t.length) {
         c.t = Arrays.copyOf(c.t, c.n * 2);
      }
      c.t[c.n] = en;
      en.rang = c.n;
      en.cas = c;
      c.n++;
   }

   private void enleverDeCase(Entree en) {
      Case c = en.cas;
      int r = en.rang;
      int dernier = --c.n;
      if (r != dernier) {
         Entree m = c.t[dernier];
         c.t[r] = m;
         m.rang = r;
      }
      c.t[dernier] = null;
      en.cas = null;
      if (c.n == 0) {
         cases.remove(c.cle);
         if (libres.size() < 512) {
            libres.add(c);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------- jugements

   private boolean pret(Int2ObjectMap<?> suivis) {
      if (!NoyauConfig.suiviParTroncon || !fiable || !crochetVus || !crochetAjout || !crochetRetrait || !crochetTroncon
            || !VoirBukkit.ok() || aRefaire) {
         return false;
      }
      if (nombre != suivis.size()) {
         ecart = true; // peut-être une entité en cours d'ajout : revu à la fin du tick
         return false;
      }
      utilise = true;
      return true;
   }

   /**
    * {@code ChunkMap.move(joueur)} : la boucle de Minecraft sur toutes les entités suivies, faite ici sur les seules
    * entités proches ou déjà vues. Faux : rien n'a été fait de définitif, Minecraft fait sa boucle (elle rejuge alors
    * aussi ce qui l'a déjà été ici : même décision, rien ne change).
    */
   public boolean bouger(Int2ObjectMap<?> suivis, ServerPlayer p) {
      if (profondeur > 0 || !pret(suivis)) {
         return false;
      }
      VusJoueur vp = vusDe(p.connection);
      if (vp == null) {
         return false;
      }
      profondeur++;
      try {
         int vue = acces.heraliz$vue();
         double px = p.getX();
         double pz = p.getZ();
         Vus propre = null;
         Object t = suivis.get(p.getId());
         if (t instanceof SuiviEntreeAcces a && a.heraliz$entiteSuivie() == p) {
            if (!(a.heraliz$vus() instanceof Vus v)) {
               return false;
            }
            propre = v;
            joueurs(v, acces.heraliz$niveau().players(), vue);
         }
         if (!candidats(p, vp, vue, propre)) {
            return false;
         }
         if (p.getX() != px || p.getZ() != pz || acces.heraliz$vue() != vue) {
            return false; // le joueur a été déplacé pendant les jugements : Minecraft refait tout
         }
         Allege.suiviMouvements++;
         return true;
      } finally {
         profondeur--;
      }
   }

   /** Les entités proches de {@code p} ou qu'il voit, jugées pour lui ({@code propre} : sa propre entrée, à part). */
   private boolean candidats(ServerPlayer p, VusJoueur vp, int vue, Vus propre) {
      long m = ++marque;
      int n = 0;
      Entree[] b = tampon;
      double px = p.getX();
      double pz = p.getZ();
      double r = vue * 16 + 1.0D;
      double r2 = r * r;
      int cx0 = Mth.floor(px - r) >> CASE;
      int cx1 = Mth.floor(px + r) >> CASE;
      int cz0 = Mth.floor(pz - r) >> CASE;
      int cz1 = Mth.floor(pz + r) >> CASE;
      try {
         for (int cx = cx0; cx <= cx1; cx++) {
            double bx = (double) (cx << CASE);
            double ex = px < bx ? bx - px : (px >= bx + 32.0D ? px - (bx + 32.0D) : 0.0D);
            for (int cz = cz0; cz <= cz1; cz++) {
               double bz = (double) (cz << CASE);
               double ez = pz < bz ? bz - pz : (pz >= bz + 32.0D ? pz - (bz + 32.0D) : 0.0D);
               if (ex * ex + ez * ez > r2) {
                  continue; // toute la case est hors de portée
               }
               Case c = cases.get(ChunkPos.asLong(cx, cz));
               if (c == null) {
                  continue;
               }
               for (int i = 0; i < c.n; i++) {
                  Entree en = c.t[i];
                  if (en.vus == propre) {
                     continue;
                  }
                  en.marque = m;
                  if (n == b.length) {
                     b = tampon = Arrays.copyOf(b, n * 2);
                  }
                  b[n++] = en;
               }
            }
         }
         for (Vus v : vp.suivis) {
            if (v.mort || v == propre) {
               continue;
            }
            Entree en = v.entree;
            if (en == null || en.morte) {
               if (v.index == null || v.index == this) {
                  return false; // entrée suivie pas encore dans l'index (ajout en cours) : Minecraft
               }
               continue;
            }
            if (en.index != this || en.marque == m) {
               continue;
            }
            en.marque = m;
            if (n == b.length) {
               b = tampon = Arrays.copyOf(b, n * 2);
            }
            b[n++] = en;
         }
         Allege.suiviCandidats += n;
         for (int i = 0; i < n; i++) {
            Entree en = b[i];
            if (!en.morte) {
               juger(en.vus, p, vp, vue);
            }
         }
         return true;
      } finally {
         Arrays.fill(b, 0, n, null);
      }
   }

   /** {@code TrackedEntity.updatePlayers(liste)} (mixin) : faux = Minecraft fait sa boucle. */
   public static boolean majJoueurs(Object suivi, List<ServerPlayer> joueurs) {
      if (profondeur > 0 || !NoyauConfig.suiviParTroncon || !fiable || !VoirBukkit.ok()) {
         return false;
      }
      if (!(suivi instanceof SuiviEntreeAcces a) || !(a.heraliz$vus() instanceof Vus v) || v.mort || v.index == null) {
         return false;
      }
      profondeur++;
      try {
         v.index.joueurs(v, joueurs, v.index.acces.heraliz$vue());
         Allege.suiviListes++;
         return true;
      } finally {
         profondeur--;
      }
   }

   /**
    * Les joueurs de {@code joueurs} à portée de l'entité, puis ceux de la liste qui la voient déjà mais sont loin (sa liste
    * {@code seenBy}, quelques joueurs), jugés pour elle ; les autres, loin et qui ne la voient pas : Minecraft ne ferait rien.
    */
   private void joueurs(Vus v, List<ServerPlayer> joueurs, int vue) {
      Entity e = v.acces.heraliz$entiteSuivie();
      double ex = e.getX();
      double ez = e.getZ();
      double max = vue * 16;
      double max2 = max * max;
      long m = ++marque;
      for (ServerPlayer q : joueurs) {
         VusJoueur vq = vusDe(q.connection);
         if (vq == null) {
            v.acces.heraliz$juger(q); // connexion sans miroir : Minecraft
            continue;
         }
         vq.liste = m;
         vq.joueur = q;
         double dx = q.getX() - ex;
         double dz = q.getZ() - ez;
         if (dx * dx + dz * dz <= max2) {
            vq.fait = m;
            juger(v, q, vq, vue);
         }
      }
      if (v.base.isEmpty()) {
         return;
      }
      ServerPlayerConnection[] b = connexions;
      int n = 0;
      for (ServerPlayerConnection c : v.base) {
         if (n == b.length) {
            b = connexions = Arrays.copyOf(b, n * 2);
         }
         b[n++] = c;
      }
      try {
         for (int i = 0; i < n; i++) {
            VusJoueur vc = vusDe(b[i]);
            if (vc != null && vc.liste == m && vc.fait != m) {
               vc.fait = m;
               juger(v, vc.joueur, vc, vue);
            }
         }
      } finally {
         Arrays.fill(b, 0, n, null);
      }
   }

   /**
    * Fin de la 1re boucle de {@code ChunkMap.tick} (mixin, à la place de {@code liste.isEmpty()}) : les joueurs qui ont
    * changé de section, que Minecraft fait juger par TOUTES les entités suivies. Vrai : fait ici (Minecraft saute sa boucle).
    */
   public boolean joueursChanges(List<?> liste) {
      if (profondeur > 0 || liste.isEmpty() || !pret(acces.heraliz$suivis())) {
         return false;
      }
      for (Object o : liste) {
         if (!(o instanceof ServerPlayer q) || vusDe(q.connection) == null) {
            return false;
         }
      }
      profondeur++;
      try {
         int vue = acces.heraliz$vue();
         for (Object o : liste) {
            ServerPlayer q = (ServerPlayer) o;
            if (!candidats(q, vusDe(q.connection), vue, null)) {
               return false; // Minecraft refait tout (mêmes décisions pour ce qui est déjà jugé)
            }
         }
         Allege.suiviSections += liste.size();
         return true;
      } finally {
         profondeur--;
      }
   }

   /**
    * La décision de {@code ChunkMap.TrackedEntity.updatePlayer(p)} de Mohist, refaite à l'identique : même distance (position
    * du joueur moins celle de l'entité, x et z), même portée ({@code min(getEffectiveRange(), vue × 16)}, calculée seulement
    * si l'entité n'est pas déjà au-delà de {@code vue × 16} : sans effet de bord), puis {@code broadcastToPlayer}, puis le test
    * de Bukkit, puis l'ajout ou le retrait du joueur et les paquets ({@code addPairing} / {@code removePairing}). Seule
    * différence : « le joueur la voit-il déjà ? » est lu dans le miroir (même réponse que {@code seenBy}).
    */
   private static void juger(Vus v, ServerPlayer p, VusJoueur vp, int vue) {
      SuiviEntreeAcces a = v.acces;
      Entity e = a.heraliz$entiteSuivie();
      if (p == e) {
         return;
      }
      double dx = p.getX() - e.getX();
      double dz = p.getZ() - e.getZ();
      double d1 = dx * dx + dz * dz;
      double max = vue * 16;
      boolean dedans;
      if (d1 > max * max) {
         dedans = false;
      } else {
         double d0 = (double) Math.min(a.heraliz$portee(), vue * 16);
         dedans = d1 <= d0 * d0 && e.broadcastToPlayer(p);
      }
      if (dedans) {
         int voit = VoirBukkit.voit(p, e);
         if (voit < 0) {
            a.heraliz$juger(p); // test de Bukkit impossible ici : Minecraft juge la paire
            return;
         }
         dedans = voit == 1;
      }
      boolean vu = vp.suivis.contains(v);
      if (dedans) {
         if (!vu && v.add(p.connection)) {
            a.heraliz$serveur().addPairing(p);
         }
      } else if (vu && v.remove(p.connection)) {
         a.heraliz$serveur().removePairing(p);
      }
      if ((++essais & 8191) == 0) {
         essai(v, p, vp);
      }
   }

   /** Une paire jugée ici est rejugée par Minecraft lui-même : rien ne doit changer. */
   private static void essai(Vus v, ServerPlayer p, VusJoueur vp) {
      boolean miroir = vp.suivis.contains(v);
      boolean liste = v.base.contains(p.connection);
      v.acces.heraliz$juger(p);
      Allege.suiviEssais++;
      if (miroir != liste || liste != v.base.contains(p.connection) || vp.suivis.contains(v) != v.base.contains(p.connection)) {
         couper("décision différente de celle de Minecraft pour " + v.acces.heraliz$entiteSuivie().getType() + " vue par un joueur");
      }
   }

   // ------------------------------------------------------------------------------------------ fin de tick, contrôles

   /** Le fil du serveur (au démarrage). */
   public static void fil(Thread f) {
      fil = f;
   }

   private static void couper(String raison) {
      if (fiable) {
         fiable = false;
         LOG.warn("[noyau] suivi des entités par tronçon coupé jusqu'au redémarrage (Minecraft reprend sa boucle) : {}", raison);
      }
   }

   /** Fin de chaque tick : index refaits si besoin ; toutes les 60 s, comparaison complète avec Minecraft. */
   public static void finDuTick(int tick) {
      if (TOUS.isEmpty()) {
         return;
      }
      for (int i = 0; i < TOUS.size(); i++) {
         SuiviParTroncon s = TOUS.get(i);
         if (s.aRefaire || (s.ecart && s.nombre != s.acces.heraliz$suivis().size())) {
            s.refaire();
         }
         s.ecart = false;
      }
      if (fiable && NoyauConfig.suiviParTroncon && tick % 1200 == 0) {
         for (int i = 0; i < TOUS.size(); i++) {
            SuiviParTroncon s = TOUS.get(i);
            if (s.utilise) {
               s.verifier();
            }
         }
      }
   }

   /** Monde déchargé (arènes après un tournoi) ou arrêt : son index est oublié. */
   public static void oublierCarte(ChunkMap carte) {
      TOUS.removeIf(s -> s.carte == carte);
   }

   public static void vider() {
      TOUS.clear();
   }

   private void refaire() {
      aRefaire = false;
      refaits++;
      Allege.suiviIndexRefaits++;
      for (Case c : cases.values()) {
         for (int i = 0; i < c.n; i++) {
            Entree en = c.t[i];
            en.morte = true;
            en.cas = null;
            if (((AvecEntree) en.entite).heraliz$entree() == en) {
               ((AvecEntree) en.entite).heraliz$entree(null);
            }
            if (en.vus.entree == en) {
               en.vus.entree = null;
            }
            c.t[i] = null;
         }
         c.n = 0;
      }
      cases.clear();
      nombre = 0;
      for (Object t : acces.heraliz$suivis().values()) {
         if (!(t instanceof SuiviEntreeAcces a) || !(a.heraliz$vus() instanceof Vus v)) {
            couper("une entrée du suivi sans la liste « vus » de HeraliZ");
            return;
         }
         Entity e = a.heraliz$entiteSuivie();
         if (!((Object) e instanceof AvecEntree)) {
            couper("entité sans entrée d'index (mixin d'Entity pas appliqué)");
            return;
         }
         v.index = this;
         Entree ancienne = entreeDe(e);
         if (ancienne != null && ancienne.index != this) {
            ancienne.index.oublier(ancienne);
         }
         inscrire(v, e);
      }
      if (refaits > 100) {
         couper("index refait plus de 100 fois depuis le démarrage");
      }
   }

   /** Miroirs des joueurs de ce monde refaits d'après les listes de Minecraft. */
   private void refaireVus() {
      for (ServerPlayer p : acces.heraliz$niveau().players()) {
         VusJoueur vp = vusDe(p.connection);
         if (vp != null) {
            vp.suivis.removeIf(v -> v.index == this || v.index == null || v.mort);
         }
      }
      for (Object t : acces.heraliz$suivis().values()) {
         if (t instanceof SuiviEntreeAcces a && a.heraliz$vus() instanceof Vus v) {
            for (ServerPlayerConnection c : v.base) {
               VusJoueur vc = vusDe(c);
               if (vc != null) {
                  vc.suivis.add(v);
               }
            }
         }
      }
   }

   /** Comparaison complète avec Minecraft (index et miroirs). */
   private void verifier() {
      Int2ObjectMap<?> suivis = acces.heraliz$suivis();
      int fautes = nombre != suivis.size() ? 1 : 0;
      for (Object t : suivis.values()) {
         if (!(t instanceof SuiviEntreeAcces a) || !(a.heraliz$vus() instanceof Vus v)) {
            fautes++;
            continue;
         }
         Entity e = a.heraliz$entiteSuivie();
         Entree en = v.entree;
         if (en == null || en.morte || en.index != this || en.entite != e || en.cas == null || en.cas.cle != cle(e.chunkPosition())
               || en.cas.t[en.rang] != en || cases.get(en.cas.cle) != en.cas) {
            fautes++;
         }
         for (ServerPlayerConnection c : v.base) {
            VusJoueur vc = vusDe(c);
            if (vc != null && !vc.suivis.contains(v)) {
               fautes++;
            }
         }
      }
      for (ServerPlayer p : acces.heraliz$niveau().players()) {
         VusJoueur vp = vusDe(p.connection);
         if (vp == null) {
            continue;
         }
         for (Vus v : vp.suivis) {
            if (v.mort || v.index != this || !v.base.contains(p.connection)) {
               fautes++;
            }
         }
      }
      Allege.suiviControles++;
      if (fautes > 0) {
         ecartsVerifies++;
         LOG.warn("[noyau] suivi des entités par tronçon : {} écart(s) avec Minecraft dans {} (index et miroirs refaits)", fautes,
               acces.heraliz$niveau().dimension().location());
         refaire();
         refaireVus();
         if (ecartsVerifies > 5) {
            couper("plus de 5 contrôles en écart avec Minecraft");
         }
      }
   }

   /** Pour {@code /diag etat}. */
   static String etat() {
      int entrees = 0;
      for (SuiviParTroncon s : TOUS) {
         entrees += s.nombre;
      }
      return (fiable ? "" : "COUPÉ, ") + entrees + " entités dans l'index";
   }
}
