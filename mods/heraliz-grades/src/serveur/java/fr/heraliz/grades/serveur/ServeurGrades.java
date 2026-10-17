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
 * Partie serveur : registre des grades, commandes /grade, /kit, /lier et /delier, préfixe du grade devant le pseudo (liste Tab, chat).
 * <p>
 * Nom affiché : « [CLAN] [Traqueur] Pseudo » (tag de clan lu dans heraliz-clansetbases, puis grade), mis par NameFormat (chat,
 * liste Tab : on remplace le nom de liste posé par les clans, qui doublerait le tag) et dans le nom affiché Bukkit.
 */
public final class ServeurGrades implements Partie {
    /** Null hors d'un serveur démarré. Fil serveur seulement. */
    static RegistreGrades registre;
    /** Dernier état AuthMe vu par joueur : quand il change, on renvoie l'arbre des commandes (/grade apparaît après /login). */
    private static final Map<UUID, Boolean> AUTH = new HashMap<>();
    /** Dernier tag de clan vu par joueur : quand il change (clan rejoint, quitté, renommé), on refait le nom affiché. */
    private static final Map<UUID, String> CLANS = new HashMap<>();
    private static int ticks;

    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::demarrage);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::arret);
        MinecraftForge.EVENT_BUS.addListener(CommandesGrades::enregistrer);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> KitsServeur.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> LienDiscord.enregistrer(e.getDispatcher()));
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
        LienDiscord.demarrer(e.getServer());
        AUTH.clear();
    }

    private static void arret(ServerStoppedEvent e) {
        registre = null;
        KitsServeur.arreter();
        TabServeur.oublier();
        LienDiscord.arreter();
        AUTH.clear();
    }

    private static void nomAffiche(PlayerEvent.NameFormat e) {
        if (registre == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        net.minecraft.network.chat.MutableComponent clan = TagClan.prefixe(p);
        CLANS.put(p.getUUID(), TagClan.texte(clan));
        // morceaux côte à côte (pas imbriqués) : le pseudo garde sa couleur au lieu d'hériter de celle du grade ou du clan
        net.minecraft.network.chat.MutableComponent nom = net.minecraft.network.chat.Component.empty();
        if (clan != null) nom.append(TagClan.joli(clan));
        nom.append(registre.affiche(p.getUUID()).prefixe()).append(e.getDisplayname());
        e.setDisplayname(nom);
    }

    /** Liste Tab : le nom affiché complet (clan + grade + pseudo), à la place du nom posé par les clans (tag en double). */
    private static void nomTab(PlayerEvent.TabListNameFormat e) {
        if (registre == null) return;
        e.setDisplayName(e.getEntity().getDisplayName());
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (registre == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        PontBukkit.brancherChat();
        registre.noterNom(p.getUUID(), p.getGameProfile().getName());
        rafraichir(p);
        TabServeur.maj(p.server, true);
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        AUTH.remove(e.getEntity().getUUID());
        CLANS.remove(e.getEntity().getUUID());
        KitsServeur.oublier(e.getEntity().getUUID());
        LienDiscord.oublier(e.getEntity().getUUID());
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || registre == null || ++ticks % 40 != 0) return;
        if (ticks % 200 == 0) KitsServeur.relireSiChange();
        if (ticks % 120 == 0) LienDiscord.suivre();
        TabServeur.maj(e.getServer(), false);
        MinecraftServer srv = e.getServer();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            String clan = TagClan.texte(TagClan.prefixe(p));
            if (!clan.equals(CLANS.get(p.getUUID()))) rafraichir(p);
            boolean auth = PontBukkit.authentifie(p);
            Boolean avant = AUTH.put(p.getUUID(), auth);
            if (avant != null && avant == auth) continue;
            PontBukkit.nomAffiche(p, nomBukkit(p));
            if (registre.fiche(p.getUUID()) != null && registre.fiche(p.getUUID()).staff() != null) srv.getCommands().sendCommands(p);
        }
    }

    /** Remet à jour le préfixe du joueur partout (après un changement de grade ou à la connexion). */
    static void rafraichir(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
        PontBukkit.nomAffiche(p, nomBukkit(p));
    }

    private static String nomBukkit(ServerPlayer p) {
        Grade g = registre == null ? Grade.SURVIVANT : registre.affiche(p.getUUID());
        return TagClan.bukkit(TagClan.prefixe(p)) + g.prefixeBukkit() + "§f" + p.getGameProfile().getName();
    }
}
