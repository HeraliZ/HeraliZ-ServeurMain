package fr.heraliz.crac.heraliz;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Une base par clan (CRac 1.16.0, demande du propriétaire du 21/10) : sans clan, pas de base. Chaque clan qui pose une
 * armoire a son groupe CRac, reflet exact du clan : mêmes membres, son chef pour propriétaire, tenu à jour toutes les
 * 5 secondes ({@link #seconde}) ; un groupe ne peut tenir qu'un claim, donc une seule base par clan. Personne hors du
 * clan n'y a accès ; dans le clan, les droits des grades et des joueurs (BASE_ENTRER, BASE_CONSTRUIRE…) décident
 * ({@link ClanAccess#allows}). Un joueur qui quitte le clan perd l'accès tout de suite. Clan dissous : l'armoire disparaît
 * et le claim est libéré : la base n'appartient plus à personne, tout le monde ouvre ses portes, la pille, et peut y
 * poser sa propre armoire. Gardé avec le monde principal ({@code data/crac_bases_clans.dat}). Fil du serveur seulement ;
 * HeraliZ Clans n'est touché que par {@link Pont}, et seulement s'il est là.
 */
public final class BasesClans extends SafeSavedData {
    public static final String ID = "crac_bases_clans";

    /** Clan -> son groupe CRac. */
    private final Map<UUID, UUID> groupes = new HashMap<>();
    /** Clans déjà vus (création d'un clan : tutoriel des bases pour son chef). */
    private final Set<UUID> vus = new HashSet<>();
    /** Clans qui ont reçu le matériel de leur première base dans leur coffre (CRac 1.17.0). */
    private final Set<UUID> kits = new HashSet<>();
    /** Clans en attente de ce matériel : heure (ms) où on les a vus sans lui (30 s pour que le tutoriel démarre). */
    private final Map<UUID, Long> attenteKit = new HashMap<>();
    /** Ce qui n'est pas entré dans le coffre du clan (plein) : redéposé plus tard, jamais perdu (1.21.0). */
    private final Map<UUID, java.util.List<net.minecraft.world.item.ItemStack>> resteKit = new HashMap<>();
    /**
     * Clans dont le chef n'était pas connecté quand leur matériel de première base a été rangé (25/10) : il est prévenu à
     * sa prochaine connexion (avant, personne ne lui disait que le matériel l'attendait dans le coffre du clan).
     */
    private final Set<UUID> kitAPrevenir = new HashSet<>();
    /** Clans dont le chef a déjà été prévenu que le coffre est plein (depuis le démarrage). */
    private static final Set<UUID> PREVENUS = new HashSet<>();
    private boolean premierPassage = true;
    private static int ticks;

    public static BasesClans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BasesClans::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new BasesClans()), ID);
    }

    /** HeraliZ Clans est là : les bases sont celles des clans. */
    public static boolean actif() {
        return ClanAccess.clansLoaded();
    }

    /** Le groupe est celui d'un clan (sa base suit le clan). */
    public static boolean estGroupeClan(MinecraftServer server, @Nullable UUID groupe) {
        return groupe != null && get(server).groupes.containsValue(groupe);
    }

    /** Le groupe CRac de la base du clan (null si le clan n'a jamais posé d'armoire). */
    @Nullable
    public static UUID groupeDuClan(MinecraftServer server, UUID clan) {
        return get(server).groupes.get(clan);
    }

    /** Clan -> son groupe (copie). */
    static Map<UUID, UUID> groupesDesClans(MinecraftServer server) {
        return Map.copyOf(get(server).groupes);
    }

    @Nullable
    public static UUID clanDuGroupe(MinecraftServer server, @Nullable UUID groupe) {
        if (groupe == null) {
            return null;
        }
        for (Map.Entry<UUID, UUID> e : get(server).groupes.entrySet()) {
            if (groupe.equals(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Le groupe CRac du clan du joueur, créé à la demande (pose de l'armoire, tutoriel) ; null sans clan ou sans HeraliZ
     * Clans. Le joueur quitte d'abord un ancien groupe qui n'est pas celui d'un clan (les groupes à la main n'existent plus).
     */
    @Nullable
    public static Group groupe(MinecraftServer server, UUID joueur) {
        if (!actif()) {
            return null;
        }
        try {
            return Pont.groupe(server, joueur);
        } catch (LinkageError | RuntimeException e) {
            Crac.LOGGER.warn("[CRac] bases des clans : groupe du clan introuvable", e);
            return null;
        }
    }

    /** Le joueur est dans un clan. */
    public static boolean aUnClan(UUID joueur) {
        if (!actif()) {
            return false;
        }
        try {
            return Pont.clan(joueur) != null;
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Le joueur peut gérer la base de son clan (pose de l'armoire, agrandissement) : chef, ou droit BASE_GERER. */
    public static boolean peutGerer(UUID joueur) {
        if (!actif()) {
            return false;
        }
        try {
            return Pont.perm(joueur, ClanAccess.Action.MANAGE);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Le joueur peut faire cette action dans la base de ce groupe de clan : membre du clan avec le droit voulu (le chef
     * toujours). Faux pour un groupe qui n'est pas celui d'un clan.
     */
    static boolean permis(MinecraftServer server, Group groupe, UUID joueur, ClanAccess.Action action) {
        UUID clan = clanDuGroupe(server, groupe.id());
        if (clan == null) {
            return false;
        }
        try {
            return clan.equals(Pont.clan(joueur)) && Pont.perm(joueur, action);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Le joueur est membre du clan à qui est ce groupe, mais sans le droit voulu (25/10 : coffres de la base de son propre
     * clan, droit « Ouvrir les coffres »). Faux pour un joueur d'un autre clan ou sans clan, et pour un groupe sans clan.
     */
    public static boolean duClanSansDroit(MinecraftServer server, @Nullable Group groupe, UUID joueur, ClanAccess.Action action) {
        UUID clan = groupe == null ? null : clanDuGroupe(server, groupe.id());
        if (clan == null) {
            return false;
        }
        try {
            return clan.equals(Pont.clan(joueur)) && !Pont.perm(joueur, action);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Toutes les 5 secondes : groupes des clans remis à l'image des clans, clans dissous, nouveaux clans. */
    public static void seconde(MinecraftServer server) {
        if (!actif() || ++ticks % 5 != 0) {
            return;
        }
        try {
            Pont.synchroniser(server, get(server));
        } catch (LinkageError | RuntimeException e) {
            Crac.LOGGER.error("[CRac] bases des clans : mise à jour en erreur", e);
        }
        // saison des clans : armoires vues (Purge), taille des bases (arbre du clan), points des cœurs
        try {
            Purge.noterArmoires(server);
            ArbreBases.appliquer(server, get(server));
            CoeurDeplacement.majScores(server);
        } catch (LinkageError | RuntimeException e) {
            Crac.LOGGER.error("[CRac] saison des clans : mise à jour en erreur", e);
        }
    }

    /**
     * Remise à zéro du serveur ({@code /wipe serveur}, avant la dissolution des clans) : cœurs retirés, groupes des clans,
     * kits de première base, délais de déplacement et premières armoires vues par la Purge oubliés. Les bases elles-mêmes
     * sont effacées par {@code bases/WipeBases}. Renvoie le nombre de cœurs retirés.
     */
    public static int toutOublier(MinecraftServer server) {
        int coeurs = CoeursClans.toutRetirer(server);
        BasesClans d = get(server);
        d.groupes.clear();
        d.vus.clear();
        d.kits.clear();
        d.attenteKit.clear();
        d.resteKit.clear();
        d.kitAPrevenir.clear();
        d.setDirty();
        CoeurDeplacement.toutOublier(server);
        Purge.Donnees p = Purge.Donnees.get(server);
        p.vues.clear();
        p.setDirty();
        return coeurs;
    }

    /**
     * Clan dissous : son armoire est retirée (sans rien faire tomber) et son claim libéré ; les pièces restent, à tout le
     * monde ; son groupe disparaît.
     */
    static void liberer(MinecraftServer server, BasesClans d, UUID clan) {
        UUID gid = d.groupes.remove(clan);
        d.setDirty();
        if (gid == null) {
            return;
        }
        // son cœur de clan disparaît avec lui (et ses points : le clan n'existe plus)
        net.minecraft.core.GlobalPos coeur = CoeursClans.coeur(server, gid);
        if (coeur != null) {
            ServerLevel cl = server.getLevel(coeur.dimension());
            if (cl != null && cl.getBlockState(coeur.pos()).getBlock() instanceof fr.heraliz.crac.block.CoeurClanBlock) {
                cl.removeBlock(coeur.pos(), false);
            }
            CoeursClans.oublier(server, gid);
        }
        java.util.List<Claim> essais = new java.util.ArrayList<>();
        ServerLevel tuto = server.getLevel(fr.heraliz.crac.bases.MondeBases.TUTO);
        for (ServerLevel level : server.getAllLevels()) {
            Claim c = ClaimData.get(level).getByGroup(gid);
            if (c == null) {
                continue;
            }
            if (level == tuto) {
                // cabane d'essai du tutoriel (1.21.0) : gardée, passée plus bas au groupe à son nom (sinon le chef reste
                // coincé à l'étape de l'armoire, son armoire disparue)
                ClaimManager.removeByAnchor(level, c.anchor());
                essais.add(c);
                continue;
            }
            BlockPos a = c.anchor();
            // 25/10 (décision du propriétaire) : l'armoire part sans rien faire tomber. Avant, releaseClaim la cassait
            // avec destroyBlock(…, true) : l'armoire et tout son stock de planches tombaient au sol.
            ClaimManager.removeByAnchor(level, a);
            ClaimManager.retirerArmoireSansRien(level, a);
            fr.heraliz.crac.bases.Protections.majTextes(server);
            C2SRequestNearbyClaimsTraitement.pushAll(level);
            Crac.LOGGER.info("[CRac] clan {} dissous : armoire de sa base retirée en {} {}, base libre", clan, level.dimension().location(), a);
        }
        GroupData data = GroupData.get(server);
        Group g = data.getById(gid);
        if (g != null) {
            data.disband(g);
        }
        for (Claim c : essais) {
            BlockPos a = c.anchor();
            fr.heraliz.crac.build.model.Building b = fr.heraliz.crac.build.world.StructureService.buildingOfClaim(tuto, a);
            ServerPlayer poseur = null;
            if (b != null) {
                for (fr.heraliz.crac.build.model.Piece x : fr.heraliz.crac.build.world.StructureStore.get(tuto).piecesOf(b)) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(x.owner);
                    if (sp != null && fr.heraliz.crac.bases.Tutoriel.occupe(server, x.owner)) {
                        poseur = sp;
                        break;
                    }
                }
            }
            Group essai = poseur == null ? null : fr.heraliz.crac.bases.Tutoriel.groupeEssai(poseur);
            if (essai != null) {
                ClaimManager.addClaim(tuto, a, essai.id(), c.tier());
            } else {
                ClaimManager.retirerArmoireSansRien(tuto, a);
            }
            C2SRequestNearbyClaimsTraitement.pushAll(tuto);
        }
    }

    public static BasesClans load(CompoundTag tag) {
        BasesClans d = new BasesClans();
        ListTag l = tag.getList("Clans", 10);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.hasUUID("Clan") && t.hasUUID("Groupe")) {
                d.groupes.put(t.getUUID("Clan"), t.getUUID("Groupe"));
            }
        }
        ListTag v = tag.getList("Vus", 10);
        for (int i = 0; i < v.size(); i++) {
            if (v.getCompound(i).hasUUID("Clan")) {
                d.vus.add(v.getCompound(i).getUUID("Clan"));
            }
        }
        ListTag k = tag.getList("Kits", 10);
        for (int i = 0; i < k.size(); i++) {
            if (k.getCompound(i).hasUUID("Clan")) {
                d.kits.add(k.getCompound(i).getUUID("Clan"));
            }
        }
        ListTag r = tag.getList("ResteKit", 10);
        for (int i = 0; i < r.size(); i++) {
            CompoundTag t = r.getCompound(i);
            if (!t.hasUUID("Clan")) {
                continue;
            }
            java.util.List<net.minecraft.world.item.ItemStack> objets = new java.util.ArrayList<>();
            ListTag os = t.getList("Objets", 10);
            for (int k2 = 0; k2 < os.size(); k2++) {
                net.minecraft.world.item.ItemStack st = net.minecraft.world.item.ItemStack.of(os.getCompound(k2));
                if (!st.isEmpty()) {
                    objets.add(st);
                }
            }
            if (!objets.isEmpty()) {
                d.resteKit.put(t.getUUID("Clan"), objets);
            }
        }
        ListTag ap = tag.getList("KitAPrevenir", 10);
        for (int i = 0; i < ap.size(); i++) {
            if (ap.getCompound(i).hasUUID("Clan")) {
                d.kitAPrevenir.add(ap.getCompound(i).getUUID("Clan"));
            }
        }
        d.premierPassage = d.vus.isEmpty();
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, UUID> e : this.groupes.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", e.getKey());
            t.putUUID("Groupe", e.getValue());
            l.add(t);
        }
        tag.put("Clans", l);
        ListTag v = new ListTag();
        for (UUID c : this.vus) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", c);
            v.add(t);
        }
        tag.put("Vus", v);
        ListTag k = new ListTag();
        for (UUID c : this.kits) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", c);
            k.add(t);
        }
        tag.put("Kits", k);
        ListTag r = new ListTag();
        for (Map.Entry<UUID, java.util.List<net.minecraft.world.item.ItemStack>> e : this.resteKit.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", e.getKey());
            ListTag os = new ListTag();
            for (net.minecraft.world.item.ItemStack st : e.getValue()) {
                os.add(st.save(new CompoundTag()));
            }
            t.put("Objets", os);
            r.add(t);
        }
        tag.put("ResteKit", r);
        ListTag ap = new ListTag();
        for (UUID c : this.kitAPrevenir) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", c);
            ap.add(t);
        }
        tag.put("KitAPrevenir", ap);
        return tag;
    }

    /** La seule classe qui nomme les types de HeraliZ Clans : chargée au premier appel, qui n'a lieu que s'il est là. */
    private static final class Pont {
        private Pont() {
        }

        @Nullable
        static UUID clan(UUID joueur) {
            return fr.heralisz.clans.api.ClansApi.clanDe(joueur).orElse(null);
        }

        static boolean perm(UUID joueur, ClanAccess.Action action) {
            Optional<UUID> clan = fr.heralisz.clans.api.ClansApi.clanDe(joueur);
            if (clan.isEmpty()) {
                return false;
            }
            Optional<fr.heralisz.clans.api.ClanInfo> info = fr.heralisz.clans.api.ClansApi.infos(clan.get());
            if (info.isPresent() && joueur.equals(info.get().chef())) {
                return true;
            }
            fr.heralisz.clans.model.Perm p = switch (action) {
                case ENTER -> fr.heralisz.clans.model.Perm.BASE_ENTRER;
                case BUILD -> fr.heralisz.clans.model.Perm.BASE_CONSTRUIRE;
                case BREAK -> fr.heralisz.clans.model.Perm.BASE_CASSER;
                case DOORS -> fr.heralisz.clans.model.Perm.BASE_PORTES;
                case CHESTS -> fr.heralisz.clans.model.Perm.BASE_COFFRES;
                case MANAGE -> fr.heralisz.clans.model.Perm.BASE_GERER;
                case TELEPORT -> fr.heralisz.clans.model.Perm.BASE_TP;
            };
            return fr.heralisz.clans.api.ClansApi.aPerm(joueur, clan.get(), p);
        }

        @Nullable
        static Group groupe(MinecraftServer server, UUID joueur) {
            Optional<UUID> clan = fr.heralisz.clans.api.ClansApi.clanDe(joueur);
            if (clan.isEmpty()) {
                return null;
            }
            Optional<fr.heralisz.clans.api.ClanInfo> info = fr.heralisz.clans.api.ClansApi.infos(clan.get());
            if (info.isEmpty()) {
                return null;
            }
            BasesClans d = get(server);
            GroupData data = GroupData.get(server);
            UUID gid = d.groupes.get(clan.get());
            Group g = gid == null ? null : data.getById(gid);
            if (g == null) {
                fr.heralisz.clans.api.ClanInfo i = info.get();
                UUID chef = i.chef();
                quitterAncien(data, chef, null);
                String tag = i.tag();
                if (tag == null || data.tagTaken(tag)) {
                    tag = "";              // tag déjà pris (cabane d'essai d'un tutoriel...) : groupe sans tag, jamais null
                }
                g = data.create(i.nom(), tag, i.banniere().accent() & 0xFFFFFF, chef);
                if (g == null) {
                    return null;
                }
                d.groupes.put(clan.get(), g.id());
                d.setDirty();
                Crac.LOGGER.info("[CRac] base du clan [{}] {} : groupe {}", i.tag(), i.nom(), g.id());
            }
            mettreAJour(data, g, info.get());
            return g;
        }

        /** Le joueur quitte un groupe qui n'est pas {@code garder} (groupe fait à la main, ou d'un autre clan). */
        static void quitterAncien(GroupData data, UUID joueur, @Nullable Group garder) {
            Group autre = data.getByMember(joueur);
            if (autre == null || autre == garder) {
                return;
            }
            if (autre.isOwner(joueur)) {
                if (autre.members().size() <= 1) {
                    data.disband(autre);
                    return;
                }
                for (UUID m : autre.members()) {
                    if (!m.equals(joueur)) {
                        data.transferOwnership(autre, m);
                        break;
                    }
                }
            }
            data.removeMember(autre, joueur);
        }

        /** Membres du groupe = membres du clan, propriétaire = chef. */
        static void mettreAJour(GroupData data, Group g, fr.heralisz.clans.api.ClanInfo info) {
            Set<UUID> clan = info.membres().keySet();
            for (UUID m : Set.copyOf(g.members())) {
                if (!clan.contains(m) && !m.equals(g.owner())) {
                    data.removeMember(g, m);
                }
            }
            for (UUID m : clan) {
                if (!g.isMember(m)) {
                    quitterAncien(data, m, g);
                    data.addMember(g, m);
                }
            }
            UUID chef = info.chef();
            if (chef != null && g.isMember(chef) && !g.isOwner(chef)) {
                UUID ancien = g.owner();
                data.transferOwnership(g, chef);
                if (!clan.contains(ancien)) {
                    data.removeMember(g, ancien);
                }
            } else if (!clan.contains(g.owner()) && chef != null && g.isMember(chef)) {
                data.transferOwnership(g, chef);
            }
        }

        static void synchroniser(MinecraftServer server, BasesClans d) {
            GroupData data = GroupData.get(server);
            Set<UUID> existants = new HashSet<>();
            for (fr.heralisz.clans.api.ClanInfo info : fr.heralisz.clans.api.ClansApi.tous()) {
                existants.add(info.id());
                UUID gid = d.groupes.get(info.id());
                Group g = gid == null ? null : data.getById(gid);
                if (g != null) {
                    mettreAJour(data, g, info);
                }
                if (d.vus.add(info.id())) {
                    d.setDirty();
                    if (!d.premierPassage) {
                        ServerPlayer chef = info.chef() == null ? null : server.getPlayerList().getPlayer(info.chef());
                        if (chef != null) {
                            fr.heraliz.crac.bases.TutorielApi.clanCree(chef);
                        } else if (info.chef() != null) {
                            fr.heraliz.crac.bases.TutorielApi.clanCreeHorsLigne(server, info.chef());
                        }
                    }
                }
            }
            d.premierPassage = false;
            // 25/10 : liste des clans lue en partie (fichier abîmé, retouché à la main, absent) : rien n'est libéré ni
            // oublié. Avant, chaque clan manquant voyait son armoire retirée, sa base livrée au pillage, son cœur et ses
            // points perdus, et recevait un second matériel de première base une fois le fichier réparé.
            if (clansFiables(d, existants)) {
                for (UUID clan : Set.copyOf(d.groupes.keySet())) {
                    if (!existants.contains(clan)) {
                        liberer(server, d, clan);
                    }
                }
                if (d.vus.retainAll(existants) | d.kits.retainAll(existants) | d.resteKit.keySet().retainAll(existants)
                    | d.kitAPrevenir.retainAll(existants)) {
                    d.setDirty();
                }
            }
            d.attenteKit.keySet().retainAll(existants);
            kits(server, d);
            prevenirChefs(server, d);
        }

        /**
         * La liste des clans peut servir à libérer les bases des clans disparus : HeraliZ Clans a ses données, les a lues
         * en entier au démarrage ({@code ClanStore.lectureIncomplete}, lu par réflexion : faux s'il est introuvable), et
         * elles ne sont pas toutes parties d'un coup (plusieurs bases de clans et plus aucun clan : fichier absent ou
         * remplacé ; une vraie dissolution arrive clan par clan, vue à la passe suivante). Prévient la console une fois.
         */
        private static boolean clansFiables(BasesClans d, Set<UUID> existants) {
            String pourquoi = null;
            if (!fr.heralisz.clans.api.ClansApi.disponible()) {
                pourquoi = "données des clans pas encore chargées";
            } else if (lectureIncomplete()) {
                pourquoi = "clans.json ou joueurs.json lu en partie au démarrage";
            } else if (existants.isEmpty() && d.groupes.size() >= 2) {
                pourquoi = "plus aucun clan alors que " + d.groupes.size() + " clans ont une base";
            }
            if (pourquoi == null) {
                return true;
            }
            if (!avertiNonFiable) {
                avertiNonFiable = true;
                Crac.LOGGER.error("[CRac] bases des clans : {} : aucune base n'est libérée tant que ce n'est pas réglé"
                    + " (réparer le fichier des clans, puis redémarrer)", pourquoi);
            }
            return false;
        }

        private static boolean avertiNonFiable;

        private static boolean lectureIncomplete() {
            try {
                Class<?> store = Class.forName("fr.heralisz.clans.server.ClanStore");
                Object s = store.getMethod("get").invoke(null);
                return s != null && Boolean.TRUE.equals(store.getMethod("lectureIncomplete").invoke(s));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return false;
            }
        }

        /** Chef connecté depuis une minute (le temps du /login) : prévenu du matériel rangé pendant son absence. */
        private static void prevenirChefs(MinecraftServer server, BasesClans d) {
            if (d.kitAPrevenir.isEmpty()) {
                return;
            }
            for (UUID clan : Set.copyOf(d.kitAPrevenir)) {
                UUID chef = fr.heralisz.clans.api.ClansApi.infos(clan).map(fr.heralisz.clans.api.ClanInfo::chef).orElse(null);
                ServerPlayer p = chef == null ? null : server.getPlayerList().getPlayer(chef);
                if (p == null || p.tickCount < 1200 || fr.heraliz.crac.bases.Tutoriel.occupe(server, chef)) {
                    continue;
                }
                d.kitAPrevenir.remove(clan);
                d.setDirty();
                p.sendSystemMessage(Component.translatableWithFallback("crac.clan.kit_rappel",
                    "Ton clan a son matériel de première base dans le coffre du clan (plan, marteau, armoire, brindilles) : /clan coffre au spawn, ou onglet Banque.")
                    .withStyle(ChatFormatting.GOLD));
            }
        }

        /**
         * Matériel de la première base (plan, marteau, armoire, brindilles) rangé une fois dans le coffre de chaque clan
         * (HeraliZ Clans 3.5.0, {@code ClansApi.deposerCoffre}, par réflexion : la copie de Clans de libs/ ne l'a pas) :
         * 30 s après sa création, et seulement quand le chef a fini le tutoriel des bases (sinon à la fin de celui-ci).
         */
        private static java.util.List<net.minecraft.world.item.ItemStack> copie(java.util.List<net.minecraft.world.item.ItemStack> l) {
            java.util.List<net.minecraft.world.item.ItemStack> c = new java.util.ArrayList<>();
            for (net.minecraft.world.item.ItemStack s : l) {
                c.add(s.copy());
            }
            return c;
        }

        /** Nombre d'objets d'une liste de piles. */
        private static int total(java.util.List<net.minecraft.world.item.ItemStack> l) {
            int n = 0;
            for (net.minecraft.world.item.ItemStack s : l) {
                n += s.getCount();
            }
            return n;
        }

        static void kits(MinecraftServer server, BasesClans d) {
            java.lang.reflect.Method deposer;
            try {
                deposer = fr.heralisz.clans.api.ClansApi.class.getMethod("deposerCoffre", UUID.class, java.util.List.class);
            } catch (NoSuchMethodException e) {
                return;   // HeraliZ Clans sans coffre : rien à faire
            }
            long now = System.currentTimeMillis();
            for (fr.heralisz.clans.api.ClanInfo info : fr.heralisz.clans.api.ClansApi.tous()) {
                boolean premier = !d.kits.contains(info.id());
                java.util.List<net.minecraft.world.item.ItemStack> kit;
                if (!premier) {
                    // déjà rangé : seulement ce qui attendait une place (coffre plein)
                    kit = d.resteKit.get(info.id());
                    if (kit == null || kit.isEmpty()) {
                        continue;
                    }
                } else {
                    long vu = d.attenteKit.computeIfAbsent(info.id(), c -> now);
                    if (now - vu < 30_000L || info.chef() != null && fr.heraliz.crac.bases.Tutoriel.occupe(server, info.chef())) {
                        continue;
                    }
                    kit = fr.heraliz.crac.bases.Tutoriel.kitPremiereBase();
                }
                Object reste;
                try {
                    reste = deposer.invoke(null, info.id(), copie(kit));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Crac.LOGGER.error("[CRac] coffre du clan [{}] : matériel de la première base non rangé", info.tag(), e);
                    continue;
                }
                java.util.List<net.minecraft.world.item.ItemStack> restant = new java.util.ArrayList<>();
                if (reste instanceof java.util.List<?> l) {
                    for (Object o : l) {
                        if (o instanceof net.minecraft.world.item.ItemStack st && !st.isEmpty()) {
                            restant.add(st.copy());
                        }
                    }
                }
                if (!premier && total(restant) >= total(kit)) {
                    continue;   // coffre toujours plein : rien n'a bougé, rien à réécrire (avant : le fichier toutes les 5 s)
                }
                d.kits.add(info.id());
                d.attenteKit.remove(info.id());
                if (!restant.isEmpty()) {
                    // coffre plein (1.21.0) : rien n'est perdu, le reste est redéposé dès qu'il y a de la place
                    d.resteKit.put(info.id(), restant);
                    if (PREVENUS.add(info.id())) {
                        Crac.LOGGER.warn("[CRac] coffre du clan [{}] plein : {} objet(s) du matériel de la première base en attente", info.tag(), restant.size());
                        ServerPlayer chef = info.chef() == null ? null : server.getPlayerList().getPlayer(info.chef());
                        if (chef != null) {
                            chef.sendSystemMessage(Component.translatableWithFallback("crac.clan.kit_plein",
                                "Le coffre du clan est plein : une partie du matériel de la première base attend une place. Libère des cases (/clan coffre) : il y sera rangé tout seul.")
                                .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                } else if (d.resteKit.remove(info.id()) != null) {
                    PREVENUS.remove(info.id());
                }
                if (premier && info.chef() != null && server.getPlayerList().getPlayer(info.chef()) == null) {
                    d.kitAPrevenir.add(info.id());
                }
                d.setDirty();
                // 24/10 : enregistré tout de suite, comme le coffre du clan (un plantage entre les deux redéposait le
                // matériel, armoire comprise). 25/10 : seulement ce fichier (avant : toutes les données du monde, loot
                // et coffres compris, d'un coup sur le fil serveur). 25/10 (2e passe) : APRÈS la mise à jour de ce qui
                // reste à ranger (avant, le fichier était écrit avant et marqué propre : un plantage redéposait le reste
                // déjà rangé, ou perdait ce qui n'était pas entré)
                d.save(SafeSavedData.dataFile(server.overworld(), ID).toFile());
                if (!premier) {
                    continue;
                }
                Crac.LOGGER.info("[CRac] matériel de la première base rangé dans le coffre du clan [{}]", info.tag());
                for (UUID m : info.membres().keySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(m);
                    if (p != null) {
                        p.sendSystemMessage(Component.translatableWithFallback("crac.clan.kit_coffre",
                            "Le matériel de la première base du clan (plan, marteau, armoire, brindilles) est dans le coffre du clan : /clan coffre ou onglet Banque.")
                            .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
        }
    }
}
