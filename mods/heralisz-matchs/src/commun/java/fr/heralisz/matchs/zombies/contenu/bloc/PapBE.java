package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatPap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Pack-a-Punch (case maitresse de {@link BlocPap}) : suit l'etat donne par le jeu ({@code ZombiesDecor.packAPunch}) et
 * garde l'arme a montrer. ETEINTE : machine terne, rouleaux gris ; PRETE : enseigne et rouleaux allumes (l'enseigne
 * gresille 1,5 s a l'allumage), rouleaux qui tournent doucement, petites etincelles de temps en temps a la batterie ;
 * AMELIORATION : l'arme entre dans la fenetre, rouleaux emballes, gerbes d'etincelles, lumiere qui pulse ; OFFERTE :
 * eclat, l'arme amelioree ressort et flotte devant la fenetre ; REPRISE : l'arme disparait, retour a PRETE. Les sons
 * d'amelioration sont joues par le jeu.
 */
public class PapBE extends ZbBE {
    private EtatPap etat = EtatPap.ETEINTE;
    private ItemStack arme = ItemStack.EMPTY;
    private long prochainJingle;
    /** Client : prochaine petite gerbe d'etincelles au repos (temps de jeu). */
    private long prochaineEtincelle;
    /** Temps de jeu du dernier allumage (ETEINTE -> PRETE) : gresillement de l'enseigne et des rouleaux. */
    private long allumage = -100000L;

    public PapBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.PAP_BE.get(), pos, state);
    }

    /** Serveur : nouvel etat ({@code arme} : arme d'origine pendant AMELIORATION, arme amelioree pendant OFFERTE). */
    public void etat(EtatPap e, ItemStack a) {
        if (e == null) e = EtatPap.ETEINTE;
        if (e == EtatPap.REPRISE) {
            etat = EtatPap.PRETE;
            arme = ItemStack.EMPTY;
        } else {
            boolean allume = e == EtatPap.PRETE && etat == EtatPap.ETEINTE;
            boolean dejaPrete = e == EtatPap.PRETE && etat == EtatPap.PRETE;
            etat = e;
            arme = a == null || e == EtatPap.ETEINTE || e == EtatPap.PRETE ? ItemStack.EMPTY : a.copy();
            if (allume && level != null) allumage = level.getGameTime();
            // PRETE redonne juste apres une reprise : rien a animer de plus
            if (dejaPrete) {
                synchroniser();
                return;
            }
        }
        marquer();
        if (level != null && !level.isClientSide) {
            boolean on = etat != EtatPap.ETEINTE;
            for (int p = 0; p < 6; p++) {
                BlockPos q = BlocLarge.position(worldPosition, getBlockState().getValue(BlocLarge.FACING), p);
                BlockState b = level.getBlockState(q);
                if (b.hasProperty(BlocPap.ALIMENTE) && b.getValue(BlocPap.ALIMENTE) != on) level.setBlock(q, b.setValue(BlocPap.ALIMENTE, on), 2);
            }
            if (on && prochainJingle == 0) prochainJingle = level.getGameTime() + 200;
        }
        synchroniser();
    }

    public EtatPap etat() {
        return etat;
    }

    public ItemStack arme() {
        return arme;
    }

    public boolean alimente() {
        return etat != EtatPap.ETEINTE;
    }

    /** Ticks (avec fraction) depuis le dernier allumage (courant retabli). */
    public float depuisAllumage(float partiel) {
        return level == null ? 1e6F : (float) (level.getGameTime() - allumage) + partiel;
    }

    /** Ticker serveur : jingle de la machine de temps en temps (60 a 120 s) si un joueur est proche et qu'elle attend. */
    public static void tickServeur(Level level, BlockPos pos, BlockState state, PapBE be) {
        if (be.etat != EtatPap.PRETE || !(level instanceof ServerLevel sl)) return;
        long t = level.getGameTime();
        if (t < be.prochainJingle || t % 20 != 0) return;
        if (sl.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 14, false) == null) return;
        level.playSound(null, pos, ZbSons.PAP_JINGLE.get(), SoundSource.RECORDS, 0.8F, 1.0F);
        be.prochainJingle = t + 1200 + level.random.nextInt(1200);
    }

    // ------------------------------------------------------------------ client : etincelles (reserve fixe, dessinees par PapRendu)
    /** Etincelles du client, dans le repere de la machine (pixels, pixels par tick) : reserve fixe, aucune allocation. */
    public static final int ETINCELLES = 72;
    public final float[] eX = new float[ETINCELLES], eY = new float[ETINCELLES], eZ = new float[ETINCELLES];
    public final float[] eVx = new float[ETINCELLES], eVy = new float[ETINCELLES], eVz = new float[ETINCELLES];
    public final float[] eAge = new float[ETINCELLES], eVie = new float[ETINCELLES];
    public int nEtincelles;

    /**
     * Ticker client : etincelles orangees qui jaillissent et retombent en rebondissant. PRETE = petite gerbe a la borne de la
     * batterie ou a la prise du cable toutes les 2 a 6 s ; AMELIORATION = gerbes aux bords de la fenetre et a la batterie a
     * chaque tick, un peu de fumee sur le dessus ; OFFERTE = grande gerbe a la sortie de l'arme. Rien a plus de 32 blocs.
     */
    public static void tickClient(Level level, BlockPos pos, BlockState state, PapBE be) {
        // mise a jour des etincelles en vol (gravite, frottement, rebond sur le sol)
        int w = 0;
        for (int i = 0; i < be.nEtincelles; i++) {
            if (++be.eAge[i] >= be.eVie[i]) continue;
            be.eX[i] += be.eVx[i];
            be.eY[i] += be.eVy[i];
            be.eZ[i] += be.eVz[i];
            be.eVx[i] *= 0.96F;
            be.eVz[i] *= 0.96F;
            be.eVy[i] = be.eVy[i] * 0.96F - 0.32F;
            if (be.eY[i] < 0.2F && be.eVy[i] < 0) {
                be.eY[i] = 0.2F;
                be.eVy[i] *= -0.35F;
                be.eVx[i] *= 0.6F;
                be.eVz[i] *= 0.6F;
            }
            if (i != w) {
                be.eX[w] = be.eX[i];
                be.eY[w] = be.eY[i];
                be.eZ[w] = be.eZ[i];
                be.eVx[w] = be.eVx[i];
                be.eVy[w] = be.eVy[i];
                be.eVz[w] = be.eVz[i];
                be.eAge[w] = be.eAge[i];
                be.eVie[w] = be.eVie[i];
            }
            w++;
        }
        be.nEtincelles = w;
        if (be.etat == EtatPap.ETEINTE) return;
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 32, false) == null) return;
        RandomSource r = level.random;
        long t = level.getGameTime();
        switch (be.etat) {
            case PRETE -> {
                // allumage : gerbe sous l'enseigne qui grésille
                float al = be.depuisAllumage(0);
                if (al < 24 && r.nextInt(3) == 0) {
                    for (int i = 2 + r.nextInt(4); i > 0; i--) {
                        be.etincelle((r.nextFloat() - 0.5F) * 28F, 34.8F, -3.2F, (r.nextFloat() - 0.5F) * 2.4F, r.nextFloat() * 1.2F, 0.4F + r.nextFloat() * 1.4F,
                                12 + r.nextInt(14));
                    }
                }
                if (t >= be.prochaineEtincelle) {
                    be.prochaineEtincelle = t + 40 + r.nextInt(80);
                    boolean borne = r.nextBoolean();
                    float lx = borne ? -11.0F : -16.6F, ly = borne ? 31.7F : 22.0F, lz = borne ? -2.0F : -3.0F;
                    for (int i = 3 + r.nextInt(4); i > 0; i--) {
                        be.etincelle(lx, ly, lz, (r.nextFloat() - 0.5F) * 1.6F + (borne ? 0 : -0.8F), 0.8F + r.nextFloat() * 1.6F,
                                (r.nextFloat() - 0.5F) * 1.6F, 8 + r.nextInt(10));
                    }
                }
            }
            case AMELIORATION -> {
                for (int i = 0; i < 2; i++) {
                    float sx = r.nextBoolean() ? -1 : 1;
                    be.etincelle(sx * (12.6F + r.nextFloat()), 17.0F + r.nextFloat() * 5.6F, 7.0F, sx * (0.6F + r.nextFloat() * 1.8F),
                            0.6F + r.nextFloat() * 2.2F, 0.6F + r.nextFloat() * 1.6F, 10 + r.nextInt(12));
                }
                if (r.nextInt(3) == 0) {
                    be.etincelle(-11.0F, 31.7F, -2.0F, (r.nextFloat() - 0.5F) * 2F, 1.2F + r.nextFloat() * 1.5F, (r.nextFloat() - 0.5F) * 2F,
                            8 + r.nextInt(8));
                }
                if (r.nextInt(8) == 0) {
                    Direction f = state.hasProperty(BlocLarge.FACING) ? state.getValue(BlocLarge.FACING) : Direction.NORTH;
                    double a = Math.toRadians(-f.toYRot()), lx = (r.nextDouble() - 0.5) * 1.2, lz = -0.15;
                    level.addParticle(ZbRegistres.BRUME.get(), pos.getX() + 0.5 + lx * Math.cos(a) + lz * Math.sin(a), pos.getY() + 1.75,
                            pos.getZ() + 0.5 - lx * Math.sin(a) + lz * Math.cos(a), 0, 0.02, 0);
                }
            }
            case OFFERTE -> {
                if (be.ecoule(0) < 1.5F) {
                    for (int i = 0; i < 28; i++) {
                        be.etincelle((r.nextFloat() - 0.5F) * 24F, 17.0F + r.nextFloat() * 5.6F, 7.2F, (r.nextFloat() - 0.5F) * 3.5F,
                                0.8F + r.nextFloat() * 2.6F, 0.8F + r.nextFloat() * 2.4F, 12 + r.nextInt(14));
                    }
                }
            }
            default -> {
            }
        }
    }

    private void etincelle(float x, float y, float z, float vx, float vy, float vz, int vie) {
        if (nEtincelles >= ETINCELLES) return;
        int i = nEtincelles++;
        eX[i] = x;
        eY[i] = y;
        eZ[i] = z;
        eVx[i] = vx;
        eVy[i] = vy;
        eVz[i] = vz;
        eAge[i] = 0;
        eVie[i] = vie;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putString("Etat", etat.name());
        tag.putLong("Allumage", allumage);
        if (!arme.isEmpty()) tag.put("Arme", arme.save(new CompoundTag()));
    }

    @Override
    protected void lire(CompoundTag tag) {
        try {
            etat = EtatPap.valueOf(tag.getString("Etat"));
        } catch (IllegalArgumentException e) {
            etat = EtatPap.ETEINTE;
        }
        arme = tag.contains("Arme") ? ItemStack.of(tag.getCompound("Arme")) : ItemStack.EMPTY;
        if (tag.contains("Allumage")) allumage = tag.getLong("Allumage");
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.6, 0, 1.6).expandTowards(0, 2.4, 0);
    }
}
