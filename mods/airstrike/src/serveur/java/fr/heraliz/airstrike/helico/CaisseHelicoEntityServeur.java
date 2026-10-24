package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Partie SERVEUR de {@link CaisseHelicoEntity} : contenu tiré dans la table T6 de HeraliZ Loot (bonus de loot de celui
 * qui l'ouvre le premier, {@code LootApi}), refus tant que l'épave brûle, disparition avec l'épave (objets restants
 * perdus), sauvegarde. Objets toujours déplacés par l'écran de coffre de Minecraft, jamais copiés.
 */
public class CaisseHelicoEntityServeur extends CaisseHelicoEntity {
    private NonNullList<ItemStack> objets = NonNullList.withSize(CASES, ItemStack.EMPTY);
    private String table = "t6";
    private boolean rempli;
    private long fin;

    public CaisseHelicoEntityServeur(EntityType<? extends CaisseHelicoEntity> type, Level level) {
        super(type, level);
    }

    void preparer(String table, long feuFin, long fin) {
        this.table = table;
        this.fin = fin;
        entityData.set(FEU_FIN, feuFin);
    }

    @Override
    protected void tickServeur() {
        if (fin > 0 && level().getGameTime() >= fin) discard();
    }

    /** Clic droit (ou paquet de fouille) : refus si c'est encore chaud, sinon contenu tiré la première fois, puis écran. */
    void ouvrir(ServerPlayer p) {
        if (!isAlive() || p.isSpectator() || p.distanceToSqr(this) > 6 * 6 || p.containerMenu != p.inventoryMenu) return;
        if (brule()) {
            Fouilles.chaud(p);
            return;
        }
        if (!rempli && level() instanceof ServerLevel sl) {
            rempli = true;
            List<ItemStack> l = Ponts.loot(sl, table, p, CASES);
            List<Integer> cases = new ArrayList<>();
            for (int i = 0; i < CASES; i++) cases.add(i);
            Collections.shuffle(cases, new java.util.Random(random.nextLong()));
            for (int i = 0; i < Math.min(l.size(), CASES); i++) objets.set(cases.get(i), l.get(i));
            AirstrikeMod.LOGGER.info("[helico] {} ouvre une caisse de l'hélicoptère ({} objets, table {})",
                    p.getGameProfile().getName(), l.size(), table);
        }
        level().playSound(null, getX(), getY() + 0.5, getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 0.8f, 0.8f);
        p.openMenu(this);
    }

    @Override
    public InteractionResult interact(Player joueur, InteractionHand main) {
        if (joueur instanceof ServerPlayer sp && main == InteractionHand.MAIN_HAND) ouvrir(sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        // « t6 » ou « t7 » : badge du palier de l'écran de fouille de HeraliZ Loot
        return Component.translatable("block.heraliz_loot." + (table.startsWith("t7") ? "t7" : "t6") + "_caisse_helico");
    }

    // ------------------------------------------------------------------------------------------------ contenu
    @Override
    public boolean isEmpty() {
        for (ItemStack s : objets) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return i >= 0 && i < CASES ? objets.get(i) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int i, int n) {
        return ContainerHelper.removeItem(objets, i, n);
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        if (i < 0 || i >= CASES) return ItemStack.EMPTY;
        ItemStack s = objets.get(i);
        objets.set(i, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        if (i >= 0 && i < CASES) objets.set(i, stack);
    }

    @Override
    public void clearContent() {
        objets.clear();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        objets = NonNullList.withSize(CASES, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, objets);
        table = tag.contains("Table") ? tag.getString("Table") : "t6";
        rempli = tag.getBoolean("Rempli");
        fin = tag.getLong("Fin");
        entityData.set(FEU_FIN, tag.getLong("FeuFin"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.saveAllItems(tag, objets);
        tag.putString("Table", table);
        tag.putBoolean("Rempli", rempli);
        tag.putLong("Fin", fin);
        tag.putLong("FeuFin", feuFin());
    }
}
