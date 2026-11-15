package fr.heraliz.casino.data;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Registre des versements des machines du casino (15/11, audit de l'économie n° 6 : après un plantage, une manche déjà
 * payée pouvait être rejouée). L'état d'une machine (mises, phase, tirage) n'est gardé qu'avec le monde, alors que gains
 * et remboursements partent tout de suite sur le compte de capsules : un monde rechargé plus ancien qu'un versement
 * rejouait la manche et la payait une seconde fois.
 * <p>Chaque machine porte un numéro ({@link MachineBlockEntity#argent}, gardé avec le monde). AVANT chaque gain,
 * remboursement ou fin de manche, il avance de un, dans la machine ET ici, hors du monde ({@code heralizcasino/manches.log},
 * une ligne « monde|x|y|z numéro » ajoutée sur le disque) ; seulement ensuite le versement est fait. Au premier tick d'une
 * machine chargée, un numéro plus petit que celui noté ici veut dire que le monde a été rechargé plus ancien qu'un
 * versement déjà fait : la machine repart à neuf (partie serveur, {@code MachinesServeur}), rien n'est rejoué. Un plantage
 * peut faire perdre une mise prise après la dernière sauvegarde du monde, jamais faire payer deux fois.
 * <p>Le fichier est relu une fois (premier usage) et réécrit avec une seule ligne par machine. Fil serveur.
 */
public final class RegistreManches {
    private RegistreManches() {
    }

    private static final Map<String, Long> NOTES = new HashMap<>();
    private static boolean charge;

    /** Une fois, au chargement de la partie serveur : tout oublier à l'arrêt (monde suivant en solo). */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, e -> {
            NOTES.clear();
            charge = false;
        });
    }

    private static Path fichier() {
        return FMLPaths.GAMEDIR.get().resolve("heralizcasino").resolve("manches.log");
    }

    private static String cle(ServerLevel l, BlockPos p) {
        return l.dimension().location() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ();
    }

    /** Relit le registre (une fois) et le réécrit avec une ligne par machine ; une dernière ligne coupée est ignorée. */
    private static void charger() {
        if (charge) return;
        charge = true;
        NOTES.clear();
        Path f = fichier();
        if (!Files.isRegularFile(f)) return;
        try {
            for (String l : new String(Files.readAllBytes(f), StandardCharsets.UTF_8).split("\n")) {
                int i = l.lastIndexOf(' ');
                if (i <= 0) continue;
                try {
                    NOTES.merge(l.substring(0, i), Long.parseLong(l.substring(i + 1).trim()), Math::max);
                } catch (NumberFormatException ignore) {
                    // ligne coupée par un plantage : son versement n'a jamais été fait
                }
            }
            StringBuilder b = new StringBuilder();
            NOTES.forEach((k, n) -> b.append(k).append(' ').append(n).append('\n'));
            Path tmp = f.resolveSibling("manches.log.tmp");
            Files.writeString(tmp, b, StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            HeralizCasino.LOGGER.error("[HeralizCasino] registre des manches {} illisible ou pas réécrit ({}) : machines non vérifiées", f, ex.toString());
        }
    }

    /** Numéro noté hors du monde pour la machine à cette place (0 : aucun versement noté). */
    public static long note(ServerLevel l, BlockPos p) {
        charger();
        return NOTES.getOrDefault(cle(l, p), 0L);
    }

    /**
     * Juste AVANT un gain, un remboursement ou la fin d'une manche : le numéro de la machine avance, dans son état (gardé
     * avec le monde) et ici, sur le disque. Une écriture ratée est signalée : le versement est fait quand même (le joueur
     * ne perd rien), mais un plantage dans les minutes suivantes pourrait rejouer cette manche.
     */
    public static void avant(ServerLevel l, MachineBlockEntity be) {
        charger();
        String k = cle(l, be.getBlockPos());
        long n = Math.max(be.argent, NOTES.getOrDefault(k, 0L)) + 1;
        be.argent = n;
        be.setChanged();
        NOTES.put(k, n);
        Path f = fichier();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, k + " " + n + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            HeralizCasino.LOGGER.error("[HeralizCasino] registre des manches : {} n° {} pas noté ({})", k, n, ex.toString());
        }
    }
}
