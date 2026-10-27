package fr.heraliz.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Butin des zombies de la survie (26/10, demande du propriétaire) : un tirage par zombie dans une table propre aux
 * zombies ({@code coffres/zombies.json}, {@code zombies_militaire.json}, même moteur que les conteneurs :
 * {@link TablesCoffres}), plus les fragments de cartes d'accès tirés à part. Tables et règles écrites par
 * {@code outils/loot_zombies.py} ; règles dans {@code config/heraliz_loot/zombies.json}, relues avec les tables.
 * <p>
 * Un zombie ne compte (butin, quêtes du jour) que si TOUT est vrai, vérifié ici côté serveur :
 * <ul>
 *   <li>son type est dans {@code entites} et il porte une étiquette d'{@code origines} (peuplement, apparition naturelle) :
 *   ni /summon, ni œuf, ni générateur, ni vagues de capture, ni banc d'essai, ni zombie « recréé » à la main ;</li>
 *   <li>il est tué par un vrai joueur (auteur du coup, ou tireur d'un projectile) en survie ou en aventure, ni bot ni en
 *   match, sur la carte, à {@code distance_max} blocs au plus ;</li>
 *   <li>les joueurs lui ont fait au moins {@code part_joueur_min} de ses dégâts (pas de fermes à chute, feu, cactus...) ;</li>
 *   <li>une seule fois par zombie (marque posée avant de donner quoi que ce soit).</li>
 * </ul>
 * Anti-farm, par joueur : au-delà de {@code kills_heure_plein} zombies dans l'heure (moyenne glissante), puis au-delà de
 * {@code kills_case_plein} dans un même carré de {@code case_blocs} blocs, les chances baissent jusqu'à {@code chance_min}.
 * Fragments : {@code fragments_max_jour} par joueur et par jour (heure de Paris). Compteurs gardés sur le disque
 * ({@code heraliz_loot/zombies_etat.json} à la racine du serveur) : une déconnexion ou un redémarrage ne les remet pas à
 * zéro. Rien ne tourne entre deux morts de zombie, sauf une écriture par minute si quelque chose a changé.
 */
public final class ButinZombies {
    private ButinZombies() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** Marques posées sur le zombie (données persistantes). */
    private static final String FAIT = "heraliz_butin_fait", DEG_J = "heraliz_butin_dj", DEG_T = "heraliz_butin_dt";
    private static final String[] COULEURS = {"green", "blue", "red"};

    /** Règles lues dans config/heraliz_loot/zombies.json (valeurs par défaut = celles de l'outil). */
    record Regles(Set<EntityType<?>> entites, List<String> origines, List<String> exclus, Map<String, String> tables, double partJoueurMin,
                  double distanceMax, double heurePlein, double heureZero, double chanceMin, int caseBlocs, double caseMinutes,
                  double casePlein, double caseZero, double[] fragments, int fragmentsMaxJour) {
    }

    private static volatile Regles regles;
    private static final Set<EntityType<?>> TYPES = new HashSet<>();

    /** Écouteurs d'un vrai kill (joueur, tenue du zombie) : quêtes du jour (HeraliZ Quêtes, par réflexion). */
    private static final List<BiConsumer<ServerPlayer, String>> ECOUTEURS = new CopyOnWriteArrayList<>();

    /**
     * Appelé à chaque zombie qui compte vraiment pour son tueur (voir l'en-tête), même si l'anti-farm a coupé son butin.
     * Deuxième argument : la tenue du zombie ({@code MILITAIRE}, {@code VILLE_0}...) ou « » si inconnue. API lue par
     * réflexion par d'autres mods : ne jamais renommer.
     */
    public static void ecouter(BiConsumer<ServerPlayer, String> f) {
        ECOUTEURS.add(f);
    }

    // ------------------------------------------------------------------ état par joueur (anti-farm, fragments, stats)
    private static final class Joueur {
        double charge;
        long t;
        String jour = "";
        int fragmentsJour;
        final Map<Long, double[]> cases = new HashMap<>();
    }

    private static final Map<UUID, Joueur> JOUEURS = new HashMap<>();
    /** Statistiques depuis le démarrage (pour /heralizloot zombies stats). */
    private static long kills, butins, coupes, fragments, debutStats;
    private static final Map<String, Long> OBJETS_SORTIS = new HashMap<>();
    private static boolean sale;
    private static Path fichier;

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(ButinZombies::degats);
        MinecraftForge.EVENT_BUS.addListener(ButinZombies::mort);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> demarrer(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            ecrire();
            JOUEURS.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 1200 != 0) return;
            if (ECHEC.getAndSet(false)) sale = true;
            if (sale) ecrire();
        });
    }

    /** Écriture en fond ratée (fil d'écriture du loot) : l'état redevient « à sauver ». */
    private static final java.util.concurrent.atomic.AtomicBoolean ECHEC = new java.util.concurrent.atomic.AtomicBoolean();

    // ------------------------------------------------------------------ règles
    /** Relit config/heraliz_loot/zombies.json (avec les tables, /heralizloot tables) ; un fichier illisible garde les anciennes. */
    static void charger() {
        Path f = TablesCoffres.dossierConfig().getParent().resolve("zombies.json");
        JsonObject o = new JsonObject();
        if (Files.exists(f)) {
            try {
                o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                HeralizLoot.LOG.error("{} illisible : règles du butin des zombies inchangées", f, ex);
                if (regles != null) return;
                o = new JsonObject();
            }
        }
        Set<EntityType<?>> types = new HashSet<>();
        for (String id : liste(o, "entites", List.of("heralisz_utils:zombie_survie"))) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) types.add(ForgeRegistries.ENTITY_TYPES.getValue(rl));
        }
        Map<String, String> tables = new HashMap<>();
        if (o.has("tables")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("tables").entrySet()) tables.put(e.getKey(), e.getValue().getAsString());
        } else {
            tables.put("MILITAIRE", "zombies_militaire");
            tables.put("*", "zombies");
        }
        double[] frag = new double[3];
        JsonObject fo = o.has("fragments") ? o.getAsJsonObject("fragments") : new JsonObject();
        for (int i = 0; i < 3; i++) frag[i] = Mth.clamp(nombre(fo, COULEURS[i], 0), 0, 1);
        regles = new Regles(Set.copyOf(types), liste(o, "origines", List.of("heraliz_peuple", "heraliz_naturel")),
                liste(o, "exclus", List.of("heraliz_assaut", "heraliz_banc_zombie", "heraliz_bot")), Map.copyOf(tables),
                nombre(o, "part_joueur_min", 0.5), nombre(o, "distance_max", 96), nombre(o, "kills_heure_plein", 100),
                nombre(o, "kills_heure_zero", 200), Mth.clamp(nombre(o, "chance_min", 0), 0, 1), Math.max(8, (int) nombre(o, "case_blocs", 32)),
                Math.max(1, nombre(o, "case_minutes", 20)), nombre(o, "kills_case_plein", 25), nombre(o, "kills_case_zero", 60), frag,
                (int) nombre(o, "fragments_max_jour", 6));
        TYPES.clear();
        TYPES.addAll(types);
        HeralizLoot.LOG.info("Butin des zombies : {} type(s), fragments vert {} / bleu {} / rouge {} par zombie", types.size(), frag[0], frag[1], frag[2]);
    }

    private static List<String> liste(JsonObject o, String cle, List<String> defaut) {
        if (!o.has(cle)) return defaut;
        List<String> l = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(cle)) l.add(e.getAsString());
        return List.copyOf(l);
    }

    private static double nombre(JsonObject o, String cle, double defaut) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsDouble() : defaut;
    }

    // ------------------------------------------------------------------ dégâts : part des joueurs
    private static void degats(LivingDamageEvent e) {
        LivingEntity v = e.getEntity();
        if (!TYPES.contains(v.getType()) || v.level().isClientSide) return;
        float d = Math.max(0, Math.min(e.getAmount(), v.getHealth()));
        if (d <= 0) return;
        CompoundTag pd = v.getPersistentData();
        pd.putFloat(DEG_T, pd.getFloat(DEG_T) + d);
        if (e.getSource().getEntity() instanceof ServerPlayer) pd.putFloat(DEG_J, pd.getFloat(DEG_J) + d);
    }

    // ------------------------------------------------------------------ mort
    private static void mort(LivingDropsEvent e) {
        LivingEntity v = e.getEntity();
        Regles r = regles;
        if (r == null || !TYPES.contains(v.getType()) || !(v.level() instanceof ServerLevel lvl)) return;
        CompoundTag pd = v.getPersistentData();
        if (pd.getBoolean(FAIT)) return;
        pd.putBoolean(FAIT, true);
        ServerPlayer p = tueur(v, e, r);
        if (p == null) return;
        String tenue = tenue(v);
        kills++;
        for (BiConsumer<ServerPlayer, String> f : ECOUTEURS) {
            try {
                f.accept(p, tenue);
            } catch (RuntimeException ex) {
                HeralizLoot.LOG.error("Écouteur des kills de zombies en erreur", ex);
            }
        }
        long maintenant = System.currentTimeMillis();
        Joueur j = JOUEURS.computeIfAbsent(p.getUUID(), u -> new Joueur());
        double chance = chance(j, r, v.blockPosition(), maintenant);
        sale = true;
        RandomSource rnd = lvl.getRandom();
        if (chance <= 0 || rnd.nextDouble() >= chance) {
            coupes += chance < 1 ? 1 : 0;
            return;
        }
        List<ItemStack> sortis = new ArrayList<>();
        TablesCoffres.Table t = TablesCoffres.table(table(r, tenue));
        if (t != null) {
            ItemStack st = TablesCoffres.tirer(t, rnd);
            if (!st.isEmpty()) sortis.add(st);
        }
        // fragments : tirés à part, à chance pleine (la baisse de l'anti-farm est déjà passée au-dessus)
        String jour = LocalDate.now(PARIS).toString();
        if (!jour.equals(j.jour)) {
            j.jour = jour;
            j.fragmentsJour = 0;
        }
        for (int i = 0; i < 3; i++) {
            if (r.fragments()[i] <= 0 || j.fragmentsJour >= r.fragmentsMaxJour() || rnd.nextDouble() >= r.fragments()[i]) continue;
            Item it = fragment(i, 1 + rnd.nextInt(4));
            if (it == null) continue;
            j.fragmentsJour++;
            fragments++;
            sortis.add(new ItemStack(it));
        }
        if (sortis.isEmpty()) return;
        butins++;
        for (ItemStack st : sortis) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
            OBJETS_SORTIS.merge(id == null ? "?" : id.toString(), 1L, Long::sum);
            ItemEntity ie = new ItemEntity(lvl, v.getX(), v.getY() + 0.3, v.getZ(), st);
            ie.setDefaultPickUpDelay();
            e.getDrops().add(ie);
        }
    }

    /** Le joueur à qui revient ce zombie, ou null s'il ne compte pas (voir l'en-tête). */
    @Nullable
    private static ServerPlayer tueur(LivingEntity v, LivingDropsEvent e, Regles r) {
        if (v.level().dimension() != Level.OVERWORLD) return null;
        Set<String> tags = v.getTags();
        boolean origine = false;
        for (String o : r.origines()) origine |= tags.contains(o);
        if (!origine) return null;
        for (String x : r.exclus()) if (tags.contains(x)) return null;
        Entity src = e.getSource().getEntity();
        if (!(src instanceof ServerPlayer p) || !p.isAlive() || p.level() != v.level()) return null;
        if (p.isCreative() || p.isSpectator() || p.getTags().contains("heraliz_bot")) return null;
        if (p.getPersistentData().getBoolean("heralisz:en_match")) return null;
        if (p.distanceToSqr(v) > r.distanceMax() * r.distanceMax()) return null;
        CompoundTag pd = v.getPersistentData();
        float total = pd.getFloat(DEG_T), joueurs = pd.getFloat(DEG_J);
        if (total <= 0 || joueurs < total * r.partJoueurMin()) return null;
        return p;
    }

    /** Chance de butin (0 à 1) après l'anti-farm ; compte ce kill dans la charge du joueur et de sa case. */
    private static double chance(Joueur j, Regles r, BlockPos pos, long maintenant) {
        // charge horaire : moyenne glissante (décroissance exponentielle, 1 h) ; en régime, = kills par heure
        j.charge = j.charge * Math.exp(-Math.max(0, maintenant - j.t) / 3_600_000.0) + 1;
        j.t = maintenant;
        long cle = ((long) Math.floorDiv(pos.getX(), r.caseBlocs()) << 32) | (Math.floorDiv(pos.getZ(), r.caseBlocs()) & 0xFFFFFFFFL);
        double tau = r.caseMinutes() * 60_000.0;
        double[] c = j.cases.computeIfAbsent(cle, k -> new double[2]);
        c[0] = c[0] * Math.exp(-Math.max(0, maintenant - (long) c[1]) / tau) + 1;
        c[1] = maintenant;
        if (j.cases.size() > 64) {
            // ménage : cases oubliées (moins d'un kill en mémoire)
            for (Iterator<Map.Entry<Long, double[]>> it = j.cases.entrySet().iterator(); it.hasNext(); ) {
                double[] x = it.next().getValue();
                if (x[0] * Math.exp(-Math.max(0, maintenant - (long) x[1]) / tau) < 1) it.remove();
            }
        }
        return Math.min(baisse(j.charge, r.heurePlein(), r.heureZero(), r.chanceMin()), baisse(c[0], r.casePlein(), r.caseZero(), r.chanceMin()));
    }

    private static double baisse(double n, double plein, double zero, double min) {
        if (n <= plein) return 1;
        if (zero <= plein || n >= zero) return min;
        return 1 - (1 - min) * (n - plein) / (zero - plein);
    }

    private static String table(Regles r, String tenue) {
        String t = r.tables().get(tenue);
        return t != null ? t : r.tables().getOrDefault("*", "zombies");
    }

    @Nullable
    private static Item fragment(int couleur, int numero) {
        ResourceLocation rl = new ResourceLocation("heralisz_bunker", "keycard_fragment_" + COULEURS[couleur] + "_" + numero);
        return ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
    }

    private static final Map<Class<?>, Method> TENUE = new HashMap<>();

    /** Tenue du zombie (HeraliZ Utils, ZombieSurvie.tenue(), par réflexion) : « MILITAIRE », « VILLE_0 »... ou « ». */
    static String tenue(Entity v) {
        Method m = TENUE.computeIfAbsent(v.getClass(), c -> {
            try {
                return c.getMethod("tenue");
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        });
        if (m == null) return "";
        try {
            Object o = m.invoke(v);
            return o instanceof Enum<?> en ? en.name() : "";
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return "";
        }
    }

    // ------------------------------------------------------------------ disque
    private static void demarrer(MinecraftServer s) {
        fichier = s.getServerDirectory().toPath().resolve("heraliz_loot").resolve("zombies_etat.json");
        JOUEURS.clear();
        kills = butins = coupes = fragments = 0;
        OBJETS_SORTIS.clear();
        debutStats = System.currentTimeMillis();
        if (regles == null) charger();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("joueurs").entrySet()) {
                JsonObject x = e.getValue().getAsJsonObject();
                Joueur j = new Joueur();
                j.charge = x.get("charge").getAsDouble();
                j.t = x.get("t").getAsLong();
                j.jour = x.get("jour").getAsString();
                j.fragmentsJour = x.get("fragments_jour").getAsInt();
                if (x.has("cases")) {
                    for (JsonElement ce : x.getAsJsonArray("cases")) {
                        JsonArray a = ce.getAsJsonArray();
                        j.cases.put(a.get(0).getAsLong(), new double[]{a.get(1).getAsDouble(), a.get(2).getAsDouble()});
                    }
                }
                JOUEURS.put(UUID.fromString(e.getKey()), j);
            }
        } catch (Exception ex) {
            // fichier mis de côté, jamais écrasé : les compteurs repartent de zéro (au pire, un peu plus de butin)
            HeralizLoot.LOG.error("{} illisible : compteurs du butin des zombies repartis de zéro, fichier mis de côté", fichier, ex);
            try {
                Files.move(fichier, fichier.resolveSibling("zombies_etat.illisible.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
                // tant pis : il sera réécrit
            }
        }
    }

    /**
     * Relevé sur le fil serveur (arbre JSON neuf), mise en texte et écriture sur le fil d'écriture du loot (27/10, perf :
     * toutes les minutes, sur le fil serveur, avant).
     */
    private static void ecrire() {
        if (fichier == null || !sale) return;
        long maintenant = System.currentTimeMillis();
        String aujourdhui = LocalDate.now(PARIS).toString();
        JsonObject racine = new JsonObject(), joueurs = new JsonObject();
        JOUEURS.forEach((u, j) -> {
            // joueur oublié : moins d'un kill en mémoire et rien de compté aujourd'hui
            boolean ancien = j.charge * Math.exp(-Math.max(0, maintenant - j.t) / 3_600_000.0) < 1
                    && !j.jour.equals(aujourdhui);
            if (ancien) return;
            JsonObject x = new JsonObject();
            x.addProperty("charge", j.charge);
            x.addProperty("t", j.t);
            x.addProperty("jour", j.jour);
            x.addProperty("fragments_jour", j.fragmentsJour);
            JsonArray cs = new JsonArray();
            j.cases.forEach((k, c) -> {
                JsonArray a = new JsonArray();
                a.add(k);
                a.add(c[0]);
                a.add(c[1]);
                cs.add(a);
            });
            x.add("cases", cs);
            joueurs.add(u.toString(), x);
        });
        racine.add("joueurs", joueurs);
        Path f = fichier;
        sale = false;
        EcrituresFond.ecrire(f, () -> {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("zombies_etat.json.tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomiqueImpossible) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        }, () -> ECHEC.set(true));
    }

    // ------------------------------------------------------------------ commandes (op 2) : /heralizloot zombies ...
    static LiteralArgumentBuilder<CommandSourceStack> commandes() {
        return Commands.literal("zombies")
                .then(Commands.literal("stats").executes(c -> {
                    double h = Math.max(1, System.currentTimeMillis() - debutStats) / 3_600_000.0;
                    c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.zombies.stats",
                            "Depuis le démarrage (%s h) : %s zombies comptés, %s avec butin, %s coupés par l'anti-farm, %s fragments.",
                            String.format(java.util.Locale.ROOT, "%.1f", h), String.valueOf(kills), String.valueOf(butins),
                            String.valueOf(coupes), String.valueOf(fragments)), false);
                    return (int) kills;
                }))
                .then(Commands.literal("simuler").then(Commands.argument("nombre", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(c -> simuler(c.getSource(), IntegerArgumentType.getInteger(c, "nombre"), "*"))
                        .then(Commands.literal("militaire")
                                .executes(c -> simuler(c.getSource(), IntegerArgumentType.getInteger(c, "nombre"), "MILITAIRE")))))
                .then(Commands.literal("joueur").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                    Joueur j = JOUEURS.get(p.getUUID());
                    Regles r = regles;
                    long m = System.currentTimeMillis();
                    double charge = j == null ? 0 : j.charge * Math.exp(-Math.max(0, m - j.t) / 3_600_000.0);
                    double ch = r == null ? 1 : baisse(charge, r.heurePlein(), r.heureZero(), r.chanceMin());
                    c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.zombies.joueur",
                            "%s : %s zombies dans l'heure, chance de butin %s %%, %s fragment(s) aujourd'hui.", p.getDisplayName(),
                            String.format(java.util.Locale.ROOT, "%.0f", charge), String.format(java.util.Locale.ROOT, "%.0f", ch * 100),
                            String.valueOf(j == null ? 0 : j.fragmentsJour)), false);
                    return 1;
                })));
    }

    /**
     * Tire n zombies dans la vraie table (même moteur, mêmes chances que le jeu, sans anti-farm ni plafond) et donne les
     * comptes : sert à vérifier les probabilités annoncées (le butin n'est pas donné).
     */
    private static int simuler(CommandSourceStack s, int n, String tenue) {
        Regles r = regles;
        TablesCoffres.Table t = r == null ? null : TablesCoffres.table(table(r, tenue));
        if (t == null) {
            s.sendFailure(Component.translatableWithFallback("heraliz_loot.zombies.sans_table", "Table des zombies introuvable."));
            return 0;
        }
        RandomSource rnd = RandomSource.create();
        Map<String, Long> comptes = new HashMap<>();
        long rien = 0;
        long[] frag = new long[3];
        for (int i = 0; i < n; i++) {
            ItemStack st = TablesCoffres.tirer(t, rnd);
            if (st.isEmpty()) rien++;
            else comptes.merge(String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem())), 1L, Long::sum);
            for (int k = 0; k < 3; k++) if (rnd.nextDouble() < r.fragments()[k]) frag[k]++;
        }
        long rienF = rien;
        s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.zombies.simu",
                "%s zombies (%s) : rien %s %% ; fragments vert %s, bleu %s, rouge %s (attendu %s, %s, %s).", String.valueOf(n), tenue,
                String.format(java.util.Locale.ROOT, "%.1f", 100.0 * rienF / n), String.valueOf(frag[0]), String.valueOf(frag[1]),
                String.valueOf(frag[2]), String.format(java.util.Locale.ROOT, "%.1f", n * r.fragments()[0]),
                String.format(java.util.Locale.ROOT, "%.1f", n * r.fragments()[1]), String.format(java.util.Locale.ROOT, "%.1f", n * r.fragments()[2])), false);
        comptes.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(12).forEach(en ->
                s.sendSuccess(() -> Component.literal("  " + en.getKey() + " : " + en.getValue()
                        + String.format(java.util.Locale.ROOT, " (%.2f pour 100)", 100.0 * en.getValue() / n)), false));
        HeralizLoot.LOG.info("Simulation du butin de {} zombies ({}) : rien {}, fragments {}/{}/{}, objets {}", n, tenue, rienF, frag[0], frag[1], frag[2], comptes);
        return n;
    }
}
