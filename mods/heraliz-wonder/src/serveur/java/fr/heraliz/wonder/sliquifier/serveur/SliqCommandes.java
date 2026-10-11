package fr.heraliz.wonder.sliquifier.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.serveur.Perf;
import fr.heraliz.wonder.sliquifier.Sliquifier;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;

/**
 * {@code /sliq} (op 2, banc d'essai et reglage du Sliquifier) :
 * <ul>
 *   <li>{@code pap <0..3>} : niveau de Pack-a-Punch du Sliquifier tenu (marqueurs, camo {@code pap_<n>} et grand
 *   chargeur, comme la machine du mode zombies) ;</li>
 *   <li>{@code tir} : une boule de gelee depuis les yeux du joueur (sans TaCZ) ;</li>
 *   <li>{@code horde <n> [distance]} : n zombies immobiles devant soi (de jour des husks) ;</li>
 *   <li>{@code etat}, {@code nettoyer} : flaques en cours, tout effacer ;</li>
 *   <li>{@code perf} : cout serveur depuis le dernier releve ;</li>
 *   <li>{@code stress <secondes>} : 4 tireurs virtuels qui arrosent la horde (vraies boules, flaques, glissades,
 *   paquets), puis le bilan dans le journal.</li>
 * </ul>
 * Reponses par message direct au joueur (barre d'action).
 */
public final class SliqCommandes {
    private SliqCommandes() {
    }

    private static final String NBT_CAMO = "HeraliszCamo";

    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("sliq").requires(s -> s.hasPermission(2))
                .then(Commands.literal("pap").then(Commands.argument("niveau", IntegerArgumentType.integer(0, Armes.PAP_MAX)).executes(SliqCommandes::pap)))
                .then(Commands.literal("tir").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    SliqServeur.tirer(p.serverLevel(), p, p.getEyePosition(), p.getLookAngle(), Sliquifier.papTenu(p));
                    return 1;
                }))
                .then(Commands.literal("horde").then(Commands.argument("n", IntegerArgumentType.integer(1, 120)).executes(c -> horde(c, 10))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(2, 64)).executes(c -> horde(c, IntegerArgumentType.getInteger(c, "distance"))))))
                .then(Commands.literal("etat").executes(c -> {
                    dire(c, SliqServeur.etat());
                    return 1;
                }))
                .then(Commands.literal("nettoyer").executes(c -> {
                    dire(c, Component.translatableWithFallback("heraliz_wonder.banc.flaques_effacees", "%s flaques effacées.",
                            String.valueOf(SliqServeur.nettoyer())));
                    return 1;
                }))
                .then(Commands.literal("perf").executes(c -> {
                    MutableComponent r = SliqPerf.resume(true);
                    HeralizWonder.LOG.info("[sliquifier] perf {}", Perf.journal(r));
                    dire(c, r);
                    return 1;
                }))
                .then(Commands.literal("stress").then(Commands.argument("secondes", IntegerArgumentType.integer(1, 300)).executes(SliqCommandes::stress))));
    }

    private static void dire(CommandContext<CommandSourceStack> c, MutableComponent s) {
        if (c.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(s.withStyle(ChatFormatting.LIGHT_PURPLE), true);
        else HeralizWonder.LOG.info("[sliquifier] {}", Perf.journal(s));
    }

    private static int pap(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = p.getMainHandItem();
        if (!Sliquifier.est(s)) {
            dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pas_sliquifier", "Pas de Sliquifier en main."));
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
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) SliqTacz.chargeurPap(s, n > 0);
        SliqPap.renommer(s);
        p.getInventory().setChanged();
        HeralizWonder.LOG.info("[sliquifier] banc : Pack-a-Punch niveau {}", n);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pap", "%s : Pack-a-Punch niveau %s", "Sliquifier", String.valueOf(n)));
        return 1;
    }

    private static int horde(CommandContext<CommandSourceStack> c, int distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = poserHorde(p, IntegerArgumentType.getInteger(c, "n"), distance, 20);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.zombies_poses", "%s zombies posés.", String.valueOf(n)));
        return n;
    }

    /** n zombies immobiles en grille lache a {@code distance} devant le joueur, tournes vers lui (de jour des husks). */
    static int poserHorde(ServerPlayer p, int n, double distance, double pv) {
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
            double gx = (i % cote - (cote - 1) / 2.0) * 2.2 + (r.nextDouble() - 0.5) * 0.6;
            double gz = (i / cote - (cote - 1) / 2.0) * 2.2 + (r.nextDouble() - 0.5) * 0.6;
            Vec3 pos = centre.add(droite.scale(gx)).add(regard.scale(gz));
            int y = niveau.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
            z.moveTo(pos.x, y, pos.z, p.getYRot() + 180f, 0f);
            z.setYHeadRot(p.getYRot() + 180f);
            z.setYBodyRot(p.getYRot() + 180f);
            z.setNoAi(true);
            z.setSilent(true);
            z.setPersistenceRequired();
            if (z.getAttribute(Attributes.MAX_HEALTH) != null) z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(pv);
            z.setHealth((float) pv);
            z.getPersistentData().putBoolean("heraliz_wonder:banc", true);
            niveau.addFreshEntity(z);
            poses++;
        }
        return poses;
    }

    // ------------------------------------------------------------------ stress : 4 tireurs virtuels
    private static ServerPlayer stressJoueur;
    private static int stressTicks, stressTick;

    private static int stress(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        stressJoueur = c.getSource().getPlayerOrException();
        stressTicks = IntegerArgumentType.getInteger(c, "secondes") * 20;
        stressTick = 0;
        SliqPerf.resume(true);
        HeralizWonder.LOG.info("[sliquifier] stress : 4 tireurs virtuels pendant {} s", stressTicks / 20);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.stress", "Stress : 4 tireurs virtuels pendant %s s.", String.valueOf(stressTicks / 20)));
        return 1;
    }

    public static void stressTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || stressJoueur == null) return;
        ServerPlayer p = stressJoueur;
        if (stressTick >= stressTicks || p.isRemoved()) {
            MutableComponent r = SliqPerf.resume(true);
            HeralizWonder.LOG.info("[sliquifier] stress termine : {}", Perf.journal(r));
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_wonder.banc.stress_fin", "Stress terminé : %s", r).withStyle(ChatFormatting.LIGHT_PURPLE));
            stressJoueur = null;
            return;
        }
        int t = stressTick++;
        ServerLevel niveau = p.serverLevel();
        List<LivingEntity> cibles = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(40),
                x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive());
        if (cibles.size() < 40 && t % 10 == 0) poserHorde(p, 40 - cibles.size(), 16, 60);
        if (cibles.isEmpty()) return;
        RandomSource r = p.getRandom();
        for (int tireur = 0; tireur < 4; tireur++) {
            // cadence reelle du Sliquifier : 150 coups par minute = un tir toutes les 8 ticks par tireur
            if ((t + tireur * 2) % 8 != 0) continue;
            Vec3 o = p.getEyePosition().add((tireur - 1.5) * 1.5, 0, 0);
            LivingEntity cible = cibles.get(r.nextInt(cibles.size()));
            // vise le sol pres de la cible une fois sur deux (flaques), la cible elle-meme sinon (coups directs)
            Vec3 vise = new Vec3(cible.getX() + (r.nextDouble() - 0.5) * 3, cible.getY() + ((t / 8 + tireur) % 2 == 0 ? 0.1 : cible.getBbHeight() * 0.6),
                    cible.getZ() + (r.nextDouble() - 0.5) * 3);
            SliqServeur.tirer(niveau, p, o, vise.subtract(o).add(0, vise.distanceTo(o) * 0.012, 0), tireur % 2);
        }
    }
}
