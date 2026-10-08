package fr.heralisz.matchs.moteur.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Point nomme d'une carte : position (centre du bloc si les coordonnees du JSON sont entieres, voir {@link #lire}) et
 * orientation horizontale (yaw en degres, convention Minecraft : 0 = sud, 90 = ouest, 180 = nord, -90 = est).
 * <p>Dans {@code cartes/<carte>.json} les poses sont <b>relatives</b> a l'origine de la structure ({@code [x, y, z, yaw]},
 * yaw facultatif) ; {@link Instance#points(String)} les rend <b>absolues</b> (origine de l'instance ajoutee).
 * Immuable, sans allocation a la lecture.
 */
public record Pose(double x, double y, double z, float yaw) {
    /** Pose a l'origine, regard au sud. */
    public static final Pose ZERO = new Pose(0, 0, 0, 0);

    /** Position sous forme de vecteur (nouvelle instance). */
    public Vec3 position() {
        return new Vec3(x, y, z);
    }

    /** Bloc contenant la pose (pieds du joueur). */
    public BlockPos bloc() {
        return BlockPos.containing(x, y, z);
    }

    /** Copie decalee de l'origine donnee (relatif -> absolu). */
    public Pose decaler(BlockPos origine) {
        return new Pose(x + origine.getX(), y + origine.getY(), z + origine.getZ(), yaw);
    }

    /** Distance au carre jusqu'a un point (comparaisons sans racine). */
    public double distanceCarre(double px, double py, double pz) {
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Teleporte le joueur sur cette pose (absolue) dans ce niveau, regard horizontal. */
    public void teleporter(ServerPlayer joueur, ServerLevel niveau) {
        joueur.teleportTo(niveau, x, y, z, yaw, 0f);
    }

    /**
     * Lit {@code [x, y, z]} ou {@code [x, y, z, yaw]}. Des coordonnees x et z entieres designent un bloc : la pose est
     * placee au centre du bloc (+0,5) ; des decimales sont gardees telles quelles. Rend null si le tableau est invalide.
     */
    public static Pose lire(JsonElement e) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        if (a.size() < 3) return null;
        try {
            double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble(), z = a.get(2).getAsDouble();
            float yaw = a.size() > 3 ? a.get(3).getAsFloat() : 0f;
            if (x == Math.floor(x)) x += 0.5;
            if (z == Math.floor(z)) z += 0.5;
            return new Pose(x, y, z, yaw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
    }

    public static Pose read(FriendlyByteBuf buf) {
        return new Pose(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    /**
     * Rotation d'une annexe : none, clockwise_90, clockwise_180, counterclockwise_90 (noms de StructureTemplate). Corps
     * d'origine de moteur/arenes/Structure.rotation (serveur), qui la reprend.
     */
    public static net.minecraft.world.level.block.Rotation rotationAnnexe(String nom) {
        String n = switch (nom.toLowerCase(java.util.Locale.ROOT)) {
            case "90" -> "clockwise_90";
            case "180" -> "clockwise_180";
            case "270", "-90" -> "counterclockwise_90";
            default -> nom;
        };
        // par le nom de la constante (aucune reference directe aux constantes de l'enum dans le jar reobfusque)
        for (net.minecraft.world.level.block.Rotation r : net.minecraft.world.level.block.Rotation.values()) {
            if (String.valueOf(r).equalsIgnoreCase(n)) return r;
        }
        return net.minecraft.world.level.block.Rotation.NONE;
    }
}
