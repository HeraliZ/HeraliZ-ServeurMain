package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.pont.Partie;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Partie serveur : registre des grades, commandes /grade et /kit, préfixe du grade devant le pseudo (liste Tab, chat).
 * <p>
 * Préfixe : NameFormat (nom affiché Forge, repris par la liste Tab et par le tag de clan de heraliz-clansetbases, qui se
 * place devant : « [CLAN] [Traqueur] Pseudo ») et nom affiché Bukkit (chat des serveurs hybrides).
 */
public final class ServeurGrades implements Partie {
    /** Null hors d'un serveur démarré. Fil serveur seulement. */
    static RegistreGrades registre;
    /** Dernier état AuthMe vu par joueur : quand il change, on renvoie l'arbre des commandes (/grade apparaît après /login). */
    private static final Map<UUID, Boolean> AUTH = new HashMap<>();
    private static int ticks;

    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::demarrage);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::arret);
        MinecraftForge.EVENT_BUS.addListener(CommandesGrades::enregistrer);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> KitsServeur.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::nomAffiche);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServeurGrades::nomTab);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::connexion);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::tick);
        ReseauGrades.RECEPTION_SERVEUR.brancher(KitsServeur::demande);
    }

    private static void demarrage(ServerAboutToStartEvent e) {
        java.nio.file.Path dossier = e.getServer().getServerDirectory().toPath().resolve("heraliz_grades");
        registre = new RegistreGrades(dossier);
        KitsServeur.demarrer(dossier);
        AUTH.clear();
    }

    private static void arret(ServerStoppedEvent e) {
        registre = null;
        KitsServeur.arreter();
        AUTH.clear();
    }

    private static void nomAffiche(PlayerEvent.NameFormat e) {
        if (registre == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        e.setDisplayname(registre.affiche(p.getUUID()).prefixe().append(e.getDisplayname()));
    }

    /** Liste Tab : sans clan, personne ne fixe le nom de la liste ; on y met le nom affiché (avec le grade). */
    private static void nomTab(PlayerEvent.TabListNameFormat e) {
        if (registre == null || e.getDisplayName() != null) return;
        e.setDisplayName(e.getEntity().getDisplayName());
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (registre == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        registre.noterNom(p.getUUID(), p.getGameProfile().getName());
        rafraichir(p);
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        AUTH.remove(e.getEntity().getUUID());
        KitsServeur.oublier(e.getEntity().getUUID());
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || registre == null || ++ticks % 40 != 0) return;
        if (ticks % 100 == 0) KitsServeur.relireSiChange();
        MinecraftServer srv = e.getServer();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            boolean auth = PontBukkit.authentifie(p);
            Boolean avant = AUTH.put(p.getUUID(), auth);
            if (avant != null && avant == auth) continue;
            PontBukkit.nomAffiche(p, registre.affiche(p.getUUID()).prefixeBukkit() + p.getGameProfile().getName());
            if (registre.fiche(p.getUUID()) != null && registre.fiche(p.getUUID()).staff() != null) srv.getCommands().sendCommands(p);
        }
    }

    /** Remet à jour le préfixe du joueur partout (après un changement de grade ou à la connexion). */
    static void rafraichir(ServerPlayer p) {
        Grade g = registre == null ? Grade.SURVIVANT : registre.affiche(p.getUUID());
        p.refreshDisplayName();
        p.refreshTabListName();
        PontBukkit.nomAffiche(p, g.prefixeBukkit() + p.getGameProfile().getName());
    }
}
