package fr.heraliz.casino.world;

import fr.heraliz.casino.block.DecorBlock;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachinePartBlock;
import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.decor.DecorType;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Construit les deux salles du casino du bunker, machines et decor compris (commande /casino salle) :
 * <ul>
 * <li>le Trou, casino des clochards (entree payante) : un vieux parking souterrain squatte. Beton tache d'huile,
 * parpaings tagues et tole rouillee, ampoules nues et reglettes qui gresillent, barils-braseros, matelas sur
 * palettes, bottes de paille autour de la table-touret, canape defonce, cabane de carton, caddie, caisse grillagee
 * sous son graffiti fluo. Toutes les machines sauf celles du Coffre.</li>
 * <li>le Coffre, casino VIP : l'ancienne salle des coffres d'une banque. Parquet, lambris, enduit creme et blindage,
 * lustres de recuperation, tapis bordeaux entre des potelets a cordon de velours, fauteuils club, Chesterfield et
 * gueridons, bar a tabourets, juke-box ; la porte ronde ouverte sur la chambre forte (coffres de depot, lingots,
 * billets). Le Souffle du Loup, La Niche et La Fusee, et une caisse.</li>
 * </ul>
 * Repere local : x vers la gauche du joueur qui entre, y vers le haut (0 = sol), z vers le fond (0 = mur de facade,
 * l'entree au milieu, face au joueur). Le parvis (6 blocs) s'etend devant l'entree, jusqu'aux pieds du joueur.
 */
public final class SalleBuilder {
    public enum Kind {
        CLOCHARD, VIP
    }

    private final ServerLevel level;
    private final BlockPos origin;
    /** Pas de rotation (sens horaire) du repere local vers le monde : le sud local devient "fwd". */
    private final int rot;
    private final Direction fwd, lx;

    private SalleBuilder(ServerLevel level, BlockPos origin, Direction fwd) {
        this.level = level;
        this.origin = origin;
        this.fwd = fwd;
        this.rot = fwd.get2DDataValue();
        this.lx = world(Direction.EAST);
    }

    /** Largeur, profondeur, hauteur (plafond) de chaque salle. */
    static int width(Kind k) {
        return k == Kind.CLOCHARD ? 33 : 23;
    }

    static int depth(Kind k) {
        return k == Kind.CLOCHARD ? 25 : 19;
    }

    /**
     * Construit la salle devant le joueur : il se tient au bout du parvis, l'entree en face de lui.
     * @return le bloc au milieu du seuil de l'entree
     */
    public static BlockPos build(ServerLevel level, BlockPos feet, Direction facing, Kind kind) {
        Direction fwd = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        int mid = width(kind) / 2;
        SalleBuilder tmp = new SalleBuilder(level, BlockPos.ZERO, fwd);
        BlockPos origin = feet.below().relative(fwd, 7).relative(tmp.lx, -mid);
        SalleBuilder b = new SalleBuilder(level, origin, fwd);
        if (kind == Kind.CLOCHARD) b.trou();
        else b.coffre();
        return b.at(mid, 1, 0);
    }

    // =================================================================== repere
    /** Direction locale (repere de la salle) -> direction du monde. */
    Direction world(Direction local) {
        if (!local.getAxis().isHorizontal()) return local;
        return Direction.from2DDataValue((local.get2DDataValue() + rot) & 3);
    }

    BlockPos at(int x, int y, int z) {
        return origin.relative(lx, x).relative(Direction.UP, y).relative(fwd, z);
    }

    private static BlockState b(String id) {
        return ModRegistry.BUILD_BLOCKS.get(id).get().defaultBlockState();
    }

    private void set(int x, int y, int z, BlockState s) {
        level.setBlock(at(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
            }
        }
    }

    /** Recalcule la forme des blocs qui se raccordent a leurs voisins (grillage), poses sans mise a jour. */
    private void connect(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    BlockPos q = at(x, y, z);
                    level.setBlock(q, Block.updateFromNeighbourShapes(level.getBlockState(q), level, q), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Vide une boite : machines et decors d'abord (leurs blocs annexes partent avec eux), puis le reste. */
    private void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockState s = level.getBlockState(at(x, y, z));
                    if (s.getBlock() instanceof MachineBlock || s.getBlock() instanceof DecorBlock) level.setBlock(at(x, y, z), air, Block.UPDATE_ALL);
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (!(level.getBlockState(at(x, y, z)).getBlock() instanceof MachinePartBlock)) set(x, y, z, air);
                    else level.setBlock(at(x, y, z), air, Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Machine : bloc principal en (x, y, z), face avant tournee vers "face" (repere local). */
    private void machine(MachineType t, int x, int y, int z, Direction face) {
        MachineBlock mb = ModRegistry.MACHINES.get(t).get();
        level.setBlock(at(x, y, z), mb.withConfiguredLight(mb.defaultBlockState().setValue(MachineBlock.FACING, world(face))), Block.UPDATE_ALL);
    }

    /**
     * Element de decor : piece murale posee devant le mur, face tournee vers "face" (dos au mur). Les tapis et les
     * blocs remplacables de son encombrement sont retires d'abord (sinon ses blocs annexes ne s'y posent pas).
     */
    private void decor(DecorType t, int x, int y, int z, Direction face, int mode) {
        Direction facing = world(face);
        BlockPos master = at(x, y, z);
        for (int[] c : t.footprint.cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = fr.heraliz.casino.machine.Footprint.rotateCell(c[0], c[1], c[2], facing);
            BlockPos q = master.offset(w[0], w[1], w[2]);
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock || s.canBeReplaced()) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(master, ModRegistry.DECOR.get(t).get().defaultBlockState().setValue(MachineBlock.FACING, facing).setValue(DecorBlock.ON, true),
                Block.UPDATE_ALL);
        if (mode != 0 && level.getBlockEntity(at(x, y, z)) instanceof DecorBlockEntity be) be.setMode(mode);
    }

    private void decor(DecorType t, int x, int y, int z, Direction face) {
        decor(t, x, y, z, face, 0);
    }

    /** Petite table de salon : piquet de chene sombre et lanterne. */
    private void lampTable(int x, int z) {
        set(x, 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
        set(x, 2, z, Blocks.LANTERN.defaultBlockState());
    }

    /** Caisse de recuperation (tonneau couche) servant de tabouret ou de support. */
    private void crate(int x, int y, int z) {
        set(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
    }

    /** Bloc d'un mur selon sa position (pil : pilier, coin). */
    private interface Pick {
        BlockState at(int x, int y, int z, boolean pil);
    }

    /** Murs de la salle (w x d, hauteur h) ; piliers tous les "every" blocs et aux coins. */
    private void walls(int w, int d, int h, int every, Pick pick) {
        for (int y = 1; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edgeX = x == 0 || x == w - 1, edgeZ = z == 0 || z == d - 1;
                    if (!edgeX && !edgeZ) continue;
                    boolean pil = (edgeZ && x % every == 0) || (edgeX && z % every == 0) || (edgeX && edgeZ);
                    set(x, y, z, pick.at(x, y, z, pil));
                }
            }
        }
    }

    /** Hasard fixe d'une position (0..1). */
    static float hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    // =================================================================== le Trou
    private void trou() {
        int w = width(Kind.CLOCHARD), d = depth(Kind.CLOCHARD), h = 7, mid = w / 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        clear(0, 0, 0, w - 1, h, d - 1);
        clear(mid - 3, 1, -6, mid + 3, 4, -1);
        // sol de parking, plafond de beton, murs : parpaings tagues en bas, tole rouillee en haut, piliers de beton
        fill(0, 0, 0, w - 1, 0, d - 1, b("beton_tache"));
        fill(0, h, 0, w - 1, h, d - 1, b("beton_bunker"));
        BlockState beton = b("beton_bunker"), parpaing = b("parpaing"), tague = b("parpaing_tague");
        BlockState[] panels = {b("tole_rouillee"), b("tole_ondulee"), b("tole_rouillee"), beton};
        walls(w, d, h, 8, (x, y, z, pil) -> {
            if (pil) return beton;
            if (y <= 2) return hash(x, y, z) < 0.22f ? tague : parpaing;
            // en haut, une matiere par travee (tole rouillee, tole verte, beton) : rapieçages de squat
            int seg = (z == 0 || z == d - 1) ? x / 8 + (z == 0 ? 0 : 5) : z / 8 + (x == 0 ? 11 : 17);
            return panels[(int) (hash(seg, 3, 7) * panels.length) % panels.length];
        });
        // lambeaux de l'ancienne moquette du casino sous les tables
        for (int x = 3; x <= 27; x++) {
            for (int z = 7; z <= 17; z++) if (hash(x / 2, 0, z / 2) < 0.45f) set(x, 0, z, b("moquette_casino"));
        }
        // piliers du parking
        for (int[] c : new int[][]{{11, 13}, {20, 13}, {11, 19}, {20, 19}}) fill(c[0], 1, c[1], c[0], h - 1, c[1], beton);
        // entree (3 x 3) et parvis
        fill(mid - 1, 1, 0, mid + 1, 3, 0, air);
        fill(mid - 3, 0, -6, mid + 3, 0, -1, b("beton_tache"));
        // ---- coin du squat (pres de l'entree, cote x bas) : matelas, cartons, table-touret, brasero, tele
        fill(1, 1, 2, 9, 1, 3, b("carton"));
        decor(DecorType.MATELAS, 1, 1, 1, Direction.SOUTH);
        decor(DecorType.MATELAS, 4, 1, 1, Direction.SOUTH);
        fill(4, 0, 4, 8, 0, 7, b("palettes"));
        decor(DecorType.TOURET, 6, 1, 6, Direction.SOUTH);
        // bottes de paille autour de la table (on s'y assoit), rechaud, pile de pneus
        decor(DecorType.BOTTE, 5, 1, 6, Direction.EAST, 0);
        decor(DecorType.BOTTE, 7, 1, 6, Direction.WEST, 1);
        decor(DecorType.BOTTE, 6, 1, 7, Direction.NORTH, 2);
        decor(DecorType.RECHAUD, 8, 1, 4, Direction.SOUTH);
        decor(DecorType.PNEUS, 9, 1, 6, Direction.WEST);
        decor(DecorType.BARIL, 10, 1, 3, Direction.SOUTH);
        crate(1, 1, 6);
        decor(DecorType.TELE, 1, 2, 6, Direction.EAST, 2);
        crate(1, 1, 7);
        // canape defonce face a la tele, bache bleue clouee sur le mur du squat
        decor(DecorType.CANAPE, 3, 1, 6, Direction.WEST);
        fill(0, 1, 1, 0, 2, 5, b("bache_bleue"));
        // ---- coin radio et cabane de carton le long du mur x bas
        decor(DecorType.RADIO, 1, 1, 11, Direction.EAST);
        decor(DecorType.CHAISE, 3, 1, 12, Direction.WEST);
        decor(DecorType.CABANE, 1, 1, 17, Direction.EAST);
        // ---- a l'entree : la pancarte du mendiant, des sacs poubelle, un caddie
        decor(DecorType.PANCARTE, 14, 1, 1, Direction.SOUTH, 0);
        decor(DecorType.SACS, 12, 1, 1, Direction.SOUTH);
        decor(DecorType.CADDIE, 19, 1, 1, Direction.SOUTH);
        // ---- caisse grillagee (cote x haut, contre la facade) et son graffiti fluo
        machine(MachineType.CAISSE, 26, 1, 1, Direction.SOUTH);
        for (int x : new int[]{23, 29}) {
            fill(x, 1, 1, x, 3, 3, b("grillage"));
            connect(x, 1, 1, x, 3, 3);
        }
        decor(DecorType.GRAFFITI_CAISSE, 26, 4, 1, Direction.SOUTH, 2);
        decor(DecorType.BARIL, 21, 1, 5, Direction.SOUTH);
        // ---- deux rangees de tables au milieu
        machine(MachineType.BLACKJACK, 7, 1, 9, Direction.NORTH);
        machine(MachineType.ROULETTE, 15, 1, 9, Direction.NORTH);
        machine(MachineType.COURSE, 23, 1, 9, Direction.NORTH);
        machine(MachineType.MINES, 7, 1, 15, Direction.NORTH);
        machine(MachineType.PIERRE_FEUILLE_CISEAUX, 15, 1, 15, Direction.NORTH);
        machine(MachineType.BATAILLE_NAVALE, 23, 1, 15, Direction.NORTH);
        // ---- au fond : Rotten, le Plinko sous l'ecran du jackpot, l'Olympe, deux braseros
        machine(MachineType.ROTTEN, 6, 1, d - 2, Direction.NORTH);
        machine(MachineType.PLINKO, mid, 1, d - 2, Direction.NORTH);
        machine(MachineType.OLYMPE, 26, 1, d - 2, Direction.NORTH);
        decor(DecorType.JACKPOT, mid, 5, d - 2, Direction.NORTH);
        decor(DecorType.BARIL, 11, 1, d - 2, Direction.SOUTH);
        decor(DecorType.BARIL, 21, 1, d - 2, Direction.SOUTH);
        // ---- tableaux des gagnants, des perdants, des parieurs contre le mur x haut
        machine(MachineType.TABLEAU, w - 2, 1, 10, Direction.WEST);
        machine(MachineType.TABLEAU_PERDANTS, w - 2, 1, 14, Direction.WEST);
        machine(MachineType.TABLEAU_PARIEURS, w - 2, 1, 18, Direction.WEST);
        decor(DecorType.BARIL, 2, 1, 13, Direction.SOUTH);
        // ---- murs : affiches dechirees, neons bricoles
        decor(DecorType.AFFICHE, 1, 2, 10, Direction.EAST, 0);
        decor(DecorType.AFFICHE, 1, 3, 16, Direction.EAST, 2);
        decor(DecorType.AFFICHE, 1, 2, 20, Direction.EAST, 3);
        decor(DecorType.AFFICHE, 12, 3, 1, Direction.SOUTH, 4);
        decor(DecorType.AFFICHE, 3, 2, d - 2, Direction.NORTH, 6);
        decor(DecorType.AFFICHE, 30, 2, d - 2, Direction.NORTH, 7);
        decor(DecorType.AFFICHE, 20, 3, 1, Direction.SOUTH, 1);
        decor(DecorType.NEON, 1, 4, 8, Direction.EAST, 0);
        decor(DecorType.NEON, 1, 4, 14, Direction.EAST, 2);
        decor(DecorType.NEON, 1, 4, 20, Direction.EAST, 3);
        decor(DecorType.NEON, w - 2, 5, 14, Direction.WEST, 1);
        decor(DecorType.NEON, 9, 4, d - 2, Direction.NORTH, 4);
        decor(DecorType.NEON, 23, 4, d - 2, Direction.NORTH, 5);
        decor(DecorType.FLECHE, mid, 4, 1, Direction.SOUTH, 2);
        // ---- plafond : ampoules nues, trois reglettes qui gresillent (vraie lumiere), baladeuses au-dessus des tables
        int[][] neons = {{16, 13, 0}, {28, 4, 0}, {4, 19, 1}};
        for (int x : new int[]{4, 10, 16, 22, 28}) {
            for (int z : new int[]{4, 13, 19}) {
                boolean neon = false;
                for (int[] n : neons) neon |= n[0] == x && n[1] == z;
                if (!neon) decor(DecorType.AMPOULE, x, h - 1, z, Direction.SOUTH);
            }
        }
        for (int[] n : neons) decor(DecorType.NEON_PLAFOND, n[0], h - 1, n[1], Direction.SOUTH, n[2]);
        for (int x : new int[]{7, 15, 23}) decor(DecorType.LAMPE, x, h - 1, 11, Direction.SOUTH);
        // bric-a-brac : palettes empilees, futs
        fill(1, 1, d - 2, 2, 1, d - 2, b("palettes"));
        set(1, 2, d - 2, b("palettes"));
        crate(w - 2, 1, d - 2);
        crate(w - 3, 1, d - 2);
        crate(w - 2, 2, d - 2);
        crate(w - 2, 1, d - 4);
        // toiles d'araignee dans les coins du plafond
        for (int[] c : new int[][]{{1, 1}, {w - 2, 1}, {1, d - 2}, {w - 2, d - 2}, {1, 12}}) set(c[0], h - 1, c[1], Blocks.COBWEB.defaultBlockState());
        // ---- dehors : enseigne, panneau OUVERT, file d'attente a chaine, murets de sacs de sable
        decor(DecorType.ENSEIGNE, mid, 4, -1, Direction.NORTH, 4);
        decor(DecorType.OUVERT, mid + 3, 2, -1, Direction.NORTH);
        for (int x : new int[]{mid - 2, mid + 2}) {
            decor(DecorType.POTEAU, x, 1, -2, Direction.NORTH);
            decor(DecorType.POTEAU, x, 1, -6, Direction.NORTH);
        }
        fill(mid - 3, 1, -5, mid - 3, 1, -3, b("sacs_sable"));
        fill(mid + 3, 1, -5, mid + 3, 1, -3, b("sacs_sable"));
    }

    // =================================================================== le Coffre
    private void coffre() {
        int w = width(Kind.VIP), d = depth(Kind.VIP), h = 6, mid = w / 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        clear(0, 0, 0, w - 1, h, d - 1);
        clear(mid - 3, 1, -6, mid + 3, 4, -1);
        // parquet, plafond de lambris ; murs : lambris en soubassement, enduit creme, corniche et piliers de blindage
        fill(0, 0, 0, w - 1, 0, d - 1, b("parquet_use"));
        fill(0, h, 0, w - 1, h, d - 1, b("lambris"));
        BlockState blindage = b("blindage"), lambris = b("lambris"), enduit = b("enduit_creme");
        walls(w, d, h, 6, (x, y, z, pil) -> pil || y == h - 1 ? blindage : y <= 2 ? lambris : enduit);
        // entree encadree de blindage, linteau de blindage
        fill(mid - 2, 1, 0, mid - 2, h - 1, 0, b("blindage"));
        fill(mid + 2, 1, 0, mid + 2, h - 1, 0, b("blindage"));
        fill(mid - 1, 4, 0, mid + 1, h - 1, 0, b("blindage"));
        fill(mid - 1, 1, 0, mid + 1, 3, 0, air);
        // parvis de beton, tapis rouge jusqu'au fond de la salle
        fill(mid - 3, 0, -6, mid + 3, 0, -1, b("beton_bunker"));
        fill(mid - 1, 1, -6, mid + 1, 1, -1, b("tapis_bordeaux"));
        fill(mid - 1, 1, 1, mid + 1, 1, 14, b("tapis_bordeaux"));
        // ---- potelets a cordon de velours le long du tapis, dedans et dehors
        for (int x : new int[]{mid - 2, mid + 2}) {
            for (int z : new int[]{2, 7, 12}) decor(DecorType.POTELET, x, 1, z, Direction.SOUTH);
            decor(DecorType.POTELET, x, 1, -2, Direction.NORTH);
            decor(DecorType.POTELET, x, 1, -6, Direction.NORTH);
        }
        // ---- au fond : le Souffle du Loup, La Fusee au bout du tapis, La Niche
        machine(MachineType.LOUP, 4, 1, d - 2, Direction.NORTH);
        machine(MachineType.CRASH, mid, 1, d - 3, Direction.NORTH);
        machine(MachineType.NICHE, w - 5, 1, d - 2, Direction.NORTH);
        // ---- la caisse contre le mur x bas, sa fleche ; l'ecran du jackpot et un neon
        machine(MachineType.CAISSE, 1, 1, 4, Direction.EAST);
        decor(DecorType.FLECHE, 1, 3, 4, Direction.EAST, 0);
        decor(DecorType.JACKPOT, 1, 3, 13, Direction.EAST);
        decor(DecorType.NEON, w - 2, 3, 13, Direction.WEST, 0);
        decor(DecorType.AFFICHE, 1, 3, 6, Direction.EAST, 4);
        // ---- la porte ronde du coffre, ouverte, dans la facade : derriere, la chambre forte (coffres de depot, lingots,
        // mallette de billets) ; le vantail ouvert se rabat vers le tapis rouge
        vault(mid + 5);
        // ---- salons sur tapis : fauteuils club autour de gueridons (whisky, cigare), un Chesterfield
        fill(4, 1, 8, 8, 1, 12, b("tapis_bordeaux"));
        decor(DecorType.GUERIDON, 6, 1, 10, Direction.SOUTH);
        decor(DecorType.FAUTEUIL, 5, 1, 10, Direction.EAST);
        decor(DecorType.FAUTEUIL, 7, 1, 10, Direction.WEST);
        decor(DecorType.FAUTEUIL, 6, 1, 11, Direction.NORTH);
        fill(w - 9, 1, 8, w - 5, 1, 12, b("tapis_bordeaux"));
        decor(DecorType.GUERIDON, w - 7, 1, 10, Direction.SOUTH);
        decor(DecorType.FAUTEUIL, w - 8, 1, 10, Direction.EAST);
        decor(DecorType.FAUTEUIL, w - 6, 1, 10, Direction.WEST);
        decor(DecorType.CHESTERFIELD, w - 7, 1, 11, Direction.NORTH);
        decor(DecorType.FAUTEUIL, w - 3, 1, 5, Direction.WEST);
        lampTable(w - 3, 6);
        decor(DecorType.FAUTEUIL, w - 3, 1, 7, Direction.WEST);
        // ---- le bar contre le mur x bas : comptoir de lambris sur un damier de marbre, bouteilles (alambics), lampe
        // de banquier, tabourets, reserve de futs
        fill(1, 0, 7, 4, 0, 13, b("damier_marbre"));
        fill(2, 1, 8, 2, 1, 12, lambris);
        set(2, 2, 9, Blocks.BREWING_STAND.defaultBlockState());
        decor(DecorType.LAMPE_BANQUIER, 2, 2, 10, Direction.EAST);
        set(2, 2, 11, Blocks.BREWING_STAND.defaultBlockState());
        for (int z = 8; z <= 12; z += 2) crate(1, 1, z);
        decor(DecorType.TABOURET, 3, 1, 9, Direction.WEST);
        decor(DecorType.TABOURET, 3, 1, 11, Direction.WEST);
        decor(DecorType.FLECHE, 1, 4, 10, Direction.EAST, 6);
        // ---- appliques de laiton aux murs
        decor(DecorType.APPLIQUE, w - 2, 3, 4, Direction.WEST);
        decor(DecorType.APPLIQUE, 1, 3, 16, Direction.EAST);
        // ---- juke-box et palmier dans les coins de la facade, plantes mortes au fond
        decor(DecorType.JUKEBOX, w - 2, 1, 1, Direction.SOUTH);
        decor(DecorType.PALMIER, 1, 1, 1, Direction.SOUTH);
        for (int[] c : new int[][]{{1, d - 2}, {w - 2, d - 2}}) set(c[0], 1, c[1], Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        // ---- lustres
        decor(DecorType.LUSTRE, 6, h - 1, 8, Direction.SOUTH);
        decor(DecorType.LUSTRE, w - 7, h - 1, 8, Direction.SOUTH);
        decor(DecorType.LUSTRE, mid, h - 1, 11, Direction.SOUTH);
        // ---- dehors : graffiti VIP dore au-dessus de la porte, la fleche vers l'entree
        decor(DecorType.GRAFFITI_VIP, mid, 4, -1, Direction.NORTH, 2);
    }

    /**
     * Chambre forte du Coffre : la facade est percee (3 x 3) derriere la porte ronde ; une petite piece blindee
     * (dalles de marbre noir) abrite le mur de coffres de depot, une pile de lingots, la mallette de billets et une
     * applique. La porte est posee ouverte.
     */
    private void vault(int x) {
        BlockState blindage = b("blindage"), air = Blocks.AIR.defaultBlockState();
        // coque blindee derriere la facade, sol de marbre noir
        clear(x - 2, 0, -4, x + 2, 4, -1);
        fill(x - 2, 0, -4, x + 2, 4, -1, blindage);
        fill(x - 1, 1, -3, x + 1, 3, -1, air);
        fill(x - 1, 0, -3, x + 1, 0, -1, b("marbre_noir"));
        // passage dans la facade
        fill(x - 1, 1, 0, x + 1, 3, 0, air);
        decor(DecorType.CASIERS, x, 1, -3, Direction.SOUTH);
        decor(DecorType.APPLIQUE, x, 3, -3, Direction.SOUTH);
        decor(DecorType.LINGOTS, x - 1, 1, -2, Direction.SOUTH);
        decor(DecorType.VALISE, x + 1, 1, -2, Direction.SOUTH);
        // la porte, ouverte contre la facade (le vantail se rabat sur la gauche)
        decor(DecorType.COFFRE, x, 1, 1, Direction.SOUTH);
    }
}
