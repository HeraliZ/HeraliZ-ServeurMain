package fr.heraliz.casino.machine;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.BlackjackBlockEntity;
import fr.heraliz.casino.block.entity.CashierBlockEntity;
import fr.heraliz.casino.block.entity.CrashBlockEntity;
import fr.heraliz.casino.block.entity.HorseRaceBlockEntity;
import fr.heraliz.casino.block.entity.MinesBlockEntity;
import fr.heraliz.casino.block.entity.NavalBlockEntity;
import fr.heraliz.casino.block.entity.PlinkoBlockEntity;
import fr.heraliz.casino.block.entity.RouletteBlockEntity;
import fr.heraliz.casino.block.entity.RpsBlockEntity;
import fr.heraliz.casino.block.entity.SlotBlockEntity;
import fr.heraliz.casino.pont.UsineBlocEntite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;

import static fr.heraliz.casino.machine.Footprint.box;

/**
 * Les machines du casino. Chaque machine a son bloc, son entite, son modele 3D (client) et son ecran de jeu.
 */
public enum MachineType {
    CAISSE("caisse", 0xFFD36A, 0x3DF5A0, 12, CashierBlockEntity::new,
            new Footprint(box(-15, 0, 1, 31, 14, 15.2f), box(-15, 14, 8.9f, 31, 30.5f, 10.3f))),
    // vieux televiseur sur des futs et une caisse de munitions + tabouret devant (le joueur s'y assoit)
    ROTTEN("rotten", 0x9CFF3A, 0xFF4A2A, 10, SlotBlockEntity::new,
            new Footprint(box(-0.4f, 0, 0.5f, 16.4f, 44, 17.0f), box(4.6f, 0, 20.6f, 11.4f, 8.6f, 27.4f))),
    // ruines de temple : grille 6 x 5 qui paie partout, cascades, multiplicateurs de Zeus
    OLYMPE("portes_olympe", 0xFFD36A, 0x6AB8FF, 10, SlotBlockEntity::new,
            new Footprint(box(-0.3f, 0, 0.5f, 16.3f, 44, 17.0f), box(4.6f, 0, 20.6f, 11.4f, 8.6f, 27.4f))),
    // ferme : cochons, maisons de paille / bois / brique, le loup souffle
    LOUP("souffle_loup", 0xFFB040, 0xFF4A2A, 10, SlotBlockEntity::new,
            new Footprint(box(-0.3f, 0, 0.5f, 16.3f, 47.5f, 17.0f), box(4.6f, 0, 20.6f, 11.4f, 8.6f, 27.4f))),
    PLINKO("plinko", 0xFF4FD8, 0x5FE3FF, 12, PlinkoBlockEntity::new,
            new Footprint(box(-15, 0, 3, 31, 62, 14))),
    BLACKJACK("blackjack", 0x3DF5A0, 0xFFD36A, 10, BlackjackBlockEntity::new,
            new Footprint(box(-15, 0, -14, 31, 15, 15))),
    ROULETTE("roulette", 0xFF4D5E, 0xFFD36A, 10, RouletteBlockEntity::new,
            new Footprint(box(-31, 0, -14, 31, 15, 15), box(-31, 15, -12, -26.5f, 35, -9.5f))),
    CRASH("crash", 0xFF7A2E, 0x5FE3FF, 14, CrashBlockEntity::new,
            new Footprint(box(-15, 0, -14, 31, 3.5f, 15), box(-15, 3.5f, -14, 31, 46, -8.5f), box(-12.7f, 3.5f, -0.5f, 1.7f, 6.2f, 13.7f),
                    box(-0.6f, 6, 1.4f, 2, 24.8f, 4))),
    MINES("mines", 0x3DF5A0, 0xFF4D5E, 12, MinesBlockEntity::new,
            new Footprint(box(-15, 0, -10.5f, 31, 15, 15), box(0, 15, -10, 16, 18, -8.4f))),
    COURSE("course", 0x7CFF6A, 0xFFD36A, 10, HorseRaceBlockEntity::new,
            new Footprint(box(-15, 0, -14, 31, 15, 15))),
    // touret de cable en guise de table, tableau des scores ; les joueurs se tiennent debout de chaque cote
    PIERRE_FEUILLE_CISEAUX("pierre_feuille_ciseaux", 0xFFB040, 0xFF4D5E, 10, RpsBlockEntity::new,
            Footprint.withSpots(new float[][]{box(-16, 0, 2, 0, 30, 14), box(16, 0, 2, 32, 30, 14)},
                    box(1.5f, 0, 1.5f, 14.5f, 12, 14.5f), box(6.5f, 12, 0.5f, 9.5f, 34, 3.5f))),
    // longue table, paravent au milieu, un tabouret a chaque bout
    BATAILLE_NAVALE("bataille_navale", 0x5FB8FF, 0xFF4D5E, 10, NavalBlockEntity::new,
            new Footprint(box(-8, 0, 2, 24, 12, 14), box(7, 12, 1.5f, 9, 30, 14.5f), box(-16, 0, 4.5f, -9, 7.5f, 11.5f),
                    box(25, 0, 4.5f, 32, 7.5f, 11.5f))),
    // vieil ecran de bunker (classement des gagnants) sur son pied
    TABLEAU("tableau_gagnants", 0x9CFF3A, 0xFF4A2A, 7, fr.heraliz.casino.block.entity.LeaderboardBlockEntity::new,
            new Footprint(box(-9, 0, 5, 25, 40, 12.5f))),
    TABLEAU_PERDANTS("tableau_perdants", 0xFF5A4A, 0x9CFF3A, 7, fr.heraliz.casino.block.entity.LeaderboardBlockEntity::new,
            new Footprint(box(-9, 0, 5, 25, 40, 12.5f))),
    TABLEAU_PARIEURS("tableau_parieurs", 0xFFC040, 0x9CFF3A, 7, fr.heraliz.casino.block.entity.LeaderboardBlockEntity::new,
            new Footprint(box(-9, 0, 5, 25, 40, 12.5f))),
    // niche de recuperation : 4 grilles Multihold, wilds collants x2 / x3 copies d'une grille a l'autre
    // (ajoutee en dernier : les statistiques enregistrees designent les machines par leur rang)
    NICHE("la_niche", 0x6AC8FF, 0xFFB040, 10, SlotBlockEntity::new,
            new Footprint(box(-0.4f, 0, 0.5f, 16.4f, 45, 17.4f), box(4.6f, 0, 20.6f, 11.4f, 8.6f, 27.4f)));
    /** Fabrique de l'entite de bloc. */
    @FunctionalInterface
    public interface Factory {
        MachineBlockEntity create(BlockPos pos, BlockState state);
    }

    public final String id;
    /** Couleur principale (lumiere, textes) et secondaire. */
    public final int glow, accent;
    /** Lumiere emise par la machine (0-15). */
    public final int light;
    public final Factory factory;
    /**
     * Usine de l'entite de bloc enregistree (docs/METHODE_CLIENT_SERVEUR.md) : version commune (affichage), ou version
     * serveur (regles, mises, tirages) branchee par la partie serveur.
     */
    public final UsineBlocEntite<MachineBlockEntity> usine;
    public final Footprint footprint;

    MachineType(String id, int glow, int accent, int light, Factory factory, Footprint footprint) {
        this.id = id;
        this.glow = glow;
        this.accent = accent;
        this.light = light;
        this.factory = factory;
        this.usine = new UsineBlocEntite<>(factory::create);
        this.footprint = footprint;
    }

    public MutableComponent displayName() {
        return Component.translatable("block.heralizcasino." + id);
    }

    public MutableComponent description() {
        return Component.translatable("machine.heralizcasino." + id + ".desc");
    }

    /** Machine de jeu (mises, statistiques, reglages) : ni la caisse ni le tableau des gagnants. */
    public boolean game() {
        return this != CAISSE && !board();
    }

    /** Ecran de classement. */
    public boolean board() {
        return this == TABLEAU || this == TABLEAU_PERDANTS || this == TABLEAU_PARIEURS;
    }

    /** Machine a sous "a sequence" (Rotten, Olympe, Loup, La Niche). */
    public boolean slot() {
        return this == ROTTEN || this == OLYMPE || this == LOUP || this == NICHE;
    }

    public static MachineType byId(String id) {
        for (MachineType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
