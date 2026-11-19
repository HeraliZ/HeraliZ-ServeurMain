package fr.heraliz.carte.serveur;

import fr.heraliz.carte.HeralizCarte;
import fr.heraliz.carte.Lieu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Un repère sur la carte du monde pour chaque recycleur de la carte (19/11, demande du propriétaire : « chaque recycleur
 * doit avoir un petit point sur la carte, posé tout seul dès qu'il est posé : je ne sais plus où je les ai mis »). Les
 * repères sont des lieux de type {@link Lieu.Type#RECYCLEUR}, ajoutés à la liste des lieux envoyée à chaque joueur à sa
 * connexion et à tous à chaque changement ({@link LieuxData#paquet}) ; ils sont gardés à part des lieux du staff
 * ({@link RecycleursData}) : {@code /carte lieu retirer} ne les voit pas et {@code /carte lieu ajouter recycleur} est refusé.
 * Le seul moyen d'enlever un repère est d'enlever la machine.
 * <ul>
 *     <li><b>Posé, chargé</b> : la machine le dit elle-même (HeraliZ Quêtes, {@code RecycleurCarteEntiteServeur.onLoad},
 *     par réflexion : {@link fr.heraliz.carte.api.RecycleursApi#signaler}) ; une machine pas encore connue apparaît
 *     aussitôt chez tout le monde, y compris quand son tronçon se charge.</li>
 *     <li><b>Cassée</b> (staff) : pareil, depuis {@code RecycleurCarteEntiteServeur.retire} ; le repère part aussitôt.</li>
 *     <li><b>Partie sans le dire</b> (WorldEdit qui écrit le monde directement…) : au chargement d'un tronçon qui a un
 *     recycleur connu, le bloc est relu ; si ce n'est plus un recycleur, le repère part ({@link #troncon}).</li>
 *     <li><b>Machines d'avant cette version</b> : retrouvées une fois par la recherche automatique
 *     ({@link RechercheRecycleurs}), relancée au besoin par {@code /carte recycleurs chercher}.</li>
 * </ul>
 * Ne coûte rien quand rien ne change : aucun tick ; un tronçon chargé sans recycleur connu coûte deux lectures de table ;
 * les changements d'un même tick partent aux joueurs en un seul envoi. Fil serveur.
 */
public final class RecycleursCarte {
    /** Identifiant figé du bloc (et de son entité de bloc) : HeraliZ Quêtes. */
    static final ResourceLocation ID = new ResourceLocation("heraliz_quetes", "recycleur_carte");

    @Nullable
    private static RecycleursData donnees;
    @Nullable
    private static Block bloc;
    private static boolean blocCherche, diffusionPrevue;

    private RecycleursCarte() {
    }

    /** Registre des recycleurs (lu une fois, gardé jusqu'à l'arrêt du serveur). Fil serveur. */
    static RecycleursData donnees(MinecraftServer srv) {
        RecycleursData d = donnees;
        if (d == null) donnees = d = RecycleursData.de(srv);
        return d;
    }

    /** Le bloc du recycleur de la carte, ou null sans HeraliZ Quêtes (alors rien n'est jamais retiré). */
    @Nullable
    static Block bloc() {
        if (!blocCherche) {
            blocCherche = true;
            bloc = ForgeRegistries.BLOCKS.containsKey(ID) ? ForgeRegistries.BLOCKS.getValue(ID) : null;
            if (bloc == null) HeralizCarte.LOG.info("[carte] {} absent : pas de repère pour les recycleurs de la carte", ID);
        }
        return bloc;
    }

    /** Repères pour la liste des lieux envoyée aux joueurs. */
    static List<Lieu> lieux(MinecraftServer srv) {
        return donnees(srv).lieux();
    }

    static List<RecycleursData.Recycleur> tous(MinecraftServer srv) {
        return donnees(srv).tous();
    }

    // ------------------------------------------------------------------ ce que dit la machine
    /**
     * Voir {@link fr.heraliz.carte.api.RecycleursApi#signaler} (appelé par la machine elle-même : posée, chargée ou
     * retirée). Une erreur ici est notée au journal, jamais renvoyée à la machine.
     */
    public static void signaler(ServerLevel level, BlockPos pos, boolean present) {
        try {
            MinecraftServer srv = level.getServer();
            if (!srv.isSameThread()) {
                BlockPos p = pos.immutable();
                srv.execute(() -> signaler(level, p, present));
                return;
            }
            ResourceKey<Level> monde = level.dimension();
            long p = pos.asLong();
            RechercheRecycleurs.touche(monde, p);
            RecycleursData d = donnees(srv);
            if (present ? d.ajouter(monde, p) : d.retirer(monde, p)) {
                HeralizCarte.LOG.info(present ? "[carte] recycleur de la carte repéré : {} {} {} ({}), {} en tout"
                        : "[carte] recycleur de la carte retiré : {} {} {} ({}), {} en tout", pos.getX(), pos.getY(), pos.getZ(),
                        monde.location(), d.nombre());
                diffuser(srv);
            }
        } catch (RuntimeException ex) {
            HeralizCarte.LOG.error("[carte] repère du recycleur de la carte {} impossible", pos, ex);
        }
    }

    /** Ajout trouvé par la recherche (déjà vérifié par elle). Vrai si la machine n'était pas connue. */
    static boolean ajouter(MinecraftServer srv, ResourceKey<Level> monde, long pos) {
        if (!donnees(srv).ajouter(monde, pos)) return false;
        diffuser(srv);
        return true;
    }

    /** Repère dont la machine n'est plus là. Vrai s'il était connu. */
    static boolean retirer(MinecraftServer srv, ResourceKey<Level> monde, long pos) {
        if (!donnees(srv).retirer(monde, pos)) return false;
        diffuser(srv);
        return true;
    }

    // ------------------------------------------------------------------ chargement d'un tronçon
    /**
     * Tronçon chargé : ses recycleurs connus sont-ils toujours là ? Une machine retirée sans passer par le jeu (WorldEdit qui
     * écrit le monde directement…) perd ici son repère. Les machines présentes, elles, se signalent seules
     * ({@link #signaler}). Rien n'est lu ni créé pour un tronçon sans recycleur connu (presque tous).
     */
    static void troncon(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel l)) return;
        try {
            MinecraftServer srv = l.getServer();
            RecycleursData d = donnees;
            if (d == null) {
                // pendant « Preparing level », le monde principal n'est pas encore là (overworld() nul) : ses données non
                // plus ; un tronçon chargé si tôt n'est pas relu (19/11, ligne ERROR vue à chaque démarrage d'essai)
                if (!srv.isSameThread() || srv.overworld() == null) return;
                d = donnees(srv);
            }
            if (d.nombre() == 0) return;
            ChunkAccess c = e.getChunk();
            long[] connus = d.dansTroncon(l.dimension(), c.getPos().toLong());
            if (connus == null) return;
            Block b = bloc();
            if (b == null) return;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            long[] partis = new long[connus.length];
            int n = 0;
            for (long p : connus) {
                m.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
                if (!c.getBlockState(m).is(b)) partis[n++] = p;
            }
            if (n == 0) return;
            long[] aRetirer = Arrays.copyOf(partis, n);
            ResourceKey<Level> monde = l.dimension();
            srv.execute(() -> {
                for (long p : aRetirer) {
                    if (RecycleursCarte.retirer(srv, monde, p)) {
                        HeralizCarte.LOG.info("[carte] recycleur de la carte disparu (bloc relu au chargement du tronçon) : {} {} {} ({}) : repère retiré",
                                BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p), monde.location());
                    }
                }
            });
        } catch (RuntimeException ex) {
            // jamais d'erreur renvoyée au chargement du tronçon
            HeralizCarte.LOG.error("[carte] repères des recycleurs : tronçon {} non relu", e.getChunk().getPos(), ex);
        }
    }

    /** Bloc chargé (tronçon chargé, fil serveur) : est-ce toujours un recycleur de la carte ? */
    static boolean estRecycleur(LevelChunk c, BlockPos pos) {
        Block b = bloc();
        if (b == null) return false;
        BlockState s = c.getBlockState(pos);
        return s.is(b);
    }

    // ------------------------------------------------------------------ envoi aux joueurs
    /** Tous les changements d'un même tick partent en un seul envoi de la liste des lieux, juste après. */
    static void diffuser(MinecraftServer srv) {
        if (diffusionPrevue) return;
        diffusionPrevue = true;
        srv.tell(new TickTask(srv.getTickCount(), () -> {
            diffusionPrevue = false;
            LieuxData.de(srv).diffuser(srv);
        }));
    }

    // ------------------------------------------------------------------ démarrage, arrêt
    /** Premier démarrage avec cette version (ou carte remplacée) : recherche automatique, une minute après le démarrage. */
    static void demarrage(ServerStartedEvent e) {
        MinecraftServer srv = e.getServer();
        RecycleursData d = donnees(srv);
        if (bloc() == null) return;
        if (d.rechercheFaite()) {
            HeralizCarte.LOG.info("[carte] {} recycleur(s) de la carte sur la carte du monde (recherche automatique déjà faite)", d.nombre());
        } else {
            RechercheRecycleurs.lancer(srv, null, true);
        }
    }

    static void arret(ServerStoppingEvent e) {
        RechercheRecycleurs.arreter();
        donnees = null;
        diffusionPrevue = false;
    }
}
