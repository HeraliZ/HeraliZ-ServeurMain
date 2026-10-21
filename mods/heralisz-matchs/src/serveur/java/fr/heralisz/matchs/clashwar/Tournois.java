package fr.heralisz.matchs.clashwar;

import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.clashwar.net.VueTableau;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Chef d'orchestre du ClashWar (fil serveur) : planification (annonce a H-15 min, inscriptions de H-10 min a H, rappels
 * a -5 et -1 min, MATCHS_SPEC §2.1), edition en cours ({@link Tournoi}), diffusion de l'etat aux clients (tableau mural,
 * ecrans) au plus deux fois par seconde, cles de caisse en attente, entrees des paquets C2S.
 */
public final class Tournois {
    private Tournois() {
    }

    /** Couleurs de la charte (accent bleu HeraliZ, or du sacre). */
    public static final int ACCENT = 0x6FA8FF, OR = 0xE8C36A;

    @Nullable
    private static MinecraftServer serveur;
    @Nullable
    private static Tournoi courant;
    private static long creneauAnnonce, creneauOuvert;
    private static boolean vueSale = true;
    private static long derniereDiffusion;
    @Nullable
    private static VueTableau vue;

    @Nullable
    public static Tournoi courant() {
        return courant;
    }

    // ================================================================== cycle de vie du serveur
    static void demarrage(MinecraftServer s) {
        serveur = s;
        courant = null;
        vue = null;
        vueSale = true;
        creneauAnnonce = creneauOuvert = 0;
        CwConfig.charger(s);
        CwEtat.charger(s);
        CwClassement.charger(s);
    }

    static void arret() {
        // les Coffres restent sur disque : rendus a la reconnexion (aucune partie active apres redemarrage)
        serveur = null;
        courant = null;
        vue = null;
    }

    static void tick(MinecraftServer s) {
        if (serveur != s) return;
        if (courant != null && courant.actif()) courant.tick();
        int t = s.getTickCount();
        if (t % 20 == 0) planifier();
        if (t % 100 == 0) clesEnAttente(s);
        if (vueSale && System.currentTimeMillis() - derniereDiffusion >= 500) diffuser();
    }

    // ================================================================== planification
    /** Prochain creneau (debut des matchs = fin des inscriptions) strictement apres maintenant. */
    static ZonedDateTime prochainCreneau(ZonedDateTime maintenant) {
        CwConfig cfg = CwConfig.get();
        for (int j = 0; j <= 7; j++) {
            LocalDate d = maintenant.toLocalDate().plusDays(j);
            if (!cfg.jours.contains(d.getDayOfWeek())) continue;
            ZonedDateTime c = d.atTime(cfg.heure, cfg.minute).atZone(maintenant.getZone());
            if (c.isAfter(maintenant)) return c;
        }
        return maintenant.plusDays(8);
    }

    /** Format de la prochaine edition planifiee (sans avancer la rotation). */
    static Format prochainFormat() {
        CwConfig cfg = CwConfig.get();
        if (cfg.formatFixe != null) return cfg.formatFixe;
        return cfg.rotation.get(Math.floorMod(CwEtat.rotation, cfg.rotation.size()));
    }

    private static Format prendreFormat() {
        Format f = prochainFormat();
        if (CwConfig.get().formatFixe == null) {
            CwEtat.rotation++;
        }
        return f;
    }

    private static void planifier() {
        if (serveur == null) return;
        // une simulation du banc n'empeche jamais une vraie edition
        if (courant != null && courant.actif() && !courant.simulation) return;
        CwConfig cfg = CwConfig.get();
        ZonedDateTime now = ZonedDateTime.now(cfg.zone());
        ZonedDateTime h = prochainCreneau(now);
        long hMs = h.toInstant().toEpochMilli();
        long reste = hMs - now.toInstant().toEpochMilli();
        if (reste <= cfg.inscriptionMinutes * 60_000L) {
            if (creneauOuvert != hMs) {
                creneauOuvert = hMs;
                creneauAnnonce = hMs;
                ouvrir(prendreFormat(), hMs, false);
            }
        } else if (reste <= cfg.annonceMinutes * 60_000L && creneauAnnonce != hMs) {
            creneauAnnonce = hMs;
            Format f = prochainFormat();
            Annonces.chat(serveur, Component.translatable("clashwar.chat.annonce", Tournoi.fort(f.nom), Tournoi.fort(heure(hMs)),
                    Tournoi.fort(String.valueOf(cfg.inscriptionMinutes))));
            MatchsNet.versTous(new CwPaquets.ToastS2C(Component.translatable("clashwar.toast.annonce"),
                    Component.translatable("clashwar.toast.annonce.texte", f.nom, heure(hMs)), ACCENT, CwSons.id(CwSons.ANNONCE)));
            marquerVue();
        }
    }

    /** Ouvre une edition : inscriptions jusqu'a {@code finMs}. */
    static Tournoi ouvrir(Format format, long finMs, boolean simulation) {
        CwEtat.edition++;
        if (serveur != null) CwEtat.sauver(serveur);
        courant = new Tournoi(serveur, CwEtat.edition, format, finMs, simulation);
        HeraliszMatchs.LOG.info("[clashwar] edition {} ouverte : format {}, cloture {}", CwEtat.edition, format.nom, heure(finMs));
        if (!simulation) courant.annoncerOuverture();
        marquerVue();
        return courant;
    }

    /** /clashwar lancer : inscriptions ouvertes tout de suite (format donne ou suivant de la rotation). */
    static boolean lancer(@Nullable Format format, int minutes) {
        if (courant != null && courant.actif() && !courant.simulation) return false;
        Format f = format != null ? format : prendreFormat();
        ouvrir(f, System.currentTimeMillis() + Math.max(1, minutes) * 60_000L, false);
        return true;
    }

    /** Remplace l'edition courante par une simulation (banc d'essai). */
    static void simulation(Tournoi t) {
        if (courant != null && courant.actif() && !courant.simulation) return;
        courant = t;
        marquerVue();
    }

    // ================================================================== diffusion
    public static void marquerVue() {
        vueSale = true;
    }

    /** Vue courante (construite a la demande). */
    static VueTableau vue() {
        if (vue == null || vueSale) vue = construireVue();
        return vue;
    }

    private static VueTableau construireVue() {
        CwConfig cfg = CwConfig.get();
        ZonedDateTime now = ZonedDateTime.now(cfg.zone());
        long prochaine = prochainCreneau(now).toInstant().toEpochMilli();
        String prochainFormat = prochainFormat().nom;
        VueTableau.Equipe dernier = null;
        int dernierEdition = 0;
        if (CwEtat.champion != null) {
            CwEtat.Champion c = CwEtat.champion;
            dernier = new VueTableau.Equipe(c.numero() > 0 ? "[" + c.tag() + "]" : c.tag(), c.tag(), c.nom(), c.couleur(), c.banniere(),
                    List.of(), List.of(), false);
            dernierEdition = c.edition();
        }
        if (courant == null) {
            return new VueTableau(CwEtat.edition, "", VueTableau.Etat.AUCUN, 0, prochaine, prochainFormat, 0, 0, 0, List.of(), List.of(),
                    dernier, dernierEdition);
        }
        return courant.vue(prochaine, prochainFormat, dernier, dernierEdition);
    }

    private static void diffuser() {
        vueSale = true;
        VueTableau v = vue();
        vueSale = false;
        derniereDiffusion = System.currentTimeMillis();
        MatchsNet.versTous(new CwPaquets.TableauS2C(v));
    }

    /** Classement des clans (10 premiers) tel que le montre le panneau du spawn. */
    static CwPaquets.ClassementS2C classement() {
        List<CwPaquets.LigneClassement> l = new java.util.ArrayList<>();
        for (CwClassement.Ligne x : CwClassement.meilleurs(10)) {
            l.add(new CwPaquets.LigneClassement(x.tag, x.nom, x.couleur, x.titres, x.matchsGagnes, x.matchs, x.eliminations, x.morts));
        }
        CwConfig cfg = CwConfig.get();
        long prochaine = prochainCreneau(ZonedDateTime.now(cfg.zone())).toInstant().toEpochMilli();
        CwEtat.Champion c = CwEtat.champion;
        return new CwPaquets.ClassementS2C(l, prochaine, c == null ? "" : c.tag(), c == null ? 0 : c.edition());
    }

    /** Classement a tous les joueurs (apres chaque match et chaque edition). */
    static void diffuserClassement(MinecraftServer s) {
        MatchsNet.versTous(classement());
    }

    // ================================================================== joueurs
    static void connexion(ServerPlayer p) {
        MatchsNet.versJoueur(p, new CwPaquets.TableauS2C(vue()));
        MatchsNet.versJoueur(p, classement());
    }

    private static void clesEnAttente(MinecraftServer s) {
        if (CwEtat.CLES.isEmpty()) return;
        for (UUID u : List.copyOf(CwEtat.CLES.keySet())) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) Recompenses.donnerCles(p);
        }
    }

    /** /clashwar : ecran d'inscription pendant les inscriptions (membre d'un clan), sinon le tableau. */
    static void ouvrirEcran(ServerPlayer p) {
        Tournoi t = courant;
        if (t != null && t.etat == Tournoi.Etat.INSCRIPTION && !t.simulation) {
            Optional<ClanInfo> info = ClansApi.clanDe(p.getUUID()).flatMap(ClansApi::infos);
            if (info.isPresent()) {
                t.envoyerInscription(p, info.get(), true, "", false);
                return;
            }
            Annonces.chat(p, Component.translatable("clashwar.inscription.sans_clan").withStyle(ChatFormatting.GRAY));
        }
        MatchsNet.versJoueur(p, new CwPaquets.TableauS2C(vue()));
        MatchsNet.versJoueur(p, new CwPaquets.OuvrirS2C("tableau"));
    }

    // ================================================================== entrees des paquets C2S
    public static void inscrire(ServerPlayer p, List<List<UUID>> equipes) {
        Tournoi t = courant;
        if (t == null || !t.actif() || t.simulation) {
            Annonces.chat(p, Component.translatable("clashwar.inscription.fermee").withStyle(ChatFormatting.RED));
            return;
        }
        t.inscrire(p, equipes);
    }

    public static void retirer(ServerPlayer p) {
        Tournoi t = courant;
        if (t == null || !t.actif() || t.simulation) {
            Annonces.chat(p, Component.translatable("clashwar.chat.pas_inscrit"));
            return;
        }
        t.retirer(p);
    }

    static void quitter(ServerPlayer p) {
        Tournoi t = courant;
        if (t == null || !t.actif() || t.simulation) {
            Annonces.chat(p, Component.translatable("clashwar.chat.pas_participant"));
            return;
        }
        t.quitter(p);
    }

    static void regarder(ServerPlayer p, int tour, int index) {
        Tournoi t = courant;
        if (t == null || !t.actif() || t.simulation) {
            Annonces.chat(p, Component.translatable("clashwar.chat.pas_participant"));
            return;
        }
        t.regarder(p, tour, index);
    }

    static void revenirEnSalle(ServerPlayer p) {
        Tournoi t = courant;
        if (t == null || !t.actif() || t.simulation) {
            Annonces.chat(p, Component.translatable("clashwar.chat.pas_spectateur"));
            return;
        }
        t.revenirEnSalle(p);
    }

    // ================================================================== aides
    /** Heure locale « 20:30 » (fuseau du serveur), avec le jour si ce n'est pas aujourd'hui (« mer. 20:30 »). */
    static String heure(long epochMs) {
        ZoneId z = CwConfig.get().zone();
        ZonedDateTime d = Instant.ofEpochMilli(epochMs).atZone(z);
        String hm = String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
        if (d.toLocalDate().equals(LocalDate.now(z))) return hm;
        return jour(d.getDayOfWeek()) + " " + hm;
    }

    private static String jour(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "lun.";
            case TUESDAY -> "mar.";
            case WEDNESDAY -> "mer.";
            case THURSDAY -> "jeu.";
            case FRIDAY -> "ven.";
            case SATURDAY -> "sam.";
            case SUNDAY -> "dim.";
        };
    }
}
