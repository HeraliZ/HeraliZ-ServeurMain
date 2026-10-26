package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.block.CoeurClanBlock;
import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cœurs de clan (saison des clans, 22/10) : un seul par groupe (base d'un clan), posé dans la zone de son armoire, par
 * un membre. Le staff en créatif le pose où il veut (il prend alors le groupe de la base où il est, s'il y en a une).
 * Gardé avec le monde principal ({@code data/crac_coeurs_clans.dat}) : groupe -> place du cœur. Une place dont le cœur
 * a disparu (commande, nouvelle carte) est oubliée à la pose suivante.
 */
@EventBusSubscriber(modid = "crac")
public final class CoeursClans extends SafeSavedData {
    public static final String ID = "crac_coeurs_clans";
    private final Map<UUID, GlobalPos> coeurs = new HashMap<>();

    public static CoeursClans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CoeursClans::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new CoeursClans()), ID);
    }

    /** Place du cœur du groupe, s'il est toujours là (null sinon ; une place périmée est oubliée). */
    @Nullable
    public static GlobalPos coeur(MinecraftServer server, UUID groupe) {
        CoeursClans d = get(server);
        GlobalPos g = d.coeurs.get(groupe);
        if (g == null) {
            return null;
        }
        ServerLevel l = server.getLevel(g.dimension());
        // chunk pas chargé : on le croit là (on ne charge rien pour ça)
        if (l != null && l.isLoaded(g.pos()) && !(l.getBlockState(g.pos()).getBlock() instanceof CoeurClanBlock)) {
            d.coeurs.remove(groupe);
            d.setDirty();
            return null;
        }
        return l == null ? null : g;
    }

    /** Le cœur du groupe n'est plus posé (repris par {@code /coeur deplacer}, clan dissous). */
    static void oublier(MinecraftServer server, UUID groupe) {
        CoeursClans d = get(server);
        if (d.coeurs.remove(groupe) != null) {
            d.setDirty();
        }
    }

    /** Remise à zéro du serveur ({@code /wipe serveur}) : chaque cœur posé retiré (sans rien faire tomber), liste vidée. */
    static int toutRetirer(MinecraftServer server) {
        CoeursClans d = get(server);
        int n = 0;
        for (GlobalPos g : d.coeurs.values()) {
            ServerLevel l = server.getLevel(g.dimension());
            if (l != null && l.getBlockState(g.pos()).getBlock() instanceof CoeurClanBlock) {
                l.removeBlock(g.pos(), false);
                n++;
            }
        }
        d.coeurs.clear();
        d.setDirty();
        return n;
    }

    /** Copie : groupe -> place de son cœur (places pas vérifiées). */
    static Map<UUID, GlobalPos> tous(MinecraftServer server) {
        return Map.copyOf(get(server).coeurs);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void poser(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getPlacedBlock().getBlock() instanceof CoeurClanBlock) || !(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Claim claim = ClaimData.get(level).getAt(level, e.getPos());
        UUID groupe;
        if (e.getEntity() instanceof ServerPlayer p && !p.isCreative()) {
            Group g = GroupData.get(p.server).getByMember(p.getUUID());
            if (g == null) {
                g = BasesClans.groupe(p.server, p.getUUID());
            }
            if (g == null) {
                refuser(e, p, "crac.coeur.sans_clan", "Il faut un clan pour poser un cœur de clan.");
                return;
            }
            if (!BasesClans.peutGerer(p.getUUID())) {
                refuser(e, p, "crac.coeur.pas_droit_poser", "Seuls le chef et les membres qui gèrent la base posent le cœur.");
                return;
            }
            if (claim == null || !claim.groupId().equals(g.id())) {
                refuser(e, p, "crac.coeur.hors_base", "Le cœur de clan se pose dans la base de ton clan, près de votre armoire.");
                return;
            }
            groupe = g.id();
        } else if (e.getEntity() instanceof ServerPlayer) {
            groupe = claim == null ? null : claim.groupId();   // staff en créatif
        } else {
            e.setCanceled(true);   // jamais posé par une machine
            return;
        }
        if (groupe != null) {
            GlobalPos deja = coeur(level.getServer(), groupe);
            if (deja != null && !(deja.dimension() == level.dimension() && deja.pos().equals(e.getPos()))) {
                if (e.getEntity() instanceof ServerPlayer p) {
                    refuser(e, p, "crac.coeur.deja", "Ton clan a déjà un cœur posé (%s %s %s).", deja.pos().getX(), deja.pos().getY(), deja.pos().getZ());
                } else {
                    e.setCanceled(true);
                }
                return;
            }
            CoeursClans d = get(level.getServer());
            d.coeurs.put(groupe, GlobalPos.of(level.dimension(), e.getPos().immutable()));
            d.setDirty();
            CoeurDeplacement.repose(level.getServer(), groupe);
        }
        if (level.getBlockEntity(e.getPos()) instanceof CoeurClanBlockEntity be) {
            be.setGroupe(groupe);
        }
    }

    private static void refuser(BlockEvent.EntityPlaceEvent e, ServerPlayer p, String cle, String texte, Object... args) {
        e.setCanceled(true);
        p.displayClientMessage(Component.translatableWithFallback(cle, texte, args).withStyle(ChatFormatting.RED), true);
    }

    public static CoeursClans load(CompoundTag tag) {
        CoeursClans d = new CoeursClans();
        ListTag l = tag.getList("Coeurs", 10);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("Monde"));
            if (t.hasUUID("Groupe") && dim != null) {
                ResourceKey<net.minecraft.world.level.Level> cle = ResourceKey.create(Registries.DIMENSION, dim);
                d.coeurs.put(t.getUUID("Groupe"), GlobalPos.of(cle, BlockPos.of(t.getLong("Pos"))));
            }
        }
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, GlobalPos> e : this.coeurs.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Groupe", e.getKey());
            t.putString("Monde", e.getValue().dimension().location().toString());
            t.putLong("Pos", e.getValue().pos().asLong());
            l.add(t);
        }
        tag.put("Coeurs", l);
        return tag;
    }
}
