package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Emplacement de boite mystere v2 (bloc central d'une boite de 3 blocs le long du mur, {@link BlocBoite#FACING} = vers
 * la salle ; D-048). Etats du jeu ({@link ZombiesDecor.EtatBoite}) animes par le rendu (BoiteRendu, modele
 * {@code zbmodeles/boite_v2.json}), temps du storyboard valide ({@code work/zombies_v3/boite/storyboard.png}) :
 * <ul>
 *   <li>FERMEE : les « ? » respirent, faisceau bleu ; si l'on vient d'une ouverture, le couvercle se referme
 *   ({@link #FERMETURE} ticks, petit rebond) ; si l'on vient d'une arrivee, les « ? » s'allument ;</li>
 *   <li>ROULETTE : ouverture en {@link #OUVERTURE} ticks (couvercle a 92 deg, lumiere, rayons), l'arme qui defile est
 *   l'ItemDisplay du jeu ; OUVERTE : arme offerte (l'instant d'ouverture est garde, {@link #depuisTirage} = arret) ;</li>
 *   <li>DEPART (nounours, {@link #DUREE_DEPART}) : le nounours monte, le couvercle claque, la caisse tremble puis
 *   s'envole en tournant et en retrecissant, le faisceau baisse et s'eteint ;</li>
 *   <li>ARRIVEE ({@link #DUREE_ARRIVEE}) : le faisceau s'allume, la caisse tombe du ciel, rebond et poussiere.</li>
 * </ul>
 * L'etat {@link BlocBoite#ALLUMEE} des trois cases eclaire les alentours tant qu'une boite est la, et choisit le modele
 * cuit de la case centrale (table seule, ou table + bidons + nounours quand l'emplacement est vide).
 */
public class BoiteBE extends ZbBE {
    /** Ouverture du couvercle, fermeture (avant le rebond) : ticks. */
    public static final int OUVERTURE = 10, FERMETURE = 8;
    /** Nounours : il monte (0-20), le couvercle claque (20-26), la caisse tremble (26-56), s'envole (56-96). */
    public static final int DUREE_DEPART = 100, P_MONTEE = 20, P_CLAQUE = 26, P_ENVOL = 56, P_FIN = 96;
    /** Arrivee : faisceau (0-10), chute de {@link #HAUTEUR_CHUTE} blocs (10-24), rebond et poussiere (24-30). */
    public static final int DUREE_ARRIVEE = 30, A_CHUTE = 10, A_IMPACT = 24;
    public static final float HAUTEUR_CHUTE = 6F;
    /** Ancien nom (compatibilite) : duree d'ouverture du couvercle. */
    public static final int DUREE_COUVERCLE = OUVERTURE;

    /** Couleur par defaut (bleu du faisceau). */
    public static final int COULEUR_DEFAUT = 0x9FE3FF;
    private ZombiesDecor.EtatBoite etat = ZombiesDecor.EtatBoite.ABSENTE;
    /** Etat precedent (anime la fermeture apres une ouverture, l'allumage apres une arrivee). */
    private ZombiesDecor.EtatBoite precedent = ZombiesDecor.EtatBoite.ABSENTE;
    /** Couleur de la rarete du dernier tirage (eclat a l'arret), tant que la boite est ouverte. */
    private int couleur = COULEUR_DEFAUT;
    /** Temps de jeu de l'arret de la roulette. */
    private long tirage = -100000L;

    public BoiteBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.BOITE_BE.get(), pos, state);
    }

    /** Serveur : la roulette s'arrete sur une arme de cette couleur de rarete. */
    public void tirage(int rgb) {
        couleur = rgb & 0xFFFFFF;
        if (level != null) tirage = level.getGameTime();
        synchroniser();
    }

    public int couleur() {
        return couleur;
    }

    /** Ticks depuis l'arret de la roulette (rendu). */
    public float depuisTirage(float partiel) {
        return level == null ? 1e6F : (float) (level.getGameTime() - tirage) + partiel;
    }

    /** Serveur : nouvel etat (anime). */
    public void etat(ZombiesDecor.EtatBoite e) {
        if (e == null) e = ZombiesDecor.EtatBoite.ABSENTE;
        ZombiesDecor.EtatBoite avant = etat;
        // meme etat : l'animation est rejouee (banc d'essai), l'etat precedent reste celui d'avant
        if (avant != e) precedent = avant;
        etat = e;
        if (e != ZombiesDecor.EtatBoite.OUVERTE) couleur = COULEUR_DEFAUT;
        // garder l'instant d'ouverture quand on passe de ROULETTE a OUVERTE (couvercle deja leve)
        boolean ouvertAvant = avant == ZombiesDecor.EtatBoite.ROULETTE || avant == ZombiesDecor.EtatBoite.OUVERTE;
        boolean ouvert = e == ZombiesDecor.EtatBoite.ROULETTE || e == ZombiesDecor.EtatBoite.OUVERTE;
        if (!(ouvertAvant && ouvert)) marquer();
        eclairer(e != ZombiesDecor.EtatBoite.ABSENTE);
        synchroniser();
    }

    /** Serveur : etat sans animation (installation). */
    public void reinitialiser(ZombiesDecor.EtatBoite e) {
        etat = e == null ? ZombiesDecor.EtatBoite.ABSENTE : e;
        precedent = etat;
        depuis = -100000L;
        eclairer(etat != ZombiesDecor.EtatBoite.ABSENTE);
        synchroniser();
    }

    private void eclairer(boolean oui) {
        if (level == null || level.isClientSide) return;
        BlockState s = getBlockState();
        if (!s.hasProperty(BlocBoite.FACING)) return;
        Direction f = s.getValue(BlocBoite.FACING);
        for (BlockPos p : new BlockPos[]{worldPosition, worldPosition.relative(f.getClockWise()), worldPosition.relative(f.getCounterClockWise())}) {
            BlockState b = level.getBlockState(p);
            if (b.hasProperty(BlocBoite.ALLUMEE) && b.getValue(BlocBoite.ALLUMEE) != oui) level.setBlock(p, b.setValue(BlocBoite.ALLUMEE, oui), 2);
        }
    }

    public ZombiesDecor.EtatBoite etat() {
        return etat;
    }

    public ZombiesDecor.EtatBoite precedent() {
        return precedent;
    }

    /**
     * Vrai si la caisse occupe physiquement l'emplacement (collision haute) : presente, en train de partir (jusqu'a
     * l'envol) ou arrivee (apres l'impact). Sinon seule la table (et les bidons) bloque.
     */
    public boolean occupe() {
        return switch (etat) {
            case ABSENTE -> false;
            case DEPART -> ecoule(0) < P_ENVOL;
            case ARRIVEE -> ecoule(0) >= A_IMPACT - 2;
            default -> true;
        };
    }

    // ------------------------------------------------------------------ particules (client)
    /**
     * Ticker client : particules calees sur l'animation (aucune geometrie) : eclat bleu a l'ouverture, trainees
     * lumineuses pendant l'envol et la chute, nuage de poussiere a l'impact.
     */
    public static void tickClient(Level level, BlockPos pos, BlockState state, BoiteBE be) {
        if (be.etat == ZombiesDecor.EtatBoite.ABSENTE || be.etat == ZombiesDecor.EtatBoite.FERMEE || be.etat == ZombiesDecor.EtatBoite.OUVERTE) return;
        int t = (int) (level.getGameTime() - be.depuis);
        if (t < 0 || t > DUREE_DEPART) return;
        Direction f = state.hasProperty(BlocBoite.FACING) ? state.getValue(BlocBoite.FACING) : Direction.NORTH;
        RandomSource r = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        // axe de la caisse (x du modele) dans le monde : sens anti-horaire de la face (sud -> est)
        Direction axe = f.getCounterClockWise();
        double ax = axe.getStepX(), az = axe.getStepZ();
        switch (be.etat) {
            case ROULETTE -> {
                if (t == 5) {
                    // eclat a l'ouverture (pic des rayons)
                    for (int i = 0; i < 16; i++) {
                        double u = (r.nextDouble() - 0.5) * 2.4;
                        level.addParticle(ZbRegistres.LUEUR.get(), cx + ax * u, cy + 0.95 + r.nextDouble() * 0.5, cz + az * u, 0.62, 0.86, 1.0);
                    }
                }
            }
            case DEPART -> {
                if (t >= P_ENVOL && t < P_FIN && (t & 1) == 0) {
                    float p = (t - P_ENVOL) / (float) (P_FIN - P_ENVOL);
                    double y = cy + 0.6 + 8.0 * p * p;
                    for (int i = 0; i < 2; i++) {
                        double u = (r.nextDouble() - 0.5) * 2.2 * (1 - p);
                        level.addParticle(ZbRegistres.LUEUR.get(), cx + ax * u, y - r.nextDouble() * 0.6, cz + az * u, 0.55, 0.8, 1.0);
                    }
                }
            }
            case ARRIVEE -> {
                if (t >= A_CHUTE && t < A_IMPACT) {
                    float p = (t - A_CHUTE) / (float) (A_IMPACT - A_CHUTE);
                    double y = cy + 0.3 + HAUTEUR_CHUTE * (1 - p * p);
                    for (int i = 0; i < 2; i++) {
                        double u = (r.nextDouble() - 0.5) * 2.4;
                        level.addParticle(ZbRegistres.LUEUR.get(), cx + ax * u, y + 0.5 + r.nextDouble() * 0.8, cz + az * u, 0.55, 0.8, 1.0);
                    }
                } else if (t == A_IMPACT) {
                    // nuage de poussiere au ras de la table : bouffees + poussiere du sol
                    BlockState sol = level.getBlockState(pos.below());
                    boolean grains = sol.getRenderShape() != RenderShape.INVISIBLE && !sol.isAir();
                    for (int i = 0; i < 18; i++) {
                        double u = (r.nextDouble() - 0.5) * 3.0, w = (r.nextDouble() - 0.5) * 1.2;
                        double x = cx + ax * u + az * w, z = cz + az * u - ax * w;
                        double vu = Math.signum(u) * (0.03 + r.nextDouble() * 0.05);
                        level.addParticle(ParticleTypes.POOF, x, cy + 0.15 + r.nextDouble() * 0.2, z, ax * vu, 0.01, az * vu);
                        if (grains && i % 2 == 0) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, sol), x, cy + 0.1, z, 0, 0.05, 0);
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putByte("Etat", (byte) etat.ordinal());
        tag.putByte("Avant", (byte) precedent.ordinal());
        tag.putInt("Couleur", couleur);
        tag.putLong("Tirage", tirage);
    }

    @Override
    protected void lire(CompoundTag tag) {
        ZombiesDecor.EtatBoite[] v = ZombiesDecor.EtatBoite.values();
        int i = tag.getByte("Etat");
        etat = i >= 0 && i < v.length ? v[i] : ZombiesDecor.EtatBoite.ABSENTE;
        int a = tag.contains("Avant") ? tag.getByte("Avant") : i;
        precedent = a >= 0 && a < v.length ? v[a] : etat;
        couleur = tag.contains("Couleur") ? tag.getInt("Couleur") : COULEUR_DEFAUT;
        tirage = tag.contains("Tirage") ? tag.getLong("Tirage") : -100000L;
    }

    /**
     * Volume de rendu : la caisse (2,6 x 0,9 bloc, quelle que soit l'orientation), le faisceau jusqu'a 18 blocs, l'envol
     * (8 blocs) et la chute (6 blocs) compris. Calcule une fois (la position ne change pas).
     */
    private AABB volume;

    @Override
    public AABB getRenderBoundingBox() {
        if (volume == null) {
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY(), z = worldPosition.getZ() + 0.5;
            volume = new AABB(x - 1.6, y - 0.1, z - 1.6, x + 1.6, y + 18.5, z + 1.6);
        }
        return volume;
    }
}
