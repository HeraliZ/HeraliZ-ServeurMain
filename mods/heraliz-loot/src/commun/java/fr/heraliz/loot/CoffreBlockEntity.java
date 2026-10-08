package fr.heraliz.loot;

import java.util.Collections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Coffre T7. Serveur : loot tiré à la première ouverture (27 cases, objets placés au hasard), états fermé → ouverture
 * (animation, puis l'écran s'ouvre) → ouvert → fermeture ; identification : tant que quelqu'un regarde dans le coffre,
 * les objets sont identifiés un par un (ordre de lecture), chacun prend le temps de sa rareté ; si plus personne ne
 * regarde, l'objet en cours repart de zéro. Les objets pas encore identifiés ne quittent jamais le serveur (les joueurs
 * voient un « ? »). Client : moment de l'animation, particules et sons de l'ouverture.
 */
public class CoffreBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TAILLE = 27;
    public static final int FERME = 0, OUVERTURE = 1, OUVERT = 2, FERMETURE = 3, APPARITION = 4, DISPARITION = 5;
    public static final int DUREE_DISPARITION = 26;
    int etat;
    long debut;
    /** Client : moment d'animation au tick précédent (déclencheurs de sons et de particules). */
    public float tPrecedent;

    public CoffreBlockEntity(BlockPos pos, BlockState st) {
        super(HeralizLoot.COFFRE_BE.get(), pos, st);
    }

    public int etat() {
        return etat;
    }

    /** Durée de l'apparition (ticks) : le largage descend du ciel sous son parachute, les autres se matérialisent. */
    public int dureeApparition() {
        return type().nom.equals("t7_largage") ? 90 : 26;
    }

    /** Avancement de l'apparition ou de la disparition (0 à 1). */
    public float avancement(long now, float partiel) {
        float d = etat == APPARITION ? dureeApparition() : DUREE_DISPARITION;
        return Math.max(0, Math.min(1, (now - debut + partiel) / d));
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        int r = getBlockState().getBlock() instanceof CoffreBlock cb ? cb.encombrement().reach() : 1;
        if (etat == APPARITION) return new net.minecraft.world.phys.AABB(worldPosition).inflate(r + 2, 3, r + 2).expandTowards(0, 40, 0);
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(r, 3, r);   // faisceau, panneaux qui tombent
    }

    public TypeCoffre type() {
        return getBlockState().getBlock() instanceof CoffreBlock b ? b.type : TypeCoffre.TOUS.values().iterator().next();
    }

    // ------------------------------------------------------------------------------------------ ouverture / fermeture
    public void ouvrir(ServerPlayer p) {
        // neutre : loot, animation et ecran sont tenus par la version serveur (CoffreBlockEntityServeur)
    }

    /** Case i identifiee (objet visible et prenable) : version serveur ; neutre ici. */
    boolean estIdentifie(int i) {
        return false;
    }

    /** Ticker du serveur (bloc) : la logique est dans la version serveur (tickLogique). */
    public static void tickServeur(Level l, BlockPos pos, BlockState st, CoffreBlockEntity be) {
        be.tickLogique(l, pos, st);
    }

    /** Apparitions, identification, ouverture et fermeture : version serveur ; neutre ici. */
    protected void tickLogique(Level l, BlockPos pos, BlockState st) {
    }

    void fermer(Player p) {
        // neutre : spectateurs tenus par la version serveur
    }

    void synchroniser() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Moment de l'animation (ticks, 0 = fermé, type().ouverture = grand ouvert). */
    public float tempsAnimation(long now, float partiel) {
        float dt = now - debut + partiel;
        int D = type().ouverture;
        return switch (etat) {
            case OUVERTURE -> Math.min(D, Math.max(0, dt));
            case OUVERT -> D;
            case FERMETURE -> Math.max(0, D - dt * D / (float) type().fermeture());
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------------------------ client : sons et particules
    /** Ticker du client (sons et particules de l'animation) : branche par la partie client (client/EffetsCoffre). */
    public static volatile BlockEntityTicker<CoffreBlockEntity> TICK_CLIENT;

    public static void tickClient(Level l, BlockPos pos, BlockState st, CoffreBlockEntity be) {
        BlockEntityTicker<CoffreBlockEntity> tk = TICK_CLIENT;
        if (tk != null) tk.tick(l, pos, st, be);
    }

    // ------------------------------------------------------------------------------------------ écran
    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + HeralizLoot.MODID + "." + type().nom);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        // l'ecran ne s'ouvre que par le serveur (CoffreBlockEntityServeur : menu avec les objets identifies)
        return null;
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        // part commune : synchro de l'animation ; la sauvegarde complete (loot) est lue par la version serveur
        if (t.contains("Etat")) {                                       // synchro de l'animation (client)
            etat = t.getInt("Etat");
            debut = t.getLong("Debut");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();                              // jamais le contenu : seulement l'animation
        t.putInt("Etat", etat);
        t.putLong("Debut", debut);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
