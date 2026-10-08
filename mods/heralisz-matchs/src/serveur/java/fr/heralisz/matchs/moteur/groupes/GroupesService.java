package fr.heralisz.matchs.moteur.groupes;

import fr.heralisz.matchs.moteur.Noms;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groupes de 1 a 4 joueurs independants des clans (MATCHS_SPEC §1.6) : invitations de 60 s, acceptation, refus,
 * depart, exclusion, transfert du chef ; toast d'invitation et ecran leger cote client (etat pousse par
 * {@code GroupeS2C} a chaque changement). Etat en memoire (un groupe ne survit pas a un redemarrage). Toutes les
 * regles sont verifiees ici (l'ecran ne fait qu'envoyer des demandes). Fil serveur.
 */
public final class GroupesService {
    private GroupesService() {
    }

    public static final int ACTION_OUVRIR = Groupes.ACTION_OUVRIR, ACTION_INVITER = Groupes.ACTION_INVITER,
            ACTION_ACCEPTER = Groupes.ACTION_ACCEPTER, ACTION_REFUSER = Groupes.ACTION_REFUSER, ACTION_QUITTER = Groupes.ACTION_QUITTER,
            ACTION_EXCLURE = Groupes.ACTION_EXCLURE, ACTION_CHEF = Groupes.ACTION_CHEF;

    /** Invitation en attente : de (chef ou joueur seul), pour, expiration (ms). */
    private record Invitation(UUID de, UUID pour, long expireMs) {
    }

    private static final Map<UUID, List<Invitation>> INVITATIONS = new HashMap<>();
    private static final Map<UUID, Long> DERNIERE_ACTION = new HashMap<>();
    private static final int ACCENT = 0x6FA8FF;

    // ------------------------------------------------------------------ actions
    /** {@code /groupe inviter <joueur>} : le joueur doit etre chef de son groupe (ou seul), groupe non plein. */
    public static void inviter(ServerPlayer de, ServerPlayer pour) {
        MinecraftServer s = de.server;
        if (de == pour) {
            erreur(de, "matchs.groupe.erreur.soi");
            return;
        }
        Groupe g = Groupes.de(de.getUUID());
        if (g != null && !g.estChef(de.getUUID())) {
            erreur(de, "matchs.groupe.erreur.pas_chef");
            return;
        }
        if (g != null && g.plein()) {
            erreur(de, "matchs.groupe.erreur.plein");
            return;
        }
        if (g != null && g.contient(pour.getUUID())) {
            erreur(de, "matchs.groupe.erreur.deja_membre");
            return;
        }
        if (Groupes.de(pour.getUUID()) != null) {
            erreur(de, "matchs.groupe.erreur.deja_groupe", pour.getGameProfile().getName());
            return;
        }
        List<Invitation> l = INVITATIONS.computeIfAbsent(pour.getUUID(), k -> new ArrayList<>());
        l.removeIf(i -> i.de.equals(de.getUUID()));
        l.add(new Invitation(de.getUUID(), pour.getUUID(), System.currentTimeMillis() + Groupes.INVITATION_MS));
        int sec = (int) (Groupes.INVITATION_MS / 1000);
        MatchsNet.versJoueur(pour, new PaquetsMoteur.InvitationS2C(de.getGameProfile().getName(), sec));
        Annonces.chat(pour, Component.translatable("matchs.groupe.invite_par", de.getGameProfile().getName())
                .append(Component.literal("  "))
                .append(lien("matchs.groupe.lien.accepter", "/groupe accepter " + de.getGameProfile().getName(), ACCENT))
                .append(Component.literal("  "))
                .append(lien("matchs.groupe.lien.refuser", "/groupe refuser " + de.getGameProfile().getName(), 0x8F95A0)));
        Annonces.chat(de, Component.translatable("matchs.groupe.invitation_envoyee", pour.getGameProfile().getName()));
        envoyer(pour, false);
        envoyer(de, false);
        if (s != null) etatDuGroupe(s, g);
    }

    /** {@code /groupe accepter [joueur]} : la plus recente invitation valide (ou celle de ce joueur). */
    public static void accepter(ServerPlayer p, @Nullable String deNom) {
        MinecraftServer s = p.server;
        Invitation inv = invitation(s, p.getUUID(), deNom);
        if (inv == null) {
            erreur(p, "matchs.groupe.erreur.aucune_invitation");
            return;
        }
        if (Groupes.de(p.getUUID()) != null) {
            erreur(p, "matchs.groupe.erreur.quitter_avant");
            return;
        }
        Groupe g = Groupes.de(inv.de);
        if (g != null && !g.estChef(inv.de)) {
            // l'invitant n'est plus chef : invitation caduque
            retirerInvitation(inv);
            erreur(p, "matchs.groupe.erreur.invitation_caduque");
            return;
        }
        if (g != null && g.plein()) {
            erreur(p, "matchs.groupe.erreur.plein");
            return;
        }
        Groupe ancien = g != null ? g : Groupe.solo(inv.de);
        List<UUID> m = new ArrayList<>(ancien.membres());
        m.add(p.getUUID());
        Groupe nouveau = new Groupe(g != null ? g.id() : UUID.randomUUID(), inv.de, m);
        Groupes.definir(g, nouveau);
        INVITATIONS.remove(p.getUUID());
        diffuserMessage(s, nouveau, Component.translatable("matchs.groupe.rejoint", p.getGameProfile().getName()));
        etatDuGroupe(s, nouveau);
    }

    /** {@code /groupe refuser [joueur]}. */
    public static void refuser(ServerPlayer p, @Nullable String deNom) {
        Invitation inv = invitation(p.server, p.getUUID(), deNom);
        if (inv == null) {
            erreur(p, "matchs.groupe.erreur.aucune_invitation");
            return;
        }
        retirerInvitation(inv);
        ServerPlayer de = p.server.getPlayerList().getPlayer(inv.de);
        if (de != null) Annonces.chat(de, Component.translatable("matchs.groupe.refusee", p.getGameProfile().getName()));
        envoyer(p, false);
    }

    /** {@code /groupe quitter} : un chef qui part passe la main au membre suivant ; un groupe d'un seul est dissous. */
    public static void quitter(ServerPlayer p) {
        Groupe g = Groupes.de(p.getUUID());
        if (g == null) {
            erreur(p, "matchs.groupe.erreur.aucun_groupe");
            return;
        }
        retirerMembre(p.server, g, p.getUUID(), "matchs.groupe.parti");
        envoyer(p, false);
    }

    /** {@code /groupe exclure <joueur>} (chef). */
    public static void exclure(ServerPlayer chef, String nom) {
        Groupe g = Groupes.de(chef.getUUID());
        if (g == null || !g.estChef(chef.getUUID())) {
            erreur(chef, "matchs.groupe.erreur.pas_chef");
            return;
        }
        UUID cible = membreParNom(chef.server, g, nom);
        if (cible == null || cible.equals(chef.getUUID())) {
            erreur(chef, "matchs.groupe.erreur.inconnu", nom);
            return;
        }
        retirerMembre(chef.server, g, cible, "matchs.groupe.exclu");
        ServerPlayer c = chef.server.getPlayerList().getPlayer(cible);
        if (c != null) {
            Annonces.chat(c, Component.translatable("matchs.groupe.vous_exclu"));
            envoyer(c, false);
        }
    }

    /** {@code /groupe chef <joueur>} (chef) : transfert. */
    public static void chef(ServerPlayer chef, String nom) {
        Groupe g = Groupes.de(chef.getUUID());
        if (g == null || !g.estChef(chef.getUUID())) {
            erreur(chef, "matchs.groupe.erreur.pas_chef");
            return;
        }
        UUID cible = membreParNom(chef.server, g, nom);
        if (cible == null || cible.equals(chef.getUUID())) {
            erreur(chef, "matchs.groupe.erreur.inconnu", nom);
            return;
        }
        List<UUID> m = new ArrayList<>(g.membres());
        m.remove(cible);
        m.add(0, cible);
        Groupe nouveau = new Groupe(g.id(), cible, m);
        Groupes.definir(g, nouveau);
        diffuserMessage(chef.server, nouveau, Component.translatable("matchs.groupe.nouveau_chef", Noms.nom(chef.server, cible)));
        etatDuGroupe(chef.server, nouveau);
    }

    /** Action venue de l'ecran (meme regles que les commandes ; cadence bornee). */
    public static void actionEcran(ServerPlayer p, int action, String cible) {
        long now = System.currentTimeMillis();
        Long d = DERNIERE_ACTION.get(p.getUUID());
        if (d != null && now - d < 250) return;
        DERNIERE_ACTION.put(p.getUUID(), now);
        String c = cible == null ? "" : cible.trim();
        switch (action) {
            case ACTION_OUVRIR -> envoyer(p, true);
            case ACTION_INVITER -> {
                ServerPlayer pour = c.isEmpty() ? null : p.server.getPlayerList().getPlayerByName(c);
                if (pour == null) erreur(p, "matchs.groupe.erreur.hors_ligne", c);
                else inviter(p, pour);
            }
            case ACTION_ACCEPTER -> accepter(p, c.isEmpty() ? null : c);
            case ACTION_REFUSER -> refuser(p, c.isEmpty() ? null : c);
            case ACTION_QUITTER -> quitter(p);
            case ACTION_EXCLURE -> exclure(p, c);
            case ACTION_CHEF -> chef(p, c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ interne
    private static void retirerMembre(MinecraftServer s, Groupe g, UUID u, String cleMessage) {
        List<UUID> m = new ArrayList<>(g.membres());
        m.remove(u);
        Groupe nouveau = m.isEmpty() ? null : new Groupe(g.id(), g.estChef(u) ? m.get(0) : g.chef(), m);
        Groupes.definir(g, nouveau);
        if (nouveau != null) {
            diffuserMessage(s, nouveau, Component.translatable(cleMessage, Noms.nom(s, u)));
            if (g.estChef(u) && nouveau.taille() > 1) {
                diffuserMessage(s, nouveau, Component.translatable("matchs.groupe.nouveau_chef", Noms.nom(s, nouveau.chef())));
            }
            etatDuGroupe(s, nouveau);
        }
    }

    @Nullable
    private static UUID membreParNom(MinecraftServer s, Groupe g, String nom) {
        for (UUID u : g.membres()) if (Noms.nom(s, u).equalsIgnoreCase(nom)) return u;
        return null;
    }

    @Nullable
    private static Invitation invitation(MinecraftServer s, UUID pour, @Nullable String deNom) {
        List<Invitation> l = INVITATIONS.get(pour);
        if (l == null) return null;
        long now = System.currentTimeMillis();
        l.removeIf(i -> i.expireMs <= now);
        for (int i = l.size() - 1; i >= 0; i--) {
            Invitation inv = l.get(i);
            if (deNom == null || Noms.nom(s, inv.de).equalsIgnoreCase(deNom)) return inv;
        }
        return null;
    }

    private static void retirerInvitation(Invitation inv) {
        List<Invitation> l = INVITATIONS.get(inv.pour);
        if (l != null) {
            l.remove(inv);
            if (l.isEmpty()) INVITATIONS.remove(inv.pour);
        }
    }

    private static MutableComponent lien(String cle, String commande, int couleur) {
        return Component.literal("[").append(Component.translatable(cle)).append("]")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(couleur)).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commande))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(commande))));
    }

    private static void erreur(ServerPlayer p, String cle, Object... args) {
        Annonces.chat(p, Component.translatable(cle, args).withStyle(ChatFormatting.GRAY));
    }

    private static void diffuserMessage(MinecraftServer s, Groupe g, Component msg) {
        for (ServerPlayer p : g.enLigne(s)) Annonces.chat(p, msg);
    }

    /** Pousse l'etat a tous les membres connectes du groupe (sans ouvrir l'ecran). */
    private static void etatDuGroupe(MinecraftServer s, @Nullable Groupe g) {
        if (g == null) return;
        for (ServerPlayer p : g.enLigne(s)) envoyer(p, false);
    }

    /** Envoie au joueur l'etat de son groupe et de ses invitations ({@code ouvrir} = ouvrir l'ecran). */
    public static void envoyer(ServerPlayer p, boolean ouvrir) {
        MinecraftServer s = p.server;
        Groupe g = Groupes.de(p.getUUID());
        List<PaquetsMoteur.MembreVue> membres = new ArrayList<>();
        if (g != null) {
            for (UUID u : g.membres()) {
                membres.add(new PaquetsMoteur.MembreVue(Noms.nom(s, u), g.estChef(u), s.getPlayerList().getPlayer(u) != null));
            }
        }
        List<PaquetsMoteur.InvitVue> invs = new ArrayList<>();
        List<Invitation> l = INVITATIONS.get(p.getUUID());
        long now = System.currentTimeMillis();
        if (l != null) {
            for (Invitation i : l) {
                if (i.expireMs > now) invs.add(new PaquetsMoteur.InvitVue(Noms.nom(s, i.de), (int) ((i.expireMs - now) / 1000)));
            }
        }
        MatchsNet.versJoueur(p, new PaquetsMoteur.GroupeS2C(ouvrir, membres, g == null || g.estChef(p.getUUID()), invs));
    }

    /** Expire les invitations (appel periodique). */
    public static void tick() {
        if (INVITATIONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        INVITATIONS.values().forEach(l -> l.removeIf(i -> i.expireMs <= now));
        INVITATIONS.values().removeIf(List::isEmpty);
    }

    /** Connexion / deconnexion : les autres membres voient le changement d'etat (en ligne). */
    public static void presence(ServerPlayer p, boolean connecte) {
        Groupe g = Groupes.de(p.getUUID());
        if (g == null) return;
        MinecraftServer s = p.server;
        for (ServerPlayer m : g.enLigne(s)) {
            if (!connecte && m == p) continue;
            envoyer(m, false);
        }
    }

    /** Banc d'essai : invitation factice recue de {@code de} (UUID d'un joueur factice nomme dans {@link Noms#FACTICES}). */
    public static void inviterFactice(ServerPlayer pour, UUID de) {
        List<Invitation> l = INVITATIONS.computeIfAbsent(pour.getUUID(), k -> new ArrayList<>());
        l.add(new Invitation(de, pour.getUUID(), System.currentTimeMillis() + Groupes.INVITATION_MS));
        MatchsNet.versJoueur(pour, new PaquetsMoteur.InvitationS2C(Noms.nom(pour.server, de), (int) (Groupes.INVITATION_MS / 1000)));
    }

    public static void oublier(UUID u) {
        DERNIERE_ACTION.remove(u);
    }

    public static void vider() {
        INVITATIONS.clear();
        DERNIERE_ACTION.clear();
    }
}
