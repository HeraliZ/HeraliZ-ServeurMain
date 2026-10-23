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
 * Kits (2.0.0) : chaque grade a ses kits (0 à 8), réglés dans l'éditeur en jeu et gardés dans
 * {@code heraliz_grades/definitions.json} ({@link DefinitionsGrades}).
 * <p>
 * Règles (pas de pay2win, pas de give) :
 * <ul>
 *    <li>kits de Survivant et de la boutique : seulement ceux de son grade de la boutique s'il en a, sinon ceux de
 *    Survivant ; une seule recharge pour tous ces kits (2.4.0 : un seul kit par recharge, quel que soit le grade) ;</li>
 *    <li>refusés en combat, à terre, en match, pendant le tutoriel des bases et sur la carte ({@link Restrictions}) ;</li>
 *    <li>en plus, les kits de ses grades du staff et spéciaux (Streamer…) et les kits Soutien s'il porte le tag HeraliZ
 *    sur Discord (compte lié, vu par HeraliBot), chacun avec sa recharge ;</li>
 *    <li>le joueur ne fait qu'envoyer un identifiant : grade, recharge, connexion AuthMe, place dans l'inventaire et
 *    contenu sont vérifiés ici ;</li>
 *    <li>la recharge est écrite AVANT de donner les objets (si l'écriture échoue, rien n'est donné) ;</li>
 *    <li>une demande par seconde au plus par joueur ; chaque kit pris est noté dans le journal.</li>
 * </ul>
 */
final class KitsServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long ANTI_SPAM_MS = 1000;

    static Recharges recharges;
    private static final Map<UUID, Long> DERNIERE_DEMANDE = new HashMap<>();

    private KitsServeur() {
    }

    static void demarrer(Path dossier) {
        recharges = new Recharges(dossier);
        DERNIERE_DEMANDE.clear();
    }

    static void arreter() {
        recharges = null;
        DERNIERE_DEMANDE.clear();
    }

    static void oublier(UUID uuid) {
        DERNIERE_DEMANDE.remove(uuid);
    }

    /** Plus rien à relire : les kits sont dans les définitions des grades (gardé pour la boucle du serveur). */
    static void relireSiChange() {
    }

    // ------------------------------------------------------------------ règles
    /** Groupe de recharge d'un grade : « base » pour Survivant et la boutique, « soutien », sinon son identifiant. */
    private static String groupe(Grade g) {
        return g.type == Grade.Type.BASE || g.type == Grade.Type.BOUTIQUE ? "base" : g.type == Grade.Type.SOUTIEN ? "soutien" : g.id;
    }

    private static String idVue(DefinitionsGrades.KitDef k) {
        return k.grade() + "/" + k.id();
    }

    /** Le grade dont ce joueur prend les kits de base : sa boutique si elle a des kits, sinon Survivant. */
    private static Grade gradeDeBase(UUID uuid) {
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null ? null : r.fiche(uuid);
        if (f != null && f.boutique() != null && !DefinitionsGrades.kitsDe(f.boutique()).isEmpty()) return f.boutique();
        return Grade.SURVIVANT;
    }

    /** Vrai si ce joueur peut prendre ce kit. */
    private static boolean debloque(UUID uuid, DefinitionsGrades.KitDef k) {
        if (k == null) return false;
        Grade g = Grade.parId(k.grade());
        if (g == null) return false;
        return switch (g.type) {
            case SOUTIEN -> LienDiscord.estSoutien(uuid);
            case BASE, BOUTIQUE -> g == gradeDeBase(uuid);
            default -> {
                RegistreGrades r = ServeurGrades.registre;
                RegistreGrades.Fiche f = r == null ? null : r.fiche(uuid);
                yield f != null && f.a(g);
            }
        };
    }

    /** Affiché dans le menu : Survivant, la boutique et Soutien pour tous ; staff et spéciaux seulement pour qui les a. */
    private static boolean visible(UUID uuid, Grade g, DefinitionsGrades.KitDef k) {
        return g.type == Grade.Type.BASE || g.type == Grade.Type.BOUTIQUE || g.type == Grade.Type.SOUTIEN || debloque(uuid, k);
    }

    private static String cle(UUID uuid, DefinitionsGrades.KitDef k) {
        Grade g = Grade.parId(k.grade());
        return Recharges.cle(uuid, g == null ? k.grade() : groupe(g), k.id());
    }

    private static long rechargeMs(DefinitionsGrades.KitDef k) {
        return k.rechargeH() * 3_600_000L;
    }

    /**
     * Temps avant de pouvoir reprendre ce kit. Depuis la 2.4.0 (règle du propriétaire : un seul kit par recharge), prendre
     * un kit d'un groupe consomme la recharge de TOUS les kits de ce groupe (Survivant et boutique ensemble, Soutien à
     * part, chaque grade du staff ou spécial à part) : le plus long reste du groupe l'emporte.
     */
    private static long reste(UUID uuid, DefinitionsGrades.KitDef k, long maintenant) {
        if (k == null || recharges == null) return 0;
        Grade g = Grade.parId(k.grade());
        String groupe = g == null ? k.grade() : groupe(g);
        long max = resteUn(uuid, k, maintenant);
        for (Grade g2 : Grade.values()) {
            if (!groupe(g2).equals(groupe)) continue;
            for (DefinitionsGrades.KitDef k2 : DefinitionsGrades.kitsDe(g2)) max = Math.max(max, resteUn(uuid, k2, maintenant));
        }
        return max;
    }

    private static long resteUn(UUID uuid, DefinitionsGrades.KitDef k, long maintenant) {
        long d = recharges.dernier(cle(uuid, k));
        return d <= 0 ? 0 : Math.max(0, d + rechargeMs(k) - maintenant);
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.kit." + cle, fr, args);
    }

    /** Ouvre (ou met à jour) le menu des kits chez le joueur. */
    static void envoyerEtat(ServerPlayer p, boolean ouvrir) {
        List<ReseauGrades.VueKit> vues = new ArrayList<>();
        long maintenant = System.currentTimeMillis();
        String sien = "";
        List<Grade> grades = new ArrayList<>(Grade.values());
        java.util.Collections.reverse(grades);
        for (Grade g : grades) {
            for (DefinitionsGrades.KitDef k : DefinitionsGrades.kitsDe(g)) {
                if (k.objets().isEmpty() || !visible(p.getUUID(), g, k)) continue;
                boolean ok = debloque(p.getUUID(), k);
                if (ok && sien.isEmpty() && g == gradeDeBase(p.getUUID())) sien = idVue(k);
                vues.add(new ReseauGrades.VueKit(idVue(k), g.id, k.nom(), k.objets(), rechargeMs(k), ok, ok ? reste(p.getUUID(), k, maintenant) : 0));
            }
        }
        ReseauGrades.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauGrades.EtatKits(ouvrir, vues, sien));
    }

    /** Demande d'un kit venue du menu (identifiant « grade/kit »). */
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
        Component non = Restrictions.empechement(p);
        if (non != null) {
            p.sendSystemMessage(non.copy().withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        if (recharges == null || recharges.illisible() || ServeurGrades.registre == null || ServeurGrades.registre.illisible()) {
            p.sendSystemMessage(msg("indisponible", "Les kits sont indisponibles pour le moment."));
            return;
        }
        String[] morceaux = d.id().split("/", 2);
        DefinitionsGrades.KitDef k = morceaux.length == 2 ? DefinitionsGrades.kit(morceaux[0], morceaux[1]) : null;
        if (k == null || k.objets().isEmpty() || !debloque(p.getUUID(), k)) {
            p.sendSystemMessage(msg("pas_le_tien", "Ce kit n'est pas débloqué pour toi."));
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
        if (!recharges.noter(cle(p.getUUID(), k), maintenant)) {
            p.sendSystemMessage(msg("indisponible", "Les kits sont indisponibles pour le moment."));
            return;
        }
        for (ItemStack modele : k.objets()) {
            ItemStack s = modele.copy();
            if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
        }
        p.containerMenu.broadcastChanges();
        ServeurGrades.registre.journaliser("kit | " + p.getGameProfile().getName() + " (" + p.getUUID() + ") | " + idVue(k));
        p.sendSystemMessage(msg("recu", "Kit %s récupéré.", k.nom()));
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
                    Component non = Restrictions.empechement(p);
                    if (non != null) {
                        p.sendSystemMessage(non.copy().withStyle(net.minecraft.ChatFormatting.RED));
                        return 0;
                    }
                    java.util.UUID uuid = p.getUUID();
                    LienDiscord.verifierSoutien(uuid, () -> {
                        ServerPlayer j = c.getSource().getServer().getPlayerList().getPlayer(uuid);
                        if (j != null) envoyerEtat(j, true);
                    });
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
