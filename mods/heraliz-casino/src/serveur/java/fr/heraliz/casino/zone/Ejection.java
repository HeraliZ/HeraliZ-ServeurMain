package fr.heraliz.casino.zone;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.entity.EscortEntity;
import fr.heraliz.casino.entity.GuardEntity;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ZoneCutscenePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Expulsion d'un joueur par deux videurs (serveur), une douzaine de secondes :
 * <ol>
 * <li>les videurs arrivent en courant depuis l'entree (le joueur ne peut plus bouger) ;</li>
 * <li>ils l'attrapent, un de chaque cote ;</li>
 * <li>ils le poussent jusqu'a la porte, bourrade apres bourrade, en contournant les machines ;</li>
 * <li>ils prennent leur elan et le jettent dehors : il atterrit a plat ventre quelques blocs plus loin ;</li>
 * <li>ils s'epoussettent les mains, lachent une derniere replique et rentrent.</li>
 * </ol>
 * Le joueur "monte" une entite invisible que l'expulsion deplace : son trajet est le meme pour tout le monde.
 */
final class Ejection {
    /** Motifs : plus un jeton ; sorti par un operateur. */
    static final int BROKE = 0, STAFF = 1;
    /** Durees fixes (ticks) : prise du joueur, elan, vol plane, scene de fin. */
    private static final int GRAB = 24, WINDUP = 12, FLIGHT = 14, AFTER = 66;

    private final CasinoZone zone;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final UUID id;
    private final int reason;
    private final CasinoZone.Entrance door;
    /** Trajet du joueur : de la ou il est jusqu'a l'interieur de la porte ; longueurs cumulees. */
    private final List<Vec3> path;
    private final double[] cum;
    private final double length;
    private final Vec3 inside, land;
    private final EscortEntity escort;
    private final GuardEntity[] guards = new GuardEntity[2];
    private final int tArrive, tMarch;
    private final double arriveFrom;
    private final List<BlockPos> doors = new ArrayList<>();
    private int t;
    private boolean done, landed;

    private Ejection(CasinoZone zone, ServerLevel level, ServerPlayer player, int reason, CasinoZone.Entrance door, List<Vec3> path, Vec3 inside, Vec3 land) {
        this.zone = zone;
        this.level = level;
        this.player = player;
        this.id = player.getUUID();
        this.reason = reason;
        this.door = door;
        this.path = path;
        this.inside = inside;
        this.land = land;
        cum = new double[path.size()];
        for (int i = 1; i < path.size(); i++) cum[i] = cum[i - 1] + path.get(i).distanceTo(path.get(i - 1));
        length = cum[cum.length - 1];
        // les videurs courent (6,8 blocs/s au plus) : de trop loin, ils surgissent en cours de route
        tArrive = Mth.clamp((int) Math.round(length / 6.5 * 20), 40, 70);
        arriveFrom = Math.min(length, GAP + 0.34 * tArrive);
        tMarch = Mth.clamp((int) Math.round(length / 3.0 * 20), 80, 140);
        Vec3 d0 = dir(arriveFrom);
        float toPlayer = ZoneManager.yaw(-d0.x, -d0.z);
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? -1 : 1;
            guards[k] = fr.heraliz.casino.entity.GuardEntityServeur.temporary(level, side(at(arriveFrom), d0, side, 0.42, 0), toPlayer, side);
            guards[k].stance(GuardEntity.RUN);
        }
        escort = EscortEntity.create(level, path.get(0), ZoneManager.yaw(d0.x, d0.z));
    }

    /** Distance a laquelle les videurs s'arretent devant le joueur. */
    private static final double GAP = 1.0;

    UUID player() {
        return id;
    }

    /** Prepare l'expulsion (trajet, videurs, monture) ; null si le joueur ne peut pas etre pris en charge. */
    @Nullable
    static Ejection begin(CasinoZone zone, ServerLevel level, ServerPlayer player, int reason) {
        CasinoZone.Entrance door = zone.nearest(player.position());
        if (door == null) door = zone.facade(player.position());
        // il quitte sa machine (tabouret, place debout)
        if (player.isPassenger()) {
            MachineBlockEntity.allowDismount = true;
            try {
                player.stopRiding();
            } finally {
                MachineBlockEntity.allowDismount = false;
            }
            if (player.isPassenger()) return null;
        }
        Vec3 from = ZoneManager.ground(level, player.position());
        if (from == null) from = player.position();
        Vec3 inside = ZoneManager.ground(level, door.at(-1.0));
        if (inside == null) inside = door.at(-1.0);
        // point de chute : le plus loin possible devant la porte, la ou l'on peut tenir debout ; a defaut, le seuil
        Vec3 land = null;
        for (double d : new double[]{4.2, 3.2, 2.4, 1.6, 1.0, 0.4}) {
            land = ZoneManager.ground(level, door.at(d));
            if (land != null) break;
        }
        if (land == null) land = door.at(0.4);
        List<Vec3> path = route(level, inside, from);
        Ejection e = new Ejection(zone, level, player, reason, door, path, inside, land);
        if (!player.startRiding(e.escort, true)) {
            e.cleanup();
            return null;
        }
        player.setYRot(e.escort.face());
        player.setYHeadRot(e.escort.face());
        CasinoNet.sendTo(player, new ZoneCutscenePacket(e.escort.getId(), e.guards[0].getId(), e.guards[1].getId(), reason,
                door.door().x, door.door().y, door.door().z, door.nx(), door.nz()));
        Component msg = Component.translatable(reason == BROKE ? "message.heralizcasino.zone.ejected" : "message.heralizcasino.zone.ejected_staff",
                player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer o : level.players()) {
            if (o != player && o.distanceToSqr(player) < 64 * 64) o.sendSystemMessage(msg);
        }
        e.sound(path.get(0), SoundEvents.BELL_BLOCK, 0.9f, 0.55f);
        return e;
    }

    /**
     * Trajet a pied de la porte au joueur (recherche de chemin d'un videur : il contourne machines et murs), rendu dans
     * le sens de la marche du joueur. Sans chemin praticable : la ligne droite.
     */
    private static List<Vec3> route(ServerLevel level, Vec3 inside, Vec3 from) {
        List<Vec3> pts = new ArrayList<>();
        // eclaireur jamais ajoute au monde : il ne sert qu'a chercher le chemin
        GuardEntity scout = new fr.heraliz.casino.entity.GuardEntityServeur(fr.heraliz.casino.registry.ModRegistry.GUARD.get(), level);
        try {
            scout.setPos(inside.x, inside.y, inside.z);
            scout.setOnGround(true);
            // les portes interieures de la salle ne l'arretent pas
            if (scout.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) ground.setCanOpenDoors(true);
            Path nav = scout.getNavigation().createPath(BlockPos.containing(from), 1);
            if (nav != null && nav.getNodeCount() >= 2) {
                for (int i = nav.getNodeCount() - 1; i >= 0; i--) {
                    Node n = nav.getNode(i);
                    pts.add(new Vec3(n.x + 0.5, n.y, n.z + 0.5));
                }
                // chemin incomplet (le joueur est inaccessible a pied) : on ne s'en sert pas
                if (pts.get(0).distanceTo(from) > 2.5 || pts.get(pts.size() - 1).distanceTo(inside) > 2.5) pts.clear();
            }
        } catch (RuntimeException ignored) {
            pts.clear();
        }
        if (pts.isEmpty()) {
            pts.add(from);
            pts.add(inside);
            return pts;
        }
        pts.set(0, from);
        pts.set(pts.size() - 1, inside);
        if (pts.size() < 2) pts.add(inside);
        return smooth(pts);
    }

    /** Arrondit les angles du trajet (les cases de la recherche de chemin font des zigzags a 45 et 90 degres). */
    private static List<Vec3> smooth(List<Vec3> pts) {
        for (int pass = 0; pass < 2 && pts.size() > 2; pass++) {
            List<Vec3> out = new ArrayList<>();
            out.add(pts.get(0));
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                if (i > 0) out.add(a.lerp(b, 0.25));
                if (i < pts.size() - 2) out.add(a.lerp(b, 0.75));
            }
            out.add(pts.get(pts.size() - 1));
            pts = out;
        }
        return pts;
    }

    // =================================================================== geometrie du trajet
    /** Point du trajet a la distance s du depart. */
    private Vec3 at(double s) {
        s = Mth.clamp(s, 0, length);
        for (int i = 1; i < cum.length; i++) {
            if (s <= cum[i] || i == cum.length - 1) {
                double seg = cum[i] - cum[i - 1];
                return seg < 1e-6 ? path.get(i) : path.get(i - 1).lerp(path.get(i), (s - cum[i - 1]) / seg);
            }
        }
        return path.get(path.size() - 1);
    }

    /** Direction de la marche (horizontale, unitaire) a la distance s. */
    private Vec3 dir(double s) {
        Vec3 a = at(Math.max(0, s - 1.0)), b = at(Math.min(length, s + 1.0));
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        if (d.lengthSqr() < 1e-6) d = new Vec3(inside.x - path.get(0).x, 0, inside.z - path.get(0).z);
        if (d.lengthSqr() < 1e-6) d = door.normal();
        return d.normalize();
    }

    /** Point a cote de "center" : "lateral" blocs sur le cote (side = 1 : a droite), "back" blocs en arriere. */
    private static Vec3 side(Vec3 center, Vec3 dir, int side, double lateral, double back) {
        return new Vec3(center.x - dir.z * side * lateral - dir.x * back, center.y, center.z + dir.x * side * lateral - dir.z * back);
    }

    /** Place d'un videur autour du joueur : sur le cote s'il y a la place, sinon derriere (couloir, porte). */
    private Vec3 flank(Vec3 center, Vec3 dir, int side) {
        Vec3 a = side(center, dir, side, 0.74, 0.34);
        if (ZoneManager.free(level, a)) return a;
        Vec3 b = side(center, dir, side, 0.36, 0.9);
        if (ZoneManager.free(level, b)) return b;
        return side(center, dir, side, 0.1, side > 0 ? 0.95 : 1.75);
    }

    private void move(GuardEntity g, Vec3 target, float yaw, double smooth) {
        Vec3 p = g.position();
        g.place(smooth >= 1 ? target : p.lerp(target, smooth), Mth.rotLerp(0.45f, g.getYRot(), yaw));
    }

    private void sound(Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y + 1, at.z, s, SoundSource.NEUTRAL, volume, pitch);
    }

    private Component line(String key) {
        return Component.translatable("guard.heralizcasino.eject." + key);
    }

    // =================================================================== deroulement
    /** @return false quand l'expulsion est terminee (ou interrompue) */
    boolean tick() {
        if (done) return false;
        if (player.hasDisconnected() || !player.isAlive() || player.isRemoved() || player.level() != level) {
            abort();
            return false;
        }
        int a = tArrive, b = a + GRAB, c = b + tMarch, d = c + WINDUP, e = d + FLIGHT, f = e + AFTER;
        if (!landed && player.getVehicle() != escort) {
            if (escort.isRemoved() || !player.startRiding(escort, true)) {
                abort();
                return false;
            }
        }
        Vec3 start = path.get(0);
        if (t < a) {
            arrive(a, start);
        } else if (t < b) {
            grab(t - a, start);
        } else if (t < c) {
            march(t - b);
        } else if (t < d) {
            windup(t - c);
        } else if (t < e) {
            flight(t - d);
        } else if (t < f) {
            after(t - e);
        } else {
            finish();
            return false;
        }
        t++;
        return true;
    }

    /** Les videurs arrivent en courant ; le joueur, cloue sur place, se tourne vers eux. */
    private void arrive(int a, Vec3 start) {
        double u = 1 - Math.pow(1 - (t + 1) / (double) a, 1.5);
        double s = Mth.lerp(u, arriveFrom, GAP);
        Vec3 d = dir(s), c = at(s);
        float yaw = ZoneManager.yaw(-d.x, -d.z);
        for (GuardEntity g : guards) move(g, side(c, d, g.side(), 0.42, 0), yaw, 1);
        escort.phase(EscortEntity.WAIT);
        escort.face(ZoneManager.yaw(c.x - start.x, c.z - start.z));
        if (t == 5) guards[0].say(line(reason == BROKE ? "spot" : "spot_staff"));
        if (t % 5 == 0) sound(c, SoundEvents.IRON_GOLEM_STEP, 0.4f, 1.35f);
    }

    /** Ils l'attrapent : un de chaque cote, tournes vers lui, puis vers la sortie. */
    private void grab(int k, Vec3 start) {
        Vec3 d = dir(0);
        float out = ZoneManager.yaw(d.x, d.z);
        for (GuardEntity g : guards) {
            Vec3 spot = flank(start, d, g.side());
            float toPlayer = ZoneManager.yaw(start.x - g.getX(), start.z - g.getZ());
            move(g, spot, k < GRAB / 2 ? toPlayer : out, 0.35);
            g.stance(GuardEntity.PUSH);
        }
        escort.phase(EscortEntity.HELD);
        if (k >= GRAB / 3) escort.face(Mth.rotLerp(0.25f, escort.face(), out));
        if (k == 0) {
            sound(start, SoundEvents.ARMOR_EQUIP_LEATHER, 1f, 0.7f);
            sound(start, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.8f);
            guards[1].say(line("grab." + level.random.nextInt(2)));
        }
    }

    /** Pousse jusqu'a la porte : le joueur avance par a-coups, une bourrade toutes les 13 ticks. */
    private void march(int k) {
        double u = (k + 1) / (double) tMarch;
        double s = length * (u * u * (3 - 2 * u));
        int beat = k % 13;
        double lurch = beat < 6 ? Math.sin(Math.PI * beat / 6.0) : 0;
        Vec3 d = dir(s), c = at(Math.min(length, s + 0.1 * lurch));
        escort.phase(EscortEntity.MARCH);
        // il tourne en douceur avec le trajet
        escort.face(Mth.rotLerp(0.25f, escort.face(), ZoneManager.yaw(d.x, d.z)));
        escort.setPos(c.x, c.y + 0.02 * lurch, c.z);
        float yaw = ZoneManager.yaw(d.x, d.z);
        for (GuardEntity g : guards) {
            move(g, flank(at(s), d, g.side()), yaw, 0.5);
            g.stance(GuardEntity.PUSH);
        }
        if (beat == 0 && k > 0) {
            sound(c, SoundEvents.PLAYER_ATTACK_WEAK, 0.9f, 0.75f + level.random.nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.1, c.z, 3, 0.2, 0.02, 0.2, 0.01);
        }
        if (k % 6 == 3) sound(c, SoundEvents.IRON_GOLEM_STEP, 0.3f, 1.2f);
        if (k == tMarch * 2 / 5) guards[0].say(line("march." + level.random.nextInt(3)));
    }

    /** Devant la porte : ils le tirent en arriere pour prendre de l'elan. */
    private void windup(int k) {
        if (k == 0) {
            openDoors();
            sound(inside, SoundEvents.ARMOR_EQUIP_LEATHER, 0.9f, 0.6f);
        }
        Vec3 n = door.normal();
        double pull = 0.45 * Math.sin(Math.PI / 2 * Math.min(1.0, (k + 1) / (double) (WINDUP - 2)));
        Vec3 c = inside.subtract(n.scale(pull));
        float out = ZoneManager.yaw(n.x, n.z);
        escort.phase(EscortEntity.WINDUP);
        escort.face(out);
        escort.setPos(c.x, c.y, c.z);
        for (GuardEntity g : guards) {
            move(g, flank(c, n, g.side()), out, 0.5);
            g.stance(GuardEntity.WINDUP);
        }
    }

    /** Vol plane a travers la porte, puis la chute. */
    private void flight(int k) {
        Vec3 n = door.normal();
        if (k == 0) {
            for (GuardEntity g : guards) g.stance(GuardEntity.THROW);
            guards[0].say(line("throw"));
            sound(inside, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
            sound(inside, SoundEvents.IRON_GOLEM_ATTACK, 0.7f, 1.1f);
        }
        double u = (k + 1) / (double) FLIGHT;
        Vec3 c = inside.lerp(land, u);
        escort.phase(EscortEntity.THROWN);
        escort.face(ZoneManager.yaw(n.x, n.z));
        escort.setPos(c.x, c.y + 1.25 * 4 * u * (1 - u), c.z);
        if (k == FLIGHT - 1) land();
    }

    private void land() {
        landed = true;
        EscortEntity.allowDismount = true;
        try {
            player.stopRiding();
        } finally {
            EscortEntity.allowDismount = false;
        }
        escort.discard();
        Vec3 n = door.normal();
        player.teleportTo(level, land.x, land.y, land.z, ZoneManager.yaw(n.x, n.z), 35);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        // a plat ventre dans la poussiere
        player.setForcedPose(Pose.SWIMMING);
        sound(land, SoundEvents.PLAYER_BIG_FALL, 1f, 0.8f);
        level.sendParticles(ParticleTypes.POOF, land.x, land.y + 0.15, land.z, 14, 0.45, 0.08, 0.45, 0.03);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, land.x, land.y + 0.1, land.z, 3, 0.3, 0.02, 0.3, 0.005);
        ZoneManager.ejected(zone, level, player);
        player.displayClientMessage(Component.translatable(reason == BROKE ? "message.heralizcasino.zone.you_out" : "message.heralizcasino.zone.you_out_staff")
                .withStyle(ChatFormatting.RED), true);
    }

    /** Les videurs, sur le pas de la porte : mains epoussetees, derniere replique, puis ils rentrent. */
    private void after(int k) {
        Vec3 n = door.normal();
        float out = ZoneManager.yaw(n.x, n.z);
        if (k < 40) {
            for (GuardEntity g : guards) {
                move(g, side(door.at(-0.35), n, g.side(), 0.55, 0), out, 0.3);
                g.stance(k < 6 ? GuardEntity.THROW : GuardEntity.DUST);
            }
            if (k == 8) sound(inside, SoundEvents.ARMOR_EQUIP_LEATHER, 0.7f, 1.3f);
            if (k == 16) guards[1].say(line(reason == BROKE ? "bye." + level.random.nextInt(2) : "bye_staff"));
        } else {
            double back = 0.35 + (k - 40) * 0.11;
            for (GuardEntity g : guards) {
                move(g, side(door.at(-back), n, g.side(), 0.55, 0), out + 180, 0.5);
                g.stance(GuardEntity.IDLE);
            }
        }
        if (k == 38) player.setForcedPose(null);
    }

    private void finish() {
        done = true;
        player.setForcedPose(null);
        cleanup();
    }

    /** Interruption (deconnexion, mort, changement de monde) : tout est remis en ordre. */
    void abort() {
        if (done) return;
        done = true;
        if (!landed) {
            EscortEntity.allowDismount = true;
            try {
                if (player.getVehicle() == escort) player.stopRiding();
            } finally {
                EscortEntity.allowDismount = false;
            }
            // il n'echappe pas a la sortie en se deconnectant : sans ticket, il sera refoule a son retour
            zone.revoke(id);
        }
        player.setForcedPose(null);
        cleanup();
    }

    private void cleanup() {
        for (GuardEntity g : guards) if (g != null) g.discard();
        if (escort != null && !escort.isRemoved()) escort.discard();
        closeDoors();
    }

    // =================================================================== porte
    private void openDoors() {
        for (double d : new double[]{-0.5, 0.5}) {
            BlockPos p = BlockPos.containing(door.at(d).add(0, 0.2, 0));
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DoorBlock db && !st.getValue(DoorBlock.OPEN)) {
                db.setOpen(null, level, st, p, true);
                doors.add(p);
            } else if (st.getBlock() instanceof FenceGateBlock && !st.getValue(FenceGateBlock.OPEN)) {
                level.setBlock(p, st.setValue(FenceGateBlock.OPEN, true), 10);
                sound(Vec3.atCenterOf(p), SoundEvents.FENCE_GATE_OPEN, 1f, 1f);
                doors.add(p);
            }
        }
    }

    private void closeDoors() {
        for (BlockPos p : doors) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DoorBlock db && st.getValue(DoorBlock.OPEN)) {
                db.setOpen(null, level, st, p, false);
            } else if (st.getBlock() instanceof FenceGateBlock && st.getValue(FenceGateBlock.OPEN)) {
                level.setBlock(p, st.setValue(FenceGateBlock.OPEN, false), 10);
            }
        }
        doors.clear();
    }
}
