package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.CoeurClanBlock;
import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Prise du cœur de clan pendant la Purge (docs/SAISON_CLANS.md §2) : un membre d'un clan ennemi fait clic droit sur le
 * cœur, puis son clan le tient {@link Purge#priseSecondes} secondes (90) : au moins un de ses membres à
 * {@link Purge#priseDistance} blocs (3) au plus, vivant, pas à terre, pas en créatif ni spectateur. Personne pour le tenir :
 * la prise repart de zéro. Une barre est montrée aux deux clans. Réussite : {@code SaisonApi.raid} (-15 % pour le clan
 * pris, +10 % pour l'attaquant s'il a son cœur, une fois par clan pris et par Purge). Une prise commencée va jusqu'au bout
 * même si la Purge finit. Toutes les règles sont vérifiées ici, au serveur. Clic droit d'un membre du clan : ses points.
 */
@EventBusSubscriber(modid = "crac")
public final class PriseCoeur {
    private PriseCoeur() {
    }

    /** Une prise en cours, par cœur. */
    static final class Prise {
        final GlobalPos coeur;
        final UUID groupe, clanPris, clanAttaquant;
        final String purge;
        final String joueur;
        int ticks;
        final ServerBossEvent barre;

        Prise(GlobalPos coeur, UUID groupe, UUID clanPris, UUID clanAttaquant, String purge, String joueur) {
            this.coeur = coeur;
            this.groupe = groupe;
            this.clanPris = clanPris;
            this.clanAttaquant = clanAttaquant;
            this.purge = purge;
            this.joueur = joueur;
            this.barre = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static final Map<GlobalPos, Prise> PRISES = new HashMap<>();
    /** « purge|clan pris » déjà pris (en plus du contrôle de la saison) : pas de deuxième prise. */
    private static final Set<String> FAITES = new HashSet<>();

    /** Une prise est en cours sur ce cœur (on ne le déplace pas). */
    public static boolean enCours(GlobalPos coeur) {
        return PRISES.containsKey(coeur);
    }

    /** Le cœur est incassable en survie ; jamais cassé par un joueur sans le créatif. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void casse(BlockEvent.BreakEvent e) {
        if (e.getState().getBlock() instanceof CoeurClanBlock && !e.getPlayer().isCreative()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)
            || !(level.getBlockState(e.getPos()).getBlock() instanceof CoeurClanBlock)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.CONSUME);
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        UUID groupe = level.getBlockEntity(e.getPos()) instanceof CoeurClanBlockEntity be ? be.groupe() : null;
        MinecraftServer server = level.getServer();
        UUID clanPris = BasesClans.clanDuGroupe(server, groupe);
        if (clanPris == null) {
            barre(p, Purge.t("crac.coeur.orphelin", "Ce cœur n'appartient à aucun clan."), ChatFormatting.GRAY);
            return;
        }
        UUID monClan = clanDe(p.getUUID());
        if (clanPris.equals(monClan)) {
            int[] r = SaisonPont.rangs(clanPris);
            barre(p, Purge.t("crac.coeur.infos", "Cœur du clan : %s points · général %s · /saison pour le détail",
                SaisonPont.points(clanPris), r.length > 3 && r[3] > 0 ? "#" + r[3] : "-"), ChatFormatting.AQUA);
            return;
        }
        // pas de prise à travers un mur (contrôle de vue de CRac, ClaimProtectionHandler, passé avant)
        if (e.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY) {
            return;
        }
        commencer(p, level, e.getPos(), groupe, clanPris, monClan);
    }

    private static void commencer(ServerPlayer p, ServerLevel level, BlockPos pos, UUID groupe, UUID clanPris, @Nullable UUID monClan) {
        MinecraftServer server = level.getServer();
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        if (!Purge.systeme() || !SaisonPont.active()) {
            barre(p, Purge.t("crac.coeur.pas_saison", "Pas de saison des clans en cours : le cœur ne se prend pas."), ChatFormatting.RED);
            return;
        }
        if (PRISES.containsKey(gp)) {
            barre(p, Purge.t("crac.coeur.deja_prise", "Une prise est déjà en cours sur ce cœur."), ChatFormatting.YELLOW);
            return;
        }
        Purge.Fenetre f = Purge.enCours();
        if (f == null) {
            barre(p, Purge.t("crac.coeur.hors_purge", "Le cœur ne se prend que pendant la Purge (/purge)."), ChatFormatting.RED);
            return;
        }
        if (monClan == null) {
            barre(p, Purge.t("crac.coeur.prise_sans_clan", "Il faut être dans un clan pour prendre un cœur."), ChatFormatting.RED);
            return;
        }
        if (p.isCreative() || p.isSpectator() || SaisonPont.aTerre(p) || !p.isAlive()) {
            return;
        }
        if (Purge.baseRecente(server, groupe, f)) {
            barre(p, Purge.t("crac.coeur.base_recente", "Cette base est trop récente : elle n'est pas raidable pendant cette Purge."), ChatFormatting.RED);
            return;
        }
        String cle = f.id() + "|" + clanPris;
        if (FAITES.contains(cle)) {
            barre(p, Purge.t("crac.coeur.deja_pris", "Ce cœur a déjà été pris pendant cette Purge."), ChatFormatting.RED);
            return;
        }
        if (!aPortee(p, gp)) {
            return;
        }
        Prise prise = new Prise(gp, groupe, clanPris, monClan, f.id(), p.getGameProfile().getName());
        PRISES.put(gp, prise);
        String tagA = tag(monClan), tagP = tag(clanPris);
        Crac.LOGGER.info("[CRac] prise du cœur de [{}] commencée par {} [{}] ({})", tagP, prise.joueur, tagA, f.id());
        prevenir(server, clanPris, Purge.t("crac.coeur.alerte", "⚠ [%s] prend le cœur de votre clan ! Défendez-le (%s s).", tagA, Purge.priseSecondes)
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        prevenir(server, monClan, Purge.t("crac.coeur.debut", "Prise du cœur de [%s] : restez à %s blocs du cœur pendant %s s.", tagP,
            (int) Purge.priseDistance, Purge.priseSecondes).withStyle(ChatFormatting.GOLD));
    }

    /** Chaque tick : avance des prises. */
    static void tick(MinecraftServer server) {
        if (PRISES.isEmpty()) {
            return;
        }
        Iterator<Prise> it = PRISES.values().iterator();
        while (it.hasNext()) {
            Prise pr = it.next();
            ServerLevel level = server.getLevel(pr.coeur.dimension());
            boolean coeurLa = level != null && level.isLoaded(pr.coeur.pos())
                && level.getBlockState(pr.coeur.pos()).getBlock() instanceof CoeurClanBlock;
            boolean tenu = coeurLa && tenu(server, pr);
            if (!tenu) {
                fin(server, pr, false);
                it.remove();
                continue;
            }
            pr.ticks++;
            int total = Purge.priseSecondes * 20;
            if (pr.ticks % 10 == 0 || pr.ticks >= total) {
                majBarre(server, pr, total);
            }
            if (pr.ticks >= total) {
                fin(server, pr, true);
                it.remove();
            }
        }
    }

    /** Au moins un membre du clan attaquant tient le cœur (portée, vivant, pas à terre, en survie). */
    private static boolean tenu(MinecraftServer server, Prise pr) {
        for (UUID m : membres(pr.clanAttaquant)) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && !SaisonPont.aTerre(p) && aPortee(p, pr.coeur)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aPortee(ServerPlayer p, GlobalPos gp) {
        if (p.level().dimension() != gp.dimension()) {
            return false;
        }
        double d = Purge.priseDistance + 0.5;
        return p.position().distanceToSqr(Vec3.atCenterOf(gp.pos())) <= d * d;
    }

    private static void majBarre(MinecraftServer server, Prise pr, int total) {
        pr.barre.setProgress(Math.min(1f, pr.ticks / (float) total));
        pr.barre.setName(Purge.t("crac.coeur.barre", "Prise du cœur de [%s] par [%s] · %s s", tag(pr.clanPris), tag(pr.clanAttaquant),
            Math.max(0, (total - pr.ticks + 19) / 20)).withStyle(ChatFormatting.YELLOW));
        for (UUID clan : new UUID[]{pr.clanPris, pr.clanAttaquant}) {
            for (UUID m : membres(clan)) {
                ServerPlayer p = server.getPlayerList().getPlayer(m);
                if (p != null && !pr.barre.getPlayers().contains(p)) {
                    pr.barre.addPlayer(p);
                }
            }
        }
    }

    private static void fin(MinecraftServer server, Prise pr, boolean reussie) {
        pr.barre.removeAllPlayers();
        String tagA = tag(pr.clanAttaquant), tagP = tag(pr.clanPris);
        if (!reussie) {
            Crac.LOGGER.info("[CRac] prise du cœur de [{}] par [{}] ratée", tagP, tagA);
            prevenir(server, pr.clanAttaquant, Purge.t("crac.coeur.ratee", "Prise du cœur de [%s] ratée : personne ne le tenait.", tagP)
                .withStyle(ChatFormatting.RED));
            prevenir(server, pr.clanPris, Purge.t("crac.coeur.defendu", "Le cœur de votre clan a été défendu.").withStyle(ChatFormatting.GREEN));
            return;
        }
        FAITES.add(pr.purge + "|" + pr.clanPris);
        boolean ok = SaisonPont.raid(pr.clanAttaquant, pr.clanPris, pr.purge, pr.joueur);
        Crac.LOGGER.info("[CRac] cœur de [{}] pris par [{}] ({}) : {}", tagP, tagA, pr.purge, ok ? "points volés" : "déjà pris, rien");
        if (!ok) {
            prevenir(server, pr.clanAttaquant, Purge.t("crac.coeur.deja_pris", "Ce cœur a déjà été pris pendant cette Purge.")
                .withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------------------------------------- clans
    @Nullable
    static UUID clanDe(UUID joueur) {
        if (!BasesClans.actif()) {
            return null;
        }
        try {
            return fr.heralisz.clans.api.ClansApi.clanDe(joueur).orElse(null);
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    static Set<UUID> membres(UUID clan) {
        try {
            Optional<fr.heralisz.clans.api.ClanInfo> i = fr.heralisz.clans.api.ClansApi.infos(clan);
            return i.isPresent() ? i.get().membres().keySet() : Set.of();
        } catch (LinkageError | RuntimeException e) {
            return Set.of();
        }
    }

    static String tag(UUID clan) {
        try {
            return fr.heralisz.clans.api.ClansApi.infos(clan).map(fr.heralisz.clans.api.ClanInfo::tag).orElse("?");
        } catch (LinkageError | RuntimeException e) {
            return "?";
        }
    }

    static void prevenir(MinecraftServer server, UUID clan, Component c) {
        for (UUID m : membres(clan)) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                p.sendSystemMessage(c);
            }
        }
    }

    static void barre(ServerPlayer p, net.minecraft.network.chat.MutableComponent c, ChatFormatting couleur) {
        p.displayClientMessage(c.withStyle(couleur), true);
    }
}
