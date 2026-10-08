package fr.heralisz.matchs.clashwar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.ChoixClasse;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.Kits;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.moteur.api.Spectateur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un match du tableau (MATCHS_SPEC §2.4) : deux equipes dans leur instance d'arene (points {@code spawn_a/b}), choix de
 * classe 10 s par l'ecran du moteur ({@link ChoixClasse} : max 1 sniper et 1 pompe par equipe, memes classes pour les
 * deux), kit donne, 10 s de preparation derriere la barriere ({@code barriere_a/b}), combat de 4 min puis mort subite
 * (zone qui se resserre autour de {@code zone_centre}, degats hors zone). La derniere equipe avec un vivant gagne ; a
 * egalite absolue, l'equipe qui a inflige le plus de degats. Elimines = spectateurs de leur equipe ({@link Spectateur}),
 * puis tout le monde retourne en salle d'attente, soigne, kit retire.
 * <p>Un joueur tague en combat (ou absent) quand le match l'appelle le rate ; une equipe sans aucun joueur present
 * perd par forfait. Fil serveur, aucune allocation par tick hors evenements.
 */
public final class MatchCw extends Partie {
    private final Tournoi tournoi;
    private final Tableau.Rencontre rencontre;
    private final Tableau.EquipeT ta, tb;
    private final Equipe ea, eb;
    private final Component nomTour;
    @Nullable
    private Carte carte;
    private boolean appele, kitsDonnes;
    private final Map<UUID, Pose> spawns = new HashMap<>();
    @Nullable
    private AABB barriereA, barriereB;
    private float degatsA, degatsB;
    private double zoneX, zoneZ, zoneY;
    private float rayonDepart, rayonMin;
    private boolean retourFait;
    /** Tampon reutilise (pas d'allocation par tick). */
    private final List<ServerPlayer> tampon = new ArrayList<>();

    MatchCw(Tournoi tournoi, Tableau.Rencontre rencontre, Tableau.EquipeT ta, Tableau.EquipeT tb, Component nomTour) {
        super("clashwar-" + tournoi.edition + "-t" + rencontre.tour + "-m" + rencontre.index, "clashwar", tournoi.serveur());
        this.tournoi = tournoi;
        this.rencontre = rencontre;
        this.ta = ta;
        this.tb = tb;
        this.nomTour = nomTour;
        this.ea = new Equipe("a", ta.libelle(), ta.couleur);
        this.eb = new Equipe("b", tb.libelle(), tb.couleur == ta.couleur ? Tournois.OR : tb.couleur);
        ea.clan(ta.clan);
        eb.clan(tb.clan);
        ajouterEquipe(ea);
        ajouterEquipe(eb);
        List<Carte> cartes = Arenes.cartesDeType(tournoi.format.typeCarte);
        if (!cartes.isEmpty()) carte = cartes.get(tournoi.rnd.nextInt(cartes.size()));
        else HeraliszMatchs.LOG.warn("[clashwar] aucune carte de type {}", tournoi.format.typeCarte);
        reserver();
    }

    private void reserver() {
        if (carte == null || instance() != null) return;
        Instance i = Arenes.reserver(carte.id());
        if (i != null) {
            instance(i);
            lireCarte(i);
        }
    }

    private void lireCarte(Instance i) {
        barriereA = boite(i.points("barriere_a"));
        barriereB = boite(i.points("barriere_b"));
        Pose c = i.point("zone_centre");
        AABB b = i.bornes();
        zoneX = c != null ? c.x() : b.getCenter().x;
        zoneZ = c != null ? c.z() : b.getCenter().z;
        zoneY = c != null ? c.y() : b.minY + 1;
        rayonDepart = (float) Math.max(b.getXsize(), b.getZsize()) * 0.75f;
        rayonMin = 4f;
        JsonObject m = i.carte().meta();
        if (m.has("zone") && m.get("zone").isJsonObject()) {
            JsonObject z = m.getAsJsonObject("zone");
            if (z.has("centre")) {
                JsonArray a = z.getAsJsonArray("centre");
                zoneX = i.origine().getX() + a.get(0).getAsDouble();
                zoneY = i.origine().getY() + a.get(1).getAsDouble();
                zoneZ = i.origine().getZ() + a.get(2).getAsDouble();
            }
            if (z.has("rayon_depart")) rayonDepart = z.get("rayon_depart").getAsFloat();
            if (z.has("rayon_min")) rayonMin = z.get("rayon_min").getAsFloat();
        }
    }

    /** Boite de deux coins (blocs inclus, 3 blocs de haut au-dessus du plus haut), ou null. */
    @Nullable
    private static AABB boite(List<Pose> coins) {
        if (coins.size() < 2) return null;
        Pose p = coins.get(0), q = coins.get(1);
        double x0 = Math.floor(Math.min(p.x(), q.x())), y0 = Math.floor(Math.min(p.y(), q.y())), z0 = Math.floor(Math.min(p.z(), q.z()));
        double x1 = Math.floor(Math.max(p.x(), q.x())) + 1, y1 = Math.floor(Math.max(p.y(), q.y())) + 3, z1 = Math.floor(Math.max(p.z(), q.z())) + 1;
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    Tableau.Rencontre rencontre() {
        return rencontre;
    }

    // ================================================================== deroule
    @Override
    public void tick() {
        CwConfig cfg = CwConfig.get();
        switch (phase()) {
            case ATTENTE -> {
                Instance i = instance();
                if (i == null) {
                    if (ticksPhase() % 100 == 0) reserver();
                    if (ticksPhase() > cfg.attenteArene * 20) forfaitTechnique();
                } else if (!appele && i.prete() && ticksPhase() >= delaiAppel()) {
                    appeler(i);
                } else if (!appele && ticksPhase() > cfg.attenteArene * 20) {
                    forfaitTechnique();
                }
            }
            case CHOIX -> {
                retenir();
                // filet : l'ecran du moteur aurait du rendre la main
                if (ticksPhase() >= (cfg.choixSecondes + 5) * 20) {
                    ChoixClasse.annuler(this);
                    finChoix(Map.of());
                }
            }
            case PREPARATION -> {
                retenir();
                if (ticksPhase() >= cfg.preparationSecondes * 20) debutCombat();
            }
            case COMBAT -> {
                if (ticksPhase() >= cfg.combatSecondes * 20) mortSubite();
            }
            case PROLONGATION -> {
                if (ticksPhase() % 20 == 0) degatsZone();
                if (ticksPhase() >= (cfg.mortSubiteSecondes + cfg.limiteSecondes) * 20) departager();
            }
            case FIN -> {
                if (!retourFait && ticksPhase() >= cfg.retourSecondes * 20) {
                    retourFait = true;
                    retourSalle();
                    Equipe g = gagnante();
                    tournoi.resultat(this, rencontre, g == eb ? rencontre.b : rencontre.a, false);
                    fermer();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Ticks minimum entre le lancement du tour et l'appel : le temps de l'annonce du tour (3 s), de la finale annoncee a
     * tout le serveur (6 s). Sinon l'ecran de choix s'ouvrait dans le meme tick et la cachait (arene deja posee).
     */
    private int delaiAppel() {
        Tableau tab = tournoi.tableau;
        return tab != null && rencontre.tour == tab.nbTours() - 1 ? 120 : 60;
    }

    /** L'arene n'a pas pu etre posee a temps : la rencontre est tranchee au hasard (signale). */
    private void forfaitTechnique() {
        HeraliszMatchs.LOG.warn("[clashwar] {} : arene {} indisponible, rencontre tranchee au hasard", id(), carte == null ? "?" : carte.id());
        Annonces.chat(serveur(), Component.translatable("clashwar.chat.arene_indisponible").withStyle(ChatFormatting.GRAY));
        terminerSansJeu(tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b);
    }

    /** Rencontre tranchee sans jouer (forfait) : personne n'a ete rattache a ce match. */
    private void terminerSansJeu(int gagnant) {
        terminerSansJeu(gagnant, true);
    }

    private void terminerSansJeu(int gagnant, boolean forfait) {
        phase(Phase.TERMINEE);
        MatchApi.retirer(this);
        if (instance() != null) Arenes.liberer(instance());
        tournoi.resultat(this, rencontre, gagnant, forfait);
    }

    /** Banc d'essai : vrai si l'equipe compte des mannequins (joueurs factices). */
    private boolean aDesMannequins(Tableau.EquipeT t) {
        if (tournoi.mannequins.isEmpty()) return false;
        for (UUID u : t.joueurs) if (tournoi.mannequins.contains(u)) return true;
        return false;
    }

    /** Banc d'essai : les mannequins de l'equipe entrent dans le match (vivants, sans corps ni teleportation). */
    private void entrerMannequins(Tableau.EquipeT t, Equipe e) {
        for (UUID u : t.joueurs) if (tournoi.mannequins.contains(u)) e.ajouter(u);
    }

    /** Appel du match : joueurs presents vers leurs points d'apparition, ecran de choix de classe. */
    private void appeler(Instance i) {
        appele = true;
        List<ServerPlayer> pa = presents(ta), pb = presents(tb);
        // banc d'essai : un camp sans joueur reel joue avec ses mannequins, seulement face a un vrai joueur
        boolean mannA = pa.isEmpty() && !pb.isEmpty() && aDesMannequins(ta);
        boolean mannB = pb.isEmpty() && !pa.isEmpty() && aDesMannequins(tb);
        if ((pa.isEmpty() && !mannA) || (pb.isEmpty() && !mannB)) {
            boolean deuxFactices = pa.isEmpty() && pb.isEmpty() && aDesMannequins(ta) && aDesMannequins(tb);
            int gagnant = pa.isEmpty() && pb.isEmpty() ? (tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b) : pa.isEmpty() ? rencontre.b : rencontre.a;
            if (deuxFactices) {
                // deux equipes factices : resultat tire comme dans la simulation (pas un forfait)
                int taille = tournoi.format.taille, restants = 1 + tournoi.rnd.nextInt(taille);
                rencontre.totalA = rencontre.totalB = taille;
                rencontre.vivantsA = gagnant == rencontre.a ? restants : 0;
                rencontre.vivantsB = gagnant == rencontre.b ? restants : 0;
            }
            terminerSansJeu(gagnant, !deuxFactices);
            return;
        }
        entrer(i, pa, ea, i.points("spawn_a"));
        entrer(i, pb, eb, i.points("spawn_b"));
        if (mannA) entrerMannequins(ta, ea);
        if (mannB) entrerMannequins(tb, eb);
        rencontre.totalA = rencontre.vivantsA = ea.nbVivants();
        rencontre.totalB = rencontre.vivantsB = eb.nbVivants();
        tournoi.diffuserRencontre(rencontre);
        phase(Phase.CHOIX);
        List<ServerPlayer> joueurs = joueursEnLigne();
        Annonces.grande(joueurs, new Annonces.Annonce(nomTour, Component.translatable("clashwar.annonce.contre", ta.libelle(), tb.libelle()),
                Tournois.ACCENT, CwSons.id(CwSons.APPEL), 60, Annonces.Style.NORMALE));
        // memes classes pour les deux equipes ; limites (sniper, pompe) verifiees par equipe cote serveur
        List<Kit> kits = new ArrayList<>();
        for (String k : CwConfig.get().kits) {
            Kit kit = Kits.get(k);
            if (kit != null) kits.add(kit);
            else HeraliszMatchs.LOG.warn("[clashwar] kit {} introuvable (data/heralisz_matchs/kits)", k);
        }
        Kit defaut = Kits.get(CwConfig.get().kitDefaut);
        if (defaut != null && kits.remove(defaut)) kits.add(0, defaut);
        int s = CwConfig.get().choixSecondes;
        bandeau(s);
        ChoixClasse.ouvrir(this, joueurs, kits, CwConfig.get().limites, s,
                Component.translatable("clashwar.choix.titre", nomTour), this::finChoix);
        tournoi.bandeauSalle();
    }

    /** Joueurs de l'equipe presents en salle, connectes et non tagues ; les autres ratent ce match (prevenus). */
    private List<ServerPlayer> presents(Tableau.EquipeT t) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : t.joueurs) {
            if (!tournoi.participant(u)) continue;
            ServerPlayer p = joueur(u);
            if (p == null || MatchApi.partieDe(p) != tournoi.salle) continue;
            if (CombatTag.estTague(p)) {
                Annonces.chat(p, Component.translatable("clashwar.chat.rate_combat").withStyle(ChatFormatting.RED));
                continue;
            }
            l.add(p);
        }
        return l;
    }

    private void entrer(Instance i, List<ServerPlayer> joueurs, Equipe e, List<Pose> points) {
        for (int k = 0; k < joueurs.size(); k++) {
            ServerPlayer p = joueurs.get(k);
            e.ajouter(p.getUUID());
            MatchApi.rejoindre(p, this);
            Pose pose = points.isEmpty() ? new Pose(i.bornes().getCenter().x, i.bornes().minY + 1, i.bornes().getCenter().z, 0)
                    : points.get(k % points.size());
            spawns.put(p.getUUID(), pose);
            p.setCamera(p);
            p.setGameMode(GameType.SURVIVAL);
            p.removeAllEffects();
            p.clearFire();
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            pose.teleporter(p, i.niveau());
        }
    }

    /** Pendant le choix et la preparation : chaque joueur reste derriere la barriere de son camp. */
    private void retenir() {
        Instance i = instance();
        if (i == null) return;
        retenir(ea, barriereA, i);
        retenir(eb, barriereB, i);
    }

    private void retenir(Equipe e, @Nullable AABB barriere, Instance i) {
        for (UUID u : e.vivants()) {
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            Pose s = spawns.get(u);
            if (s == null) continue;
            boolean dehors = barriere != null ? !barriere.contains(p.getX(), p.getY(), p.getZ()) : s.distanceCarre(p.getX(), p.getY(), p.getZ()) > 2.25;
            if (dehors) p.teleportTo(i.niveau(), s.x(), s.y(), s.z(), p.getYRot(), p.getXRot());
        }
    }

    /** Fin du choix (ecran du moteur) : kits donnes, preparation. */
    private void finChoix(Map<UUID, Kit> choix) {
        if (phase() != Phase.CHOIX || kitsDonnes) return;
        kitsDonnes = true;
        for (Map.Entry<UUID, Kit> e : choix.entrySet()) {
            Equipe eq = equipeDe(e.getKey());
            ServerPlayer p = joueur(e.getKey());
            if (p == null || eq == null || !eq.estVivant(e.getKey())) continue;
            if (!Equipement.donner(p, e.getValue(), id())) {
                HeraliszMatchs.LOG.warn("[clashwar] {} : kit {} non donne a {}", id(), e.getValue().id(), p.getGameProfile().getName());
            }
        }
        phase(Phase.PREPARATION);
        int s = CwConfig.get().preparationSecondes;
        Annonces.compteARebours(joueursEnLigne(), s, Component.translatable("clashwar.annonce.preparation"));
        bandeau(s);
    }

    private void debutCombat() {
        phase(Phase.COMBAT);
        // le compte a rebours du moteur affiche deja « COMBAT ! » a zero : une grande annonce le doublait (deux textes
        // superposes) ; on ne garde que le son du ClashWar
        for (ServerPlayer p : joueursEnLigne()) p.playNotifySound(CwSons.COMBAT.get(), SoundSource.MASTER, 1f, 1f);
        bandeau(CwConfig.get().combatSecondes);
    }

    /** Fin du temps : la zone commence a se resserrer. */
    private void mortSubite() {
        phase(Phase.PROLONGATION);
        List<ServerPlayer> joueurs = joueursEnLigne();
        MatchsNet.versJoueurs(joueurs, new CwPaquets.ZoneS2C(true, zoneX, zoneZ, zoneY, rayonDepart, rayonMin, CwConfig.get().mortSubiteSecondes * 20));
        Annonces.grande(joueurs, new Annonces.Annonce(Component.translatable("clashwar.annonce.mort_subite"),
                Component.translatable("clashwar.annonce.mort_subite.sous"), 0xFF6B6B, CwSons.id(CwSons.ZONE), 60, Annonces.Style.DEFAITE));
        Hud.envoyer(this, titre(), -1, Component.translatable("clashwar.hud.mort_subite"));
    }

    /** Rayon courant de la zone. */
    private float rayon() {
        float t = Math.min(1f, ticksPhase() / (CwConfig.get().mortSubiteSecondes * 20f));
        return rayonDepart + (rayonMin - rayonDepart) * t;
    }

    private void degatsZone() {
        float r = rayon(), r2 = r * r;
        float degats = CwConfig.get().degatsZone;
        for (int k = 0; k < equipes.size(); k++) {
            tampon.clear();
            for (UUID u : equipes.get(k).vivants()) {
                ServerPlayer p = joueur(u);
                if (p == null) continue;
                double dx = p.getX() - zoneX, dz = p.getZ() - zoneZ;
                if (dx * dx + dz * dz > r2) tampon.add(p);
            }
            // degats hors de la boucle sur les vivants (une elimination modifie l'ensemble)
            for (int i = 0; i < tampon.size(); i++) tampon.get(i).hurt(tampon.get(i).damageSources().outOfBorder(), degats);
            tampon.clear();
        }
    }

    /** Temps vraiment ecoule : le plus de vivants gagne, puis le plus de degats infliges. */
    private void departager() {
        if (ea.nbVivants() != eb.nbVivants()) terminer(ea.nbVivants() > eb.nbVivants() ? ea : eb);
        else terminer(degatsA >= degatsB ? ea : eb);
    }

    // ================================================================== evenements du moteur
    @Override
    public float surDegats(ServerPlayer victime, @Nullable LivingEntity attaquant, DamageSource source, float montant) {
        if (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION) return 0f;
        Equipe ev = equipeDe(victime.getUUID());
        if (ev == null || !ev.estVivant(victime.getUUID())) return 0f;
        if (attaquant instanceof ServerPlayer ap) {
            Equipe et = equipeDe(ap.getUUID());
            if (et == ev) return 0f;
            float utile = Math.min(montant, victime.getHealth() + victime.getAbsorptionAmount());
            if (et == ea) degatsA += utile;
            else if (et == eb) degatsB += utile;
        }
        return montant;
    }

    @Override
    public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        UUID u = victime.getUUID();
        Equipe e = equipeDe(u);
        victime.setHealth(victime.getMaxHealth());
        victime.clearFire();
        Instance i = instance();
        if (e == null || !e.estVivant(u) || (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION)) {
            // pas de vraie elimination (choix, preparation, spectateur) : retour a sa place
            Pose s = spawns.get(u);
            if (i != null && e != null && e.estVivant(u) && s != null) s.teleporter(victime, i.niveau());
            return;
        }
        String arme = "", nomTueur = "";
        int couleurTueur = 0xFFFFFF;
        if (tueur != null) {
            nomTueur = tueur.getName().getString();
            ItemStack main = tueur.getMainHandItem();
            if (!main.isEmpty()) arme = main.getHoverName().getString();
            if (tueur instanceof ServerPlayer tp) {
                Equipe et = equipeDe(tp.getUUID());
                if (et != null) couleurTueur = et.couleur();
            }
        }
        KillFeed.ajouter(this, new KillFeed.Ligne(nomTueur, couleurTueur, victime.getGameProfile().getName(), e.couleur(), arme, false));
        e.eliminer(u);
        victime.removeAllEffects();
        victime.playNotifySound(CwSons.ELIMINATION.get(), SoundSource.MASTER, 1f, 1f);
        Spectateur.suivre(victime, this);
        majCompteurs();
        bandeau(phase() == Phase.COMBAT ? Math.max(0, CwConfig.get().combatSecondes - ticksPhase() / 20) : -1);
        verifierFin();
    }

    /**
     * Banc d'essai ({@code /clashwar banc eliminer}) : le joueur abat un mannequin vivant du camp adverse (fil des
     * eliminations, compteurs du tableau, fin du match si c'etait le dernier). Faux hors combat ou sans mannequin.
     */
    boolean eliminerMannequin(ServerPlayer tueur) {
        if (phase() != Phase.COMBAT && phase() != Phase.PROLONGATION) return false;
        Equipe et = equipeDe(tueur.getUUID());
        if (et == null || !et.estVivant(tueur.getUUID())) return false;
        Equipe ev = et == ea ? eb : ea;
        Tableau.EquipeT tv = ev == ea ? ta : tb;
        UUID cible = null;
        for (UUID u : ev.vivants()) {
            if (tournoi.mannequins.contains(u)) {
                cible = u;
                break;
            }
        }
        if (cible == null) return false;
        ItemStack main = tueur.getMainHandItem();
        KillFeed.ajouter(this, new KillFeed.Ligne(tueur.getGameProfile().getName(), et.couleur(), tv.nom(cible), ev.couleur(),
                main.isEmpty() ? "" : main.getHoverName().getString(), true));
        if (et == ea) degatsA += 20f;
        else degatsB += 20f;
        ev.eliminer(cible);
        tueur.playNotifySound(CwSons.ELIMINATION.get(), SoundSource.MASTER, 0.8f, 1.2f);
        majCompteurs();
        bandeau(phase() == Phase.COMBAT ? Math.max(0, CwConfig.get().combatSecondes - ticksPhase() / 20) : -1);
        verifierFin();
        return true;
    }

    private void verifierFin() {
        if (phase() == Phase.FIN || phase() == Phase.TERMINEE) return;
        boolean a = ea.enVie(), b = eb.enVie();
        if (a && b) return;
        if (a) terminer(ea);
        else if (b) terminer(eb);
        else terminer(degatsA >= degatsB ? ea : eb);
    }

    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        Equipe e = equipeDe(joueur.getUUID());
        if (e == null || !e.estVivant(joueur.getUUID()) || phase() == Phase.FIN || phase() == Phase.TERMINEE) return;
        KillFeed.ajouter(this, new KillFeed.Ligne("", 0xFFFFFF, joueur.getGameProfile().getName(), e.couleur(), "", false));
        e.eliminer(joueur.getUUID());
        majCompteurs();
        if (phase() != Phase.ATTENTE) verifierFin();
    }

    @Override
    public boolean surReconnexion(ServerPlayer joueur) {
        // revenu pendant le match : il ne le rejoue pas, il attend en salle
        if (!tournoi.actif() || tournoi.salle == null || !tournoi.participant(joueur.getUUID())) return false;
        Spectateur.arreter(joueur, false);
        Equipement.retirer(joueur);
        tournoi.placerEnSalle(joueur);
        return true;
    }

    @Override
    public void surSortieBornes(ServerPlayer joueur) {
        UUID u = joueur.getUUID();
        Equipe e = equipeDe(u);
        Instance i = instance();
        if (e == null || !e.estVivant(u)) {
            Pose s = i == null ? null : i.point("spectateur");
            if (s != null) s.teleporter(joueur, i.niveau());
            return;
        }
        if (phase() == Phase.COMBAT || phase() == Phase.PROLONGATION) {
            surElimination(joueur, null, joueur.damageSources().fellOutOfWorld());
        } else if (i != null && spawns.containsKey(u)) {
            spawns.get(u).teleporter(joueur, i.niveau());
        }
    }

    // ================================================================== fin
    @Override
    protected void surFin(@Nullable Equipe gagnante) {
        List<ServerPlayer> joueurs = joueursEnLigne();
        MatchsNet.versJoueurs(joueurs, CwPaquets.ZoneS2C.AUCUNE);
        Component contre = Component.translatable("clashwar.annonce.contre", ta.libelle(), tb.libelle());
        for (ServerPlayer p : joueurs) {
            boolean gagne = equipeDe(p.getUUID()) == gagnante;
            Annonces.grande(p, new Annonces.Annonce(Component.translatable(gagne ? "clashwar.annonce.victoire" : "clashwar.annonce.defaite"),
                    contre, gagne ? Tournois.OR : 0xFF6B6B, CwSons.id(gagne ? CwSons.VICTOIRE : CwSons.DEFAITE), 80,
                    gagne ? Annonces.Style.VICTOIRE : Annonces.Style.DEFAITE));
        }
        Hud.envoyer(this, titre(), CwConfig.get().retourSecondes, Component.translatable("clashwar.hud.vainqueur",
                gagnante != null ? gagnante.nom() : "?"));
        HeraliszMatchs.LOG.info("[clashwar] {} : {} gagne (degats {} / {})", id(), gagnante != null ? gagnante.nom() : "?", degatsA, degatsB);
    }

    /** Tout le monde retourne en salle d'attente : spectateur arrete, kit retire, soigne. */
    private void retourSalle() {
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            Spectateur.arreter(p, false);
            Equipement.retirer(p);
            tournoi.placerEnSalle(p);
        }
        spectateurs.clear();
    }

    /** Annulation de l'edition : renvoie les joueurs en salle (ils seront rendus par le tournoi) et ferme. */
    void annuler() {
        if (phase() == Phase.TERMINEE) return;
        ChoixClasse.annuler(this);
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            Spectateur.arreter(p, false);
            Equipement.retirer(p);
            if (tournoi.salle != null) tournoi.placerEnSalle(p);
        }
        fermer();
    }

    // ================================================================== affichage
    private Component titre() {
        return Component.translatable("clashwar.hud.titre", nomTour);
    }

    private void bandeau(int secondes) {
        Hud.envoyer(this, titre(), secondes, Component.translatable("clashwar.hud.contre", ta.libelle(), tb.libelle()));
    }

    /** Compteurs en direct du tableau (vivants / engages). */
    private void majCompteurs() {
        int va = ea.nbVivants(), vb = eb.nbVivants();
        if (va == rencontre.vivantsA && vb == rencontre.vivantsB) return;
        rencontre.vivantsA = va;
        rencontre.vivantsB = vb;
        tournoi.diffuserRencontre(rencontre);
    }
}
