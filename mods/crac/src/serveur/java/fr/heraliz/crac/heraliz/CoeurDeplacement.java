package fr.heraliz.crac.heraliz;

import com.mojang.brigadier.CommandDispatcher;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.CoeurClanBlock;
import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

/**
 * Déplacer le cœur de clan (docs/SAISON_CLANS.md §2) : {@code /coeur deplacer}, par le chef ou un membre qui gère la
 * base, à {@code 4} blocs du cœur, hors Purge et pas dans l'heure qui la précède, aucune prise en cours ; coûte
 * {@link Purge#deplacementPrix} hérals (50 000) pris dans la banque du clan. Le cœur revient en objet dans l'inventaire
 * et le clan a {@link Purge#deplacementMinutes} minutes (10) pour le reposer dans sa base ; sinon il perd 100 % de ses
 * points de saison. Tant qu'il n'est pas posé, le clan ne gagne rien. L'objet ne se jette pas. Délais gardés avec le monde
 * principal ({@code data/crac_coeurs_deplaces.dat}) : un redémarrage n'efface rien.
 */
@EventBusSubscriber(modid = "crac")
public final class CoeurDeplacement extends SafeSavedData {
    public static final String ID = "crac_coeurs_deplaces";
    private static final double PORTEE = 4.0;

    /** Groupe -> clan et heure limite (ms). */
    private final Map<UUID, UUID> clans = new HashMap<>();
    private final Map<UUID, Long> limites = new HashMap<>();

    public static CoeurDeplacement get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CoeurDeplacement::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new CoeurDeplacement()), ID);
    }

    /** Le cœur du groupe a été reposé : plus de délai. */
    static void repose(MinecraftServer server, UUID groupe) {
        CoeurDeplacement d = get(server);
        if (d.limites.remove(groupe) != null) {
            d.clans.remove(groupe);
            d.setDirty();
        }
    }

    /** Remise à zéro du serveur : plus aucun délai en cours. */
    static void toutOublier(MinecraftServer server) {
        CoeurDeplacement d = get(server);
        d.clans.clear();
        d.limites.clear();
        d.setDirty();
    }

    /** Chaque seconde : délais dépassés. */
    static void seconde(MinecraftServer server) {
        CoeurDeplacement d = get(server);
        if (d.limites.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID g : Map.copyOf(d.limites).keySet()) {
            if (CoeursClans.coeur(server, g) != null) {
                repose(server, g);
                continue;
            }
            if (now < d.limites.get(g)) {
                continue;
            }
            UUID clan = d.clans.get(g);
            d.limites.remove(g);
            d.clans.remove(g);
            d.setDirty();
            if (clan != null) {
                Crac.LOGGER.info("[CRac] cœur du clan [{}] pas reposé à temps : tous ses points de saison sont perdus", PriseCoeur.tag(clan));
                SaisonPont.perdreTout(clan, "cœur pas reposé dans les " + Purge.deplacementMinutes + " minutes");
            }
        }
    }

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("coeur").then(Commands.literal("deplacer").executes(c -> deplacer(c.getSource().getPlayerOrException()))));
    }

    private static int deplacer(ServerPlayer p) {
        MinecraftServer server = p.server;
        UUID clan = PriseCoeur.clanDe(p.getUUID());
        Group g = clan == null ? null : BasesClans.groupe(server, p.getUUID());
        if (clan == null || g == null) {
            return refus(p, Purge.t("crac.coeur.sans_clan", "Il faut un clan pour poser un cœur de clan."));
        }
        if (!BasesClans.peutGerer(p.getUUID())) {
            return refus(p, Purge.t("crac.coeur.pas_droit", "Seuls le chef et les membres qui gèrent la base déplacent le cœur."));
        }
        GlobalPos gp = CoeursClans.coeur(server, g.id());
        if (gp == null) {
            return refus(p, Purge.t("crac.coeur.aucun", "Ton clan n'a pas de cœur posé."));
        }
        if (Purge.deplacementInterdit()) {
            return refus(p, Purge.t("crac.coeur.deplacement_purge", "Le cœur ne se déplace pas pendant la Purge ni dans l'heure qui la précède."));
        }
        if (PriseCoeur.enCours(gp)) {
            return refus(p, Purge.t("crac.coeur.deja_prise", "Une prise est déjà en cours sur ce cœur."));
        }
        ServerLevel level = server.getLevel(gp.dimension());
        if (level == null || p.level() != level || p.position().distanceToSqr(Vec3.atCenterOf(gp.pos())) > PORTEE * PORTEE
            || !(level.getBlockState(gp.pos()).getBlock() instanceof CoeurClanBlock)) {
            return refus(p, Purge.t("crac.coeur.trop_loin", "Mets-toi juste à côté du cœur de ton clan pour le reprendre."));
        }
        if (p.getInventory().getFreeSlot() < 0) {
            return refus(p, Purge.t("crac.coeur.inventaire_plein", "Fais une place dans ton inventaire pour reprendre le cœur."));
        }
        boolean paye;
        try {
            paye = Purge.deplacementPrix <= 0 || fr.heralisz.clans.api.ClansApi.debiterBanque(clan, Purge.deplacementPrix,
                "déplacement du cœur", p.getUUID());
        } catch (LinkageError | RuntimeException ex) {
            paye = false;
        }
        if (!paye) {
            return refus(p, Purge.t("crac.coeur.pas_assez", "Il faut %s hérals dans la banque du clan pour déplacer le cœur.", Purge.deplacementPrix));
        }
        level.removeBlock(gp.pos(), false);
        CoeursClans.oublier(server, g.id());
        ItemStack coeur = new ItemStack(ModItems.COEUR_CLAN.get());
        if (!p.getInventory().add(coeur)) {
            p.drop(coeur, false);
        }
        CoeurDeplacement d = get(server);
        d.clans.put(g.id(), clan);
        d.limites.put(g.id(), System.currentTimeMillis() + Purge.deplacementMinutes * 60_000L);
        d.setDirty();
        Crac.LOGGER.info("[CRac] cœur du clan [{}] repris par {} ({} hérals)", PriseCoeur.tag(clan), p.getGameProfile().getName(), Purge.deplacementPrix);
        PriseCoeur.prevenir(server, clan, Purge.t("crac.coeur.deplace", "%s a repris le cœur du clan : il a %s minutes pour le reposer dans la base, sinon le clan perd TOUS ses points de saison.",
            p.getGameProfile().getName(), Purge.deplacementMinutes).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return 1;
    }

    private static int refus(ServerPlayer p, net.minecraft.network.chat.MutableComponent c) {
        p.sendSystemMessage(c.withStyle(ChatFormatting.RED));
        return 0;
    }

    /** Le cœur ne se jette pas (il revient dans l'inventaire). */
    @SubscribeEvent
    public static void jete(ItemTossEvent e) {
        ItemEntity it = e.getEntity();
        if (it.getItem().is(ModItems.COEUR_CLAN.get()) && !e.getPlayer().isCreative()) {
            e.setCanceled(true);
            if (!e.getPlayer().getInventory().add(it.getItem().copy())) {
                e.setCanceled(false);
            }
        }
    }

    public static CoeurDeplacement load(CompoundTag tag) {
        CoeurDeplacement d = new CoeurDeplacement();
        ListTag l = tag.getList("Coeurs", 10);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.hasUUID("Groupe") && t.hasUUID("Clan")) {
                d.clans.put(t.getUUID("Groupe"), t.getUUID("Clan"));
                d.limites.put(t.getUUID("Groupe"), t.getLong("Limite"));
            }
        }
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Long> e : this.limites.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Groupe", e.getKey());
            UUID c = this.clans.get(e.getKey());
            if (c != null) {
                t.putUUID("Clan", c);
            }
            t.putLong("Limite", e.getValue());
            l.add(t);
        }
        tag.put("Coeurs", l);
        return tag;
    }

    /** Mise à jour des points et rangs affichés sur chaque cœur chargé (toutes les 5 s, bases des clans). */
    static void majScores(MinecraftServer server) {
        if (!SaisonPont.present()) {
            return;
        }
        for (Map.Entry<UUID, GlobalPos> e : CoeursClans.tous(server).entrySet()) {
            ServerLevel level = server.getLevel(e.getValue().dimension());
            if (level == null || !level.isLoaded(e.getValue().pos())) {
                continue;
            }
            if (level.getBlockEntity(e.getValue().pos()) instanceof CoeurClanBlockEntity be) {
                UUID clan = BasesClans.clanDuGroupe(server, e.getKey());
                if (clan == null) {
                    be.majScores(0, new int[4]);
                } else {
                    be.majScores(SaisonPont.points(clan), SaisonPont.rangs(clan));
                }
            }
        }
    }
}
