package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.crates.api.ClesApi;
import fr.heraliz.crates.cles.ComptesCles;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.grades.reseau.ReseauStaff;
import fr.heraliz.grades.staff.ActionStaff;
import fr.heraliz.grades.staff.OperationEco;
import fr.heralisz.clans.server.PanelClans;
import fr.heralisz.economie.server.EconomieCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Onglet « Économie » du panel du staff (03/11, demande du propriétaire : « ajouter dans le panel admin ce dont les
 * Fondateurs ont besoin : donner / retirer des sous, des clés, des points de clan, des PB ») : partie serveur. Le joueur ne
 * fait que DEMANDER ({@link ReseauStaff.Economie}) ; tout est décidé ici :
 * <ul>
 *     <li><b>qui</b> : le vrai Fondateur seulement (grade Fondateur, op 4, connecté sur AuthMe : {@link PanelStaff#fondateur}),
 *     comme donner un grade ; jamais réglable dans l'onglet Permissions ;</li>
 *     <li><b>quoi</b> ({@link OperationEco}) : hérals (la même opération que {@code /eco donner|retirer} : 10 millions au
 *     plus, plafond du solde, journal_admin.log) ; clés de caisse (compte de clés, une sorte par opération, 1 000 au plus) ;
 *     Points Boutique (par le site, comme la boutique : 20 000 au plus par opération, le site refuse au-delà de 50 000 créés
 *     en 24 h) ; points de saison d'un clan (PVE, PVP ou événements, notés au journal de la saison) et XP d'un clan
 *     ({@link PanelClans}) ;</li>
 *     <li><b>bornes</b> : montant de 1 au plafond de l'opération, motif obligatoire, cible (pseudo, connecté ou non, ou tag
 *     de clan) trouvée par le serveur ; jamais de solde négatif (retirer plus qu'il n'a est refusé) ; une opération toutes
 *     les 0,7 s, une opération de PB à la fois ;</li>
 *     <li><b>trace</b> : chaque opération réussie va au journal du staff (qui, quoi, combien, motif), sur le salon du staff
 *     Discord, et dans le journal propre à ce qu'elle change (journal_admin.log des hérals, journal des clés, journal de la
 *     boutique pour les PB, journal de la saison, journal du serveur pour l'XP).</li>
 * </ul>
 * La fiche de l'onglet montre ce qu'a la cible ({@link ReseauStaff.SoldeEco}), avant et après chaque opération. Fil serveur
 * seulement (les réponses du site y reviennent).
 */
final class PanelEconomie {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long ECART_MS = 700, ECART_VOIR_MS = 150, ECART_PB_VOIR_MS = 3000;
    private static final Pattern PSEUDO = Pattern.compile("[A-Za-z0-9_]{1,16}"), TAG = Pattern.compile("[A-Za-z0-9]{1,8}");
    private static final int MOTIF_MAX = 80, CLANS_MAX = 500;

    private static final Map<UUID, Long> DERNIERE = new HashMap<>(), DERNIER_VOIR = new HashMap<>(), DERNIER_PB = new HashMap<>();
    /** Fondateurs dont une opération de PB attend la réponse du site (une à la fois). */
    private static final Set<UUID> PB_EN_ROUTE = new HashSet<>();

    private PanelEconomie() {
    }

    static void brancher() {
        ReseauStaff.RECEPTION_ECONOMIE.brancher(r -> recevoir(r.joueur(), r.message()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            UUID u = e.getEntity().getUUID();
            DERNIERE.remove(u);
            DERNIER_VOIR.remove(u);
            DERNIER_PB.remove(u);
        });
    }

    static void vider() {
        DERNIERE.clear();
        DERNIER_VOIR.clear();
        DERNIER_PB.clear();
        PB_EN_ROUTE.clear();
    }

    /** Les clans pour la liste de l'onglet (Fondateur seulement) ; vide sans HeraliZ Clans. */
    static List<ReseauStaff.ClanLigne> clans() {
        List<ReseauStaff.ClanLigne> l = new ArrayList<>();
        try {
            for (PanelClans.Ligne c : PanelClans.clans(CLANS_MAX)) l.add(new ReseauStaff.ClanLigne(c.tag(), c.nom(), c.niveau()));
        } catch (LinkageError ex) {
            // HeraliZ Clans absent : pas de clans dans l'onglet
        }
        return l;
    }

    // ------------------------------------------------------------------ textes
    /** Nombre lisible (« 12 500 »), le même dans les trois langues. */
    static String nombre(long v) {
        return String.format(Locale.ROOT, "%,d", v).replace(',', ' ');
    }

    /** Unité d'une opération, traduite chez le joueur (argument « @clé » des messages du panel). */
    private static String unite(OperationEco op, String option) {
        return "@" + op.cleUnite(option);
    }

    /** Unité en français (journal du staff, salon Discord). */
    private static String uniteFr(OperationEco op, String option) {
        return switch (op) {
            case HERALS -> "hérals";
            case CLES -> "clé(s) " + option;
            case PB -> "PB";
            case SAISON -> "pts " + option;
            case XP_CLAN -> "XP";
        };
    }

    private static String nettoyer(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 32 || c == '§' || c == '|') continue;
            if (b.length() >= max) break;
            b.append(c);
        }
        return b.toString().strip();
    }

    // ------------------------------------------------------------------ réponses
    /** Fiche de l'onglet : ce qu'a la cible (texte « clé|arg|arg »). */
    private static void fiche(ServerPlayer p, ReseauStaff.Economie m, boolean ok, String cle, Object... args) {
        if (p == null || p.hasDisconnected()) return;
        StringBuilder b = new StringBuilder(cle);
        for (Object a : args) b.append('|').append(String.valueOf(a).replace('|', '/'));
        ReseauStaff.CANAL.send(PacketDistributor.PLAYER.with(() -> p),
                new ReseauStaff.SoldeEco(m.action(), m.cible(), m.option(), ok, b.toString()));
    }

    private static void refus(ServerPlayer p, String cle, Object... args) {
        if (p != null && !p.hasDisconnected()) PanelStaff.repondre(p, false, cle, args);
    }

    /**
     * Opération faite : journal du staff, salon Discord, réponse au Fondateur et fiche à jour. {@code apres} : ce qu'a la
     * cible maintenant (« ? » si le site ne l'a pas dit).
     */
    private static void fait(ServerPlayer p, String staff, ReseauStaff.Economie m, ActionStaff a, OperationEco op, String cible, boolean retirer,
                             long n, String motif, String apres) {
        String signe = retirer ? "-" : "+";
        String detail = signe + n + " " + uniteFr(op, m.option());
        JournalStaff.noter(staff, fr.heraliz.grades.Grade.FONDATEUR.id, a, cible, detail, motif);
        LienDiscord.evenement("💰 Panel staff : **" + staff + "** (Fondateur) : " + signe + nombre(n) + " " + uniteFr(op, m.option())
                + " pour **" + cible + "** (maintenant " + apres + ") — « " + motif + " »");
        if (p == null || p.hasDisconnected()) return;
        PanelStaff.repondre(p, true, retirer ? "eco_retire" : "eco_donne", nombre(n), unite(op, m.option()), cible, apres);
        fiche(p, m, true, "eco_solde", cible, apres, unite(op, m.option()));
    }

    // ------------------------------------------------------------------ demande
    private static void recevoir(ServerPlayer p, ReseauStaff.Economie m) {
        ActionStaff a = ActionStaff.parId(m.action());
        OperationEco op = a == null ? null : OperationEco.de(a);
        if (op == null) return;
        if (!PanelStaff.fondateur(p)) {
            refus(p, "fondateur");
            return;
        }
        int sens = m.sens();
        if (sens != OperationEco.VOIR && sens != OperationEco.DONNER && sens != OperationEco.RETIRER) return;
        String cible = m.cible() == null ? "" : m.cible().strip();
        if (!(op.clan ? TAG : PSEUDO).matcher(cible).matches()) {
            if (sens != OperationEco.VOIR) refus(p, op.clan ? "eco_cible_clan" : "eco_cible");
            return;
        }
        String option = a.options == null ? "" : m.option();
        if (a.options != null && !a.options.contains(option)) {
            if (sens != OperationEco.VOIR) refus(p, "eco_option");
            return;
        }
        UUID u = p.getUUID();
        long maintenant = System.currentTimeMillis();
        if (sens == OperationEco.VOIR) {
            Long d = DERNIER_VOIR.get(u);
            if (d != null && maintenant - d < ECART_VOIR_MS) return;
            DERNIER_VOIR.put(u, maintenant);
            if (op == OperationEco.PB) {
                // le site limite tout le serveur (une seule adresse) : une lecture de PB toutes les 3 s au plus
                Long dp = DERNIER_PB.get(u);
                if (dp != null && maintenant - dp < ECART_PB_VOIR_MS) {
                    fiche(p, m, false, "eco_attends");
                    return;
                }
                DERNIER_PB.put(u, maintenant);
            }
            try {
                voir(p, m, op, cible, option);
            } catch (RuntimeException | LinkageError ex) {
                LOG.error("[grades] onglet Économie : lecture « {} » de {} impossible", a.id, cible, ex);
                fiche(p, m, false, "erreur");
            }
            return;
        }
        Long d = DERNIERE.get(u);
        if (d != null && maintenant - d < ECART_MS) {
            refus(p, "trop_vite");
            return;
        }
        long n = m.montant();
        if (n < 1 || n > op.max) {
            refus(p, "eco_montant", nombre(op.max));
            return;
        }
        String motif = nettoyer(m.motif(), MOTIF_MAX);
        if (motif.isEmpty()) {
            refus(p, "eco_motif");
            return;
        }
        DERNIERE.put(u, maintenant);
        boolean retirer = sens == OperationEco.RETIRER;
        try {
            switch (op) {
                case HERALS -> herals(p, m, a, cible, n, retirer, motif);
                case CLES -> cles(p, m, a, cible, option, n, retirer, motif);
                case PB -> pb(p, m, a, cible, n, retirer, motif);
                case SAISON -> saison(p, m, a, cible, option, n, retirer, motif);
                case XP_CLAN -> xp(p, m, a, cible, n, retirer, motif);
            }
        } catch (RuntimeException | LinkageError ex) {
            LOG.error("[grades] onglet Économie : « {} » sur {} impossible", a.id, cible, ex);
            refus(p, "erreur");
        }
    }

    /** Ce qu'a la cible, pour la fiche (rien n'est changé). */
    private static void voir(ServerPlayer p, ReseauStaff.Economie m, OperationEco op, String cible, String option) {
        switch (op) {
            case HERALS -> {
                EconomieCommands.ResultatPanel r = EconomieCommands.soldePanel(cible);
                if (!r.code().equals("ok")) fiche(p, m, false, r.code().equals("introuvable") ? "eco_introuvable" : "eco_indisponible", cible);
                else fiche(p, m, true, "eco_solde", r.nom(), nombre(r.avant()), unite(op, option));
            }
            case CLES -> {
                Compte c = compte(p.server, cible);
                if (c == null) {
                    fiche(p, m, false, "eco_introuvable", cible);
                    return;
                }
                if (!ComptesCles.ouvert(c.uuid())) fiche(p, m, false, "eco_cles_ferme", c.nom());
                else fiche(p, m, true, "eco_solde", c.nom(), nombre(ComptesCles.solde(c.uuid(), ClesApi.caisse(option))), unite(op, option));
                if (!c.enLigne()) ComptesCles.oublier(c.uuid());
            }
            case PB -> {
                if (!Pb.actif()) {
                    fiche(p, m, false, "eco_pb_inactif");
                    return;
                }
                UUID staff = p.getUUID();
                MinecraftServer srv = p.server;
                Compte c = compte(srv, cible);
                String nom = c != null ? c.nom() : cible;
                Pb.solde(nom, r -> {
                    ServerPlayer s = srv.getPlayerList().getPlayer(staff);
                    if (r == null) fiche(s, m, false, "eco_pb_injoignable");
                    else if (r.code() == 200 && r.nombre("solde") >= 0) fiche(s, m, true, "eco_solde", nom, nombre(r.nombre("solde")), unite(op, option));
                    else if ("compte_inconnu".equals(r.raison())) fiche(s, m, false, "eco_pb_compte", nom);
                    else fiche(s, m, false, "eco_pb_refus", r.raison());
                });
            }
            case SAISON -> {
                PanelClans.Resultat r = PanelClans.voirPoints(cible, option);
                if (!r.code().equals("ok")) fiche(p, m, false, r.code().equals("introuvable") ? "eco_clan_introuvable" : "eco_indisponible", cible);
                else fiche(p, m, true, "eco_solde", "[" + r.tag() + "] " + r.nom(), nombre(r.avant()), unite(op, option));
            }
            case XP_CLAN -> {
                PanelClans.Resultat r = PanelClans.voirXp(cible);
                if (!r.code().equals("ok")) fiche(p, m, false, r.code().equals("introuvable") ? "eco_clan_introuvable" : "eco_indisponible", cible);
                else fiche(p, m, true, "eco_solde_xp", "[" + r.tag() + "] " + r.nom(), r.niveauAvant(), nombre(r.avant()),
                        nombre(fr.heralisz.clans.model.Niveaux.xpPour(r.niveauAvant())));
            }
        }
    }

    // ------------------------------------------------------------------ hérals
    private static void herals(ServerPlayer p, ReseauStaff.Economie m, ActionStaff a, String cible, long n, boolean retirer, String motif) {
        EconomieCommands.ResultatPanel r = EconomieCommands.panel(p, retirer, cible, n, motif);
        switch (r.code()) {
            case "ok" -> fait(p, p.getGameProfile().getName(), m, a, OperationEco.HERALS, r.nom(), retirer, n, motif, nombre(r.apres()));
            case "introuvable" -> refus(p, "eco_introuvable", cible);
            case "plafond" -> refus(p, "eco_plafond_herals", r.nom(), nombre(r.plafond()), nombre(r.avant()));
            case "insuffisant" -> refus(p, "eco_insuffisant", r.nom(), nombre(r.avant()), unite(OperationEco.HERALS, ""));
            case "indisponible" -> refus(p, "eco_indisponible");
            default -> refus(p, "fondateur");
        }
    }

    // ------------------------------------------------------------------ clés
    /** Compte visé : identifiant, pseudo, connecté ou non. */
    private record Compte(UUID uuid, String nom, boolean enLigne) {
    }

    /**
     * Joueur connecté (pseudo sans tenir compte des majuscules), sinon un joueur déjà venu (compte des hérals, créé à la
     * première connexion : lu en mémoire, jamais de recherche sur internet) ; null : inconnu.
     */
    private static Compte compte(MinecraftServer srv, String pseudo) {
        for (ServerPlayer x : srv.getPlayerList().getPlayers()) {
            if (x.getGameProfile().getName().equalsIgnoreCase(pseudo)) return new Compte(x.getUUID(), x.getGameProfile().getName(), true);
        }
        try {
            EconomieCommands.ResultatPanel r = EconomieCommands.soldePanel(pseudo);
            return r.uuid() == null ? null : new Compte(r.uuid(), r.nom(), false);
        } catch (LinkageError ex) {
            return null;
        }
    }

    private static void cles(ServerPlayer p, ReseauStaff.Economie m, ActionStaff a, String cible, String option, long n, boolean retirer, String motif) {
        CrateType t = ClesApi.caisse(option);
        Compte c = compte(p.server, cible);
        if (t == null) {
            refus(p, "eco_option");
            return;
        }
        if (c == null) {
            refus(p, "eco_introuvable", cible);
            return;
        }
        String staff = p.getGameProfile().getName();
        String source = "panel " + staff + " : " + motif;
        try {
            if (!ComptesCles.ouvert(c.uuid())) {
                refus(p, "eco_cles_ferme", c.nom());
                return;
            }
            int avant = ComptesCles.solde(c.uuid(), t);
            boolean ok;
            if (retirer) {
                if (avant < n) {
                    refus(p, "eco_insuffisant", c.nom(), nombre(avant), unite(OperationEco.CLES, option));
                    return;
                }
                ok = ComptesCles.consommer(p.server, c.uuid(), t, (int) n, source);
            } else {
                ok = ComptesCles.crediter(p.server, c.uuid(), c.enLigne() ? c.nom() : null, t, (int) n, source);
            }
            if (!ok) {
                refus(p, "eco_cles_ferme", c.nom());
                return;
            }
            ServerPlayer j = p.server.getPlayerList().getPlayer(c.uuid());
            if (j != null) {
                j.sendSystemMessage(Component.translatableWithFallback(retirer ? "message.heraliz_grades.staff.eco.cles_perdues"
                                : "message.heraliz_grades.staff.eco.cles_recues", retirer ? "Le staff t'a retiré %s × %s." : "Le staff t'a donné %s × %s.",
                        nombre(n), t.keyName()).withStyle(retirer ? ChatFormatting.GOLD : ChatFormatting.GREEN));
            }
            fait(p, staff, m, a, OperationEco.CLES, c.nom(), retirer, n, motif, nombre(ComptesCles.solde(c.uuid(), t)));
        } finally {
            // compte d'un joueur absent : pas gardé en mémoire (son fichier est écrit à chaque changement)
            if (!c.enLigne()) ComptesCles.oublier(c.uuid());
        }
    }

    // ------------------------------------------------------------------ Points Boutique
    /**
     * PB par le site, comme la boutique et la console ({@link Pb} : requête signée, référence unique, faite une seule fois
     * même renvoyée). Site muet : on lui demande si l'opération a été faite avant de répondre ; toujours muet : « rien n'est
     * sûr, regarde le solde avant de recommencer ».
     */
    private static void pb(ServerPlayer p, ReseauStaff.Economie m, ActionStaff a, String saisi, long n, boolean retirer, String motif) {
        if (!Pb.actif()) {
            refus(p, "eco_pb_inactif");
            return;
        }
        // compte du site au pseudo exact du joueur s'il est déjà venu (majuscules comprises), sinon tel qu'il a été écrit
        Compte c = compte(p.server, saisi);
        String cible = c != null ? c.nom() : saisi;
        UUID staffId = p.getUUID();
        if (!PB_EN_ROUTE.add(staffId)) {
            refus(p, "eco_pb_attente");
            return;
        }
        String staff = p.getGameProfile().getName();
        MinecraftServer srv = p.server;
        String ref = Pb.ref(retirer ? "panel-ret" : "panel-don");
        String motifSite = "panel " + staff + " : " + motif;
        java.util.function.Consumer<Pb.Reponse> fin = r -> {
            ServerPlayer s = srv.getPlayerList().getPlayer(staffId);
            if (r == null) {
                // site muet : l'opération a-t-elle été faite ? (référence unique : jamais faite deux fois)
                Pb.statut(ref, st -> {
                    PB_EN_ROUTE.remove(staffId);
                    ServerPlayer s2 = srv.getPlayerList().getPlayer(staffId);
                    if (st != null && st.code() == 200 && st.corps().has("fait") && st.corps().get("fait").getAsBoolean()) {
                        pbFait(srv, s2, staff, m, a, cible, retirer, n, motif, ref, "?");
                    } else if (st != null && st.code() == 200) {
                        refus(s2, "eco_pb_injoignable");
                    } else {
                        LOG.warn("[grades] onglet Économie : PB {} {} pour {} : site muet, résultat inconnu ({})", retirer ? "-" : "+", n, cible, ref);
                        refus(s2, "eco_pb_incertain");
                    }
                });
                return;
            }
            PB_EN_ROUTE.remove(staffId);
            if (r.ok()) {
                long solde = r.nombre("solde");
                pbFait(srv, s, staff, m, a, cible, retirer, n, motif, ref, solde >= 0 ? nombre(solde) : "?");
                return;
            }
            switch (r.raison()) {
                case "plafond" -> {
                    if (r.nombre("max_jour") >= 0) refus(s, "eco_pb_plafond_jour", nombre(Math.max(0, r.nombre("jour"))), nombre(r.nombre("max_jour")));
                    else refus(s, "eco_pb_plafond", nombre(Math.max(0, r.nombre("max"))));
                }
                case "compte_inconnu" -> refus(s, "eco_pb_compte", cible);
                case "solde_insuffisant" -> refus(s, "eco_insuffisant", cible, nombre(Math.max(0, r.nombre("solde"))), unite(OperationEco.PB, ""));
                default -> refus(s, "eco_pb_refus", r.raison().isEmpty() ? String.valueOf(r.code()) : r.raison());
            }
        };
        if (retirer) Pb.debiter(ref, cible, n, motifSite, fin);
        else Pb.crediter(ref, cible, n, motifSite, fin);
    }

    private static void pbFait(MinecraftServer srv, ServerPlayer s, String staff, ReseauStaff.Economie m, ActionStaff a, String cible, boolean retirer,
                               long n, String motif, String ref, String apres) {
        BoutiqueServeur.journal("panel " + staff + " " + (retirer ? "retirer " : "donner ") + cible + " " + n + " PB (" + ref + ") : " + motif);
        ServerPlayer j = srv.getPlayerList().getPlayerByName(cible);
        if (j != null) {
            j.sendSystemMessage(Component.translatableWithFallback(retirer ? "message.heraliz_grades.staff.eco.pb_perdus" : "message.heraliz_grades.pb.recu",
                    retirer ? "Le staff t'a retiré %s PB." : "Tu as reçu %s PB.", nombre(n)).withStyle(ChatFormatting.GOLD));
        }
        fait(s, staff, m, a, OperationEco.PB, cible, retirer, n, motif, apres);
    }

    // ------------------------------------------------------------------ clans
    private static void saison(ServerPlayer p, ReseauStaff.Economie m, ActionStaff a, String tag, String categorie, long n, boolean retirer,
                               String motif) {
        String staff = p.getGameProfile().getName();
        PanelClans.Resultat r = PanelClans.points(tag, categorie, retirer ? -n : n, motif, staff);
        clan(p, staff, m, a, OperationEco.SAISON, tag, categorie, n, retirer, motif, r, nombre(r.apres()));
    }

    private static void xp(ServerPlayer p, ReseauStaff.Economie m, ActionStaff a, String tag, long n, boolean retirer, String motif) {
        String staff = p.getGameProfile().getName();
        PanelClans.Resultat r = PanelClans.xp(tag, retirer ? -n : n, motif, staff);
        clan(p, staff, m, a, OperationEco.XP_CLAN, tag, "", n, retirer, motif, r, nombre(r.apres()));
        if (r.code().equals("ok") && p != null && !p.hasDisconnected()) {
            // la fiche de l'XP montre aussi le niveau
            fiche(p, m, true, "eco_solde_xp", "[" + r.tag() + "] " + r.nom(), r.niveauApres(), nombre(r.apres()),
                    nombre(fr.heralisz.clans.model.Niveaux.xpPour(r.niveauApres())));
        }
    }

    private static void clan(ServerPlayer p, String staff, ReseauStaff.Economie m, ActionStaff a, OperationEco op, String tag, String option, long n,
                             boolean retirer, String motif, PanelClans.Resultat r, String apres) {
        switch (r.code()) {
            case "ok" -> fait(p, staff, m, a, op, "[" + r.tag() + "]", retirer, n, motif, apres);
            case "introuvable" -> refus(p, "eco_clan_introuvable", tag);
            case "insuffisant" -> refus(p, "eco_insuffisant", "[" + r.tag() + "]", nombre(r.avant()), unite(op, option));
            case "categorie" -> refus(p, "eco_option");
            case "montant" -> refus(p, "eco_montant", nombre(op.max));
            default -> refus(p, "eco_indisponible");
        }
    }
}
