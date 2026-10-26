package fr.heraliz.corps.entite;

import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.registre.Registre;
import fr.heraliz.corps.registre.Registres;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link AncienCorps} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new AncienCorps(...) » côté serveur.
 */
public class AncienCorpsServeur extends AncienCorps {
    public AncienCorpsServeur(EntityType<? extends AncienCorps> type, Level level) {
        super(type, level);
    }

    private CompoundTag mort = new CompoundTag();
    /** Conversion ratee : l'ancien corps est garde tel quel (rien n'est perdu), sans nouvel essai avant le redemarrage. */
    private boolean echec;

    @Override
    public void tick() {
        if (level().isClientSide || isRemoved() || echec) return;
        if (level() instanceof ServerLevel sl) {
            try {
                convertir(sl);
            } catch (RuntimeException e) {
                echec = true;
                HeralizCorps.LOG.error("[corps] conversion d'un ancien corps impossible (garde tel quel)", e);
                return;
            }
        }
        discard();
    }

    private void convertir(ServerLevel level) {
        ItemStack[] inv = lire(mort, "MainInventory", 36);
        ItemStack[] armure = lire(mort, "ArmorInventory", 4);
        ItemStack[] gauche = lire(mort, "OffHandInventory", 1);
        List<ItemStack> autres = new ArrayList<>();
        ListTag l = mort.getList("Items", Tag.TAG_COMPOUND);
        ItemStack sac = ItemStack.EMPTY;
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i));
            if (s.isEmpty()) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (sac.isEmpty() && id != null && id.getNamespace().equals("backpacked")) sac = s;
            else autres.add(s);
        }
        CorpsContenu c = new CorpsContenu(0, autres.size());
        int n = 0;
        for (int i = 0; i < 36; i++) if (!inv[i].isEmpty()) { c.poser(CorpsContenu.INVENTAIRE + i, inv[i]); n++; }
        for (int i = 0; i < 4; i++) if (!armure[i].isEmpty()) { c.poser(CorpsContenu.ARMURE + i, armure[i]); n++; }
        if (!gauche[0].isEmpty()) { c.poser(CorpsContenu.MAIN_GAUCHE, gauche[0]); n++; }
        if (!sac.isEmpty()) { c.poser(CorpsContenu.SAC, sac); n++; }
        for (int i = 0; i < autres.size(); i++) c.poser(c.debutAutres() + i, autres.get(i));
        n += autres.size();
        String nom = mort.getString("PlayerName");
        if (n == 0) {
            HeralizCorps.LOG.info("[corps] ancien corps vide de {} retire", nom);
            return;
        }
        UUID proprietaire = mort.hasUUID("PlayerUuid") ? mort.getUUID("PlayerUuid") : new UUID(mort.getLong("PlayerUuidMost"), mort.getLong("PlayerUuidLeast"));
        if (!(Registres.CORPS.get().create(level) instanceof CorpsEntityServeur corps)) throw new IllegalStateException("type de corps absent");
        corps.moveTo(getX(), getY(), getZ(), getYRot(), 0);
        ServerPlayer enLigne = level.getServer().getPlayerList().getPlayer(proprietaire);
        if (enLigne != null) corps.preparer(enLigne, c, Component.empty(), 0);
        else corps.preparerSansJoueur(proprietaire, nom, c, mort.getLong("Timestamp"));
        if (!level.addFreshEntity(corps)) throw new IllegalStateException("le monde refuse le corps");
        Registre.Fiche f = Registre.de(level.getServer()).ajouter(corps);
        HeralizCorps.LOG.info("[corps] ancien corps de {} converti en {} {} {} : corps n{} ({} objet(s))", nom, (int) getX(), (int) getY(), (int) getZ(),
                f.numero, n);
    }

    private static ItemStack[] lire(CompoundTag t, String nom, int taille) {
        ItemStack[] r = new ItemStack[taille];
        java.util.Arrays.fill(r, ItemStack.EMPTY);
        ListTag l = t.getList(nom, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag o = l.getCompound(i);
            int j = o.getInt("Slot");
            if (j >= 0 && j < taille) r[j] = ItemStack.of(o);
        }
        return r;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        mort = t.getCompound("Death");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.put("Death", mort);
    }
}
