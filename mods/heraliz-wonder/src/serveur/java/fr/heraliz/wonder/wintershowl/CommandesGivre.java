package fr.heraliz.wonder.wintershowl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.serveur.Perf;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * {@code /wonder givre ...} (op 2, banc d'essai ; se greffe sous {@code /wonder} sans toucher {@code Commandes}) :
 * <ul>
 *   <li>{@code pap <0..3>} : niveau de Pack-a-Punch du Winter's Howl tenu (marqueurs et camo {@code pap_<n>} du mode
 *   zombies, grand chargeur de TaCZ : 24 coups et lueurs du Winter's Fury, comme la vraie machine) ;</li>
 *   <li>{@code horde <n> [distance] [pv] [ia]} : n zombies en groupe devant soi (de jour des husks) ; {@code ia} = 1 pour
 *   qu'ils marchent vers le joueur (ralentissement visible) ;</li>
 *   <li>{@code etat} : creatures suivies ; {@code perf} : cout serveur ;</li>
 *   <li>{@code stress <secondes>} : 4 tireurs virtuels qui tirent sans arret au Winter's Howl sur une horde de 40 qui se
 *   reforme (vrais calculs, vrais paquets), puis le bilan.</li>
 * </ul>
 */
public final class CommandesGivre {
    private CommandesGivre() {
    }

    private static final String NBT_CAMO = "HeraliszCamo";

    static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("wonder").requires(s -> s.hasPermission(2)).then(Commands.literal("givre")
                .then(Commands.literal("pap").then(Commands.argument("niveau", IntegerArgumentType.integer(0, Armes.PAP_MAX)).executes(CommandesGivre::pap)))
                .then(Commands.literal("horde").then(Commands.argument("n", IntegerArgumentType.integer(1, 120))
                        .executes(c -> horde(c, 10, 30, false))
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(2, 64))
                                .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), 30, false))
                                .then(Commands.argument("pv", DoubleArgumentType.doubleArg(1, 100000))
                                        .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), DoubleArgumentType.getDouble(c, "pv"), false))
                                        .then(Commands.argument("ia", IntegerArgumentType.integer(0, 1))
                                                .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), DoubleArgumentType.getDouble(c, "pv"),
                                                        IntegerArgumentType.getInteger(c, "ia") == 1)))))))
                .then(Commands.literal("etat").executes(c -> {
                    MutableComponent etat = Givre.etat();
                    HeralizWonder.LOG.info("[wonder] givre : {}", Perf.journal(etat));
                    dire(c, etat);
                    return 1;
                }))
                .then(Commands.literal("perf").executes(c -> {
                    MutableComponent r = PerfGivre.resume(true);
                    HeralizWonder.LOG.info("[wonder] perf givre {}", Perf.journal(r));
                    dire(c, r);
                    return 1;
                }))
                .then(Commands.literal("stress").then(Commands.argument("secondes", IntegerArgumentType.integer(1, 300)).executes(CommandesGivre::stress)))));
    }

    private static void dire(CommandContext<CommandSourceStack> c, MutableComponent s) {
        if (c.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(s.withStyle(ChatFormatting.AQUA), true);
        else HeralizWonder.LOG.info("[wonder] {}", Perf.journal(s));
    }

    private static int pap(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = p.getMainHandItem();
        if (!WintersHowl.est(s)) {
            dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pas_winters_howl", "Pas de Winter's Howl en main."));
            return 0;
        }
        int n = IntegerArgumentType.getInteger(c, "niveau");
        CompoundTag t = s.getOrCreateTag();
        if (n == 0) {
            t.remove(Armes.NBT_PAP);
            t.remove(Armes.NBT_PAP_NIVEAU);
            if (t.getString(NBT_CAMO).startsWith("pap_")) t.remove(NBT_CAMO);
            s.resetHoverName();
        } else {
            t.putBoolean(Armes.NBT_PAP, true);
            t.putInt(Armes.NBT_PAP_NIVEAU, n);
            t.putString(NBT_CAMO, "pap_" + n);
        }
        if (ModList.get().isLoaded("tacz")) fr.heraliz.wonder.serveur.TaczServeur.chargeurPap(s, n > 0);
        p.getInventory().setChanged();
        HeralizWonder.LOG.info("[wonder] banc : Winter's Howl au Pack-a-Punch niveau {}", n);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pap", "%s : Pack-a-Punch niveau %s", "Winter's Howl", String.valueOf(n)));
        return 1;
    }

    private static int horde(CommandContext<CommandSourceStack> c, double distance, double pv, boolean ia) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "n");
        int poses = poserHorde(p, n, distance, pv, ia);
        HeralizWonder.LOG.info("[wonder] banc givre : horde de {} a {} blocs ({} PV, ia {})", poses, distance, pv, ia);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.zombies_poses", "%s zombies posés.", String.valueOf(poses)));
        return poses;
    }

    static int poserHorde(ServerPlayer p, int n, double distance, double pv, boolean ia) {
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
            double gx = (i % cote - (cote - 1) / 2.0) * 1.9 + (r.nextDouble() - 0.5) * 0.5;
            double gz = (i / cote - (cote - 1) / 2.0) * 1.9 + (r.nextDouble() - 0.5) * 0.5;
            Vec3 pos = centre.add(droite.scale(gx)).add(regard.scale(gz));
            int y = niveau.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
            z.moveTo(pos.x, y, pos.z, p.getYRot() + 180f, 0f);
            z.setYHeadRot(p.getYRot() + 180f);
            z.setYBodyRot(p.getYRot() + 180f);
            z.setNoAi(!ia);
            z.setSilent(true);
            z.setPersistenceRequired();
            if (z.getAttribute(Attributes.MAX_HEALTH) != null) z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(pv);
            z.setHealth((float) pv);
            if (ia) z.setTarget(p);
            z.getPersistentData().putBoolean("heraliz_wonder:banc", true);
            niveau.addFreshEntity(z);
            poses++;
        }
        return poses;
    }

    // ------------------------------------------------------------------ stress : 4 tireurs virtuels au Winter's Howl
    private static ServerPlayer stressJoueur;
    private static int stressTicks, stressTick;

    private static int stress(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        stressJoueur = c.getSource().getPlayerOrException();
        stressTicks = IntegerArgumentType.getInteger(c, "secondes") * 20;
        stressTick = 0;
        PerfGivre.resume(true);
        HeralizWonder.LOG.info("[wonder] stress givre : 4 tireurs virtuels pendant {} s", stressTicks / 20);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.stress_givre", "Stress givre : 4 tireurs virtuels pendant %s s.",
                String.valueOf(stressTicks / 20)));
        return 1;
    }

    static void stressTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || stressJoueur == null) return;
        ServerPlayer p = stressJoueur;
        if (stressTick >= stressTicks || p.isRemoved()) {
            MutableComponent r = PerfGivre.resume(true);
            HeralizWonder.LOG.info("[wonder] stress givre termine : {}", Perf.journal(r));
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_wonder.banc.stress_givre_fin", "Stress givre terminé : %s", r)
                    .withStyle(ChatFormatting.AQUA));
            stressJoueur = null;
            return;
        }
        int t = stressTick++;
        ServerLevel niveau = p.serverLevel();
        List<LivingEntity> cibles = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(32),
                x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive());
        if (cibles.size() < 40 && t % 10 == 0) poserHorde(p, 40 - cibles.size(), 8, 30, true);
        if (cibles.isEmpty()) return;
        RandomSource r = p.getRandom();
        for (int tireur = 0; tireur < 4; tireur++) {
            // cadence reelle : 200 coups / min = un tir toutes les 6 ticks par tireur
            if ((t + tireur * 2) % 6 != 0) continue;
            Vec3 o = p.getEyePosition().add((tireur - 1.5) * 1.5, 0, 0);
            LivingEntity cible = cibles.get(r.nextInt(cibles.size()));
            Vec3 vise = new Vec3(cible.getX(), cible.getY() + cible.getBbHeight() * 0.5, cible.getZ());
            Givre.tirer(niveau, p, o, vise.subtract(o), tireur % 2);
        }
    }
}
