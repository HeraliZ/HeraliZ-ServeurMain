package fr.heraliz.wonder.blundergat.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.blundergat.BlunderArmes;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * {@code /blundergat} (op 2, banc d'essai et reglage) :
 * <ul>
 *   <li>{@code pap <0..3>} : niveau de Pack-a-Punch du Blundergat / de l'Acid Gat tenu (marqueurs et camo {@code pap_<n>} du
 *   mode zombies, grand chargeur comme la vraie machine : lueurs de The Sweeper / Vitriolic Withering) ;</li>
 *   <li>{@code acide} : convertit le Blundergat tenu en Acid Gat (ce que fait le kit de l'etabli du mode zombies) ;</li>
 *   <li>{@code perf} : cout serveur depuis le dernier releve ;</li>
 *   <li>{@code stress <secondes>} : 4 tireurs virtuels qui tirent des salves d'acide et des gerbes d'impacts sur une horde de
 *   40 monstres qui se reforme (vrais calculs, vrais paquets), puis le bilan dans le journal.</li>
 * </ul>
 */
public final class BlunderCommandes {
    private BlunderCommandes() {
    }

    private static final String NBT_CAMO = "HeraliszCamo";

    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("blundergat").requires(s -> s.hasPermission(2))
                .then(Commands.literal("pap").then(Commands.argument("niveau", IntegerArgumentType.integer(0, Armes.PAP_MAX)).executes(BlunderCommandes::pap)))
                .then(Commands.literal("acide").executes(BlunderCommandes::acide))
                .then(Commands.literal("perf").executes(c -> {
                    MutableComponent r = BlunderPerf.resume(true);
                    HeralizWonder.LOG.info("[wonder] perf blundergat {}", Perf.journal(r));
                    dire(c, r);
                    return 1;
                }))
                .then(Commands.literal("stress").then(Commands.argument("secondes", IntegerArgumentType.integer(1, 300)).executes(BlunderCommandes::stress))));
    }

    private static void dire(CommandContext<CommandSourceStack> c, MutableComponent s) {
        if (c.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(s.withStyle(ChatFormatting.AQUA), true);
        else HeralizWonder.LOG.info("[wonder] {}", Perf.journal(s));
    }

    private static int pap(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = p.getMainHandItem();
        byte type = BlunderArmes.type(s);
        if (type == BlunderArmes.AUCUNE) {
            dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pas_blundergat", "Pas de Blundergat ni d'Acid Gat en main."));
            return 0;
        }
        int n = IntegerArgumentType.getInteger(c, "niveau");
        CompoundTag t = s.getOrCreateTag();
        if (n == 0) {
            t.remove(Armes.NBT_PAP);
            t.remove(Armes.NBT_PAP_NIVEAU);
            if (t.getString(NBT_CAMO).startsWith("pap_")) t.remove(NBT_CAMO);
        } else {
            t.putBoolean(Armes.NBT_PAP, true);
            t.putInt(Armes.NBT_PAP_NIVEAU, n);
            t.putString(NBT_CAMO, "pap_" + n);
        }
        if (ModList.get().isLoaded("tacz")) fr.heraliz.wonder.serveur.TaczServeur.chargeurPap(s, n > 0);
        BlunderNoms.renommer(s);
        p.getInventory().setChanged();
        HeralizWonder.LOG.info("[wonder] banc : {} au Pack-a-Punch niveau {}", BlunderArmes.nom(type), n);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pap", "%s : Pack-a-Punch niveau %s", BlunderArmes.nom(type), String.valueOf(n)));
        return 1;
    }

    private static int acide(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = p.getMainHandItem();
        if (!ModList.get().isLoaded("tacz") || !BlunderTacz.convertir(s)) {
            dire(c, Component.translatableWithFallback("heraliz_wonder.banc.tenir_blundergat", "Il faut tenir un Blundergat."));
            return 0;
        }
        p.getInventory().setChanged();
        HeralizWonder.LOG.info("[wonder] banc : Blundergat converti en Acid Gat (Pack-a-Punch {})", Armes.pap(s));
        dire(c, Component.literal("Acid Gat !"));
        return 1;
    }

    // ------------------------------------------------------------------ stress : 4 tireurs virtuels
    private static ServerPlayer stressJoueur;
    private static int stressTicks, stressTick;

    private static int stress(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        stressJoueur = c.getSource().getPlayerOrException();
        stressTicks = IntegerArgumentType.getInteger(c, "secondes") * 20;
        stressTick = 0;
        BlunderPerf.resume(true);
        HeralizWonder.LOG.info("[wonder] stress blundergat : 4 tireurs virtuels pendant {} s", stressTicks / 20);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.stress_arme", "Stress %s : 4 tireurs virtuels pendant %s s.", "Blundergat",
                String.valueOf(stressTicks / 20)));
        return 1;
    }

    public static void stressTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || stressJoueur == null) return;
        ServerPlayer p = stressJoueur;
        if (stressTick >= stressTicks || p.isRemoved()) {
            MutableComponent r = BlunderPerf.resume(true);
            HeralizWonder.LOG.info("[wonder] stress blundergat termine : {}", Perf.journal(r));
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_wonder.banc.stress_fin", "Stress terminé : %s", r).withStyle(ChatFormatting.AQUA));
            stressJoueur = null;
            return;
        }
        int t = stressTick++;
        ServerLevel niveau = p.serverLevel();
        List<LivingEntity> cibles = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(40),
                x -> x instanceof Enemy && x.isAlive());
        if (cibles.size() < 40 && t % 10 == 0) poserHorde(p, 40 - cibles.size(), 14, 60);
        if (cibles.isEmpty()) return;
        RandomSource r = p.getRandom();
        for (int tireur = 0; tireur < 4; tireur++) {
            Vec3 o = p.getEyePosition().add((tireur - 1.5) * 1.5, 0, 0);
            LivingEntity cible = cibles.get(r.nextInt(cibles.size()));
            Vec3 vise = new Vec3(cible.getX(), cible.getY() + cible.getBbHeight() * 0.5, cible.getZ());
            int pap = tireur % 2;
            // Acid Gat : une salve de 3 cartouches toutes les 0,86 s par tireur (sa cadence reelle)
            if ((t + tireur * 4) % 17 == 0) Cartouches.tirer(niveau, p, o, vise.subtract(o).normalize(), pap);
            // Blundergat : une gerbe de 8 impacts toutes les 0,6 s (effets et paquets ; les degats des obus sont ceux de TaCZ)
            if ((t + tireur * 3) % 12 == 0) {
                for (int k = 0; k < 8; k++) {
                    EclatsServeur.ajouter(niveau, p.getId(), pap, vise.x + (r.nextDouble() - 0.5) * 1.6, vise.y + (r.nextDouble() - 0.5) * 1.4,
                            vise.z + (r.nextDouble() - 0.5) * 1.6, k % 3 == 0 ? 1 : -1, (byte) 0);
                }
            }
        }
    }

    /** Horde de banc : n zombies (husks de jour) en grille lache devant le joueur. */
    static int poserHorde(ServerPlayer p, int n, double distance, double pv) {
        ServerLevel niveau = p.serverLevel();
        Vec3 regard = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 droite = new Vec3(-regard.z, 0, regard.x);
        Vec3 centre = p.position().add(regard.scale(distance));
        int cote = Mth.ceil(Math.sqrt(n));
        RandomSource r = p.getRandom();
        boolean jour = niveau.isDay();
        int poses = 0;
        for (int i = 0; i < n; i++) {
            Zombie z = (jour ? EntityType.HUSK : EntityType.ZOMBIE).create(niveau);
            if (z == null) break;
            double gx = (i % cote - (cote - 1) / 2.0) * 2.2 + (r.nextDouble() - 0.5) * 0.6;
            double gz = (i / cote - (cote - 1) / 2.0) * 2.2 + (r.nextDouble() - 0.5) * 0.6;
            Vec3 pos = centre.add(droite.scale(gx)).add(regard.scale(gz));
            int y = niveau.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
            z.moveTo(pos.x, y, pos.z, p.getYRot() + 180f, 0f);
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
}
