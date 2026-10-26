package fr.heralisz.clans.territory.supply;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.registry.ClansRegistry;
import fr.heralisz.clans.registry.Sons;
import fr.heralisz.clans.territory.CaisseRavitaillementBlockEntity;
import fr.heralisz.clans.territory.Territoires;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Caisse sous parachute, version serveur (code d'origine) : chute de 0,35 bloc/tick avec derive bornee, traversee du
 * feuillage, atterrissage (bloc caisse rempli depuis la table de butin, son, fumee rouge), sauvegarde. Creee par
 * {@code ClansRegistry.CAISSE_PARACHUTE_USINE} dans un monde serveur, et par {@link Ravitaillement} au largage.
 */
public class CaisseParachuteEntityServeur extends CaisseParachuteEntity {
    private String zoneId = "", clanTag = "";
    private double derivX, derivZ, x0, z0;

    public CaisseParachuteEntityServeur(EntityType<? extends CaisseParachuteEntity> type, Level level) {
        super(type, level);
    }

    /** Zone et clan d'origine, derive aleatoire (direction et vitesse) ; a appeler apres setPos. */
    public void initialiser(String zoneId, String clanTag, Random r) {
        this.zoneId = zoneId == null ? "" : zoneId;
        this.clanTag = clanTag == null ? "" : clanTag;
        double ang = r.nextDouble() * Math.PI * 2, v = 0.012 + r.nextDouble() * 0.018;
        derivX = Math.cos(ang) * v;
        derivZ = Math.sin(ang) * v;
        x0 = getX();
        z0 = getZ();
        setYRot(r.nextFloat() * 360f);
    }

    @Override
    protected void chute() {
        double dx = derivX, dz = derivZ;
        double ex = getX() - x0, ez = getZ() - z0;
        if (ex * ex + ez * ez >= DERIVE_MAX * DERIVE_MAX) dx = dz = 0;
        Vec3 mv = new Vec3(dx, -CHUTE, dz);
        move(MoverType.SELF, mv);
        setDeltaMovement(mv);
        if (onGround() || verticalCollision) {
            if (surFeuillage()) {
                // le feuillage ne retient pas la caisse : elle s'y enfonce (la collision ignore ensuite ce bloc deja
                // traverse) et finit au sol sous l'arbre au lieu de rester perchee a la cime
                setPos(getX(), getY() - CHUTE, getZ());
            } else {
                atterrir();
            }
        } else if (getY() < level().getMinBuildHeight() - 8) {
            discard();
        }
    }

    /** Tout ce qui porte la caisse (blocs avec collision juste sous sa boite) est du feuillage. */
    private boolean surFeuillage() {
        AABB b = getBoundingBox();
        int y = Mth.floor(b.minY - 0.05);
        boolean feuilles = false;
        for (int x = Mth.floor(b.minX); x <= Mth.floor(b.maxX - 1e-6); x++) {
            for (int z = Mth.floor(b.minZ); z <= Mth.floor(b.maxZ - 1e-6); z++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level().getBlockState(p);
                if (s.getCollisionShape(level(), p).isEmpty()) continue;
                if (!s.is(BlockTags.LEAVES)) return false;
                feuilles = true;
            }
        }
        return feuilles;
    }

    private void atterrir() {
        ServerLevel level = (ServerLevel) level();
        BlockPos pos = blockPosition();
        for (int i = 0; i < 4 && !level.getBlockState(pos).canBeReplaced(); i++) pos = pos.above();
        level.setBlock(pos, ClansRegistry.CAISSE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CaisseRavitaillementBlockEntity be) {
            be.initialiser(zoneId, clanTag, System.currentTimeMillis());
            try {
                TableButin.remplir(level.getServer(), be, level.random);
            } catch (RuntimeException ex) {
                // table de butin editee a la main et incoherente : caisse vide plutot qu'un plantage de tick d'entite
                HeraliszClans.LOG.error("[clans] remplissage de la caisse de ravitaillement impossible (table de butin a verifier)", ex);
            }
            be.synchroniser();
            int objets = 0;
            for (int i = 0; i < be.getContainerSize(); i++) if (!be.getItem(i).isEmpty()) objets++;
            HeraliszClans.LOG.info("[clans] caisse de ravitaillement posee en {} ({}, a {} bloc(s) de la verticale du largage) : {} objet(s)",
                    pos.toShortString(), zoneId, Math.round(Math.hypot(pos.getX() + 0.5 - x0, pos.getZ() + 0.5 - z0)), objets);
        } else {
            HeraliszClans.LOG.warn("[clans] caisse de ravitaillement non posee en {}", pos);
        }
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Sons.CAISSE_ATTERRIT.get(), SoundSource.BLOCKS, 2f, 1f);
        Ravitaillement r = Territoires.ravitaillement();
        if (r != null) r.fumeeRouge(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, Ravitaillement.FUMEE_TICKS);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        zoneId = tag.getString("zone");
        clanTag = tag.getString("clan");
        derivX = tag.getDouble("derivX");
        derivZ = tag.getDouble("derivZ");
        x0 = tag.getDouble("x0");
        z0 = tag.getDouble("z0");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("zone", zoneId);
        tag.putString("clan", clanTag);
        tag.putDouble("derivX", derivX);
        tag.putDouble("derivZ", derivZ);
        tag.putDouble("x0", x0);
        tag.putDouble("z0", z0);
    }
}
