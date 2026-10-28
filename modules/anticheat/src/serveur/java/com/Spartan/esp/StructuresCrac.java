package com.Spartan.esp;

import com.Spartan.Spartan;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cases de construction de CRac ({@code crac:structure}) vues par l'anti-ESP (2.9.4).
 *
 * Toutes les pièces d'une base (murs, fenêtres, portes, planchers…) passent dans un même bloc
 * {@code crac:structure} : sa forme est dans son entité de bloc, son état ne porte que six drapeaux
 * « face entièrement fermée », faits pour la lumière et le rendu. Lue par état, la case ne dit donc pas où
 * est l'ouverture d'une fenêtre : avant la 2.9.4, l'anti-ESP posait une plaque sur chaque face « fermée »,
 * et ces drapeaux ne sont recalculés qu'au changement d'une pièce (une fenêtre posée avant que CRac
 * n'élargisse l'ouverture de 16 à 28 px, le 26/10, garde des faces « fermées » sur les bords de la nouvelle
 * ouverture), réunissent toutes les pièces de la case et ferment toute la case quand ses six faces le sont.
 *
 * Ici, chaque case de construction rencontrée par un rayon est relue à sa position, sur le thread
 * serveur, d'après les vraies pièces qui la traversent : la forme qui cache la vue, en points de 1/16
 * (raster « intérieur » de CRac : seuls les points entièrement dans une pièce). Un mur plein, une porte
 * fermée cachent ; l'ouverture d'une fenêtre, ses barreaux, sa vitre (transparente), une porte ouverte laissent voir.
 * Les murs en brindilles (treillis ouvert) ne cachent pas, comme pour la lumière dans CRac.
 *
 * Source de la forme, par ordre de préférence (réflexion tolérante, sans dépendance à CRac) :
 * 1. {@code fr.heraliz.crac.api.VueApi.formeVue(BlockGetter, BlockPos)} → {@link VoxelShape} (vide = rien ne
 *    cache, null = inconnu) si CRac l'expose ;
 * 2. sinon les classes de CRac (occupants de l'entité de bloc, {@code PieceShapes.occlusion}) ;
 * 3. sinon (CRac changé, ancienne donnée) : les drapeaux de l'état, comme avant.
 *
 * Fils : les threads de l'anti-ESP ne lisent que la table (ConcurrentHashMap) ; une case inconnue est
 * demandée au thread serveur et lue, en attendant, avec les drapeaux (on ne montre pas un intérieur de base
 * par erreur). Le thread serveur la calcule au tick suivant et marque la case comme changée : les paires
 * concernées sont recalculées. Une case modifiée (sendBlockUpdated, que CRac appelle à chaque changement
 * de pièce, de porte, de vitre) est oubliée.
 */
final class StructuresCrac {
    private static final String BLOC = "crac:structure";
    private static final int MAX_BOXES = 32;
    /** Cases calculées par tick serveur au plus. */
    private static final int BUDGET = 1024;
    /** Au-delà, la table d'un monde est vidée (elle se reremplit au besoin). */
    private static final int MAX_CASES = 131_072;

    /** Rien ne cache dans cette case. */
    static final double[] AUCUNE = new double[0];
    /** Case demandée au thread serveur, pas encore calculée. */
    private static final Object ATTENTE = new Object();

    /** Forme calculée pour un état donné de la case (un autre état = périmée ; occ null = lire les drapeaux). */
    private record Case(BlockState state, double[] occ) {
    }

    private record Demande(ServerLevel level, long pos) {
    }

    private static final Map<Level, ConcurrentHashMap<Long, Object>> CASES = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Demande> DEMANDES = new ConcurrentLinkedQueue<>();

    private StructuresCrac() {
    }

    /** Ce bloc est-il la case de construction de CRac ? (identifiant figé, appelé une fois par état). */
    static boolean estCase(net.minecraft.resources.ResourceLocation id) {
        return id != null && BLOC.equals(id.toString());
    }

    // ------------------------------------------------------------------ lecture (tout thread)

    /**
     * Occultation de la case {@code (x, y, z)}, d'état {@code state} : forme calculée, ou {@code null} si elle
     * n'est pas encore connue (le demandeur lit alors les drapeaux de l'état). Sur le thread serveur
     * ({@code main}), la case est calculée tout de suite.
     */
    static double[] at(ServerLevel level, int x, int y, int z, BlockState state, boolean main) {
        if (main ? !Reflet.utilisable() : !Reflet.pret()) {
            return null;
        }
        long key = BlockPos.asLong(x, y, z);
        ConcurrentHashMap<Long, Object> cases = CASES.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        Object v = cases.get(key);
        if (v instanceof Case c && c.state == state) {
            return c.occ;
        }
        if (main) {
            double[] occ = calculer(level, new BlockPos(x, y, z), state);
            cases.put(key, new Case(state, occ));
            return occ;
        }
        if (v == null ? cases.putIfAbsent(key, ATTENTE) == null : (v != ATTENTE && cases.replace(key, v, ATTENTE))) {
            DEMANDES.add(new Demande(level, key));
        }
        return null;
    }

    // ------------------------------------------------------------------ thread serveur

    /**
     * Début de tick : calcule les cases demandées par les threads de l'anti-ESP et signale chacune comme
     * changée (les paires immobiles qui la traversent sont recalculées).
     */
    static void resoudre(java.util.function.BiConsumer<ServerLevel, BlockPos> changee) {
        if (!Reflet.utilisable()) { // classes de CRac cherchées sur le thread serveur, jamais par un thread de calcul
            return;
        }
        for (int n = 0; n < BUDGET; n++) {
            Demande d = DEMANDES.poll();
            if (d == null) {
                return;
            }
            ConcurrentHashMap<Long, Object> cases = CASES.get(d.level);
            if (cases == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(d.pos);
            if (!d.level.isLoaded(pos)) {
                cases.remove(d.pos, ATTENTE);
                continue;
            }
            BlockState state = d.level.getBlockState(pos);
            double[] occ = calculer(d.level, pos, state);
            cases.put(d.pos, new Case(state, occ)); // null : drapeaux de l'état, sans redemander
            if (occ != null) {
                changee.accept(d.level, pos);
            }
        }
    }

    /** Une case a changé (pièce posée, cassée, porte, vitre) : sa forme est oubliée. */
    static void oublier(Level level, BlockPos pos) {
        ConcurrentHashMap<Long, Object> cases = CASES.get(level);
        if (cases != null && !cases.isEmpty()) {
            cases.remove(pos.asLong());
            if (cases.size() > MAX_CASES) {
                cases.clear();
            }
        }
    }

    static void vider() {
        CASES.clear();
        DEMANDES.clear();
    }

    /** Forme qui cache la vue dans la case, ou null (inconnue : drapeaux de l'état). Thread serveur. */
    private static double[] calculer(ServerLevel level, BlockPos pos, BlockState state) {
        try {
            VoxelShape s = Reflet.forme(level, pos);
            if (s == null) {
                return null;
            }
            double[] b = Occluders.boxes(s, MAX_BOXES);
            return b == null ? AUCUNE : b;
        } catch (Throwable t) {
            Reflet.panne(t);
            return null;
        }
    }

    // ------------------------------------------------------------------ réflexion vers CRac

    /** Accès à CRac, cherché une fois ; en panne = drapeaux de l'état (comportement d'avant). */
    private static final class Reflet {
        private static volatile int etat; // 0 = à chercher, 1 = prêt, -1 = absent ou en panne
        private static Method api;
        private static Class<?> classeCase;
        private static Method occupants;
        private static Method legacy;
        private static Method occludes;
        private static Method kind;
        private static Method stateOf;
        private static Method key;
        private static Method variant;
        private static Method hasDoor;
        private static Method occlusion;
        private static int stateSas;
        private static int insertMask;
        private static int insertShift;

        /** Sans rien chercher (threads de calcul). */
        static boolean pret() {
            return etat > 0;
        }

        static boolean utilisable() {
            if (etat == 0) {
                chercher();
            }
            return etat > 0;
        }

        private static synchronized void chercher() {
            if (etat != 0) {
                return;
            }
            try {
                ClassLoader cl = StructuresCrac.class.getClassLoader();
                try {
                    Class<?> c = Class.forName("fr.heraliz.crac.api.VueApi", true, cl);
                    Method m = c.getMethod("formeVue", BlockGetter.class, BlockPos.class);
                    if (VoxelShape.class.isAssignableFrom(m.getReturnType())) {
                        api = m;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // API pas encore exposée par CRac : classes internes
                }
                classeCase = Class.forName("fr.heraliz.crac.build.world.StructureBlockEntity", true, cl);
                occupants = classeCase.getMethod("occupants");
                try {
                    legacy = classeCase.getMethod("hasLegacyData");
                } catch (NoSuchMethodException ignored) {
                    legacy = null;
                }
                Class<?> occ = Class.forName("fr.heraliz.crac.build.world.Occupant", true, cl);
                occludes = occ.getMethod("occludes");
                kind = occ.getMethod("kind");
                stateOf = occ.getMethod("state");
                key = occ.getMethod("key");
                variant = occ.getMethod("variant");
                Class<?> pieceKind = Class.forName("fr.heraliz.crac.build.model.PieceKind", true, cl);
                hasDoor = pieceKind.getMethod("hasDoor");
                Class<?> piece = Class.forName("fr.heraliz.crac.build.model.Piece", true, cl);
                stateSas = entier(piece, "STATE_SAS");
                insertMask = entier(piece, "INSERT_MASK");
                insertShift = entier(piece, "INSERT_SHIFT");
                Class<?> pieceKey = Class.forName("fr.heraliz.crac.build.model.PieceKey", true, cl);
                Class<?> shapes = Class.forName("fr.heraliz.crac.build.geom.PieceShapes", true, cl);
                occlusion = shapes.getMethod("occlusion", pieceKey, int.class, long.class, BlockPos.class);
                etat = 1;
                Spartan.LOGGER.info("[Spartan] Anti-ESP : cases de construction de CRac lues pièce par pièce ({}).",
                        api != null ? "VueApi" : "classes de CRac");
            } catch (Throwable t) {
                etat = -1;
                if (!(t instanceof ClassNotFoundException)) {
                    Spartan.LOGGER.warn("[Spartan] Anti-ESP : CRac illisible ({}), cases de construction lues par leur état.",
                            t.toString());
                }
            }
        }

        private static int entier(Class<?> c, String nom) throws ReflectiveOperationException {
            Field f = c.getField(nom);
            return f.getInt(null);
        }

        static void panne(Throwable t) {
            if (etat > 0) {
                etat = -1;
                vider();
                Spartan.LOGGER.warn("[Spartan] Anti-ESP : lecture des pièces de CRac en panne ({}), cases de construction"
                        + " lues par leur état.", t.toString());
            }
        }

        /** Forme qui cache la vue dans la case (coordonnées de la case), vide = rien ; null = inconnue. */
        static VoxelShape forme(ServerLevel level, BlockPos pos) throws ReflectiveOperationException {
            if (api != null) {
                return (VoxelShape) api.invoke(null, level, pos);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !classeCase.isInstance(be)) {
                return null;
            }
            if (legacy != null && (Boolean) legacy.invoke(be)) {
                return null; // ancienne donnée de CRac : drapeaux de l'état
            }
            List<?> list = (List<?>) occupants.invoke(be);
            VoxelShape s = Shapes.empty();
            for (Object o : list) {
                if (!(Boolean) occludes.invoke(o)) {
                    continue; // mur en brindilles : treillis ouvert
                }
                Object k = kind.invoke(o);
                int st = (Integer) stateOf.invoke(o);
                if ("WINDOW".equals(((Enum<?>) k).name())) {
                    // ouverture, barreaux et vitre (transparente) laissent voir : seul le cadre de la fenêtre cache
                    st &= ~insertMask;
                } else if ((Boolean) hasDoor.invoke(k)) {
                    st &= ~stateSas; // porte de sas bloquée : vue comme ouverte (Piece.etatVisible)
                }
                VoxelShape part = (VoxelShape) occlusion.invoke(null, key.invoke(o), st, (Long) variant.invoke(o), pos);
                s = Shapes.joinUnoptimized(s, part, BooleanOp.OR);
            }
            return s.optimize();
        }
    }
}
