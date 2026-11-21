package fr.heraliz.loot;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Conteneurs T2-T6 posés pour un temps par un événement de HeraliZ Événements (19/11, demande du propriétaire : « en plus du
 * loot T7, deux lootables T6 aléatoires » au crash de l'hélicoptère), HORS de la rotation du loot : jamais rattachés à une
 * zone ({@link LootServeur} les cache à {@link RotationLoot}).
 * <ul>
 * <li><b>Contenu</b> : tiré à la première ouverture dans la table de leur palier ({@link TablesCoffres#pourConteneur}),
 * avec le bonus de loot de celui qui ouvre, puis gardé ; même écran de fouille qu'un conteneur de la carte (rien ne s'y
 * dépose) ; étoiles tant qu'il reste quelque chose. Ni points de saison ni quête : un conteneur d'événement ne compte pas
 * comme un conteneur de la carte (même choix que pour le coffre T7 de l'épave, 25/10).</li>
 * <li><b>Fermés</b> tant que l'événement le dit ({@code EvenementsApi.refusOuverture}, demandé à chaque essai avant de tirer
 * le contenu : le feu de l'épave, comme le coffre T7) : un message court au-dessus de la barre d'objets, sans bruit.</li>
 * <li><b>Retirés</b> à leur heure, comme un coffre T7 : 30 s après avoir été vidés, jamais sous le nez de quelqu'un qui
 * fouille (60 s de grâce au plus) ; annulés (feu pas fini) : ceux jamais ouverts sont retirés ({@link #retirer}).</li>
 * </ul>
 * Un fichier par dimension ({@code heraliz_loot_conteneurs_evenements}) : après un redémarrage, ils disparaissent quand
 * même à leur heure ; un tronçon pas chargé attend d'être chargé (jamais de chargement ici). Sans conteneur d'événement,
 * le tick de chaque seconde sort tout de suite. API appelée par réflexion par HeraliZ Événements ({@link #poser},
 * {@link #retirer}, {@link #noms}) : ne pas renommer.
 */
public final class ConteneursEvenement extends SavedData {
    private static final String FICHIER = "heraliz_loot_conteneurs_evenements";
    private static final int TAILLE = RotationLoot.TAILLE;

    /** Un conteneur d'événement : place, nom du bloc, événement, heure de retrait, contenu (tiré à la première ouverture). */
    static final class Conteneur {
        final long pos;
        final String nom;
        final String evenement;
        long expire;
        boolean genere;
        final Inventaire inv;

        Conteneur(ConteneursEvenement etat, long pos, String nom, String evenement, long expire) {
            this.pos = pos;
            this.nom = nom;
            this.evenement = evenement;
            this.expire = expire;
            this.inv = new Inventaire(etat, pos);
        }
    }

    final Long2ObjectOpenHashMap<Conteneur> conteneurs = new Long2ObjectOpenHashMap<>();
    @Nullable
    private ServerLevel niveau;

    static ConteneursEvenement get(ServerLevel l) {
        ConteneursEvenement d = l.getDataStorage().computeIfAbsent(ConteneursEvenement::charger, ConteneursEvenement::new, FICHIER);
        d.niveau = l;
        return d;
    }

    // ------------------------------------------------------------------------------------------ sauvegarde
    static ConteneursEvenement charger(CompoundTag t) {
        ConteneursEvenement d = new ConteneursEvenement();
        for (Tag e : t.getList("Conteneurs", Tag.TAG_COMPOUND)) {
            CompoundTag x = (CompoundTag) e;
            Conteneur c = new Conteneur(d, x.getLong("Pos"), x.getString("Nom"), x.getString("Evenement"), x.getLong("Expire"));
            c.genere = x.getBoolean("Genere");
            if (c.genere) ContainerHelper.loadAllItems(x, c.inv.objets);
            d.conteneurs.put(c.pos, c);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Conteneur c : conteneurs.values()) {
            CompoundTag x = new CompoundTag();
            x.putLong("Pos", c.pos);
            x.putString("Nom", c.nom);
            x.putString("Evenement", c.evenement);
            x.putLong("Expire", c.expire);
            x.putBoolean("Genere", c.genere);
            if (c.genere) ContainerHelper.saveAllItems(x, c.inv.objets);
            l.add(x);
        }
        t.put("Conteneurs", l);
        return t;
    }

    // ------------------------------------------------------------------------------------------ API (par réflexion)
    /**
     * Pose le conteneur {@code nom} (T2-T6 de HeraliZ Loot, avec une table de loot) en {@code pos}, face avant vers
     * {@code face}, pour {@code presence} ticks, au nom de l'événement {@code evenement} (« helico#12 » : c'est lui qui dit
     * s'il s'ouvre, {@code refusOuverture}). Refusé (faux) : nom inconnu, place prise (une case du conteneur n'est pas
     * libre), une case du bas sans sol solide dessous, place d'un conteneur de la rotation, ou {@code libre} qui refuse sa
     * boîte (blocs du monde ; null : pas de contrôle).
     */
    public static boolean poser(ServerLevel l, BlockPos pos, Direction face, String nom, int presence, String evenement,
                                @Nullable Predicate<AABB> libre) {
        ConteneurBlock cb = nom == null ? null : ConteneurBlock.parNom(nom);
        if (cb == null || cb.vehicule || !RotationLoot.avecLoot(cb) || face.getAxis().isVertical()) return false;
        long k = pos.asLong();
        ConteneursEvenement d = get(l);
        if (d.conteneurs.containsKey(k) || !fr.heraliz.noyau.api.Troncons.charge(l, pos) || !l.getBlockState(pos).canBeReplaced()) return false;
        if (ZonesLoot.zoneDe(l.dimension(), k) != null) return false;
        // 21/11 (validation) : toutes ses cases (et le sol dessous) dans des tronçons complets, sinon une autre place
        if (!casesPretes(l, pos, face, cb)) return false;
        if (!cb.placeLibre(l, pos, face, null) || !surLeSol(l, pos, face, cb)) return false;
        VoxelShape forme = cb.encombrement().outline(face, 0, 0, 0);
        AABB boite = forme.isEmpty() ? new AABB(pos) : forme.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        if (libre != null && !libre.test(boite)) return false;
        Conteneur c = new Conteneur(d, k, nom, evenement == null ? "" : evenement, l.getGameTime() + Math.max(20, presence));
        // connu AVANT d'être posé : la rotation ne l'adopte pas au passage (LootServeur, blocPose)
        d.conteneurs.put(k, c);
        d.setDirty();
        l.setBlock(pos, cb.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face), 3);
        if (!(l.getBlockState(pos).getBlock() instanceof ConteneurBlock pose) || !pose.nom.equals(nom)) {
            d.conteneurs.remove(k);
            return false;
        }
        HeralizLoot.LOG.info("Conteneur d'evenement {} pose en {} pour {} ({} s)", nom, pos.toShortString(), c.evenement, presence / 20);
        return true;
    }

    /**
     * Événement annulé : ses conteneurs jamais ouverts sont retirés (rien n'est donné) ; ceux d'un tronçon pas chargé le
     * seront à son chargement. -> nombre de conteneurs concernés.
     */
    public static int retirer(ServerLevel l, String evenement) {
        if (evenement == null || evenement.isEmpty()) return 0;
        ConteneursEvenement d = get(l);
        if (d.conteneurs.isEmpty()) return 0;
        LongArrayList siens = new LongArrayList();
        for (Conteneur c : d.conteneurs.values()) {
            if (!c.genere && c.evenement.equals(evenement)) siens.add(c.pos);
        }
        long now = l.getGameTime();
        for (long k : siens) {
            Conteneur c = d.conteneurs.get(k);
            BlockPos b = BlockPos.of(k);
            BlockState st = fr.heraliz.noyau.api.Troncons.etat(l, b);
            ConteneurBlock sien = st != null && st.getBlock() instanceof ConteneurBlock cb && cb.nom.equals(c.nom) ? cb : null;
            // 21/11 (validation) : retiré tout de suite seulement si toutes ses cases sont dans des tronçons complets (le
            // retrait lit et efface aussi les cases voisines) ; sinon par le tick, dès qu'elles le sont
            if (st != null && (sien == null || casesPretes(l, b, st.getValue(HorizontalDirectionalBlock.FACING), sien))) {
                d.conteneurs.remove(k);
                if (sien != null) l.removeBlock(b, false);
            } else {
                c.expire = now;
            }
        }
        if (!siens.isEmpty()) d.setDirty();
        return siens.size();
    }

    /** Noms des conteneurs de loot d'un palier (« t6 » : t6_caisse_missiles…), dans l'ordre de l'onglet. */
    public static List<String> noms(String palier) {
        List<String> l = new ArrayList<>();
        String prefixe = palier + "_";
        for (var o : HeralizLoot.ORDRE) {
            if (o.get() instanceof BlockItem bi && bi.getBlock() instanceof ConteneurBlock cb && !cb.vehicule
                    && cb.nom.startsWith(prefixe) && RotationLoot.avecLoot(cb)) {
                l.add(cb.nom);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------------------------------ rotation (LootServeur)
    /** Un conteneur d'événement est (ou va être) en {@code pos} : la rotation ne l'adopte pas. */
    static boolean estLa(ServerLevel l, BlockPos pos) {
        return get(l).conteneurs.containsKey(pos.asLong());
    }

    /** Le bloc en {@code pos} vient d'être cassé ou remplacé : si c'était un conteneur d'événement, il est oublié (vrai). */
    static boolean retire(ServerLevel l, BlockPos pos) {
        ConteneursEvenement d = get(l);
        if (d.conteneurs.remove(pos.asLong()) == null) return false;
        d.setDirty();
        return true;
    }

    /**
     * Clic droit sur un conteneur (bloc principal en {@code pos}) : faux si ce n'est pas un conteneur d'événement (la
     * rotation s'en occupe). Fermé par l'événement : son message, rien d'autre (19/11 : plus de bruit de coffre verrouillé).
     */
    static boolean ouvrir(ServerLevel l, BlockPos pos, BlockState st, ConteneurBlock cb, ServerPlayer p) {
        ConteneursEvenement d = get(l);
        Conteneur c = d.conteneurs.get(pos.asLong());
        if (c == null) return false;
        if (!c.nom.equals(cb.nom)) {
            d.conteneurs.remove(c.pos);   // autre conteneur en place : il n'est plus à l'événement
            d.setDirty();
            return false;
        }
        if (!c.genere) {
            Component refus = c.evenement.isEmpty() ? null : refus(p, c.evenement);
            if (refus != null) {
                p.displayClientMessage(refus, true);
                return true;
            }
            int bonus = BonusLoot.pourcent(p);
            remplir(c, l.random, bonus);
            c.genere = true;
            d.setDirty();
            BonusLoot.annoncer(p, bonus);
        }
        if (c.inv.isEmpty()) {
            p.displayClientMessage(Component.translatableWithFallback("heraliz_loot.conteneur.vide", "Vide.").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        Inventaire inv = c.inv;
        boolean premier = !d.regarde(inv, p);
        p.openMenu(new SimpleMenuProvider((id, pi, pl) -> new RotationLoot.MenuFouille(id, pi, inv), RotationLoot.titre(cb)));
        if (premier && p.containerMenu instanceof ChestMenu cm && cm.getContainer() == inv) RotationLoot.son(l, pos, st, true);
        return true;
    }

    /** Contenu tiré dans la table du palier du conteneur, objets placés au hasard dans les 27 cases. */
    private static void remplir(Conteneur c, RandomSource r, int bonus) {
        c.inv.objets.clear();
        TablesCoffres.Choix t = TablesCoffres.pourConteneur(c.nom);
        if (t == null || t.table().objets().isEmpty()) {
            HeralizLoot.LOG.warn("Conteneur d'evenement {} : aucune table de loot (data/heraliz_loot/coffres/)", c.nom);
            return;
        }
        int n = Math.min(TAILLE, TablesCoffres.nombre(t.table(), t.min(), t.max(), r));
        int[] cases = new int[TAILLE];
        for (int i = 0; i < TAILLE; i++) cases[i] = i;
        for (int i = TAILLE - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int x = cases[i];
            cases[i] = cases[j];
            cases[j] = x;
        }
        Set<String> deja = new HashSet<>();
        for (int i = 0; i < n; i++) {
            c.inv.objets.set(cases[i], i == 0 ? TablesCoffres.tirerPlein(t.table(), r, bonus, deja) : TablesCoffres.tirerUnique(t.table(), r, bonus, deja));
        }
    }

    // ------------------------------------------------------------------------------------------ chaque seconde
    /**
     * Toutes les secondes (LootServeur) : conteneurs à retirer (heure passée, personne dedans ; vidés : 30 s), étoiles de
     * ceux qui ont encore quelque chose. Seulement dans les tronçons chargés. Rien à faire : sort tout de suite.
     */
    static void tick(ServerLevel l) {
        ConteneursEvenement d = get(l);
        if (d.conteneurs.isEmpty()) return;
        long now = l.getGameTime();
        LongArrayList finis = null;
        boolean etoiles = ConfigLoot.etoiles() && !l.players().isEmpty();
        for (Conteneur c : d.conteneurs.values()) {
            BlockPos b = BlockPos.of(c.pos);
            if (!fr.heraliz.noyau.api.Troncons.charge(l, b)) continue;
            BlockState st = l.getBlockState(b);
            if (!(st.getBlock() instanceof ConteneurBlock cb) || !cb.nom.equals(c.nom)) {
                if (finis == null) finis = new LongArrayList();
                finis.add(c.pos);   // cassé ou remplacé
                continue;
            }
            if (c.genere && c.inv.isEmpty()) c.expire = Math.min(c.expire, now + CoffreBlockEntityServeur.SURSIS_VIDE);
            boolean occupe = d.regarde(c.inv, null);
            if (now >= c.expire && (!occupe || now >= c.expire + CoffreBlockEntityServeur.SURSIS_SPECTATEUR)) {
                // 21/11 (validation) : son retrait lit et efface aussi ses cases voisines ; une case dans un tronçon encore
                // en lecture (conteneur à cheval, joueur qui arrive de loin) faisait attendre le serveur : à la seconde suivante
                if (!casesPretes(l, b, st.getValue(HorizontalDirectionalBlock.FACING), cb)) continue;
                if (finis == null) finis = new LongArrayList();
                finis.add(c.pos);
                continue;
            }
            if (etoiles && !(c.genere && c.inv.isEmpty())) d.etoiles(l, b, st, cb);
        }
        if (finis == null) return;
        for (long k : finis) {
            Conteneur c = d.conteneurs.remove(k);
            if (c == null) continue;
            d.fermerEcrans(c);
            BlockPos b = BlockPos.of(k);
            if (l.getBlockState(b).getBlock() instanceof ConteneurBlock cb && cb.nom.equals(c.nom)) l.removeBlock(b, false);
        }
        d.setDirty();
    }

    /** Les étoiles d'un conteneur aux joueurs proches (comme les conteneurs actifs de la rotation), une fois par seconde. */
    private void etoiles(ServerLevel l, BlockPos b, BlockState st, ConteneurBlock cb) {
        int rayon = ConfigLoot.rayonEtoiles(), nb = ConfigLoot.etoilesParSeconde();
        double r2 = (double) rayon * rayon;
        List<net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>> lot = null;
        for (ServerPlayer p : l.players()) {
            if (p.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) > r2) continue;
            if (lot == null) lot = new ArrayList<>();
            lot.clear();
            RotationLoot.scintiller(l, p, b, st, cb, nb, lot);
            RotationLoot.envoyerLot(p, lot);
        }
    }

    /** Ferme l'écran de ceux qui fouillent encore ce conteneur (il va disparaître). */
    private void fermerEcrans(Conteneur c) {
        if (niveau == null) return;
        for (ServerPlayer q : new ArrayList<>(niveau.players())) {
            if (q.containerMenu instanceof ChestMenu cm && cm.getContainer() == c.inv) q.closeContainer();
        }
    }

    /** Quelqu'un regarde-t-il dans cet inventaire ? (autre que sauf) */
    private boolean regarde(Container inv, @Nullable Player sauf) {
        if (niveau == null) return false;
        for (ServerPlayer q : niveau.players()) {
            if (q != sauf && q.containerMenu instanceof ChestMenu cm && cm.getContainer() == inv) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ refus de l'événement
    @Nullable
    private static Method refusOuverture;
    private static boolean refusCherche;

    /**
     * Message de refus de l'événement {@code evenement} (EvenementsApi.refusOuverture, par réflexion), ou null : il s'ouvre.
     * Sans HeraliZ Événements, ou avec une version qui n'a pas cette fonction : jamais de refus. Aussi pour le coffre T7 d'un
     * événement ({@link CoffreBlockEntityServeur}).
     */
    @Nullable
    static Component refus(ServerPlayer p, String evenement) {
        if (!refusCherche) {
            refusCherche = true;
            try {
                refusOuverture = Class.forName("fr.heraliz.evenements.api.EvenementsApi").getMethod("refusOuverture", ServerPlayer.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                refusOuverture = null;
            }
        }
        if (refusOuverture == null) return null;
        try {
            return refusOuverture.invoke(null, p, evenement) instanceof Component c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizLoot.LOG.warn("Conteneur de l'evenement {} : refus d'ouverture illisible ({})", evenement, e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------ inventaire
    /** Contenu d'un conteneur d'événement, partagé par tous ceux qui l'ouvrent (comme un coffre). */
    static final class Inventaire implements Container {
        final ConteneursEvenement etat;
        final long pos;
        final NonNullList<ItemStack> objets = NonNullList.withSize(TAILLE, ItemStack.EMPTY);

        Inventaire(ConteneursEvenement etat, long pos) {
            this.etat = etat;
            this.pos = pos;
        }

        @Override
        public int getContainerSize() {
            return TAILLE;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : objets) {
                if (!s.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return objets.get(i);
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            ItemStack s = ContainerHelper.removeItem(objets, i, n);
            if (!s.isEmpty()) setChanged();
            return s;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack s = ContainerHelper.takeItem(objets, i);
            setChanged();
            return s;
        }

        @Override
        public void setItem(int i, ItemStack s) {
            objets.set(i, s);
            if (s.getCount() > getMaxStackSize()) s.setCount(getMaxStackSize());
            setChanged();
        }

        @Override
        public void setChanged() {
            etat.setDirty();
        }

        @Override
        public boolean stillValid(Player p) {
            Conteneur c = etat.conteneurs.get(pos);
            if (c == null || c.inv != this || etat.niveau == null || p.level() != etat.niveau) return false;
            return p.distanceToSqr(BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.5, BlockPos.getZ(pos) + 0.5) <= 36;
        }

        @Override
        public void clearContent() {
            objets.clear();
            setChanged();
        }

        @Override
        public void stopOpen(Player p) {
            Conteneur c = etat.conteneurs.get(pos);
            if (c == null || c.inv != this || etat.niveau == null || etat.regarde(this, p)) return;
            BlockPos b = BlockPos.of(pos);
            RotationLoot.son(etat.niveau, b, etat.niveau.getBlockState(b), false);
        }
    }

    /**
     * 21/11 (validation) : les tronçons de TOUTES les cases du conteneur (posé en {@code pos}, face avant vers {@code face})
     * sont complets, comme pour les coffres T7 ({@code PointsT7.pret}). Un conteneur de plusieurs blocs (12 des 49 T6)
     * peut déborder sur le tronçon voisin : le poser ou le retirer lit et change aussi ses cases et le sol dessous (mêmes
     * colonnes) ; dans un tronçon encore en lecture, le fil du serveur attendait la fin de sa lecture. Ne charge rien,
     * n'attend rien.
     */
    private static boolean casesPretes(ServerLevel l, BlockPos pos, Direction face, ConteneurBlock cb) {
        for (int[] c : cb.encombrement().cells) {
            int[] w = Encombrement.rotateCell(c[0], c[1], c[2], face);
            if (fr.heraliz.noyau.api.Troncons.pret(l, (pos.getX() + w[0]) >> 4, (pos.getZ() + w[2]) >> 4) == null) return false;
        }
        return true;
    }

    /** Chaque case du bas du conteneur repose sur un bloc solide (aucun coin dans le vide). */
    private static boolean surLeSol(ServerLevel l, BlockPos pos, Direction face, ConteneurBlock cb) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int[] c : cb.encombrement().cells) {
            if (c[1] != 0) continue;
            int[] w = Encombrement.rotateCell(c[0], c[1], c[2], face);
            m.set(pos.getX() + w[0], pos.getY() - 1, pos.getZ() + w[2]);
            if (!l.getBlockState(m).isFaceSturdy(l, m, Direction.UP)) return false;
        }
        return true;
    }
}
