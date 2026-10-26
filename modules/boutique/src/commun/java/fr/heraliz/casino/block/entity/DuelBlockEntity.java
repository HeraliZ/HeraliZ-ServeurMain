package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.DuelPrivatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Jeu joueur contre joueur : un joueur lance un defi (sa mise), un autre l'accepte avec la meme mise, puis la partie
 * se joue. Le gagnant remporte les deux mises moins la commission du casino. Personne ne releve le defi a temps, ou
 * la machine est cassee : chacun est rembourse.
 */
public abstract class DuelBlockEntity extends MachineBlockEntity {
    public static final int IDLE = 0, OPEN = 1, PLAYING = 2, DONE = 3;
    public static final int ACTION_CREATE = 0, ACTION_JOIN = 1, ACTION_CANCEL = 2, ACTION_SYNC = 3;
    /** Affichage du resultat avant de liberer la machine (ticks). */
    public static final int SHOW_TICKS = 100;
    /** Gagnant : 1 = createur (A), 2 = adversaire (B), 3 = duel annule (rembourse). */
    public static final int WIN_A = 1, WIN_B = 2, VOID = 3;
    /** Adversaire artificiel (commande /casino bot) : il ne mise rien et n'apparait pas dans les statistiques. */
    public static final UUID BOT = UUID.nameUUIDFromBytes("heralizcasino:bot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    public static final String BOT_NAME = "Rodeur [BOT]";

    // ---- synchronise
    public int phase = IDLE;
    @Nullable
    public UUID playerA, playerB;
    public String nameA = "", nameB = "";
    public long stake;
    public long phaseStart, deadline;
    public int winner;
    public long prize;
    /** Cote tenu par le bot (1 = A, 2 = B), 0 = aucun. */
    public int botSide;

    protected DuelBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract DuelRules rules();

    /** Debut de la partie (les deux mises sont prises). Version serveur de chaque jeu. */
    protected void startGame(ServerLevel level, long now) {
    }

    /** Version serveur de chaque jeu. */
    protected void tickGame(ServerLevel level, long now) {
    }

    /** Version serveur de chaque jeu. */
    protected void handleGame(ServerPlayer p, int side, int action, long[] args) {
    }

    /** Donnees secretes du joueur (sa flotte, son coup...) : envoyees a lui seul. */
    protected long[] privateData(int side) {
        return new long[0];
    }

    /** Client : donnees secretes recues. */
    public void receivePrivate(long[] data) {
    }

    protected void resetGame() {
    }

    /** Place du joueur (1 = A, 2 = B) pendant la partie, ou null (le joueur reste libre). */
    @Nullable
    protected Spot spot(int side) {
        return null;
    }

    /** Les deux joueurs quittent leur place (partie serveur : unseat). */
    protected void unseatAll() {
        unseat(playerA);
        unseat(playerB);
    }

    protected abstract void writeGame(CompoundTag tag);

    protected abstract void readGame(CompoundTag tag);

    /** Le bot joue son coup quand c'est a lui (appele a chaque tick de la partie). */
    protected void tickBot(ServerLevel level, long now, int side) {
    }

    /** Action du bot, comme s'il avait clique. */
    protected void botAct(int side, int action, long... args) {
        handleGame(null, side, action, args);
    }

    // =================================================================== verrou et forfait
    /** Un joueur absent de la table (deconnecte, parti, mort) pendant ce temps perd la partie par forfait. */
    public static final int FORFEIT_TICKS = 200;
    /** Forfait des absents (desactive par les tests automatiques, dont les joueurs factices ne sont pas "en ligne"). */
    public static boolean forfeitEnabled = true;
    final int[] absent = new int[3];

    /** Duel commence : les deux joueurs restent a la table jusqu'a la fin. */
    @Override
    public boolean locks(@Nullable UUID player) {
        return player != null && phase == PLAYING && sideOf(player) != 0;
    }

    /** Defi lance (mise en attente d'un adversaire) ou duel en cours. */
    @Override
    public boolean engaged(@Nullable UUID player) {
        return player != null && (phase == OPEN || phase == PLAYING) && sideOf(player) != 0;
    }

    // =================================================================== bot
    /** Place d'un cote (pour dessiner le bot). */
    @Nullable
    public Spot seatOf(int side) {
        return spot(side);
    }

    public boolean isBot(int side) {
        return side != 0 && side == botSide;
    }

    // =================================================================== utilitaires
    /** 1 si le joueur est le createur, 2 s'il est l'adversaire, 0 sinon. */
    public int sideOf(@Nullable UUID id) {
        if (id == null) return 0;
        if (id.equals(playerA)) return 1;
        if (id.equals(playerB)) return 2;
        return 0;
    }

    @Nullable
    public UUID player(int side) {
        return side == 1 ? playerA : side == 2 ? playerB : null;
    }

    public String name(int side) {
        return side == 1 ? nameA : side == 2 ? nameB : "";
    }

    /** La machine accepte un nouveau defi. */
    public boolean free(long now) {
        return phase == IDLE || (phase == DONE && now >= phaseStart + SHOW_TICKS);
    }

    protected void sendPrivate(int side) {
        ServerPlayer p = player(player(side));
        if (p != null) CasinoNet.sendTo(p, new DuelPrivatePacket(worldPosition, privateData(side)));
    }

    // =================================================================== partie serveur (docs/METHODE_CLIENT_SERVEUR.md)
    /**
     * Deroulement commun des duels (src/serveur : DuelsServeur) : defi, adversaire, forfait, fin et reglement. Branche
     * par la partie serveur ; sans elle, la machine est neutre.
     */
    public interface Moteur {
        void handleAction(DuelBlockEntity be, ServerPlayer p, int action, long[] args);

        void tickServer(DuelBlockEntity be, ServerLevel level);

        void finish(DuelBlockEntity be, ServerLevel level, int win);

        void onRemoved(DuelBlockEntity be, ServerLevel level);
    }

    static volatile Moteur moteur;

    // =================================================================== actions
    @Override
    public void handleAction(ServerPlayer p, int action, long[] args) {
        Moteur m = moteur;
        if (m != null) m.handleAction(this, p, action, args);
    }

    // =================================================================== tick
    @Override
    protected void tickServer(ServerLevel level) {
        Moteur m = moteur;
        if (m != null) m.tickServer(this, level);
    }

    /** Fin du duel : le gagnant touche le pot moins la commission ; VOID rembourse les deux joueurs. */
    protected void finish(ServerLevel level, int win) {
        Moteur m = moteur;
        if (m != null) m.finish(this, level, win);
    }

    @Override
    public void onRemoved(ServerLevel level) {
        Moteur m = moteur;
        if (m != null) m.onRemoved(this, level);
    }

    // =================================================================== NBT
    @Override
    protected final void writeSync(CompoundTag tag) {
        tag.putInt("Phase", phase);
        putUuid(tag, "PlayerA", playerA);
        putUuid(tag, "PlayerB", playerB);
        tag.putString("NameA", nameA);
        tag.putString("NameB", nameB);
        tag.putLong("Stake", stake);
        tag.putLong("PhaseStart", phaseStart);
        tag.putLong("Deadline", deadline);
        tag.putInt("Winner", winner);
        tag.putLong("Prize", prize);
        tag.putInt("BotSide", botSide);
        writeGame(tag);
    }

    @Override
    protected final void readSync(CompoundTag tag) {
        if (!tag.contains("Phase")) return;
        phase = tag.getInt("Phase");
        playerA = getUuid(tag, "PlayerA");
        playerB = getUuid(tag, "PlayerB");
        nameA = tag.getString("NameA");
        nameB = tag.getString("NameB");
        stake = tag.getLong("Stake");
        phaseStart = tag.getLong("PhaseStart");
        deadline = tag.getLong("Deadline");
        winner = tag.getInt("Winner");
        prize = tag.getLong("Prize");
        botSide = tag.getInt("BotSide");
        readGame(tag);
    }
}
