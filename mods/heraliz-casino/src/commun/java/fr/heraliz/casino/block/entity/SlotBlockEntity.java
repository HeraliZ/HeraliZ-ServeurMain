package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.slot.Loup;
import fr.heraliz.casino.game.slot.Niche;
import fr.heraliz.casino.game.slot.Olympe;
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.SlotEngine;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * Machines a sous "a sequence" (Rotten, Portes de l'Olympe, Souffle du Loup) : le joueur s'assoit sur le tabouret,
 * mise et lance. Toute la partie (tour, cascades, relances, tours gratuits) est tiree au lancement ; les clients la
 * rejouent et le gain est verse a la fin.
 */
public class SlotBlockEntity extends MachineBlockEntity {
    public static final int ACTION_SPIN = 0, ACTION_BUY = 1, ACTION_SIT = 2, ACTION_LEAVE = 3;
    /** Tabouret devant la machine (le joueur regarde l'ecran, vers -Z). */
    public static final Spot STOOL = new Spot(8, 0, 24, 0, -1, 8, 34, true, -0.2f, 75);

    // ---- synchronise
    public long spinStart = Long.MIN_VALUE / 4;
    public int[] seqData = new int[0];
    public long bet, cost, win;
    public int duration;
    public UUID spinner;
    public String spinnerName = "";
    /** Joueur assis devant la machine (la machine lui est reservee). */
    public UUID seated;
    // ---- client : partie decodee (Rotten.Sequence, Olympe.Sequence ou Loup.Sequence)
    public Object clientGame;
    /** Client : instant ou le joueur local s'est installe a la machine (son intro se joue sur l'ecran). */
    public long introStart = Long.MIN_VALUE / 4;

    public SlotBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        clientGame = decode(new int[0]);
    }

    /** Entite creee par le type enregistre (usine) : la machine a sous est donnee par son bloc. */
    public SlotBlockEntity(BlockPos pos, BlockState state) {
        this(typeOf(state, MachineType.ROTTEN), pos, state);
    }

    /** Moteur du jeu avec les reglages actuels. */
    public SlotEngine engine() {
        CasinoConfig.Settings c = CasinoConfig.get();
        return switch (type) {
            case OLYMPE -> Olympe.engine(c.portesOlympe);
            case LOUP -> Loup.engine(c.souffleLoup);
            case NICHE -> Niche.engine(c.laNiche);
            default -> Rotten.engine(c.rotten);
        };
    }

    private Object decode(int[] d) {
        return switch (type) {
            case OLYMPE -> Olympe.decode(d);
            case LOUP -> Loup.decode(d);
            case NICHE -> Niche.decode(d);
            default -> Rotten.decode(d);
        };
    }

    /** Etat de la partie au temps t (ticks depuis le lancement). */
    public SlotEngine.Status status(float t) {
        if (clientGame instanceof Olympe.Sequence s) return Olympe.status(s, t);
        if (clientGame instanceof Loup.Sequence s) return Loup.status(s, t);
        if (clientGame instanceof Niche.Sequence s) return Niche.status(s, t);
        if (clientGame instanceof Rotten.Sequence s) return Rotten.status(s, t);
        return SlotEngine.Status.NONE;
    }

    /** Au temps t, un rouleau tourne-t-il au ralenti (suspense : la borne affiche sa propre annonce) ? */
    public boolean teasing(float t) {
        if (clientGame instanceof Olympe.Sequence s) return Olympe.teasing(s, t);
        if (clientGame instanceof Loup.Sequence s) return Loup.teasing(s, t);
        if (clientGame instanceof Niche.Sequence s) return Niche.teasing(s, t);
        if (clientGame instanceof Rotten.Sequence s) return Rotten.teasing(s, t);
        return false;
    }

    /** Au temps t, grand moment de mise en scene (super roue, agrandissement de la grille) : l'interface s'efface. */
    public boolean cinematic(float t) {
        return clientGame instanceof Loup.Sequence s && Loup.cinematic(s, t);
    }

    public boolean playing(long now) {
        return now - spinStart < duration;
    }

    /** Le resultat est a l'ecran de la machine : pas de message au-dessus de la barre d'objets. */
    @Override
    protected boolean quietResults() {
        return true;
    }

    /** Pas question de se lever pendant que ses rouleaux (et ses bonus) tournent. */
    @Override
    public boolean locks(@javax.annotation.Nullable UUID player) {
        return player != null && player.equals(spinner) && playing(now());
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        tag.putLong("SpinStart", spinStart);
        tag.putIntArray("Seq", seqData);
        tag.putInt("Duration", duration);
        tag.putLong("Bet", bet);
        tag.putLong("Cost", cost);
        tag.putLong("Win", win);
        putUuid(tag, "Spinner", spinner);
        tag.putString("SpinnerName", spinnerName);
        putUuid(tag, "Seated", seated);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        if (!tag.contains("SpinStart")) return;
        spinStart = tag.getLong("SpinStart");
        int[] d = tag.getIntArray("Seq");
        if (!java.util.Arrays.equals(d, seqData)) {
            seqData = d;
            clientGame = decode(d);
        }
        duration = tag.getInt("Duration");
        bet = tag.getLong("Bet");
        cost = tag.getLong("Cost");
        win = tag.getLong("Win");
        spinner = getUuid(tag, "Spinner");
        spinnerName = tag.getString("SpinnerName");
        seated = getUuid(tag, "Seated");
    }
}
