package fr.heraliz.quetes.serveur;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * {@code /wipe tout} (op niveau 4 ou console, demande du propriétaire du 22/10) : remise à zéro complète du serveur,
 * comme un wipe de Rust. Triple vérification, toujours par la même personne : {@code /wipe tout} montre ce qui va
 * disparaître et un code ; {@code /wipe tout confirmer <code>} (dans la minute) donne un dernier avertissement et un
 * second code ; {@code /wipe tout valider <code>} (dans les 30 s) annonce à tous un compte à rebours de 10 s, que
 * {@code /wipe tout annuler} arrête ; puis tout part :
 * <ul>
 * <li>toutes les bases de CRac, contenu compris, et leurs données (homes, tutoriels, cœurs, groupes) ;</li>
 * <li>tous les clans dissous (banque et coffre perdus), hérals de tous les comptes à 0, saison et classement du
 * ClashWar effacés ;</li>
 * <li>objets au sol et cadavres des zones chargées ;</li>
 * <li>chaque joueur (en ligne : tout de suite ; sinon à sa prochaine connexion) : inventaire, coffre de l'Ender, Curios,
 * planque et coffre VIP, quêtes, personnage, cinématique, guide et tutoriel, comme {@code /wipe <joueur>}, puis
 * expulsé.</li>
 * </ul>
 * Gardés : objets à vie (rendus dans l'inventaire, y compris ceux trouvés dans sa base ou le coffre de son clan),
 * collection de camos, emotes débloquées, grades et permissions, comptes AuthMe, bans, la carte elle-même. Joueurs à
 * remettre à zéro et objets à rendre gardés hors du monde ({@code heraliz_quetes/wipe.dat}) : un redémarrage n'efface
 * rien.
 */
final class WipeServeur {
    private WipeServeur() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation CADAVRE = new ResourceLocation("heraliz_corps", "corpse");
    /** Triple vérification : étape atteinte (0 rien, 1 code donné, 2 dernier code donné), code attendu, sa fin, qui l'a demandé. */
    private static int etape;
    private static String code;
    private static long codeFin;
    private static String auteur = "";
    /** Compte à rebours lancé par la 3e étape : tick du lancement (-1 : aucun) et la source à qui répondre. */
    private static int lancementTick = -1;
    private static CommandSourceStack lanceur;
    /** Joueurs connectés à remettre à zéro, et le tick où le faire (laisse les autres mods finir leur connexion). */
    private static final Map<UUID, Integer> FILE = new HashMap<>();
    private static int ticks;
    private static final int COMPTE_A_REBOURS = 10;

    static LiteralArgumentBuilder<CommandSourceStack> commande() {
        return Commands.literal("tout")
                .executes(c -> annoncer(c.getSource()))
                .then(Commands.literal("confirmer").then(Commands.argument("code", StringArgumentType.word())
                        .executes(c -> confirmer(c.getSource(), StringArgumentType.getString(c, "code")))))
                .then(Commands.literal("valider").then(Commands.argument("code", StringArgumentType.word())
                        .executes(c -> valider(c.getSource(), StringArgumentType.getString(c, "code")))))
                .then(Commands.literal("annuler").executes(c -> annuler(c.getSource())));
    }

    private static String nouveauCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    /** 1re étape : ce qui va disparaître, et un premier code. */
    private static int annoncer(CommandSourceStack src) {
        if (lancementTick >= 0) return dejaLance(src);
        etape = 1;
        code = nouveauCode();
        codeFin = System.currentTimeMillis() + 60_000L;
        auteur = src.getTextName();
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.wipe.serveur.avertir",
                "REMISE À ZÉRO DU SERVEUR : toutes les bases, tous les clans, les hérals, la saison, le classement du ClashWar, "
                        + "et pour chaque joueur inventaire, coffres, quêtes, personnage et tutoriel. Gardés : objets à vie, camos, "
                        + "emotes, grades, permissions, comptes. Impossible à annuler.").withStyle(ChatFormatting.RED), false);
        String c = code;
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.wipe.serveur.code",
                "Pour lancer : /wipe tout confirmer %s (valable 1 minute).", c).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /** 2e étape : premier code juste, par la même personne ; dernier avertissement et un second code. */
    private static int confirmer(CommandSourceStack src, String saisi) {
        if (lancementTick >= 0) return dejaLance(src);
        if (!codeBon(src, 1, saisi)) return mauvaisCode(src);
        etape = 2;
        code = nouveauCode();
        codeFin = System.currentTimeMillis() + 30_000L;
        String c = code;
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.wipe.serveur.derniere",
                "DERNIÈRE VÉRIFICATION : tout le serveur sera remis à zéro. Pour lancer : /wipe tout valider %s (valable 30 secondes).",
                c).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        return 1;
    }

    /** 3e étape : second code juste ; compte à rebours de 10 s annoncé à tous, {@code /wipe tout annuler} l'arrête. */
    private static int valider(CommandSourceStack src, String saisi) {
        if (lancementTick >= 0) return dejaLance(src);
        if (!codeBon(src, 2, saisi)) return mauvaisCode(src);
        etape = 0;
        code = null;
        lancementTick = ticks + COMPTE_A_REBOURS * 20;
        lanceur = src;
        LOG.warn("[Quêtes] remise à zéro du serveur validée par {} : lancement dans {} s", src.getTextName(), COMPTE_A_REBOURS);
        String n = String.valueOf(COMPTE_A_REBOURS);
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.wipe.serveur.rebours",
                    "Remise à zéro du serveur dans %s secondes.", n).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.wipe.serveur.annulable",
                "Lancement dans %s secondes. Pour l'arrêter : /wipe tout annuler", n).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int annuler(CommandSourceStack src) {
        boolean enCours = lancementTick >= 0 || etape != 0;
        etape = 0;
        code = null;
        lancementTick = -1;
        lanceur = null;
        if (enCours) {
            LOG.warn("[Quêtes] remise à zéro du serveur annulée par {}", src.getTextName());
            for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.wipe.serveur.annulee",
                        "Remise à zéro du serveur annulée.").withStyle(ChatFormatting.GREEN));
            }
        }
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.wipe.serveur.annulee",
                "Remise à zéro du serveur annulée."), false);
        return 1;
    }

    private static boolean codeBon(CommandSourceStack src, int attendue, String saisi) {
        return etape == attendue && code != null && System.currentTimeMillis() <= codeFin && code.equals(saisi)
                && auteur.equals(src.getTextName());
    }

    private static int mauvaisCode(CommandSourceStack src) {
        src.sendFailure(Component.translatableWithFallback("heraliz_quetes.wipe.serveur.mauvais_code",
                "Code faux ou périmé : tape d'abord /wipe tout."));
        return 0;
    }

    private static int dejaLance(CommandSourceStack src) {
        src.sendFailure(Component.translatableWithFallback("heraliz_quetes.wipe.serveur.deja",
                "Une remise à zéro est déjà lancée : /wipe tout annuler pour l'arrêter."));
        return 0;
    }

    /** La remise à zéro elle-même, à la fin du compte à rebours. */
    private static void lancer(CommandSourceStack src) {
        MinecraftServer srv = src.getServer();
        LOG.warn("[Quêtes] REMISE À ZÉRO DU SERVEUR lancée par {}", src.getTextName());
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.wipe.serveur.annonce",
                    "Remise à zéro du serveur : nouvelle saison ! Tu vas être déconnecté, reconnecte-toi pour recommencer.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        Predicate<ItemStack> aVie = Commandes::estAVie;
        Map<UUID, List<ItemStack>> rendus = new HashMap<>();
        // les bases d'abord : elles sont rattachées aux groupes des clans
        fusionner(rendus, appel("fr.heraliz.crac.api.WipeApi", "toutEffacer",
                new Class<?>[]{MinecraftServer.class, Predicate.class}, srv, aVie));
        fusionner(rendus, appel("fr.heralisz.clans.server.ClanService", "wipeServeur", new Class<?>[]{Predicate.class}, aVie));
        appel("fr.heralisz.matchs.clashwar.CwClassement", "toutEffacer", new Class<?>[]{MinecraftServer.class}, srv);
        int sol = nettoyerSol(srv);
        Etat e = Etat.lire(srv);
        if (e.illisible) e = new Etat();   // l'ancien fichier est mis de côté, jamais écrasé
        e.numero++;
        e.date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        e.joueurs.addAll(joueursConnus(srv));
        for (Map.Entry<UUID, List<ItemStack>> r : rendus.entrySet()) {
            e.objets.computeIfAbsent(r.getKey(), k -> new ArrayList<>()).addAll(r.getValue());
        }
        e.ecrire(srv);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) FILE.put(p.getUUID(), ticks + 20);
        int n = e.joueurs.size();
        LOG.warn("[Quêtes] remise à zéro du serveur n°{} : {} joueurs à remettre à zéro, {} objets au sol ou cadavres retirés, "
                + "objets à vie à rendre pour {} joueurs", e.numero, n, sol, rendus.size());
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.wipe.serveur.fait",
                "Serveur remis à zéro : %s joueurs le seront à leur connexion. Lance une nouvelle saison avec /saison commencer <jours>.",
                n).withStyle(ChatFormatting.GREEN), true);
    }

    /** À la connexion : joueur pas encore remis à zéro depuis le dernier {@code /wipe tout}. */
    static void connexion(ServerPlayer p) {
        if (Etat.lire(p.server).joueurs.contains(p.getUUID())) FILE.put(p.getUUID(), ticks + 40);
    }

    static void tick(MinecraftServer srv) {
        ticks++;
        if (lancementTick >= 0 && ticks >= lancementTick) {
            CommandSourceStack src = lanceur;
            lancementTick = -1;
            lanceur = null;
            if (src != null) {
                try {
                    lancer(src);
                } catch (RuntimeException ex) {
                    LOG.error("[Quêtes] remise à zéro du serveur en erreur", ex);
                }
            }
        }
        if (FILE.isEmpty()) return;
        for (UUID u : List.copyOf(FILE.keySet())) {
            if (FILE.get(u) > ticks) continue;
            FILE.remove(u);
            ServerPlayer p = srv.getPlayerList().getPlayer(u);
            if (p == null) continue;   // parti avant : ce sera à sa prochaine connexion
            Etat e = Etat.lire(srv);
            if (e.illisible) continue;   // wipe.dat mis de côté : rien à rejouer, rien d'écrasé
            List<ItemStack> enPlus = e.objets.getOrDefault(u, List.of());
            try {
                Commandes.remettreAZero(p, enPlus, Component.translatableWithFallback("heraliz_quetes.wipe.serveur.kick",
                        "Le serveur a été remis à zéro : nouvelle saison ! Reconnecte-toi pour recommencer."));
            } catch (RuntimeException ex) {
                // 23/10 : rien n'est noté, ses objets à vie à rendre restent dans wipe.dat (nouvel essai à sa connexion)
                LOG.error("[Quêtes] remise à zéro de {} en erreur (objets à vie à rendre : {})", p.getGameProfile().getName(), enPlus, ex);
                continue;
            }
            // noté seulement après une remise à zéro réussie (23/10 : avant, un échec perdait les objets à vie à rendre)
            e.joueurs.remove(u);
            e.objets.remove(u);
            e.ecrire(srv);
            LOG.info("[Quêtes] remise à zéro du serveur : {} remis à zéro ({} objets à vie rendus en plus)",
                    p.getGameProfile().getName(), enPlus.size());
        }
    }

    // ------------------------------------------------------------------ outils
    private static void fusionner(Map<UUID, List<ItemStack>> dans, Object r) {
        if (!(r instanceof Map<?, ?> m)) return;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() instanceof UUID u && e.getValue() instanceof List<?> l) {
                for (Object o : l) if (o instanceof ItemStack s && !s.isEmpty()) dans.computeIfAbsent(u, k -> new ArrayList<>()).add(s);
            }
        }
    }

    private static Object appel(String classe, String methode, Class<?>[] types, Object... args) {
        try {
            return Class.forName(classe).getMethod(methode, types).invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            LOG.error("[Quêtes] /wipe tout : {}.{} impossible", classe, methode, t);
            return null;
        }
    }

    /** Objets au sol et cadavres des zones chargées : retirés. Renvoie combien. */
    private static int nettoyerSol(MinecraftServer srv) {
        int n = 0;
        for (ServerLevel l : srv.getAllLevels()) {
            List<Entity> retirer = new ArrayList<>();
            for (Entity en : l.getAllEntities()) {
                if (en instanceof ItemEntity || CADAVRE.equals(ForgeRegistries.ENTITY_TYPES.getKey(en.getType()))) retirer.add(en);
            }
            for (Entity en : retirer) {
                en.discard();
                n++;
            }
        }
        return n;
    }

    /** Tous les joueurs qui ont déjà joué (sauvegardes du monde, quêtes, connectés). */
    private static Set<UUID> joueursConnus(MinecraftServer srv) {
        Set<UUID> s = new LinkedHashSet<>();
        lireUuids(srv.getWorldPath(LevelResource.PLAYER_DATA_DIR), ".dat", s);
        lireUuids(srv.getServerDirectory().toPath().resolve("heraliz_quetes").resolve("joueurs"), ".json", s);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) s.add(p.getUUID());
        return s;
    }

    private static void lireUuids(Path dossier, String ext, Set<UUID> dans) {
        if (!Files.isDirectory(dossier)) return;
        try (Stream<Path> fs = Files.list(dossier)) {
            fs.forEach(f -> {
                String n = f.getFileName().toString();
                if (!n.endsWith(ext)) return;
                try {
                    dans.add(UUID.fromString(n.substring(0, n.length() - ext.length())));
                } catch (IllegalArgumentException ignore) {
                    // pas un fichier de joueur
                }
            });
        } catch (IOException ex) {
            LOG.error("[Quêtes] /wipe tout : {} illisible ({})", dossier, ex.toString());
        }
    }

    /** {@code heraliz_quetes/wipe.dat} à la racine du serveur (hors du monde). */
    private static final class Etat {
        int numero;
        String date = "";
        final Set<UUID> joueurs = new LinkedHashSet<>();
        final Map<UUID, List<ItemStack>> objets = new HashMap<>();
        /** Fichier illisible (mis de côté en {@code wipe.dat.illisible-<date>}) : rien n'est rejoué ni écrit dessus. */
        boolean illisible;

        private static Path fichier(MinecraftServer srv) {
            return srv.getServerDirectory().toPath().resolve("heraliz_quetes").resolve("wipe.dat");
        }

        static Etat lire(MinecraftServer srv) {
            Etat e = new Etat();
            Path f = fichier(srv);
            if (!Files.isRegularFile(f)) return e;
            try {
                CompoundTag t = NbtIo.readCompressed(f.toFile());
                e.numero = t.getInt("Numero");
                e.date = t.getString("Date");
                ListTag js = t.getList("Joueurs", Tag.TAG_STRING);
                for (int i = 0; i < js.size(); i++) {
                    try {
                        e.joueurs.add(UUID.fromString(js.getString(i)));
                    } catch (IllegalArgumentException ignore) {
                        // ligne abîmée
                    }
                }
                CompoundTag os = t.getCompound("Objets");
                for (String k : os.getAllKeys()) {
                    List<ItemStack> l = new ArrayList<>();
                    ListTag li = os.getList(k, Tag.TAG_COMPOUND);
                    for (int i = 0; i < li.size(); i++) {
                        ItemStack s = ItemStack.of(li.getCompound(i));
                        if (!s.isEmpty()) l.add(s);
                    }
                    try {
                        if (!l.isEmpty()) e.objets.put(UUID.fromString(k), l);
                    } catch (IllegalArgumentException ignore) {
                        // clé abîmée
                    }
                }
            } catch (IOException | RuntimeException ex) {
                // 23/10 : jamais écrasé (il garde les objets à vie à rendre) : mis de côté pour le staff
                Path cote = f.resolveSibling("wipe.dat.illisible-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                LOG.error("[Quêtes] {} illisible ({}) : mis de côté en {}", f, ex.toString(), cote.getFileName());
                try {
                    Files.move(f, cote);
                } catch (IOException ex2) {
                    LOG.error("[Quêtes] {} : impossible de le mettre de côté ({})", f, ex2.toString());
                }
                Etat vide = new Etat();
                vide.illisible = true;
                return vide;
            }
            return e;
        }

        void ecrire(MinecraftServer srv) {
            CompoundTag t = new CompoundTag();
            t.putInt("Numero", numero);
            t.putString("Date", date);
            ListTag js = new ListTag();
            for (UUID u : joueurs) js.add(StringTag.valueOf(u.toString()));
            t.put("Joueurs", js);
            CompoundTag os = new CompoundTag();
            for (Map.Entry<UUID, List<ItemStack>> e : objets.entrySet()) {
                ListTag li = new ListTag();
                for (ItemStack s : e.getValue()) li.add(s.save(new CompoundTag()));
                os.put(e.getKey().toString(), li);
            }
            t.put("Objets", os);
            Path f = fichier(srv);
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling("wipe.dat.tmp");
                NbtIo.writeCompressed(t, tmp.toFile());
                try {
                    Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                LOG.error("[Quêtes] {} : écriture impossible ({})", f, ex.toString());
            }
        }
    }
}
