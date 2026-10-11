package fr.heralisz.bases.pillage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.blindage.Blindage;
import fr.heralisz.bases.blindage.Protection;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.server.Reponses;
import fr.heralisz.bases.model.EntreeJour;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Sous-commandes op du module (ajoutees a {@code /base admin}) :
 * <pre>
 * /base admin coups &lt;x y z&gt; [n]          coups du bloc protege (n : les fixer ; 0 = intact ; &gt;= niveau = rupture)
 * /base admin c4 &lt;x y z&gt;                 explosion de C4 simulee (vraie explosion du monde, source heralisz_grenade:c4)
 * /base admin pillages                    pillages en cours (clan attaque, attaquants, pillards)
 * /base admin pillage avertir|fin         avertissement de fin de fenetre / renvoi des pillards (tests)
 * /base admin entrees effacer [joueur]    efface l'entree de pillage du jour (tests)
 * /base admin protection recharger        relit protection.json (copie editable config/heralisz_bases/ d'abord)
 * </pre>
 */
public final class CommandesPillage {
    private CommandesPillage() {
    }

    static void ajouter(LiteralArgumentBuilder<CommandSourceStack> base, LiteralArgumentBuilder<CommandSourceStack> admin) {
        admin.then(Commands.literal("coups")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> coups(c, -1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 64))
                                        .executes(c -> coups(c, IntegerArgumentType.getInteger(c, "n"))))))
                .then(Commands.literal("c4")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesPillage::c4)))
                .then(Commands.literal("pillages").executes(c -> pillages(c.getSource())))
                .then(Commands.literal("pillage")
                        .then(Commands.literal("avertir").executes(c -> {
                            Pillages.avertir(c.getSource().getServer(), Math.max(1, PillageConfig.AVERTISSEMENT_S.get() / 60), null);
                            return 1;
                        }))
                        .then(Commands.literal("fin").executes(c -> {
                            Pillages.finDeFenetre(c.getSource().getServer());
                            return 1;
                        })))
                .then(Commands.literal("entrees")
                        .then(Commands.literal("effacer")
                                .executes(c -> effacer(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("joueur", EntityArgument.player())
                                        .executes(c -> effacer(c.getSource(), EntityArgument.getPlayer(c, "joueur"))))))
                .then(Commands.literal("protection")
                        .then(Commands.literal("recharger").executes(c -> recharger(c.getSource()))));
    }

    private static int coups(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        BlockState s = level.getBlockState(pos);
        BlockPos k = Blindage.cle(level, pos, s);
        String txt = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (k == null) {
            Reponses.avertissement(c.getSource(), Component.translatable("cmd.heralisz_bases.coups_aucun", txt, id(s)));
            return 0;
        }
        int niveau = Protection.niveau(level.getBlockState(k));
        if (n >= 0) {
            boolean rompu = Blindage.fixer(level, k, n);
            Reponses.ok(c.getSource(), Component.translatable(rompu ? "cmd.heralisz_bases.coups_rompu" : "cmd.heralisz_bases.coups_mis",
                    txt, n, niveau));
            return 1;
        }
        int coups = Blindage.coups(level, k);
        // l'admin voit aussi l'infobulle du jeu (meme rendu que le clic droit accroupi)
        if (c.getSource().getEntity() instanceof ServerPlayer p) {
            fr.heralisz.bases.network.BasesNet.toPlayer(p, new PillageNet.InfoBlindageS2C(k, id(level.getBlockState(k)), niveau, coups));
        }
        Reponses.ok(c.getSource(), Component.translatable("cmd.heralisz_bases.coups", txt, id(level.getBlockState(k)), niveau, coups, niveau));
        return coups;
    }

    private static String id(BlockState s) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return rl == null ? "?" : rl.toString();
    }

    private static int c4(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        // comme un vrai C4 colle : centre a 0,65 bloc du centre du bloc vise, sur la face tournee vers l'executant
        // (un centre DANS un bloc de resistance 1200 etoufferait tous les rayons de l'explosion)
        Vec3 centre = Vec3.atCenterOf(pos);
        if (!level.getBlockState(pos).isAir()) {
            Vec3 d = c.getSource().getPosition().subtract(centre);
            net.minecraft.core.Direction face = net.minecraft.core.Direction.getNearest(d.x, d.y, d.z);
            centre = centre.add(face.getStepX() * 0.65, face.getStepY() * 0.65, face.getStepZ() * 0.65);
        }
        int n = Blindage.simulerC4(level, centre);
        Reponses.ok(c.getSource(), Component.translatable("cmd.heralisz_bases.c4", pos.getX() + " " + pos.getY() + " " + pos.getZ(), n));
        return n;
    }

    private static int pillages(CommandSourceStack src) {
        if (Pillages.raids().isEmpty()) {
            Reponses.ok(src, Component.translatable("cmd.heralisz_bases.pillages_aucun"));
            return 0;
        }
        List<Base> vues = new ArrayList<>();
        for (Pillages.Raid r : Pillages.raids()) {
            Base b = BasesApi.base(r.base);
            if (b == null || vues.contains(b)) continue;
            vues.add(b);
            String att = String.join(", ", Pillages.attaquants(b.clan));
            MutableComponent noms = Component.empty();
            for (Component n : Pillages.noms(b.clan)) {
                if (!noms.getSiblings().isEmpty()) noms.append(", ");
                noms.append(n);
            }
            Reponses.ok(src, Component.translatable("cmd.heralisz_bases.pillages", Pillages.tagDe(b), att, Pillages.pillards(b.clan), noms));
        }
        return vues.size();
    }

    private static int effacer(CommandSourceStack src, ServerPlayer p) {
        // une entree d'un autre « jour » ne compte plus (purgee au prochain changement de jour)
        BasesApi.entreeJour(new EntreeJour(p.getUUID(), "", null, false, 0));
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.entrees_effacees", p.getGameProfile().getName()));
        return 1;
    }

    private static int recharger(CommandSourceStack src) {
        boolean ok = Protection.charger(src.getServer());
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) Blindage.envoyerTable(p);
        Component m = Component.translatable(ok ? "cmd.heralisz_bases.protection_rechargee" : "cmd.heralisz_bases.protection_erreur",
                Protection.taille(), Protection.nombreSources(), Protection.origine());
        if (ok) Reponses.ok(src, m);
        else Reponses.erreur(src, m);
        return ok ? 1 : 0;
    }
}
