package fr.heraliz.wonder.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
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

import java.util.List;

/**
 * {@code /wonder} (op 2, banc d'essai et reglage) :
 * <ul>
 *   <li>{@code pap <0..3>} : niveau de Pack-a-Punch de la Wonder Weapon tenue (marqueurs et camo {@code pap_<n>} du mode
 *   zombies ; le Thundergun recoit aussi le grand chargeur, comme avec la vraie machine) ;</li>
 *   <li>{@code horde <n> [distance] [pv]} : n zombies immobiles en groupe devant soi (cible d'essai ; de jour des husks,
 *   qui ne brulent pas au soleil) ;</li>
 *   <li>{@code perf} : cout serveur des tirs depuis le dernier releve ;</li>
 *   <li>{@code stress <secondes>} : 4 tireurs virtuels qui tirent sans arret avec les 4 armes sur les monstres proches
 *   (foudres, eclaboussures, impacts, ondes de choc : vrais calculs, vrais paquets), puis le bilan dans le journal.</li>
 * </ul>
 * Reponses par message direct au joueur.
 */
public final class Commandes {
    private Commandes() {
    }

    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("wonder").requires(s -> s.hasPermission(2))
                .then(Commands.literal("pap").then(Commands.argument("niveau", IntegerArgumentType.integer(0, Armes.PAP_MAX)).executes(Commandes::pap)))
                .then(Commands.literal("horde").then(Commands.argument("n", IntegerArgumentType.integer(1, 120))
                        .executes(c -> horde(c, 12, 20))
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(2, 64))
                                .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), 20))
                                .then(Commands.argument("pv", DoubleArgumentType.doubleArg(1, 100000))
                                        .executes(c -> horde(c, DoubleArgumentType.getDouble(c, "distance"), DoubleArgumentType.getDouble(c, "pv")))))))
                .then(Commands.literal("perf").executes(c -> {
                    MutableComponent r = Perf.resume(true);
                    HeralizWonder.LOG.info("[wonder] perf {}", Perf.journal(r));
                    dire(c, r);
                    return 1;
                }))
                .then(Commands.literal("stress").then(Commands.argument("secondes", IntegerArgumentType.integer(1, 300)).executes(Commandes::stress))));
    }

    /** Etiquette du camo de heralisz_camo ({@code CamoData.KEY}), posee par la machine Pack-a-Punch du mode zombies. */
    private static final String NBT_CAMO = "HeraliszCamo";

    private static void dire(CommandContext<CommandSourceStack> c, MutableComponent s) {
        // barre d'action : message direct au joueur, sans remplir le chat (captures du banc lisibles)
        if (c.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(s.withStyle(ChatFormatting.AQUA), true);
        else HeralizWonder.LOG.info("[wonder] {}", Perf.journal(s));
    }

    /** Nom de l'arme pour le banc : noms propres tels quels, « pistolet a rayons » traduit. */
    private static Component nom(byte type) {
        return type == Armes.RAYON ? Component.translatableWithFallback("heraliz_wonder.banc.nom_rayon", "pistolet à rayons") : Component.literal(Armes.nom(type));
    }

    private static int pap(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = p.getMainHandItem();
        byte type = Armes.type(s);
        if (type == Armes.AUCUNE) {
            dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pas_wonder", "Pas de Wonder Weapon en main."));
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
            // camo du niveau (heralisz_camo), comme la machine Pack-a-Punch du mode zombies (1.0.2)
            t.putString(NBT_CAMO, "pap_" + n);
        }
        // le Thundergun montre les lueurs du Zeus Cannon quand le chargeur grande capacite est monte (comme la machine)
        // (Winter's Howl : cartouche Fury ; Paralyzer : lueurs turquoise du Petrifier)
        if ((type == Armes.TONNERRE || type == Armes.HURLEMENT || type == Armes.PARALYSEUR) && net.minecraftforge.fml.ModList.get().isLoaded("tacz"))
            TaczServeur.chargeurPap(s, n > 0);
        NomsPap.renommer(s);
        p.getInventory().setChanged();
        HeralizWonder.LOG.info("[wonder] banc : {} au Pack-a-Punch niveau {}", Armes.nom(type), n);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.pap", "%s : Pack-a-Punch niveau %s", nom(type), String.valueOf(n)));
        return 1;
    }

    private static int horde(CommandContext<CommandSourceStack> c, double distance, double pv) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "n");
        int poses = poserHorde(p, n, distance, pv);
        HeralizWonder.LOG.info("[wonder] banc : horde de {} zombies a {} blocs ({} PV)", poses, distance, pv);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.zombies_poses", "%s zombies posés.", String.valueOf(poses)));
        return poses;
    }

    /** Pose n zombies immobiles en grille lache centree a {@code distance} devant le joueur, tournes vers lui. */
    static int poserHorde(ServerPlayer p, int n, double distance, double pv) {
        ServerLevel niveau = p.serverLevel();
        Vec3 regard = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 droite = new Vec3(-regard.z, 0, regard.x);
        Vec3 centre = p.position().add(regard.scale(distance));
        int cote = Mth.ceil(Math.sqrt(n));
        if (cote % 2 == 0) cote++; // colonne du milieu pleine : le joueur vise un zombie, pas un trou
        RandomSource r = p.getRandom();
        int poses = 0;
        // de jour, des husks : ils ne brulent pas au soleil (captures du banc lisibles)
        boolean jour = niveau.isDay();
        for (int i = 0; i < n; i++) {
            Zombie z = (jour ? EntityType.HUSK : EntityType.ZOMBIE).create(niveau);
            if (z == null) break;
            double gx = (i % cote - (cote - 1) / 2.0) * 2.2 + (r.nextDouble() - 0.5) * 0.6;
            double gz = (i / cote - (cote - 1) / 2.0) * 2.2 + (r.nextDouble() - 0.5) * 0.6;
            Vec3 pos = centre.add(droite.scale(gx)).add(regard.scale(gz));
            int y = niveau.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
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
        Perf.resume(true);
        HeralizWonder.LOG.info("[wonder] stress : 4 tireurs virtuels pendant {} s", stressTicks / 20);
        dire(c, Component.translatableWithFallback("heraliz_wonder.banc.stress", "Stress : 4 tireurs virtuels pendant %s s.", String.valueOf(stressTicks / 20)));
        return 1;
    }

    /** Branche une fois pour toutes (ne fait rien hors stress). */
    public static void stressTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || stressJoueur == null) return;
        ServerPlayer p = stressJoueur;
        if (stressTick >= stressTicks || p.isRemoved()) {
            MutableComponent r = Perf.resume(true);
            HeralizWonder.LOG.info("[wonder] stress termine : {}", Perf.journal(r));
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_wonder.banc.stress_fin", "Stress terminé : %s", r).withStyle(ChatFormatting.AQUA));
            stressJoueur = null;
            return;
        }
        int t = stressTick++;
        ServerLevel niveau = p.serverLevel();
        List<LivingEntity> cibles = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(40),
                x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive());
        // la horde se reforme (comme une manche haute) : au moins 40 monstres
        if (cibles.size() < 40 && t % 10 == 0) poserHorde(p, 40 - cibles.size(), 16, 60);
        if (cibles.isEmpty()) return;
        RandomSource r = p.getRandom();
        for (int tireur = 0; tireur < 4; tireur++) {
            Vec3 o = p.getEyePosition().add((tireur - 1.5) * 1.5, 0, 0);
            LivingEntity cible = cibles.get(r.nextInt(cibles.size()));
            Vec3 vise = new Vec3(cible.getX(), cible.getY() + cible.getBbHeight() * 0.5, cible.getZ());
            int pap = tireur % 2;
            // DG-2 : 1 tir / s par tireur ; pistolet a rayons : ~3 tirs / s ; Mk II : rafale de 3 toutes les 0,5 s
            if ((t + tireur * 5) % 20 == 0) Foudre.tirer(niveau, p, o, vise.subtract(o), pap);
            if ((t + tireur) % 7 == 0) Eclaboussure.eclater(niveau, p, vise.x, vise.y, vise.z, pap, cible, null);
            // Thundergun : 1 onde de choc toutes les 1,2 s par tireur (sa cadence reelle)
            if ((t + tireur * 6) % 24 == 0) Tonnerre.tirer(niveau, p, o, vise.subtract(o), pap);
            if ((t + tireur * 3) % 10 < 3) {
                fr.heraliz.wonder.net.Reseau.autour(niveau, vise.x, vise.y, vise.z, new fr.heraliz.wonder.net.Impact(Armes.MK2, (byte) pap,
                        (float) vise.x, (float) vise.y, (float) vise.z, (byte) -1, (byte) 0));
            }
        }
    }
}
