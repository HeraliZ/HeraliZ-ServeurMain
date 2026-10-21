package fr.heralisz.matchs.clashwar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.clashwar.bloc.MurTableau;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.clashwar.net.VueTableau;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
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
    final Random rnd = new Random();
    private final Map<UUID, Long> cadence = new HashMap<>();

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
        switch (etat) {
            case INSCRIPTION -> {
                if (simulation) return;
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
                Inscription avant = inscriptions.get(clan);
                boolean dejaInscrit = avant != null && avant.contient(u);
                if (p == null && !dejaInscrit) {
                    refus(sp, "clashwar.inscription.hors_ligne");
                    return;
                }
                if (MatchApi.enMatch(u)) {
                    refus(sp, "clashwar.inscription.en_match");
                    return;
                }
                l.add(u);
            }
            equipes.add(l);
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
        HeraliszMatchs.LOG.info("[clashwar] inscription de {} par {} : {}", info.tag(), sp.getGameProfile().getName(), equipes);
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

    private void refus(ServerPlayer sp, String cle) {
        Optional<UUID> clan = ClansApi.clanDe(sp.getUUID());
        Optional<ClanInfo> info = clan.flatMap(ClansApi::infos);
        if (info.isPresent()) envoyerInscription(sp, info.get(), false, cle, true);
        else Annonces.chat(sp, Component.translatable(cle).withStyle(ChatFormatting.RED));
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
        instanceSalle.quandPrete(this::entrerSalle);
    }

    /** Salle prete : chaque inscrit passe par le Coffre puis y est teleporte ; le tableau est tire. */
    private void entrerSalle() {
        if (etat != Etat.PREPARATION || instanceSalle == null) return;
        salle = new PartieSalle(this, "clashwar-" + edition + "-salle");
        salle.instance(instanceSalle);
        MatchApi.enregistrer(salle);
        poserMur(instanceSalle);
        List<Tableau.EquipeT> finales = new ArrayList<>();
        for (Tableau.EquipeT e : provisoires) {
            List<UUID> ok = new ArrayList<>();
            for (UUID u : e.joueurs) {
                if (mannequins.contains(u)) {
                    // banc d'essai : joueur factice, ni connecte ni Coffre
                    ok.add(u);
                    continue;
                }
                ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                if (p == null) continue;
                if (CombatTag.estTague(p)) {
                    Annonces.chat(p, Component.translatable("clashwar.chat.retire_combat").withStyle(ChatFormatting.RED));
                    continue;
                }
                Coffre.Refus r = Coffre.refus(p);
                if (r != null) {
                    Annonces.chat(p, Component.translatable("clashwar.chat.retire_coffre",
                            Component.translatable("matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.RED));
                    continue;
                }
                if (!Coffre.deposer(p, "clashwar")) {
                    Annonces.chat(p, Component.translatable("clashwar.chat.retire_coffre",
                            Component.translatable("matchs.coffre.refus.erreur")).withStyle(ChatFormatting.RED));
                    continue;
                }
                participants.add(u);
                ok.add(u);
                placerEnSalle(p);
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
                HeraliszMatchs.LOG.warn("[clashwar] mur-tableau non pose dans {} (place occupee)", i);
            }
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[clashwar] tableau_mur illisible pour {} : {}", i.carte().id(), ex.toString());
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
        HeraliszMatchs.LOG.info("[clashwar] edition {} : tour {} ({}) lance, {} matchs", edition, t, tableau.cleTour(t), lances);
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
            HeraliszMatchs.LOG.info("[clashwar] simulation : vainqueur {} ({})", ch.libelle(), noms);
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
        bandeauSalle();
        HeraliszMatchs.LOG.info("[clashwar] edition {} : vainqueur {} ({})", edition, ch.libelle(), noms);
    }

    /** Annule l'edition (raison = suffixe de {@code clashwar.annule.*}) et rend tout le monde. */
    void annuler(String raison) {
        if (!actif()) return;
        HeraliszMatchs.LOG.info("[clashwar] edition {} annulee : {}", edition, raison);
        for (MatchCw m : new ArrayList<>(matchs)) m.annuler();
        matchs.clear();
        rendreTout();
        etat(Etat.ANNULE);
        Annonces.chat(serveur, Component.translatable("clashwar.chat.annule", Component.translatable("clashwar.annule." + raison))
                .withStyle(ChatFormatting.GRAY));
    }

    /** Rend le Coffre a chaque participant (les deconnectes le recuperent a leur retour) et ferme la salle. */
    private void rendreTout() {
        for (UUID u : new ArrayList<>(participants)) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) rendreJoueur(p);
            else MatchApi.quitter(u);
        }
        participants.clear();
        for (MatchCw m : new ArrayList<>(matchs)) m.fermer();
        matchs.clear();
        if (salle != null) salle.fermer();
        else if (instanceSalle != null) Arenes.liberer(instanceSalle);
        instanceSalle = null;
    }

    /** Rend son Coffre a un joueur (position d'origine) et le detache ; donne ses cles de caisse en attente. */
    void rendreJoueur(ServerPlayer p) {
        Equipement.retirer(p);
        p.setCamera(p);
        Hud.effacer(p);
        MatchsNet.versJoueur(p, new CwPaquets.ZoneS2C(false, 0, 0, 0, 0, 0, 0));
        if (salle != null) salle.retirer(p.getUUID());
        if (!Coffre.rendre(p)) {
            HeraliszMatchs.LOG.warn("[clashwar] Coffre.rendre({}) a echoue : le depot reste sur disque", p.getGameProfile().getName());
            if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) p.setGameMode(GameType.SURVIVAL);
        }
        MatchApi.quitter(p);
        participants.remove(p.getUUID());
        Recompenses.donnerCles(p);
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
     */
    void regarder(ServerPlayer p, int tour, int index) {
        UUID u = p.getUUID();
        if (!participants.contains(u) || salle == null) {
            Annonces.chat(p, Component.translatable("clashwar.chat.pas_participant"));
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
                tourCourant, equipes, tours, champion, championEdition);
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
