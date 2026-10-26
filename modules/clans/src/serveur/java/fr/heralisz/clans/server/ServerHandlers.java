package fr.heralisz.clans.server;

import fr.heralisz.clans.model.Texte;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traitement cote serveur des messages client -> serveur : cadence anti-abus (1 action / 200 ms par joueur), puis
 * delegation a {@link ClanService} qui verifie tout (permissions, longueurs, cooldowns, argent). Le client n'est
 * jamais cru.
 */
public final class ServerHandlers {
    private ServerHandlers() {
    }

    /** Anti-abus : 1 action reseau toutes les 200 ms par joueur. */
    private static final Map<UUID, Long> DERNIERE_ACTION = new HashMap<>();
    private static final long CADENCE_MS = 200;

    /** Vrai si l'action passe la cadence ; sinon un message « trop vite » et faux. */
    static boolean cadence(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = DERNIERE_ACTION.get(p.getUUID());
        if (last != null && now - last < CADENCE_MS) {
            erreur(p, "msg.trop_rapide");
            return false;
        }
        DERNIERE_ACTION.put(p.getUUID(), now);
        return true;
    }

    /** Oublie un joueur (deconnexion). */
    static void oublier(UUID joueur) {
        DERNIERE_ACTION.remove(joueur);
    }

    /** Message d'erreur : chat (gris) + toast ERREUR. */
    public static void erreur(ServerPlayer p, String cle, Object... args) {
        Component c = Component.translatable(cle, args);
        p.sendSystemMessage(c.copy().withStyle(s -> s.withColor(0xFF6B6B)));
        ClansNet.toPlayer(p, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.ERREUR, cleEtArgs(cle, args), "", null));
    }

    /**
     * Arguments d'une notice ERREUR / INFO : la cle de traduction puis ses arguments en texte. Le client traduit dans la
     * langue du joueur (le serveur dedie n'a pas les fichiers de langue : un texte deja ecrit ici resterait une cle).
     * Un argument traduisible sans parametre (libelle de permission, nom d'entite) part en cle, traduite aussi par le
     * client ; les autres composants partent en texte. 7 arguments au plus (le paquet en lit 8).
     */
    public static List<String> cleEtArgs(String cle, Object... args) {
        List<String> l = new ArrayList<>(Math.min(8, args.length + 1));
        l.add(coupe(cle));
        for (int i = 0; i < args.length && l.size() < 8; i++) {
            Object o = args[i];
            String s;
            if (o instanceof Component c) {
                s = c.getContents() instanceof TranslatableContents t && t.getArgs().length == 0 && c.getSiblings().isEmpty()
                        ? t.getKey() : c.getString();
            } else {
                s = String.valueOf(o);
            }
            l.add(coupe(s));
        }
        return l;
    }

    /** Message d'information simple dans le chat. */
    public static void info(ServerPlayer p, String cle, Object... args) {
        p.sendSystemMessage(Component.translatable(cle, args));
    }

    /** Les arguments d'une notice font 96 caracteres au plus (le paquet coupe aussi de lui-meme, voir Texte). */
    static String coupe(String s) {
        return Texte.couper(s, 96);
    }

    /** CreerClanC2S : validation tag/nom/description, unicite, cout, fondateur = CHEF, snapshot + tags. */
    public static void creerClan(ServerPlayer p, Packets.CreerClanC2S m) {
        if (!cadence(p)) return;
        if (!banniereRecevable(p, m.calquesEnvoyes())) return;
        ClanService.creer(p, m.tag(), m.nom(), m.description(), m.banniere().valider());
    }

    /** Refuse une banniere annoncee avec plus de 12 calques (client modifie : l'atelier s'arrete a 12). */
    private static boolean banniereRecevable(ServerPlayer p, int calquesEnvoyes) {
        String refus = fr.heralisz.clans.model.Banniere.refus(calquesEnvoyes);
        if (refus == null) return true;
        erreur(p, refus, fr.heralisz.clans.model.Banniere.MAX_CALQUES);
        fr.heralisz.clans.HeraliszClans.LOG.warn("[clans] banniere refusee pour {} : {} calques annonces (max {})",
                p.getGameProfile().getName(), calquesEnvoyes, fr.heralisz.clans.model.Banniere.MAX_CALQUES);
        return false;
    }

    /** InviterC2S : perm INVITER, joueur en ligne, pas deja membre, place libre, invitation 5 min + notice. */
    public static void inviter(ServerPlayer p, Packets.InviterC2S m) {
        if (!cadence(p)) return;
        ClanService.inviter(p, m.nom());
    }

    /** ReponseInvitationC2S : invitation valide, delai de rejoindre, place libre ; accepte -> membre RECRUE. */
    public static void reponseInvitation(ServerPlayer p, Packets.ReponseInvitationC2S m) {
        if (!cadence(p)) return;
        ClanService.repondre(p, m.clanId(), m.accepte());
    }

    /** ActionMembreC2S : EXCLURE/PROMOUVOIR/RETROGRADER (perm + hierarchie), TRANSFERER (chef seulement). */
    public static void actionMembre(ServerPlayer p, Packets.ActionMembreC2S m) {
        if (!cadence(p)) return;
        switch (m.genre()) {
            case EXCLURE -> ClanService.exclure(p, m.cible());
            case PROMOUVOIR -> ClanService.promouvoir(p, m.cible());
            case RETROGRADER -> ClanService.retrograder(p, m.cible());
            case TRANSFERER -> ClanService.transferer(p, m.cible());
        }
    }

    /** BanqueC2S : montant > 0, perm, solde suffisant (joueur ou banque), journal, sauvegarde immediate. */
    public static void banque(ServerPlayer p, Packets.BanqueC2S m) {
        if (!cadence(p)) return;
        ClanService.banque(p, m.depot(), m.montant());
    }

    /** ParametreC2S : perm MODIFIER_*, validation, unicite, cooldown 72 h par champ. */
    public static void parametre(ServerPlayer p, Packets.ParametreC2S m) {
        if (!cadence(p)) return;
        ClanService.parametre(p, m.champ(), m.valeur());
    }

    /** BanniereC2S : <= 12 calques, validation, perm MODIFIER_BANNIERE, cooldown, journal, diffusion des tags (couleur). */
    public static void banniere(ServerPlayer p, Packets.BanniereC2S m) {
        if (!cadence(p)) return;
        if (!banniereRecevable(p, m.calquesEnvoyes())) return;
        ClanService.banniere(p, m.banniere().valider());
    }

    /** PermsC2S : chef seulement, grade modifiable, masque filtre. */
    public static void perms(ServerPlayer p, Packets.PermsC2S m) {
        if (!cadence(p)) return;
        ClanService.perms(p, m.grade(), m.masque());
    }

    /**
     * PermsJoueurC2S : reglages individuels d'un membre (GERER_PERMISSIONS, grade strictement superieur a la cible,
     * pas de permission accordee qu'on n'a pas, chef non reglable) ; refus = message + snapshot correctif.
     */
    public static void permsJoueur(ServerPlayer p, Packets.PermsJoueurC2S m) {
        if (!cadence(p)) {
            Sync.envoyerSnapshot(p); // l'editeur affiche deja le changement : on le corrige
            return;
        }
        ClanService.permsJoueur(p, m.membre(), m.masque(), m.autorise(), m.refuse());
    }

    /**
     * SimpleC2S : OUVRIR (ecran ouvert + renvoie snapshot/invitations), FERMER (ecran ferme), QUITTER, DISSOUDRE (chef),
     * CLASSEMENT, CHAT_BASCULE.
     */
    public static void simple(ServerPlayer p, Packets.SimpleC2S m) {
        if (m.genre() == Packets.SimpleC2S.Genre.FERMER) {
            Sync.ecranFerme(p.getUUID()); // sans cadence : ne coute rien et ne doit jamais etre perdu
            return;
        }
        if (!cadence(p)) return;
        switch (m.genre()) {
            case OUVRIR -> {
                Sync.ecranOuvert(p.getUUID());
                envoyerEtat(p);
            }
            case QUITTER -> ClanService.quitter(p);
            case DISSOUDRE -> ClanService.dissoudre(p);
            case CLASSEMENT -> ClansNet.toPlayer(p, fr.heralisz.clans.saison.SaisonVue.pour(p));
            case CHAT_BASCULE -> ClanService.chatBascule(p);
            case COFFRE -> CoffreClan.ouvrir(p);
            default -> {
            }
        }
    }

    /**
     * AmeliorerC2S : arbre du clan (chef). Passe par la cadence comme les autres actions (25/10, audit : avant, un double
     * clic sur « Améliorer » envoyait deux paquets traites tous les deux, et achetait deux paliers d'un coup).
     */
    public static void ameliorer(ServerPlayer p, Packets.AmeliorerC2S m) {
        if (!cadence(p)) return;
        fr.heralisz.clans.model.Arbre.Branche b = fr.heralisz.clans.model.Arbre.Branche.parId(m.branche());
        if (b != null) ClanService.ameliorer(p, b);
    }

    /** RavitaillementC2S : zone connue, membre du proprietaire, perm RAVITAILLEMENT, delai ecoule -> signal. */
    public static void ravitaillement(ServerPlayer p, Packets.RavitaillementC2S m) {
        if (!cadence(p)) return;
        fr.heralisz.clans.territory.Territoires.demanderRavitaillement(p, m.zoneId());
    }

    /** Envoie au joueur son etat courant : snapshot de son clan (ou null) et ses invitations. */
    public static void envoyerEtat(ServerPlayer p) {
        Sync.envoyerEtat(p);
    }
}
