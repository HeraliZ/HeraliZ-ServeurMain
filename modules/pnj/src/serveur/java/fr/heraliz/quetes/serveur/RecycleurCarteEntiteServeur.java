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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Recycleur de la carte, partie serveur (31/10, décision du propriétaire) : le contenu est À LA MACHINE (6 cases « à
 * recycler », 6 cases « récupéré »), partagé comme un coffre par tous ceux qui ouvrent l'écran
 * ({@link MenuRecycleurCarteServeur}) ; le recyclage continue sans personne, n'importe qui reprend ce qu'il y a.
 * Mêmes règles que le recycleur du spawn ({@link Recyclage}), chance du profil « carte », pas de point de saison. Les
 * hérals d'un objet qui n'a rien rendu vont (18/11, demande du propriétaire) dans la BOURSE de la machine, rangée dans ses
 * sorties comme les pièces ({@link Bourses#dansMachine}) : celui qui la prend la revend à Mila ; elle se perd ou se vole
 * comme le reste. Bourses bloquées ou sortie pleine : versés au déposant, comme avant. 15/11 (audit n° 4) : une case « à
 * recycler » n'est qu'à un joueur à la fois (vide, ou déjà remplie par lui : {@link #peutDeposer}) ; avant, celui qui
 * ajoutait une balle à la case d'un autre touchait les hérals et la quête du jour de toute la case.
 *
 * <p><b>Ne coûte rien à vide</b> : le bloc n'a un ticker que dans l'état {@link RecycleurCarte#MARCHE} (quelque chose à
 * recycler et de la place en sortie) ; tout autre changement (clic, chargement) passe par {@link #reprendre}. Tronçon
 * déchargé pendant qu'elle tournait : au retour, le temps passé est rattrapé (20 min de jeu au plus).
 *
 * <p><b>Repère sur la carte du monde</b> (19/11, demande du propriétaire) : la machine se signale à HeraliZ Carte quand
 * elle est posée ou que son tronçon se charge ({@link #onLoad}), et quand elle est retirée ({@link #retire}) ; la carte
 * montre un repère « Recycleur » à tous les joueurs ({@link Ponts#repereCarte}).
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
 * <li>recyclage : objet retiré, pièces et bourse ajoutées dans la même écriture (registre des bourses écrit juste avant :
 * un plantage entre les deux perd au pire un gain, il ne copie rien) ;</li>
 * <li>machine cassée par le staff : contenu au sol, puis fichier effacé.</li>
 * </ul>
 * Un fichier illisible n'est jamais écrasé : la machine reste fermée (journal du serveur). Une écriture ratée (disque
 * plein...) ferme aussi la machine ; 21/11 (audit) : mieux vaut perdre que copier, l'ancien fichier (d'avant ce changement :
 * il pouvait encore contenir un objet qu'un joueur venait de reprendre, revenu en double après un redémarrage) est effacé
 * tout de suite, puis l'écriture est réessayée toutes les 5 s, au déchargement du tronçon et à l'arrêt du serveur ; dès
 * qu'elle passe, la machine rouvre. Un plantage avant fait perdre le contenu de la machine, jamais un objet en double.
 * Les clics sont traités un par un par le serveur : deux joueurs sur la même case, un seul l'obtient.
 */
public final class RecycleurCarteEntiteServeur extends RecycleurCarteEntite {
    private static final Logger LOG = LogUtils.getLogger();
    static final int ENTREES = MenuRecycleur.ENTREES, SORTIES = MenuRecycleur.SORTIES;
    /** Rattrapage au plus 20 min de jeu après un tronçon déchargé. */
    private static final int RATTRAPAGE_MAX = 20 * 60 * 20;
    /**
     * 18/11 (demande du propriétaire : « qu'on entende quelqu'un qui recycle ») : bruit de la machine en marche, toutes les
     * {@code BRUIT_TICKS}, porté à {@code 16 x BRUIT_VOLUME} blocs (au-delà de 1, Minecraft allonge la portée sans
     * monter le son) ; la fin de chaque objet s'entend aussi loin.
     */
    private static final int BRUIT_TICKS = 24;
    private static final float BRUIT_VOLUME = 1.5F;
    /** Machines chargées (monde + position), pour {@code /wipe tout}. */
    private static final Map<String, RecycleurCarteEntiteServeur> CHARGEES = new HashMap<>();
    /** Machines dont la dernière écriture a raté (21/11) : réessayées toutes les 5 s ({@link #reessayerEcritures}). */
    private static final Set<RecycleurCarteEntiteServeur> A_REECRIRE = new HashSet<>();

    final SimpleContainer entree = new SimpleContainer(ENTREES), sortie = new SimpleContainer(SORTIES);
    /** Qui a déposé l'objet de chaque case « à recycler » (ses hérals « rien »), ou null. */
    private final UUID[] deposants = new UUID[ENTREES];
    /** Écrans ouverts sur cette machine. */
    final List<MenuRecycleurCarteServeur> vues = new ArrayList<>(2);
    private int enCours = -1, progres;
    private long dernierTick;
    /** {@code bloquee} : fichier illisible au chargement (jamais écrit) ; {@code ecritureRatee} : dernière écriture ratée (21/11). */
    private boolean charge, retiree, bloquee, ecritureRatee;
    /** Données montrées par les écrans (lues par {@link MenuRecycleurCarteServeur}). */
    int dEtat = MenuRecycleur.VIDE, dAttente, dCase = -1, dDixiemes, dProgres;
    /** Verdicts des cases « à recycler », refaits quand une case change ou que les règles sont relues. */
    private final ItemStack[] vus = new ItemStack[ENTREES];
    private final Recyclage.Verdict[] verdicts = new Recyclage.Verdict[ENTREES];
    /** {@link Recyclage#cleVerdicts} des verdicts gardés (règles et économie de la bêta). */
    private long versionRegles = Long.MIN_VALUE;
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
        // 19/11 : repère sur la carte du monde (posée, ou tronçon chargé : rien ne part aux joueurs si elle était connue)
        Ponts.repereCarte(sl, worldPosition, true);
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
        A_REECRIRE.remove(this);
    }

    /**
     * Tronçon déchargé (21/11) : dernière chance d'écrire une machine dont l'écriture avait raté ; sinon son ancien fichier
     * reste effacé ({@link #ecrire}) : au retour du tronçon, elle est vide plutôt qu'avec un objet déjà repris.
     */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        enDechargement = true;
        if (ecritureRatee && level instanceof ServerLevel && ecrire()) {
            LOG.info("[heraliz_quetes] recycleur de la carte {} : écriture enfin passée au déchargement du tronçon", worldPosition);
        }
        A_REECRIRE.remove(this);
    }

    /** Son tronçon se décharge (plus aucun bloc changé par {@link #reprendre}). */
    private boolean enDechargement;

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
     * machine fermée) si c'est impossible : l'ancien fichier est alors effacé (mieux vaut perdre que copier) et l'écriture
     * réessayée plus tard ({@link #reessayerEcritures}) ; une écriture qui passe enfin rouvre la machine.
     */
    private boolean ecrire() {
        if (bloquee || retiree || !(level instanceof ServerLevel)) return false;
        Path f = fichier();
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try {
            if (vide()) {
                Files.deleteIfExists(f);
                ecritureReussie();
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
            NbtIo.writeCompressed(t, tmp.toFile());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            ecritureReussie();
            return true;
        } catch (IOException | RuntimeException ex) {
            boolean premiere = !ecritureRatee;
            ecritureRatee = true;
            A_REECRIRE.add(this);
            // 21/11 (audit) : l'ancien fichier date d'avant ce changement ; s'il restait, un objet qu'un joueur vient de reprendre
            // reviendrait en double après un redémarrage. Il est effacé (un effacement passe même disque plein) : un plantage
            // avant la prochaine écriture réussie fait perdre le contenu de la machine, jamais un objet en double.
            boolean efface = effacer(tmp) & effacer(f);
            if (premiere) {
                LOG.error("[heraliz_quetes] recycleur de la carte {} : écriture de {} impossible ({}) ; machine fermée, ancien fichier {}, "
                        + "nouvel essai toutes les 5 s", worldPosition, f, ex.toString(), efface ? "effacé" : "PAS effacé");
            }
            reprendre();
            return false;
        }
    }

    /** Une écriture est passée : une machine fermée par une écriture ratée rouvre (le joueur la retrouve telle quelle). */
    private void ecritureReussie() {
        if (!ecritureRatee) return;
        ecritureRatee = false;
        A_REECRIRE.remove(this);
        LOG.info("[heraliz_quetes] recycleur de la carte {} : écriture de nouveau possible, machine rouverte", worldPosition);
    }

    /** Efface un fichier ; faux (et une ligne au journal) si c'est impossible. */
    private boolean effacer(Path f) {
        try {
            Files.deleteIfExists(f);
            return true;
        } catch (IOException | RuntimeException ex) {
            LOG.error("[heraliz_quetes] recycleur de la carte {} : fichier {} impossible à effacer ({})", worldPosition, f, ex.toString());
            return false;
        }
    }

    /**
     * Toutes les 5 s, seulement s'il y en a (21/11) : machines dont l'écriture avait raté, réécrites avec ce qu'elles
     * contiennent (sur le disque, l'ancien fichier a déjà été effacé). Celles qui ne sont plus dans le monde sont oubliées.
     */
    static void reessayerEcritures(MinecraftServer srv) {
        if (A_REECRIRE.isEmpty() || srv.getTickCount() % 100 != 37) return;
        for (RecycleurCarteEntiteServeur m : new ArrayList<>(A_REECRIRE)) {
            if (m.isRemoved() || m.retiree || !(m.level instanceof ServerLevel)) {
                A_REECRIRE.remove(m);
                continue;
            }
            if (m.ecrire()) m.reprendre();
        }
    }

    /** Arrêt du serveur (21/11) : dernier essai d'écrire les machines dont l'écriture avait raté (sinon vides au retour). */
    static void arret() {
        for (RecycleurCarteEntiteServeur m : new ArrayList<>(A_REECRIRE)) {
            if (!m.isRemoved() && !m.retiree && m.level instanceof ServerLevel && m.ecrire()) {
                LOG.info("[heraliz_quetes] recycleur de la carte {} : écriture enfin passée à l'arrêt du serveur", m.worldPosition);
            }
        }
        A_REECRIRE.clear();
    }

    private boolean vide() {
        return entree.isEmpty() && sortie.isEmpty();
    }

    /**
     * 15/11 (audit n° 4) : ce joueur peut-il déposer dans la case « à recycler » n° {@code i} ? Oui si elle est vide ou déjà
     * à lui (une case d'avant, sans déposant noté, reste ouverte) : chacun touche les hérals et la quête du jour de ce qu'il
     * a déposé, jamais de ce qu'un autre a mis.
     */
    boolean peutDeposer(int i, UUID joueur) {
        if (i < 0 || i >= ENTREES || entree.getItem(i).isEmpty()) return true;
        return deposants[i] == null || deposants[i].equals(joueur);
    }

    /** La machine peut être ouverte (pas cassée, fichier lisible, dernière écriture réussie). */
    boolean utilisable() {
        return !retiree && !ferme() && !isRemoved() && level instanceof ServerLevel;
    }

    /** Machine fermée : fichier illisible, ou dernière écriture ratée (en attendant qu'elle passe). */
    private boolean ferme() {
        return bloquee || ecritureRatee;
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
        if (ferme()) {
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
        // en marche (pas de bruit pendant un rattrapage : tronçon qui revient de déchargé)
        if (ticks == 1 && !ferme() && enCours >= 0 && (level.getGameTime() + worldPosition.hashCode()) % BRUIT_TICKS == 0) {
            BlockState s = level.getBlockState(worldPosition);
            if (s.getBlock() instanceof RecycleurCarte && s.getValue(RecycleurCarte.MARCHE)) {
                level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, BRUIT_VOLUME,
                        0.6F + level.random.nextFloat() * 0.12F);
            }
        }
    }

    /**
     * Ce qu'un objet recyclé doit encore donner, une fois la machine écrite : quête du jour, message au déposant, et ses
     * hérals sur son compte seulement s'ils n'ont pas pu aller dans la bourse ({@code enBourse} : déjà dans la bourse de la
     * machine, qui contient maintenant {@code bourse}).
     */
    private record Paiement(UUID deposant, int objets, long herals, long enBourse, long bourse) {
    }

    /** Dernier message « dans la bourse du recycleur » de chaque déposant (tick du jeu) : un toutes les 20 s au plus. */
    private final Map<UUID, Long> derniersMessages = new HashMap<>(2);

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
                BRUIT_VOLUME, t.pieces().isEmpty() ? 0.8F : 1.1F);
        // 18/11 : les hérals dans la bourse des sorties, AVANT l'écriture de la machine (registre des bourses écrit d'abord)
        long h = t.herals(), bourse = h > 0 ? Bourses.dansMachine(sortie, h) : 0;
        boolean enBourse = bourse > 0;
        // quête du jour : un paquet de munitions entier compte pour un objet (15/11, Recyclage.objetsQuete)
        return new Paiement(qui, Recyclage.objetsQuete(v, n), enBourse ? 0 : h, enBourse ? h : 0, Math.max(0, bourse));
    }

    private void payer(List<Paiement> paiements) {
        MinecraftServer srv = level.getServer();
        for (Paiement pa : paiements) {
            if (pa.deposant() == null) continue;
            ServerPlayer p = srv.getPlayerList().getPlayer(pa.deposant());
            if (pa.enBourse() > 0) {
                boolean regarde = false;
                for (MenuRecycleurCarteServeur m : vues) {
                    m.herals(pa.deposant(), pa.enBourse());
                    regarde |= m.joueur.getUUID().equals(pa.deposant());
                }
                // parti plus loin : prévenu que la bourse de la machine se remplit (et qu'on peut la lui prendre)
                Long dernier = derniersMessages.get(pa.deposant());
                long maintenant = level.getGameTime();
                if (p != null && !regarde && (dernier == null || maintenant - dernier >= 400)) {
                    derniersMessages.put(pa.deposant(), maintenant);
                    Bourses.prevenir(p, pa.enBourse(), pa.bourse(), true);
                }
            } else if (pa.herals() > 0 && Ponts.deposer(pa.deposant(), pa.herals(), "Recycleur de la carte")) {
                for (MenuRecycleurCarteServeur m : vues) m.herals(pa.deposant(), pa.herals());
            }
            if (p != null && pa.objets() > 0) Journalieres.recyclage(p, pa.objets());
        }
    }

    /** Remet en marche (ou arrête) selon le contenu, et met à jour ce que montrent les écrans. */
    void reprendre() {
        // 21/11 : jamais pendant le déchargement de son tronçon (pas de bloc changé dans un tronçon qui part)
        if (!(level instanceof ServerLevel) || retiree || isRemoved() || enDechargement) return;
        verdicts();
        int i = choisir();
        boolean plein = i >= 0 && casesLibres() < Recyclage.maxPieces();
        boolean marche = !ferme() && i >= 0 && !plein;
        int total = duree(i);
        dDixiemes = total / 2;
        if (i < 0 || ferme()) {
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
            if (marche) {
                // le temps à l'arrêt ne compte pas. 19/11 (audit des duplications, B2) : l'heure du départ est aussi écrite
                // tout de suite dans le fichier ; avant, seulement en mémoire : un tronçon déchargé avant la fin du premier
                // objet relisait l'ancienne heure, et la machine rattrapait jusqu'à 20 min passées à l'arrêt
                dernierTick = level.getGameTime();
                if (!ecrire()) return;   // écriture ratée : machine fermée, déjà remise à jour par ecrire()
            }
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

    /**
     * Ticks pour l'objet (ou le paquet) de la case : {@code profils.carte.secondes_par_objet} (18/11 : 5 s, 3 au spawn) x part
     * du paquet, 0,5 s au moins.
     */
    private int duree(int i) {
        int plein = Math.max(10, Recyclage.ticksParObjet(true));
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

    /** Verdicts refaits seulement pour les cases qui ont changé (ou toutes, après {@code /recharger quetes}). */
    private void verdicts() {
        long cle = Recyclage.cleVerdicts();
        if (versionRegles != cle) {
            versionRegles = cle;
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
        A_REECRIRE.remove(this);
        // 19/11 : son repère quitte la carte du monde
        Ponts.repereCarte((ServerLevel) level, worldPosition, false);
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
