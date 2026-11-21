package fr.heraliz.usure;

import fr.heralisz.utils.serveur.DatesFichiers;
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

/**
 * Regles en vigueur cote serveur (fil serveur) : lues du fichier {@code serverconfig/heraliz_usure-server.toml} au
 * demarrage du monde, relues quand le fichier change (lecture une fois la date stable : un fichier en cours
 * d'enregistrement n'est jamais lu a moitie), reecrites par l'ecran d'administration. A chaque changement, les regles
 * partent a tous les joueurs (quelques centaines d'octets).
 * <p>
 * 21/11 (audit) : la date du fichier est relevee par le fil a part du module ({@link DatesFichiers}, toutes les 5 s),
 * comme pour les autres fichiers relus quand ils changent ; avant, le fil du serveur la demandait lui-meme au disque
 * toutes les 2 s, meme quand rien ne changeait. Il ne regarde plus que la date gardee la-bas ; quand elle a change et
 * n'a pas bouge d'un controle a l'autre (2 s), il relit la date au disque une seule fois, juste avant de lire le
 * fichier : s'il a encore bouge depuis le releve du fil, il attend (meme garantie qu'avant contre le fichier a moitie
 * enregistre). Un changement a la main est pris en compte en 2 a 9 s (avant : 2 a 4 s) ; l'ecran
 * {@code /usure config} applique toujours tout de suite.
 */
public final class ReglesServeur {
    private ReglesServeur() {
    }

    private static volatile Regles actives = Regles.defauts();
    private static Path fichier;
    /** Date du fichier deja prise en compte, et date vue au dernier controle (ms, 0 : aucune ; lecture quand elle ne
     *  bouge plus). */
    private static long dateConnue, dateVue;
    private static int controle;
    private static boolean erreurSignalee;

    public static Regles get() {
        return actives;
    }

    /** Demarrage du monde : lecture du fichier (cree avec les valeurs par defaut s'il manque, complete s'il est ancien). */
    static void onDemarrage(ServerAboutToStartEvent e) {
        // meme dossier que les configurations « serveur » de Forge : <monde>/serverconfig
        fichier = e.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(UsureConfig.NOM).normalize();
        dateConnue = dateVue = 0L;
        erreurSignalee = false;
        DatesFichiers.date(fichier); // inscrit le fichier au fil des dates : premier releve ici, au demarrage, pas en jeu
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

    /** Toutes les 2 s : le fichier a-t-il ete modifie a la main ? (date relevee par DatesFichiers : le disque n'est
     *  touche ici que quand le fichier a change) */
    static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || fichier == null || ++controle < 40) return;
        controle = 0;
        long date = DatesFichiers.date(fichier);
        if (date == 0L || date == dateConnue) return; // absent (en cours de remplacement ?) ou deja pris en compte
        if (date != dateVue) {
            dateVue = date; // la date bouge encore : on attend qu'elle soit stable
            return;
        }
        // meme date qu'au controle d'avant : le fichier a-t-il rebouge depuis le releve du fil ? (un acces au disque par
        // changement, juste avant la lecture du fichier lui-meme)
        long maintenant;
        try {
            maintenant = Files.getLastModifiedTime(fichier).toMillis();
        } catch (IOException ex) {
            return; // fichier absent ou en cours de remplacement : on reessaie plus tard
        }
        if (maintenant != date) {
            dateVue = maintenant; // encore en cours d'enregistrement : on attend qu'il ne bouge plus
            return;
        }
        relire(false);
    }

    private static void relire(boolean demarrage) {
        try {
            long date = Files.getLastModifiedTime(fichier).toMillis();
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
                dateConnue = dateVue = Files.getLastModifiedTime(fichier).toMillis();
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
            dateConnue = dateVue = Files.getLastModifiedTime(fichier).toMillis();
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
