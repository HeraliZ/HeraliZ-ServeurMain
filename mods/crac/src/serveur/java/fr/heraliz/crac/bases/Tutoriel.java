package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupNames;
import fr.heraliz.crac.heraliz.ClanAccess;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * Tutoriel du monde des bases, après la cinématique d'arrivée de HeraliZ Utils, pour un joueur qui n'a jamais eu de base.
 * <ol>
 *   <li>Au spawn ({@code heralisz:spawn}), quand la cinématique a commencé (donnée persistante
 *   {@code heraliz_cinematique_vue} de Utils, lue par son nom) : on attend {@code [tuto] attente_min_s} secondes puis le
 *   premier pas du joueur, ou au plus {@code attente_max_s} secondes (la cinématique dure environ 122 s ; Utils n'envoie
 *   pas d'évènement de fin).</li>
 *   <li>Titres à l'écran, puis le kit : de quoi bâtir avec CRac une cabane 2 × 1 avec une porte (2 fondations, 5 murs,
 *   1 pièce « Porte », 2 planchers en toit), calculé avec les coûts réels de crac-building.toml, plus la mise en bois au
 *   marteau, un jour d'entretien du Claim Block et une marge de 10 % ; un groupe CRac est créé pour lui s'il n'en a pas.</li>
 *   <li>Envoi dans le monde des bases, à un point plat tiré au hasard loin des autres bases : il y est protégé
 *   ({@link Protections}) tant que sa base n'est pas posée.</li>
 * </ol>
 * Staff : {@code /tuto rejouer <joueur>}.
 */
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

    private record Attente(long debut, Vec3 pos) {
    }

    /** Joueurs qui attendent la fin de la cinématique (heure de jeu du début, position). */
    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    /** Séquences en cours (tick de la séquence), étape KIT comprise. */
    private static final Map<UUID, Integer> SEQUENCES = new HashMap<>();

    private static final int T_TITRE_2 = 80;
    private static final int T_TITRE_3 = 170;
    private static final int T_ENVOI = 230;

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
            UUID id = p.getUUID();
            if (p.isSpectator() || p.isCreative() || SEQUENCES.containsKey(id)) {
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
                    if (!MondeBases.estSpawn(p.level()) || !cinematiqueVue(p)) {
                        ATTENTES.remove(id);
                        continue;
                    }
                    Attente a = ATTENTES.computeIfAbsent(id, k -> new Attente(now, p.position()));
                    long ecoule = (now - a.debut()) / 20L;
                    boolean bouge = p.position().distanceToSqr(a.pos()) > 1.0;
                    if (ecoule >= BasesReglages.i(BasesReglages.ATTENTE_MAX_S, 130)
                        || ecoule >= BasesReglages.i(BasesReglages.ATTENTE_MIN_S, 15) && bouge) {
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
                default -> ATTENTES.remove(id);
            }
        }
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
            if (p == null || !MondeBases.estSpawn(p.level()) || !p.isAlive()) {
                it.remove();
                continue;
            }
            int t = e.getValue();
            if (t == 0) {
                actions.add(() -> titre1(p));
            } else if (t == T_TITRE_2) {
                actions.add(() -> kit(p));
            } else if (t == T_TITRE_3) {
                actions.add(() -> titre3(p));
            } else if (t >= T_ENVOI) {
                it.remove();
                actions.add(() -> envoyer(p));
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
        ATTENTES.remove(joueur);
        SEQUENCES.remove(joueur);
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
        T.titre(p, T.t("crac.tuto.titre2", "Ta première base").withStyle(ChatFormatting.GOLD),
            T.t("crac.tuto.sous2", "Voici de quoi bâtir une cabane 2 × 1 avec une porte."), 80);
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
            j.protectionDue = true;
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

    private static void envoyer(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return;
        }
        Vec3 dest = Points.auHasard(bases, p);
        if (dest == null) {
            T.chat(p, T.t("crac.home.pas_de_place", "Aucune place sûre trouvée dans le monde des bases : réessaie dans un instant."),
                ChatFormatting.RED);
            return;   // étape KIT : nouvel essai à la prochaine seconde
        }
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        p.stopRiding();
        p.teleportTo(bases, dest.x, dest.y, dest.z, p.getYRot(), 0f);
        p.resetFallDistance();
        j.etape = BasesDonnees.Etape.PHASE;
        j.phaseTicks = 0;
        d.marquer();
        long max = BasesReglages.i(BasesReglages.PHASE_MAX_MIN, 120) * 60_000L;
        int fond = BasesReglages.i(BasesReglages.FONDATIONS_MIN, 2);
        T.chat(p, T.t("crac.tuto.etape1", "[Tuto] 1. Trouve un coin plat qui te plaît. Le monde des bases est en PvP : seul ton contour vert te protège, pendant %s au plus.",
            T.duree(max)), ChatFormatting.YELLOW);
        T.chat(p, T.t("crac.tuto.etape2", "[Tuto] 2. Plan de construction en main, clic droit : pose %s fondations côte à côte.",
            String.valueOf(fond)), ChatFormatting.YELLOW);
        T.chat(p, T.t("crac.tuto.etape3", "[Tuto] 3. Puis 5 murs, 1 porte (pièce « Porte » du plan, la porte est comprise) et 2 planchers par-dessus pour le toit."),
            ChatFormatting.YELLOW);
        T.chat(p, T.t("crac.tuto.etape4", "[Tuto] 4. Pose le Claim Block sur une fondation, à l'intérieur : ta base est à toi, et protégée des raids."),
            ChatFormatting.YELLOW);
        T.chat(p, T.t("crac.tuto.etape5", "[Tuto] 5. Marteau en main : passe tes pièces en bois, et mets des planches dans le Claim Block (entretien)."),
            ChatFormatting.YELLOW);
        T.chat(p, T.t("crac.tuto.etape6", "[Tuto] Depuis ta base, /spawn ramène au spawn ; du spawn, /home ramène chez toi (toutes les 3 min)."),
            ChatFormatting.GRAY);
    }

    // ---------------------------------------------------------------------------------------------- kit

    /** Le kit : plan, marteau, Claim Block, porte en chêne, matières de la cabane (coûts réels) avec la marge. */
    static List<ItemStack> contenuKit() {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(ModItems.BUILDING_PLAN.get(), 1);
        m.put(ModItems.BUILDING_HAMMER.get(), 1);
        m.put(ModItems.CLAIM_BLOCK.get(), 1);
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
                .executes(c -> rejouer(c.getSource(), EntityArgument.getPlayer(c, "joueur"))))));
    }

    private static int rejouer(CommandSourceStack src, ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(src.getServer());
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        boolean base = aUneBase(p);
        j.etape = BasesDonnees.Etape.AUCUNE;
        j.phaseTicks = 0;
        // un joueur qui a déjà une base ne regagne pas la protection de base par cette commande (/protection donner)
        j.protectionDue = !base;
        d.marquer();
        oublier(p.getUUID());
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
