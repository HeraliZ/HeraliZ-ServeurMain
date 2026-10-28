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
 * Partie serveur : registre des grades, commandes /grade, /kit, /discord lier, /discord delier, /avantages nick, /avantages couleur, /boutique abonnement, /staff vanish,
 * /staff nametag et /staff panel (panel du staff, touche P : {@link PanelStaff}), préfixe du grade devant le pseudo (liste Tab, chat).
 * <p>
 * Nom affiché : « [CLAN] [Traqueur] Pseudo » (tag de clan lu dans heraliz-clansetbases, puis grade), mis par NameFormat (chat,
 * liste Tab : on remplace le nom de liste posé par les clans, qui doublerait le tag) et dans le nom affiché Bukkit.
 */
public final class ServeurGrades implements Partie {
    /** Null hors d'un serveur démarré. Fil serveur seulement. */
    static RegistreGrades registre;
    /** Dernier état AuthMe vu par joueur : quand il change, on renvoie l'arbre des commandes (/grade apparaît après /login). */
    private static final Map<UUID, Boolean> AUTH = new HashMap<>();
    /**
     * Dernier tag de clan (et surnom) vu par joueur : quand il change (clan rejoint, quitté, renommé ; surnom perdu ou
     * retrouvé avec le grade), on refait le nom affiché.
     */
    private static final Map<UUID, String> CLANS = new HashMap<>();
    private static int ticks;

    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::demarrage);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::arret);
        MinecraftForge.EVENT_BUS.addListener(CommandesGrades::enregistrer);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> KitsServeur.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> LienDiscord.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> EditeurServeur.enregistrer(e.getDispatcher()));
        // /avantages nick et /avantages couleur (grades de la boutique, 22/10)
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> SurnomsServeur.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::nomAffiche);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServeurGrades::nomTab);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::connexion);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(ServeurGrades::tick);
        ReseauGrades.RECEPTION_SERVEUR.brancher(KitsServeur::demande);
        // zones VIP délimitées par le staff (/zonevip), réservées aux grades de la boutique
        ZonesVip.brancher();
        // warps : menu /warp, seulement depuis le spawn
        WarpsServeur.brancher();
        TpaServeur.brancher();
        // kit de départ des nouveaux joueurs (23/10)
        KitDepart.brancher();
        // éditeur des grades des op (Registre des grades, /grade editeur)
        EditeurServeur.brancher();
        // Points Boutique (solde du compte du site) : /boutique, /trade, /pb
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> CommandesPb.enregistrer(e.getDispatcher()));
        BoutiqueServeur.brancher();
        EchangeServeur.brancher();
        // hôtel des ventes (2.9.0) : /hdv au spawn, PNJ commissaire-priseur (HeraliZ Quêtes, HdvApi)
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> HdvServeur.enregistrer(e.getDispatcher()));
        HdvServeur.brancher();
        // bons réglés par les op (/bon editeur) : hérals, PB, grade, bonus de loot, commande au clic droit
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> BonServeur.enregistrer(e.getDispatcher()));
        BonServeur.brancher();
        // modération du staff (2.7.0) : /staff vanish, /staff nametag ; /boutique abonnement et /staff panel (panneau admin des op)
        VanishServeur.brancher();
        // panel du staff (touche P, 25/10) : actions selon le grade du staff, permissions du Fondateur, journal
        PanelStaff.brancher();
        // chat à canaux (25/10) : Proximité, Global, Clan
        ChatServeur.brancher();
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> AbonnementServeur.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            BoutiqueServeur.tick();
            EchangeServeur.tick();
            HdvServeur.tick();
            BonServeur.tick();
        });
    }

    private static void demarrage(ServerAboutToStartEvent e) {
        java.nio.file.Path dossier = e.getServer().getServerDirectory().toPath().resolve("heraliz_grades");
        // les définitions d'abord : les grades créés en jeu doivent exister avant de lire qui les a
        DefinitionsGrades.charger(dossier);
        registre = new RegistreGrades(dossier);
        KitsServeur.demarrer(dossier);
        TpaServeur.demarrer(dossier);
        KitDepart.charger();
        SurnomsServeur.demarrer(dossier);
        LienDiscord.demarrer(e.getServer());
        Pb.demarrer(e.getServer());
        BoutiqueServeur.demarrer(e.getServer(), dossier);
        EchangeServeur.demarrer(e.getServer(), dossier);
        HdvServeur.demarrer(e.getServer(), dossier);
        BonServeur.demarrer(e.getServer(), dossier);
        VanishServeur.demarrer(dossier);
        PanelStaff.demarrer(dossier);
        AUTH.clear();
    }

    private static void arret(ServerStoppedEvent e) {
        registre = null;
        KitsServeur.arreter();
        TpaServeur.arreter();
        SurnomsServeur.arreter();
        TabServeur.oublier();
        LienDiscord.arreter();
        EchangeServeur.arreter();
        HdvServeur.arreter();
        BoutiqueServeur.arreter();
        BonServeur.arreter();
        VanishServeur.arreter();
        PanelStaff.arreter();
        Pb.arreter();
        AUTH.clear();
    }

    private static void nomAffiche(PlayerEvent.NameFormat e) {
        if (registre == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        net.minecraft.network.chat.MutableComponent clan = TagClan.prefixe(p);
        // surnom (/avantages nick) : « ~Surnom », vrai pseudo au survol ; gardé avec le tag pour refaire le nom s'il change
        String surnom = SurnomsServeur.surnomAffiche(p);
        CLANS.put(p.getUUID(), TagClan.texte(clan) + "|" + surnom);
        // morceaux côte à côte (pas imbriqués) : le pseudo garde sa couleur au lieu d'hériter de celle du grade ou du clan
        net.minecraft.network.chat.MutableComponent nom = net.minecraft.network.chat.Component.empty();
        if (clan != null) nom.append(TagClan.joli(clan));
        net.minecraft.network.chat.Component pseudo = surnom == null ? e.getDisplayname()
                : net.minecraft.network.chat.Component.literal("~" + surnom).withStyle(st -> st.withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, net.minecraft.network.chat.Component.literal(p.getGameProfile().getName()))));
        nom.append(registre.affiche(p.getUUID()).prefixe()).append(pseudo);
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
        EditeurServeur.envoyerDefinitions(p);
        // joueurs cachés (/staff vanish) : retirés tout de suite chez celui qui arrive, et lui caché s'il l'est
        VanishServeur.connexion(p);
        rafraichir(p);
        TabServeur.maj(p.server, true);
        // objets d'un échange conclu pendant son absence
        EchangeServeur.connexion(p);
        // hôtel des ventes : ventes interrompues par un arrêt, objets achetés en attente
        HdvServeur.connexion(p);
        // effet « Bonus de loot » et bons restés en cours
        BonServeur.connexion(p);
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        AUTH.remove(e.getEntity().getUUID());
        CLANS.remove(e.getEntity().getUUID());
        KitsServeur.oublier(e.getEntity().getUUID());
        SurnomsServeur.oublier(e.getEntity().getUUID());
        LienDiscord.oublier(e.getEntity().getUUID());
        EchangeServeur.deconnexion(e.getEntity().getUUID());
        HdvServeur.deconnexion(e.getEntity().getUUID());
        VanishServeur.deconnexion(e.getEntity().getUUID());
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || registre == null || ++ticks % 40 != 0) return;
        if (ticks % 200 == 0) KitsServeur.relireSiChange();
        if (ticks % 120 == 0) LienDiscord.suivre();
        VanishServeur.appliquer(e.getServer());
        TabServeur.maj(e.getServer(), false);
        MinecraftServer srv = e.getServer();
        if (ticks % 1200 == 0) {
            for (java.util.UUID u : registre.expirer()) {
                ServerPlayer p = srv.getPlayerList().getPlayer(u);
                if (p == null) continue;
                rafraichir(p);
                // commandes réservées aux grades (/avantages nick…) remises à jour chez le joueur
                srv.getCommands().sendCommands(p);
                // 25/10 (audit) : un grade en réserve qui démarre n'est plus annoncé comme « terminé, à racheter »
                RegistreGrades.Fiche f = registre.fiche(u);
                p.sendSystemMessage(f != null && f.boutique() != null
                        ? net.minecraft.network.chat.Component.translatableWithFallback("message.heraliz_grades.achat_reserve_demarre",
                        "Ton grade %s, qui était en réserve, démarre maintenant.", f.boutique().nom())
                        : net.minecraft.network.chat.Component.translatableWithFallback("message.heraliz_grades.achat_fin",
                        "Ton grade de la boutique est terminé. Il ne se renouvelle pas tout seul : tu peux le racheter sur le site."));
            }
        }
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            // couleur du chat (/avantages couleur) : suit la permission du grade (perdue = blanc)
            SurnomsServeur.majChat(p);
            String clan = TagClan.texte(TagClan.prefixe(p)) + "|" + SurnomsServeur.surnomAffiche(p);
            if (!clan.equals(CLANS.get(p.getUUID()))) rafraichir(p);
            boolean auth = PontBukkit.authentifie(p);
            Boolean avant = AUTH.put(p.getUUID(), auth);
            if (avant != null && avant == auth) continue;
            PontBukkit.nomAffiche(p, nomBukkit(p));
            if (registre.fiche(p.getUUID()) != null && registre.fiche(p.getUUID()).staff() != null || p.hasPermissions(2)) srv.getCommands().sendCommands(p);
        }
    }

    /** Note une ligne dans le journal des grades (rien hors d'un serveur démarré). */
    static void registreJournal(String ligne) {
        if (registre != null) registre.journaliser(ligne);
    }

    /** Remet à jour le préfixe du joueur partout (après un changement de grade ou à la connexion). */
    static void rafraichir(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
        PontBukkit.nomAffiche(p, nomBukkit(p));
    }

    private static String nomBukkit(ServerPlayer p) {
        Grade g = registre == null ? Grade.SURVIVANT : registre.affiche(p.getUUID());
        String surnom = SurnomsServeur.surnomAffiche(p);
        return TagClan.bukkit(TagClan.prefixe(p)) + g.prefixeBukkit() + "§f" + (surnom == null ? p.getGameProfile().getName() : "~" + surnom);
    }
}
