package fr.heraliz.crac.bases;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.claim.ClaimUpkeep;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.network.C2SAccesBase;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CAccesBase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

/**
 * Onglets Accès et Base de l'armoire à outils (CRac 1.10.0, écran validé le 18/10). Tout est revérifié ici : armoire à
 * moins de 6 blocs, ouverte seulement par qui a l'accès ; la liste ne se change que par le propriétaire du groupe (ou
 * en créatif). Recherche : joueurs connectés dont le pseudo contient le texte, plus le joueur déconnecté qui porte
 * exactement ce pseudo (déjà venu sur le serveur).
 */
@EventBusSubscriber(modid = "crac")
public final class AccesBaseTraitement {
    private static final double PORTEE = 6.0;
    private static final int TROUVES_MAX = 8;

    private AccesBaseTraitement() {
    }

    public static void traiter(C2SAccesBase m, Supplier<Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p != null) {
                try {
                    traiter(p, m);
                } catch (RuntimeException e) {
                    Crac.LOGGER.error("[CRac] accès de la base : paquet de {} refusé", p.getGameProfile().getName(), e);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void traiter(ServerPlayer p, C2SAccesBase m) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = m.pos();
        if (!level.isLoaded(pos) || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > PORTEE * PORTEE) {
            return;
        }
        Claim claim = ClaimManager.getByAnchor(level, pos);
        Group g = claim == null ? null : GroupData.get(p.server).getById(claim.groupId());
        boolean createur = p.getAbilities().instabuild;
        if (g == null || !createur && !ClanAccess.allows(g, p.getUUID(), ClanAccess.Action.CHESTS)) {
            return;
        }
        boolean gerer = createur || g.isOwner(p.getUUID());
        AccesBase acces = AccesBase.get(p.server);
        String recherche = "";
        switch (m.action()) {
            case ENTRETIEN -> {
                if (!Tutoriel.entretienTropTot(p) && level.getBlockEntity(pos) instanceof ClaimBlockEntity be) {
                    ClaimUpkeep.recompute(level, claim, be);
                    NetworkHooks.openScreen(p, be, buf -> buf.writeBlockPos(pos));
                }
                return;
            }
            case OUVRIR -> p.closeContainer();
            case CHERCHER -> recherche = propre(m.texte());
            case DONNER -> {
                if (gerer && m.cible() != null) {
                    donner(p, g, acces, m.cible());
                }
                recherche = propre(m.texte());
            }
            case RETIRER -> {
                if (gerer && m.cible() != null && acces.retirer(g.id(), m.cible())) {
                    String nom = nom(p.server, m.cible());
                    T.barre(p, T.t("crac.acces.retire", "Accès retiré à %s.", nom), ChatFormatting.YELLOW);
                    ServerPlayer cible = p.server.getPlayerList().getPlayer(m.cible());
                    if (cible != null) {
                        T.chat(cible, T.t("crac.acces.retire_cible", "%s t'a retiré l'accès à sa base.", p.getGameProfile().getName()), ChatFormatting.YELLOW);
                        C2SRequestNearbyClaimsTraitement.sendToPlayer(cible.serverLevel(), cible);
                    }
                    Crac.LOGGER.info("[CRac] {} retire l'accès de la base {} à {}", p.getGameProfile().getName(), g.name(), nom);
                }
                recherche = propre(m.texte());
            }
        }
        envoyer(p, pos, claim, g, gerer, recherche);
    }

    private static void donner(ServerPlayer p, Group g, AccesBase acces, UUID cible) {
        MinecraftServer server = p.server;
        ServerPlayer enLigne = server.getPlayerList().getPlayer(cible);
        String nom = enLigne != null ? enLigne.getGameProfile().getName() : null;
        if (nom == null && server.getProfileCache() != null) {
            nom = server.getProfileCache().get(cible).map(GameProfile::getName).orElse(null);
        }
        if (nom == null || g.isMember(cible)) {
            return;   // joueur jamais venu, ou déjà de la base
        }
        if (!acces.donner(g.id(), cible, nom)) {
            if (acces.de(g.id()).size() >= AccesBase.MAX) {
                T.barre(p, T.t("crac.acces.plein", "La liste est pleine (%s joueurs).", String.valueOf(AccesBase.MAX)), ChatFormatting.RED);
            }
            return;
        }
        T.barre(p, T.t("crac.acces.donne", "%s a maintenant l'accès à ta base.", nom), ChatFormatting.GREEN);
        if (enLigne != null) {
            T.chat(enLigne, T.t("crac.acces.donne_cible", "%s t'a donné l'accès à sa base : portes, coffres et construction.", p.getGameProfile().getName()), ChatFormatting.GREEN);
            C2SRequestNearbyClaimsTraitement.sendToPlayer(enLigne.serverLevel(), enLigne);
        }
        Crac.LOGGER.info("[CRac] {} donne l'accès de la base {} à {}", p.getGameProfile().getName(), g.name(), nom);
    }

    private static void envoyer(ServerPlayer p, BlockPos pos, Claim claim, Group g, boolean gerer, String recherche) {
        MinecraftServer server = p.server;
        List<S2CAccesBase.Ligne> ont = new ArrayList<>();
        ont.add(ligne(server, g.owner(), S2CAccesBase.PROPRIETAIRE));
        for (UUID u : g.members()) {
            if (!u.equals(g.owner())) {
                ont.add(ligne(server, u, S2CAccesBase.GROUPE));
            }
        }
        Map<UUID, String> donnes = AccesBase.get(server).de(g.id());
        for (Map.Entry<UUID, String> e : donnes.entrySet()) {
            ont.add(new S2CAccesBase.Ligne(e.getKey(), e.getValue(), S2CAccesBase.ACCES, server.getPlayerList().getPlayer(e.getKey()) != null));
        }
        List<S2CAccesBase.Ligne> trouves = new ArrayList<>();
        if (gerer && !recherche.isEmpty()) {
            String q = recherche.toLowerCase(Locale.ROOT);
            List<ServerPlayer> joueurs = new ArrayList<>(server.getPlayerList().getPlayers());
            joueurs.sort(Comparator.comparing((ServerPlayer x) -> !x.getGameProfile().getName().toLowerCase(Locale.ROOT).startsWith(q))
                .thenComparing(x -> x.getGameProfile().getName().toLowerCase(Locale.ROOT)));
            for (ServerPlayer x : joueurs) {
                String n = x.getGameProfile().getName();
                if (trouves.size() < TROUVES_MAX && n.toLowerCase(Locale.ROOT).contains(q) && !g.isMember(x.getUUID()) && !donnes.containsKey(x.getUUID())) {
                    trouves.add(new S2CAccesBase.Ligne(x.getUUID(), n, S2CAccesBase.TROUVE, true));
                }
            }
            // un joueur déconnecté : seulement par son pseudo exact
            if (recherche.length() >= 3 && server.getProfileCache() != null && server.getPlayerList().getPlayerByName(recherche) == null) {
                Optional<GameProfile> hors = server.getProfileCache().get(recherche);
                if (hors.isPresent() && hors.get().getId() != null && !g.isMember(hors.get().getId()) && !donnes.containsKey(hors.get().getId())) {
                    trouves.add(new S2CAccesBase.Ligne(hors.get().getId(), hors.get().getName(), S2CAccesBase.TROUVE, false));
                }
            }
        }
        long reste = Protections.groupeProtege(server, g.id()) ? Math.max(0L, BasesDonnees.get(server).finProtection(g.id()) - System.currentTimeMillis()) : 0L;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
            new S2CAccesBase(pos, g.name(), claim.tier(), claim.radius(), reste, gerer, ont, trouves, recherche));
    }

    private static S2CAccesBase.Ligne ligne(MinecraftServer server, UUID id, int role) {
        return new S2CAccesBase.Ligne(id, nom(server, id), role, server.getPlayerList().getPlayer(id) != null);
    }

    private static String nom(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            return p.getGameProfile().getName();
        }
        String connu = server.getProfileCache() == null ? null : server.getProfileCache().get(id).map(GameProfile::getName).orElse(null);
        return connu != null ? connu : id.toString().substring(0, 8);
    }

    /** Pseudo de Minecraft : lettres, chiffres et _ seulement, 16 au plus. */
    private static String propre(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (b.length() < 16 && (c == '_' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z')) {
                b.append(c);
            }
        }
        return b.toString();
    }

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            AccesBase.get(p.server).renommer(p.getUUID(), p.getGameProfile().getName());
        }
    }
}
