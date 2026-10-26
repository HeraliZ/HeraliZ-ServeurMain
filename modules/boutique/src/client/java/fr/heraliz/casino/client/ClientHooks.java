package fr.heraliz.casino.client;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.BlackjackBlockEntity;
import fr.heraliz.casino.block.entity.CashierBlockEntity;
import fr.heraliz.casino.block.entity.CrashBlockEntity;
import fr.heraliz.casino.block.entity.HorseRaceBlockEntity;
import fr.heraliz.casino.block.entity.MinesBlockEntity;
import fr.heraliz.casino.block.entity.PlinkoBlockEntity;
import fr.heraliz.casino.block.entity.RouletteBlockEntity;
import fr.heraliz.casino.block.entity.SlotBlockEntity;
import fr.heraliz.casino.client.screen.BlackjackScreen;
import fr.heraliz.casino.client.screen.CashierScreen;
import fr.heraliz.casino.client.screen.CrashScreen;
import fr.heraliz.casino.client.screen.MinesScreen;
import fr.heraliz.casino.client.screen.MachineScreen;
import fr.heraliz.casino.client.screen.PlinkoScreen;
import fr.heraliz.casino.client.screen.RaceScreen;
import fr.heraliz.casino.client.screen.RouletteScreen;
import fr.heraliz.casino.client.screen.SlotScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/**
 * Appels cote client depuis le code commun (ouverture des ecrans de jeu).
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openMachine(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof MachineBlockEntity be)) return;
        Screen s = screen(be);
        if (s != null) mc.setScreen(s);
    }

    /**
     * Depart d'un tour ou le joueur a mise : la camera va sur la machine automatiquement (ecran "spectateur" qui se
     * referme a la fin du tour, sauf si le joueur s'en sert).
     */
    public static void watch(MachineBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || !be.isNear(mc.player)) return;
        Screen s = screen(be);
        if (s instanceof MachineScreen<?> ms) {
            ms.setWatch();
            mc.setScreen(ms);
        }
    }

    /** Tablette du Directeur (operateurs) : reglages d'une machine, ou de toutes. */
    public static void openAdmin(@javax.annotation.Nullable fr.heraliz.casino.machine.MachineType type) {
        Minecraft.getInstance().setScreen(new fr.heraliz.casino.client.screen.AdminScreen(type));
    }

    /** Statistiques recues pour la Tablette du Directeur. */
    public static void adminStats(String json) {
        if (Minecraft.getInstance().screen instanceof fr.heraliz.casino.client.screen.AdminScreen a) a.setStats(json);
    }

    /** Donnees secretes d'un duel (ma flotte, mon coup) recues du serveur. */
    public static void duelPrivate(BlockPos pos, long[] data) {
        Minecraft mc = Minecraft.getInstance();
        fr.heraliz.casino.HeralizCasino.LOGGER.debug("[HeralizCasino] donnees privees de duel recues ({} valeurs) en {}", data.length, pos);
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof fr.heraliz.casino.block.entity.DuelBlockEntity d) d.receivePrivate(data);
    }

    private static Screen screen(MachineBlockEntity be) {
        if (be instanceof SlotBlockEntity b) return new SlotScreen(b);
        if (be instanceof RouletteBlockEntity b) return new RouletteScreen(b);
        if (be instanceof CrashBlockEntity b) return new CrashScreen(b);
        if (be instanceof HorseRaceBlockEntity b) return new RaceScreen(b);
        if (be instanceof PlinkoBlockEntity b) return new PlinkoScreen(b);
        if (be instanceof BlackjackBlockEntity b) return new BlackjackScreen(b);
        if (be instanceof MinesBlockEntity b) return new MinesScreen(b);
        if (be instanceof CashierBlockEntity b) return new CashierScreen(b);
        if (be instanceof fr.heraliz.casino.block.entity.RpsBlockEntity b) return new fr.heraliz.casino.client.screen.RpsScreen(b);
        if (be instanceof fr.heraliz.casino.block.entity.NavalBlockEntity b) return new fr.heraliz.casino.client.screen.NavalScreen(b);
        return null;
    }
}
