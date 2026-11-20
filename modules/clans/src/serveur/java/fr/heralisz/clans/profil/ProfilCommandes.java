package fr.heralisz.clans.profil;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.clans.classement.KillsJoueurs;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.LienJoueur;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.saison.Saison;
import fr.heralisz.clans.saison.SaisonApi;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.economie.api.Compte;
import fr.heralisz.economie.api.EconomieApi;
import fr.heralisz.economie.server.ComptesStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /profil [pseudo]} et {@code /stats [pseudo]} (19/11, demande du propriétaire ; maquettes validées) : l'écran du
 * profil, le sien ou celui d'un autre joueur, connecté ou non. Permises partout, même sur la carte et en combat (listes du
 * noyau) : elles ne font que lire. Le profil d'un autre joueur ne dit jamais son argent, ce qu'il a sur lui ni où il est.
 * Une demande par seconde au plus par joueur ; les suggestions ne proposent que les joueurs en ligne visibles (jamais le
 * staff caché). Pseudo : joueur en ligne, sinon dernier pseudo connu (profils, comptes de hérals, clans), jamais une
 * demande à Mojang. Fil serveur.
 */
public final class ProfilCommandes {
    private ProfilCommandes() {
    }

    private static final long ECART_MS = 1000;
    private static final Map<UUID, Long> DERNIER = new HashMap<>();
    /** Couleurs des refus (bandeau au-dessus de la barre, trait rouge ou ambre). */
    private static final int ROUGE = 0xFF6B6B, AMBRE = 0xE8B34A;

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        enregistrer(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        DERNIER.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        DERNIER.clear();
    }

    /** Joueurs en ligne, sans le staff caché. */
    private static final SuggestionProvider<CommandSourceStack> EN_LIGNE = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            if (!PontsProfil.cache(p.getUUID())) l.add(p.getGameProfile().getName());
        }
        return SharedSuggestionProvider.suggest(l, b);
    };

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(arbre(Commands.literal("profil")));
        d.register(arbre(Commands.literal("stats")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> arbre(LiteralArgumentBuilder<CommandSourceStack> racine) {
        return racine.executes(c -> ouvrir(c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(EN_LIGNE)
                        .executes(c -> ouvrir(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "joueur"))));
    }

    private static int ouvrir(ServerPlayer p, @Nullable String nom) throws CommandSyntaxException {
        long now = System.currentTimeMillis();
        Long avant = DERNIER.get(p.getUUID());
        if (avant != null && now - avant < ECART_MS) {
            refus(p, Component.translatableWithFallback("msg.profil.attends", "Attends une seconde avant d'ouvrir un autre profil"), AMBRE);
            return 0;
        }
        DERNIER.put(p.getUUID(), now);
        UUID cible = nom == null ? p.getUUID() : trouver(p.server, nom);
        if (cible == null) {
            refus(p, Component.translatableWithFallback("msg.profil.inconnu", "Aucun joueur nommé « %s »", nom), ROUGE);
            return 0;
        }
        ClansNet.toPlayer(p, new Packets.ProfilS2C(vue(p, cible, nom, now)));
        return 1;
    }

    /** Refus en message court au-dessus de la barre d'objets (bandeau du noyau), comme les autres refus du serveur. */
    private static void refus(ServerPlayer p, Component texte, int couleur) {
        p.displayClientMessage(texte.copy().withStyle(s -> s.withColor(couleur)), true);
    }

    /** Joueur par son pseudo : en ligne, sinon dernier pseudo connu (profils, comptes de hérals, clans) ; null : inconnu. */
    @Nullable
    private static UUID trouver(MinecraftServer srv, String nom) {
        ServerPlayer p = srv.getPlayerList().getPlayerByName(nom);
        if (p != null) return p.getUUID();
        UUID u = Profils.parNom(nom);
        if (u != null) return u;
        ComptesStore cs = ComptesStore.get();
        u = cs == null ? null : cs.resoudre(nom);
        if (u != null) return u;
        ClanStore s = ClanStore.get();
        if (s != null) {
            for (LienJoueur l : s.liens()) if (nom.equalsIgnoreCase(l.dernierNom)) return l.joueur;
        }
        return null;
    }

    // ------------------------------------------------------------------ la vue
    /** Ce que montre l'écran : tout pour soi ; pour un autre joueur, ni argent, ni ce qu'il a sur lui, ni l'endroit. */
    static ProfilVue vue(ServerPlayer demandeur, UUID cible, @Nullable String nomTape, long now) {
        boolean moi = demandeur.getUUID().equals(cible);
        ServerPlayer enLigne = demandeur.server.getPlayerList().getPlayer(cible);
        Profils.Fiche f = Profils.get(cible);
        String pseudo = ProfilVue.nom(enLigne != null ? enLigne.getGameProfile().getName() : nomConnu(cible, f, nomTape));

        ProfilVue.Noms grade = ProfilVue.Noms.VIDE;
        int couleur = 0xA0A6B0;
        String[] g = PontsProfil.grade(cible);
        // grade de base (Survivant) : rien à montrer
        if (g != null && !"BASE".equals(g[5])) {
            grade = new ProfilVue.Noms(ProfilVue.nom(g[1]), ProfilVue.nom(g[2]), ProfilVue.nom(g[3]));
            try {
                couleur = Integer.parseInt(g[4], 16) & 0xFFFFFF;
            } catch (NumberFormatException ignore) {
                // couleur par défaut
            }
        }

        ProfilVue.Compteurs c = new ProfilVue.Compteurs(KillsJoueurs.kills(cible), f == null ? 0 : f.morts, f == null ? 0 : f.mortsJoueur,
                f == null ? 0 : f.zombies, f == null ? 0 : f.extractions, f == null ? 0 : f.conteneurs, f == null ? 0 : f.serieMax,
                Profils.vieMax(cible, now));

        int quetes = -1;
        ProfilVue.Noms titre = ProfilVue.Noms.VIDE;
        String[] q = PontsProfil.quetes(cible);
        if (q != null) {
            try {
                quetes = Math.max(0, Integer.parseInt(q[0]));
            } catch (NumberFormatException ignore) {
                quetes = -1;
            }
            titre = new ProfilVue.Noms(ProfilVue.nom(q[1]), ProfilVue.nom(q[2]), ProfilVue.nom(q[3]));
        }

        int comptes = 0;
        int rangHerals = 0;
        if (moi) {
            ComptesStore cs = ComptesStore.get();
            if (cs != null) {
                comptes = cs.tous().size();
                rangHerals = rangHerals(cs, EconomieApi.solde(cible));
            }
        }
        ProfilVue.Rangs rangs = new ProfilVue.Rangs(KillsJoueurs.rang(cible), Profils.rangZombies(cible), rangHerals,
                Math.max(Profils.taille(), KillsJoueurs.taille()), comptes);

        ProfilVue.SurLui surLui = null;
        if (moi) {
            long[] b = PontsProfil.bourses(demandeur);
            surLui = new ProfilVue.SurLui(b == null ? -1 : b[0], b == null ? 0 : (int) b[1], PontsProfil.capsulesCompte(cible),
                    PontsProfil.capsulesSurLui(demandeur));
        }
        return new ProfilVue(cible, pseudo, moi, grade, couleur, f == null ? 0 : f.depuis, Profils.tempsJeu(cible, now),
                moi ? EconomieApi.solde(cible) : -1, c, quetes, titre, clan(cible), rangs, surLui);
    }

    /** Pseudo d'un joueur hors ligne : celui du profil, sinon du compte de hérals, sinon celui tapé. */
    private static String nomConnu(UUID cible, @Nullable Profils.Fiche f, @Nullable String nomTape) {
        if (f != null && !f.nom.isEmpty()) return f.nom;
        ComptesStore cs = ComptesStore.get();
        Compte compte = cs == null ? null : cs.get(cible);
        if (compte != null && compte.dernierNom() != null && !compte.dernierNom().isEmpty()) return compte.dernierNom();
        return nomTape == null ? "?" : nomTape;
    }

    /** Place en hérals (1 + les comptes plus riches) ; 0 sans hérals. */
    private static int rangHerals(ComptesStore cs, long solde) {
        if (solde <= 0) return 0;
        int devant = 0;
        for (Compte x : cs.tous()) if (x.solde() > solde) devant++;
        return devant + 1;
    }

    /** Clan du joueur et sa saison ; null sans clan. */
    @Nullable
    private static ProfilVue.ClanVue clan(UUID joueur) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(joueur);
        Membre m = c == null ? null : c.membre(joueur);
        if (m == null) return null;
        int[] rangs = SaisonApi.rangs(c.id);
        String tag = c.tag == null ? "" : c.tag.length() > 16 ? c.tag.substring(0, 16) : c.tag;
        return new ProfilVue.ClanVue(ProfilVue.nom(c.nom), tag, m.grade, m.rejointLe, Saison.numero(), Saison.active(),
                rangs.length > 3 ? rangs[3] : 0, SaisonApi.points(c.id), c.banniere == null ? Banniere.DEFAUT : c.banniere);
    }
}
