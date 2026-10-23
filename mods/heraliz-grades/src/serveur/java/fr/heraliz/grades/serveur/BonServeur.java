package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.bon.Bon;
import fr.heraliz.grades.bon.ObjetBon;
import fr.heraliz.grades.bon.ReseauBon;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bons (2.5.0, 23/10, demande du propriétaire) : pages réglées par les op ({@code /bon editeur}, op niveau 4 connecté sur
 * AuthMe) et données par exemple dans les caisses. Clic droit : hérals, Points Boutique, grade de la boutique pour un
 * temps (même règle que les achats du site : durée ajoutée, grade plus haut qui met l'autre en réserve…), bonus de loot
 * pour un temps, commande de la console.
 * <p>
 * Contre les copies : chaque bon a un identifiant unique et une signature (HMAC avec la clé {@code heraliz_grades/bons.cle},
 * créée au premier démarrage, jamais dans Git) ; un bon trafiqué est refusé, un identifiant déjà utilisé aussi
 * ({@code heraliz_grades/bons_utilises.json}). Un bon avec des PB est mis de côté AVANT l'appel au site (référence
 * « bon-&lt;id&gt; », faite une seule fois par le site), noté dans {@code heraliz_grades/bons_en_cours.json} : PB versés =
 * le reste est donné ; refusés = le bon est rendu ; site muet = gardé et repris toutes les 30 s (rendu si le site dit
 * qu'il n'a rien fait).
 */
final class BonServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PERSISTE = "PlayerPersisted", CLE_BONUS = "HeralizBonusLoot";

    /** Bon mis de côté pendant le versement des PB : etat « credit », « appliquer » (PB versés) ou « rendre » (refusés). */
    private static final class EnCours {
        String id, uuid, joueur, objet, etat;
        long pb;
    }

    private static final Set<String> UTILISES = new LinkedHashSet<>();
    private static final Map<String, EnCours> EN_COURS = new LinkedHashMap<>();
    private static final Set<String> OCCUPES = new HashSet<>();
    private static Path dossier, fichierUtilises, fichierEnCours;
    private static byte[] cle;
    private static MinecraftServer serveur;
    private static int ticks;

    private BonServeur() {
    }

    static void brancher() {
        ObjetBon.UTILISER.brancher(u -> utiliser(u.joueur(), u.main()));
        ReseauBon.RECEPTION_CREER.brancher(r -> creer(r.joueur(), r.donnees()));
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.bon." + cle, fr, args);
    }

    private static boolean droit(ServerPlayer p) {
        return p.hasPermissions(4) && PontBukkit.authentifie(p);
    }

    // ------------------------------------------------------------------ démarrage
    static void demarrer(MinecraftServer srv, Path dossierDonnees) {
        serveur = srv;
        dossier = dossierDonnees;
        fichierUtilises = dossier.resolve("bons_utilises.json");
        fichierEnCours = dossier.resolve("bons_en_cours.json");
        UTILISES.clear();
        EN_COURS.clear();
        OCCUPES.clear();
        cle = null;
        Path f = dossier.resolve("bons.cle");
        try {
            Files.createDirectories(dossier);
            if (!Files.exists(f)) {
                byte[] b = new byte[32];
                new SecureRandom().nextBytes(b);
                Files.writeString(f, HexFormat.of().formatHex(b), StandardCharsets.UTF_8);
            }
            String hex = Files.readString(f, StandardCharsets.UTF_8).trim();
            if (hex.length() >= 32) cle = HexFormat.of().parseHex(hex);
        } catch (Exception ex) {
            LOG.error("[grades] clé des bons {} illisible ({}) : bons désactivés", f, ex.toString());
        }
        try {
            if (Files.exists(fichierUtilises)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(fichierUtilises, StandardCharsets.UTF_8)).getAsJsonArray()) UTILISES.add(e.getAsString());
            }
        } catch (Exception ex) {
            // sans la liste, un bon pourrait servir deux fois : bons désactivés
            cle = null;
            LOG.error("[grades] {} illisible ({}) : bons désactivés", fichierUtilises, ex.toString());
        }
        try {
            if (Files.exists(fichierEnCours)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(fichierEnCours, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    EnCours c = GSON.fromJson(e, EnCours.class);
                    if (c != null && c.id != null) EN_COURS.put(c.id, c);
                }
            }
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible ({}) : bons en cours NON repris (à regarder à la main)", fichierEnCours, ex.toString());
            try {
                Files.copy(fichierEnCours, fichierEnCours.resolveSibling("bons_en_cours.illisible." + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
            }
        }
        if (!EN_COURS.isEmpty()) LOG.warn("[grades] bons : {} bon(s) en cours à reprendre", EN_COURS.size());
    }

    static void arreter() {
        serveur = null;
        OCCUPES.clear();
    }

    static void tick() {
        if (serveur == null) return;
        ticks++;
        if (ticks % 100 == 0) {
            for (ServerPlayer p : serveur.getPlayerList().getPlayers()) {
                majEffet(p);
                suite(p);
            }
        }
        if (ticks % 600 == 0) reprendre();
    }

    static void connexion(ServerPlayer p) {
        majEffet(p);
        suite(p);
    }

    // ------------------------------------------------------------------ commandes
    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bon")
                .requires(s -> s.getEntity() instanceof ServerPlayer p && droit(p))
                .executes(c -> ouvrir(c.getSource().getPlayerOrException()))
                .then(Commands.literal("editeur").executes(c -> ouvrir(c.getSource().getPlayerOrException()))));
    }

    /** Ouvre l'éditeur ; un bon tenu en main est repris comme point de départ (on en fait une copie, l'original ne change pas). */
    private static int ouvrir(ServerPlayer p) {
        if (!droit(p)) return 0;
        ItemStack main = p.getMainHandItem();
        Bon.Donnees depart = main.is(ObjetBon.BON.get()) ? Bon.lire(main) : null;
        ReseauBon.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauBon.Editeur(true, depart, "", false));
        return 1;
    }

    private static void repondre(ServerPlayer p, String message, boolean ok) {
        ReseauBon.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauBon.Editeur(false, null, message, ok));
    }

    // ------------------------------------------------------------------ création
    /** Données remises au propre (espaces, « / » de la commande, minuscules) avant contrôle et signature. */
    private static Bon.Donnees propre(Bon.Donnees d) {
        List<Bon.Effet> l = new ArrayList<>();
        for (Bon.Effet e : d.effets()) {
            String v = e.valeur() == null ? "" : e.valeur().trim(), du = e.duree() == null ? "" : e.duree().trim().toLowerCase(Locale.ROOT).replace(" ", "");
            switch (e.type()) {
                case COMMANDE -> {
                    if (v.startsWith("/")) v = v.substring(1);
                    l.add(new Bon.Effet(e.type(), 0, v, ""));
                }
                case GRADE -> l.add(new Bon.Effet(e.type(), 0, v.toLowerCase(Locale.ROOT), du));
                case BONUS_LOOT -> l.add(new Bon.Effet(e.type(), e.nombre(), "", du));
                default -> l.add(new Bon.Effet(e.type(), e.nombre(), "", ""));
            }
        }
        return new Bon.Donnees(d.nom() == null ? "" : d.nom().trim(), d.couleur() & 0xFFFFFF, d.rarete() == null ? "" : d.rarete(), l);
    }

    private static void creer(ServerPlayer p, Bon.Donnees brut) {
        if (!droit(p)) return;
        if (cle == null) {
            repondre(p, "indisponible", false);
            return;
        }
        Bon.Donnees d = propre(brut);
        String err = Bon.erreur(d);
        if (err != null) {
            repondre(p, err, false);
            return;
        }
        String id = UUID.randomUUID().toString();
        ItemStack st = new ItemStack(ObjetBon.BON.get());
        Bon.ecrire(st, d, id, signer(id, d));
        if (!p.getInventory().add(st)) p.drop(st, false);
        String resume = resume(d);
        RegistreGrades r = ServeurGrades.registre;
        if (r != null) r.journaliser(p.getGameProfile().getName() + " (bons) | créé | " + id + " | " + d.nom() + " | " + resume);
        LienDiscord.evenement("🎟️ " + p.getGameProfile().getName() + " a créé un bon « " + d.nom() + " » (" + resume + ").");
        repondre(p, "cree", true);
    }

    private static String resume(Bon.Donnees d) {
        List<String> l = new ArrayList<>();
        for (Bon.Effet e : d.effets()) {
            l.add(switch (e.type()) {
                case HERALS, PB -> e.type().id() + " " + e.nombre();
                case GRADE -> "grade " + e.valeur() + " " + e.duree();
                case BONUS_LOOT -> "bonus_loot " + e.nombre() + "% " + e.duree();
                case COMMANDE -> "commande « " + e.valeur() + " »";
            });
        }
        return String.join(", ", l);
    }

    private static String signer(String id, Bon.Donnees d) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(Bon.texteASigner(id, d).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean signatureValide(ItemStack st, Bon.Donnees d) {
        String id = Bon.id(st), sig = Bon.signature(st);
        if (cle == null || id.isEmpty() || sig.isEmpty()) return false;
        return MessageDigest.isEqual(signer(id, d).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ utilisation
    private static void utiliser(ServerPlayer p, net.minecraft.world.InteractionHand main) {
        ItemStack st = p.getItemInHand(main);
        if (!st.is(ObjetBon.BON.get())) return;
        Bon.Donnees d = Bon.lire(st);
        if (d == null) {
            p.sendSystemMessage(msg("vierge", "Ce bon est vierge : il ne donne rien.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (cle == null) {
            p.sendSystemMessage(msg("indisponible", "Les bons sont indisponibles pour le moment.").withStyle(ChatFormatting.RED));
            return;
        }
        String id = Bon.id(st);
        if (!signatureValide(st, d) || Bon.erreur(d) != null) {
            p.sendSystemMessage(msg("faux", "Ce bon n'est pas valable.").withStyle(ChatFormatting.RED));
            LOG.warn("[grades] bon refusé (signature ou contenu) : {} chez {}", id, p.getGameProfile().getName());
            return;
        }
        if (UTILISES.contains(id) || EN_COURS.containsKey(id)) {
            st.shrink(st.getCount());
            p.sendSystemMessage(msg("deja", "Ce bon a déjà été utilisé.").withStyle(ChatFormatting.RED));
            LOG.warn("[grades] copie d'un bon déjà utilisé retirée : {} chez {}", id, p.getGameProfile().getName());
            return;
        }
        Component non = empechement(p, d);
        if (non != null) {
            p.sendSystemMessage(non.copy().withStyle(ChatFormatting.RED));
            return;
        }
        long pb = 0;
        for (Bon.Effet e : d.effets()) if (e.type() == Bon.Type.PB) pb += e.nombre();
        if (pb > 0) {
            EnCours c = new EnCours();
            c.id = id;
            c.uuid = p.getUUID().toString();
            c.joueur = p.getGameProfile().getName();
            c.objet = st.save(new CompoundTag()).toString();
            c.pb = pb;
            c.etat = "credit";
            EN_COURS.put(id, c);
            if (!sauverEnCours()) {
                EN_COURS.remove(id);
                p.sendSystemMessage(msg("erreur", "Impossible d'utiliser ce bon pour le moment (voir la console du serveur).").withStyle(ChatFormatting.RED));
                return;
            }
            // mis de côté : il ne revient que si le site refuse
            st.shrink(st.getCount());
            p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            crediter(c, true);
            return;
        }
        UTILISES.add(id);
        if (!sauverUtilises()) {
            UTILISES.remove(id);
            p.sendSystemMessage(msg("erreur", "Impossible d'utiliser ce bon pour le moment (voir la console du serveur).").withStyle(ChatFormatting.RED));
            return;
        }
        st.shrink(st.getCount());
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        appliquer(p, id, d, false);
    }

    /** Ce qui empêche d'utiliser le bon maintenant (le bon reste alors dans la main) ; null : rien. */
    private static Component empechement(ServerPlayer p, Bon.Donnees d) {
        for (Bon.Effet e : d.effets()) {
            switch (e.type()) {
                case HERALS -> {
                    if (!ecoDisponible()) return msg("herals_indispo", "Les hérals sont indisponibles pour le moment : garde ton bon.");
                }
                case PB -> {
                    if (!Pb.actif()) return msg("pb_indispo", "Les Points Boutique sont indisponibles pour le moment : garde ton bon.");
                    if (!PontBukkit.authentifie(p)) return Component.translatableWithFallback("message.heraliz_grades.kit.connexion", "Connecte-toi d'abord avec /login.");
                }
                case GRADE -> {
                    RegistreGrades r = ServeurGrades.registre;
                    Grade g = Grade.parId(e.valeur());
                    if (r == null || r.illisible() || g == null) return msg("grades_indispo", "Les grades sont indisponibles pour le moment : garde ton bon.");
                    RegistreGrades.Fiche f = r.fiche(p.getUUID());
                    // grade boutique à vie : la boutique ne pourrait rien ajouter, le bon serait perdu
                    if (f != null && f.boutique() != null && f.finBoutique() == 0 && (f.boutique() == g || g.rang <= f.boutique().rang)) {
                        return msg("vie_deja", "Tu as déjà un grade de la boutique à vie : ce bon ne t'apporterait rien, garde-le ou donne-le.");
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    /** Donne tout ce que dit le bon (sauf les PB si déjà versés) et annonce chaque gain. */
    private static void appliquer(ServerPlayer p, String id, Bon.Donnees d, boolean pbVerses) {
        p.sendSystemMessage(msg("utilise", "Bon utilisé : %s", Component.literal(d.nom())).withStyle(ChatFormatting.GOLD));
        List<String> rates = new ArrayList<>();
        for (Bon.Effet e : d.effets()) {
            boolean ok = switch (e.type()) {
                case PB -> pbVerses;
                case HERALS -> deposer(p.getUUID(), e.nombre());
                case GRADE -> donnerGrade(p, e);
                case BONUS_LOOT -> {
                    ajouterBonus(p, (int) e.nombre(), Bon.dureeMs(e.duree()));
                    yield true;
                }
                case COMMANDE -> commande(p, e.valeur());
            };
            if (ok) p.sendSystemMessage(Component.literal("• ").append(Bon.texteEffet(e)).withStyle(ChatFormatting.YELLOW));
            else rates.add(e.type().id());
        }
        if (!rates.isEmpty()) {
            p.sendSystemMessage(msg("rate", "Une partie du bon n'a pas pu être donnée : le staff est prévenu.").withStyle(ChatFormatting.RED));
            LienDiscord.evenement("⚠️ Bon « " + d.nom() + " » de **" + p.getGameProfile().getName() + "** : " + String.join(", ", rates) + " NON donné(s), à régler à la main.");
        }
        majEffet(p);
        RegistreGrades r = ServeurGrades.registre;
        if (r != null) r.journaliser(p.getGameProfile().getName() + " (bons) | utilisé | " + id + " | " + d.nom() + " | " + resume(d)
                + (rates.isEmpty() ? "" : " | RATÉ : " + String.join(", ", rates)));
    }

    private static boolean donnerGrade(ServerPlayer p, Bon.Effet e) {
        RegistreGrades r = ServeurGrades.registre;
        Grade g = Grade.parId(e.valeur());
        int jours = Bon.joursGrade(e.duree());
        if (r == null || g == null || jours < 0) return false;
        RegistreGrades.Achat a = r.acheter(p.getUUID(), p.getGameProfile().getName(), g, jours);
        if (a == RegistreGrades.Achat.ILLISIBLE || a == RegistreGrades.Achat.ERREUR || a == RegistreGrades.Achat.VIE_DEJA) return false;
        ServeurGrades.rafraichir(p);
        p.server.getCommands().sendCommands(p);
        if (a == RegistreGrades.Achat.EN_RESERVE) {
            p.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.achat_reserve",
                    "Merci ! Ton grade %s démarrera quand ton grade actuel se terminera.", g.nom()));
        }
        return true;
    }

    private static boolean commande(ServerPlayer p, String brute) {
        String ligne = brute.replace("{joueur}", p.getGameProfile().getName()).replace("{player}", p.getGameProfile().getName());
        try {
            int res = p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(), ligne);
            if (res <= 0) LOG.warn("[grades] bon : « {} » a rendu {}", ligne, res);
            return res > 0;
        } catch (Exception ex) {
            LOG.error("[grades] bon : « {} » a planté ({})", ligne, ex.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ Points Boutique
    private static void crediter(EnCours c, boolean annoncer) {
        UUID u = UUID.fromString(c.uuid);
        OCCUPES.add(c.id);
        Pb.crediter("bon-" + c.id, c.joueur, c.pb, "bon", r -> {
            OCCUPES.remove(c.id);
            if (r != null && r.code() == 200 && r.ok()) {
                c.etat = "appliquer";
                sauverEnCours();
            } else if (r != null && (r.code() == 200 || r.code() >= 400 && r.code() < 500)) {
                c.etat = "rendre";
                sauverEnCours();
                LOG.warn("[grades] bon {} : PB refusés par le site ({} {}) : bon rendu", c.id, r.code(), r.raison());
            } else {
                // site muet ou en erreur : on ne sait pas, le bon reste de côté et la question est reposée plus tard
                ServerPlayer j = serveur == null ? null : serveur.getPlayerList().getPlayer(u);
                if (j != null && annoncer) {
                    j.sendSystemMessage(msg("attente", "Le site ne répond pas : ton bon est gardé de côté et sera repris tout seul (ou rendu).")
                            .withStyle(ChatFormatting.GOLD));
                }
                return;
            }
            ServerPlayer j = serveur == null ? null : serveur.getPlayerList().getPlayer(u);
            if (j != null) suite(j);
        });
    }

    /** Bons du joueur dont le site a répondu : donnés (PB versés) ou rendus (refusés). Il doit être connecté. */
    private static void suite(ServerPlayer p) {
        String u = p.getUUID().toString();
        for (EnCours c : new ArrayList<>(EN_COURS.values())) {
            if (!u.equals(c.uuid) || OCCUPES.contains(c.id)) continue;
            ItemStack st;
            try {
                st = ItemStack.of(TagParser.parseTag(c.objet));
            } catch (Exception ex) {
                LOG.error("[grades] bon en cours {} illisible ({}) : à régler à la main", c.id, ex.toString());
                continue;
            }
            if ("appliquer".equals(c.etat)) {
                Bon.Donnees d = Bon.lire(st);
                UTILISES.add(c.id);
                EN_COURS.remove(c.id);
                if (!sauverUtilises() || !sauverEnCours()) {
                    UTILISES.remove(c.id);
                    EN_COURS.put(c.id, c);
                    continue;
                }
                if (d != null) appliquer(p, c.id, d, true);
            } else if ("rendre".equals(c.etat)) {
                EN_COURS.remove(c.id);
                if (!sauverEnCours()) {
                    EN_COURS.put(c.id, c);
                    continue;
                }
                if (!p.getInventory().add(st)) p.drop(st, false);
                p.sendSystemMessage(msg("rendu", "Les Points Boutique n'ont pas pu être versés : ton bon t'est rendu.").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Bons restés « credit » (site muet, plantage) : on demande au site s'il a versé les PB. */
    private static void reprendre() {
        for (EnCours c : new ArrayList<>(EN_COURS.values())) {
            if (!"credit".equals(c.etat) || OCCUPES.contains(c.id)) continue;
            OCCUPES.add(c.id);
            Pb.statut("bon-" + c.id, r -> {
                OCCUPES.remove(c.id);
                if (r == null || r.code() != 200) return;
                if (r.corps().has("fait") && r.corps().get("fait").getAsBoolean()) c.etat = "appliquer";
                else c.etat = "rendre";
                sauverEnCours();
            });
        }
    }

    // ------------------------------------------------------------------ hérals (HeraliZ Économie, par réflexion)
    private static boolean ecoDisponible() {
        try {
            return (boolean) Class.forName("fr.heralisz.economie.api.EconomieApi").getMethod("disponible").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean deposer(UUID joueur, long montant) {
        try {
            Class<?> c = Class.forName("fr.heralisz.economie.api.EconomieApi");
            if (!(boolean) c.getMethod("disponible").invoke(null)) return false;
            c.getMethod("deposer", UUID.class, long.class, String.class).invoke(null, joueur, montant, "bon");
            return true;
        } catch (Throwable t) {
            LOG.error("[grades] bon : versement de {} hérals à {} impossible ({})", montant, joueur, t.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ bonus de loot (contrat avec HeraliZ Loot, CLAUDE.md §5)
    /** Source « bon_&lt;pct&gt; » : un même pourcentage prolonge sa durée, des pourcentages différents s'additionnent (plafond chez Loot). */
    private static void ajouterBonus(ServerPlayer p, int pct, long ms) {
        CompoundTag donnees = p.getPersistentData();
        CompoundTag persiste = donnees.getCompound(PERSISTE);
        CompoundTag sources = persiste.getCompound(CLE_BONUS);
        String nom = "bon_" + pct;
        CompoundTag s = sources.getCompound(nom);
        long now = System.currentTimeMillis();
        s.putInt("Pct", pct);
        s.putLong("Fin", Math.max(now, s.getLong("Fin")) + ms);
        sources.put(nom, s);
        persiste.put(CLE_BONUS, sources);
        donnees.put(PERSISTE, persiste);
    }

    /**
     * Effet « Bonus de loot » sans particules : niveau = bonus total (toutes les sources, plafond 25 %), durée = la source
     * qui dure le plus. Remis à jour toutes les 5 s (mort, lait, autre source qui change).
     */
    static void majEffet(ServerPlayer p) {
        MobEffect eff = ObjetBon.BONUS_LOOT.get();
        CompoundTag persiste = p.getPersistentData().getCompound(PERSISTE);
        CompoundTag sources = persiste.getCompound(CLE_BONUS);
        long now = System.currentTimeMillis(), reste = 0;
        int total = 0;
        for (String k : sources.getAllKeys()) {
            CompoundTag t = sources.getCompound(k);
            long r;
            if (t.contains("Fin")) r = (t.getLong("Fin") - now) / 50L;
            else if (t.contains("Restant")) r = t.getLong("Restant");
            else continue;
            if (r <= 0) continue;
            total += Math.max(0, t.getInt("Pct"));
            reste = Math.max(reste, r);
        }
        total = Math.min(total, Bon.MAX_PCT);
        MobEffectInstance cur = p.getEffect(eff);
        if (total <= 0 || reste <= 0) {
            if (cur != null) p.removeEffect(eff);
            return;
        }
        int duree = (int) Math.min(reste, Integer.MAX_VALUE - 1L);
        if (cur == null || cur.getAmplifier() != total - 1 || Math.abs(cur.getDuration() - duree) > 200) {
            if (cur != null) p.removeEffect(eff);
            p.addEffect(new MobEffectInstance(eff, duree, total - 1, true, false, true));
        }
    }

    // ------------------------------------------------------------------ fichiers
    private static boolean sauverUtilises() {
        JsonArray a = new JsonArray();
        for (String s : UTILISES) a.add(s);
        return ecrire(fichierUtilises, GSON.toJson(a));
    }

    private static boolean sauverEnCours() {
        return ecrire(fichierEnCours, GSON.toJson(new ArrayList<>(EN_COURS.values())));
    }

    private static boolean ecrire(Path f, String texte) {
        try {
            Files.createDirectories(dossier);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, texte, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomiqueImpossible) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            LOG.error("[grades] {} impossible à écrire ({})", f, ex.toString());
            return false;
        }
    }
}
