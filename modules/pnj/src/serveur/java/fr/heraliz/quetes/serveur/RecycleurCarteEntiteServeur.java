package fr.heraliz.quetes.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.bloc.MenuRecycleur;
import fr.heraliz.quetes.bloc.RecycleurCarte;
import fr.heraliz.quetes.bloc.RecycleurCarteEntite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Recycleur de la carte, partie serveur (31/10, décision du propriétaire) : le contenu est À LA MACHINE (6 cases « à
 * recycler », 6 cases « récupéré »), partagé comme un coffre par tous ceux qui ouvrent l'écran
 * ({@link MenuRecycleurCarteServeur}) ; le recyclage continue sans personne, n'importe qui reprend ce qu'il y a.
 * Mêmes règles que le recycleur du spawn ({@link Recyclage}), chance du profil « carte », pas de point de saison ; les
 * hérals d'un objet qui n'a rien rendu vont à celui qui l'a déposé (même hors ligne).
 *
 * <p><b>Ne coûte rien à vide</b> : le bloc n'a un ticker que dans l'état {@link RecycleurCarte#MARCHE} (quelque chose à
 * recycler et de la place en sortie) ; tout autre changement (clic, chargement) passe par {@link #reprendre}. Tronçon
 * déchargé pendant qu'elle tournait : au retour, le temps passé est rattrapé (20 min de jeu au plus).
 *
 * <p><b>Contre la duplication, même en cas de plantage</b> : rien n'est gardé dans le monde. Le contenu est écrit HORS DU
 * MONDE ({@code heraliz_quetes/recycleurs/<monde>/<x>_<y>_<z>.dat}), d'un coup (fichier temporaire puis remplacement),
 * à CHAQUE changement, sur le fil serveur, dans un ordre qui ne peut jamais laisser un objet à deux endroits sur le
 * disque :
 * <ul>
 * <li>objet déposé (de l'inventaire ou du curseur vers la machine) : le joueur est sauvegardé d'abord (sans l'objet),
 * puis la machine (avec) ; un plantage entre les deux perd l'objet, il ne le copie pas ;</li>
 * <li>objet repris (de la machine vers l'inventaire, le curseur ou le sol) : la machine est écrite d'abord (sans
 * l'objet) ; le joueur, sauvegardé plus tard avec lui, ne peut pas l'avoir sur le disque avant ;</li>
 * <li>échange avec la barre (touches 1 à 9, F) entre une case pleine de la machine et une case pleine de l'inventaire :
 * refusé (les deux sens à la fois ; {@link MenuRecycleurCarteServeur#clicked}) ; un échange au curseur se range dans le
 * premier cas (le curseur n'est jamais sauvegardé) ;</li>
 * <li>recyclage : objet retiré et pièces ajoutées dans la même écriture, puis les hérals ;</li>
 * <li>machine cassée par le staff : contenu au sol, puis fichier effacé.</li>
 * </ul>
 * Un fichier illisible n'est jamais écrasé : la machine reste fermée (journal du serveur). Une écriture ratée ferme aussi
 * la machine jusqu'au redémarrage. Les clics sont traités un par un par le serveur : deux joueurs sur la même case, un
 * seul l'obtient.
 */
public final class RecycleurCarteEntiteServeur extends RecycleurCarteEntite {
    private static final Logger LOG = LogUtils.getLogger();
    static final int ENTREES = MenuRecycleur.ENTREES, SORTIES = MenuRecycleur.SORTIES;
    /** Rattrapage au plus 20 min de jeu après un tronçon déchargé. */
    private static final int RATTRAPAGE_MAX = 20 * 60 * 20;
    /** Machines chargées (monde + position), pour {@code /wipe tout}. */
    private static final Map<String, RecycleurCarteEntiteServeur> CHARGEES = new HashMap<>();

    final SimpleContainer entree = new SimpleContainer(ENTREES), sortie = new SimpleContainer(SORTIES);
    /** Qui a déposé l'objet de chaque case « à recycler » (ses hérals « rien »), ou null. */
    private final UUID[] deposants = new UUID[ENTREES];
    /** Écrans ouverts sur cette machine. */
    final List<MenuRecycleurCarteServeur> vues = new ArrayList<>(2);
    private int enCours = -1, progres;
    private long dernierTick;
    private boolean charge, retiree, bloquee;
    /** Données montrées par les écrans (lues par {@link MenuRecycleurCarteServeur}). */
    int dEtat = MenuRecycleur.VIDE, dAttente, dCase = -1, dDixiemes, dProgres;
    /** Verdicts des cases « à recycler », refaits quand une case change ou que les règles sont relues. */
    private final ItemStack[] vus = new ItemStack[ENTREES];
    private final Recyclage.Verdict[] verdicts = new Recyclage.Verdict[ENTREES];
    private int versionRegles = -1;
    /** Contenu juste avant un clic (pour savoir ce qui est entré ou sorti). */
    private final ItemStack[] avant = new ItemStack[ENTREES + SORTIES];
    private boolean instantane;

    public RecycleurCarteEntiteServeur(BlockPos pos, BlockState s) {
        super(pos, s);
    }

    // ------------------------------------------------------------------ chargement
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel sl)) return;
        CHARGEES.put(cle(), this);
        if (!charge) {
            charge = true;
            lire();
        }
        // après le chargement du tronçon (tâche mise en file, jamais pendant le chargement) : en marche ou à l'arrêt selon le contenu
        MinecraftServer srv = sl.getServer();
        srv.tell(new TickTask(srv.getTickCount(), this::reprendre));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel) CHARGEES.remove(cle(), this);
    }

    private String cle() {
        return level.dimension().location() + "|" + worldPosition.asLong();
    }

    private Path fichier() {
        return fichier(level.getServer(), level.dimension().location().toString(), worldPosition);
    }

    static Path dossier(MinecraftServer srv) {
        return srv.getServerDirectory().toPath().resolve("heraliz_quetes").resolve("recycleurs");
    }

    private static Path fichier(MinecraftServer srv, String monde, BlockPos pos) {
        return dossier(srv).resolve(monde.replace(':', '_').replace('/', '_'))
                .resolve(pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + ".dat");
    }

    private void lire() {
        Path f = fichier();
        if (!Files.exists(f)) return;
        try {
            CompoundTag t = NbtIo.readCompressed(f.toFile());
            lireCases(t.getList("entree", Tag.TAG_COMPOUND), entree);
            lireCases(t.getList("sortie", Tag.TAG_COMPOUND), sortie);
            ListTag d = t.getList("deposants", Tag.TAG_COMPOUND);
            for (int k = 0; k < d.size(); k++) {
                CompoundTag c = d.getCompound(k);
                int i = c.getByte("Slot") & 255;
                if (i < ENTREES && c.hasUUID("Joueur")) deposants[i] = c.getUUID("Joueur");
            }
            enCours = t.getInt("enCours");
            progres = Math.max(0, t.getInt("progres"));
            dernierTick = t.getLong("tick");
        } catch (Exception ex) {
            // jamais écrasé : la machine reste fermée, le staff regarde le fichier
            bloquee = true;
            entree.clearContent();
            sortie.clearContent();
            LOG.error("[heraliz_quetes] recycleur de la carte {} : fichier {} illisible ({}), machine fermée", worldPosition, f, ex.toString());
        }
    }

    private static void lireCases(ListTag l, SimpleContainer c) {
        for (int k = 0; k < l.size(); k++) {
            CompoundTag t = l.getCompound(k);
            int i = t.getByte("Slot") & 255;
            if (i < c.getContainerSize()) c.setItem(i, ItemStack.of(t));
        }
    }

    private static ListTag cases(SimpleContainer c) {
        ListTag l = new ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            s.save(t);
            l.add(t);
        }
        return l;
    }

    /**
     * Écrit tout le contenu d'un coup (fichier temporaire puis remplacement) ; machine vide : fichier effacé. Faux (et
     * machine fermée) si c'est impossible.
     */
    private boolean ecrire() {
        if (bloquee || retiree || !(level instanceof ServerLevel)) return false;
        Path f = fichier();
        try {
            if (vide()) {
                Files.deleteIfExists(f);
                return true;
            }
            Files.createDirectories(f.getParent());
            CompoundTag t = new CompoundTag();
            t.putInt("version", 1);
            t.put("entree", cases(entree));
            t.put("sortie", cases(sortie));
            ListTag d = new ListTag();
            for (int i = 0; i < ENTREES; i++) {
                if (deposants[i] == null) continue;
                CompoundTag c = new CompoundTag();
                c.putByte("Slot", (byte) i);
                c.putUUID("Joueur", deposants[i]);
                d.add(c);
            }
            t.put("deposants", d);
            t.putInt("enCours", enCours);
            t.putInt("progres", progres);
            t.putLong("tick", dernierTick);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            NbtIo.writeCompressed(t, tmp.toFile());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException ex) {
            bloquee = true;
            LOG.error("[heraliz_quetes] recycleur de la carte {} : écriture de {} impossible ({}), machine fermée jusqu'au redémarrage",
                    worldPosition, f, ex.toString());
            reprendre();
            return false;
        }
    }

    private boolean vide() {
        return entree.isEmpty() && sortie.isEmpty();
    }

    /** La machine peut être ouverte (pas cassée, fichier lisible, dernière écriture réussie). */
    boolean utilisable() {
        return !retiree && !bloquee && !isRemoved() && level instanceof ServerLevel;
    }

    // ------------------------------------------------------------------ clics (écran)
    ItemStack caseMachine(int i) {
        return i < ENTREES ? entree.getItem(i) : sortie.getItem(i - ENTREES);
    }

    /** Juste avant un clic d'un joueur : contenu des 12 cases. */
    void avantClic() {
        for (int i = 0; i < ENTREES + SORTIES; i++) avant[i] = caseMachine(i).copy();
        instantane = true;
    }

    /**
     * Juste après : ce qui est entré dans la machine (déposé) ou en est sorti (repris), puis la sauvegarde dans l'ordre sûr
     * (voir la description de la classe). Un clic qui ne change rien ne coûte rien. Vrai si la machine a changé.
     */
    boolean apresClic(ServerPlayer p) {
        if (!instantane) return false;
        instantane = false;
        boolean entre = false, sort = false;
        for (int i = 0; i < ENTREES + SORTIES; i++) {
            ItemStack b = avant[i], a = caseMachine(i);
            avant[i] = null;
            if (ItemStack.matches(b, a)) continue;
            boolean meme = !b.isEmpty() && !a.isEmpty() && ItemStack.isSameItemSameTags(a, b);
            if (!b.isEmpty() && (!meme || a.getCount() < b.getCount())) sort = true;
            if (!a.isEmpty() && (!meme || a.getCount() > b.getCount())) {
                entre = true;
                if (i < ENTREES) deposants[i] = p.getUUID();
            }
            if (a.isEmpty() && i < ENTREES) deposants[i] = null;
        }
        if (!entre && !sort) return false;
        // déposé : le joueur sur le disque SANS l'objet avant la machine AVEC (un plantage entre les deux perd, ne copie pas)
        if (entre) Ponts.sauverJoueur(p);
        ecrire();
        reprendre();
        return true;
    }

    // ------------------------------------------------------------------ recyclage
    /** Ticker du bloc, seulement en marche. */
    @Override
    public void tickServeur() {
        if (!(level instanceof ServerLevel) || retiree) return;
        long maintenant = level.getGameTime();
        long ecoule = dernierTick <= 0 ? 1 : maintenant - dernierTick;
        dernierTick = maintenant;
        avancer((int) Math.max(1, Math.min(RATTRAPAGE_MAX, ecoule)));
    }

    /** Avance de {@code ticks} (1 en temps normal, plus après un tronçon déchargé) ; écrit et paie si un objet est fini. */
    private void avancer(int ticks) {
        if (bloquee) {
            reprendre();
            return;
        }
        verdicts();
        List<Paiement> paiements = null;
        int budget = ticks;
        while (budget > 0) {
            int i = choisir();
            if (i < 0 || casesLibres() < Recyclage.maxPieces()) break;
            if (i != enCours) {
                enCours = i;
                progres = 0;
            }
            int total = duree(i);
            int pas = Math.max(0, Math.min(budget, total - progres));
            progres += pas;
            budget -= pas;
            if (progres < total) break;
            Paiement pa = recycler(i);
            progres = 0;
            if (pa == null) break;
            if (paiements == null) paiements = new ArrayList<>(2);
            paiements.add(pa);
            verdicts();
        }
        if (paiements != null && ecrire()) payer(paiements);
        reprendre();
    }

    /** Ce qu'un objet recyclé doit encore donner, une fois la machine écrite : hérals au déposant, quête du jour. */
    private record Paiement(UUID deposant, int objets, long herals) {
    }

    private Paiement recycler(int i) {
        Recyclage.Verdict v = verdicts[i];
        int n = quantite(i);
        ItemStack retire = entree.removeItem(i, n);
        if (retire.getCount() < n) {
            // ne devrait pas arriver (vérifié juste avant) : rien n'est perdu, ce qui a été retiré est remis
            entree.setItem(i, retire);
            return null;
        }
        Recyclage.Tirage t = Recyclage.tirer(v, n, true);
        for (ItemStack piece : t.pieces()) {
            ItemStack reste = sortie.addItem(piece);
            // impossible (assez de cases libres vérifiées avant) : au sol plutôt que perdu
            if (!reste.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, reste);
        }
        UUID qui = deposants[i];
        if (entree.getItem(i).isEmpty()) deposants[i] = null;
        level.playSound(null, worldPosition, t.pieces().isEmpty() ? SoundEvents.GRINDSTONE_USE : SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS,
                0.45F, t.pieces().isEmpty() ? 0.8F : 1.1F);
        return new Paiement(qui, n, t.herals());
    }

    private void payer(List<Paiement> paiements) {
        MinecraftServer srv = level.getServer();
        for (Paiement pa : paiements) {
            if (pa.deposant() == null) continue;
            if (pa.herals() > 0 && Ponts.deposer(pa.deposant(), pa.herals(), "Recycleur de la carte")) {
                for (MenuRecycleurCarteServeur m : vues) m.herals(pa.deposant(), pa.herals());
            }
            ServerPlayer p = srv.getPlayerList().getPlayer(pa.deposant());
            if (p != null) Journalieres.recyclage(p, pa.objets());
        }
    }

    /** Remet en marche (ou arrête) selon le contenu, et met à jour ce que montrent les écrans. */
    void reprendre() {
        if (!(level instanceof ServerLevel) || retiree || isRemoved()) return;
        verdicts();
        int i = choisir();
        boolean plein = i >= 0 && casesLibres() < Recyclage.maxPieces();
        boolean marche = !bloquee && i >= 0 && !plein;
        int total = duree(i);
        dDixiemes = total / 2;
        if (i < 0 || bloquee) {
            dCase = -1;
            dProgres = 0;
            dAttente = 0;
            dEtat = entree.isEmpty() ? MenuRecycleur.VIDE : MenuRecycleur.RIEN;
        } else {
            if (i != enCours) {
                enCours = i;
                progres = 0;
            }
            dCase = i;
            dAttente = Math.min(Short.MAX_VALUE, attente() - 1);
            dEtat = plein ? MenuRecycleur.PLEIN : MenuRecycleur.EN_COURS;
            dProgres = Math.min(1000, progres * 1000 / total);
        }
        BlockState s = level.getBlockState(worldPosition);
        if (s.getBlock() instanceof RecycleurCarte && s.getValue(RecycleurCarte.MARCHE) != marche) {
            if (marche) dernierTick = level.getGameTime();   // le temps à l'arrêt ne compte pas
            level.setBlock(worldPosition, s.setValue(RecycleurCarte.MARCHE, marche), 3);
        }
    }

    /** La case en cours si elle se recycle encore, sinon la première qui se recycle (-1 : aucune). */
    private int choisir() {
        if (enCours >= 0 && enCours < ENTREES && ok(enCours)) return enCours;
        for (int i = 0; i < ENTREES; i++) if (ok(i)) return i;
        return -1;
    }

    private boolean ok(int i) {
        Recyclage.Verdict v = verdicts[i];
        return v != null && v.etat() == Recyclage.OK && !entree.getItem(i).isEmpty();
    }

    /** Objets retirés de la case à la fois : un paquet complet, ou ce qui reste (1 à {@code par}). */
    private int quantite(int i) {
        return Math.max(1, Math.min(entree.getItem(i).getCount(), verdicts[i].par()));
    }

    /** Ticks pour l'objet (ou le paquet) de la case : comme au spawn ({@code secondes_par_objet} x part du paquet, 0,5 s au moins). */
    private int duree(int i) {
        int plein = Math.max(10, Recyclage.ticksParObjet());
        if (i < 0 || verdicts[i] == null) return plein;
        return Math.max(10, Math.round(plein * verdicts[i].part(quantite(i))));
    }

    private int attente() {
        int n = 0;
        for (int i = 0; i < ENTREES; i++) {
            if (ok(i)) n += (entree.getItem(i).getCount() + verdicts[i].par() - 1) / verdicts[i].par();
        }
        return n;
    }

    private int casesLibres() {
        int n = 0;
        for (int i = 0; i < SORTIES; i++) if (sortie.getItem(i).isEmpty()) n++;
        return n;
    }

    /** Verdicts refaits seulement pour les cases qui ont changé (ou toutes, après {@code /quete recharger}). */
    private void verdicts() {
        if (versionRegles != Recyclage.version) {
            versionRegles = Recyclage.version;
            java.util.Arrays.fill(vus, null);
        }
        Map<String, Long> ventes = null, valeurs = null;
        for (int i = 0; i < ENTREES; i++) {
            ItemStack s = entree.getItem(i);
            if (vus[i] != null && ItemStack.isSameItemSameTags(vus[i], s) && vus[i].getCount() == s.getCount()) continue;
            vus[i] = s.copy();
            if (s.isEmpty()) {
                verdicts[i] = null;
                continue;
            }
            if (ventes == null) {
                ventes = SaisonQuetes.ventes();
                valeurs = new HashMap<>();
            }
            verdicts[i] = Recyclage.verdict(s, ventes, valeurs, true);
        }
    }

    // ------------------------------------------------------------------ départ du bloc, remise à zéro
    /** Bloc cassé (staff) : contenu au sol, PUIS fichier effacé (un plantage entre les deux perd, ne copie pas). */
    @Override
    public void retire() {
        if (!(level instanceof ServerLevel) || retiree) return;
        int n = 0;
        for (int i = 0; i < ENTREES + SORTIES; i++) {
            ItemStack s = caseMachine(i);
            if (s.isEmpty()) continue;
            n += s.getCount();
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5, s.copy());
        }
        entree.clearContent();
        sortie.clearContent();
        java.util.Arrays.fill(deposants, null);
        retiree = true;
        CHARGEES.remove(cle(), this);
        if (!bloquee) {
            try {
                Files.deleteIfExists(fichier());
            } catch (IOException ex) {
                LOG.error("[heraliz_quetes] recycleur de la carte {} : fichier {} pas effacé ({})", worldPosition, fichier(), ex.toString());
            }
        }
        LOG.info("[heraliz_quetes] recycleur de la carte {} {} retiré : {} objet(s) posés au sol", level.dimension().location(), worldPosition, n);
    }

    /** {@code /wipe tout} : toutes les machines vidées (chargées ou non), fichiers effacés. Renvoie le nombre d'objets. */
    static int toutVider(MinecraftServer srv) {
        int n = 0;
        for (RecycleurCarteEntiteServeur m : new ArrayList<>(CHARGEES.values())) {
            for (int i = 0; i < ENTREES + SORTIES; i++) n += m.caseMachine(i).getCount();
            m.entree.clearContent();
            m.sortie.clearContent();
            java.util.Arrays.fill(m.deposants, null);
            m.enCours = -1;
            m.progres = 0;
            m.reprendre();
        }
        Path d = dossier(srv);
        if (Files.isDirectory(d)) {
            try (Stream<Path> fichiers = Files.walk(d)) {
                for (Path f : fichiers.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
            } catch (IOException ex) {
                LOG.error("[heraliz_quetes] remise à zéro : recycleurs de la carte pas tous effacés ({})", ex.toString());
            }
        }
        return n;
    }
}
