package fr.heraliz.casino.block;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Entite commune des machines : synchronisation client, horloge, mises et gains en jetons, statistiques.
 * Le serveur decide de tout (tirages avec un generateur cryptographique) ; le client ne fait qu'animer
 * a partir des instants de depart synchronises.
 */
public abstract class MachineBlockEntity extends BlockEntity {
    /** Distance maximale (blocs) pour jouer. */
    public static final double MAX_DISTANCE = 8.0;

    public final MachineType type;
    /** Valeur propre a la machine (desynchronise les animations d'ambiance). */
    public final float seed;

    // ---- client uniquement : dernier tick d'effets joue, vue du modele 3D
    public long fxLastTick = Long.MIN_VALUE;
    public Object clientView;
    /** Client : debut du dernier tour pour lequel la camera est deja partie sur la machine. */
    public long clientWatched = Long.MIN_VALUE;

    protected MachineBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(ModRegistry.beType(type), pos, state);
        this.type = type;
        this.seed = (Mth.murmurHash3Mixer(pos.hashCode()) & 0xFFFF) / 65535f;
    }

    /** Type d'une machine d'apres son bloc (entite creee par le type enregistre : constructeur (pos, etat)). */
    protected static MachineType typeOf(BlockState state, MachineType defaut) {
        return state.getBlock() instanceof MachineBlock mb ? mb.type : defaut;
    }

    // =================================================================== partie serveur (docs/METHODE_CLIENT_SERVEUR.md)
    /**
     * Logique serveur commune a toutes les machines (src/serveur : MachinesServeur) : tick du serveur, mises, gains,
     * remboursements, places des joueurs. Branchee par la partie serveur ; sans elle, les machines sont neutres.
     */
    public interface Moteur {
        void serverTick(ServerLevel level, BlockPos pos, BlockState state, MachineBlockEntity be);

        boolean takeBet(MachineBlockEntity be, ServerPlayer p, long amount);

        void refund(MachineBlockEntity be, ServerLevel level, UUID player, long amount);

        void payout(MachineBlockEntity be, ServerLevel level, UUID player, String name, long amount, long wager);

        boolean seat(MachineBlockEntity be, ServerPlayer p, Spot s);

        void unseat(MachineBlockEntity be, @Nullable UUID id);
    }

    static volatile Moteur moteur;

    // =================================================================== cycle de vie
    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        Moteur m = moteur;
        if (m != null) m.serverTick(sl, pos, state, be);
    }

    /** Ticker du client (animations, sons, effets) : branche par la partie client (MachineClientTicker). */
    public static volatile BlockEntityTicker<MachineBlockEntity> TICK_CLIENT;

    public static void clientTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        BlockEntityTicker<MachineBlockEntity> t = TICK_CLIENT;
        if (t != null) t.tick(level, pos, state, be);
    }

    /** Tick du serveur : redefini par la version serveur de chaque machine (neutre ici). */
    protected void tickServer(ServerLevel level) {
    }

    /**
     * Action demandee par un joueur depuis l'ecran de la machine (deja verifie : distance) : redefinie par la version
     * serveur de chaque machine (neutre ici).
     */
    public void handleAction(ServerPlayer player, int action, long[] args) {
    }

    /** La machine est cassee : rembourser / payer ce qui est en cours. */
    public void onRemoved(ServerLevel level) {
    }

    /** Etat envoye aux clients (et sauvegarde). */
    protected abstract void writeSync(CompoundTag tag);

    protected abstract void readSync(CompoundTag tag);

    /** Donnees secretes, jamais envoyees aux clients (sauvegarde seulement). */
    protected void writeServer(CompoundTag tag) {
    }

    protected void readServer(CompoundTag tag) {
    }

    // =================================================================== synchro
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeSync(tag);
        writeServer(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readSync(tag);
        readServer(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeSync(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        int r = type.footprint.reach() + 1;
        int h = type.footprint.height() + 2;
        return new AABB(worldPosition.getX() - r, worldPosition.getY() - 1, worldPosition.getZ() - r,
                worldPosition.getX() + 1 + r, worldPosition.getY() + h, worldPosition.getZ() + 1 + r);
    }

    // =================================================================== utilitaires
    public long now() {
        return level == null ? 0 : level.getGameTime();
    }

    public boolean isNear(Player p) {
        return p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5)
                <= MAX_DISTANCE * MAX_DISTANCE;
    }

    public long minBet() {
        return CasinoConfig.get().limits(type)[0];
    }

    public long maxBet() {
        return CasinoConfig.get().limits(type)[1];
    }

    /**
     * Prend une mise : limites de la machine, jetons suffisants. Envoie le message d'erreur au joueur.
     * @return true si la mise est encaissee (partie serveur : MachinesServeur)
     */
    protected boolean takeBet(ServerPlayer p, long amount) {
        Moteur m = moteur;
        return m != null && m.takeBet(this, p, amount);
    }

    /** Rembourse une mise (partie annulee) : ne compte pas comme un gain (partie serveur : MachinesServeur). */
    protected void refund(ServerLevel level, UUID player, long amount) {
        Moteur m = moteur;
        if (m != null) m.refund(this, level, player, amount);
    }

    /**
     * Verse un gain (en jetons ; au prochain login si le joueur est deconnecte) avec messages, bandeau et annonce
     * (partie serveur : MachinesServeur).
     * @param wager mise correspondante (pour le multiplicateur affiche)
     */
    protected void payout(ServerLevel level, UUID player, String name, long amount, long wager) {
        Moteur m = moteur;
        if (m != null) m.payout(this, level, player, name, amount, wager);
    }

    /**
     * Pas de message de resultat au-dessus de la barre d'objets (gagne, perdu, "tu recuperes...") ni de bandeau de gain
     * par-dessus l'image : la machine affiche deja le resultat a l'ecran (machines a sous). L'annonce aux autres
     * joueurs (tchat) reste.
     */
    protected boolean quietResults() {
        return false;
    }

    public static String mult(float m) {
        if (Math.abs(m - Math.round(m)) < 0.005f) return "x" + Math.round(m);
        return String.format(java.util.Locale.ROOT, "x%.2f", m);
    }

    public static void msg(ServerPlayer p, Component c) {
        p.displayClientMessage(c, true);
    }

    @Nullable
    public ServerPlayer player(UUID id) {
        return level instanceof ServerLevel sl && id != null ? sl.getServer().getPlayerList().getPlayer(id) : null;
    }

    // =================================================================== places des joueurs
    /** Une place (repere du modele, px) : position, regard, sortie, assis ou debout. */
    public record Spot(float x, float y, float z, float lookX, float lookZ, float exitX, float exitZ, boolean sit, float feet, float range) {
    }

    /**
     * Installe le joueur a une place de la machine (chaise, tabouret, place debout) : partie serveur (MachinesServeur).
     * @return false si le joueur est trop loin ou deja pris ailleurs
     */
    protected boolean seat(ServerPlayer p, Spot s) {
        Moteur m = moteur;
        return m != null && m.seat(this, p, s);
    }

    /** Le joueur quitte sa place sur cette machine (s'il en a une) : partie serveur (MachinesServeur). */
    protected void unseat(@Nullable UUID id) {
        Moteur m = moteur;
        if (m != null) m.unseat(this, id);
    }

    /** Vrai pendant que la machine fait elle-meme lever un joueur (le verrou ne s'applique pas). */
    public static boolean allowDismount;

    /**
     * Le joueur est en pleine partie sur cette machine (cartes distribuees, mines payees, duel commence, rouleaux
     * lances...) : il ne peut ni fermer l'ecran ni se lever avant la fin du tour.
     */
    public boolean locks(@Nullable UUID player) {
        return false;
    }

    /**
     * Le joueur a de l'argent en jeu sur cette machine (mise posee, partie en cours, bille en vol, duel ouvert) : tant
     * que c'est le cas, il n'est pas "fauche" pour les videurs du casino.
     */
    public boolean engaged(@Nullable UUID player) {
        return locks(player);
    }

    /** Le joueur est assis a cette machine. */
    public boolean isSeated(@Nullable UUID id) {
        ServerPlayer p = player(id);
        return p != null && p.getVehicle() instanceof fr.heraliz.casino.entity.SeatEntity s && worldPosition.equals(s.machine());
    }

    // =================================================================== NBT : UUID optionnels
    protected static void putUuid(CompoundTag tag, String key, @Nullable UUID id) {
        if (id != null) tag.putUUID(key, id);
    }

    @Nullable
    protected static UUID getUuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }
}
