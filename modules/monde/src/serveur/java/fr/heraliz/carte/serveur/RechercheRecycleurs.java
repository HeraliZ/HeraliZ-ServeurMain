package fr.heraliz.carte.serveur;

import fr.heraliz.carte.HeralizCarte;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recherche des recycleurs de la carte posés avant leurs repères (19/11) : leurs positions n'étaient notées nulle part (les
 * fichiers {@code heraliz_quetes/recycleurs/} n'existent que pour les machines qui contiennent quelque chose). Faite UNE
 * fois, toute seule, au premier démarrage avec cette version (une minute après), puis notée faite dans
 * {@link RecycleursData} ; {@code /carte recycleurs chercher} la relance (staff).
 * <ul>
 *     <li><b>Sans charger le monde</b> : chaque tronçon ENREGISTRÉ de chaque monde (fichiers {@code region/r.X.Z.mca}
 *     présents) est lu par le lecteur de fichiers du monde lui-même ({@code ServerChunkCache.chunkScanner()}, la file de
 *     lecture du jeu, qui voit aussi les tronçons en attente d'écriture), jamais chargé dans le monde. Rien n'est jamais
 *     écrit dans les fichiers du monde.</li>
 *     <li><b>Léger</b> : de chaque tronçon, seule la liste {@code block_entities} est lue (identifiant et position de
 *     chaque entité de bloc ; tout le reste est sauté sans être gardé en mémoire), et la lecture s'arrête au bout de cette
 *     liste. Cherché : {@code heraliz_quetes:recycleur_carte}.</li>
 *     <li><b>Sans ralentir le serveur</b> : au plus {@value #EN_COURS_MAX} lectures en cours (la file est partagée avec les
 *     tronçons que demandent les joueurs : ils n'attendent jamais plus que ces quelques lectures), au plus
 *     {@value #PAR_TICK} nouvelles par tick, et rien de nouveau tant que le tick moyen dépasse {@value #TICK_LENT_MS} ms.
 *     Sur le fil serveur : seulement les demandes et la mise à jour des repères ; la lecture se fait sur le fil des
 *     fichiers. Carte de 4 500 x 4 500 blocs (100 régions) : 5 à 12 minutes environ.</li>
 *     <li><b>Journal</b> : lancement, avancement toutes les 30 s, chaque recycleur trouvé, résultat (et au staff qui l'a
 *     lancée).</li>
 * </ul>
 * Tronçon chargé pendant la recherche : son état en jeu compte, pas celui du disque. À la fin, la liste est refaite : un
 * repère dont la machine n'est ni sur le disque ni en jeu est retiré (sauf si une lecture a échoué : alors rien n'est
 * retiré, par prudence). Arrêt du serveur en cours de route : rien n'est noté, la recherche automatique recommencera.
 */
final class RechercheRecycleurs {
    /** Lectures en cours au plus. */
    static final int EN_COURS_MAX = 8;
    /** Nouvelles lectures demandées par tick au plus. */
    static final int PAR_TICK = 16;
    /** Tick moyen (ms) au-delà duquel la recherche attend : le jeu passe d'abord. */
    static final float TICK_LENT_MS = 40f;
    /** Attente après le démarrage du serveur avant la recherche automatique (ticks) : laisser passer les reconnexions. */
    private static final int ATTENTE_DEMARRAGE = 60 * 20;
    private static final long JOURNAL_MS = 30_000L;
    /** Tronçons par fichier de région (32 x 32). */
    private static final int PAR_REGION = 1024;
    /** Taille d'un fichier de région sans aucun tronçon (son en-tête seul). */
    private static final long ENTETE_REGION = 8192L;
    private static final String ID = RecycleursCarte.ID.toString();

    @Nullable
    private static Recherche enCours;

    private RechercheRecycleurs() {
    }

    private record Monde(ResourceKey<Level> cle, Path dossier) {
    }

    private record Region(ResourceKey<Level> monde, int rx, int rz) {
    }

    /**
     * Régions à lire, et mondes dont le dossier des régions est absent ou illisible (rien n'y est lu : aucun repère n'y
     * sera retiré).
     */
    private record Liste(List<Region> regions, Set<ResourceKey<Level>> nonLus) {
    }

    private record Trouve(ResourceKey<Level> monde, long pos) {
    }

    static boolean active() {
        return enCours != null;
    }

    /** Avancement de la recherche en cours : {pourcentage, tronçons lus, tronçons à lire, trouvés sur le disque}, ou null. */
    @Nullable
    static int[] avancement() {
        Recherche r = enCours;
        if (r == null) return null;
        int lus = r.lus.get(), total = r.total;
        return new int[]{total <= 0 ? 0 : (int) (100L * lus / total), lus, total, r.nombreSurDisque};
    }

    /**
     * Lance la recherche (rien si une est déjà en cours). automatique : premier démarrage avec cette version (elle attend
     * {@link #ATTENTE_DEMARRAGE}), sinon demandée par le staff ; demandeur : le joueur à prévenir à la fin (null : la
     * console, ou personne).
     */
    static boolean lancer(MinecraftServer srv, @Nullable UUID demandeur, boolean automatique) {
        if (enCours != null) return false;
        int attente = automatique ? ATTENTE_DEMARRAGE : 0;
        Path racine = srv.getWorldPath(LevelResource.ROOT);
        List<Monde> mondes = new ArrayList<>();
        Set<ResourceKey<Level>> cles = new HashSet<>();
        for (ServerLevel l : srv.getAllLevels()) {
            mondes.add(new Monde(l.dimension(), DimensionType.getStorageFolder(l.dimension(), racine).resolve("region")));
            cles.add(l.dimension());
        }
        // la carte du monde (monde principal) d'abord
        mondes.sort(Comparator.comparing((Monde m) -> m.cle() != Level.OVERWORLD).thenComparing(m -> m.cle().location().toString()));
        CompletableFuture<Liste> liste = CompletableFuture.supplyAsync(() -> regions(mondes), Util.ioPool());
        enCours = new Recherche(srv, demandeur, srv.getTickCount() + attente, liste, cles);
        if (automatique) {
            HeralizCarte.LOG.info("[carte] recherche automatique des recycleurs de la carte dans {} s (première fois avec cette version) :"
                    + " tronçons enregistrés relus sans charger le monde, {} lectures en cours au plus", attente / 20, EN_COURS_MAX);
        } else {
            HeralizCarte.LOG.info("[carte] recherche des recycleurs de la carte lancée par le staff");
        }
        return true;
    }

    /** Arrêt du serveur : la recherche s'arrête là (les lectures encore en cours se finissent sans suite). */
    static void arreter() {
        Recherche r = enCours;
        if (r == null) return;
        enCours = null;
        HeralizCarte.LOG.info("[carte] recherche des recycleurs arrêtée avec le serveur ({} tronçons lus sur {}) : pas notée faite",
                r.lus.get(), r.total);
    }

    /** Une machine s'est signalée (posée, chargée, retirée) pendant la recherche : c'est elle qui a raison. */
    static void touche(ResourceKey<Level> monde, long pos) {
        Recherche r = enCours;
        if (r != null) r.touchees.computeIfAbsent(monde, k -> new LongOpenHashSet()).add(pos);
    }

    /** Tick du serveur : rien du tout hors recherche. */
    static void tick(TickEvent.ServerTickEvent e) {
        Recherche r = enCours;
        if (r == null || e.phase != TickEvent.Phase.END) return;
        try {
            r.pas();
        } catch (RuntimeException ex) {
            enCours = null;
            HeralizCarte.LOG.error("[carte] recherche des recycleurs arrêtée sur une erreur (pas notée faite)", ex);
        }
    }

    /** Fil des fichiers : les régions présentes de chaque monde (un fichier sans tronçon est sauté). */
    private static Liste regions(List<Monde> mondes) {
        List<Region> out = new ArrayList<>();
        Set<ResourceKey<Level>> nonLus = new HashSet<>();
        for (Monde m : mondes) {
            if (!Files.isDirectory(m.dossier())) {
                nonLus.add(m.cle());
                continue;
            }
            List<Region> ici = new ArrayList<>();
            try (DirectoryStream<Path> fichiers = Files.newDirectoryStream(m.dossier(), "r.*.mca")) {
                for (Path f : fichiers) {
                    String[] p = f.getFileName().toString().split("\\.");
                    if (p.length != 4) continue;
                    try {
                        if (Files.size(f) <= ENTETE_REGION) continue;
                        ici.add(new Region(m.cle(), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                    } catch (NumberFormatException | IOException ignore) {
                        // nom inattendu ou fichier parti entre-temps : sauté
                    }
                }
            } catch (IOException | RuntimeException ex) {
                nonLus.add(m.cle());
                HeralizCarte.LOG.warn("[carte] recherche des recycleurs : dossier {} illisible ({})", m.dossier(), ex.toString());
            }
            ici.sort(Comparator.comparingInt(Region::rz).thenComparingInt(Region::rx));
            out.addAll(ici);
        }
        return new Liste(out, nonLus);
    }

    private static MutableComponent tr(String cle, String francais, Object... args) {
        return Component.translatableWithFallback(cle, francais, args);
    }

    // ------------------------------------------------------------------ une recherche
    private static final class Recherche {
        final MinecraftServer srv;
        @Nullable
        final UUID demandeur;
        final int debutTick;
        /** Début des lectures (ms), après l'attente du démarrage. */
        long debutMs;
        final CompletableFuture<Liste> liste;
        final Set<ResourceKey<Level>> mondes;
        @Nullable
        List<Region> regions;
        Set<ResourceKey<Level>> incomplets = new HashSet<>();
        /** Prochain tronçon à demander : région et rang dans la région. */
        int region, rang;
        int total, nombreSurDisque, ajoutes, retires;
        long prochainJournal;
        /** Compteurs tenus par le fil des fichiers. */
        final AtomicInteger enVol = new AtomicInteger(), lus = new AtomicInteger(), erreurs = new AtomicInteger();
        /** Machines lues sur le disque, en attente du fil serveur. */
        final ConcurrentLinkedQueue<Trouve> trouves = new ConcurrentLinkedQueue<>();
        final Map<ResourceKey<Level>, LongOpenHashSet> surDisque = new HashMap<>();
        /** Positions signalées par les machines elles-mêmes pendant la recherche ({@link #touche}). */
        final Map<ResourceKey<Level>, LongOpenHashSet> touchees = new HashMap<>();

        Recherche(MinecraftServer srv, @Nullable UUID demandeur, int debutTick, CompletableFuture<Liste> liste, Set<ResourceKey<Level>> mondes) {
            this.srv = srv;
            this.demandeur = demandeur;
            this.debutTick = debutTick;
            this.liste = liste;
            this.mondes = mondes;
        }

        /** Fil des fichiers : une machine trouvée dans un tronçon enregistré. */
        void trouve(ResourceKey<Level> monde, long pos) {
            trouves.add(new Trouve(monde, pos));
        }

        void pas() {
            if (srv.getTickCount() < debutTick) return;
            if (regions == null) {
                if (!liste.isDone()) return;
                Liste l = liste.join();
                regions = l.regions();
                incomplets.addAll(l.nonLus());
                total = regions.size() * PAR_REGION;
                debutMs = Util.getMillis();
                prochainJournal = debutMs + JOURNAL_MS;
                Set<ResourceKey<Level>> avecRegions = new HashSet<>();
                for (Region r : regions) avecRegions.add(r.monde());
                HeralizCarte.LOG.info("[carte] recherche des recycleurs : {} région(s) dans {} monde(s), {} tronçons au plus, {} par tick au plus",
                        regions.size(), avecRegions.size(), total, PAR_TICK);
            }
            recevoir();
            if (srv.getAverageTickTime() <= TICK_LENT_MS) {
                for (int n = 0; n < PAR_TICK && region < regions.size() && enVol.get() < EN_COURS_MAX; n++) {
                    lire(regions.get(region), rang);
                    if (++rang == PAR_REGION) {
                        rang = 0;
                        region++;
                    }
                }
            }
            long t = Util.getMillis();
            if (t >= prochainJournal) {
                prochainJournal = t + JOURNAL_MS;
                int lu = lus.get();
                HeralizCarte.LOG.info("[carte] recherche des recycleurs : {} % ({} tronçons sur {}), {} recycleur(s) trouvé(s), {} ajouté(s)",
                        total <= 0 ? 100 : 100L * lu / total, lu, total, nombreSurDisque, ajoutes);
            }
            if (region >= regions.size() && enVol.get() == 0) {
                recevoir();
                finir();
            }
        }

        /** Demande la lecture d'un tronçon au fil des fichiers de son monde (jamais un chargement). */
        private void lire(Region r, int i) {
            ServerLevel l = srv.getLevel(r.monde());
            if (l == null) {
                incomplets.add(r.monde());
                lus.incrementAndGet();
                return;
            }
            ChunkPos cp = new ChunkPos((r.rx() << 5) + (i & 31), (r.rz() << 5) + (i >> 5));
            enVol.incrementAndGet();
            try {
                l.getChunkSource().chunkScanner().scanChunk(cp, new Lecteur(this, r.monde())).whenComplete((v, ex) -> {
                    if (ex != null) erreurs.incrementAndGet();
                    lus.incrementAndGet();
                    enVol.decrementAndGet();
                });
            } catch (RuntimeException ex) {
                erreurs.incrementAndGet();
                lus.incrementAndGet();
                enVol.decrementAndGet();
            }
        }

        /** Fil serveur : machines lues sur le disque ; tronçon chargé : c'est le bloc en jeu qui compte. */
        private void recevoir() {
            Trouve t;
            while ((t = trouves.poll()) != null) {
                if (!surDisque.computeIfAbsent(t.monde(), k -> new LongOpenHashSet()).add(t.pos())) continue;
                nombreSurDisque++;
                if (touchee(t.monde(), t.pos())) continue;
                ServerLevel l = srv.getLevel(t.monde());
                if (l == null) continue;
                BlockPos pos = BlockPos.of(t.pos());
                LevelChunk c = l.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (c != null && !RecycleursCarte.estRecycleur(c, pos)) continue;
                if (RecycleursCarte.ajouter(srv, t.monde(), t.pos())) {
                    ajoutes++;
                    HeralizCarte.LOG.info("[carte] recherche : recycleur de la carte trouvé en {} {} {} ({})", pos.getX(), pos.getY(), pos.getZ(),
                            t.monde().location());
                }
            }
        }

        private boolean touchee(ResourceKey<Level> monde, long pos) {
            LongOpenHashSet s = touchees.get(monde);
            return s != null && s.contains(pos);
        }

        /** Tout est lu : repères sans machine retirés (s'il n'y a eu aucune erreur), recherche notée faite, résultat. */
        private void finir() {
            enCours = null;
            int err = erreurs.get();
            if (err == 0) {
                for (RecycleursData.Recycleur r : RecycleursCarte.tous(srv)) {
                    if (!mondes.contains(r.monde()) || incomplets.contains(r.monde())) continue;
                    long p = r.pos().asLong();
                    LongOpenHashSet vus = surDisque.get(r.monde());
                    if (touchee(r.monde(), p) || (vus != null && vus.contains(p))) continue;
                    ServerLevel l = srv.getLevel(r.monde());
                    if (l == null) continue;
                    LevelChunk c = l.getChunkSource().getChunkNow(r.pos().getX() >> 4, r.pos().getZ() >> 4);
                    if (c != null && RecycleursCarte.estRecycleur(c, r.pos())) continue;   // posée depuis le dernier enregistrement
                    if (RecycleursCarte.retirer(srv, r.monde(), p)) {
                        retires++;
                        HeralizCarte.LOG.info("[carte] recherche : plus de recycleur en {} {} {} ({}) : repère retiré", r.pos().getX(), r.pos().getY(),
                                r.pos().getZ(), r.monde().location());
                    }
                }
            }
            RecycleursData d = RecycleursCarte.donnees(srv);
            d.rechercheFinie(System.currentTimeMillis());
            long s = Math.max(0L, (Util.getMillis() - debutMs) / 1000L);
            HeralizCarte.LOG.info("[carte] recherche des recycleurs finie en {} min {} s : {} recycleur(s) sur la carte ({} ajouté(s), {} retiré(s)),"
                    + " {} tronçons lus, {} erreur(s) de lecture", s / 60, s % 60, d.nombre(), ajoutes, retires, lus.get(), err);
            if (err > 0) {
                HeralizCarte.LOG.warn("[carte] recherche des recycleurs : {} tronçon(s) illisible(s), aucun repère retiré par prudence", err);
            }
            ServerPlayer p = demandeur == null ? null : srv.getPlayerList().getPlayer(demandeur);
            if (p == null) return;
            p.sendSystemMessage(tr("heraliz_carte.cmd.recycleurs.finie", "Recherche des recycleurs finie (%s min %s s) : %s recycleur(s) sur la carte (%s ajouté(s), %s retiré(s)).",
                    String.valueOf(s / 60), String.valueOf(s % 60), String.valueOf(d.nombre()), String.valueOf(ajoutes), String.valueOf(retires))
                    .withStyle(ChatFormatting.GREEN));
            if (err > 0) {
                p.sendSystemMessage(tr("heraliz_carte.cmd.recycleurs.erreurs", "%s tronçon(s) illisible(s) : aucun repère retiré par prudence (journal du serveur).",
                        String.valueOf(err)).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ------------------------------------------------------------------ lecture d'un tronçon (fil des fichiers)
    /**
     * Lit un tronçon enregistré au fil de l'eau : seulement la liste {@code block_entities} (pour chaque entité de bloc :
     * {@code id}, {@code x}, {@code y}, {@code z}) ; tout le reste est sauté sans être gardé, et la lecture s'arrête au bout
     * de la liste. Un lecteur par tronçon.
     */
    private static final class Lecteur implements StreamTagVisitor {
        private static final int RACINE = 0, LISTE = 1, ENTITE = 2, FINI = 3;
        private static final int AUCUNE = 0, CLE_ID = 1, CLE_X = 2, CLE_Y = 3, CLE_Z = 4;
        private final Recherche recherche;
        private final ResourceKey<Level> monde;
        private int etat = RACINE, cle, x, y, z, vus;
        private boolean recycleur;

        Lecteur(Recherche recherche, ResourceKey<Level> monde) {
            this.recherche = recherche;
            this.monde = monde;
        }

        @Override
        public ValueResult visitRootEntry(TagType<?> type) {
            return type == CompoundTag.TYPE ? ValueResult.CONTINUE : ValueResult.HALT;
        }

        @Override
        public EntryResult visitEntry(TagType<?> type) {
            return etat == RACINE || etat == ENTITE ? EntryResult.ENTER : EntryResult.SKIP;
        }

        @Override
        public EntryResult visitEntry(TagType<?> type, String nom) {
            if (etat == RACINE) {
                if (type != ListTag.TYPE || !nom.equals("block_entities")) return EntryResult.SKIP;
                etat = LISTE;
                return EntryResult.ENTER;
            }
            if (etat != ENTITE) return EntryResult.SKIP;
            if (type == StringTag.TYPE && nom.equals("id")) cle = CLE_ID;
            else if (type == IntTag.TYPE && nom.equals("x")) cle = CLE_X;
            else if (type == IntTag.TYPE && nom.equals("y")) cle = CLE_Y;
            else if (type == IntTag.TYPE && nom.equals("z")) cle = CLE_Z;
            else return EntryResult.SKIP;
            return EntryResult.ENTER;
        }

        @Override
        public ValueResult visitList(TagType<?> type, int taille) {
            // liste des entités de bloc : des composés (vide : type « fin ») ; autre chose : sautée
            return etat == LISTE && type == CompoundTag.TYPE ? ValueResult.CONTINUE : ValueResult.BREAK;
        }

        @Override
        public EntryResult visitElement(TagType<?> type, int rang) {
            if (etat != LISTE || type != CompoundTag.TYPE) return EntryResult.SKIP;
            etat = ENTITE;
            cle = AUCUNE;
            vus = 0;
            recycleur = false;
            return EntryResult.ENTER;
        }

        @Override
        public ValueResult visit(String s) {
            if (etat == ENTITE && cle == CLE_ID) recycleur = ID.equals(s);
            cle = AUCUNE;
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(int v) {
            if (etat == ENTITE) {
                if (cle == CLE_X) {
                    x = v;
                    vus |= 1;
                } else if (cle == CLE_Y) {
                    y = v;
                    vus |= 2;
                } else if (cle == CLE_Z) {
                    z = v;
                    vus |= 4;
                }
            }
            cle = AUCUNE;
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visitContainerEnd() {
            if (etat == ENTITE) {
                // fin d'une entité de bloc
                if (recycleur && vus == 7) recherche.trouve(monde, BlockPos.asLong(x, y, z));
                etat = LISTE;
                return ValueResult.CONTINUE;
            }
            if (etat == LISTE) {
                // fin de la liste : rien d'autre à lire dans ce tronçon
                etat = FINI;
                return ValueResult.HALT;
            }
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visitEnd() {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(byte v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(short v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(long v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(float v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(double v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(byte[] v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(int[] v) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(long[] v) {
            return ValueResult.CONTINUE;
        }
    }
}
