package fr.heraliz.quetes.bloc;

import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Le recycleur de la carte (décision du propriétaire du 31/10) : la machine du spawn ({@link Recycleur}, même forme, même
 * case invisible à droite : {@code recycleur_carte_partie}) en version de terrain. Modèles (à l'arrêt, en marche), texture
 * et états ÉCRITS par {@code modules/pnj/outils/recycleur_carte_modele.py --installer}, jamais à la main.
 * <p>
 * Mêmes règles et même écran que celui du spawn, avec 10 % de chance en plus (profil « carte » de recyclage.json), permis
 * en combat et sur la carte, sans point de saison, et son contenu est À LA MACHINE, partagé comme un coffre : tout le
 * monde voit et prend ce qu'il contient, le recyclage continue sans personne. Tout est décidé par la partie serveur
 * ({@code serveur/RecycleurCarteEntiteServeur}, {@code serveur/MenuRecycleurCarteServeur}).
 * <p>
 * {@link #MARCHE} : la machine recycle. Elle n'a de ticker QUE dans cet état : une machine vide, arrêtée ou pleine ne
 * coûte rien. À l'arrêt / en marche : deux modèles (voyants et gyrophare allumés), un peu de lumière, fumée au pot
 * d'échappement (chez le joueur seulement).
 */
public class RecycleurCarte extends Recycleur implements EntityBlock {
    /** Ouverture demandée (serveur) : branchée par la partie serveur. */
    public static final Crochet<Ouverture> OUVRIR_CARTE = new Crochet<>();
    public static final BooleanProperty MARCHE = BooleanProperty.create("marche");

    public RecycleurCarte(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(MARCHE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(MARCHE);
    }

    @Override
    protected Block blocPartie() {
        return HeralizQuetes.RECYCLEUR_CARTE_PARTIE.get();
    }

    @Override
    protected void ouvrir(ServerPlayer joueur, BlockPos pos) {
        OUVRIR_CARTE.accept(new Ouverture(joueur, pos));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
        return HeralizQuetes.RECYCLEUR_CARTE_ENTITE.get().create(pos, s);
    }

    /** Ticker seulement côté serveur et seulement en marche (Minecraft le reprend à chaque changement d'état). */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> type) {
        if (l.isClientSide || !s.getValue(MARCHE) || type != HeralizQuetes.RECYCLEUR_CARTE_ENTITE.get()) return null;
        return (lv, pos, st, be) -> ((RecycleurCarteEntite) be).tickServeur();
    }

    /** Le bloc part (staff en créatif, commande) : son contenu tombe au sol (partie serveur), puis la case de droite. */
    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState nouveau, boolean deplace) {
        if (!s.is(nouveau.getBlock()) && l.getBlockEntity(pos) instanceof RecycleurCarteEntite m) m.retire();
        super.onRemove(s, l, pos, nouveau, deplace);
    }

    /** En marche : un peu de fumée au pot d'échappement (modèle : x 27,2, z 10,6, haut à 26,6 px), chez le joueur seulement. */
    @Override
    public void animateTick(BlockState s, Level l, BlockPos pos, RandomSource r) {
        if (!s.getValue(MARCHE) || r.nextInt(3) != 0) return;
        double[] xz = tourne(27.2, 10.6, s.getValue(FACING));
        l.addParticle(ParticleTypes.SMOKE, pos.getX() + xz[0] / 16.0, pos.getY() + 26.8 / 16.0, pos.getZ() + xz[1] / 16.0,
                0, 0.035, 0);
    }

    /**
     * Point (x, z) du modèle (en pixels, tourné vers le nord) dans le bloc posé : le modèle tourne autour du centre du bloc
     * (8, 8), d'un quart de tour dans le sens des aiguilles d'une montre par cran (nord, est, sud, ouest).
     */
    static double[] tourne(double x, double z, Direction face) {
        int k = switch (face) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        double dx = x - 8, dz = z - 8;
        for (int i = 0; i < k; i++) {
            double t = dx;
            dx = -dz;
            dz = t;
        }
        return new double[]{8 + dx, 8 + dz};
    }

    /** Lumière du bloc : un peu en marche (voyants, gyrophare), rien à l'arrêt. */
    public static int lumiere(BlockState s) {
        return s.hasProperty(MARCHE) && s.getValue(MARCHE) ? 6 : 0;
    }
}
