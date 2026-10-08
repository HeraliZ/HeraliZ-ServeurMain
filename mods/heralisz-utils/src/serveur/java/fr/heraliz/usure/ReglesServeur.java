package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Regles en vigueur cote serveur (fil serveur) : lues du fichier {@code serverconfig/heraliz_usure-server.toml} au
 * demarrage du monde, relues quand le fichier change (verification de la date toutes les 2 s, lecture une fois la date
 * stable : un fichier en cours d'enregistrement n'est jamais lu a moitie), reecrites par l'ecran d'administration. A
 * chaque changement, les regles partent a tous les joueurs (quelques centaines d'octets).
 */
public final class ReglesServeur {
    private ReglesServeur() {
    }

    private static volatile Regles actives = Regles.defauts();
    private static Path fichier;
    /** Date du fichier deja prise en compte, et date vue au dernier controle (lecture quand elle ne bouge plus). */
    private static FileTime dateConnue, dateVue;
    private static int controle;
    private static boolean erreurSignalee;

    public static Regles get() {
        return actives;
    }

    /** Demarrage du monde : lecture du fichier (cree avec les valeurs par defaut s'il manque, complete s'il est ancien). */
    static void onDemarrage(ServerAboutToStartEvent e) {
        // meme dossier que les configurations « serveur » de Forge : <monde>/serverconfig
        fichier = e.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(UsureConfig.NOM).normalize();
        dateConnue = dateVue = null;
        erreurSignalee = false;
        if (!Files.exists(fichier)) {
            actives = Regles.defauts();
            ecrireFichier(actives);
            HeralizUsure.LOG.info("[usure] reglages par defaut ecrits dans {}", fichier);
            return;
        }
        relire(true);
    }

    static void onArret(ServerStoppedEvent e) {
        fichier = null;
        actives = Regles.defauts();
    }

    /** Toutes les 2 s : le fichier a-t-il ete modifie a la main ? */
    static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || fichier == null || ++controle < 40) return;
        controle = 0;
        FileTime date;
        try {
            date = Files.getLastModifiedTime(fichier);
        } catch (IOException ex) {
            return; // fichier absent ou en cours de remplacement : on reessaie plus tard
        }
        if (date.equals(dateConnue)) return;
        if (!date.equals(dateVue)) {
            dateVue = date; // la date bouge encore : on attend qu'elle soit stable
            return;
        }
        relire(false);
    }

    private static void relire(boolean demarrage) {
        try {
            FileTime date = Files.getLastModifiedTime(fichier);
            UsureConfig.Lecture l = UsureConfig.lire(fichier);
            dateConnue = dateVue = date;
            erreurSignalee = false;
            boolean change = !l.regles().memesQue(actives);
            actives = l.regles();
            if (l.incomplet()) ecrireFichier(actives);
            HeralizUsure.LOG.info("[usure] reglages {} ({}) : {} surcharge(s), {} accessoire(s), actif = {}", demarrage ? "charges" : "relus",
                    fichier.getFileName(), actives.surcharges.size(), actives.accessoires.size(), actives.actif);
            if (change && !demarrage) diffuser();
        } catch (Exception ex) {
            // fichier illisible (faute de frappe...) : on garde les regles en cours, le fichier n'est pas touche
            try {
                dateConnue = dateVue = Files.getLastModifiedTime(fichier);
            } catch (IOException ignored) {
                // tant pis
            }
            if (!erreurSignalee) {
                erreurSignalee = true;
                HeralizUsure.LOG.warn("[usure] {} illisible, reglages precedents gardes : {}", fichier, ex.toString());
            }
        }
    }

    private static void ecrireFichier(Regles r) {
        if (fichier == null) return;
        try {
            UsureConfig.ecrire(fichier, r);
            dateConnue = dateVue = Files.getLastModifiedTime(fichier);
        } catch (IOException ex) {
            HeralizUsure.LOG.error("[usure] impossible d'ecrire {}", fichier, ex);
        }
    }

    /** Connexion d'un joueur (et /reload) : il recoit les regles ; les categories automatiques sont recalculees. */
    static void onDatapackSync(OnDatapackSyncEvent e) {
        actives.viderCache();
        if (e.getPlayer() != null) Reseau.envoyer(e.getPlayer(), new Reseau.SyncRegles(actives, false));
    }

    static void diffuser() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s != null) Reseau.envoyerATous(new Reseau.SyncRegles(actives, false));
    }

    /**
     * Remplace les regles (fil serveur) : valeurs corrigees, fichier reecrit d'un coup, joueurs prevenus. Pour d'autres
     * mods et le banc d'essai ; l'ecran passe par {@link #enregistrerDepuisEcran} qui verifie les droits.
     */
    public static void appliquer(Regles r) {
        r.corriger();
        actives = r;
        ecrireFichier(r);
        diffuser();
    }

    /** Ecran d'administration : verifie les droits, corrige les valeurs, reecrit le fichier, previent tout le monde. */
    static void enregistrerDepuisEcran(ServerPlayer p, Regles r) {
        if (!p.hasPermissions(2)) {
            p.sendSystemMessage(Component.translatable("heraliz_usure.cmd.refus").withStyle(ChatFormatting.RED));
            return;
        }
        appliquer(r);
        HeralizUsure.LOG.info("[usure] reglages modifies en jeu par {}", p.getGameProfile().getName());
        p.sendSystemMessage(Component.translatable("heraliz_usure.ecran.enregistre").withStyle(ChatFormatting.GREEN));
    }
}
