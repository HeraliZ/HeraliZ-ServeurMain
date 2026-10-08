package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.FormesDecoGiant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Decor cuit de The Giant (D-053) : UNE case d'un prop ({@code heralisz_matchs:zb_deco_<nom>}). Chaque case de l'emprise
 * recoit ce bloc avec {@link #part} = {@code a + largeur * (b + hauteur * c)} (a : colonne de gauche a droite vue de face,
 * b : etage, c : rang du dos vers l'avant) et {@link #FACING} = la face du prop ; le modele de la case ne contient que sa
 * geometrie (modele {@code zbquads}, cuit dans le troncon : aucun cout par image). Proprietes booleennes facultatives lues
 * dans {@code formes.json} ({@code allume}, {@code actif}, {@code ouvert}). Collisions par case tournees, lumiere sur une
 * seule case ({@link FormesDecoGiant.Bloc#partLumiere}). Incassable, sans butin ; les zombies ne le traversent pas.
 */
public class BlocDecoGiant extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Passage des proprietes au constructeur : createBlockStateDefinition est appele par le constructeur de Block. */
    private static IntegerProperty partEnCours;
    private static BooleanProperty[] booleensEnCours;

    public final IntegerProperty part;
    public final BooleanProperty[] booleens;
    public final FormesDecoGiant.Bloc donnees;

    /** Fabrique (fil du registre) : proprietes et lumiere lues dans formes.json. */
    public static BlocDecoGiant creer(String id, Properties p) {
        FormesDecoGiant.Bloc b = preparer(id);
        IntegerProperty pp = partEnCours;
        return new BlocDecoGiant(b.lumineux() ? p.lightLevel(s -> b.lumiere(s, pp)) : p, b);
    }

    /** Prepare les proprietes du prochain bloc construit (appel suivi du constructeur, meme fil). */
    static FormesDecoGiant.Bloc preparer(String id) {
        FormesDecoGiant.Bloc b = FormesDecoGiant.bloc(id);
        if (b == null) throw new IllegalStateException("decor inconnu dans formes.json : " + id);
        partEnCours = IntegerProperty.create("part", 0, Math.max(1, b.parts() - 1));
        booleensEnCours = b.proprietes().stream().map(BooleanProperty::create).toArray(BooleanProperty[]::new);
        return b;
    }

    protected BlocDecoGiant(Properties p, FormesDecoGiant.Bloc b) {
        super(p);
        this.part = partEnCours;
        this.booleens = booleensEnCours;
        this.donnees = b;
        BlockState s = stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(part, 0);
        for (BooleanProperty bp : booleens) s = s.setValue(bp, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, partEnCours);
        for (BooleanProperty bp : booleensEnCours) b.add(bp);
    }

    /** Propriete booleenne de ce nom, ou null. */
    @Nullable
    public BooleanProperty booleen(String nom) {
        for (BooleanProperty bp : booleens) if (bp.getName().equals(nom)) return bp;
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return donnees.forme(s.getValue(part), s.getValue(FACING).get2DDataValue());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0F;
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }

    // ------------------------------------------------------------------ repere du prop
    /**
     * Centre bas du prop (monde, en blocs) vu depuis la case {@code pos} de cet etat : la case (a, b, c) a son centre en
     * ((a + 0.5) * pas - largeur * pas / 2, b * pas, (c + 0.5) * pas - profondeur * pas / 2) dans le repere du prop
     * (x de gauche a droite vu de face, z du dos vers l'avant), tourne selon la face.
     */
    public Vec3 origineProp(BlockState s, BlockPos pos) {
        FormesDecoGiant.Bloc b = donnees;
        int pt = s.getValue(part), w = b.largeur(), h = b.hauteur();
        int a = pt % w, bb = (pt / w) % h, c = pt / (w * h);
        double pas = b.pas();
        double lx = (a + 0.5) * pas - w * pas / 2.0, lz = (c + 0.5) * pas - b.profondeur() * pas / 2.0;
        Vec3 r = tourner(lx, lz, s.getValue(FACING));
        return new Vec3(pos.getX() + 0.5 - r.x, pos.getY() - bb * pas, pos.getZ() + 0.5 - r.z);
    }

    /** Point du repere du prop (pixels) en coordonnees du monde, vu depuis la case {@code pos}. */
    public Vec3 pointMonde(BlockState s, BlockPos pos, double xPx, double yPx, double zPx) {
        Vec3 o = origineProp(s, pos);
        Vec3 r = tourner(xPx / 16.0, zPx / 16.0, s.getValue(FACING));
        return new Vec3(o.x + r.x, o.y + yPx / 16.0, o.z + r.z);
    }

    /** Rotation du repere du prop (face sud) vers la face : west (-z, x), north (-x, -z), east (z, -x). */
    public static Vec3 tourner(double x, double z, Direction f) {
        return switch (f) {
            case WEST -> new Vec3(-z, 0, x);
            case NORTH -> new Vec3(-x, 0, -z);
            case EAST -> new Vec3(z, 0, -x);
            default -> new Vec3(x, 0, z);
        };
    }
}
