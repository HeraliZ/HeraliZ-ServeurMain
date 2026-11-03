package fr.heralisz.clans.classement;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.server.Reponses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * {@code /classement} (staff, niveau 2) : hologrammes de classement du spawn (03/11).
 * <ul>
 * <li>{@code /classement poser <argent|kills|clans>} : pose un hologramme sur le bloc vise (16 blocs au plus ; contre un
 * mur : devant lui), sinon aux pieds du staff. Le texte monte a partir de ce point (environ 3 blocs de haut).</li>
 * <li>{@code /classement retirer} : retire l'hologramme le plus proche (10 blocs au plus).</li>
 * <li>{@code /classement maj} : met tout de suite a jour tous les hologrammes charges.</li>
 * <li>{@code /classement liste} : hologrammes charges (genre et position).</li>
 * </ul>
 * Les hologrammes sont des entites du monde : ils restent apres un redemarrage.
 */
public final class ClassementCommandes {
    private ClassementCommandes() {
    }

    private static final double PORTEE = 16, RAYON_RETRAIT = 10;
    /** Hauteur du bas du texte au-dessus du sol vise. */
    private static final double AU_DESSUS = 0.3;

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> poser = Commands.literal("poser");
        for (Classements.Genre g : Classements.Genre.values()) poser.then(Commands.literal(g.id).executes(c -> poser(c, g)));
        d.register(Commands.literal("classement").requires(s -> s.hasPermission(2))
                .then(poser)
                .then(Commands.literal("retirer").executes(ClassementCommandes::retirer))
                .then(Commands.literal("maj").executes(ClassementCommandes::maj))
                .then(Commands.literal("liste").executes(ClassementCommandes::liste)));
    }

    private static int poser(CommandContext<CommandSourceStack> c, Classements.Genre genre) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Vec3 pos;
        HitResult hit = p.pick(PORTEE, 1f, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && bh.getDirection() != Direction.DOWN) {
            // sol vise : sur ce bloc ; mur vise : dans la case devant lui
            BlockPos b = bh.getDirection() == Direction.UP ? bh.getBlockPos().above() : bh.getBlockPos().relative(bh.getDirection());
            pos = new Vec3(b.getX() + 0.5, b.getY() + AU_DESSUS, b.getZ() + 0.5);
        } else {
            pos = p.position().add(0, AU_DESSUS, 0);
        }
        Display.TextDisplay t = Hologrammes.poser(p.serverLevel(), pos, genre);
        if (t == null) {
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("heralisz_clans.classement.cmd.occupe",
                    "Impossible ici : un hologramme de classement est déjà à cet endroit (ou le monde refuse l'entité)."));
        }
        Reponses.ok(c.getSource(), Component.translatableWithFallback("heralisz_clans.classement.cmd.pose",
                "Hologramme « %s » posé. /classement retirer à côté de lui pour l'enlever.",
                Component.translatableWithFallback("heralisz_clans.classement.genre." + genre.id, genre.id)));
        HeraliszClans.LOG.info("[classements] hologramme {} pose par {} en {} {}", genre.id, p.getGameProfile().getName(),
                p.level().dimension().location(), coords(pos));
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Display.TextDisplay t = Hologrammes.retirer(p.serverLevel(), p.position(), RAYON_RETRAIT);
        if (t == null) {
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("heralisz_clans.classement.cmd.aucun",
                    "Aucun hologramme de classement à moins de %s blocs.", String.valueOf((int) RAYON_RETRAIT)));
        }
        Reponses.ok(c.getSource(), Component.translatableWithFallback("heralisz_clans.classement.cmd.retire", "Hologramme retiré."));
        HeraliszClans.LOG.info("[classements] hologramme retire par {} en {} {}", p.getGameProfile().getName(),
                p.level().dimension().location(), coords(t.position()));
        return 1;
    }

    private static int maj(CommandContext<CommandSourceStack> c) {
        int n = Hologrammes.toutRafraichir();
        Reponses.ok(c.getSource(), Component.translatableWithFallback("heralisz_clans.classement.cmd.maj",
                "%s hologramme(s) chargé(s) mis à jour.", String.valueOf(n)));
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> c) {
        var l = Hologrammes.charges();
        Reponses.ok(c.getSource(), Component.translatableWithFallback("heralisz_clans.classement.cmd.liste",
                "%s hologramme(s) chargé(s) :", String.valueOf(l.size())));
        for (Display.TextDisplay t : l) {
            Classements.Genre g = Hologrammes.genre(t);
            Reponses.ligne(c.getSource(), Component.literal((g == null ? "?" : g.id) + " · " + t.level().dimension().location() + " " + coords(t.position())));
        }
        return 1;
    }

    private static String coords(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", v.x, v.y, v.z);
    }
}
