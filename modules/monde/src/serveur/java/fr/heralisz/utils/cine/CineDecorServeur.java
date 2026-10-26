package fr.heralisz.utils.cine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Décor de la cinématique, côté serveur (jar fusionné : partie serveur de CineDecor, code d'origine déplacé tel quel) :
 * données du monde « heraliz_cinematique » (réveil, regard, épave, monde ; même nom, même format), réglages des ops,
 * barrières invisibles de l'épave, dispositions de l'épave, point de réveil praticable.
 */
public final class CineDecorServeur extends SavedData {
    private static final String NOM = "heraliz_cinematique";
    /** Demi-longueur, bas et haut (au-dessus du rail) et demi-largeur des caisses : volume des barrières. */
    private static final double[] LOCO = {9.7, 0.2, 5.1, 2.15}, VOITURE = {11.3, 0.2, 5.0, 2.15};

    private CineDecor.Etat etat;
    private final Set<Long> barrieres = new LinkedHashSet<>();

    private CineDecorServeur() {}

    // ---------------------------------------------------------------------------------------------- monde
    public static CineDecorServeur get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CineDecorServeur::lire, CineDecorServeur::new, NOM);
    }

    public CineDecor.Etat etat() {
        return etat != null ? etat : CineDecor.defaut();
    }

    /** Le monde du décor (celui du spawn s'il existe sur ce serveur, sinon le monde principal). */
    public static ServerLevel niveau(MinecraftServer server, CineDecor.Etat e) {
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, e.monde()));
        return l != null ? l : server.overworld();
    }

    public ServerLevel niveau(MinecraftServer server) {
        return niveau(server, etat());
    }

    public boolean regle() {
        return etat != null;
    }

    private static CineDecorServeur lire(CompoundTag t) {
        CineDecorServeur d = new CineDecorServeur();
        if (t.contains("reveil")) {
            CompoundTag r = t.getCompound("reveil");
            List<CineDecor.Piece> ep = new ArrayList<>();
            for (Tag x : t.getList("epave", Tag.TAG_COMPOUND)) {
                CompoundTag p = (CompoundTag) x;
                ep.add(new CineDecor.Piece(p.getString("modele"), p.getDouble("x"), p.getDouble("y"), p.getDouble("z"), p.getFloat("lacet"), p.getFloat("roulis")));
            }
            ResourceLocation m = t.contains("monde") ? ResourceLocation.tryParse(t.getString("monde")) : null;
            d.etat = new CineDecor.Etat(new Vec3(r.getDouble("x"), r.getDouble("y"), r.getDouble("z")), r.getFloat("cap"), List.copyOf(ep),
                    m != null ? m : CineDecor.MONDE_PRINCIPAL);
        }
        for (long l : t.getLongArray("barrieres")) {
            d.barrieres.add(l);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        if (etat != null) {
            CompoundTag r = new CompoundTag();
            r.putDouble("x", etat.reveil().x);
            r.putDouble("y", etat.reveil().y);
            r.putDouble("z", etat.reveil().z);
            r.putFloat("cap", etat.cap());
            t.put("reveil", r);
            ListTag l = new ListTag();
            for (CineDecor.Piece p : etat.epave()) {
                CompoundTag c = new CompoundTag();
                c.putString("modele", p.modele());
                c.putDouble("x", p.x());
                c.putDouble("y", p.y());
                c.putDouble("z", p.z());
                c.putFloat("lacet", p.lacet());
                c.putFloat("roulis", p.roulis());
                l.add(c);
            }
            t.put("epave", l);
            t.putString("monde", etat.monde().toString());
        }
        t.putLongArray("barrieres", barrieres.stream().mapToLong(Long::longValue).toArray());
        return t;
    }

    // ---------------------------------------------------------------------------------------------- réglages
    /** Nouveau point de réveil ; s'il change de monde, l'épave de l'ancien monde est retirée (barrières comprises). */
    void reveil(ServerLevel l, Vec3 pos, float cap) {
        ResourceLocation m = l.dimension().location();
        if (!m.equals(etat().monde())) {
            retirerBarrieres(niveau(l.getServer()));
            etat = new CineDecor.Etat(pos, Mth.wrapDegrees(cap), List.of(), m);
        } else {
            etat = new CineDecor.Etat(pos, Mth.wrapDegrees(cap), etat().epave(), m);
        }
        setDirty();
    }

    /** Remplace l'épave (retire les barrières de l'ancienne, pose celles de la nouvelle) ; renvoie le nombre de barrières. */
    int epave(ServerLevel l, List<CineDecor.Piece> ep) {
        retirerBarrieres(l);
        etat = new CineDecor.Etat(etat().reveil(), etat().cap(), List.copyOf(ep), etat().monde());
        int n = 0;
        for (CineDecor.Piece p : ep) {
            for (BlockPos b : volume(p, 0.3)) {
                BlockState st = l.getBlockState(b);
                if (st.isAir() || st.canBeReplaced() && st.getFluidState().isEmpty() && !st.is(Blocks.BARRIER)) {
                    l.setBlock(b, Blocks.BARRIER.defaultBlockState(), 2);
                    barrieres.add(b.asLong());
                    n++;
                }
            }
        }
        setDirty();
        return n;
    }

    /** Retire les barrières posées par une commande et celles du générateur de la map dans le volume de l'épave actuelle. */
    private void retirerBarrieres(ServerLevel l) {
        Set<Long> tout = new LinkedHashSet<>(barrieres);
        for (CineDecor.Piece p : etat().epave()) {
            for (BlockPos b : volume(p, 1.0)) {
                tout.add(b.asLong());
            }
        }
        for (long k : tout) {
            BlockPos b = BlockPos.of(k);
            if (l.getBlockState(b).is(Blocks.BARRIER)) {
                l.setBlock(b, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        barrieres.clear();
        setDirty();
    }

    /** Blocs dont le centre tombe dans la caisse couchée (boîte du modèle tournée comme au rendu : lacet puis roulis),
     *  élargie de `marge`. Les bogies n'en ont pas. */
    static List<BlockPos> volume(CineDecor.Piece p, double marge) {
        List<BlockPos> out = new ArrayList<>();
        double[] d = p.modele().equals("loco") ? LOCO : p.modele().startsWith("voiture") || p.modele().equals("fourgon")
                || p.modele().equals("automotrice") ? VOITURE : null;
        if (d == null) {
            return out;
        }
        double ax = d[0] + marge, y0 = d[1] - marge, y1 = d[2] + marge, az = d[3] + marge;
        double r = Math.sqrt(ax * ax + y1 * y1 + az * az) + 1;
        double cl = Math.cos(Math.toRadians(p.lacet())), sl = Math.sin(Math.toRadians(p.lacet()));
        double cr = Math.cos(Math.toRadians(p.roulis())), sr = Math.sin(Math.toRadians(p.roulis()));
        for (int x = Mth.floor(p.x() - r); x <= Mth.floor(p.x() + r); x++) {
            for (int z = Mth.floor(p.z() - r); z <= Mth.floor(p.z() + r); z++) {
                for (int y = Mth.floor(p.y() - r); y <= Mth.floor(p.y() + r); y++) {
                    double wx = x + 0.5 - p.x(), wy = y + 0.5 - p.y(), wz = z + 0.5 - p.z();
                    // monde -> modèle : inverse de rotateY(-lacet).rotateX(roulis)
                    double lx = wx * cl + wz * sl, mz = -wx * sl + wz * cl;
                    double ly = wy * cr + mz * sr, lz = -wy * sr + mz * cr;
                    if (Math.abs(lx) <= ax && ly >= y0 && ly <= y1 && Math.abs(lz) <= az) {
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- dispositions
    /** L'épave du refuge reportée autour d'un autre point de réveil : même place derrière le joueur, même angle. */
    static List<CineDecor.Piece> derriere(ServerLevel l, Vec3 reveil, float cap) {
        CineDecor.Etat d = CineDecor.defaut();
        double da = Math.toRadians(cap - d.cap());
        double c = Math.cos(da), s = Math.sin(da);
        List<CineDecor.Piece> out = new ArrayList<>();
        for (CineDecor.Piece p : d.epave()) {
            double dx = p.x() - d.reveil().x, dz = p.z() - d.reveil().z;
            double x = reveil.x + dx * c - dz * s, z = reveil.z + dx * s + dz * c;
            out.add(new CineDecor.Piece(p.modele(), x, sol(l, x, reveil.y, z) + (p.y() - d.reveil().y), z, (float) (p.lacet() + cap - d.cap()), p.roulis()));
        }
        return out;
    }

    /** L'épave devant soi : la locomotive couchée à `dist` blocs, le nez vers soi, la voiture derrière elle. */
    static List<CineDecor.Piece> devant(ServerLevel l, Vec3 pos, float cap, double dist) {
        CineDecor.Etat d = CineDecor.defaut();
        CineDecor.Piece loco = d.epave().stream().filter(p -> p.modele().equals("loco")).findFirst().orElse(null);
        if (loco == null) {
            return List.of();
        }
        float yaw = cap * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double lx = pos.x + fx * (dist + LOCO[0]), lz = pos.z + fz * (dist + LOCO[0]);
        // repère de la rame du jar : avance vers +x tourné de loco.lacet() ; ici elle avance vers le joueur
        double cible = Math.toDegrees(Math.atan2(-fz, -fx));
        double da = Math.toRadians(cible), c = Math.cos(da), s = Math.sin(da);
        List<CineDecor.Piece> out = new ArrayList<>();
        for (CineDecor.Piece p : d.epave()) {
            double dx = p.x() - loco.x(), dz = p.z() - loco.z();
            double x = lx + dx * c - dz * s, z = lz + dx * s + dz * c;
            out.add(new CineDecor.Piece(p.modele(), x, sol(l, x, pos.y, z) + (p.y() - d.reveil().y), z, (float) (p.lacet() + cible), p.roulis()));
        }
        return out;
    }

    /** Dessus du sol sous (x, z) autour de la hauteur y (on cherche de y + 6 à y - 24 ; grottes comprises). */
    static double sol(ServerLevel l, double x, double y, double z) {
        BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int k = Mth.floor(y) + 6; k > Mth.floor(y) - 24; k--) {
            b.set(bx, k, bz);
            BlockState st = l.getBlockState(b);
            if (st.is(Blocks.BARRIER)) {
                continue;
            }
            var forme = st.getCollisionShape(l, b);
            if (!forme.isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()) {
                return k + forme.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return y;
    }

    /** Le joueur tient-il debout au point de réveil (place libre, sol dessous) ? Charge le chunk au besoin. */
    static boolean libre(ServerLevel l, Vec3 p) {
        // monde du spawn pas encore déposé : rien à charger (sinon le jeu y génèrerait du vide, et le dossier
        // dimensions/heralisz/spawn/ existerait avant le dépôt du vrai monde)
        if (l.dimension().location().equals(CineDecor.SPAWN)
                && !fr.heralisz.utils.spawn.DepartServeur.regionPresente(l, net.minecraft.util.Mth.floor(p.x), net.minecraft.util.Mth.floor(p.z))) {
            return false;
        }
        l.getChunk(BlockPos.containing(p));
        AABB corps = EntityType.PLAYER.getDimensions().makeBoundingBox(p);
        return l.noCollision(corps) && !l.noCollision(new AABB(p.x - 0.25, p.y - 0.6, p.z - 0.25, p.x + 0.25, p.y - 0.01, p.z + 0.25));
    }
}
