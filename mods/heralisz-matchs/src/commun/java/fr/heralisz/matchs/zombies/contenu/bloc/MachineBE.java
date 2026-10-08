package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.MachinesV3;
import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatPad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Etat d'une machine v3 de The Giant (case maitresse de {@link BlocMachine}), suivi par le decor et synchronise aux clients
 * seulement quand il change (quelques octets). Sens des champs selon {@link MachinesV3.Type} :
 * <ul>
 *     <li>COURANT : {@link #etat()} 0 coupe / 1 courant (instant du levier = {@link #depuis()}), {@link #bits()} = fusibles
 *     poses (bit i = douille i), {@link #depuisBits()} = dernier fusible pose ;</li>
 *     <li>TP_PAD, TP_PUPITRE : {@link #etat()} = {@link EtatPad}, {@link #lettre()} 0..2 (A, B, C), {@link #duree()} = duree
 *     de la liaison ou de la recharge (ticks) ;</li>
 *     <li>CUVE_AMES : {@link #niveau()} 0..1, {@link #etat()} 1 = pleine, {@link #depuisBits()} = derniere ame arrivee ;</li>
 *     <li>MF_PUPITRE : {@link #etat()} 0 / 1 courant, {@link #bits()} = pads lies (bits 0..2) et en liaison (bits 3..5),
 *     {@link #depuisBits()} = derniere liaison (levier) ;</li>
 *     <li>MF_TOUR : {@link #etat()} 0 / 1 courant, {@link #bits()} = nombre de pads lies, {@link #depuis()} = instant du
 *     changement, {@link #depuisBits()} = derniere arrivee d'un joueur teleporte (effet dessine en
 *     {@link #arriveeX()}... si la plateforme validee n'est pas posee) ;</li>
 *     <li>MF_PLATEFORME : {@link #etat()} 1 = rideau ouvert (lampe de la niche verte), {@link #depuisBits()} = arrivee ;</li>
 *     <li>PAP_RIDEAU : {@link #etat()} 0 ferme / 1 deverrouille (lampe verte, les lames montent en 3 s) / 2 ouvert,
 *     {@link #echelleX()} et {@link #echelleY()} = mise a l'echelle de l'ouverture de la carte.</li>
 * </ul>
 * Etincelles et fumees : particules cote client ({@link #tickClient}), seulement a moins de 24 blocs d'un joueur.
 */
public class MachineBE extends ZbBE {
    private int etat, bits, lettre, duree;
    private float niveau;
    private long depuisBits = -100000L;
    private float sx = 1, sy = 1;
    private boolean arrivee;
    private float ax, ay, az;
    /** Client : prochaine petite etincelle de repos (temps de jeu). */
    private long prochaine;

    public MachineBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.MACHINE_BE.get(), pos, state);
    }

    public MachinesV3.Type type() {
        return getBlockState().getBlock() instanceof BlocMachine b ? b.type : MachinesV3.Type.COURANT;
    }

    public Direction face() {
        return getBlockState().hasProperty(BlocMachine.FACING) ? getBlockState().getValue(BlocMachine.FACING) : Direction.SOUTH;
    }

    // ------------------------------------------------------------------ lectures
    public int etat() {
        return etat;
    }

    public int bits() {
        return bits;
    }

    public int lettre() {
        return lettre;
    }

    public int duree() {
        return duree;
    }

    public float niveau() {
        return niveau;
    }

    public float echelleX() {
        return sx;
    }

    public float echelleY() {
        return sy;
    }

    public boolean arrivee() {
        return arrivee;
    }

    public float arriveeX() {
        return ax;
    }

    public float arriveeY() {
        return ay;
    }

    public float arriveeZ() {
        return az;
    }

    /** Ticks (avec fraction) depuis le dernier changement de {@link #bits()} (ou l'arrivee d'une ame, d'un joueur). */
    public float depuisBits(float partiel) {
        return level == null ? 1e6F : (float) (level.getGameTime() - depuisBits) + partiel;
    }

    public EtatPad etatPad() {
        EtatPad[] v = EtatPad.values();
        return etat >= 0 && etat < v.length ? v[etat] : EtatPad.ETEINT;
    }

    // ------------------------------------------------------------------ ecritures (serveur, par le decor)
    /** Nouvel etat (anime depuis maintenant) ; rien n'est envoye s'il ne change pas. */
    public void etat(int e, int dureeTicks) {
        if (e == etat && dureeTicks == duree) return;
        etat = e;
        duree = dureeTicks;
        marquer();
        allumer();
        synchroniser();
    }

    public void bits(int b) {
        bits(b, true);
    }

    /** Nouveaux bits ; {@code marquer} = l'instant {@link #depuisBits()} est remis (animation : fusible pose, levier tire). */
    public void bits(int b, boolean marquer) {
        if (b == bits) return;
        bits = b;
        if (marquer && level != null) depuisBits = level.getGameTime();
        allumer();
        synchroniser();
    }

    /** Remet l'instant de {@link #depuisBits()} (ame arrivee, joueur arrive) sans autre changement. */
    public void impulsion() {
        if (level != null) depuisBits = level.getGameTime();
        synchroniser();
    }

    public void lettre(int l) {
        if (l == lettre) return;
        lettre = l;
        synchroniser();
    }

    public void niveau(float n) {
        niveau(n, false);
    }

    /** Niveau de la cuve ; {@code impulsion} = une ame vient d'arriver (eclat), un seul paquet. */
    public void niveau(float n, boolean impulsion) {
        n = Math.max(0, Math.min(1, n));
        if (Math.abs(n - niveau) < 1e-4F && !impulsion) return;
        niveau = n;
        if (impulsion && level != null) depuisBits = level.getGameTime() + 14;
        allumer();
        synchroniser();
    }

    public void echelle(float x, float y) {
        sx = x;
        sy = y;
        synchroniser();
    }

    /** Point d'arrivee des teleportes (blocs, relatif au centre bas de la case, axes du monde) ; dessine par la tour. */
    public void arrivee(float x, float y, float z) {
        arrivee = true;
        ax = x;
        ay = y;
        az = z;
        synchroniser();
    }

    /** Remet a zero (debut de partie). */
    public void reinitialiser() {
        etat = bits = duree = 0;
        niveau = 0;
        depuis = depuisBits = -100000L;
        allumer();
        synchroniser();
    }

    /** Lumiere emise par le bloc (propriete {@link BlocMachine#ALLUME}) selon l'etat. */
    private void allumer() {
        if (level == null || level.isClientSide) return;
        boolean on = switch (type()) {
            case TP_PAD, TP_PUPITRE -> etat >= EtatPad.ALIMENTE.ordinal();
            case CUVE_AMES -> niveau > 0.05F;
            case PAP_RIDEAU, MF_PLATEFORME -> etat >= 1;
            default -> etat >= 1;
        };
        BlockState s = getBlockState();
        if (s.hasProperty(BlocMachine.ALLUME) && s.getValue(BlocMachine.ALLUME) != on) level.setBlock(worldPosition, s.setValue(BlocMachine.ALLUME, on), 2);
    }

    // ------------------------------------------------------------------ client : etincelles
    public static void tickClient(Level level, BlockPos pos, BlockState state, MachineBE be) {
        long t = level.getGameTime();
        if ((t + pos.asLong()) % 2 != 0) return;
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 24, false) == null) return;
        RandomSource r = level.random;
        float e = be.ecoule(0);
        switch (be.type()) {
            case COURANT -> {
                if (be.etat == 1 && e >= 10 && e < 34) {
                    // contact du sectionneur : gerbes aux trois couteaux
                    for (int i = 0; i < 3; i++) be.particule(level, ZbRegistres.ETINCELLE.get(), (r.nextInt(3) - 1) * 6F + (r.nextFloat() - 0.5F) * 2, 20.5F, 8.5F, 0.15);
                } else if (be.etat == 1 && t >= be.prochaine) {
                    be.prochaine = t + 60 + r.nextInt(120);
                    be.particule(level, ZbRegistres.ETINCELLE.get(), (r.nextInt(3) - 1) * 6F, 21F, 8F, 0.06);
                }
                if (be.bits != 0 && be.depuisBits(0) < 8) be.particule(level, ZbRegistres.ETINCELLE.get(), 0, 12F, 8F, 0.05);
            }
            case TP_PAD -> {
                EtatPad p = be.etatPad();
                if (p == EtatPad.LIAISON && r.nextInt(3) == 0) {
                    int k = r.nextInt(4);
                    float a = (float) Math.toRadians(45 + 90 * k);
                    be.particule(level, ZbRegistres.ETINCELLE.get(), 24.2F * (float) Math.sin(a), 38F, 24.2F * (float) Math.cos(a), 0.12);
                } else if (p == EtatPad.TRANSPORT && e < 30) {
                    for (int i = 0; i < 3; i++) be.particule(level, ZbRegistres.ETINCELLE.get(), (r.nextFloat() - 0.5F) * 30, 4F + r.nextFloat() * 30, (r.nextFloat() - 0.5F) * 30, 0.2);
                } else if (p == EtatPad.RECHARGE && r.nextInt(6) == 0) {
                    be.particule(level, ZbRegistres.BRUME.get(), (r.nextFloat() - 0.5F) * 36, 40F, (r.nextFloat() - 0.5F) * 36, 0.01);
                }
            }
            case MF_TOUR -> {
                if (be.etat >= 1 && be.bits >= 3 && e < 80 && r.nextInt(2) == 0) {
                    be.particule(level, ZbRegistres.ETINCELLE.get(), (r.nextFloat() - 0.5F) * 30, 70F + r.nextFloat() * 20, (r.nextFloat() - 0.5F) * 30, 0.25);
                } else if (be.etat >= 1 && be.bits > 0 && t >= be.prochaine) {
                    be.prochaine = t + 40 + r.nextInt(80);
                    be.particule(level, ZbRegistres.ETINCELLE.get(), (r.nextFloat() - 0.5F) * 20, 84F, (r.nextFloat() - 0.5F) * 20, 0.1);
                }
            }
            case PAP_RIDEAU -> {
                if (be.etat == 1 && e < 60 && r.nextInt(3) == 0) {
                    be.particule(level, ZbRegistres.CENDRE.get(), (r.nextFloat() - 0.5F) * 36 * be.sx, 44F * be.sy, 2F, 0.02);
                }
            }
            default -> {
            }
        }
    }

    /** Particule a une position du repere du modele (px), tournee comme la machine. */
    private void particule(Level level, ParticleOptions p, float px, float py, float pz, double v) {
        double x = px / 16.0, z = pz / 16.0, wx, wz;
        switch (face()) {
            case WEST -> {
                wx = -z;
                wz = x;
            }
            case NORTH -> {
                wx = -x;
                wz = -z;
            }
            case EAST -> {
                wx = z;
                wz = -x;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        RandomSource r = level.random;
        level.addParticle(p, worldPosition.getX() + 0.5 + wx, worldPosition.getY() + py / 16.0, worldPosition.getZ() + 0.5 + wz,
                (r.nextDouble() - 0.5) * v, r.nextDouble() * v, (r.nextDouble() - 0.5) * v);
    }

    // ------------------------------------------------------------------ NBT
    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putInt("Etat", etat);
        tag.putInt("Bits", bits);
        tag.putInt("Lettre", lettre);
        tag.putInt("Duree", duree);
        tag.putFloat("Niveau", niveau);
        tag.putLong("DepuisBits", depuisBits);
        tag.putFloat("Sx", sx);
        tag.putFloat("Sy", sy);
        if (arrivee) {
            tag.putFloat("Ax", ax);
            tag.putFloat("Ay", ay);
            tag.putFloat("Az", az);
        }
    }

    @Override
    protected void lire(CompoundTag tag) {
        etat = tag.getInt("Etat");
        bits = tag.getInt("Bits");
        lettre = tag.getInt("Lettre");
        duree = tag.getInt("Duree");
        niveau = tag.getFloat("Niveau");
        depuisBits = tag.contains("DepuisBits") ? tag.getLong("DepuisBits") : -100000L;
        sx = tag.contains("Sx") ? tag.getFloat("Sx") : 1;
        sy = tag.contains("Sy") ? tag.getFloat("Sy") : 1;
        arrivee = tag.contains("Ax");
        if (arrivee) {
            ax = tag.getFloat("Ax");
            ay = tag.getFloat("Ay");
            az = tag.getFloat("Az");
        }
    }

    /** Volume de rendu : la machine et ses pieces animees (arcs, colonnes, eclairs vers le ciel), point d'arrivee compris. */
    @Override
    public AABB getRenderBoundingBox() {
        AABB b = new AABB(worldPosition);
        b = switch (type()) {
            case COURANT -> b.inflate(1.6, 0, 1.6).expandTowards(0, 2.0, 0);
            case TP_PAD -> b.inflate(2.2, 1.0, 2.2).expandTowards(0, 2.8, 0);
            case TP_PUPITRE, CUVE_AMES -> b.inflate(1.0, 0, 1.0).expandTowards(0, 1.2, 0);
            case MF_PUPITRE -> b.inflate(1.4, 0, 1.4).expandTowards(0, 1.4, 0);
            case MF_TOUR -> b.inflate(5.0, 0, 5.0).expandTowards(0, 13.0, 0);
            case MF_PLATEFORME -> b.inflate(3.0, 0, 4.0).expandTowards(0, 8.0, 0);
            case PAP_RIDEAU -> b.inflate(1.6 * Math.max(1, sx), 0, 1.6 * Math.max(1, sx)).expandTowards(0, 3.6 * Math.max(1, sy), 0);
        };
        if (arrivee) {
            double x = worldPosition.getX() + 0.5 + ax, y = worldPosition.getY() + ay, z = worldPosition.getZ() + 0.5 + az;
            b = b.minmax(new AABB(x - 2.5, y - 0.5, z - 2.5, x + 2.5, y + 5, z + 2.5));
        }
        return b;
    }
}
