package fr.heralisz.utils.deco;

import fr.heralisz.utils.pont.Crochet;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Entité de la tête d'un pont de corde, utile au client seulement : elle lit la forme du pont dans le monde (hauteur
 * du plancher de chaque colonne dans le sens FACING, demi-largeur), puis à chaque tick mesure le « balancement » donné
 * par les joueurs qui marchent dessus (vitesse, réceptions de saut) et qui retombe doucement. Le rendu (PontRenderer)
 * et le roulis de la caméra (PontClient) en tirent la dérive latérale, le roulis et le rebond du tablier.
 * Abscisse s : 0 à l'entrée de la première colonne, n au bout ; latéral : vers la droite du sens du pont.
 */
public class PontBlockEntity extends BlockEntity {
    /** Ponts chargés côté client (roulis de la caméra de celui qui marche dessus). */
    public static final Set<PontBlockEntity> CHARGES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int[] ESSAIS = {0, -1, 1};
    private static final float OMEGA = 0.13f;                  // pulsation du balancement (rad/tick) : ~2,4 s

    boolean lu;
    int n, demi;
    float[] h = new float[0];
    int attente;
    float energie, energiePrec;
    float ventLat, ventPrec, brise;
    final float[] marche = new float[4];
    int nMarche;
    private AABB boite;

    /** Tick client (balancement sous les pas, vent de la faille) : branché par la partie client (PontTick). */
    public static final Crochet<PontBlockEntity> TICK_CLIENT = new Crochet<>();

    public PontBlockEntity(BlockPos p, BlockState s) {
        super(DecoBlocks.PONT_BE.get(), p, s);
    }

    public Direction sens() {
        return getBlockState().getValue(PontTeteBlock.FACING);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CHARGES.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CHARGES.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CHARGES.remove(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return boite != null ? boite : new AABB(worldPosition);
    }

    public float abscisse(double x, double z) {
        Direction d = sens();
        double ox = worldPosition.getX() + 0.5 - d.getStepX() * 0.5, oz = worldPosition.getZ() + 0.5 - d.getStepZ() * 0.5;
        return (float) ((x - ox) * d.getStepX() + (z - oz) * d.getStepZ());
    }

    public float lateral(double x, double z) {
        Direction r = sens().getClockWise();
        return (float) ((x - worldPosition.getX() - 0.5) * r.getStepX() + (z - worldPosition.getZ() - 0.5) * r.getStepZ());
    }

    /** Dessus du plancher (relatif au bas du bloc de tête) à l'abscisse s, entre les centres des colonnes. */
    public float hauteur(float s) {
        if (n == 0) {
            return 0;
        }
        float c = s - 0.5f;
        if (c <= 0) {
            return h[0];
        }
        if (c >= n - 1) {
            return h[n - 1];
        }
        int i = (int) c;
        float f = c - i;
        return h[i] * (1 - f) + h[i + 1] * f;
    }

    public float energie(float partial) {
        return Mth.lerp(partial, energiePrec, energie);
    }

    private float phase() {
        return (float) (Mth.getSeed(worldPosition) & 0xFFFFL) / 65536f * 6.2832f;
    }

    private float enveloppe(float s) {
        return n == 0 ? 0 : Mth.sin((float) Math.PI * Mth.clamp(s / n, 0f, 1f));
    }

    /** Proximité d'un marcheur (1 sous ses pieds, 0 à deux blocs) : le tablier y reste sous ses pieds. */
    private float pres(float s) {
        float p = 0;
        for (int i = 0; i < nMarche; i++) {
            float u = (s - marche[i]) / 1.6f;
            p = Math.max(p, (float) Math.exp(-u * u));
        }
        return p;
    }

    /** Poussée latérale du vent (rafale signée selon la droite du pont), lissée et interpolée entre deux ticks. */
    private float vent(float t) {
        float f = t - (float) Math.floor(t);
        return Mth.lerp(f, ventPrec, ventLat);
    }

    /**
     * Dérive latérale du tablier (blocs) : balancement permanent (le pont bouge même vide, plus encore dans la brise de
     * la faille), plus fort sous les pas, et sous une rafale le tablier se couche dans le sens du vent en vibrant.
     */
    public float derive(float s, float t, float e) {
        float env = Math.min(1f, n / 16f) * enveloppe(s);
        float v = vent(t);
        float balance = (0.08f + 0.2f * e + 0.12f * brise) * Mth.sin(OMEGA * t + phase())
                + 0.035f * Mth.sin(0.047f * t + phase() * 2.1f);
        return env * (balance + v * 0.55f * (1f + 0.25f * Mth.sin(0.6f * t)) * (1f - 0.45f * pres(s)));
    }

    /** Roulis du tablier (radians), atténué sous les marcheurs. */
    public float roulis(float s, float t, float e) {
        return roulisBrut(s, t, e) * (1 - 0.6f * pres(s));
    }

    /** Roulis ressenti par celui qui marche (radians), sans l'atténuation sous ses pieds. */
    public float roulisCamera(float s, float t, float e) {
        return roulisBrut(s, t, e);
    }

    private float roulisBrut(float s, float t, float e) {
        float v = vent(t);
        return enveloppe(s) * ((0.03f + 0.085f * e + 0.03f * brise) * Mth.sin(OMEGA * t + phase() + 0.9f)
                + v * 0.17f * (0.8f + 0.2f * Mth.sin(0.5f * t + 1.1f)));
    }

    /** Rebond vertical du tablier (blocs), nul sous les marcheurs ; la rafale le fait vibrer. */
    public float rebond(float s, float t, float e) {
        float v = Math.abs(vent(t));
        return (0.07f * e + 0.06f * v) * enveloppe(s) * Mth.sin(2 * OMEGA * t + phase() * 1.7f + v * 3f) * (1 - pres(s));
    }

    /** Le joueur est-il debout sur ce pont ? -> son abscisse, sinon NaN. */
    public float surLePont(double x, double y, double z) {
        if (!lu || n == 0) {
            return Float.NaN;
        }
        float s = abscisse(x, z);
        if (s < -0.3f || s > n + 0.3f || Math.abs(lateral(x, z)) > demi + 0.7f) {
            return Float.NaN;
        }
        return Math.abs(y - worldPosition.getY() - hauteur(s)) < 0.7 ? s : Float.NaN;
    }

    /** Lit le pont : planchers qui se suivent dans le sens FACING (un bloc plus haut ou plus bas au plus par colonne). */
    void lire(Level l) {
        Direction d = sens(), r = d.getClockWise();
        float[] tmp = new float[128];
        int y = worldPosition.getY(), k = 0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (; k < tmp.length; k++) {
            int cx = worldPosition.getX() + d.getStepX() * k, cz = worldPosition.getZ() + d.getStepZ() * k;
            if (!l.hasChunk(SectionPos.blockToSectionCoord(cx), SectionPos.blockToSectionCoord(cz))) {
                return;                                                  // bout pas encore chargé : on réessaiera
            }
            float top = Float.NaN;
            for (int dy : ESSAIS) {
                BlockState s = l.getBlockState(mp.set(cx, y + dy, cz));
                if (s.getBlock() instanceof PontBlock || s.getBlock() instanceof PontTeteBlock) {
                    top = (y + dy - worldPosition.getY()) + PontBlock.dessus(s);
                    y += dy;
                    break;
                }
            }
            if (Float.isNaN(top)) {
                break;
            }
            tmp[k] = top;
        }
        if (k < 2) {
            return;                                                      // pont incomplet (pas encore posé) : on réessaiera
        }
        n = k;
        h = new float[n];
        System.arraycopy(tmp, 0, h, 0, n);
        demi = 0;
        for (int m = 1; m <= 3; m++) {
            BlockState a = l.getBlockState(worldPosition.relative(r, m)), b = l.getBlockState(worldPosition.relative(r.getOpposite(), m));
            if (a.getBlock() instanceof PontBlock && b.getBlock() instanceof PontBlock) {
                demi = m;
            } else {
                break;
            }
        }
        lu = true;
        float lo = 0, hi = 0;
        for (float v : h) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double ox = worldPosition.getX() + 0.5 - d.getStepX() * 0.5, oz = worldPosition.getZ() + 0.5 - d.getStepZ() * 0.5;
        double w = demi + 2.5;
        double ax = ox - d.getStepX() * 1.5 - r.getStepX() * w, az = oz - d.getStepZ() * 1.5 - r.getStepZ() * w;
        double bx = ox + d.getStepX() * (n + 1.5) + r.getStepX() * w, bz = oz + d.getStepZ() * (n + 1.5) + r.getStepZ() * w;
        boite = new AABB(ax, worldPosition.getY() + lo - 1.5, az, bx, worldPosition.getY() + hi + 2.5, bz);
    }
}
