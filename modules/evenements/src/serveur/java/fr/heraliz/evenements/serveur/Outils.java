package fr.heraliz.evenements.serveur;

import fr.heraliz.evenements.HeralizEvenements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ce que tous les événements partagent : annonces (notification en haut à gauche chez les joueurs, comme les vagues
 * d'extraction), secteur de la carte M (jamais la position exacte), journal, points de saison et état des autres mods
 * (par réflexion : sans eux, rien ne casse).
 */
public final class Outils {
    private Outils() {
    }

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(PARIS);
    /** Côté d'un secteur de la carte M (grille de 500 m depuis le coin nord-ouest de la bordure du monde). */
    private static final int SECTEUR = 500;
    @Nullable
    static Path dossier;

    // ------------------------------------------------------------------ annonces
    /**
     * Annonce à tous les joueurs : préfixe {@code heraliz_evenements.chat.prefixe} (le jeu des joueurs la montre en
     * notification en haut à gauche, pas dans le chat) et un petit son. {@code chat} : aussi une ligne dans le chat
     * général, « [Événement] … » comme une annonce du serveur (gardée dans le chat : coordonnées à relire).
     */
    public static void annoncer(MinecraftServer server, Component texte, boolean chat) {
        MutableComponent m = Component.translatableWithFallback("heraliz_evenements.chat.prefixe", "[Événement] ").append(texte);
        MutableComponent ligne = chat ? Component.translatable("chat.type.announcement",
                t("heraliz_evenements.chat.nom", "Événement").withStyle(ChatFormatting.GOLD), texte) : null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(m);
            if (ligne != null) p.sendSystemMessage(ligne);
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.5f, 0.8f);
        }
        HeralizEvenements.LOG.info("[evenements] annonce : {}", texte.getString());
    }

    public static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    /** Secteur de la carte M (« C4 ») d'un point : colonnes A, B… d'ouest en est, lignes 1, 2… du nord au sud. */
    public static String secteur(ServerLevel level, double x, double z) {
        WorldBorder b = level.getWorldBorder();
        int col = (int) Math.max(0, Math.floor((x - b.getMinX()) / SECTEUR));
        int lig = (int) Math.max(0, Math.floor((z - b.getMinZ()) / SECTEUR)) + 1;
        return (col < 26 ? String.valueOf((char) ('A' + col)) : "Z" + col) + lig;
    }

    /** Joueurs en survie ou en aventure sur la carte (overworld), sans les bots de charge. */
    public static int joueursCarte(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == Level.OVERWORLD && !p.isSpectator() && !p.isCreative() && !p.getTags().contains("heraliz_bot")) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ journal
    /**
     * Une ligne dans {@code <serveur>/heraliz_evenements/journal.log} : heure prise ici, ajout au fichier sur le fil
     * d'écriture des événements, dans l'ordre (27/10, perf : le planning en écrit à chaque passage).
     */
    public static void journal(String ligne) {
        HeralizEvenements.LOG.info("[evenements] {}", ligne);
        Path dir = dossier;
        if (dir == null) return;
        String texte = HEURE.format(ZonedDateTime.now(PARIS)) + " | " + ligne + "\n";
        Path f = dir.resolve("journal.log");
        EcrituresFond.ajouter(f, () -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(f, texte, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                HeralizEvenements.LOG.warn("[evenements] journal impossible a ecrire : {}", e.toString());
            }
        });
    }

    // ------------------------------------------------------------------ autres mods (réflexion)
    @Nullable
    private static Method saisonEvenement, saisonBareme, saisonActive, saisonClanDe, saisonCoeur;
    private static boolean saisonCherche, saisonVueCherche;

    private static void chercherSaisonVue() {
        if (saisonVueCherche) return;
        saisonVueCherche = true;
        try {
            Class<?> c = Class.forName("fr.heralisz.clans.saison.SaisonApi");
            saisonBareme = c.getMethod("pointsEvenement", String.class);
            saisonActive = c.getMethod("active");
            saisonClanDe = c.getMethod("clanDe", java.util.UUID.class);
            saisonCoeur = c.getMethod("aUnCoeur", java.util.UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            saisonBareme = null;
        }
    }

    /** Points du barème de la saison pour cette source ({@code events.points.<source>}), 0 si inconnue ou sans saison. */
    public static double pointsBareme(String source) {
        chercherSaisonVue();
        try {
            return saisonBareme == null ? 0 : (double) saisonBareme.invoke(null, source);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** Pourquoi le clan du joueur ne gagnerait aucun point : 0 il en gagne, 1 pas de saison en cours, 2 sans clan, 3 clan sans cœur. */
    public static int raisonSansPoints(ServerPlayer p) {
        chercherSaisonVue();
        if (saisonBareme == null) return 1;
        try {
            if (!(boolean) saisonActive.invoke(null)) return 1;
            Object clan = saisonClanDe.invoke(null, p.getUUID());
            if (clan == null) return 2;
            return (boolean) saisonCoeur.invoke(null, clan) ? 0 : 3;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    /**
     * Points de saison d'un événement pour le clan du joueur ({@code SaisonApi.evenement} de HeraliZ Clans : barème
     * {@code events.points.<source>}, plafond par clan et par jour). Rend les points donnés (0 : aucun).
     */
    public static double pointsSaison(ServerPlayer p, String source) {
        if (!saisonCherche) {
            saisonCherche = true;
            try {
                saisonEvenement = Class.forName("fr.heralisz.clans.saison.SaisonApi").getMethod("evenement", ServerPlayer.class, String.class, double.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                HeralizEvenements.LOG.warn("[evenements] saison des clans absente : aucun point de saison ({})", e.toString());
            }
        }
        if (saisonEvenement == null) return 0;
        try {
            return (double) saisonEvenement.invoke(null, p, source, 1.0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizEvenements.LOG.warn("[evenements] points de saison non donnes a {} ({}) : {}", p.getGameProfile().getName(), source, e.toString());
            return 0;
        }
    }

    @Nullable
    private static Method clashwar;
    private static boolean clashwarCherche;

    /** Un tournoi ClashWar est ouvert (inscriptions ou matchs) : aucun événement ne part. */
    public static boolean clashwarEnCours() {
        if (!clashwarCherche) {
            clashwarCherche = true;
            try {
                clashwar = Class.forName("fr.heraliz.clashwar.tournoi.Tournois").getMethod("courant");
            } catch (ReflectiveOperationException | LinkageError e) {
                clashwar = null;
            }
        }
        try {
            return clashwar != null && clashwar.invoke(null) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
