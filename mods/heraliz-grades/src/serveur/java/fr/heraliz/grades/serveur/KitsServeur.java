package fr.heraliz.grades.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kits : un par grade de la boutique, plus celui de Survivant (tout le monde). Contenu dans
 * {@code config/heraliz_grades/kits.json} (dans le dépôt : serveur/config/heraliz_grades/kits.json), relu tout seul quand
 * il change.
 * <p>
 * Règles (pas de pay2win, pas de give) :
 * <ul>
 *    <li>chaque joueur ne peut prendre QU'UN kit par recharge : celui de son grade boutique, sinon Survivant ;</li>
 *    <li>le joueur ne fait qu'envoyer un identifiant : grade, recharge, connexion AuthMe, place dans l'inventaire et
 *    contenu sont vérifiés ici ;</li>
 *    <li>la recharge est écrite AVANT de donner les objets (si l'écriture échoue, rien n'est donné) ;</li>
 *    <li>une demande par seconde au plus par joueur ; chaque kit pris est noté dans le journal.</li>
 * </ul>
 */
final class KitsServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long ANTI_SPAM_MS = 1000;

    record Kit(Grade grade, long rechargeMs, List<ItemStack> objets) {
    }

    private static Map<Grade, Kit> kits = Collections.emptyMap();
    private static long dateFichier = Long.MIN_VALUE;
    static Recharges recharges;
    private static final Map<UUID, Long> DERNIERE_DEMANDE = new HashMap<>();

    private KitsServeur() {
    }

    private static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("kits.json");
    }

    static void demarrer(Path dossier) {
        recharges = new Recharges(dossier);
        dateFichier = Long.MIN_VALUE;
        DERNIERE_DEMANDE.clear();
        relireSiChange();
    }

    static void arreter() {
        recharges = null;
        kits = Collections.emptyMap();
        DERNIERE_DEMANDE.clear();
    }

    static void oublier(UUID uuid) {
        DERNIERE_DEMANDE.remove(uuid);
    }

    // ------------------------------------------------------------------ lecture de kits.json
    /** Relit kits.json s'il a changé. Un fichier faux garde les kits d'avant (et le dit dans la console). */
    static void relireSiChange() {
        Path f = fichier();
        long date;
        try {
            date = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : -1;
        } catch (Exception ex) {
            return;
        }
        if (date == dateFichier) return;
        dateFichier = date;
        if (date < 0) {
            kits = Collections.emptyMap();
            LOG.warn("[grades] {} absent : aucun kit", f);
            return;
        }
        try {
            kits = lire(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            LOG.info("[grades] {} kit(s) chargé(s) depuis {}", kits.size(), f);
        } catch (Exception ex) {
            LOG.error("[grades] {} faux ({}) : les kits d'avant sont gardés ({} kit(s))", f, ex.getMessage(), kits.size());
        }
    }

    private static Map<Grade, Kit> lire(JsonObject racine) throws Exception {
        Map<Grade, Kit> out = new EnumMap<>(Grade.class);
        for (JsonElement e : racine.getAsJsonArray("kits")) {
            JsonObject o = e.getAsJsonObject();
            Grade g = Grade.parId(o.get("grade").getAsString());
            if (g == null || g.type == Grade.Type.STAFF) throw new IllegalArgumentException("grade de kit invalide : " + o.get("grade"));
            if (out.containsKey(g)) throw new IllegalArgumentException("deux kits pour " + g.id);
            double heures = o.get("recharge_heures").getAsDouble();
            if (!(heures >= 1 && heures <= 24 * 30)) throw new IllegalArgumentException(g.id + " : recharge_heures entre 1 et 720");
            List<ItemStack> objets = new ArrayList<>();
            for (JsonElement x : o.getAsJsonArray("objets")) {
                JsonObject ob = x.getAsJsonObject();
                ResourceLocation id = new ResourceLocation(ob.get("item").getAsString());
                if (!ForgeRegistries.ITEMS.containsKey(id)) throw new IllegalArgumentException(g.id + " : objet inconnu " + id);
                Item item = ForgeRegistries.ITEMS.getValue(id);
                int nombre = ob.has("nombre") ? ob.get("nombre").getAsInt() : 1;
                if (nombre < 1 || nombre > 64) throw new IllegalArgumentException(g.id + " : nombre entre 1 et 64 pour " + id);
                CompoundTag nbt = ob.has("nbt") ? TagParser.parseTag(ob.get("nbt").getAsString()) : null;
                ItemStack modele = new ItemStack(item);
                if (nbt != null) modele.setTag(nbt);
                int max = Math.max(1, modele.getMaxStackSize());
                for (int reste = nombre; reste > 0; reste -= max) {
                    ItemStack s = modele.copy();
                    s.setCount(Math.min(reste, max));
                    objets.add(s);
                }
            }
            if (objets.isEmpty() || objets.size() > 27) throw new IllegalArgumentException(g.id + " : entre 1 et 27 piles d'objets");
            out.put(g, new Kit(g, Math.round(heures * 3_600_000L), List.copyOf(objets)));
        }
        return out;
    }

    // ------------------------------------------------------------------ règles
    /** Le seul kit que ce joueur peut prendre : celui de son grade boutique s'il existe, sinon Survivant. */
    private static Kit kitDe(UUID uuid) {
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null ? null : r.fiche(uuid);
        if (f != null && f.boutique() != null && kits.containsKey(f.boutique())) return kits.get(f.boutique());
        return kits.get(Grade.SURVIVANT);
    }

    private static long reste(UUID uuid, Kit k, long maintenant) {
        if (k == null || recharges == null) return 0;
        long d = recharges.dernier(uuid);
        return d <= 0 ? 0 : Math.max(0, d + k.rechargeMs() - maintenant);
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.kit." + cle, fr, args);
    }

    /** Ouvre (ou met à jour) le menu des kits chez le joueur. */
    static void envoyerEtat(ServerPlayer p, boolean ouvrir) {
        List<ReseauGrades.VueKit> vues = new ArrayList<>();
        for (Grade g : Grade.values()) {
            Kit k = kits.get(g);
            if (k != null) vues.add(new ReseauGrades.VueKit(g.id, k.objets(), k.rechargeMs()));
        }
        Kit sien = kitDe(p.getUUID());
        ReseauGrades.EtatKits etat = new ReseauGrades.EtatKits(ouvrir, vues, sien == null ? "" : sien.grade().id,
                reste(p.getUUID(), sien, System.currentTimeMillis()));
        ReseauGrades.CANAL.send(PacketDistributor.PLAYER.with(() -> p), etat);
    }

    /** Demande d'un kit venue du menu. */
    static void demande(ReseauGrades.Demande d) {
        ServerPlayer p = d.joueur();
        long maintenant = System.currentTimeMillis();
        Long avant = DERNIERE_DEMANDE.put(p.getUUID(), maintenant);
        if (avant != null && maintenant - avant < ANTI_SPAM_MS) return;
        if (!p.isAlive() || p.isSpectator()) return;
        if (!PontBukkit.authentifie(p)) {
            p.sendSystemMessage(msg("connexion", "Connecte-toi d'abord avec /login."));
            return;
        }
        if (recharges == null || recharges.illisible() || ServeurGrades.registre == null || ServeurGrades.registre.illisible()) {
            p.sendSystemMessage(msg("indisponible", "Les kits sont indisponibles pour le moment."));
            return;
        }
        Kit k = kitDe(p.getUUID());
        if (k == null || !k.grade().id.equals(d.id())) {
            p.sendSystemMessage(msg("pas_le_tien", "Ce kit n'est pas celui de ton grade."));
            envoyerEtat(p, false);
            return;
        }
        long reste = reste(p.getUUID(), k, maintenant);
        if (reste > 0) {
            long min = (reste + 59_999) / 60_000;
            p.sendSystemMessage(msg("attente", "Ton prochain kit arrive dans %1$s h %2$s min.", String.valueOf(min / 60), String.valueOf(min % 60)));
            envoyerEtat(p, false);
            return;
        }
        int libres = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty()) libres++;
        }
        if (libres < k.objets().size()) {
            p.sendSystemMessage(msg("place", "Fais de la place : il faut %s cases libres dans ton inventaire.", String.valueOf(k.objets().size())));
            return;
        }
        // la recharge d'abord : si elle ne peut pas être écrite, rien n'est donné
        if (!recharges.noter(p.getUUID(), maintenant)) {
            p.sendSystemMessage(msg("indisponible", "Les kits sont indisponibles pour le moment."));
            return;
        }
        for (ItemStack modele : k.objets()) {
            ItemStack s = modele.copy();
            if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
        }
        p.containerMenu.broadcastChanges();
        ServeurGrades.registre.journaliser("kit | " + p.getGameProfile().getName() + " (" + p.getUUID() + ") | " + k.grade().id);
        p.sendSystemMessage(msg("recu", "Kit %s récupéré.", k.grade().nom()));
        envoyerEtat(p, false);
    }

    // ------------------------------------------------------------------ commandes
    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kit")
                .executes(c -> {
                    if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                    if (!PontBukkit.authentifie(p)) {
                        p.sendSystemMessage(msg("connexion", "Connecte-toi d'abord avec /login."));
                        return 0;
                    }
                    envoyerEtat(p, true);
                    return 1;
                })
                .then(Commands.literal("reset").requires(CommandesGrades::console)
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b))
                                .executes(c -> {
                                    CommandSourceStack s = c.getSource();
                                    if (!CommandesGrades.console(s) || recharges == null) return 0;
                                    String nom = StringArgumentType.getString(c, "joueur");
                                    ServerPlayer p = s.getServer().getPlayerList().getPlayerByName(nom);
                                    if (p == null) {
                                        s.sendFailure(Component.translatableWithFallback("message.heraliz_grades.joueur_inconnu", "Joueur introuvable : %s (il doit être connecté, ou déjà avoir un grade).", nom));
                                        return 0;
                                    }
                                    String pseudo = p.getGameProfile().getName();
                                    if (!recharges.effacer(p.getUUID())) {
                                        s.sendFailure(msg("reset_rien", "%s n'avait pas de recharge en cours.", pseudo));
                                        return 0;
                                    }
                                    ServeurGrades.registre.journaliser("console | kit reset | " + pseudo + " (" + p.getUUID() + ")");
                                    LienDiscord.evenement("🎒 Console du serveur : recharge des kits remise à zéro pour **" + pseudo + "**.");
                                    s.sendSuccess(() -> msg("reset", "Recharge des kits remise à zéro pour %s.", pseudo), true);
                                    return 1;
                                }))));
    }
}
