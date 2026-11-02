package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Partie SERVEUR de {@link SupplyCrateEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SupplyCrateEntity(...) » côté serveur.
 */
public class SupplyCrateEntityServeur extends SupplyCrateEntity {
   public SupplyCrateEntityServeur(EntityType<? extends SupplyCrateEntity> type, Level level) {
      super(type, level);
   }

    // ---------------------------------------------------------------- server parts of tick() (original bodies)

    @Override
    void parachuteOuvert() {
        level().playSound(null, getX(), getY(), getZ(), ModSounds.PARACHUTE_OPEN.get(), SoundSource.NEUTRAL, 2.5f, 1.0f);
    }

    @Override
    void chuteServeur() {
        if (fallTicks % 10 == 0) entityData.set(FALL_TICKS, fallTicks);
        if (onGround() || isInWater() || isInLava() || fallTicks > 4000 || getY() < level().getMinBuildHeight()) {
            land();
        }
    }

    @Override
    void poseServeur() {
        horloge(level().getGameTime());
        if (isRemoved()) return;
        if (fillDirty && tickCount % 5 == 0) updateFill();
        long at = getOpenAt();
        if (opener != null && at >= 0 && level().getGameTime() - at >= REVEAL_TICKS) {
            // end of the reveal: the inventory opens for the player who broke the crate open
            Player p = level().getPlayerByUUID(opener);
            opener = null;
            if (p != null && p.isAlive() && p.distanceToSqr(this) < 10 * 10 && p.containerMenu == p.inventoryMenu) {
                ouvrirContenu(p);
            }
        }
    }

    // ---------------------------------------------------------------- fumée et disparition en heure réelle (02/11)
    /**
     * Fin de la fumée et disparition de la caisse posée, en temps de jeu du monde (02/11, audit mods-2 §10) : avant, comptées
     * en ticks de la caisse ; or sous Mohist une caisse posée sans joueur à 16 blocs n'est plus mise à jour : elle fumait et
     * restait pour toujours, alors que le Directeur fermait l'événement à l'heure. Le serveur les contrôle maintenant chaque
     * seconde ({@link #chaqueSeconde}), que la caisse soit mise à jour ou non ; une caisse dans un tronçon déchargé disparaît
     * dès qu'il se recharge. -1 : pas de fin.
     */
    private long fumeeJusqua = -1, disparaitA = -1;
    /** Caisses posées présentes dans un monde chargé. Fil serveur. */
    private static final java.util.Set<SupplyCrateEntityServeur> POSEES =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Fin (temps de jeu) des caisses d'un événement pas encore ouvert, même dans un tronçon déchargé (Largage). */
    private static final java.util.Map<String, Long> FINS_EVENEMENT = new java.util.HashMap<>();

    private void horloge(long maintenant) {
        if (fumeeJusqua >= 0 && maintenant >= fumeeJusqua) {
            fumeeJusqua = -1;
            entityData.set(SMOKING, false);
        }
        if (disparaitA >= 0 && maintenant >= disparaitA) {
            disparaitA = -1;
            discard();
        }
    }

    /** Une fois par seconde (ServerEvents) : fumée et disparition des caisses posées, mises à jour ou non. Rien sans caisse. */
    public static void chaqueSeconde() {
        if (POSEES.isEmpty()) return;
        for (SupplyCrateEntityServeur c : new java.util.ArrayList<>(POSEES)) {
            if (!c.isRemoved()) c.horloge(c.level().getGameTime());
        }
    }

    /** Arrêt du serveur : tout oublier. */
    public static void oublier() {
        POSEES.clear();
        FINS_EVENEMENT.clear();
    }

    /**
     * La caisse de cet événement (« largage#12 ») est-elle encore là, pas ouverte, avant sa disparition ? (HeraliZ
     * Événements : l'événement reste ouvert tant qu'elle existe.)
     */
    public static boolean caisseEnAttente(ServerLevel level, String evenement) {
        Long fin = FINS_EVENEMENT.get(evenement);
        if (fin == null) return false;
        if (level.getGameTime() < fin) return true;
        FINS_EVENEMENT.remove(evenement);
        return false;
    }

    private void suivre() {
        if (level().isClientSide || !isLanded() || isRemoved()) return;
        POSEES.add(this);
        if (evenement != null && disparaitA >= 0) FINS_EVENEMENT.put(evenement, disparaitA);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        suivre();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        POSEES.remove(this);
        RemovalReason r = getRemovalReason();
        if (evenement != null && r != null && r.shouldDestroy()) FINS_EVENEMENT.remove(evenement);
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    @Nullable
    private ResourceLocation lootTable;
    private long lootSeed;
    /** Player who broke the crate open: the inventory opens for him at the end of the animation. */
    @Nullable
    private java.util.UUID opener;
    private boolean fillDirty;
    /** Événement qui a lancé ce largage (« largage#12 »), null sinon (25/10). */
    @Nullable
    private String evenement;

    public void evenement(String id) {
        this.evenement = id;
    }

    private static java.lang.reflect.Method coffreOuvert, objetDuCoffre;
    private static boolean coffreOuvertCherche;

    /**
     * Première ouverture d'une caisse d'événement : l'objet d'événement à revendre est posé dans la caisse, puis le module
     * des événements donne les points et l'annonce ({@code fr.heraliz.evenements.api.EvenementsApi}, par réflexion : sans
     * ce module, rien).
     */
    private void prevenirEvenement(Player player, String evenement) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!coffreOuvertCherche) {
            coffreOuvertCherche = true;
            try {
                Class<?> api = Class.forName("fr.heraliz.evenements.api.EvenementsApi");
                coffreOuvert = api.getMethod("coffreOuvert", net.minecraft.server.level.ServerPlayer.class, String.class);
                objetDuCoffre = api.getMethod("objetDuCoffre", String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                coffreOuvert = null;
                objetDuCoffre = null;
            }
        }
        if (coffreOuvert == null) return;
        try {
            if (objetDuCoffre != null && objetDuCoffre.invoke(null, evenement) instanceof ItemStack obj && !obj.isEmpty()) poser(obj);
            coffreOuvert.invoke(null, sp, evenement);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fr.heraliz.airstrike.AirstrikeMod.LOGGER.warn("[airstrike] événement {} : ouverture non transmise ({})", evenement, e.toString());
        }
    }

    /** Pose l'objet dans la première case vide de la caisse (caisse pleine : à la place du dernier objet). */
    private void poser(ItemStack obj) {
        int libre = items.size() - 1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                libre = i;
                break;
            }
        }
        items.set(libre, obj);
        updateFill();
    }

    public static SupplyCrateEntity create(Level level, Vec3 pos, Vec3 velocity, float yaw) {
        // created by the type (factory): the server version
        SupplyCrateEntity e = ModEntities.SUPPLY_CRATE.get().create(level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setDeltaMovement(velocity);
        e.setYRot(yaw);
        e.yRotO = yaw;
        return e;
    }

    private void land() {
        if (!(level() instanceof ServerLevel server)) return;
        // square up with the world grid so the crate lines up with nearby buildings
        float yaw = Math.round(getYRot() / 90f) * 90f;
        setYRot(yaw);
        yRotO = yaw;
        setDeltaMovement(Vec3.ZERO);
        entityData.set(LANDED, true);
        entityData.set(LANDED_AT, server.getGameTime());
        ResourceLocation rl = ResourceLocation.tryParse(AirstrikeConfig.SUPPLY_LOOT_TABLE.get());
        lootTable = rl;
        lootSeed = random.nextLong();
        int fumee = AirstrikeConfig.SUPPLY_SMOKE_SECONDS.get() * 20, duree = AirstrikeConfig.SUPPLY_DESPAWN_MINUTES.get() * 1200;
        long maintenant = server.getGameTime();
        fumeeJusqua = fumee > 0 ? maintenant + fumee : -1;
        disparaitA = duree > 0 ? maintenant + duree : -1;
        entityData.set(SMOKING, fumee > 0);
        suivre();
        // the crate's own smoke pot takes over from the target marker
        for (EffectCloudEntity marker : server.getEntitiesOfClass(EffectCloudEntity.class, getBoundingBox().inflate(24),
                e -> e.getKind() == EffectCloudEntity.MARKER)) {
            marker.discard();
        }
        server.playSound(null, getX(), getY(), getZ(), ModSounds.CRATE_LAND.get(), SoundSource.BLOCKS, 2.5f, 0.8f);
        Network.sendImpact(server, new ImpactPacket(ImpactPacket.CRATE_LAND, getX(), getY(), getZ(), 1.0f));
    }

    private void updateFill() {
        fillDirty = false;
        int n = 0;
        for (ItemStack st : items) if (!st.isEmpty()) n++;
        entityData.set(FILL, n);
        if (n > entityData.get(FILL_MAX)) entityData.set(FILL_MAX, n);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Container

    private void unpackLoot(@Nullable Player player) {
        if (lootTable == null || !(level() instanceof ServerLevel server)) return;
        LootTable table = server.getServer().getLootData().getLootTable(lootTable);
        lootTable = null;
        LootParams.Builder params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position());
        if (player != null) params.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        // 26/10 (demande du propriétaire : « trop de munitions par 1 dans les largages ») : chaque lot tiré garde sa
        // quantité, posé entier dans une case libre au hasard. Avant : LootTable.fill de Minecraft, qui coupe les tas en
        // petits morceaux pour remplir les cases vides (30 balles devenaient 1 + 4 + 2 + ...).
        java.util.List<ItemStack> lots = table.getRandomItems(params.create(LootContextParamSets.CHEST), lootSeed);
        java.util.List<Integer> libres = new java.util.ArrayList<>();
        for (int i = 0; i < SLOTS; i++) if (items.get(i).isEmpty()) libres.add(i);
        java.util.Collections.shuffle(libres, new java.util.Random(lootSeed == 0L ? random.nextLong() : lootSeed));
        int k = 0;
        for (ItemStack st : lots) {
            while (!st.isEmpty() && k < libres.size()) {
                ItemStack part = st.split(st.getMaxStackSize());
                items.set(libres.get(k++), part);
            }
        }
        setChanged();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isLanded()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            long at = getOpenAt();
            if (at < 0) {
                // first use: break the crate open, the inventory follows at the end of the animation
                unpackLoot(player);
                updateFill();
                entityData.set(OPEN_AT, level().getGameTime());
                opener = player.getUUID();
                level().playSound(null, getX(), getY() + 1.2, getZ(), ModSounds.CRATE_REVEAL.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
                prevenirSiJoueur(player);
            } else if (level().getGameTime() - at >= REVEAL_TICKS) {
                prevenirSiJoueur(player);
                ouvrirContenu(player);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /**
     * Première ouverture par un joueur en survie : l'événement est prévenu (points, objet d'événement), une seule fois. Un
     * membre du staff en créatif ou spectateur ouvre la caisse sans prendre les points (02/11, audit mods-2 §14 ; avant,
     * les points allaient à son clan et le largage était noté récompensé) : le premier joueur en survie qui l'ouvre ensuite
     * les prend.
     */
    private void prevenirSiJoueur(Player player) {
        if (evenement == null || player.isCreative() || player.isSpectator()) return;
        String ev = evenement;
        evenement = null;   // une seule fois, même si la caisse est rechargée
        FINS_EVENEMENT.remove(ev);
        prevenirEvenement(player, ev);
    }

    private static java.lang.reflect.Method revelation;
    private static boolean revelationCherchee;

    /**
     * Le contenu s'ouvre comme un coffre T7 de HeraliZ Loot (25/10, demande du propriétaire, pour le suspense) : objets
     * cachés « ? » et découverts un par un ({@code fr.heraliz.loot.RevelationCaisses.ouvrir}, par réflexion) ; sans ce mod,
     * ou s'il refuse, coffre ordinaire comme avant.
     */
    private void ouvrirContenu(Player player) {
        unpackLoot(player);
        if (!revelationCherchee) {
            revelationCherchee = true;
            try {
                revelation = Class.forName("fr.heraliz.loot.RevelationCaisses").getMethod("ouvrir",
                        net.minecraft.server.level.ServerPlayer.class, net.minecraft.world.entity.Entity.class, net.minecraft.world.Container.class,
                        net.minecraft.network.chat.Component.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                revelation = null;
            }
        }
        if (revelation != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            try {
                if ((boolean) revelation.invoke(null, sp, this, this, getDisplayName())) return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                fr.heraliz.airstrike.AirstrikeMod.LOGGER.warn("[airstrike] caisse : révélation impossible ({}), coffre ordinaire", e.toString());
            }
        }
        player.openMenu(this);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        unpackLoot(player);
        return new MenuCaisse(id, inventory, this);
    }

    /**
     * Caisse ouverte comme un coffre ordinaire (sans la découverte de HeraliZ Loot) : on prend, on ne dépose rien (02/11,
     * audit n° 8 : la caisse disparaît avec ce qu'elle contient ; un objet rangé dedans, objets à vie compris, était
     * perdu). Comme la découverte des coffres T7, qui refuse déjà tout dépôt.
     */
    private static final class MenuCaisse extends ChestMenu {
        MenuCaisse(int id, Inventory inv, net.minecraft.world.Container c) {
            super(net.minecraft.world.inventory.MenuType.GENERIC_9x3, id, inv, c, 3);
            for (int i = 0; i < SLOTS; i++) {
                net.minecraft.world.inventory.Slot ancien = slots.get(i);
                net.minecraft.world.inventory.Slot nouveau = new net.minecraft.world.inventory.Slot(c, i, ancien.x, ancien.y) {
                    @Override
                    public boolean mayPlace(ItemStack s) {
                        return false;
                    }
                };
                nouveau.index = ancien.index;
                slots.set(i, nouveau);
            }
        }

        /** Maj + clic : de la caisse vers l'inventaire seulement (Minecraft complète les piles sans demander aux cases). */
        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            return index >= SLOTS ? ItemStack.EMPTY : super.quickMoveStack(p, index);
        }
    }

    @Override
    public boolean isEmpty() {
        unpackLoot(null);
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        unpackLoot(null);
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        unpackLoot(null);
        return ContainerHelper.removeItem(items, slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        unpackLoot(null);
        ItemStack s = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        unpackLoot(null);
        if (slot >= 0 && slot < SLOTS) items.set(slot, stack);
    }

    @Override
    public void setChanged() {
        fillDirty = true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // only creative players can remove a crate (attack it)
        if (!level().isClientSide && source.getEntity() instanceof Player p && p.getAbilities().instabuild) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        fallTicks = tag.getInt("FallTicks");
        entityData.set(FALL_TICKS, fallTicks);
        entityData.set(LANDED, tag.getBoolean("Landed"));
        entityData.set(LANDED_AT, tag.contains("LandedAt") ? tag.getLong("LandedAt") : level().getGameTime() - 100000L);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        lootTable = tag.contains("LootTable") ? ResourceLocation.tryParse(tag.getString("LootTable")) : null;
        lootSeed = tag.getLong("LootSeed");
        // fins en temps de jeu (02/11) ; une caisse enregistrée avant gardait des ticks restants : convertis
        long maintenant = level().getGameTime();
        fumeeJusqua = tag.contains("FumeeJusqua") ? tag.getLong("FumeeJusqua")
                : tag.getInt("SmokeTicks") > 0 ? maintenant + tag.getInt("SmokeTicks") : -1;
        disparaitA = tag.contains("DisparaitA") ? tag.getLong("DisparaitA")
                : tag.getInt("DespawnTicks") > 0 ? maintenant + tag.getInt("DespawnTicks") : -1;
        entityData.set(SMOKING, fumeeJusqua >= 0 && maintenant < fumeeJusqua);
        entityData.set(OPEN_AT, tag.contains("OpenAt") ? tag.getLong("OpenAt") : -1L);
        entityData.set(FILL_MAX, tag.getInt("FillMax"));
        evenement = tag.contains("Evenement") ? tag.getString("Evenement") : null;
        fillDirty = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("FallTicks", fallTicks);
        tag.putBoolean("Landed", isLanded());
        tag.putLong("LandedAt", entityData.get(LANDED_AT));
        ContainerHelper.saveAllItems(tag, items);
        if (lootTable != null) {
            tag.putString("LootTable", lootTable.toString());
            tag.putLong("LootSeed", lootSeed);
        }
        tag.putLong("FumeeJusqua", fumeeJusqua);
        tag.putLong("DisparaitA", disparaitA);
        if (getOpenAt() >= 0) tag.putLong("OpenAt", getOpenAt());
        tag.putInt("FillMax", entityData.get(FILL_MAX));
        if (evenement != null) tag.putString("Evenement", evenement);
    }
}
