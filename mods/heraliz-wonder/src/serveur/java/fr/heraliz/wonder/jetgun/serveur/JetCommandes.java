package fr.heraliz.wonder.jetgun.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.jetgun.JetGun;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;

/**
 * {@code /jetgun} (op 2, banc d'essai) : réponses par message direct au joueur.
 * <ul>
 *   <li>{@code horde <n> [distance] [pv] [marche]} : n zombies devant soi (husks de jour) ; figés par défaut (ils
 *   reprennent vie quand ils sont aspirés), ou qui marchent ({@code marche} vrai) ;</li>
 *   <li>{@code chaleur <0..100> [libre]} : chaleur du Jet Gun tenu, tenue à cette valeur pour les captures (sauf
 *   {@code libre}) ; {@code surchauffe} : chaleur pleine et verrou ;</li>
 *   <li>{@code pap <0..3>} : niveau de Pack-a-Punch (marqueurs et camo du mode zombies) ;</li>
 *   <li>{@code perf} : coût serveur de l'aspiration depuis le dernier relevé ;</li>
 *   <li>{@code stress <secondes>} : 4 tireurs virtuels aspirent sans arrêt une horde de 40 qui se reforme.</li>
 * </ul>
 */
public final class JetCommandes {
    private JetCommandes() {
    }

    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("jetgun").requires(s -> s.hasPermission(2))
                .then(Commands.literal("horde").then(Commands.argument("n", IntegerArgumentType.integer(1, 120))
                        .executes(c -> horde(c, 10, 30, false))
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(2, 64))
                                .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), 30, false))
                                .then(Commands.argument("pv", DoubleArgumentType.doubleArg(1, 100000))
                                        .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), DoubleArgumentType.getDouble(c, "pv"), false))
                                        .then(Commands.argument("marche", BoolArgumentType.bool())
                                                .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), DoubleArgumentType.getDouble(c, "pv"),
                                                        BoolArgumentType.getBool(c, "marche"))))))))
                .then(Commands.literal("chaleur").then(Commands.argument("pourcent", IntegerArgumentType.integer(0, 100)).executes(c -> chaleur(c, true))
                        .then(Commands.literal("libre").executes(c -> chaleur(c, false)))))
                .then(Commands.literal("surchauffe").executes(JetCommandes::surchauffe))
                .then(Commands.literal("pap").then(Commands.argument("niveau", IntegerArgumentType.integer(0, 3)).executes(JetCommandes::pap)))
                .then(Commands.literal("perf").executes(c -> {
                    String r = Aspiration.resume(true);
                    HeralizWonder.LOG.info("[wonder] perf {}", r);
                    dire(c, r);
                    return 1;
                }))
                .then(Commands.literal("stress").then(Commands.argument("secondes", IntegerArgumentType.integer(1, 300)).executes(JetCommandes::stress))));
    }

    private static void dire(CommandContext<CommandSourceStack> c, String s) {
        if (c.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.AQUA), true);
        else HeralizWonder.LOG.info("[wonder] {}", s);
    }

    private static ItemStack jetgun(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = p.getMainHandItem();
        return JetGun.estJetGun(s) ? s : ItemStack.EMPTY;
    }

    /** Banc : chaleur tenue à une valeur (captures de la jauge et de l'incandescence), jusqu'à « chaleur 0 » ou « libre ». */
    private static ServerPlayer gelJoueur;
    private static float gelValeur;

    private static int chaleur(CommandContext<CommandSourceStack> c, boolean gel) throws CommandSyntaxException {
        ItemStack s = jetgun(c);
        if (s.isEmpty()) {
            dire(c, "Pas de Jet Gun en main.");
            return 0;
        }
        int v = IntegerArgumentType.getInteger(c, "pourcent");
        CompoundTag t = s.getOrCreateTag();
        t.putFloat(JetGun.NBT_CHALEUR, v * JetGun.CHALEUR_MAX / 100f);
        t.putBoolean(JetGun.NBT_VERROU, false);
        gelJoueur = gel && v > 0 ? c.getSource().getPlayerOrException() : null;
        gelValeur = v * JetGun.CHALEUR_MAX / 100f;
        dire(c, "Jet Gun : chaleur " + v + " %" + (gelJoueur != null ? " (tenue)" : ""));
        return 1;
    }

    private static void gel() {
        ServerPlayer p = gelJoueur;
        if (p == null) return;
        if (p.isRemoved() || !JetGun.estJetGun(p.getMainHandItem())) {
            gelJoueur = null;
            return;
        }
        CompoundTag t = p.getMainHandItem().getOrCreateTag();
        if (t.getFloat(JetGun.NBT_CHALEUR) != gelValeur) t.putFloat(JetGun.NBT_CHALEUR, gelValeur);
    }

    private static int surchauffe(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ItemStack s = jetgun(c);
        if (s.isEmpty()) {
            dire(c, "Pas de Jet Gun en main.");
            return 0;
        }
        CompoundTag t = s.getOrCreateTag();
        t.putFloat(JetGun.NBT_CHALEUR, JetGun.CHALEUR_MAX);
        t.putBoolean(JetGun.NBT_VERROU, true);
        dire(c, "Jet Gun : surchauffe");
        return 1;
    }

    private static int pap(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ItemStack s = jetgun(c);
        if (s.isEmpty()) {
            dire(c, "Pas de Jet Gun en main.");
            return 0;
        }
        int n = IntegerArgumentType.getInteger(c, "niveau");
        CompoundTag t = s.getOrCreateTag();
        if (n == 0) {
            t.remove("HeraliszZbPap");
            t.remove("HeraliszZbPapNiveau");
            if (t.getString("HeraliszCamo").startsWith("pap_")) t.remove("HeraliszCamo");
        } else {
            t.putBoolean("HeraliszZbPap", true);
            t.putInt("HeraliszZbPapNiveau", n);
            t.putString("HeraliszCamo", "pap_" + n);
        }
        dire(c, "Jet Gun : Pack-a-Punch niveau " + n + (n > 0 ? " (surchauffe reduite)" : ""));
        return 1;
    }

    private static int horde(CommandContext<CommandSourceStack> c, double distance, double pv, boolean marche) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "n");
        int poses = poserHorde(p, n, distance, pv, marche);
        HeralizWonder.LOG.info("[wonder] banc : horde de {} zombies a {} blocs ({} PV, {})", poses, distance, pv, marche ? "qui marchent" : "figes");
        dire(c, poses + " zombies poses.");
        return poses;
    }

    /** n zombies en grille lâche centrée à {@code distance} devant le joueur, tournés vers lui. */
    static int poserHorde(ServerPlayer p, int n, double distance, double pv, boolean marche) {
        ServerLevel niveau = p.serverLevel();
        Vec3 regard = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 droite = new Vec3(-regard.z, 0, regard.x);
        Vec3 centre = p.position().add(regard.scale(distance));
        int cote = Mth.ceil(Math.sqrt(n));
        if (cote % 2 == 0) cote++;
        RandomSource r = p.getRandom();
        boolean jour = niveau.isDay();
        int poses = 0;
        for (int i = 0; i < n; i++) {
            Zombie z = (jour ? EntityType.HUSK : EntityType.ZOMBIE).create(niveau);
            if (z == null) break;
            double gx = (i % cote - (cote - 1) / 2.0) * 1.9 + (r.nextDouble() - 0.5) * 0.6;
            double gz = (i / cote - (cote - 1) / 2.0) * 1.9 + (r.nextDouble() - 0.5) * 0.6;
            Vec3 pos = centre.add(droite.scale(gx)).add(regard.scale(gz));
            int y = niveau.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
            z.moveTo(pos.x, y, pos.z, p.getYRot() + 180f, 0f);
            z.setYHeadRot(p.getYRot() + 180f);
            z.setYBodyRot(p.getYRot() + 180f);
            z.setNoAi(!marche);
            z.setSilent(true);
            z.setPersistenceRequired();
            if (z.getAttribute(Attributes.MAX_HEALTH) != null) z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(pv);
            z.setHealth((float) pv);
            z.getPersistentData().putBoolean(Aspiration.TAG_BANC, true);
            niveau.addFreshEntity(z);
            poses++;
        }
        return poses;
    }

    // ------------------------------------------------------------------ stress : 4 tireurs virtuels
    private static ServerPlayer stressJoueur;
    private static int stressTicks, stressTick;

    private static int stress(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        stressJoueur = c.getSource().getPlayerOrException();
        stressTicks = IntegerArgumentType.getInteger(c, "secondes") * 20;
        stressTick = 0;
        Aspiration.resume(true);
        HeralizWonder.LOG.info("[wonder] stress jet gun : 4 tireurs virtuels pendant {} s", stressTicks / 20);
        dire(c, "Stress Jet Gun : 4 tireurs virtuels pendant " + stressTicks / 20 + " s.");
        return 1;
    }

    public static void stressTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) gel();
        if (e.phase != TickEvent.Phase.START || stressJoueur == null) return;
        ServerPlayer p = stressJoueur;
        if (stressTick >= stressTicks || p.isRemoved()) {
            String r = Aspiration.resume(true);
            HeralizWonder.LOG.info("[wonder] stress jet gun termine : {}", r);
            p.sendSystemMessage(Component.literal("Stress Jet Gun termine : " + r).withStyle(ChatFormatting.AQUA));
            stressJoueur = null;
            return;
        }
        int t = stressTick++;
        ServerLevel niveau = p.serverLevel();
        if (t % 10 == 0) {
            List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(40),
                    x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive());
            if (l.size() < 40) poserHorde(p, 40 - l.size(), 13, 60, true);
        }
        Vec3 regard = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 droite = new Vec3(-regard.z, 0, regard.x);
        for (int k = 0; k < 4; k++) {
            // les 4 tireurs se partagent la horde (en éventail), tir continu (comme 4 joueurs qui tiennent la détente)
            Vec3 o = p.getEyePosition().add(droite.scale((k - 1.5) * 1.6)).add(0, -0.3, 0);
            Vec3 d = regard.add(droite.scale((k - 1.5) * 0.18)).normalize();
            Aspiration.impulsionVirtuelle(niveau, p, 1000 + k, o.x, o.y, o.z, d.x, d.y - 0.05, d.z, k % 2);
        }
    }
}
