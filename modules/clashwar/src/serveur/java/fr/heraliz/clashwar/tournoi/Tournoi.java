package fr.heraliz.clashwar.tournoi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.tournoi.bloc.MurTableau;
import fr.heraliz.clashwar.tournoi.net.CwPaquets;
import fr.heraliz.clashwar.tournoi.net.VueTableau;
import fr.heraliz.clashwar.moteur.api.Annonces;
import fr.heraliz.clashwar.moteur.api.Arenes;
import fr.heraliz.clashwar.moteur.api.Carte;
import fr.heraliz.clashwar.moteur.api.Coffre;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.clashwar.moteur.api.Equipement;
import fr.heraliz.clashwar.moteur.api.Hud;
import fr.heraliz.clashwar.moteur.api.Instance;
import fr.heraliz.clashwar.moteur.api.MatchApi;
import fr.heraliz.clashwar.moteur.Arrivees;
import fr.heraliz.clashwar.moteur.coffre.Coffres;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Une edition du ClashWar (MATCHS_SPEC §2.2 a §2.4) : inscriptions par clan, cloture (Coffre + salle d'attente),
 * tableau, tours joues en parallele (une {@link MatchCw} par rencontre), finale, sacre, recompenses, retour de tout le
 * monde. Fil serveur ; pilote par {@link Tournois#tick}.
 * <p>Regles du spawn (proprietaire, 15/11) : une equipe ne s'inscrit que si tous ses membres connectes sont dans le monde
 * du spawn (sinon refus qui les nomme) ; pendant les inscriptions, un inscrit qui quitte le spawn retire son equipe (toute
 * l'equipe est prevenue, elle peut se reinscrire depuis le spawn) ; au depart vers la salle, une equipe dont un joueur
 * connecte n'est pas au spawn est retiree du tournoi (personne n'est depose ni teleporte). Deconnexion (proprietaire, 15/11) :
 * un inscrit qui se deconnecte pendant les inscriptions ou avant l'entree dans la salle retire son equipe au moment meme
 * ({@link #deconnecte}) ; ses coequipiers sont prevenus (ils peuvent la reinscrire depuis le spawn tant que c'est ouvert).
 * Seuls des membres connectes s'inscrivent ; une equipe ou il manque quelqu'un au depart ne part pas.
 */
public final class Tournoi {
    public enum Etat { INSCRIPTION, PREPARATION, PAUSE, TOUR, FIN, TERMINE, ANNULE }

    /** Inscription d'un clan : ses equipes (UUID des joueurs). */
    static final class Inscription {
        final UUID clan;
        final List<List<UUID>> equipes = new ArrayList<>();

        Inscription(UUID clan) {
            this.clan = clan;
        }

        int nbJoueurs() {
            int n = 0;
            for (List<UUID> e : equipes) n += e.size();
            return n;
        }

        boolean contient(UUID u) {
            for (List<UUID> e : equipes) if (e.contains(u)) return true;
            return false;
        }
    }

    private final MinecraftServer serveur;
    final int edition;
    final Format format;
    final boolean simulation;
    Etat etat = Etat.INSCRIPTION;
    long finInscriptionMs;
    private int ticksEtat;
    private boolean rappel5, rappel1;
    final Map<UUID, Inscription> inscriptions = new LinkedHashMap<>();
    /** Equipes retenues a la cloture (avant le passage au Coffre). */
    private final List<Tableau.EquipeT> provisoires = new ArrayList<>();
    @Nullable
    Tableau tableau;
    @Nullable
    PartieSalle salle;
    @Nullable
    private Instance instanceSalle;
    final List<MatchCw> matchs = new ArrayList<>();
    /** Joueurs passes par le Coffre (rendus a la fin ou a /clashwar quitter). */
    final Set<UUID> participants = new LinkedHashSet<>();
    /**
     * Banc d'essai (client de dev, {@code /clashwar banc factices}) : equipes factices ajoutees a la cloture d'une vraie
     * edition, et leurs joueurs « mannequins » (UUID jamais connectes : pas de Coffre, jouent seulement face a un vrai
     * joueur, voir {@link MatchCw}). Vides en production.
     */
    final List<Tableau.EquipeT> facticesBanc = new ArrayList<>();
    final Set<UUID> mannequins = new LinkedHashSet<>();
    private int tour = -1;
    /** Podium pose dans la salle pendant la ceremonie de fin, ou null. */
    @Nullable
    private PodiumSalle podium;
    final Random rnd = new Random();
    private final Map<UUID, Long> cadence = new HashMap<>();
    /** Le monde du spawn : on ne s'inscrit, on ne reste inscrit et on ne part vers la salle que depuis lui (15/11). */
    private static final ResourceLocation MONDE_SPAWN = new ResourceLocation("heralisz", "spawn");
    /** Separateur des arguments d'un message de l'ecran d'inscription (« cle␟argument », traduit chez le joueur). */
    static final char SEP = '\u001F';

    Tournoi(MinecraftServer serveur, int edition, Format format, long finInscriptionMs, boolean simulation) {
        this.serveur = serveur;
        this.edition = edition;
        this.format = format;
        this.finInscriptionMs = finInscriptionMs;
        this.simulation = simulation;
    }

    public MinecraftServer serveur() {
        return serveur;
    }

    /** Vrai tant que l'edition n'est ni terminee ni annulee. */
    public boolean actif() {
        return etat != Etat.TERMINE && etat != Etat.ANNULE;
    }

    /** Vrai pendant le jeu (salle ouverte). */
    boolean enJeu() {
        return etat == Etat.PAUSE || etat == Etat.TOUR || etat == Etat.FIN;
    }

    public boolean participant(UUID u) {
        return participants.contains(u);
    }

    @Nullable
    public Tableau tableau() {
        return tableau;
    }

    // ================================================================== tick
    void tick() {
        ticksEtat++;
        CwConfig cfg = CwConfig.get();
        // 19/11 : salle d'attente gardee chargee pendant le jeu (retours des matchs sans attente)
        if (enJeu() && !simulation && ticksEtat % 200 == 0) tenirSalle();
        switch (etat) {
            case INSCRIPTION -> {
                if (simulation) return;
                // 15/11 : une equipe dont un inscrit a quitte le spawn est retiree (une fois par seconde, inscrits seulement)
                if (ticksEtat % 20 == 0) surveillerSpawn();
                // arenes du premier tour posees d'avance pendant les inscriptions (toutes les 10 s, en tache de fond)
                if (ticksEtat % 200 == 100) preparerArenes(equipesInscrites());
                long reste = finInscriptionMs - System.currentTimeMillis();
                if (!rappel5 && reste <= 5 * 60_000L && reste > 60_000L) {
                    rappel5 = true;
                    rappel(5);
                }
                if (!rappel1 && reste <= 60_000L && reste > 0) {
                    rappel1 = true;
                    rappel(1);
                }
                if (reste <= 0) cloturer();
            }
            case PREPARATION -> {
                if (ticksEtat > cfg.attenteArene * 20) annuler("salle_indisponible");
            }
            case PAUSE -> {
                if (simulation || tableau == null) return;
                if (ticksEtat >= cfg.pauseToursSecondes * 20) lancerTour(tableau.tourCourant());
            }
            case FIN -> {
                if (simulation) return;
                if (podium != null && ticksEtat % 40 == 0 && ticksEtat < cfg.finSecondes * 20 - 80) podium.lancerFeux();
                if (ticksEtat >= cfg.finSecondes * 20) {
                    rendreTout();
                    etat = Etat.TERMINE;
                    Tournois.marquerVue();
                }
            }
            default -> {
            }
        }
    }

    private void etat(Etat e) {
        etat = e;
        ticksEtat = 0;
        Tournois.marquerVue();
    }

    // ================================================================== inscriptions
    /** Ouverture : annonce a tout le serveur (chat + toast). */
    void annoncerOuverture() {
        MutableComponent msg = Component.translatable("clashwar.chat.ouverture", fort("#" + edition), fort(format.nom),
                fort(Tournois.heure(finInscriptionMs)), commande("/clashwar"));
        Annonces.chat(serveur, msg);
        MatchsNet.versTous(new CwPaquets.ToastS2C(Component.translatable("clashwar.toast.ouverture"),
                Component.translatable("clashwar.toast.ouverture.texte", format.nom), Tournois.ACCENT, CwSons.id(CwSons.ANNONCE)));
    }

    private void rappel(int minutes) {
        int equipes = 0;
        for (Inscription i : inscriptions.values()) equipes += i.equipes.size();
        Annonces.chat(serveur, Component.translatable("clashwar.chat.rappel", fort(String.valueOf(minutes)), fort(String.valueOf(equipes)),
                commande("/clashwar")));
        MatchsNet.versTous(new CwPaquets.ToastS2C(Component.translatable("clashwar.toast.rappel"),
                Component.translatable("clashwar.toast.rappel.texte", minutes), Tournois.ACCENT, CwSons.id(CwSons.ANNONCE)));
    }

    /** Equipes inscrites (tous clans). */
    int equipesInscrites() {
        int n = 0;
        for (Inscription i : inscriptions.values()) n += i.equipes.size();
        return n;
    }

    private boolean tropRapide(ServerPlayer sp) {
        long now = System.currentTimeMillis();
        Long avant = cadence.get(sp.getUUID());
        if (avant != null && now - avant < 300) return true;
        cadence.put(sp.getUUID(), now);
        return false;
    }

    /** Demande d'inscription (ou de modification, liste vide = desinscription) du clan du joueur. Tout est verifie ici. */
    void inscrire(ServerPlayer sp, List<List<UUID>> demande) {
        if (tropRapide(sp)) return;
        if (etat != Etat.INSCRIPTION) {
            refus(sp, "clashwar.inscription.fermee");
            return;
        }
        Optional<UUID> clanO = ClansApi.clanDe(sp.getUUID());
        if (clanO.isEmpty()) {
            refus(sp, "clashwar.inscription.sans_clan");
            return;
        }
        UUID clan = clanO.get();
        if (!ClansApi.aPerm(sp.getUUID(), Perm.CLASHWAR_INSCRIRE)) {
            refus(sp, "clashwar.inscription.permission");
            return;
        }
        ClanInfo info = ClansApi.infos(clan).orElse(null);
        if (info == null) {
            refus(sp, "clashwar.inscription.sans_clan");
            return;
        }
        // normalisation et validation
        List<List<UUID>> equipes = new ArrayList<>();
        Set<UUID> vus = new LinkedHashSet<>();
        List<String> horsSpawn = new ArrayList<>();
        for (List<UUID> e : demande) {
            if (e.isEmpty()) continue;
            if (e.size() > format.taille) {
                refus(sp, "clashwar.inscription.trop_grande");
                return;
            }
            List<UUID> l = new ArrayList<>(e.size());
            for (UUID u : e) {
                if (!vus.add(u)) {
                    refus(sp, "clashwar.inscription.double");
                    return;
                }
                if (!info.estMembre(u)) {
                    refus(sp, "clashwar.inscription.hors_clan");
                    return;
                }
                ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                // 15/11 (proprietaire) : seulement des membres connectes, meme deja inscrits (une deconnexion retire l'equipe)
                if (p == null) {
                    refus(sp, "clashwar.inscription.hors_ligne");
                    return;
                }
                if (MatchApi.enMatch(u)) {
                    refus(sp, "clashwar.inscription.en_match");
                    return;
                }
                if (p != null && !auSpawn(p)) horsSpawn.add(p.getGameProfile().getName());
                l.add(u);
            }
            equipes.add(l);
        }
        if (!horsSpawn.isEmpty()) {
            refusHorsSpawn(sp, horsSpawn);
            return;
        }
        int autres = equipesInscrites() - (inscriptions.containsKey(clan) ? inscriptions.get(clan).equipes.size() : 0);
        if (autres + equipes.size() > CwConfig.get().maxEquipes) {
            refus(sp, "clashwar.inscription.complet");
            return;
        }
        Inscription ancienne = inscriptions.get(clan);
        Set<UUID> avant = new LinkedHashSet<>();
        if (ancienne != null) for (List<UUID> e : ancienne.equipes) avant.addAll(e);
        if (equipes.isEmpty()) {
            inscriptions.remove(clan);
        } else {
            Inscription n = new Inscription(clan);
            n.equipes.addAll(equipes);
            inscriptions.put(clan, n);
        }
        // prevenir les joueurs ajoutes, retires, ou changes d'equipe
        for (int i = 0; i < equipes.size(); i++) {
            for (UUID u : equipes.get(i)) {
                if (avant.remove(u)) continue;
                ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                if (p == null) continue;
                Annonces.chat(p, Component.translatable("clashwar.chat.inscrit", fort(info.tagCrochets()), fort(String.valueOf(i + 1)),
                        commande("/clashwar retirer")));
                MatchsNet.versJoueur(p, new CwPaquets.ToastS2C(Component.translatable("clashwar.toast.inscrit"),
                        Component.translatable("clashwar.toast.inscrit.texte", info.tagCrochets(), i + 1), Tournois.ACCENT, CwSons.id(CwSons.INSCRIPTION)));
            }
        }
        for (UUID u : avant) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) Annonces.chat(p, Component.translatable("clashwar.chat.desinscrit", fort(info.tagCrochets())));
        }
        HeralizClashWar.LOG.info("[clashwar] inscription de {} par {} : {}", info.tag(), sp.getGameProfile().getName(), equipes);
        majClan(info, sp, equipes.isEmpty() ? "clashwar.inscription.retiree" : "clashwar.inscription.enregistree", false);
        Tournois.marquerVue();
    }

    /** Le joueur se retire de l'inscription de son clan (ou quitte l'evenement pendant le jeu). */
    void retirer(ServerPlayer sp) {
        UUID u = sp.getUUID();
        if (etat != Etat.INSCRIPTION) {
            quitter(sp);
            return;
        }
        Optional<UUID> clanO = ClansApi.clanDe(u);
        Inscription ins = clanO.map(inscriptions::get).orElse(null);
        if (ins == null || !ins.contient(u)) {
            Annonces.chat(sp, Component.translatable("clashwar.chat.pas_inscrit"));
            return;
        }
        for (List<UUID> e : ins.equipes) e.remove(u);
        ins.equipes.removeIf(List::isEmpty);
        if (ins.equipes.isEmpty()) inscriptions.remove(ins.clan);
        Annonces.chat(sp, Component.translatable("clashwar.chat.retire"));
        ClansApi.infos(ins.clan).ifPresent(info -> majClan(info, null, "", false));
        Tournois.marquerVue();
    }

    /** Un membre a quitte son clan (ou en a ete exclu) : retire de l'inscription. */
    void membreParti(UUID clan, UUID joueur) {
        Inscription ins = inscriptions.get(clan);
        if (etat != Etat.INSCRIPTION || ins == null || !ins.contient(joueur)) return;
        for (List<UUID> e : ins.equipes) e.remove(joueur);
        ins.equipes.removeIf(List::isEmpty);
        if (ins.equipes.isEmpty()) inscriptions.remove(clan);
        ClansApi.infos(clan).ifPresent(info -> majClan(info, null, "", false));
        Tournois.marquerVue();
    }

    /** Un clan est dissous : son inscription disparait (pendant le jeu, ses equipes continuent sous leur tag). */
    void clanDissous(UUID clan) {
        if (etat == Etat.INSCRIPTION && inscriptions.remove(clan) != null) Tournois.marquerVue();
    }

    /** Inscription refusee : des membres ne sont pas au spawn (nommes dans l'ecran et dans le chat). */
    private void refusHorsSpawn(ServerPlayer sp, List<String> noms) {
        String liste = String.join(", ", noms);
        Annonces.chat(sp, Component.translatableWithFallback("clashwar.chat.inscription_hors_spawn",
                "Inscription refusée, pas au spawn : %s. Tous les membres d'une équipe doivent être au spawn pour l'inscrire au ClashWar.",
                liste).withStyle(ChatFormatting.RED));
        String m = "clashwar.inscription.hors_spawn" + SEP + liste;
        // 96 caracteres au plus dans le paquet de l'ecran : la liste est coupee (le chat la donne en entier)
        refus(sp, m.length() > 96 ? m.substring(0, 95) + "\u2026" : m);
    }

    // ================================================================== spawn (15/11)
    /** Le joueur est dans le monde du spawn. */
    static boolean auSpawn(ServerPlayer p) {
        return MONDE_SPAWN.equals(p.level().dimension().location());
    }

    /** Premier joueur connecte de la liste qui n'est pas au spawn, ou null (les deconnectes ne comptent pas). */
    @Nullable
    private ServerPlayer premierHorsSpawn(List<UUID> joueurs) {
        for (int i = 0; i < joueurs.size(); i++) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(joueurs.get(i));
            if (p != null && !auSpawn(p)) return p;
        }
        return null;
    }

    /**
     * Inscriptions ouvertes, une fois par seconde : l'equipe d'un inscrit connecte qui n'est plus au spawn est retiree de
     * l'inscription ; toute l'equipe est prevenue (elle peut se reinscrire depuis le spawn tant que c'est ouvert). Seuls
     * les inscrits sont regardes ; rien a faire sans inscription.
     */
    private void surveillerSpawn() {
        if (inscriptions.isEmpty()) return;
        for (Iterator<Inscription> it = inscriptions.values().iterator(); it.hasNext(); ) {
            Inscription ins = it.next();
            boolean change = false;
            // de la derniere a la premiere : le numero annonce est celui que l'equipe voyait
            for (int i = ins.equipes.size() - 1; i >= 0; i--) {
                List<UUID> e = ins.equipes.get(i);
                ServerPlayer parti = premierHorsSpawn(e);
                if (parti == null) continue;
                ins.equipes.remove(i);
                change = true;
                String equipe = ClansApi.infos(ins.clan).map(ClanInfo::tagCrochets).orElse("?") + " " + (i + 1);
                HeralizClashWar.LOG.info("[clashwar] {} a quitte le spawn ({}) : equipe {} retiree de l'inscription",
                        parti.getGameProfile().getName(), parti.level().dimension().location(), equipe);
                Component msg = Component.translatableWithFallback("clashwar.chat.quitte_spawn",
                        "%s a quitté le spawn : votre équipe %s est retirée de l'inscription au ClashWar. Revenez tous au spawn pour la réinscrire avant %s (%s).",
                        fort(parti.getGameProfile().getName()), fort(equipe), fort(Tournois.heure(finInscriptionMs)), commande("/clashwar"))
                        .withStyle(ChatFormatting.RED);
                for (UUID u : e) {
                    ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                    if (p != null) Annonces.chat(p, msg);
                }
            }
            if (!change) continue;
            if (ins.equipes.isEmpty()) it.remove();
            ClansApi.infos(ins.clan).ifPresent(info -> majClan(info, null, "", false));
            Tournois.marquerVue();
        }
    }

    /** Depart vers la salle : l'equipe dont un joueur n'est pas au spawn est retiree du tournoi ; ses joueurs sont prevenus. */
    private void retireeAuDepart(List<UUID> joueurs, String equipe, ServerPlayer dehors) {
        HeralizClashWar.LOG.info("[clashwar] edition {} : {} pas au spawn au depart ({}) : equipe {} retiree du tournoi", edition,
                dehors.getGameProfile().getName(), dehors.level().dimension().location(), equipe);
        Component msg = Component.translatableWithFallback("clashwar.chat.depart_hors_spawn",
                "%s n'était pas au spawn au départ du ClashWar : votre équipe %s est retirée du tournoi.",
                fort(dehors.getGameProfile().getName()), fort(equipe)).withStyle(ChatFormatting.RED);
        for (UUID u : joueurs) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) Annonces.chat(p, msg);
        }
    }

    /** Premier joueur de la liste qui n'est pas connecte (les mannequins du banc d'essai ne comptent pas), ou null (15/11). */
    @Nullable
    private UUID premierAbsent(List<UUID> joueurs) {
        for (int i = 0; i < joueurs.size(); i++) {
            UUID u = joueurs.get(i);
            if (!mannequins.contains(u) && serveur.getPlayerList().getPlayer(u) == null) return u;
        }
        return null;
    }

    /**
     * 15/11 (regle du proprietaire) : un inscrit se deconnecte. Pendant les inscriptions, son equipe est retiree de
     * l'inscription au moment meme (ses coequipiers peuvent la reinscrire depuis le spawn tant que c'est ouvert) ; apres la
     * cloture et avant l'entree dans la salle, elle est retiree du tournoi. Ensuite, ce sont les regles du match. Rien a
     * faire hors de ces deux moments ou pour un joueur qui n'est pas inscrit.
     */
    void deconnecte(ServerPlayer parti) {
        if (simulation) return;
        UUID u = parti.getUUID();
        if (etat == Etat.INSCRIPTION) {
            for (Iterator<Inscription> it = inscriptions.values().iterator(); it.hasNext(); ) {
                Inscription ins = it.next();
                if (!ins.contient(u)) continue;
                String tag = ClansApi.infos(ins.clan).map(ClanInfo::tagCrochets).orElse("?");
                // de la derniere a la premiere : le numero annonce est celui que l'equipe voyait
                for (int i = ins.equipes.size() - 1; i >= 0; i--) {
                    List<UUID> e = ins.equipes.get(i);
                    if (!e.contains(u)) continue;
                    ins.equipes.remove(i);
                    retireeDeconnexion(e, tag + " " + (i + 1), u, true);
                }
                if (ins.equipes.isEmpty()) it.remove();
                ClansApi.infos(ins.clan).ifPresent(info -> majClan(info, null, "", false));
                Tournois.marquerVue();
            }
        } else if (etat == Etat.PREPARATION && !departFait) {
            // 21/11 (audit a7) : seulement avant le depart vers la salle ; une fois les depots lances, l'equipe part sans lui
            // (son depot ne se fait pas) : avant, ses coequipiers lisaient « equipe retiree du ClashWar » et jouaient quand meme
            for (Iterator<Tableau.EquipeT> it = provisoires.iterator(); it.hasNext(); ) {
                Tableau.EquipeT e = it.next();
                if (!e.joueurs.contains(u)) continue;
                it.remove();
                retireeDeconnexion(e.joueurs, "[" + e.tag + "] " + e.numero, u, false);
                Tournois.marquerVue();
            }
        }
    }

    /**
     * Equipe retiree parce que {@code absent} s'est deconnecte (15/11) : journal, et ses autres joueurs connectes prevenus
     * ({@code inscription} : ils peuvent la reinscrire ; sinon : retiree du tournoi).
     */
    private void retireeDeconnexion(List<UUID> joueurs, String equipe, UUID absent, boolean inscription) {
        String nom = ClansApi.nomJoueur(absent);
        HeralizClashWar.LOG.info("[clashwar] edition {} : {} deconnecte : equipe {} retiree {}", edition, nom, equipe,
                inscription ? "de l'inscription" : "du tournoi");
        Component msg = inscription
                ? Component.translatableWithFallback("clashwar.chat.deconnecte_inscription",
                        "%s s'est déconnecté : votre équipe %s est retirée de l'inscription au ClashWar. Revenez tous au spawn pour la réinscrire avant %s (%s).",
                        fort(nom), fort(equipe), fort(Tournois.heure(finInscriptionMs)), commande("/clashwar")).withStyle(ChatFormatting.RED)
                : Component.translatableWithFallback("clashwar.chat.deconnecte_depart",
                        "%s s'est déconnecté : votre équipe %s est retirée du ClashWar.", fort(nom), fort(equipe)).withStyle(ChatFormatting.RED);
        for (UUID m : joueurs) {
            if (m.equals(absent)) continue;
            ServerPlayer p = serveur.getPlayerList().getPlayer(m);
            if (p != null) Annonces.chat(p, msg);
        }
    }

    private void refus(ServerPlayer sp, String cle) {
        Optional<UUID> clan = ClansApi.clanDe(sp.getUUID());
        Optional<ClanInfo> info = clan.flatMap(ClansApi::infos);
        if (info.isPresent()) envoyerInscription(sp, info.get(), false, cle, true);
        else Annonces.chat(sp, Component.translatable(cle.indexOf(SEP) < 0 ? cle : cle.substring(0, cle.indexOf(SEP))).withStyle(ChatFormatting.RED));
    }

    /** Met a jour l'ecran d'inscription de tous les membres connectes du clan (message seulement pour l'auteur). */
    private void majClan(ClanInfo info, @Nullable ServerPlayer auteur, String message, boolean erreur) {
        for (UUID u : info.enLigne()) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) envoyerInscription(p, info, false, p == auteur ? message : "", erreur);
        }
    }

    /** Envoie l'etat de l'inscription du clan a ce joueur. */
    void envoyerInscription(ServerPlayer sp, ClanInfo info, boolean ouvrir, String message, boolean erreur) {
        List<CwPaquets.Membre> membres = new ArrayList<>();
        Inscription ins = inscriptions.get(info.id());
        for (Map.Entry<UUID, Grade> e : info.membres().entrySet()) {
            UUID u = e.getKey();
            boolean enLigne = serveur.getPlayerList().getPlayer(u) != null;
            if (!enLigne && (ins == null || !ins.contient(u))) continue;
            membres.add(new CwPaquets.Membre(u, info.noms().getOrDefault(u, ClansApi.nomJoueur(u)), e.getValue().libelle()));
        }
        List<List<UUID>> equipes = new ArrayList<>();
        if (ins != null) for (List<UUID> e : ins.equipes) equipes.add(List.copyOf(e));
        MatchsNet.versJoueur(sp, new CwPaquets.InscriptionS2C(ouvrir, edition, format.taille, format.nom,
                finInscriptionMs - System.currentTimeMillis(), ClansApi.aPerm(sp.getUUID(), Perm.CLASHWAR_INSCRIRE), info.tag(), info.nom(),
                Banniere.lisible(info.banniere().accent()), info.banniere(), membres, equipes, inscriptions.size(), equipesInscrites(),
                CwConfig.get().maxEquipes, message, erreur));
    }

    // ================================================================== cloture et salle
    /** Fin des inscriptions : equipes retenues, salle d'attente reservee (le Coffre suit quand elle est prete). */
    void cloturer() {
        if (etat != Etat.INSCRIPTION) return;
        CwConfig cfg = CwConfig.get();
        provisoires.clear();
        for (Inscription ins : inscriptions.values()) {
            ClanInfo info = ClansApi.infos(ins.clan).orElse(null);
            if (info == null) continue;
            for (int i = 0; i < ins.equipes.size(); i++) {
                // 15/11 : une equipe dont un joueur connecte n'est pas au spawn ne part pas
                ServerPlayer dehors = premierHorsSpawn(ins.equipes.get(i));
                if (dehors != null) {
                    retireeAuDepart(ins.equipes.get(i), info.tagCrochets() + " " + (i + 1), dehors);
                    continue;
                }
                // 15/11 (proprietaire) : ni une equipe dont un joueur n'est pas connecte (deja retiree a sa deconnexion,
                // sauf deconnexion pas vue : redemarrage)
                UUID absent = premierAbsent(ins.equipes.get(i));
                if (absent != null) {
                    retireeDeconnexion(ins.equipes.get(i), info.tagCrochets() + " " + (i + 1), absent, false);
                    continue;
                }
                List<UUID> joueurs = new ArrayList<>();
                Map<UUID, String> noms = new LinkedHashMap<>();
                for (UUID u : ins.equipes.get(i)) {
                    ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                    if (p == null) continue;
                    if (CombatTag.estTague(p)) {
                        Annonces.chat(p, Component.translatable("clashwar.chat.retire_combat").withStyle(ChatFormatting.RED));
                        continue;
                    }
                    joueurs.add(u);
                    noms.put(u, p.getGameProfile().getName());
                }
                if (joueurs.isEmpty()) continue;
                provisoires.add(new Tableau.EquipeT(provisoires.size(), info.id(), info.tag(), info.nom(), i + 1,
                        Banniere.lisible(info.banniere().accent()), info.banniere(), joueurs, noms));
            }
        }
        // banc d'essai : equipes factices (vide en production)
        for (Tableau.EquipeT e : facticesBanc) {
            provisoires.add(new Tableau.EquipeT(provisoires.size(), e.clan, e.tag, e.nomClan, e.numero, e.couleur, e.banniere, e.joueurs, e.noms));
        }
        if (provisoires.size() < cfg.minEquipes) {
            annuler("pas_assez");
            return;
        }
        etat(Etat.PREPARATION);
        Annonces.chat(serveur, Component.translatable("clashwar.chat.cloture", fort(String.valueOf(provisoires.size())),
                fort(String.valueOf(inscriptions.size()))));
        instanceSalle = Arenes.reserver(cfg.salle);
        if (instanceSalle == null) {
            annuler("salle_indisponible");
            return;
        }
        preparerArenes(provisoires.size());
        instanceSalle.quandPrete(this::entrerSalle);
    }

    /**
     * Pose d'avance des arenes du premier tour (19/11, cartes de 90 a 220 blocs de cote) : autant d'instances de la carte
     * du format que de matchs possibles (moitie des equipes, arrondie au-dessus). Tous les matchs d'un tour partent
     * ensemble et les poses passent l'une apres l'autre (une quinzaine de secondes pour la carte du 4v4) : sans cela, les
     * derniers matchs attendraient leur arene au-dela de {@code attente_arene_secondes} et seraient tranches au hasard.
     * Les instances posees restent (registre des arenes) et servent aux editions suivantes.
     */
    private void preparerArenes(int equipes) {
        int matchs = (equipes + 1) / 2;
        if (simulation || matchs <= 0) return;
        for (Carte c : Arenes.cartesDeType(format.typeCarte)) Arenes.preparer(c.id(), matchs);
    }

    /** Une equipe qui part vers la salle (19/11) : ses joueurs deposes, et ses depots encore en cours. */
    private static final class Depart {
        final Tableau.EquipeT equipe;
        final Set<UUID> deposes = new java.util.HashSet<>();
        int enCours;

        Depart(Tableau.EquipeT equipe) {
            this.equipe = equipe;
        }
    }

    /** Depart vers la salle en cours (depots etales, 19/11) ; null avant et apres. */
    @Nullable
    private List<Depart> departs;
    private boolean departFait;

    /**
     * Salle prete : chaque inscrit passe par le Coffre puis y est teleporte ; le tableau est tire quand tous les depots sont
     * finis. 19/11 (audit des lenteurs C3) : les depots sont etales sur plusieurs ticks et ecrits en tache de fond
     * ({@code Coffres.deposerPuis}) ; chaque joueur entre en salle des que le sien est confirme (arrivee prechargee).
     */
    private void entrerSalle() {
        if (etat != Etat.PREPARATION || instanceSalle == null || departFait) return;
        departFait = true;
        salle = new PartieSalle(this, "clashwar-" + edition + "-salle");
        salle.instance(instanceSalle);
        MatchApi.enregistrer(salle);
        poserMur(instanceSalle);
        tenirSalle();
        List<Depart> l = new ArrayList<>();
        departs = l;
        ticksEtat = 0;   // le delai de la preparation repart pour les depots
        for (Tableau.EquipeT e : provisoires) {
            // 15/11 (regle du proprietaire) : au depart, un joueur connecte qui n'est pas au spawn n'est pas teleporte et son
            // equipe est retiree du tournoi (personne de l'equipe n'est depose ni teleporte)
            ServerPlayer dehors = premierHorsSpawn(e.joueurs);
            if (dehors != null) {
                retireeAuDepart(e.joueurs, "[" + e.tag + "] " + e.numero, dehors);
                continue;
            }
            // 15/11 (proprietaire) : ni une equipe dont un joueur n'est pas connecte
            UUID absent = premierAbsent(e.joueurs);
            if (absent != null) {
                retireeDeconnexion(e.joueurs, "[" + e.tag + "] " + e.numero, absent, false);
                continue;
            }
            Depart d = new Depart(e);
            l.add(d);
            for (UUID u : e.joueurs) {
                // banc d'essai : joueur factice, ni connecte ni Coffre (garde dans l'equipe, voir finDeparts)
                if (mannequins.contains(u)) continue;
                ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                if (p == null) continue;
                if (CombatTag.estTague(p)) {
                    Annonces.chat(p, Component.translatable("clashwar.chat.retire_combat").withStyle(ChatFormatting.RED));
                    continue;
                }
                d.enCours++;
                Coffre.Refus r = Coffres.deposerPuis(p, "clashwar", ok -> depose(d, u, ok));
                if (r != null) {
                    d.enCours--;
                    Annonces.chat(p, Component.translatable("clashwar.chat.retire_coffre",
                            Component.translatable("matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.RED));
                }
            }
        }
        finDeparts();
    }

    /**
     * Depot d'un joueur fini (19/11) : fait, il entre en salle ; rate, il reste dehors (prevenu). Edition annulee pendant son
     * ecriture : son Coffre lui est rendu tout de suite. Le tableau est tire quand tous les depots sont finis.
     */
    private void depose(Depart d, UUID u, boolean ok) {
        d.enCours--;
        ServerPlayer p = serveur.getPlayerList().getPlayer(u);
        if (!ok) {
            if (p != null) {
                Annonces.chat(p, Component.translatable("clashwar.chat.retire_coffre",
                        Component.translatable("matchs.coffre.refus.erreur")).withStyle(ChatFormatting.RED));
            }
        } else if (etat != Etat.PREPARATION || departs == null || salle == null) {
            if (p != null) rendreJoueur(p);
        } else {
            participants.add(u);
            d.deposes.add(u);
            if (p != null) placerEnSalle(p);
        }
        finDeparts();
    }

    /** Tous les depots finis : equipes retenues (dans leur ordre, joueurs dans le leur), tableau tire, premiere pause. */
    private void finDeparts() {
        List<Depart> l = departs;
        if (l == null || etat != Etat.PREPARATION) return;
        for (Depart d : l) {
            if (d.enCours > 0) return;
        }
        departs = null;
        List<Tableau.EquipeT> finales = new ArrayList<>();
        for (Depart d : l) {
            Tableau.EquipeT e = d.equipe;
            List<UUID> ok = new ArrayList<>();
            for (UUID u : e.joueurs) {
                if (mannequins.contains(u) || d.deposes.contains(u)) ok.add(u);
            }
            if (ok.isEmpty()) continue;
            Map<UUID, String> noms = new LinkedHashMap<>();
            for (UUID u : ok) noms.put(u, e.nom(u));
            finales.add(new Tableau.EquipeT(finales.size(), e.clan, e.tag, e.nomClan, e.numero, e.couleur, e.banniere, ok, noms));
        }
        if (finales.size() < CwConfig.get().minEquipes) {
            annuler("pas_assez");
            return;
        }
        tableau = Tableau.construire(finales, rnd);
        etat(Etat.PAUSE);
        int s = CwConfig.get().pauseToursSecondes;
        Annonces.grande(joueursSalle(), new Annonces.Annonce(Component.translatable("clashwar.annonce.tableau"),
                Component.translatable("clashwar.annonce.premier_tour", s), Tournois.ACCENT, CwSons.id(CwSons.ANNONCE), 80, Annonces.Style.NORMALE));
        bandeauSalle();
    }

    /**
     * Salle d'attente gardee chargee pendant le tournoi (19/11) : ses points d'apparition prechargees (ticket de 30 s,
     * redemande toutes les 10 s) ; les joueurs qui y reviennent d'un match n'attendent pas son chargement.
     */
    private void tenirSalle() {
        Instance i = instanceSalle;
        if (i == null) return;
        List<fr.heraliz.clashwar.moteur.api.Pose> l = i.points("spawn");
        if (l.isEmpty()) {
            net.minecraft.world.phys.AABB b = i.bornes();
            Arrivees.precharger(i.niveau(), b.getCenter().x, b.getCenter().z);
            return;
        }
        for (int k = 0; k < l.size(); k++) Arrivees.precharger(i.niveau(), l.get(k).x(), l.get(k).z());
    }

    /** Rattache le joueur a la salle, le teleporte, mode aventure, soigne. */
    void placerEnSalle(ServerPlayer p) {
        if (salle == null) return;
        salle.ajouter(p.getUUID());
        MatchApi.rejoindre(p, salle);
        p.setCamera(p);
        p.setGameMode(GameType.ADVENTURE);
        p.removeAllEffects();
        p.clearFire();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        salle.teleporter(p);
        MatchsNet.versJoueur(p, new CwPaquets.ZoneS2C(false, 0, 0, 0, 0, 0, 0));
        bandeauSalle(p);
    }

    /** Pose le mur-tableau de la salle (metadonnees {@code tableau_mur} de la carte) s'il n'y est pas deja. */
    private void poserMur(Instance i) {
        try {
            JsonObject m = i.carte().meta();
            if (!m.has("tableau_mur")) return;
            JsonObject t = m.getAsJsonObject("tableau_mur");
            JsonArray c = t.getAsJsonArray("coin_bas_gauche");
            BlockPos base = i.origine().offset(c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt());
            Direction face = Direction.byName(t.has("face") ? t.get("face").getAsString() : "south");
            if (face == null || face.getAxis() == Direction.Axis.Y) face = Direction.SOUTH;
            int l = t.has("largeur") ? t.get("largeur").getAsInt() : 24, h = t.has("hauteur") ? t.get("hauteur").getAsInt() : 12;
            // le mur se pose devant la paroi (cote salle) : la paroi est en z = 1, l'ecran sur la face sud
            BlockPos devant = base.relative(face);
            if (MurTableau.estMur(i.niveau().getBlockState(devant))) return;
            if (!MurTableau.poser(i.niveau(), devant, face, l, h)) {
                HeralizClashWar.LOG.warn("[clashwar] mur-tableau non pose dans {} (place occupee)", i);
            }
        } catch (RuntimeException ex) {
            HeralizClashWar.LOG.warn("[clashwar] tableau_mur illisible pour {} : {}", i.carte().id(), ex.toString());
        }
    }

    // ================================================================== tours
    /** Lance les rencontres jouables du tour (en parallele, une instance d'arene chacune). */
    void lancerTour(int t) {
        if (tableau == null || t >= tableau.nbTours()) return;
        tour = t;
        etat(Etat.TOUR);
        Component nomTour = Component.translatable("clashwar.tour." + tableau.cleTour(t));
        boolean finale = t == tableau.nbTours() - 1;
        if (finale) {
            Tableau.Rencontre f = tableau.finale();
            Component a = libelle(f.a), b = libelle(f.b);
            Annonces.grandeATous(new Annonces.Annonce(Component.translatable("clashwar.annonce.finale", a, b),
                    Component.translatable("clashwar.annonce.finale.sous", "#" + edition, format.nom), Tournois.OR, CwSons.id(CwSons.FINALE), 120,
                    Annonces.Style.FINALE));
            Annonces.chat(serveur, Component.translatable("clashwar.chat.finale", a, b));
        } else {
            Annonces.grande(joueursSalle(), new Annonces.Annonce(nomTour, Component.translatable("clashwar.annonce.tour.sous"),
                    Tournois.ACCENT, CwSons.id(CwSons.APPEL), 60, Annonces.Style.NORMALE));
        }
        int lances = 0;
        for (Tableau.Rencontre r : tableau.tour(t)) {
            if (!r.jouable()) continue;
            if (simulation) continue;
            MatchCw m = MatchCw.creer(this, r, tableau.equipe(r.a), tableau.equipe(r.b),
                    Component.translatable("clashwar.tour." + tableau.cleRencontre(r)));
            matchs.add(m);
            MatchApi.enregistrer(m);
            r.statut = Tableau.Statut.EN_COURS;
            lances++;
        }
        HeralizClashWar.LOG.info("[clashwar] edition {} : tour {} ({}) lance, {} matchs", edition, t, tableau.cleTour(t), lances);
        bandeauSalle();
        if (lances == 0 && !simulation) apresRencontre();
    }

    /** Resultat d'une rencontre (match fini, forfait). */
    void resultat(@Nullable MatchCw m, Tableau.Rencontre r, int gagnant, boolean forfait) {
        if (tableau == null) return;
        if (m != null) matchs.remove(m);
        r.forfait = forfait;
        tableau.avancer(r, gagnant);
        diffuserRencontre(r);
        Tournois.marquerVue();
        int perdant = r.perdant();
        if (perdant >= 0 && !simulation) {
            Annonces.chat(serveur, Component.translatable(forfait ? "clashwar.chat.forfait" : "clashwar.chat.resultat", libelle(gagnant),
                    libelle(perdant), Component.translatable("clashwar.tour." + tableau.cleRencontre(r))));
            if (!tableau.termine()) proposerDepart(perdant);
        }
        // demi-finale : l'equipe envoyee au match pour la 3e place est prevenue
        Tableau.Rencontre tr = tableau.troisieme();
        if (perdant >= 0 && !simulation && tr != null && (tr.a == perdant || tr.b == perdant) && !tr.finie()) {
            for (ServerPlayer p : joueursEquipe(perdant)) {
                Annonces.chat(p, Component.translatable("clashwar.chat.vers_troisieme").withStyle(ChatFormatting.GOLD));
            }
        }
        apresRencontre();
    }

    /** Joueurs connectes de l'equipe encore participants (nouvelle liste). */
    private List<ServerPlayer> joueursEquipe(int index) {
        List<ServerPlayer> l = new ArrayList<>();
        if (tableau == null || index < 0) return l;
        for (UUID u : tableau.equipe(index).joueurs) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null && participants.contains(u)) l.add(p);
        }
        return l;
    }

    /** Vrai si l'equipe a encore une rencontre a jouer (match pour la 3e place compris). */
    private boolean aEncoreUnMatch(int index) {
        if (tableau == null || index < 0) return false;
        for (List<Tableau.Rencontre> tour : tableau.tours()) {
            for (Tableau.Rencontre r : tour) if (!r.finie() && (r.a == index || r.b == index)) return true;
        }
        return false;
    }

    /**
     * Equipe eliminee sans match restant : ses joueurs peuvent quitter l'evenement (Coffre rendu, retour en jeu) d'un
     * clic, ou rester regarder les autres matchs ({@code /clashwar tableau}, clic sur un match en direct).
     */
    private void proposerDepart(int index) {
        if (aEncoreUnMatch(index)) return;
        for (ServerPlayer p : joueursEquipe(index)) {
            Annonces.chat(p, Component.translatable("clashwar.chat.elimine_choix", commande("/clashwar quitter"), commande("/clashwar tableau")));
            MatchsNet.versJoueur(p, new CwPaquets.ToastS2C(Component.translatable("clashwar.toast.elimine"),
                    Component.translatable("clashwar.toast.elimine.texte"), 0xFF6B6B, CwSons.id(CwSons.DEFAITE)));
        }
    }

    /** Banc d'essai : rencontre tranchee sans match. */
    void resultatSimule(Tableau.Rencontre r, int gagnant) {
        resultat(null, r, gagnant, false);
    }

    private void apresRencontre() {
        if (tableau == null) return;
        if (tableau.termine()) {
            finir();
            return;
        }
        if (tableau.tourCourant() > tour && matchs.isEmpty()) {
            etat(Etat.PAUSE);
            int s = CwConfig.get().pauseToursSecondes;
            Annonces.grande(joueursSalle(), Annonces.Annonce.simple(
                    Component.translatable("clashwar.tour." + tableau.cleTour(tableau.tourCourant())),
                    Component.translatable("clashwar.annonce.prochain_tour", s), Tournois.ACCENT));
            bandeauSalle();
        }
    }

    /** Sacre du vainqueur, recompenses, stats ; retour de tout le monde apres {@code finSecondes}. */
    private void finir() {
        if (tableau == null) return;
        etat(Etat.FIN);
        Tableau.EquipeT ch = tableau.equipe(tableau.champion());
        StringBuilder noms = new StringBuilder();
        for (UUID u : ch.joueurs) {
            if (noms.length() > 0) noms.append(" · ");
            noms.append(ch.nom(u));
        }
        if (simulation) {
            // banc d'essai : pas d'annonce a tout le serveur
            HeralizClashWar.LOG.info("[clashwar] simulation : vainqueur {} ({})", ch.libelle(), noms);
            return;
        }
        Annonces.grandeATous(new Annonces.Annonce(Component.translatable("clashwar.annonce.vainqueur", ch.libelle()),
                Component.literal(noms.toString()), Tournois.OR, CwSons.id(CwSons.SACRE), 160, Annonces.Style.FINALE));
        Annonces.chat(serveur, Component.translatable("clashwar.chat.vainqueur",
                Component.literal(ch.libelle()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(ch.couleur)).withBold(true)),
                fort("#" + edition), fort(format.nom), Component.literal(noms.toString())));
        // podium dans le chat : 1er, 2e, 3e (et 4e)
        int[] podium = tableau.podium();
        Annonces.chat(serveur, Component.translatable("clashwar.chat.podium.titre", fort("#" + edition)));
        for (int i = 0; i < podium.length; i++) {
            if (podium[i] < 0) continue;
            Annonces.chat(serveur, Component.translatable("clashwar.chat.podium." + (i + 1), libelle(podium[i])));
        }
        Recompenses.distribuer(this);
        CwEtat.champion = new CwEtat.Champion(ch.tag, ch.nomClan, ch.numero, ch.couleur, ch.banniere, edition, format.nom, System.currentTimeMillis());
        CwEtat.sauver(serveur);
        Tournois.diffuserClassement(serveur);
        ceremonie(podium);
        bandeauSalle();
        HeralizClashWar.LOG.info("[clashwar] edition {} : vainqueur {} ({})", edition, ch.libelle(), noms);
    }

    /**
     * Ceremonie du podium (maquette 3) : marches posees dans la salle, trois premieres equipes dessus, titre et cartes
     * des trois premiers chez chaque joueur de la salle pendant toute la fin.
     */
    private void ceremonie(int[] places) {
        if (tableau == null) return;
        this.podium = PodiumSalle.poser(instanceSalle, tableau, places, this);
        CwConfig cfg = CwConfig.get();
        // 19/11 : primes vraiment versees (rien sous recompenses_clans_min clans, une seule par clan)
        long[] montants = Recompenses.primesParPlace(tableau);
        boolean primes = Recompenses.avecPrimes(tableau);
        List<CwPaquets.Marche> marches = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (places[i] < 0) continue;
            Tableau.EquipeT e = tableau.equipe(places[i]);
            StringBuilder noms = new StringBuilder();
            for (UUID u : e.joueurs) {
                if (noms.length() > 0) noms.append(" · ");
                noms.append(e.nom(u));
            }
            boolean cle = i == 0 && primes && cfg.clesParJoueur > 0 && !cfg.cleEvent.isBlank();
            Component recompense = montants[i] > 0
                    ? Component.translatable(cle ? "clashwar.podium.recompense_cle" : "clashwar.podium.recompense", heralsTexte(montants[i]))
                    : !primes ? Component.translatableWithFallback("clashwar.podium.sans_recompense", "Sans prime")
                    : Component.translatableWithFallback("clashwar.podium.deja_prime", "Une prime par clan");
            marches.add(new CwPaquets.Marche(i + 1, e.libelle(), e.nomClan, e.couleur, noms.toString(), recompense));
        }
        MatchsNet.versJoueurs(joueursSalle(), new CwPaquets.PodiumS2C(edition, format.nom, marches, cfg.finSecondes));
    }

    /** « 5 000 » : milliers separes par une espace fine insecable. */
    static String heralsTexte(long v) {
        String t = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            if (i > 0 && (t.length() - i) % 3 == 0) sb.append('\u202F');
            sb.append(t.charAt(i));
        }
        return (v < 0 ? "-" : "") + sb;
    }

    /** Annule l'edition (raison = suffixe de {@code clashwar.annule.*}) et rend tout le monde. */
    void annuler(String raison) {
        if (!actif()) return;
        HeralizClashWar.LOG.info("[clashwar] edition {} annulee : {}", edition, raison);
        for (MatchCw m : new ArrayList<>(matchs)) m.annuler();
        matchs.clear();
        rendreTout();
        etat(Etat.ANNULE);
        Annonces.chat(serveur, Component.translatable("clashwar.chat.annule", Component.translatable("clashwar.annule." + raison))
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Rend le Coffre a chaque participant (les deconnectes le recuperent a leur retour) et ferme la salle. 19/11 : les
     * retours sont etales ({@link #rendreJoueur}) ; la salle reste ouverte (joueurs encore rattaches, sans degats ni
     * commandes) jusqu'au dernier retour, 30 s au plus ({@link #apresFin}).
     */
    private void rendreTout() {
        if (podium != null) {
            podium.retirer();
            podium = null;
        }
        for (UUID u : new ArrayList<>(participants)) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) rendreJoueur(p);
            else MatchApi.quitter(u);
        }
        participants.clear();
        for (MatchCw m : new ArrayList<>(matchs)) m.fermer();
        matchs.clear();
        salleAFermer = true;
        ticksFermeture = 0;
        if (retours.isEmpty()) fermerSalle();
    }

    /** Joueurs dont le retour etale est en cours (19/11) ; la salle se ferme apres le dernier. */
    private final Set<UUID> retours = new java.util.HashSet<>();
    private boolean salleAFermer;
    private int ticksFermeture;

    /** Salle fermee et instance rendue (fin ou annulation, apres les retours). */
    private void fermerSalle() {
        salleAFermer = false;
        if (salle != null) salle.fermer();
        else if (instanceSalle != null) Arenes.liberer(instanceSalle);
        instanceSalle = null;
    }

    /**
     * Chaque tick apres la fin ou l'annulation (Tournois.tick) : la salle se ferme quand tous les retours sont faits, ou
     * 30 s apres au plus (un retour bloque ne la garde pas ouverte). Rien a faire ensuite.
     */
    /** Nouvelle edition ouverte : la salle de celle-ci, si elle attendait encore des retours, est fermee tout de suite. */
    void fermerSiBesoin() {
        if (salleAFermer) fermerSalle();
    }

    void apresFin() {
        if (!salleAFermer) return;
        if (retours.isEmpty() || ++ticksFermeture > 20 * 30) {
            if (!retours.isEmpty()) HeralizClashWar.LOG.warn("[clashwar] edition {} : salle fermee avec {} retour(s) encore en cours", edition, retours.size());
            fermerSalle();
        }
    }

    /**
     * Rend son Coffre a un joueur (position d'origine) et le detache ; donne ses cles de caisse en attente. 19/11 (audit
     * des lenteurs C3) : le retour lui-meme est etale ({@code Coffres.rendrePuis} : depot lu d'avance, arrivee prechargee,
     * quelques retours par tick) ; le joueur reste en salle, sans kit, jusqu'a son tour.
     */
    void rendreJoueur(ServerPlayer p) {
        UUID u = p.getUUID();
        MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
        Equipement.retirer(p);
        p.setCamera(p);
        Hud.effacer(p);
        MatchsNet.versJoueur(p, new CwPaquets.ZoneS2C(false, 0, 0, 0, 0, 0, 0));
        // une arrivee en salle encore en attente ne doit pas le ramener apres son retour
        Arrivees.oublier(u);
        participants.remove(u);
        retours.add(u);
        Coffres.rendrePuis(p, rendu -> {
            retours.remove(u);
            ServerPlayer q = serveur.getPlayerList().getPlayer(u);
            if (salle != null) salle.retirer(u);
            if (q == null) {
                // parti avant son tour : son depot reste sur le disque (rendu a sa reconnexion)
                MatchApi.quitter(u);
            } else {
                if (!rendu) {
                    HeralizClashWar.LOG.warn("[clashwar] Coffre.rendre({}) a echoue : le depot reste sur disque", q.getGameProfile().getName());
                    if (q.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) q.setGameMode(GameType.SURVIVAL);
                }
                MatchApi.quitter(q);
                Recompenses.donnerCles(q);
            }
            if (salleAFermer && retours.isEmpty()) fermerSalle();
        });
    }

    /** /clashwar quitter : un participant qui n'est pas en train de jouer recupere son Coffre et quitte l'evenement. */
    void quitter(ServerPlayer p) {
        UUID u = p.getUUID();
        if (!participants.contains(u)) {
            Annonces.chat(p, Component.translatable("clashwar.chat.pas_participant"));
            return;
        }
        // il regardait un match : d'abord ramene en salle
        if (MatchApi.partieDe(p) instanceof MatchCw m && m.equipeDe(u) == null) m.arreterRegarder(p);
        if (salle == null || MatchApi.partieDe(p) != salle) {
            Annonces.chat(p, Component.translatable("clashwar.chat.quitter_en_match").withStyle(ChatFormatting.RED));
            return;
        }
        if (tableau != null) {
            for (Tableau.EquipeT e : tableau.equipes()) e.joueurs.remove(u);
        }
        rendreJoueur(p);
        Annonces.chat(p, Component.translatable("clashwar.chat.quitte"));
        Tournois.marquerVue();
    }

    // ================================================================== spectateurs de la salle
    /**
     * {@code /clashwar regarder [tour index]} : un participant libre (en salle, ou deja en train de regarder un autre
     * match) regarde une rencontre en direct, par les yeux des joueurs ; sans rencontre donnee, le premier match en direct.
     * 18/11 : un joueur qui n'est pas du tournoi peut venir regarder ({@link #entrerSpectateur}).
     */
    void regarder(ServerPlayer p, int tour, int index) {
        UUID u = p.getUUID();
        if (salle == null) {
            Annonces.chat(p, Component.translatable("clashwar.chat.regarder_aucun").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!participants.contains(u)) {
            // 19/11 : son depot est etale ; il regarde des qu'il est en salle
            entrerSpectateur(p, tour, index);
            return;
        }
        MatchCw courantVu = MatchApi.partieDe(p) instanceof MatchCw m && m.equipeDe(u) == null ? m : null;
        if (courantVu == null && MatchApi.partieDe(p) != salle) {
            Annonces.chat(p, Component.translatable("clashwar.chat.regarder_en_match").withStyle(ChatFormatting.RED));
            return;
        }
        MatchCw cible = null;
        for (MatchCw m : matchs) {
            if (!m.regardable()) continue;
            if (tour < 0 || (m.rencontre().tour == tour && m.rencontre().index == index)) {
                cible = m;
                break;
            }
        }
        if (cible == null) {
            Annonces.chat(p, Component.translatable("clashwar.chat.regarder_aucun").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (cible == courantVu) return;
        if (courantVu != null) courantVu.arreterRegarder(p);
        if (cible.regarder(p)) {
            Annonces.chat(p, Component.translatable("clashwar.chat.regarder", cible.contre(), commande("/clashwar salle")));
        }
    }

    /**
     * Un joueur qui n'est pas du tournoi vient le regarder (18/11, retour du propriétaire : « /clashwar regarder ne marche
     * pas pour quelqu'un qui n'était ni inscrit ni éliminé »). Seulement depuis le spawn (sinon : un moyen de fuir un combat
     * ou de traverser la carte) ; il passe par le Coffre comme un inscrit (inventaire et position mis de côté, rendus à
     * {@code /clashwar quitter}, à la fin du tournoi ou à la déconnexion), puis entre en salle sans équipe : il n'est
     * jamais tiré dans un match. 19/11 : dépôt étalé ({@code Coffres.deposerPuis}) ; une fois en salle, il regarde la
     * rencontre demandée. S'il ne peut pas venir : message.
     */
    private void entrerSpectateur(ServerPlayer p, int tour, int index) {
        if (!auSpawn(p)) {
            Annonces.chat(p, Component.translatableWithFallback("clashwar.chat.regarder_spawn",
                    "Pour regarder le ClashWar, viens au spawn.").withStyle(ChatFormatting.RED));
            return;
        }
        UUID u = p.getUUID();
        Coffre.Refus r = Coffres.deposerPuis(p, "clashwar_spectateur", ok -> {
            ServerPlayer q = serveur.getPlayerList().getPlayer(u);
            if (q == null) return;
            if (!ok) {
                Annonces.chat(q, Component.translatable("matchs.coffre.refus.erreur").withStyle(ChatFormatting.RED));
                return;
            }
            if (!actif() || salle == null) {
                // l'edition s'est finie pendant l'ecriture : son Coffre lui est rendu tout de suite
                rendreJoueur(q);
                return;
            }
            participants.add(u);
            placerEnSalle(q);
            HeralizClashWar.LOG.info("[clashwar] {} vient regarder (pas inscrit)", q.getGameProfile().getName());
            regarder(q, tour, index);
        });
        if (r != null) {
            Annonces.chat(p, Component.translatable("matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.RED));
        }
    }

    /** {@code /clashwar salle} : le joueur qui regardait un match revient en salle d'attente. */
    void revenirEnSalle(ServerPlayer p) {
        if (MatchApi.partieDe(p) instanceof MatchCw m && m.equipeDe(p.getUUID()) == null) m.arreterRegarder(p);
        else Annonces.chat(p, Component.translatable("clashwar.chat.pas_spectateur").withStyle(ChatFormatting.GRAY));
    }

    // ================================================================== affichage
    /** Joueurs connectes presents dans la salle (nouvelle liste). */
    List<ServerPlayer> joueursSalle() {
        List<ServerPlayer> l = new ArrayList<>();
        if (salle == null) return l;
        for (UUID u : participants) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null && MatchApi.partieDe(p) == salle) l.add(p);
        }
        return l;
    }

    /** Bandeau de la salle (titre, tour, compte a rebours de la pause) a tous ses joueurs. */
    void bandeauSalle() {
        List<ServerPlayer> l = joueursSalle();
        if (!l.isEmpty()) Hud.envoyer(l, bandeau());
    }

    void bandeauSalle(ServerPlayer p) {
        Hud.envoyer(p, bandeau());
    }

    private Hud.Bandeau bandeau() {
        Component titre;
        int secondes = -1;
        if (tableau == null) {
            titre = Component.translatable("clashwar.hud.salle", "#" + edition);
        } else if (etat == Etat.FIN) {
            titre = Component.translatable("clashwar.hud.fin", tableau.equipe(tableau.champion()).libelle());
            secondes = CwConfig.get().finSecondes;
        } else {
            int t = tableau.tourCourant();
            titre = Component.translatable("clashwar.hud.titre", Component.translatable("clashwar.tour." + tableau.cleTour(Math.min(t, tableau.nbTours() - 1))));
            if (etat == Etat.PAUSE) secondes = Math.max(0, CwConfig.get().pauseToursSecondes - ticksEtat / 20);
        }
        return new Hud.Bandeau(titre, secondes, List.of(), Component.translatable("clashwar.hud.format", format.nom));
    }

    /** Libelle d'une equipe du tableau en couleur. */
    Component libelle(int index) {
        if (tableau == null || index < 0) return Component.literal("?");
        Tableau.EquipeT e = tableau.equipe(index);
        return Component.literal(e.libelle()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(e.couleur)).withBold(true));
    }

    /** Diffuse les compteurs en direct d'une rencontre a tous les joueurs. */
    void diffuserRencontre(Tableau.Rencontre r) {
        MatchsNet.versTous(new CwPaquets.RencontreS2C(r.tour, r.index, r.statut.ordinal(), r.gagnant, r.vivantsA, r.totalA, r.vivantsB,
                r.totalB, r.forfait));
    }

    /** Vue diffusee aux clients. */
    VueTableau vue(long prochaineMs, String prochainFormat, @Nullable VueTableau.Equipe dernierChampion, int dernierChampionEdition) {
        VueTableau.Etat e = switch (etat) {
            case INSCRIPTION -> VueTableau.Etat.INSCRIPTION;
            case PREPARATION -> VueTableau.Etat.PREPARATION;
            case PAUSE, TOUR -> VueTableau.Etat.EN_COURS;
            case FIN, TERMINE -> VueTableau.Etat.TERMINE;
            case ANNULE -> VueTableau.Etat.ANNULE;
        };
        List<VueTableau.Equipe> equipes = new ArrayList<>();
        List<List<VueTableau.Rencontre>> tours = new ArrayList<>();
        VueTableau.Equipe champion = dernierChampion;
        int championEdition = dernierChampionEdition;
        int tourCourant = 0;
        if (tableau != null && e != VueTableau.Etat.ANNULE) {
            for (Tableau.EquipeT t : tableau.equipes()) equipes.add(vueEquipe(t));
            for (List<Tableau.Rencontre> l : tableau.tours()) {
                List<VueTableau.Rencontre> vl = new ArrayList<>(l.size());
                for (Tableau.Rencontre r : l) {
                    vl.add(new VueTableau.Rencontre(r.a, r.b, r.gagnant, r.statut.ordinal(), r.vivantsA, r.totalA, r.vivantsB, r.totalB, r.forfait));
                }
                tours.add(vl);
            }
            tourCourant = tableau.tourCourant();
            if (tableau.termine()) {
                champion = equipes.get(tableau.champion());
                championEdition = edition;
            }
        }
        return new VueTableau(edition, format.nom, e, finInscriptionMs, prochaineMs, prochainFormat, inscriptions.size(), equipesInscrites(),
                tourCourant, equipes, tours, champion, championEdition, CwConfig.get().zone().getId());
    }

    static VueTableau.Equipe vueEquipe(Tableau.EquipeT t) {
        List<String> noms = new ArrayList<>(t.joueurs.size());
        for (UUID u : t.joueurs) noms.add(t.nom(u));
        return new VueTableau.Equipe(t.libelle(), t.tag, t.nomClan, t.couleur, t.banniere, noms, List.copyOf(t.joueurs), t.eliminee);
    }

    // ================================================================== aides de texte
    static MutableComponent fort(String s) {
        return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xECEEF2)).withBold(true));
    }

    static MutableComponent commande(String cmd) {
        return Component.literal(cmd).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Tournois.ACCENT)).withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd)));
    }
}
