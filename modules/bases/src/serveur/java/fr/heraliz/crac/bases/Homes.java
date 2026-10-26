package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /home} (25/10, demande du propriétaire) : du spawn vers la base de son clan, juste devant son armoire à outils.
 * Plus aucun home à poser à la main : le point suit l'armoire ({@link TeleportBase}). Mêmes règles que la réapparition à
 * la base, avec le même délai partagé (8 min) : seulement depuis le spawn ({@link Deplacements#auSpawn}), hors combat, à
 * terre et match ({@link Deplacements#refuse}), avec le droit de clan « Se téléporter à la base », après
 * {@code [home] immobile_s} secondes sans bouger. Le staff (permission 2) part de partout, sans délai.
 * <p>
 * Avant le 25/10 : homes posés par {@code /home poser} (gardés dans {@code data/crac_bases.dat}, plus lus).
 */
public final class Homes {
    @Nullable
    private static Method permGrade;
    private static boolean permCherche;

    private Homes() {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("home").executes(c -> home(c.getSource().getPlayerOrException())));
    }

    /**
     * Permission de grade (HeraliZ Grades 2.0.0, {@code GradesApi.perm}, réglée dans l'éditeur des grades) ; -1 sans le
     * mod ou avec une version plus ancienne.
     */
    static int permGrade(UUID joueur, String cle) {
        if (!permCherche) {
            permCherche = true;
            try {
                permGrade = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("perm", UUID.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                permGrade = null;
            }
        }
        if (permGrade == null) {
            return -1;
        }
        try {
            Object r = permGrade.invoke(null, joueur, cle);
            return r instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    static int home(ServerPlayer p) {
        if (Deplacements.refuse(p)) {
            return 0;
        }
        if (!Deplacements.staff(p) && !Deplacements.auSpawn(p)) {
            T.chat(p, T.t("crac.home.depuis_spawn", "/home ne marche que depuis le spawn."), ChatFormatting.RED);
            return 0;
        }
        int r = TeleportBase.raison(p);
        if (r != TeleportBase.OK) {
            T.chat(p, TeleportBase.texte(p, r), ChatFormatting.RED);
            return 0;
        }
        Deplacements.attendre(p, "home", () -> partir(p));
        return 1;
    }

    private static void partir(ServerPlayer p) {
        int r = TeleportBase.teleporter(p);
        if (r == TeleportBase.OK) {
            Deplacements.apres(p, T.t("crac.home.arrive", "Bienvenue chez toi : te voici devant l'armoire de ta base.").withStyle(ChatFormatting.GREEN));
        } else if (r < 0) {
            T.chat(p, T.t("crac.home.obstrue_tc", "La place devant l'armoire de ta base est bouchée : dégage-la (ou préviens ton clan)."),
                ChatFormatting.RED);
        } else {
            T.chat(p, TeleportBase.texte(p, r), ChatFormatting.RED);
        }
    }

    /** Ancien home posé tout seul à la première base : plus utile, /home va toujours devant l'armoire (25/10). */
    public static void premiereBase(ServerPlayer p, ServerLevel level, BlockPos ancre) {
    }
}
