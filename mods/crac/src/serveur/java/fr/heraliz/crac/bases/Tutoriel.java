package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupNames;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Reply;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CTuto;
import fr.heraliz.crac.network.S2CTutoFin;
import fr.heraliz.crac.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Tutoriel du monde des bases (« Ta première base »), pour un joueur qui n'a jamais eu de base.
 * <ol>
 *   <li>Départ : avec HeraliZ Quêtes, seulement par le Commandant Varga (bouton « Faire ma base » de son dialogue,
 *   {@link TutorielApi#lancer}) ; sans HeraliZ Quêtes, comme avant, tout seul au spawn après la cinématique d'arrivée
 *   (donnée persistante {@code heraliz_cinematique_vue} de Utils : {@code [tuto] attente_min_s} secondes puis le premier
 *   pas, ou au plus {@code attente_max_s} secondes).</li>
 *   <li>Titres à l'écran, puis le kit : de quoi bâtir avec CRac une cabane 2 × 1 avec une porte (2 fondations, 5 murs,
 *   1 pièce « Porte », 2 planchers en toit), calculé avec les coûts réels de crac-building.toml, plus la mise en bois au
 *   marteau, un jour d'entretien du Claim Block et une marge de 10 % ; un groupe CRac est créé pour lui s'il n'en a pas.</li>
 *   <li>Envoi dans le monde des bases, à un point plat tiré au hasard loin des autres bases. Six étapes, suivies ici
 *   d'après la cabane elle-même ({@link #progression}) et dites par Varga à la radio (HeraliZ Quêtes) : la première
 *   fondation sur un coin plat, le Claim Block dessus (hors d'un claim, CRac n'accepte rien d'autre), la deuxième
 *   fondation, 5 murs + la porte + 2 planchers, toute la cabane en bois, des planches dans le Claim Block (entretien).
 *   Le tutoriel finit avec la dernière. Avec Varga, aucun titre au milieu de l'écran.</li>
 * </ol>
 * Tutoriel OBLIGATOIRE (choix du propriétaire, 18/10) : tant qu'il dure (étapes KIT et PHASE, hors créatif et
 * spectateur), le joueur ne peut ni le quitter ({@code /spawn}, {@code /home}, {@code /zone base}, {@code /rtp},
 * {@code /suicide}, matchs, casino, borne de départ), ni perdre son kit : rien ne se jette, rien ne se fabrique, aucun
 * bloc ne se pose (sauf le Claim Block à son étape), seules les pièces de l'étape en cours se posent, et une pièce
 * démolie rend ce qu'elle a coûté. Il ne prend aucun dégât dans le monde des bases ({@link Protections}). Déconnecté, il
 * reprend où il en était ; ramené au spawn (mort, staff), il est renvoyé à sa cabane.
 * <p>
 * Le tutoriel ne vise que les joueurs en survie ou en aventure, à l'étape AUCUNE (jamais fait) et sans base. Staff
 * (permission 2) :
 * <ul>
 *   <li>{@code /tuto lancer <joueur>} : le tutoriel complet TOUT DE SUITE (titres, kit, envoi), où que soit le joueur,
 *   même en créatif, même s'il a déjà une base ou l'a déjà fait (la protection de base n'est redonnée qu'à un joueur
 *   sans base) ;</li>
 *   <li>{@code /tuto rejouer <joueur>} : remise à zéro (il repart au spawn, comme un nouveau joueur) ;</li>
 *   <li>{@code /tuto finir <joueur>} : le libère du tutoriel (en cas de souci), comme s'il l'avait fini ;</li>
 *   <li>sans HeraliZ Quêtes, la cinématique rejouée par le staff ({@code /cinematique} pour soi,
 *   {@code /cinematique joueur <cibles>}) relance aussi le tutoriel du joueur à la fin de la cinématique, comme
 *   {@code /tuto lancer} (un joueur qui rejoue sa propre cinématique ne reçoit pas de nouveau kit).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "crac")
public final class Tutoriel {
    /** Donnée persistante (PlayerPersisted) posée par HeraliZ Utils au lancement de la cinématique. */
    private static final String CINEMATIQUE_VUE = "heraliz_cinematique_vue";
    /** Pièces de la cabane 2 × 1 avec une porte. */
    private static final Map<PieceKind, Integer> CABANE = new LinkedHashMap<>();

    static {
        CABANE.put(PieceKind.FOUNDATION, 2);
        CABANE.put(PieceKind.WALL, 5);
        CABANE.put(PieceKind.DOORWAY, 1);
        CABANE.put(PieceKind.FLOOR, 2);
    }

    /** Attente de la fin de la cinématique : heure de jeu du début, position, relancée par le staff (cinématique rejouée). */
    private record Attente(long debut, Vec3 pos, boolean force) {
    }

    /** Joueurs qui attendent la fin de la cinématique (heure de jeu du début, position). */
    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    /** Séquences en cours (tick de la séquence), étape KIT comprise. */
    private static final Map<UUID, Integer> SEQUENCES = new HashMap<>();
    /** Séquences lancées par le staff : elles se déroulent où que soit le joueur (pas seulement au spawn). */
    private static final Set<UUID> FORCES = new HashSet<>();
    /** Essais d'envoi ratés (aucune place trouvée) d'une séquence lancée par le staff. */
    private static final Map<UUID, Integer> ESSAIS = new HashMap<>();
    private static final int ESSAIS_MAX = 5;

    private static final int T_TITRE_2 = 80;
    private static final int T_TITRE_3 = 170;
    private static final int T_ENVOI = 230;
    /** Avec Varga (pas de titres) : envoi 2 s après le kit. */
    private static final int T_ENVOI_VARGA = 40;

    /** Étapes de la cabane ({@link Cabane}) ; {@link #progression} rend FINIE quand tout est fait. */
    static final int ETAPES = Cabane.ETAPES;
    static final int FINIE = Cabane.FINIE;
    /** Commandes refusées pendant le tutoriel (elles feraient quitter le monde des bases ou mourir). */
    private static final Set<String> COMMANDES_BLOQUEES = Set.of("spawn", "home", "zone", "rtp", "suicide", "duel", "clashwar",
        "zombies", "casino", "depart", "extractions", "tpa", "tpaccept", "tpdeny", "warp");
    /** Dernier avertissement (heure en ms) par joueur, pour ne pas inonder sa barre d'action. */
    private static final Map<UUID, Long> AVERTI = new HashMap<>();
    /** Joueurs à qui Varga vient de poser le sas (pour ce qu'il leur dit à la radio, HeraliZ Quêtes). */
    static final Set<UUID> SAS = new HashSet<>();
    /** Dernier guide envoyé à chaque joueur (S2CTuto), pour ne renvoyer que ce qui change. */
    private static final Map<UUID, S2CTuto> GUIDES = new HashMap<>();

    private Tutoriel() {
    }

    private static boolean actif() {
        return BasesReglages.b(BasesReglages.TUTO_ACTIF, true);
    }

    // ---------------------------------------------------------------------------------------------- détection

    /** Toutes les secondes (MondeBasesServeur). */
    static void seconde(MinecraftServer server) {
        if (!actif() || server.getLevel(MondeBases.BASES) == null) {
            return;
        }
        BasesDonnees d = BasesDonnees.get(server);
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            majGuide(p);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (SEQUENCES.containsKey(id)) {
                continue;
            }
            Attente f = ATTENTES.get(id);
            if (f != null && f.force()) {
                // cinématique rejouée par le staff : tuto à la fin, quels que soient le mode de jeu et l'étape
                if (pret(p, f, now)) {
                    ATTENTES.remove(id);
                    lancer(p);
                }
                continue;
            }
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            BasesDonnees.Joueur j = d.joueur(id);
            switch (j.etape) {
                case AUCUNE -> {
                    if (aUneBase(p)) {
                        j.etape = BasesDonnees.Etape.FINI;
                        d.marquer();
                        ATTENTES.remove(id);
                        continue;
                    }
                    // avec HeraliZ Quêtes, le tutoriel ne part que par le Commandant Varga
                    if (parVarga() || !MondeBases.estSpawn(p.level()) || !cinematiqueVue(p)) {
                        ATTENTES.remove(id);
                        continue;
                    }
                    Attente a = ATTENTES.computeIfAbsent(id, k -> new Attente(now, p.position(), false));
                    if (pret(p, a, now)) {
                        ATTENTES.remove(id);
                        SEQUENCES.put(id, 0);
                    }
                }
                case KIT -> {
                    // kit déjà reçu (déconnecté avant l'envoi) : de retour au spawn, on repart à l'envoi
                    if (MondeBases.estSpawn(p.level())) {
                        SEQUENCES.put(id, T_TITRE_3 - 20);
                    }
                }
                case PHASE -> {
                    ATTENTES.remove(id);
                    suivre(p, d, j);
                }
                default -> ATTENTES.remove(id);
            }
        }
    }

    /** Ce que le jeu du joueur montre du tutoriel (fantômes, objet surligné...), renvoyé seulement quand ça change. */
    private static void majGuide(ServerPlayer p) {
        S2CTuto m = S2CTuto.AUCUN;
        if (enCours(p) && MondeBases.estBases(p.level())) {
            Cabane.Etat e = cabane(p);
            if (e.etape() >= 1 && e.etape() <= ETAPES) {
                String objet = e.objet() == null ? "" : String.valueOf(ForgeRegistries.ITEMS.getKey(e.objet()));
                m = new S2CTuto(e.etape(), e.cibles(), e.blocs(), objet, e.roue(), e.bois());
            }
        }
        S2CTuto avant = GUIDES.get(p.getUUID());
        if (m.equals(avant) || avant == null && m.etape() == 0) {
            return;
        }
        if (m.etape() == 0) {
            GUIDES.remove(p.getUUID());
        } else {
            GUIDES.put(p.getUUID(), m);
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    /** Le jeu du joueur oublie le tutoriel (fantômes, objet surligné) : tutoriel arrêté par le staff. */
    private static void effacerGuide(ServerPlayer p) {
        GUIDES.remove(p.getUUID());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), S2CTuto.AUCUN);
    }

    /** Fin du tutoriel : la caméra du joueur fait le tour de sa cabane (S2CTutoFin), Varga parle à la radio. */
    private static void cinemaFin(ServerPlayer p, BasesDonnees.Joueur j) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        Building b = bases == null ? null : batiment(bases, p, j);
        if (b == null) {
            return;
        }
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (Piece piece : StructureService.store(bases).piecesOf(b)) {
            if (piece.kind() == PieceKind.FOUNDATION) {
                Vec3 c = piece.key().centreWorld();
                x += c.x;
                y += c.y;
                z += c.z;
                n++;
            }
        }
        if (n > 0) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CTutoFin(x / n, y / n + 2.0, z / n, 16F));
        }
    }

    /** HeraliZ Quêtes est là : le tutoriel part du dialogue du Commandant Varga, qui le guide ensuite à la radio. */
    static boolean parVarga() {
        return ModList.get().isLoaded("heraliz_quetes");
    }

    /**
     * Étape PHASE, chaque seconde : ramené au spawn (mort, staff), le joueur repart à sa cabane ; la cabane finie, le
     * tutoriel l'est aussi. Le monde des bases et le spawn mis à part, rien (seul le staff peut l'y avoir mis).
     */
    private static void suivre(ServerPlayer p, BasesDonnees d, BasesDonnees.Joueur j) {
        if (MondeBases.estSpawn(p.level())) {
            ramener(p, j);
            return;
        }
        if (!MondeBases.estBases(p.level())) {
            return;
        }
        if (progression(p)[0] >= FINIE) {
            ServerLevel bases = p.server.getLevel(MondeBases.BASES);
            Building b = bases == null ? null : batiment(bases, p, j);
            if (b != null && Cabane.sas(bases, p, b)) {
                SAS.add(p.getUUID());
            }
            cinemaFin(p, j);
            j.etape = BasesDonnees.Etape.FINI;
            j.batiment = -1L;
            d.marquer();
            AVERTI.remove(p.getUUID());
            Crac.LOGGER.info("[CRac] tutoriel fini par {}", p.getGameProfile().getName());
            if (!parVarga()) {
                T.chat(p, T.t("crac.tuto.etape6", "[Tuto] Ta base posée, ton home y est créé : du spawn, /home t'y ramène (toutes les 3 min). Depuis ta base, /spawn ramène au spawn ; du spawn, /zone base mène à un coin au hasard de la zone de construction."),
                    ChatFormatting.GRAY);
            }
        }
    }

    /** Renvoie le joueur à sa cabane (sur sa première fondation), sinon à un point au hasard du monde des bases. */
    private static void ramener(ServerPlayer p, BasesDonnees.Joueur j) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return;
        }
        Vec3 dest = null;
        Building b = batiment(bases, p, j);
        if (b != null) {
            for (Piece x : StructureService.store(bases).piecesOf(b)) {
                if (x.kind() == PieceKind.FOUNDATION) {
                    AABB box = x.bounds();
                    dest = new Vec3((box.minX + box.maxX) / 2, box.maxY, (box.minZ + box.maxZ) / 2);
                    break;
                }
            }
        }
        if (dest == null) {
            dest = Points.auHasard(bases, p);
        }
        if (dest == null) {
            return;
        }
        p.stopRiding();
        p.teleportTo(bases, dest.x, dest.y, dest.z, p.getYRot(), 0f);
        p.resetFallDistance();
        T.barre(p, T.t("crac.tuto.ramene", "[Tuto] Retour à ta cabane : le tutoriel n'est pas fini."), ChatFormatting.YELLOW);
    }

    /** Fin de l'attente : {@code attente_min_s} passées et un premier pas, ou {@code attente_max_s} passées. */
    private static boolean pret(ServerPlayer p, Attente a, long now) {
        long ecoule = (now - a.debut()) / 20L;
        boolean bouge = p.position().distanceToSqr(a.pos()) > 1.0;
        return ecoule >= BasesReglages.i(BasesReglages.ATTENTE_MAX_S, 130)
            || ecoule >= BasesReglages.i(BasesReglages.ATTENTE_MIN_S, 15) && bouge;
    }

    /**
     * Le tutoriel complet tout de suite (titres, kit, envoi), où que soit le joueur : étape remise à AUCUNE pour que le
     * kit soit donné ; protection de base due seulement s'il n'a pas de base (dans le kit).
     */
    static void lancer(ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        j.etape = BasesDonnees.Etape.AUCUNE;
        j.phaseTicks = 0;
        j.batiment = -1L;
        j.arete = -1;
        d.marquer();
        ATTENTES.remove(p.getUUID());
        ESSAIS.remove(p.getUUID());
        FORCES.add(p.getUUID());
        SEQUENCES.put(p.getUUID(), 0);
        Crac.LOGGER.info("[CRac] tutoriel lancé pour {}", p.getGameProfile().getName());
    }

    /** Chaque tick (MondeBasesServeur) : déroulé des séquences. */
    static void tick(MinecraftServer server) {
        if (SEQUENCES.isEmpty()) {
            return;
        }
        List<Runnable> actions = new ArrayList<>();
        Iterator<Map.Entry<UUID, Integer>> it = SEQUENCES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            boolean force = FORCES.contains(e.getKey());
            if (p == null || !force && !MondeBases.estSpawn(p.level()) || !p.isAlive()) {
                it.remove();
                FORCES.remove(e.getKey());
                ESSAIS.remove(e.getKey());
                continue;
            }
            int t = e.getValue();
            if (parVarga()) {
                // avec Varga : ni titres au milieu de l'écran ni attente, le kit puis l'envoi (Varga parle à la radio)
                if (t == 0) {
                    actions.add(() -> kit(p));
                } else if (t >= T_ENVOI_VARGA) {
                    it.remove();
                    actions.add(() -> envoyer(p, force));
                    continue;
                }
            } else if (t == 0) {
                actions.add(() -> titre1(p));
            } else if (t == T_TITRE_2) {
                actions.add(() -> kit(p));
            } else if (t == T_TITRE_3) {
                actions.add(() -> titre3(p));
            } else if (t >= T_ENVOI) {
                it.remove();
                actions.add(() -> envoyer(p, force));
                continue;
            }
            e.setValue(t + 1);
        }
        for (Runnable r : actions) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac] tutoriel en erreur", ex);
            }
        }
    }

    static void oublier(UUID joueur) {
        AVERTI.remove(joueur);
        GUIDES.remove(joueur);
        ATTENTES.remove(joueur);
        SEQUENCES.remove(joueur);
        FORCES.remove(joueur);
        ESSAIS.remove(joueur);
    }

    static boolean aUneBase(ServerPlayer p) {
        Group g = GroupData.get(p.server).getByMember(p.getUUID());
        return g != null && ClaimManager.getByGroupAnywhere(p.server, g.id()) != null;
    }

    private static boolean cinematiqueVue(ServerPlayer p) {
        if (!ModList.get().isLoaded("heralisz_utils")) {
            return true;
        }
        CompoundTag root = p.getPersistentData();
        return root.contains(Player.PERSISTED_NBT_TAG) && root.getCompound(Player.PERSISTED_NBT_TAG).getBoolean(CINEMATIQUE_VUE);
    }

    // ---------------------------------------------------------------------------------------------- étapes

    private static void titre1(ServerPlayer p) {
        T.titre(p, T.t("crac.tuto.titre1", "Bienvenue, survivant").withStyle(ChatFormatting.GOLD),
            T.t("crac.tuto.sous1", "Il te faut un abri avant la nuit."), 70);
    }

    private static void kit(ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        if (!parVarga()) {
            T.titre(p, T.t("crac.tuto.titre2", "Ta première base").withStyle(ChatFormatting.GOLD),
                T.t("crac.tuto.sous2", "Voici de quoi bâtir une cabane 2 × 1 avec une porte."), 80);
        }
        if (j.etape == BasesDonnees.Etape.AUCUNE) {
            Group g = groupe(p);
            List<ItemStack> kit = contenuKit();
            for (ItemStack s : kit) {
                donner(p, s.copy());
            }
            // noms des objets traduits chez le joueur (le serveur n'a pas les langues des mods)
            MutableComponent liste = Component.empty();
            boolean premier = true;
            for (Map.Entry<Item, Integer> e : totaux(kit).entrySet()) {
                if (!premier) {
                    liste.append(", ");
                }
                premier = false;
                liste.append(e.getValue() + " × ").append(new ItemStack(e.getKey()).getHoverName());
            }
            j.etape = BasesDonnees.Etape.KIT;
            // un joueur qui a déjà une base (tuto relancé par le staff) ne regagne pas la protection de base
            j.protectionDue = !aUneBase(p);
            j.phaseTicks = 0;
            d.marquer();
            T.chat(p, T.t("crac.tuto.kit", "[Tuto] Kit reçu : %s.", liste), ChatFormatting.YELLOW);
            if (g != null) {
                T.chat(p, T.t("crac.tuto.groupe", "[Tuto] Ta base appartient à ton groupe « %s » (/group pour y inviter tes amis).",
                    g.name()), ChatFormatting.YELLOW);
            }
            Crac.LOGGER.info("[CRac] tutoriel : kit de base donné à {}", p.getGameProfile().getName());
        }
    }

    private static void titre3(ServerPlayer p) {
        T.titre(p, T.t("crac.tuto.titre3", "Monde des bases").withStyle(ChatFormatting.GOLD),
            T.t("crac.tuto.sous3", "Tu es protégé (contour vert) tant que ta base n'est pas posée."), 80);
    }

    private static void envoyer(ServerPlayer p, boolean force) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            FORCES.remove(p.getUUID());
            return;
        }
        Vec3 dest = Points.auHasard(bases, p);
        if (dest == null) {
            T.chat(p, T.t("crac.home.pas_de_place", "Aucune place sûre trouvée dans le monde des bases : réessaie dans un instant."),
                ChatFormatting.RED);
            int n = ESSAIS.merge(p.getUUID(), 1, Integer::sum);
            if (force && n < ESSAIS_MAX) {
                FORCES.add(p.getUUID());
                SEQUENCES.put(p.getUUID(), T_ENVOI - 20);   // nouvel essai dans une seconde, où qu'il soit
            } else {
                FORCES.remove(p.getUUID());
                ESSAIS.remove(p.getUUID());
            }
            return;   // étape KIT : au spawn, nouvel essai à la prochaine seconde
        }
        FORCES.remove(p.getUUID());
        ESSAIS.remove(p.getUUID());
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        p.stopRiding();
        p.teleportTo(bases, dest.x, dest.y, dest.z, p.getYRot(), 0f);
        p.resetFallDistance();
        j.etape = BasesDonnees.Etape.PHASE;
        j.phaseTicks = 0;
        d.marquer();
        j.batiment = -1L;
        j.arete = -1;
        d.marquer();
        if (parVarga()) {
            return;   // Varga donne les consignes à la radio, une par une (HeraliZ Quêtes)
        }
        // dans le chat une demi-seconde après l'arrivée (écran de chargement du monde passé)
        Deplacements.apres(p, T.t("crac.tuto.etape1", "[Tuto] 1. Trouve un coin plat qui te plaît. Le monde des bases est en PvP : seul ton contour vert te protège, jusqu'à la fin du tutoriel.")
            .withStyle(ChatFormatting.YELLOW));
        Deplacements.apres(p, T.t("crac.tuto.etape2", "[Tuto] 2. Plan de construction en main, clic droit : pose %s fondations côte à côte.",
            String.valueOf(CABANE.get(PieceKind.FOUNDATION))).withStyle(ChatFormatting.YELLOW));
        Deplacements.apres(p, T.t("crac.tuto.etape3", "[Tuto] 3. Puis 5 murs, 1 porte (pièce « Porte » du plan, la porte est comprise) et 2 planchers par-dessus pour le toit.")
            .withStyle(ChatFormatting.YELLOW));
        Deplacements.apres(p, T.t("crac.tuto.etape4", "[Tuto] 4. Pose le Claim Block sur une fondation, à l'intérieur : ta base est à toi, et protégée des raids.")
            .withStyle(ChatFormatting.YELLOW));
        Deplacements.apres(p, T.t("crac.tuto.etape5", "[Tuto] 5. Marteau en main : passe tes pièces en bois, et mets des planches dans le Claim Block (entretien).")
            .withStyle(ChatFormatting.YELLOW));
    }

    // ---------------------------------------------------------------------------------------------- cabane

    /** Le joueur est dans le tutoriel (kit reçu ou dans le monde des bases), hors créatif et spectateur. */
    public static boolean enCours(@Nullable Player p) {
        if (!(p instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) {
            return false;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(sp.server).siConnu(sp.getUUID());
        return j != null && (j.etape == BasesDonnees.Etape.KIT || j.etape == BasesDonnees.Etape.PHASE);
    }

    /** Le bâtiment de la cabane du tutoriel ; perdu (toutes ses pièces démolies), celui d'une autre de ses pièces. */
    @Nullable
    private static Building batiment(ServerLevel bases, ServerPlayer p, BasesDonnees.Joueur j) {
        var store = StructureService.store(bases);
        if (j.batiment < 0) {
            return null;   // aucune pièce posée (la première fondation le fixe : piecePosee)
        }
        Building b = store.index.building(j.batiment);
        if (b != null && !b.pieces.isEmpty()) {
            return b;
        }
        long trouve = -1L;
        for (Piece x : store.index.allPieces()) {
            if (x.owner.equals(p.getUUID())) {
                trouve = x.buildingId;
                break;
            }
        }
        if (trouve != j.batiment) {
            j.batiment = trouve;
            BasesDonnees.get(p.server).marquer();
        }
        return trouve < 0 ? null : store.index.building(trouve);
    }

    /** Où en est la cabane du tutoriel, avec ce qu'il faut poser et montrer ({@link Cabane}) ; HORS hors de l'étape PHASE. */
    static Cabane.Etat cabane(ServerPlayer p) {
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).siConnu(p.getUUID());
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (j == null || j.etape != BasesDonnees.Etape.PHASE || bases == null) {
            return Cabane.Etat.HORS;
        }
        return Cabane.etat(p, j, batiment(bases, p, j));
    }

    /** {étape (1 à 9, {@link #FINIE} quand tout est fait), fait, total} ; {0, 0, 0} hors de l'étape PHASE. */
    static int[] progression(ServerPlayer p) {
        Cabane.Etat e = cabane(p);
        return new int[]{e.etape(), e.fait(), e.total()};
    }

    /** Planches d'entretien du kit pour {@code pieces} pièces ({@code [tuto] entretien_heures} heures). */
    static int entretienKit(int pieces) {
        int heures = BasesReglages.i(BasesReglages.ENTRETIEN_HEURES, 24);
        if (heures <= 0 || !Config.SPEC.isLoaded()) {
            return 0;
        }
        return (int) Math.ceil(Config.upkeepPerPiece(UpkeepResource.WOOD) * pieces * heures / Config.upkeepHours.get());
    }

    /**
     * Pose d'une pièce pendant le tutoriel (StructureService.place) : seulement la pièce de l'étape en cours, à sa place
     * (la pièce verte qui clignote) ; la première fondation, n'importe où. Null quand c'est permis.
     */
    @Nullable
    public static Component refusPiece(ServerPlayer p, PieceKey key, long buildingId) {
        if (!enCours(p)) {
            return null;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        if (j.etape != BasesDonnees.Etape.PHASE) {
            return refus(p, T.t("crac.tuto.refus.pas_encore", "Pas encore : attends les consignes de Varga."));
        }
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        Building b = bases == null ? null : batiment(bases, p, j);
        Cabane.Etat e = cabane(p);
        boolean nouveau = buildingId == PlacementRules.NEW_BUILDING;
        boolean ok = switch (e.etape()) {
            case Cabane.PLAN, Cabane.FONDATION_1 -> key.kind() == PieceKind.FOUNDATION && b == null && nouveau;
            case Cabane.FONDATION_2, Cabane.MURS, Cabane.TOIT -> b != null && buildingId == b.id
                && (e.cibles().isEmpty() ? key.kind() == PieceKind.FOUNDATION : visee(e.cibles(), key));
            default -> false;
        };
        if (ok) {
            return null;
        }
        return refus(p, e.cibles().isEmpty()
            ? T.t("crac.tuto.refus.piece", "Pas cette pièce pour l'instant : suis les consignes de Varga.")
            : T.t("crac.tuto.refus.cible", "Pose-la sur la pièce verte qui clignote."));
    }

    private static boolean visee(List<PieceKey> cibles, PieceKey key) {
        for (PieceKey c : cibles) {
            if (Cabane.memePlace(c, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pendant le tutoriel, la cabane se bâtit avant son Claim Block (choix du propriétaire : le Claim Block à la fin) :
     * la règle « un Claim Block avant de construire plus » de CRac est levée pour elle seule.
     */
    public static boolean sansClaimPermis(ServerPlayer p, @Nullable Building b) {
        if (b == null || !enCours(p)) {
            return false;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        return j.etape == BasesDonnees.Etape.PHASE && b.id == j.batiment;
    }

    /** Une pièce vient d'être posée (StructureService.place) : la première fondation fixe la cabane et le côté de la deuxième. */
    public static void piecePosee(ServerPlayer p, Piece piece) {
        if (!enCours(p)) {
            return;
        }
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        if (j.batiment < 0 || StructureService.store(p.serverLevel()).index.building(j.batiment) == null) {
            j.batiment = piece.buildingId;
            j.arete = piece.kind() == PieceKind.FOUNDATION ? Cabane.choisirArete(p, piece) : -1;
            d.marquer();
        }
        if (piece.kind() == PieceKind.DOORWAY) {
            // la porte de la cabane s'ouvre vers l'extérieur, quel que soit le sens choisi (1.11.0)
            ServerLevel level = p.serverLevel();
            long id = piece.id;
            plusTard(p, () -> {
                Cabane.porteVersExterieur(level, id);
                majGuide(p);
            });
        }
        majGuide(p);   // la pièce verte suivante tout de suite
    }

    /** Au tick suivant (après la fin de la pose en cours). */
    private static void plusTard(ServerPlayer p, Runnable r) {
        p.server.tell(new net.minecraft.server.TickTask(p.server.getTickCount() + 1, r));
    }

    /**
     * Armoire (Claim Block) posée pendant le tutoriel à sa place : tournée vers la porte et poussée dans l'angle, comme
     * le montre la case verte (ClaimBlockHandler.claim, 1.11.0).
     */
    public static void armoirePosee(ServerPlayer p, ServerLevel level, net.minecraft.core.BlockPos pos) {
        Cabane.Coin coin = enCours(p) ? coin(p, level) : null;
        if (coin != null && coin.claim().equals(pos)) {
            plusTard(p, () -> {
                Cabane.orienter(level, pos, coin.faceClaim(), coin.adosse());
                majGuide(p);
            });
        }
    }

    @Nullable
    private static Cabane.Coin coin(ServerPlayer p, ServerLevel level) {
        Building b = batiment(level, p, BasesDonnees.get(p.server).joueur(p.getUUID()));
        Piece[] fs = b == null ? null : Cabane.fondations(level, b);
        return fs == null ? null : Cabane.coin(fs[0], fs[1]);
    }

    /** Claim Block posé pendant le tutoriel (ClaimBlockHandler.claim) : seulement à son étape, sur une fondation de la cabane. */
    @Nullable
    public static Component refusClaim(ServerLevel level, ServerPlayer p, net.minecraft.core.BlockPos pos) {
        if (!enCours(p)) {
            return null;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        if (j.etape != BasesDonnees.Etape.PHASE || progression(p)[0] != Cabane.CLAIM) {
            return T.t("crac.tuto.refus.claim_tot", "Le Claim Block, pas maintenant : suis les consignes de Varga.");
        }
        Piece siege = StructureService.seatUnder(level, pos);
        Building b = batiment(level, p, j);
        if (siege == null || b == null || siege.buildingId != b.id) {
            return T.t("crac.tuto.refus.claim_place", "Pose le Claim Block sur une fondation de ta cabane, à l'intérieur.");
        }
        return null;
    }

    /**
     * Claim Block ouvert pendant le tutoriel (ClaimBlockLogique.utiliser) : l'entretien attend son étape (les planches
     * des améliorations partiraient dedans). Vrai = refusé (le joueur est prévenu).
     */
    public static boolean entretienTropTot(ServerPlayer p) {
        if (!enCours(p) || progression(p)[0] >= Cabane.ENTRETIEN) {
            return false;
        }
        avertir(p, T.t("crac.tuto.refus.entretien", "L'entretien plus tard : suis les consignes de Varga."));
        return true;
    }

    /** Pièce démolie pendant le tutoriel (StructureService.demolish) : ce qu'elle a coûté est rendu, rien ne se perd. */
    public static void rembourser(ServerPlayer p, Piece piece) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        ajouter(m, Costs.place(piece.kind()));
        if (piece.tier.ordinal() >= Tier.WOOD.ordinal()) {
            ajouter(m, Costs.upgrade(piece.kind(), Tier.WOOD));
        }
        m.forEach((item, n) -> {
            int max = Math.max(1, new ItemStack(item).getMaxStackSize());
            for (int reste = n; reste > 0; reste -= max) {
                p.getInventory().placeItemBackInInventory(new ItemStack(item, Math.min(max, reste)));
            }
        });
    }

    private static Component refus(ServerPlayer p, MutableComponent texte) {
        return texte.withStyle(ChatFormatting.YELLOW);
    }

    /** Avertissement dans la barre d'action, au plus un toutes les 2 secondes. */
    private static void avertir(ServerPlayer p, MutableComponent texte) {
        long now = System.currentTimeMillis();
        Long avant = AVERTI.get(p.getUUID());
        if (avant == null || now - avant > 2000L) {
            AVERTI.put(p.getUUID(), now);
            T.barre(p, texte, ChatFormatting.YELLOW);
        }
    }

    // ---------------------------------------------------------------------------------------------- verrous du tutoriel

    /** Rien ne se jette pendant le tutoriel : l'objet revient dans l'inventaire. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void jeter(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !enCours(p)) {
            return;
        }
        ItemStack s = e.getEntity().getItem().copy();
        e.setCanceled(true);
        if (!p.getInventory().add(s) && !s.isEmpty()) {
            // inventaire plein (objet tenu à la souris) : remis à la souris
            p.containerMenu.setCarried(s);
        }
        p.containerMenu.broadcastChanges();
        avertir(p, T.t("crac.tuto.refus.jeter", "Garde ton matériel : tu en as besoin pour ta cabane."));
    }

    /**
     * Aucun bloc ne se pose pendant le tutoriel (les planches du kit sont comptées), sauf le Claim Block et, à son
     * étape, la caisse en métal.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void poser(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !enCours(p) || e.getPlacedBlock().getBlock() instanceof ClaimBlock) {
            return;
        }
        // la caisse est déjà dans le monde quand l'événement part : la cabane se lit alors finie
        if (e.getPlacedBlock().getBlock() instanceof MetalCrateBlock && progression(p)[0] >= Cabane.COFFRE) {
            // seulement contre le Claim Block (la case verte) : jamais au milieu du passage, devant la porte
            ServerLevel bases = p.server.getLevel(MondeBases.BASES);
            Building b = bases == null ? null : batiment(bases, p, BasesDonnees.get(p.server).joueur(p.getUUID()));
            Claim c = b == null ? null : StructureService.claimOf(bases, b);
            if (c == null || Math.abs(e.getPos().getX() - c.anchor().getX()) + Math.abs(e.getPos().getZ() - c.anchor().getZ()) <= 2
                && Math.abs(e.getPos().getY() - c.anchor().getY()) <= 1) {
                // à sa place prévue : tournée vers la pièce et collée au mur, à côté de l'armoire (1.11.0)
                Cabane.Coin coin = bases == null ? null : coin(p, bases);
                net.minecraft.core.BlockPos pos = e.getPos().immutable();
                if (coin != null && coin.caisse().equals(pos)) {
                    plusTard(p, () -> Cabane.orienter(bases, pos, coin.face(), coin.adosse()));
                }
                return;
            }
            e.setCanceled(true);
            avertir(p, T.t("crac.tuto.refus.caisse", "Pose la caisse sur la case verte, à côté du Claim Block."));
            return;
        }
        e.setCanceled(true);
        avertir(p, T.t("crac.tuto.refus.bloc", "Pas de blocs pendant le tutoriel : construis avec le plan."));
    }

    /** Commandes qui feraient quitter le tutoriel : refusées jusqu'à la fin. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void commandeBloquee(CommandEvent e) {
        CommandContextBuilder<CommandSourceStack> b = e.getParseResults().getContext();
        if (b.getNodes().isEmpty() || !(b.getSource().getEntity() instanceof ServerPlayer p) || !enCours(p)
            || !COMMANDES_BLOQUEES.contains(b.getNodes().get(0).getNode().getName())) {
            return;
        }
        e.setCanceled(true);
        T.barre(p, T.t("crac.tuto.refus.commande", "Pas avant d'avoir fini ta base (tutoriel)."), ChatFormatting.YELLOW);
    }

    /** Pas d'artisanat pendant le tutoriel : ce qui est posé dans une grille revient dans l'inventaire. */
    @SubscribeEvent
    public static void tickJoueur(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !enCours(p)) {
            return;
        }
        // pas de faim pendant le tutoriel : barre toujours pleine, rien ne s'use (le nouveau court sans manger)
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5F);
        p.getFoodData().setExhaustion(0F);
        if (p.tickCount % 5 != 0) {
            return;
        }
        boolean rendu = false;
        for (Slot s : p.containerMenu.slots) {
            Container c = s.container;
            if (c instanceof CraftingContainer && s.hasItem()) {
                ItemStack x = c.removeItemNoUpdate(s.getContainerSlot());
                p.getInventory().placeItemBackInInventory(x);
                p.containerMenu.slotsChanged(c);
                rendu = true;
            }
        }
        if (rendu) {
            p.containerMenu.broadcastChanges();
            avertir(p, T.t("crac.tuto.refus.artisanat", "Pas d'artisanat pendant le tutoriel : ton matériel est compté."));
        }
    }

    // ---------------------------------------------------------------------------------------------- kit

    /** Le kit : plan, marteau, Claim Block, caisse en métal, matières de la cabane (coûts réels) avec la marge. */
    static final int PLANCHES_EN_PLUS = 10;

    static List<ItemStack> contenuKit() {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(ModItems.BUILDING_PLAN.get(), 1);
        m.put(ModItems.BUILDING_HAMMER.get(), 1);
        m.put(ModItems.CLAIM_BLOCK.get(), 1);
        // la caisse en métal de CRac : le premier rangement de la base (dernière étape du tutoriel)
        m.put(ModItems.METAL_CRATE.get(), 1);
        // la porte est dans la pièce « Porte » (cadre + battant) : pas d'objet porte à donner
        Map<Item, Integer> matieres = new LinkedHashMap<>();
        int pieces = 0;
        for (Map.Entry<PieceKind, Integer> e : CABANE.entrySet()) {
            pieces += e.getValue();
            for (int i = 0; i < e.getValue(); i++) {
                ajouter(matieres, Costs.place(e.getKey()));
                if (BasesReglages.b(BasesReglages.AMELIORATION_BOIS, true)) {
                    ajouter(matieres, Costs.upgrade(e.getKey(), Tier.WOOD));
                }
            }
        }
        double marge = 1.0 + BasesReglages.i(BasesReglages.MARGE_POURCENT, 10) / 100.0;
        matieres.replaceAll((item, n) -> (int) Math.ceil(n * marge - 1e-9));
        // entretien du Claim Block : pièces en bois (ou brindilles) payées en « itemWood »
        int heures = BasesReglages.i(BasesReglages.ENTRETIEN_HEURES, 24);
        if (heures > 0 && Config.SPEC.isLoaded()) {
            Item bois = Config.upkeepItem(UpkeepResource.WOOD);
            int n = (int) Math.ceil(Config.upkeepPerPiece(UpkeepResource.WOOD) * pieces * heures / Config.upkeepHours.get());
            if (bois != Items.AIR && n > 0) {
                matieres.merge(bois, n, Integer::sum);
            }
        }
        // 10 planches en plus (20/10, demande du propriétaire : de quoi réparer et entretenir un peu plus)
        Item planche = Config.SPEC.isLoaded() ? Config.upkeepItem(UpkeepResource.WOOD) : Items.AIR;
        if (planche != Items.AIR) {
            matieres.merge(planche, PLANCHES_EN_PLUS, Integer::sum);
        }
        matieres.forEach((item, n) -> m.merge(item, n, Integer::sum));
        List<ItemStack> out = new ArrayList<>();
        m.forEach((item, n) -> {
            int max = Math.max(1, new ItemStack(item).getMaxStackSize());
            for (int reste = n; reste > 0; reste -= max) {
                out.add(new ItemStack(item, Math.min(max, reste)));
            }
        });
        return out;
    }

    private static void ajouter(Map<Item, Integer> m, List<BuildCost> couts) {
        for (BuildCost c : couts) {
            if (!c.isValid()) {
                continue;
            }
            ItemStack[] choix = c.ingredient().getItems();
            if (choix.length == 0) {
                continue;
            }
            Item item = choix[0].getItem();
            // pour une liste (#minecraft:planks...), les planches de chêne si elles en sont
            for (ItemStack s : choix) {
                if (s.is(Items.OAK_PLANKS) || s.is(Items.OAK_LOG) || s.is(Items.COBBLESTONE)) {
                    item = s.getItem();
                    break;
                }
            }
            m.merge(item, c.count(), Integer::sum);
        }
    }

    private static Map<Item, Integer> totaux(List<ItemStack> kit) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        for (ItemStack s : kit) {
            m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    private static void donner(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) {
            p.drop(s, false);
        }
    }

    /** Le groupe CRac du joueur ; s'il n'en a pas, celui de son clan (chef) ou un groupe à son nom, créé ici. */
    private static Group groupe(ServerPlayer p) {
        GroupData data = GroupData.get(p.server);
        Group g = data.getByMember(p.getUUID());
        if (g == null) {
            g = ClanAccess.groupForClanManager(p.server, p.getUUID());
        }
        if (g != null) {
            return g;
        }
        String nom = GroupNames.cleanName(p.getGameProfile().getName(), Config.groupNameMaxLength.get());
        if (nom == null) {
            nom = "Base";
        }
        String tag = null;
        String lettres = p.getGameProfile().getName().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (lettres.length() >= GroupNames.TAG_MIN) {
            String t = lettres.substring(0, Math.min(GroupNames.TAG_MAX, lettres.length()));
            if (!data.tagTaken(t)) {
                tag = t;
            }
        }
        for (int i = 0; tag == null && i < 200; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < GroupNames.TAG_MAX; k++) {
                sb.append((char) ('A' + p.getRandom().nextInt(26)));
            }
            if (!data.tagTaken(sb.toString())) {
                tag = sb.toString();
            }
        }
        if (tag == null) {
            return null;
        }
        g = data.create(nom, tag, 0x55E07A, p.getUUID());
        return g;
    }

    // ---------------------------------------------------------------------------------------------- commande

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tuto").requires(s -> s.hasPermission(2))
            .then(Commands.literal("rejouer").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> rejouer(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
            .then(Commands.literal("lancer").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> lancer(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
            .then(Commands.literal("finir").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> finir(c.getSource(), EntityArgument.getPlayer(c, "joueur"))))));
    }

    /** Staff : libère un joueur du tutoriel (en cas de souci), comme s'il l'avait fini. */
    private static int finir(CommandSourceStack src, ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(src.getServer());
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        j.etape = BasesDonnees.Etape.FINI;
        j.batiment = -1L;
        d.marquer();
        oublier(p.getUUID());
        effacerGuide(p);
        Reply.ok(src, T.t("crac.tuto.fini_staff", "Tutoriel de %s terminé : il est libre.", p.getGameProfile().getName())
            .withStyle(ChatFormatting.GREEN));
        Crac.LOGGER.info("[CRac] tutoriel de {} terminé par le staff ({})", p.getGameProfile().getName(), src.getTextName());
        return 1;
    }

    private static int lancer(CommandSourceStack src, ServerPlayer p) {
        if (src.getServer().getLevel(MondeBases.BASES) == null) {
            Reply.fail(src, T.t("crac.bases.absent", "Le monde des bases n'existe pas sur ce serveur (crac:bases)."));
            return 0;
        }
        boolean base = aUneBase(p);
        lancer(p);
        Reply.ok(src, (base
            ? T.t("crac.tuto.lance_base", "Tutoriel lancé pour %s : titres, kit, puis envoi dans le monde des bases. Il a déjà une base : pas de nouvelle protection de base.",
                p.getGameProfile().getName())
            : T.t("crac.tuto.lance", "Tutoriel lancé pour %s : titres, kit, puis envoi dans le monde des bases.", p.getGameProfile().getName()))
            .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * Cinématique rejouée par le staff ({@code /cinematique} pour soi, {@code /cinematique joueur <cibles>}) : le tutoriel
     * du joueur repart à la fin de la cinématique (attente comme à la première arrivée), même en créatif, même s'il l'a
     * déjà fait ou a une base. Avant, rejouer la cinématique ne changeait rien au tutoriel. Lu avant la commande
     * (évènement de Forge) ; une commande mal tapée ne change rien.
     */
    @SubscribeEvent
    public static void commande(CommandEvent e) {
        try {
            ParseResults<CommandSourceStack> r = e.getParseResults();
            CommandContextBuilder<CommandSourceStack> b = r.getContext();
            if (b.getNodes().isEmpty() || !"cinematique".equals(b.getNodes().get(0).getNode().getName())
                || r.getReader().canRead() || !r.getExceptions().isEmpty() || !actif() || parVarga()) {
                return;
            }
            CommandSourceStack src = b.getSource();
            if (!src.hasPermission(2) || src.getServer().getLevel(MondeBases.BASES) == null) {
                return;
            }
            List<ServerPlayer> cibles = new ArrayList<>();
            if (b.getNodes().size() == 1) {
                // pour soi, la cinématique ne se rejoue que depuis le spawn (sinon Utils la refuse)
                if (src.getEntity() instanceof ServerPlayer sp && MondeBases.estSpawn(sp.level())) {
                    cibles.add(sp);
                }
            } else if ("joueur".equals(b.getNodes().get(1).getNode().getName()) && b.getArguments().containsKey("cibles")) {
                CommandContext<CommandSourceStack> ctx = b.build(r.getReader().getString());
                cibles.addAll(EntityArgument.getPlayers(ctx, "cibles"));
            }
            long now = src.getServer().overworld().getGameTime();
            for (ServerPlayer p : cibles) {
                SEQUENCES.remove(p.getUUID());
                FORCES.remove(p.getUUID());
                ATTENTES.put(p.getUUID(), new Attente(now, p.position(), true));
                Reply.ok(src, T.t("crac.tuto.apres_cinematique", "Le tutoriel de %s repartira à la fin de la cinématique (/tuto lancer %s pour tout de suite).",
                    p.getGameProfile().getName(), p.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
            }
        } catch (CommandSyntaxException | RuntimeException ex) {
            Crac.LOGGER.debug("[CRac] tutoriel : lecture de /cinematique impossible ({})", ex.toString());
        }
    }

    private static int rejouer(CommandSourceStack src, ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(src.getServer());
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        boolean base = aUneBase(p);
        j.etape = BasesDonnees.Etape.AUCUNE;
        j.phaseTicks = 0;
        // un joueur qui a déjà une base ne regagne pas la protection de base par cette commande (/protection donner)
        j.protectionDue = !base;
        j.batiment = -1L;
        j.arete = -1;
        d.marquer();
        oublier(p.getUUID());
        effacerGuide(p);
        fr.heraliz.crac.heraliz.Reply.ok(src, (base
            ? T.t("crac.tuto.rejouer_base", "%s a déjà une base : le tutoriel ne repartira que s'il la perd.", p.getGameProfile().getName())
            : T.t("crac.tuto.rejouer", "Tutoriel remis à zéro pour %s : il repart au spawn.", p.getGameProfile().getName()))
            .withStyle(base ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        if (!base && MondeBases.estSpawn(p.level()) && actif()) {
            SEQUENCES.put(p.getUUID(), 0);
        }
        return 1;
    }
}
