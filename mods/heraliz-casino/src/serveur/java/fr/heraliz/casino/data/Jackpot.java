package fr.heraliz.casino.data;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.JackpotPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Jackpot progressif commun a toutes les machines a sous : une petite part de chaque mise l'alimente,
 * il est gagne avec 3 couronnes (Fruits d'Or) ou 3 etoiles (Diamant Royal).
 */
public class Jackpot extends SavedData {
    private static final String NAME = "heralizcasino_jackpot";
    /** Montant en centiemes de jeton (les petites mises alimentent aussi le jackpot). */
    private long centi = -1;
    private long lastSent = -1;

    public static Jackpot get(MinecraftServer server) {
        Jackpot j = server.overworld().getDataStorage().computeIfAbsent(Jackpot::load, Jackpot::new, NAME);
        if (j.centi < 0) {
            j.centi = CasinoConfig.get().general.jackpotSeed * 100;
            j.setDirty();
        }
        return j;
    }

    public long value() {
        return Math.max(0, centi / 100);
    }

    public void contribute(long bet, double fraction) {
        if (fraction <= 0 || bet <= 0) return;
        centi += Math.round(bet * fraction * 100.0);
        setDirty();
    }

    /** Gain du jackpot pour une mise (au prorata en dessous de la mise pleine) ; le jackpot repart du montant de depart. */
    public long win(long bet) {
        long full = Math.max(1, CasinoConfig.get().general.jackpotFullBet);
        double share = Math.min(1.0, (double) bet / full);
        long won = (long) Math.floor(value() * share);
        centi -= won * 100;
        // la maison remet le montant de depart
        long seed = CasinoConfig.get().general.jackpotSeed * 100;
        if (centi < seed) centi = seed;
        setDirty();
        return won;
    }

    public void set(long value) {
        centi = Math.max(0, value) * 100;
        setDirty();
    }

    /** Diffuse le montant aux joueurs (au plus une fois par seconde, seulement s'il a change). */
    public void broadcast(MinecraftServer server, boolean force) {
        long v = value();
        if (!force && v == lastSent) return;
        lastSent = v;
        CasinoNet.sendToAll(server, new JackpotPacket(v));
    }

    public static Jackpot load(CompoundTag tag) {
        Jackpot j = new Jackpot();
        j.centi = tag.contains("Centi") ? tag.getLong("Centi") : -1;
        return j;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Centi", centi);
        return tag;
    }
}
