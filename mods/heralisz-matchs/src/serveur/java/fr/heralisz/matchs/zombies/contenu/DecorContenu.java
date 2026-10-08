package fr.heralisz.matchs.zombies.contenu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.zombies.contenu.bloc.ArmeMurBE;
import fr.heralisz.matchs.zombies.contenu.bloc.AtelierBE;
import fr.heralisz.matchs.zombies.contenu.bloc.AtoutBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocArmeMur;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocAtout;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocBoite;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocEasterEgg;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocFenetre;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocLarge;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocLevier;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocObstacle;
import fr.heralisz.matchs.zombies.contenu.bloc.BoiteBE;
import fr.heralisz.matchs.zombies.contenu.bloc.EasterBE;
import fr.heralisz.matchs.zombies.contenu.bloc.FenetreBE;
import fr.heralisz.matchs.zombies.contenu.bloc.LevierBE;
import fr.heralisz.matchs.zombies.contenu.bloc.ObstacleBE;
import fr.heralisz.matchs.zombies.contenu.bloc.PapBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocCollision;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocMachine;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocObjetQuete;
import fr.heralisz.matchs.zombies.contenu.bloc.MachineBE;
import fr.heralisz.matchs.zombies.contenu.bloc.ObjetQueteBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoAnime;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoGiant;
import fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import fr.heralisz.matchs.zombies.jeu.api.PlanGiant;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatPad;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import fr.heralisz.matchs.zombies.contenu.entite.BonusEntite;
import fr.heralisz.matchs.zombies.contenu.entite.MateriauEntite;
import fr.heralisz.matchs.zombies.contenu.entite.OuvertureEntite;
import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.PlanZombies;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Rendu du decor d'une partie (implementation de {@link ZombiesDecor}, enregistree par {@link ZombiesContenu}) : pose
 * les blocs-entites du plan ({@link #installer}) et les fait suivre la verite du jeu (planches, portes, boite, courant,
 * atouts, Pack-a-Punch, etablis, easter egg), declenche les animations des creatures, les animations d'ouverture et les
 * particules maison. Les methodes {@code poser*} servent aussi au banc ({@code /zbdev}). Fil serveur ; aucune
 * exception ne remonte (journal).
 * <p>Portes : seul le plan median de la porte (la couche du milieu le long de la normale) est remplace par l'element du
 * contenu ({@link BlocObstacle}) ; le cadre, les chaines et le talus poses par la carte autour restent. Pancartes : la
 * case {@code pancarte} (recto) et {@code pancarte_verso} des metadonnees de la carte.
 */
public class DecorContenu implements ZombiesDecor {
    /** Drapeaux de pose : clients prevenus, formes connues (pas de mise a jour des voisins). */
    private static final int POSE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Plans des instances installees (id d'instance -> plan). */
    private final Map<String, PlanZombies> plans = new HashMap<>();

    // ------------------------------------------------------------------ installation
    @Override
    public void installer(Instance instance, PlanZombies plan) {
        ServerLevel l = instance.niveau();
        plans.put(instance.id(), plan);
        int n = 0, no = 0;
        for (PlanZombies.Fenetre f : plan.fenetres()) if (poserFenetre(l, f.boite(), f.exterieur()) != null) n++;
        for (PlanZombies.ArmeMur a : plan.armes()) poserArme(l, a.pos(), a.face(), a.gunId());
        for (Pose b : plan.boites()) poserBoite(l, b.bloc(), Direction.fromYRot(b.yaw()), EtatBoite.ABSENTE);
        for (PlanZombies.Atout a : plan.atouts()) if (!a.secret()) poserAtout(l, a.pos(), a.face(), a.type(), false);
        PlanGiant g = plan.giant();
        // v3 : l'armoire a fusibles remplace le levier v1
        if (plan.courant() != null && (g == null || g.courant == null)) poserLevier(l, plan.courant().bloc(), Direction.fromYRot(plan.courant().yaw()));
        for (PlanZombies.Porte po : plan.portes()) {
            try {
                if (poserObstacle(l, po, verso(instance, po), false) != null) no++;
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.warn("[zombies] porte {} : {}", po.id(), ex.toString());
            }
        }
        PlanZombies.PackAPunch pap = plan.packAPunch();
        if (pap != null) {
            PapBE be = poserPap(l, pap.pos(), pap.face());
            if (be != null) be.etat(EtatPap.ETEINTE, ItemStack.EMPTY);
        }
        for (PlanZombies.Atelier at : plan.ateliers()) poserAtelier(l, at.pos(), at.face());
        PlanZombies.EasterEgg ee = plan.easterEgg();
        if (ee != null) for (PlanZombies.EtapeEasterEgg e : ee.etapes()) poserEaster(l, e.pos(), e.type(), faceEtape(instance, e));
        if (g != null) {
            try {
                installerGiant(instance, l, g);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] machines de The Giant", ex);
            }
        }
        HeraliszMatchs.LOG.info("[zombies] decor installe dans {} : {} fenetres, {} portes, {} armes, {} boites, {} atouts, levier {}, "
                        + "pack-a-punch {}, {} etablis, {} etapes", instance.id(), n, no, plan.armes().size(), plan.boites().size(),
                plan.atouts().size(), plan.courant() != null, pap != null, plan.ateliers().size(), ee == null ? 0 : ee.etapes().size());
    }

    @Override
    public void nettoyer(Instance instance, PlanZombies plan) {
        AABB b = instance.bornes();
        ServerLevel l = instance.niveau();
        for (BonusEntite e : l.getEntitiesOfClass(BonusEntite.class, b)) e.discard();
        for (MateriauEntite e : l.getEntitiesOfClass(MateriauEntite.class, b)) e.discard();
        for (OuvertureEntite e : l.getEntitiesOfClass(OuvertureEntite.class, b)) e.discard();
        plans.remove(instance.id());
        machines.remove(instance.id());
    }

    private PlanZombies plan(Instance instance) {
        PlanZombies p = plans.get(instance.id());
        if (p == null) {
            p = PlanZombies.lire(instance.carte(), instance.origine());
            plans.put(instance.id(), p);
        }
        return p;
    }

    /** Pancarte du verso d'une porte (plan du jeu, sinon {@code pancarte_verso} des metadonnees), absolue, ou null. */
    @Nullable
    private static Pose verso(Instance instance, PlanZombies.Porte po) {
        if (po.pancarteVerso() != null) return po.pancarteVerso();
        try {
            JsonElement ps = instance.carte().meta().get("portes");
            if (ps == null || !ps.isJsonArray()) return null;
            for (JsonElement e : ps.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("id") || !po.id().equals(o.get("id").getAsString()) || !o.has("pancarte_verso")) continue;
                Pose p = PlanZombies.pose(o.get("pancarte_verso"));
                return p == null ? null : p.decaler(instance.origine());
            }
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[zombies] pancarte_verso de {} : {}", po.id(), ex.toString());
        }
        return null;
    }

    /** Orientation d'une etape d'easter egg ({@code face} des metadonnees), nord par defaut. */
    private static Direction faceEtape(Instance instance, PlanZombies.EtapeEasterEgg e) {
        try {
            JsonElement ee = instance.carte().meta().get("easter_egg");
            if (ee != null && ee.isJsonObject() && ee.getAsJsonObject().has("etapes")) {
                JsonElement s = ee.getAsJsonObject().getAsJsonArray("etapes").get(e.index());
                if (s.isJsonObject() && s.getAsJsonObject().has("face")) {
                    Direction d = PlanZombies.face(s.getAsJsonObject().get("face").getAsString(), null);
                    if (d != null) return d;
                }
            }
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[zombies] face de l'etape {} : {}", e.index(), ex.toString());
        }
        return Direction.NORTH;
    }

    // ------------------------------------------------------------------ pose des blocs-entites (aussi pour /zbdev)
    /** Pose une fenetre sur toutes les cases du volume ; rend son bloc-entite (6 planches) ou null. */
    @Nullable
    public static FenetreBE poserFenetre(ServerLevel l, BoundingBox bb, Direction exterieur) {
        BlockPos min = new BlockPos(bb.minX(), bb.minY(), bb.minZ());
        BlockState base = ZbRegistres.FENETRE.get().defaultBlockState().setValue(BlocFenetre.FACING, exterieur);
        for (BlockPos p : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
            l.setBlock(p, base.setValue(BlocFenetre.MAITRE, p.equals(min)), POSE);
        }
        if (l.getBlockEntity(min) instanceof FenetreBE be) {
            int largeur = exterieur.getAxis() == Direction.Axis.Z ? bb.getXSpan() : bb.getZSpan();
            be.configurer(largeur, bb.getYSpan(), exterieur);
            be.planches(FenetreBE.MAX, EvenementFenetre.INSTALLEE);
            return be;
        }
        return null;
    }

    @Nullable
    public static ArmeMurBE poserArme(ServerLevel l, BlockPos pos, Direction face, String gunId) {
        l.setBlock(pos, ZbRegistres.ARME_MUR.get().defaultBlockState().setValue(BlocArmeMur.FACING, face), POSE);
        if (l.getBlockEntity(pos) instanceof ArmeMurBE be) {
            be.arme(gunId);
            return be;
        }
        return null;
    }

    @Nullable
    public static BoiteBE poserBoite(ServerLevel l, BlockPos centre, Direction face, EtatBoite etat) {
        BlockState s = ZbRegistres.BOITE.get().defaultBlockState().setValue(BlocBoite.FACING, face);
        l.setBlock(centre.relative(face.getClockWise()), s.setValue(BlocBoite.PARTIE, 1), POSE);
        l.setBlock(centre.relative(face.getCounterClockWise()), s.setValue(BlocBoite.PARTIE, 2), POSE);
        l.setBlock(centre, s.setValue(BlocBoite.PARTIE, 0), POSE);
        if (l.getBlockEntity(centre) instanceof BoiteBE be) {
            be.reinitialiser(etat);
            return be;
        }
        return null;
    }

    @Nullable
    public static AtoutBE poserAtout(ServerLevel l, BlockPos pos, Direction face, TypeAtout type, boolean alimente) {
        BlockState s = ZbRegistres.ATOUT.get().defaultBlockState().setValue(BlocAtout.FACING, face).setValue(BlocAtout.ALIMENTE, alimente);
        l.setBlock(pos.above(), s.setValue(BlocAtout.MOITIE, DoubleBlockHalf.UPPER), POSE);
        l.setBlock(pos, s.setValue(BlocAtout.MOITIE, DoubleBlockHalf.LOWER), POSE);
        if (l.getBlockEntity(pos) instanceof AtoutBE be) {
            be.type(type);
            be.alimenter(alimente, false);
            return be;
        }
        return null;
    }

    @Nullable
    public static LevierBE poserLevier(ServerLevel l, BlockPos pos, Direction face) {
        l.setBlock(pos, ZbRegistres.LEVIER.get().defaultBlockState().setValue(BlocLevier.FACING, face), POSE);
        if (l.getBlockEntity(pos) instanceof LevierBE be) {
            be.reinitialiser();
            return be;
        }
        return null;
    }

    /** Pack-a-Punch : 6 cases (2 blocs de large centres sur {@code pos}, 2 de haut). */
    @Nullable
    public static PapBE poserPap(ServerLevel l, BlockPos pos, Direction face) {
        BlockState s = ZbRegistres.PAP.get().defaultBlockState().setValue(BlocLarge.FACING, face);
        for (int p = 5; p >= 0; p--) l.setBlock(BlocLarge.position(pos, face, p), s.setValue(BlocLarge.PARTIE, p), POSE);
        return l.getBlockEntity(pos) instanceof PapBE be ? be : null;
    }

    /** Etabli : 3 cases du bas (2 blocs de large centres sur {@code pos}). */
    @Nullable
    public static AtelierBE poserAtelier(ServerLevel l, BlockPos pos, Direction face) {
        BlockState s = ZbRegistres.ATELIER.get().defaultBlockState().setValue(BlocLarge.FACING, face);
        for (int p = 2; p >= 0; p--) l.setBlock(BlocLarge.position(pos, face, p), s.setValue(BlocLarge.PARTIE, p), POSE);
        if (l.getBlockEntity(pos) instanceof AtelierBE be) {
            be.reinitialiser();
            return be;
        }
        return null;
    }

    /** Etape d'easter egg ({@code type} = radio | interrupteur | objet). */
    @Nullable
    public static EasterBE poserEaster(ServerLevel l, BlockPos pos, String type, Direction face) {
        l.setBlock(pos, ZbRegistres.EASTER.get().defaultBlockState().setValue(BlocEasterEgg.FACING, face)
                .setValue(BlocEasterEgg.GENRE, BlocEasterEgg.genre(type)), POSE);
        if (l.getBlockEntity(pos) instanceof EasterBE be) {
            be.etat(EtatEtape.CACHEE);
            return be;
        }
        return null;
    }

    /**
     * Element d'ouverture d'une porte : remplace le plan median du volume par {@link BlocObstacle} (porte barree, grille
     * ou debris selon le type), avec ses pancartes (recto du plan du jeu, verso des metadonnees). {@code cadre} faux sur
     * la carte (elle a son propre cadre raye autour de l'ouverture).
     */
    @Nullable
    public static ObstacleBE poserObstacle(ServerLevel l, PlanZombies.Porte po, @Nullable Pose verso, boolean cadre) {
        PlanPorte g = PlanPorte.de(po);
        BlockState s = ZbRegistres.OBSTACLE.get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(g.x0, g.y0, g.z0, g.x1, g.y1, g.z1)) {
            l.setBlock(p, s.setValue(BlocObstacle.MAITRE, p.getX() == g.x0 && p.getY() == g.y0 && p.getZ() == g.z0), POSE);
        }
        if (!(l.getBlockEntity(new BlockPos(g.x0, g.y0, g.z0)) instanceof ObstacleBE be)) return null;
        be.configurer(po.type(), g.largeur, g.hauteur, 1, g.normale, po.cout());
        be.cadre(cadre);
        pancarte(be, po.pancarte(), g, po.boite());
        pancarte(be, verso, g, po.boite());
        return be;
    }

    /** Geometrie du plan pose d'une porte (plan median du volume le long de la normale). */
    private record PlanPorte(int x0, int y0, int z0, int x1, int y1, int z1, Direction normale, int largeur, int hauteur) {
        static PlanPorte de(PlanZombies.Porte po) {
            BoundingBox bb = po.boite();
            Direction n;
            if (po.pancarte() != null) n = Direction.fromYRot(po.pancarte().yaw());
            else n = bb.getXSpan() >= bb.getZSpan() ? Direction.SOUTH : Direction.EAST;
            boolean axeZ = n.getAxis() == Direction.Axis.Z;
            int largeur = axeZ ? bb.getXSpan() : bb.getZSpan();
            int prof = axeZ ? bb.getZSpan() : bb.getXSpan();
            int milieu = (prof - 1) / 2;
            int x0 = bb.minX(), x1 = bb.maxX(), z0 = bb.minZ(), z1 = bb.maxZ();
            if (axeZ) z0 = z1 = bb.minZ() + milieu;
            else x0 = x1 = bb.minX() + milieu;
            return new PlanPorte(x0, bb.minY(), z0, x1, bb.maxY(), z1, n, largeur, bb.getYSpan());
        }

        /** Origine du modele (centre bas du plan pose). */
        Vec3 origine() {
            return new Vec3((x0 + x1 + 1) / 2.0, y0, (z0 + z1 + 1) / 2.0);
        }
    }

    /**
     * Ajoute une pancarte : case d'air contre le mur ou le cadre (convention de la carte), ardoise accrochee au support
     * derriere la case, centree a mi-hauteur de la case ; case dans le volume = sur la face de l'element.
     */
    private static void pancarte(ObstacleBE be, @Nullable Pose p, PlanPorte g, BoundingBox bb) {
        if (p == null) return;
        Direction face = Direction.fromYRot(p.yaw());
        int bx = (int) Math.floor(p.x()), by = (int) Math.floor(p.y()), bz = (int) Math.floor(p.z());
        double cx = bx + 0.5, cz = bz + 0.5;
        boolean dedans = bb.isInside(bx, by, bz);
        double sx, sz;
        if (dedans) {
            sx = cx + face.getStepX() * 0.47;
            sz = cz + face.getStepZ() * 0.47;
        } else {
            sx = cx - face.getStepX() * 0.48;
            sz = cz - face.getStepZ() * 0.48;
        }
        Vec3 o = g.origine();
        be.pancarte((float) (sx - o.x), (float) (by + 0.22 - o.y), (float) (sz - o.z), face.toYRot());
    }

    // ------------------------------------------------------------------ suivi de l'etat du jeu
    @Override
    public void fenetre(Instance instance, PlanZombies.Fenetre f, int planches, EvenementFenetre ev) {
        ServerLevel l = instance.niveau();
        BlockPos min = new BlockPos(f.boite().minX(), f.boite().minY(), f.boite().minZ());
        FenetreBE be = l.getBlockEntity(min) instanceof FenetreBE b ? b : poserFenetre(l, f.boite(), f.exterieur());
        if (be == null) return;
        be.planches(planches, ev);
        Vec3 c = f.centre();
        double ix = c.x - f.exterieur().getStepX() * 0.45, iz = c.z - f.exterieur().getStepZ() * 0.45;
        if (ev == EvenementFenetre.ARRACHEE) {
            particules(l, ZbRegistres.COPEAU.get(), ix, c.y, iz, 14, 0.5, 0.4, 0.5, 0.12);
        } else if (ev == EvenementFenetre.REPAREE) {
            particules(l, ZbRegistres.COPEAU.get(), ix, c.y, iz, 5, 0.4, 0.3, 0.4, 0.05);
            particules(l, ZbRegistres.CENDRE.get(), ix, c.y, iz, 4, 0.4, 0.3, 0.4, 0.01);
        }
    }

    @Override
    public void porte(Instance instance, PlanZombies.Porte porte, boolean ouverte) {
        ServerLevel l = instance.niveau();
        if (!ouverte) {
            // refermee (fin de partie) : l'element revient tel quel
            poserObstacle(l, porte, verso(instance, porte), false);
            return;
        }
        ouvrirEffet(l, porte, false);
    }

    /** Effet d'ouverture d'une porte dont les blocs viennent d'etre retires (animation, particules, son de grille). */
    public static void ouvrirEffet(ServerLevel l, PlanZombies.Porte porte, boolean cadre) {
        PlanPorte g = PlanPorte.de(porte);
        Vec3 o = g.origine();
        String type = porte.type();
        l.addFreshEntity(OuvertureEntite.creer(l, o.x, o.y, o.z, g.normale.toYRot(), type, g.largeur, g.hauteur, 1, cadre));
        AABB v = porte.volume();
        Vec3 c = v.getCenter();
        int n = (int) Math.min(70, 8 + v.getXsize() * v.getYsize() * v.getZsize() * 3);
        switch (type) {
            case "debris" -> {
                particules(l, ZbRegistres.CENDRE.get(), c.x, c.y, c.z, n, v.getXsize() / 2, v.getYsize() / 2, v.getZsize() / 2, 0.05);
                particules(l, ZbRegistres.BRUME.get(), c.x, v.minY + 0.3, c.z, n / 3 + 3, v.getXsize() / 2, 0.3, v.getZsize() / 2, 0.02);
            }
            case "grille" -> {
                particules(l, ZbRegistres.ETINCELLE.get(), c.x, v.minY + 1.1, c.z, 18, 0.3, 0.2, 0.3, 0.18);
                l.playSound(null, c.x, c.y, c.z, ZbSons.GRILLE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            default -> {
                particules(l, ZbRegistres.COPEAU.get(), c.x, c.y, c.z, n / 2, v.getXsize() / 2, v.getYsize() / 2, v.getZsize() / 2, 0.1);
                particules(l, ZbRegistres.CENDRE.get(), c.x, v.minY + 0.5, c.z, n / 3, v.getXsize() / 2, 0.5, v.getZsize() / 2, 0.02);
            }
        }
    }

    @Override
    public void boite(Instance instance, int emplacement, EtatBoite etat) {
        BoiteBE be = boiteBE(instance, emplacement);
        if (be == null) return;
        be.etat(etat);
        ServerLevel l = instance.niveau();
        BlockPos c = be.getBlockPos();
        double x = c.getX() + 0.5, y = c.getY() + 0.6, z = c.getZ() + 0.5;
        // boite v2 : l'eclat d'ouverture, les trainees et la poussiere de l'arrivee sont des particules cote client,
        // calees sur l'animation (BoiteBE.tickClient) ; ici seulement l'allumage du faisceau et la sortie du nounours
        switch (etat) {
            case ARRIVEE -> lueurs(l, x, y + 3.0, z, 0x9FE3FF, 16, 0.4, 6.0);
            case DEPART -> {
                lueurs(l, x, y + 0.5, z, 0xFFE38A, 16, 0.8, 0.4);
                particules(l, ZbRegistres.BRUME.get(), x, y, z, 6, 0.8, 0.2, 0.4, 0.01);
            }
            default -> {
            }
        }
    }

    @Nullable
    private BoiteBE boiteBE(Instance instance, int emplacement) {
        PlanZombies plan = plan(instance);
        if (emplacement < 0 || emplacement >= plan.boites().size()) return null;
        Pose p = plan.boites().get(emplacement);
        ServerLevel l = instance.niveau();
        BlockPos c = p.bloc();
        return l.getBlockEntity(c) instanceof BoiteBE b ? b : poserBoite(l, c, Direction.fromYRot(p.yaw()), EtatBoite.ABSENTE);
    }

    @Override
    public void boiteTirage(Instance instance, int emplacement, Rarete rarete) {
        BoiteBE be = boiteBE(instance, emplacement);
        if (be == null || rarete == null) return;
        be.tirage(rarete.couleur);
        BlockPos c = be.getBlockPos();
        int n = 10 + rarete.ordinal() * 8;
        lueurs(instance.niveau(), c.getX() + 0.5, c.getY() + 1.1, c.getZ() + 0.5, rarete.couleur, n, 0.7, 0.8 + rarete.ordinal() * 0.3);
        if (rarete.ordinal() >= Rarete.LEGENDAIRE.ordinal()) {
            particules(instance.niveau(), ZbRegistres.ETINCELLE.get(), c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 24, 0.6, 0.3, 0.6, 0.2);
        }
    }

    @Override
    public void courant(Instance instance, boolean actif) {
        PlanZombies plan = plan(instance);
        if (plan.courant() == null || (plan.giant() != null && plan.giant().courant != null)) return;
        ServerLevel l = instance.niveau();
        BlockPos pos = plan.courant().bloc();
        LevierBE be = l.getBlockEntity(pos) instanceof LevierBE b ? b : poserLevier(l, pos, Direction.fromYRot(plan.courant().yaw()));
        if (be == null) return;
        be.actif(actif);
        if (actif) particules(l, ZbRegistres.ETINCELLE.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 26, 0.3, 0.4, 0.3, 0.25);
    }

    @Override
    public void atout(Instance instance, PlanZombies.Atout atout, boolean alimente) {
        ServerLevel l = instance.niveau();
        AtoutBE be = l.getBlockEntity(atout.pos()) instanceof AtoutBE b ? b : poserAtout(l, atout.pos(), atout.face(), atout.type(), false);
        if (be == null) return;
        // atouts melanges (The Giant) : la machine prend le type tire pour cette partie
        if (be.type() != atout.type()) be.type(atout.type());
        be.alimenter(alimente, true);
        if (alimente) {
            BlockPos p = atout.pos();
            particules(l, ZbRegistres.ETINCELLE.get(), p.getX() + 0.5 + atout.face().getStepX() * 0.5, p.getY() + 1.9,
                    p.getZ() + 0.5 + atout.face().getStepZ() * 0.5, 14, 0.3, 0.3, 0.3, 0.12);
        }
    }

    @Override
    public void armeAchetee(Instance instance, PlanZombies.ArmeMur arme) {
        ServerLevel l = instance.niveau();
        if (l.getBlockEntity(arme.pos()) instanceof ArmeMurBE be) be.achetee();
        BlockPos p = arme.pos();
        lueurs(l, p.getX() + 0.5 - arme.face().getStepX() * 0.4, p.getY() + 0.5, p.getZ() + 0.5 - arme.face().getStepZ() * 0.4, 0xF2F0E8, 12, 0.9, 0.35);
    }

    // ------------------------------------------------------------------ Pack-a-Punch, etablis, easter egg
    @Override
    public void packAPunch(Instance instance, PlanZombies.PackAPunch machine, EtatPap etat, ItemStack arme) {
        ServerLevel l = instance.niveau();
        PapBE be = l.getBlockEntity(machine.pos()) instanceof PapBE b ? b : poserPap(l, machine.pos(), machine.face());
        if (be == null) return;
        be.etat(etat, arme);
        BlockPos p = machine.pos();
        double fx = p.getX() + 0.5 + machine.face().getStepX() * 0.55, fz = p.getZ() + 0.5 + machine.face().getStepZ() * 0.55;
        switch (etat) {
            // les étincelles sont des traînées dessinées par le client (PapBE.tickClient / PapRendu) : ici, la fumée et la lueur
            case AMELIORATION -> particules(l, ZbRegistres.BRUME.get(), p.getX() + 0.5, p.getY() + 1.8, p.getZ() + 0.5, 8, 0.6, 0.1, 0.3, 0.02);
            case OFFERTE -> lueurs(l, fx, p.getY() + 1.2, fz, 0xB45CFF, 12, 0.6, 0.4);
            default -> {
            }
        }
    }

    /** Le contenu dessine lui-meme l'arme dans la machine (plateau, halo) : pas d'item_display du jeu. */
    @Override
    public boolean packAPunchAfficheArme() {
        return true;
    }

    @Override
    public void atelier(Instance instance, PlanZombies.Atelier atelier, @Nullable String recette) {
        ServerLevel l = instance.niveau();
        AtelierBE be = l.getBlockEntity(atelier.pos()) instanceof AtelierBE b ? b : poserAtelier(l, atelier.pos(), atelier.face());
        if (be == null) return;
        if (recette == null) {
            be.reinitialiser();
            return;
        }
        be.fabriquer(recette);
        BlockPos p = atelier.pos();
        particules(l, ZbRegistres.ETINCELLE.get(), p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 16, 0.4, 0.1, 0.3, 0.16);
    }

    @Override
    public void easterEgg(Instance instance, PlanZombies.EtapeEasterEgg etape, EtatEtape etat) {
        ServerLevel l = instance.niveau();
        EasterBE be = l.getBlockEntity(etape.pos()) instanceof EasterBE b ? b : poserEaster(l, etape.pos(), etape.type(), faceEtape(instance, etape));
        if (be == null) return;
        be.etat(etat);
        BlockPos p = etape.pos();
        double x = p.getX() + 0.5, y = p.getY() + 0.4, z = p.getZ() + 0.5;
        switch (etat) {
            case TROUVEE -> lueurs(l, x, y, z, 0x6CFFB0, 18, 0.4, 0.8);
            case REFUSEE -> particules(l, ZbRegistres.ETINCELLE.get(), x, y, z, 8, 0.2, 0.2, 0.2, 0.15);
            case TERMINE -> {
                lueurs(l, x, y, z, 0x6CFFB0, 30, 0.6, 1.6);
                particules(l, ZbRegistres.ETINCELLE.get(), x, y + 0.3, z, 20, 0.4, 0.4, 0.4, 0.2);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ creatures
    @Override
    public void apparition(Instance instance, Mob zombie, @Nullable PlanZombies.Fenetre fenetre) {
        if (zombie instanceof ZombieLabo z) {
            if (fenetre == null) z.sortirDeTerre();
        } else if (zombie instanceof Rodeur r) {
            r.animerApparition();
            // v3 : chaque chien arrive dans un eclair (CoD)
            ServerLevel l = instance.niveau();
            l.sendParticles(ZbRegistres.ECLAIR.get(), r.getX(), r.getY(), r.getZ(), 0, 18.0, 0, 0, 1.0);
            l.sendParticles(ZbRegistres.LUEUR.get(), r.getX(), r.getY() + 0.5, r.getZ(), 0, 0.8, 0.9, 1.0, 1.0);
            l.playSound(null, r.getX(), r.getY(), r.getZ(), ZbSons.ECLAIR.get(), SoundSource.HOSTILE, 1.2F, 0.9F + l.random.nextFloat() * 0.2F);
        }
    }

    /**
     * Sortie d'un point de salle selon son genre : {@code conduit} = rampe hors de la bouche d'aeration (mur voisin), ou
     * tombe d'une bouche du plafond s'il n'y a pas de mur (0.2.3), avec fracas de grille ; {@code egout} = se hisse hors
     * d'une bouche d'egout (0.2.3) ; {@code gravats} = sortie de terre dans des eclats de pierre ; {@code tunnel} = il
     * arrive en marchant de l'obscurite (poussiere et brume, pas d'immobilite ; {@code rambarde} pareil, il enjambe un
     * garde-corps) ; {@code sol}, {@code coin} et inconnus =
     * sortie de terre. Le rodeur garde sa propre apparition.
     */
    @Override
    public void apparitionSalle(Instance instance, Mob zombie, String genre) {
        if (!(zombie instanceof ZombieLabo z)) {
            apparition(instance, zombie, null);
            return;
        }
        switch (genre == null ? "" : genre) {
            case "conduit" -> z.sortirDUnConduit();
            case "egout" -> z.sortirDeLEgout();
            case "gravats" -> z.sortirDesGravats();
            case "tunnel", "rambarde" -> {
                ServerLevel l = instance.niveau();
                particules(l, ZbRegistres.BRUME.get(), z.getX(), z.getY() + 0.6, z.getZ(), 8, 0.5, 0.5, 0.5, 0.01);
                particules(l, ZbRegistres.CENDRE.get(), z.getX(), z.getY() + 0.1, z.getZ(), 10, 0.4, 0.05, 0.4, 0.02);
            }
            default -> z.sortirDeTerre();
        }
    }

    /**
     * Objet de quete montre a son ancre (0.2.3) : si {@code pos} est une case d'un decor, l'objet est pose dans la case
     * d'air voisine la plus proche de l'ancre (ou celle qui le porte deja) et decale jusqu'a l'ancre ; le decor n'est
     * jamais touche. Sans case d'air a 1,5 bloc : simple eclat (comme avant).
     */
    @Override
    public void objetQueteAncre(Instance instance, BlockPos pos, Direction face, int genre, int etat, String donnee, @Nullable Vec3 ancre) {
        ServerLevel l = instance.niveau();
        if (ancre == null || l.getBlockState(pos).isAir() || l.getBlockEntity(pos) instanceof ObjetQueteBE) {
            objetQuete(instance, pos, face, genre, etat, donnee);
            if (ancre != null && l.getBlockEntity(pos) instanceof ObjetQueteBE be) {
                be.decalage((float) (ancre.x - pos.getX() - 0.5), (float) (ancre.y - pos.getY()), (float) (ancre.z - pos.getZ() - 0.5));
            }
            return;
        }
        int g = Mth.clamp(genre, 0, 5);
        BlockPos hote = null;
        double meilleur = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState s = l.getBlockState(p);
            boolean deja = s.getBlock() instanceof BlocObjetQuete && s.getValue(BlocObjetQuete.GENRE) == g;
            if (!deja && !s.isAir()) continue;
            double ox = ancre.x - (p.getX() + 0.5), oy = ancre.y - p.getY(), oz = ancre.z - (p.getZ() + 0.5);
            if (Math.abs(ox) > 1.45 || Math.abs(oy) > 1.45 || Math.abs(oz) > 1.45) continue;
            double d = ox * ox + oy * oy + oz * oz - (deja ? 100 : 0);
            if (d < meilleur) {
                meilleur = d;
                hote = p.immutable();
            }
        }
        if (hote == null) {
            objetQuete(instance, pos, face, genre, etat, donnee);
            return;
        }
        if (etat == ZombiesDecor.OBJET_CACHE && !(l.getBlockEntity(hote) instanceof ObjetQueteBE)) return;
        objetQuete(instance, hote, face, genre, etat, donnee);
        if (l.getBlockEntity(hote) instanceof ObjetQueteBE be) {
            be.decalage((float) (ancre.x - hote.getX() - 0.5), (float) (ancre.y - hote.getY()), (float) (ancre.z - hote.getZ() - 0.5));
        }
    }

    /** Bocal a moelle (0.2.3) : decor {@code zb_deco_bocal_moelle} pose (case d'air sur un appui) ou retire. */
    @Override
    public void bocal(Instance instance, BlockPos pos, Direction face, boolean visible) {
        ServerLevel l = instance.niveau();
        Block b = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(HeraliszMatchs.id("zb_deco_bocal_moelle"));
        if (b == null || b == Blocks.AIR) return;
        BlockState s = l.getBlockState(pos);
        if (!visible) {
            if (s.is(b)) l.setBlock(pos, Blocks.AIR.defaultBlockState(), POSE);
            return;
        }
        if (s.isAir() && !l.getBlockState(pos.below()).getCollisionShape(l, pos.below()).isEmpty()) {
            BlockState n = b.defaultBlockState();
            if (n.hasProperty(BlocDecoGiant.FACING)) n = n.setValue(BlocDecoGiant.FACING, face);
            l.setBlock(pos, n, POSE);
        }
    }

    /** Escalade (0.2.3) : pose d'escalade synchronisee, ou arc d'un saut d'obstacle (animation de l'enjambement). */
    @Override
    public void escalade(Instance instance, Mob zombie, int etat) {
        if (!(zombie instanceof ZombieLabo z)) return;
        switch (etat) {
            case ESCALADE_MUR -> {
                z.grimpe(ZombieLabo.GRIMPE_MUR);
                instance.niveau().playSound(null, z.getX(), z.getY(), z.getZ(), ZbSons.ZOMBIE_ENJAMBE.get(), SoundSource.HOSTILE, 0.7F, 0.8F);
            }
            case ESCALADE_SAUT -> {
                z.grimpe(ZombieLabo.GRIMPE_NON);
                z.animerEnjambement();
                instance.niveau().playSound(null, z.getX(), z.getY(), z.getZ(), ZbSons.ZOMBIE_ENJAMBE.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
            }
            default -> z.grimpe(ZombieLabo.GRIMPE_NON);
        }
    }

    @Override
    public void arrachage(Instance instance, Mob zombie, PlanZombies.Fenetre fenetre) {
        if (zombie instanceof ZombieLabo z) z.animerArrachage();
    }

    @Override
    public void franchissement(Instance instance, Mob zombie, PlanZombies.Fenetre fenetre) {
        if (zombie instanceof ZombieLabo z) z.animerEnjambement();
        Vec3 c = fenetre.centre();
        ServerLevel l = instance.niveau();
        l.playSound(null, c.x, c.y, c.z, ZbSons.ZOMBIE_ENJAMBE.get(), SoundSource.HOSTILE, 0.9F, 0.9F + l.random.nextFloat() * 0.2F);
        particules(l, ZbRegistres.CENDRE.get(), c.x, c.y - 0.5, c.z, 8, 0.5, 0.3, 0.5, 0.02);
    }

    // ------------------------------------------------------------------ bonus et materiaux
    @Nullable
    @Override
    public Entity bonus(ServerLevel niveau, Vec3 pos, TypeBonus type) {
        BonusEntite b = BonusEntite.creer(niveau, pos.x, pos.y, pos.z, type);
        if (!niveau.addFreshEntity(b)) return null;
        lueurs(niveau, pos.x, pos.y + 0.5, pos.z, type.couleur, 16, 0.4, 0.4);
        return b;
    }

    @Override
    public void bonusExpire(Entity bonus, int secondes) {
        if (bonus instanceof BonusEntite b) b.clignoter(secondes);
        else if (bonus instanceof MateriauEntite m) m.clignoter(secondes);
    }

    @Nullable
    @Override
    public Entity materiau(ServerLevel niveau, Vec3 pos, int quantite) {
        MateriauEntite m = MateriauEntite.creer(niveau, pos.x, pos.y, pos.z, quantite);
        return niveau.addFreshEntity(m) ? m : null;
    }

    // ------------------------------------------------------------------ v3 : machines et quete de The Giant (D-051)
    /** Cases des machines posees pour une instance (resolues a l'installation). */
    private static final class Machines {
        @Nullable
        BlockPos armoire, console, tour, plateforme, rideau;
        final BlockPos[] pads = new BlockPos[3], pupitres = new BlockPos[3], cuves = new BlockPos[3];
        @Nullable
        PlanGiant plan;
    }

    private final Map<String, Machines> machines = new HashMap<>();

    private Machines machines(Instance instance) {
        return machines.computeIfAbsent(instance.id(), k -> new Machines());
    }

    /** Pose les machines v3 (armoire, teleporteurs, cuves, pupitres, mainframe, rideau) et les remet a zero. */
    private void installerGiant(Instance instance, ServerLevel l, PlanGiant g) {
        long t0 = System.nanoTime();
        Machines m = new Machines();
        m.plan = g;
        machines.put(instance.id(), m);
        int cases = 0;
        // 0.2.3 : toutes les cases maitresses (et l'ouverture du rideau) sont reservees AVANT la pose, ordre fixe : aucun
        // morceau ni collision ne s'y pose, aucune case maitresse n'ecrase un morceau (pont du mainframe, niche du PaP)
        MachinesV3.Session session = new MachinesV3.Session();
        PlanGiant.Machine[] pupitresTp = new PlanGiant.Machine[3];
        if (g.courant != null) {
            m.armoire = sol(l, g.courant.pos());
            session.reserver(m.armoire);
        }
        for (PlanGiant.Teleporteur tp : g.teleporteurs) {
            int i = tp.index();
            if (i > 2) continue;
            session.reserver(tp.pad());
            if (tp.cuve() != null) session.reserver(tp.cuve().pos());
            pupitresTp[i] = tp.pupitre() != null ? tp.pupitre() : pupitreAuto(l, tp);
            if (pupitresTp[i] != null) session.reserver(pupitresTp[i].pos());
        }
        if (g.mainframe != null) {
            if (g.mainframe.console() != null) session.reserver(g.mainframe.console().pos());
            if (g.mainframe.plateforme() != null) session.reserver(g.mainframe.plateforme().pos());
            if (g.mainframe.tour() != null) session.reserver(g.mainframe.tour().pos());
        }
        session.reserver(g.rideau);
        if (g.courant != null) cases += poserMachine(l, MachinesV3.Type.COURANT, ZbRegistres.COURANT3.get(), m.armoire, g.courant.face(), session);
        for (PlanGiant.Teleporteur tp : g.teleporteurs) {
            int i = tp.index();
            if (i > 2) continue;
            m.pads[i] = tp.pad();
            cases += poserMachine(l, MachinesV3.Type.TP_PAD, ZbRegistres.TP_PAD.get(), tp.pad(), tp.face(), session);
            if (tp.cuve() != null) {
                m.cuves[i] = tp.cuve().pos();
                cases += poserMachine(l, MachinesV3.Type.CUVE_AMES, ZbRegistres.CUVE_AMES.get(), tp.cuve().pos(), tp.cuve().face(), session);
            }
            PlanGiant.Machine pu = pupitresTp[i];
            if (pu != null) {
                m.pupitres[i] = pu.pos();
                cases += poserMachine(l, MachinesV3.Type.TP_PUPITRE, ZbRegistres.TP_PUPITRE.get(), pu.pos(), pu.face(), session);
            }
            for (BlockPos p : new BlockPos[]{m.pads[i], m.pupitres[i], m.cuves[i]}) {
                if (p != null && l.getBlockEntity(p) instanceof MachineBE be) {
                    be.reinitialiser();
                    be.lettre(i);
                }
            }
        }
        PlanGiant.Mainframe mf = g.mainframe;
        if (mf != null) {
            if (mf.console() != null) {
                m.console = mf.console().pos();
                cases += poserMachine(l, MachinesV3.Type.MF_PUPITRE, ZbRegistres.MF_PUPITRE.get(), m.console, mf.console().face(), session);
            }
            if (mf.plateforme() != null) {
                m.plateforme = mf.plateforme().pos();
                cases += poserMachine(l, MachinesV3.Type.MF_PLATEFORME, ZbRegistres.MF_PLATEFORME.get(), m.plateforme, mf.plateforme().face(), session);
            }
            if (mf.tour() != null) {
                m.tour = mf.tour().pos();
                cases += poserMachine(l, MachinesV3.Type.MF_TOUR, ZbRegistres.MF_TOUR.get(), m.tour, mf.tour().face(), session);
                if (m.plateforme == null && l.getBlockEntity(m.tour) instanceof MachineBE be) {
                    // pas de plateforme validee : la tour dessine l'arrivee des teleportes sur le pad de la carte
                    be.arrivee(mf.pad().getX() - m.tour.getX(), mf.pad().getY() - m.tour.getY(), mf.pad().getZ() - m.tour.getZ());
                }
            }
            for (BlockPos p : new BlockPos[]{m.console, m.plateforme, m.tour}) if (p != null && l.getBlockEntity(p) instanceof MachineBE be) be.reinitialiser();
        }
        if (g.rideau != null) cases += poserRideau(l, m, g);
        int decors = reinitialiserDecors(l, g);
        HeraliszMatchs.LOG.debug("[zombies] decors cuits de The Giant remis a zero dans {} : {} case(s) changee(s)", instance.id(), decors);
        HeraliszMatchs.LOG.info("[zombies] machines de The Giant posees dans {} : {} cases en {} ms (armoire {}, {} pads, mainframe {}, rideau {}, {} case(s) non posee(s))",
                instance.id(), cases, (System.nanoTime() - t0) / 1_000_000, m.armoire != null, g.teleporteurs.size(), mf != null, m.rideau != null,
                session.sautes);
    }

    /** Case du pied d'une machine : descend tant que la case du dessous est de l'air (au plus 2), cote du sol. */
    private static BlockPos sol(ServerLevel l, BlockPos p) {
        BlockPos q = p;
        for (int i = 0; i < 2 && l.getBlockState(q.below()).isAir(); i++) q = q.below();
        return q;
    }

    private static int poserMachine(ServerLevel l, MachinesV3.Type t, BlocMachine bloc, BlockPos pos, Direction face, MachinesV3.Session s) {
        return MachinesV3.poser(l, t, pos, face, bloc.defaultBlockState().setValue(BlocMachine.FACING, face), POSE, s);
    }

    /**
     * Pupitre d'un pad sans position dans la carte : sur la ligne pad -> cuve, a mi-chemin entre le bord du pad et la cuve
     * (meme alignement que la cuve), tourne comme la cuve ; sinon devant le pad. Premiere case d'air trouvee.
     */
    @Nullable
    private static PlanGiant.Machine pupitreAuto(ServerLevel l, PlanGiant.Teleporteur tp) {
        BlockPos pad = tp.pad();
        BlockPos base;
        Direction face;
        if (tp.cuve() != null) {
            BlockPos c = tp.cuve().pos();
            int dx = c.getX() - pad.getX(), dz = c.getZ() - pad.getZ();
            boolean axeX = Math.abs(dx) >= Math.abs(dz);
            int d = axeX ? dx : dz;
            int demi = (int) Math.floor((axeX ? tp.emprise().getXsize() : tp.emprise().getZsize()) / 2.0);
            int pas = Integer.signum(d) * (demi + Math.max(1, (Math.abs(d) - demi) / 2));
            base = axeX ? new BlockPos(pad.getX() + pas, pad.getY(), c.getZ()) : new BlockPos(c.getX(), pad.getY(), pad.getZ() + pas);
            face = tp.cuve().face();
        } else {
            face = tp.face();
            base = pad.relative(face, 3);
        }
        Direction lat = face.getClockWise();
        for (int k : new int[]{0, 1, -1, 2, -2}) {
            BlockPos p = base.relative(lat, k);
            if (l.getBlockState(p).isAir() && !l.getBlockState(p.below()).isAir()) return new PlanGiant.Machine(p, face);
        }
        return null;
    }

    /**
     * Rideau du Pack-a-Punch : case maitresse au centre bas de l'ouverture (mise a l'echelle de l'ouverture), les autres
     * cases de l'ouverture deviennent des blocs de collision (la carte les remet a la fin de la partie).
     */
    private static int poserRideau(ServerLevel l, Machines m, PlanGiant g) {
        BoundingBox b = g.rideau;
        Direction f = g.faceRideau;
        boolean axeX = f.getAxis() == Direction.Axis.Z;
        int largeur = axeX ? b.getXSpan() : b.getZSpan();
        BlockPos hote = new BlockPos(axeX ? (b.minX() + b.maxX()) / 2 : b.minX(), b.minY(), axeX ? b.minZ() : (b.minZ() + b.maxZ()) / 2);
        BlockState col = ZbRegistres.COLLISION.get().defaultBlockState();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            if (p.equals(hote)) continue;
            l.setBlock(p, col.setValue(BlocCollision.FORME, 16), POSE);
            n++;
        }
        l.setBlock(hote, ZbRegistres.PAP_RIDEAU.get().defaultBlockState().setValue(BlocMachine.FACING, f), POSE);
        if (l.getBlockEntity(hote) instanceof MachineBE be) {
            be.reinitialiser();
            be.echelle(Mth.clamp(largeur / 2.4F, 0.8F, 2.2F), Mth.clamp(b.getYSpan() / 3.0F, 0.8F, 1.6F));
        }
        m.rideau = hote.immutable();
        return n + 1;
    }

    @Nullable
    private static MachineBE machine(ServerLevel l, @Nullable BlockPos p) {
        return p != null && l.getBlockEntity(p) instanceof MachineBE be ? be : null;
    }

    @Override
    public void armoire(Instance instance, int fusibles, boolean courant) {
        Machines m = machines(instance);
        ServerLevel l = instance.niveau();
        MachineBE be = machine(l, m.armoire);
        if (be == null) return;
        boolean pose = Integer.bitCount(fusibles) > Integer.bitCount(be.bits());
        be.bits(fusibles, true);
        be.etat(courant ? 1 : 0, 0);
        BlockPos p = m.armoire;
        Direction f = be.face();
        double x = p.getX() + 0.5 + f.getStepX() * 0.5, z = p.getZ() + 0.5 + f.getStepZ() * 0.5;
        if (pose) particules(l, ZbRegistres.ETINCELLE.get(), x, p.getY() + 0.8, z, 6, 0.15, 0.1, 0.15, 0.08);
        if (courant) {
            particules(l, ZbRegistres.ETINCELLE.get(), x, p.getY() + 1.3, z, 30, 0.35, 0.25, 0.3, 0.25);
            lueurs(l, x, p.getY() + 1.3, z, 0xBFE6FF, 14, 0.4, 0.6);
        }
    }

    @Override
    public void objetQuete(Instance instance, BlockPos pos, Direction face, int genre, int etat, String donnee) {
        ServerLevel l = instance.niveau();
        ObjetQueteBE be = l.getBlockEntity(pos) instanceof ObjetQueteBE b ? b : null;
        BlockState ici = l.getBlockState(pos);
        // D-053 : boitier du Fly Trap cuit dans la carte : sa facade est arrachee quand le Fly Trap s'active
        if (be == null && genre == ZombiesDecor.OBJET_BOITIER && ici.getBlock() instanceof BlocDecoGiant d && d.booleen("ouvert") != null) {
            if (etat == ZombiesDecor.OBJET_ACTIF) {
                BooleanProperty bp = d.booleen("ouvert");
                if (!ici.getValue(bp)) l.setBlock(pos, ici.setValue(bp, true), POSE);
                particules(l, ZbRegistres.ETINCELLE.get(), pos.getX() + 0.5 + face.getStepX() * 0.4, pos.getY() + 0.5, pos.getZ() + 0.5 + face.getStepZ() * 0.4,
                        24, 0.2, 0.25, 0.2, 0.22);
                particules(l, ZbRegistres.CENDRE.get(), pos.getX() + 0.5 + face.getStepX() * 0.5, pos.getY() + 0.4, pos.getZ() + 0.5 + face.getStepZ() * 0.5,
                        10, 0.2, 0.2, 0.2, 0.03);
            }
            if (etat == ZombiesDecor.OBJET_ACTIF || etat == ZombiesDecor.OBJET_PRIS) {
                lueurs(l, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0xD08CFF, 10, 0.3, 0.5);
            }
            return;
        }
        // D-053 : le fusible prend la place du boitier cuit de sa cachette (meme modele, dessine par l'etat de bloc)
        boolean cachette = be == null && genre == ZombiesDecor.OBJET_FUSIBLE && ici.getBlock() instanceof BlocDecoGiant
                && ici.getBlock() == ZbRegistres.decor("zb_deco_cachette_fusible");
        if (be == null) {
            if (etat == ZombiesDecor.OBJET_CACHE || (!ici.isAir() && !cachette)) {
                // case occupee par un decor de la carte : l'objet n'est qu'un eclat (l'interaction marche quand meme)
                if (etat != ZombiesDecor.OBJET_CACHE) lueurs(l, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 0xFFE2A0, 4, 0.2, 0.3);
                return;
            }
            Direction fp = cachette ? ici.getValue(BlocDecoGiant.FACING) : face;
            l.setBlock(pos, ZbRegistres.OBJET_QUETE.get().defaultBlockState().setValue(BlocObjetQuete.FACING, fp)
                    .setValue(BlocObjetQuete.GENRE, Mth.clamp(genre, 0, 5)), POSE);
            be = l.getBlockEntity(pos) instanceof ObjetQueteBE b ? b : null;
            if (be == null) return;
            Vec3 dec = ancre(l, pos, genre);
            if (dec != null) be.decalage((float) dec.x, (float) dec.y, (float) dec.z);
        }
        be.etat(etat, donnee);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.4, z = pos.getZ() + 0.5;
        if (etat == ZombiesDecor.OBJET_PRIS) {
            lueurs(l, x, y, z, genre == ZombiesDecor.OBJET_FUSIBLE ? 0xFFE2A0 : 0x8FFFD8, 12, 0.3, 0.6);
            particules(l, ZbRegistres.ETINCELLE.get(), x, y, z, 6, 0.15, 0.15, 0.15, 0.1);
        } else if (etat == ZombiesDecor.OBJET_ACTIF) {
            lueurs(l, x, y, z, genre == ZombiesDecor.OBJET_ARME ? 0xFFC860 : 0xD08CFF, 14, 0.4, 0.9);
        }
    }

    @Override
    public void pad(Instance instance, PlanGiant.Teleporteur t, EtatPad etat, int dureeTicks) {
        Machines m = machines(instance);
        ServerLevel l = instance.niveau();
        int i = t.index();
        if (i > 2) return;
        for (BlockPos p : new BlockPos[]{m.pads[i], m.pupitres[i]}) {
            MachineBE be = machine(l, p);
            if (be == null) continue;
            be.lettre(i);
            be.etat(etat.ordinal(), dureeTicks);
        }
        BlockPos p = t.pad();
        double x = p.getX() + 0.5, y = p.getY() + 0.3, z = p.getZ() + 0.5;
        switch (etat) {
            case CHARGE -> lueurs(l, x, y + 2.2, z, 0x7DFFC8, 10, 0.3, 0.5);
            case LIAISON -> particules(l, ZbRegistres.ETINCELLE.get(), x, y + 2.3, z, 20, 1.0, 0.6, 1.0, 0.2);
            case LIE -> lueurs(l, x, y, z, 0xBFE6FF, 18, 1.4, 0.4);
            case TRANSPORT -> {
                particules(l, ZbRegistres.ETINCELLE.get(), x, y + 1, z, 24, 1.0, 1.0, 1.0, 0.25);
                lueurs(l, x, y, z, 0xBFE6FF, 20, 1.2, 2.5);
            }
            case RECHARGE -> {
                particules(l, ZbRegistres.ECLAIR.get(), x, y - 0.3, z, 0, 5.0, 0, 0, 1.0);
                particules(l, ZbRegistres.BRUME.get(), x, y + 2.5, z, 10, 1.0, 0.4, 1.0, 0.02);
            }
            default -> {
            }
        }
    }

    @Override
    public void cuveAmes(Instance instance, PlanGiant.Teleporteur t, float niveau) {
        Machines m = machines(instance);
        int i = t.index();
        if (i > 2) return;
        MachineBE be = machine(instance.niveau(), m.cuves[i]);
        if (be == null) return;
        be.lettre(i);
        be.niveau(niveau, niveau > 0);
        if (niveau >= 0.999F) {
            be.etat(1, 0);
            BlockPos p = m.cuves[i];
            lueurs(instance.niveau(), p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 0x7DFFC8, 20, 0.35, 1.0);
        } else if (niveau <= 0.001F) {
            be.etat(0, 0);
        }
    }

    @Override
    public void ame(Instance instance, Vec3 depuis, PlanGiant.Teleporteur t) {
        Machines m = machines(instance);
        int i = t.index();
        BlockPos c = i <= 2 ? m.cuves[i] : null;
        if (c == null) return;
        ServerLevel l = instance.niveau();
        double tx = c.getX() + 0.5, ty = c.getY() + 1.62, tz = c.getZ() + 0.5;
        // une ame principale et deux petites qui la suivent, vecteur jusqu'au cornet dans la vitesse
        for (int k = 0; k < 3; k++) {
            double ox = (l.random.nextDouble() - 0.5) * 0.4, oz = (l.random.nextDouble() - 0.5) * 0.4;
            l.sendParticles(ZbRegistres.AME.get(), depuis.x + ox, depuis.y + k * 0.15, depuis.z + oz, 0, tx - depuis.x - ox, ty - depuis.y - k * 0.15,
                    tz - depuis.z - oz, 1.0);
        }
        l.playSound(null, depuis.x, depuis.y, depuis.z, ZbSons.AME.get(), SoundSource.AMBIENT, 0.6F, 0.9F + l.random.nextFloat() * 0.2F);
    }

    @Override
    public void mainframe(Instance instance, boolean courant, int lies, int enLiaison) {
        Machines m = machines(instance);
        ServerLevel l = instance.niveau();
        int n = Integer.bitCount(lies);
        MachineBE c = machine(l, m.console);
        if (c != null) {
            boolean nouveauLien = (lies & ~(c.bits() & 7)) != 0;
            c.etat(courant ? 1 : 0, 0);
            c.bits(lies | (enLiaison << 3), nouveauLien);
        }
        MachineBE t = machine(l, m.tour);
        if (t != null) {
            t.etat(courant ? 1 : 0, n);
            t.bits(n | (enLiaison != 0 ? 0x10 : 0), false);
            if (n >= 3) {
                BlockPos p = m.tour;
                lueurs(l, p.getX() + 0.5, p.getY() + 5.5, p.getZ() + 0.5, 0xBFE6FF, 30, 1.6, 2.0);
                particules(l, ZbRegistres.ECLAIR.get(), p.getX() + 0.5, p.getY() + 8.0, p.getZ() + 0.5, 0, 14.0, 0, 0, 1.0);
            }
        }
        MachineBE pl = machine(l, m.plateforme);
        if (pl != null) {
            pl.etat(courant ? 1 : 0, 0);
            pl.bits(lies, false);
        }
    }

    @Override
    public void arrivee(Instance instance, Vec3 pos) {
        Machines m = machines(instance);
        ServerLevel l = instance.niveau();
        MachineBE be = machine(l, m.plateforme != null ? m.plateforme : m.tour);
        if (be != null) be.impulsion();
        particules(l, ZbRegistres.ECLAIR.get(), pos.x, pos.y, pos.z, 0, 6.0, 0, 0, 1.0);
        particules(l, ZbRegistres.ETINCELLE.get(), pos.x, pos.y + 0.5, pos.z, 26, 1.0, 0.4, 1.0, 0.25);
        lueurs(l, pos.x, pos.y, pos.z, 0xBFE6FF, 20, 1.2, 2.2);
    }

    @Override
    public void rideauPap(Instance instance, int etat) {
        Machines m = machines(instance);
        ServerLevel l = instance.niveau();
        MachineBE be = machine(l, m.rideau);
        if (be == null) return;
        be.etat(etat, 0);
        if (etat >= 2 && m.plan != null && m.plan.rideau != null) {
            // ouvert : les cases de collision de l'ouverture disparaissent (la carte les remet a la fin)
            BoundingBox b = m.plan.rideau;
            for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                if (l.getBlockState(p).getBlock() instanceof BlocCollision) l.setBlock(p, Blocks.AIR.defaultBlockState(), POSE);
            }
            // la forme de la case maitresse change avec l'etat : prevenir les clients
            l.sendBlockUpdated(m.rideau, l.getBlockState(m.rideau), l.getBlockState(m.rideau), 3);
        }
        BlockPos p = m.rideau;
        if (etat == 1) lueurs(l, p.getX() + 0.5, p.getY() + 3.4, p.getZ() + 0.5, 0x2EFF48, 10, 0.3, 0.3);
    }

    @Override
    public void courantGiant(Instance instance, boolean actif) {
        Machines m = machines(instance);
        if (m.plan == null) return;
        ServerLevel l = instance.niveau();
        int n = etatsDecor(l, m.plan, "courant", actif);
        for (PlanGiant.Piege pg : m.plan.pieges) {
            for (PlanGiant.Machine pan : pg.panneaux()) {
                DecoAnimeBE be = decoAnime(l, pan.pos());
                if (be != null && be.etat() != DecoAnimeBE.EN_MARCHE) be.etat(actif ? DecoAnimeBE.DISPONIBLE : DecoAnimeBE.ETEINT, 0, 0);
            }
        }
        HeraliszMatchs.LOG.debug("[zombies] decors du courant ({}) : {} case(s)", actif, n);
    }

    @Override
    public void porteConfinement(Instance instance, boolean ouverte) {
        Machines m = machines(instance);
        if (m.plan == null || m.plan.porteConfinement == null) return;
        ServerLevel l = instance.niveau();
        DecoAnimeBE be = decoAnime(l, m.plan.porteConfinement.pos());
        if (be == null) return;
        be.etat(ouverte ? DecoAnimeBE.OUVERTE : DecoAnimeBE.FERMEE, 0, 0);
        if (ouverte) {
            Vec3 c = Vec3.atBottomCenterOf(m.plan.porteConfinement.pos());
            particules(l, ZbRegistres.BRUME.get(), c.x, c.y + 1.5, c.z, 30, 0.4, 1.2, 1.4, 0.03);
            particules(l, ZbRegistres.ETINCELLE.get(), c.x, c.y + 3.2, c.z, 16, 0.3, 0.2, 1.2, 0.15);
            l.playSound(null, c.x, c.y + 1.5, c.z, ZbSons.GRILLE.get(), SoundSource.BLOCKS, 1.6F, 0.55F);
        }
    }

    @Override
    public void piege(Instance instance, PlanGiant.Piege piege, boolean actif) {
        ServerLevel l = instance.niveau();
        Machines mc = machines(instance);
        if (mc.plan != null) etatsDecor(l, mc.plan, "piege:" + piege.id(), actif);
        if (actif) {
            for (PlanGiant.Machine pan : piege.panneaux()) {
                DecoAnimeBE be = decoAnime(l, pan.pos());
                if (be != null) be.etat(DecoAnimeBE.EN_MARCHE, piege.dureeTicks(), piege.rechargeTicks());
            }
        }
        Vec3 c = piege.volume().getCenter();
        if (actif) {
            particules(l, ZbRegistres.ETINCELLE.get(), c.x, c.y, c.z, 40, piege.volume().getXsize() / 2, 0.6, piege.volume().getZsize() / 2, 0.3);
            for (PlanGiant.Machine p : piege.panneaux()) {
                particules(l, ZbRegistres.ETINCELLE.get(), p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5, 10, 0.1, 0.1, 0.1, 0.15);
            }
        } else {
            particules(l, ZbRegistres.BRUME.get(), c.x, piege.volume().minY + 0.3, c.z, 8, piege.volume().getXsize() / 2, 0.2, piege.volume().getZsize() / 2, 0.01);
        }
    }

    @Override
    public void piegeTick(Instance instance, PlanGiant.Piege piege, @Nullable Vec3 tue) {
        ServerLevel l = instance.niveau();
        AABB v = piege.volume();
        double x = v.minX + l.random.nextDouble() * v.getXsize(), z = v.minZ + l.random.nextDouble() * v.getZsize();
        particules(l, ZbRegistres.ETINCELLE.get(), x, v.minY + 0.2 + l.random.nextDouble() * 1.6, z, 5, 0.3, 0.3, 0.3, 0.2);
        lueurs(l, x, v.minY + 0.5, z, 0x9FD8FF, 2, 0.5, 1.2);
        if (tue != null) {
            particules(l, ZbRegistres.ECLAIR.get(), tue.x, tue.y, tue.z, 0, 3.0, 0, 0, 1.0);
            particules(l, ZbRegistres.CENDRE.get(), tue.x, tue.y + 0.8, tue.z, 14, 0.3, 0.5, 0.3, 0.04);
        }
    }

    @Override
    public void passerelle(Instance instance, boolean abaissee) {
        Machines m = machines(instance);
        if (m.plan == null || m.plan.passerelleVolume == null || !abaissee) return;
        ServerLevel l = instance.niveau();
        BoundingBox b = m.plan.passerelleVolume;
        Vec3 c = new Vec3((b.minX() + b.maxX() + 1) / 2.0, b.minY() + 0.5, (b.minZ() + b.maxZ() + 1) / 2.0);
        particules(l, ZbRegistres.CENDRE.get(), c.x, c.y, c.z, 40, b.getXSpan() / 2.0, 1.0, b.getZSpan() / 2.0, 0.05);
        particules(l, ZbRegistres.BRUME.get(), c.x, c.y, c.z, 12, b.getXSpan() / 2.0, 0.4, b.getZSpan() / 2.0, 0.02);
        l.playSound(null, c.x, c.y, c.z, ZbSons.GRILLE.get(), SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    @Override
    public void rayonRobot(Instance instance, Vec3 depuis, Vec3 vers) {
        ServerLevel l = instance.niveau();
        int n = 48;
        for (int k = 0; k <= n; k++) {
            double p = k / (double) n;
            l.sendParticles(ZbRegistres.LUEUR.get(), depuis.x + (vers.x - depuis.x) * p, depuis.y + (vers.y - depuis.y) * p, depuis.z + (vers.z - depuis.z) * p,
                    0, 0.6, 0.85, 1.0, 1.0);
        }
        particules(l, ZbRegistres.ECLAIR.get(), vers.x, vers.y, vers.z, 0, 10.0, 0, 0, 1.0);
        particules(l, ZbRegistres.ETINCELLE.get(), vers.x, vers.y + 0.5, vers.z, 40, 1.5, 0.5, 1.5, 0.3);
    }

    @Override
    public void effet(Instance instance, Vec3 pos, String type) {
        ServerLevel l = instance.niveau();
        switch (type) {
            case "bocal_lueur" -> lueurs(l, pos.x, pos.y, pos.z, 0x7DFF6A, 2, 0.15, 0.3);
            case "bocal" -> {
                lueurs(l, pos.x, pos.y, pos.z, 0x7DFF6A, 16, 0.3, 0.6);
                particules(l, ZbRegistres.BRUME.get(), pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.01);
            }
            case "objet_touche" -> {
                lueurs(l, pos.x, pos.y, pos.z, 0x8FFFD8, 20, 0.4, 0.8);
                particules(l, ZbRegistres.ETINCELLE.get(), pos.x, pos.y, pos.z, 12, 0.2, 0.2, 0.2, 0.2);
            }
            case "fly_trap" -> lueurs(l, pos.x, pos.y, pos.z, 0xD08CFF, 24, 0.8, 1.2);
            case "phare" -> {
                lueurs(l, pos.x, pos.y, pos.z, 0xFFC860, 40, 0.4, 6.0);
                particules(l, ZbRegistres.ETINCELLE.get(), pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.3, 0.3, 0.2);
            }
            case "bowie" -> particules(l, ZbRegistres.ETINCELLE.get(), pos.x, pos.y, pos.z, 10, 0.3, 0.2, 0.3, 0.1);
            // 0.2.3 : relais 935 caches (visibles de loin : envoi force aux joueurs a moins de 96 blocs), relais abattu, grenade
            case "relais" -> lueursLoin(l, pos.x, pos.y, pos.z, 0x7FD8FF, 3, 0.25, 0.5);
            case "relais_touche" -> {
                lueursLoin(l, pos.x, pos.y, pos.z, 0x9FE8FF, 30, 0.6, 1.5);
                for (ServerPlayer p : l.players()) {
                    if (p.distanceToSqr(pos) < 96 * 96) l.sendParticles(p, ZbRegistres.ECLAIR.get(), true, pos.x, pos.y, pos.z, 0, 4.0, 0, 0, 1.0);
                }
                particules(l, ZbRegistres.ETINCELLE.get(), pos.x, pos.y, pos.z, 24, 0.3, 0.3, 0.3, 0.25);
            }
            case "grenade_secret" -> {
                lueurs(l, pos.x, pos.y, pos.z, 0xFFD27A, 30, 0.6, 2.0);
                particules(l, ZbRegistres.ETINCELLE.get(), pos.x, pos.y + 0.5, pos.z, 20, 0.4, 0.4, 0.4, 0.2);
            }
            case "neige" -> {
                particules(l, ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1, pos.z, 80, 2.5, 1.0, 3.5, 0.1);
                particules(l, ZbRegistres.BRUME.get(), pos.x, pos.y + 0.5, pos.z, 30, 2.5, 0.5, 3.5, 0.03);
            }
            default -> lueurs(l, pos.x, pos.y, pos.z, 0xFFFFFF, 8, 0.3, 0.5);
        }
    }

    // ------------------------------------------------------------------ decors cuits de The Giant (D-053)
    /** Bloc-entite anime (levier, porte) du decor dont {@code pos} est une case, ou null. */
    @Nullable
    private static DecoAnimeBE decoAnime(ServerLevel l, BlockPos pos) {
        BlockState s = l.getBlockState(pos);
        if (!(s.getBlock() instanceof BlocDecoAnime b)) return null;
        return l.getBlockEntity(b.maitresse(s, pos)) instanceof DecoAnimeBE be ? be : null;
    }

    /** Met la propriete booleenne des decors de ce declencheur a {@code valeur} ; rend le nombre de cases changees. */
    private static int etatsDecor(ServerLevel l, PlanGiant g, String declencheur, boolean valeur) {
        int n = 0;
        for (PlanGiant.EtatDecor e : g.etatsDecor) if (e.declencheur().equals(declencheur)) n += etatDecor(l, e, valeur);
        return n;
    }

    private static int etatDecor(ServerLevel l, PlanGiant.EtatDecor e, boolean valeur) {
        String id = e.bloc().contains(":") ? e.bloc().substring(e.bloc().indexOf(':') + 1) : e.bloc();
        Block bloc = ZbRegistres.decor(id);
        if (!(bloc instanceof BlocDecoGiant d)) return 0;
        BooleanProperty bp = d.booleen(e.propriete());
        if (bp == null) return 0;
        BoundingBox b = e.emprise();
        int n = 0;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    c.set(x, y, z);
                    BlockState s = l.getBlockState(c);
                    if (s.getBlock() != bloc || s.getValue(bp) == valeur) continue;
                    l.setBlock(c, s.setValue(bp, valeur), POSE);
                    n++;
                }
            }
        }
        return n;
    }

    /** Debut de partie : decors a etats eteints, leviers des pieges sans courant, porte de confinement fermee. */
    private static int reinitialiserDecors(ServerLevel l, PlanGiant g) {
        int n = 0;
        for (PlanGiant.EtatDecor e : g.etatsDecor) n += etatDecor(l, e, false);
        for (PlanGiant.Piege pg : g.pieges) {
            for (PlanGiant.Machine pan : pg.panneaux()) {
                DecoAnimeBE be = decoAnime(l, pan.pos());
                if (be != null) be.etat(DecoAnimeBE.ETEINT, 0, 0);
            }
        }
        if (g.porteConfinement != null) {
            DecoAnimeBE be = decoAnime(l, g.porteConfinement.pos());
            if (be != null) be.etat(DecoAnimeBE.FERMEE, 0, 0);
        }
        return n;
    }

    /** Ancre d'un objet de quete dans un decor (pixels du repere du prop, voir INTEGRATION.md des decors). */
    private record Ancre(String bloc, int genre, double x, double y, double z) {
    }

    private static final Ancre[] ANCRES = {
            new Ancre("zb_deco_incinerateur", ZombiesDecor.OBJET_SINGE, 0, 8.0, -1.5),
            new Ancre("zb_deco_cuve_eau", ZombiesDecor.OBJET_NOUNOURS, 0, 22.0, 0),
            new Ancre("zb_deco_fourneau_garage", ZombiesDecor.OBJET_ARME, 0, 13.6, 2.5),
            new Ancre("zb_deco_bureau_maxis", ZombiesDecor.OBJET_CARTE, 3.0, 12.48, 0.9)};

    /**
     * Decalage (blocs, monde) du point de pose d'un objet de quete vers l'ancre de son decor voisin (singe sur la sole de
     * l'incinerateur, nounours sur l'eau de la cuve, Annihilator sur la tablette du fourneau, carte sur le sous-main), ou
     * null s'il n'y a pas de decor a ancre autour de la case.
     */
    @Nullable
    private static Vec3 ancre(ServerLevel l, BlockPos pos, int genre) {
        for (Ancre a : ANCRES) {
            if (a.genre() != genre) continue;
            Block bloc = ZbRegistres.decor(a.bloc());
            if (!(bloc instanceof BlocDecoGiant d)) continue;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                BlockState s = l.getBlockState(p);
                if (s.getBlock() != bloc) continue;
                Vec3 w = d.pointMonde(s, p, a.x(), a.y(), a.z());
                Vec3 o = new Vec3(w.x - (pos.getX() + 0.5), w.y - pos.getY(), w.z - (pos.getZ() + 0.5));
                if (Math.abs(o.x) <= 1.5 && Math.abs(o.y) <= 1.5 && Math.abs(o.z) <= 1.5) return o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ outils
    private static void particules(ServerLevel l, ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double v) {
        l.sendParticles(p, x, y, z, n, dx, dy, dz, v);
    }

    /** Lueurs teintees envoyees de force (au-dela de 32 blocs) aux joueurs a moins de 96 blocs (relais caches, 0.2.3). */
    private static void lueursLoin(ServerLevel l, double x, double y, double z, int rgb, int n, double rayon, double hauteur) {
        double r = ((rgb >> 16) & 255) / 255.0, g = ((rgb >> 8) & 255) / 255.0, b = (rgb & 255) / 255.0;
        for (ServerPlayer p : l.players()) {
            if (p.distanceToSqr(x, y, z) > 96 * 96) continue;
            for (int i = 0; i < n; i++) {
                l.sendParticles(p, ZbRegistres.LUEUR.get(), true, x + (l.random.nextDouble() - 0.5) * 2 * rayon, y + l.random.nextDouble() * hauteur,
                        z + (l.random.nextDouble() - 0.5) * 2 * rayon, 0, r, g, b, 1.0);
            }
        }
    }

    /** Lueurs teintees : la couleur passe par la vitesse (une particule par envoi, vitesse = couleur). */
    public static void lueurs(ServerLevel l, double x, double y, double z, int rgb, int n, double rayon, double hauteur) {
        double r = ((rgb >> 16) & 255) / 255.0, g = ((rgb >> 8) & 255) / 255.0, b = (rgb & 255) / 255.0;
        for (int i = 0; i < n; i++) {
            l.sendParticles(ZbRegistres.LUEUR.get(), x + (l.random.nextDouble() - 0.5) * 2 * rayon, y + l.random.nextDouble() * hauteur,
                    z + (l.random.nextDouble() - 0.5) * 2 * rayon, 0, r, g, b, 1.0);
        }
    }
}
