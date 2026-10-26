package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vieil ecran de bunker qui affiche un classement du casino : plus gros gains en une partie (gagnants), plus grosses
 * pertes nettes (perdants) ou plus gros montants mises (parieurs), et le record du casino. Le serveur rafraichit le
 * classement toutes les 5 secondes et ne l'envoie aux joueurs que s'il a change.
 */
public class LeaderboardBlockEntity extends MachineBlockEntity {
    public static final int ROWS = 8;

    // ---- synchronise
    public String[] names = new String[0], machines = new String[0];
    public long[] values = new long[0];
    public String recordName = "", recordMachine = "";
    public long recordAmount;

    public LeaderboardBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Entite creee par le type enregistre (usine) : le classement est donne par son bloc. */
    public LeaderboardBlockEntity(BlockPos pos, BlockState state) {
        this(typeOf(state, MachineType.TABLEAU), pos, state);
    }

    /** Classements (memes valeurs que BOARD_..., cote serveur). */
    public static final int BOARD_WINNERS = 0, BOARD_LOSERS = 1, BOARD_BETTORS = 2;

    /** Classement affiche (BOARD_...). */
    public int kind() {
        return type == MachineType.TABLEAU_PERDANTS ? BOARD_LOSERS : type == MachineType.TABLEAU_PARIEURS ? BOARD_BETTORS
                : BOARD_WINNERS;
    }

    private static ListTag strings(String[] a) {
        ListTag l = new ListTag();
        for (String s : a) l.add(StringTag.valueOf(s));
        return l;
    }

    private static String[] strings(ListTag l) {
        String[] out = new String[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = l.getString(i);
        return out;
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        tag.put("Names", strings(names));
        tag.put("Machines", strings(machines));
        tag.putLongArray("Values", values);
        tag.putString("RecordName", recordName);
        tag.putLong("RecordAmount", recordAmount);
        tag.putString("RecordMachine", recordMachine);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        names = strings(tag.getList("Names", Tag.TAG_STRING));
        machines = strings(tag.getList("Machines", Tag.TAG_STRING));
        values = tag.getLongArray("Values");
        recordName = tag.getString("RecordName");
        recordAmount = tag.getLong("RecordAmount");
        recordMachine = tag.getString("RecordMachine");
    }
}
