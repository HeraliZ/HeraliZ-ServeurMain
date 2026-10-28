package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arrivée d'un nouveau joueur avec le Commandant Varga (18/10, choix du propriétaire) :
 * <ol>
 *    <li>la cinématique d'arrivée finie (HeraliZ Utils), le suivi à droite dit « Va voir le Commandant Varga pour
 *    commencer le tutoriel », avec la distance jusqu'à lui et des lueurs sur lui quand on est près ;</li>
 *    <li>Varga ne propose alors QUE « Faire ma base » (et « Au revoir ») : ni mission, ni commerce ({@link Dialogues}) ;</li>
 *    <li>le bouton lance le tutoriel des bases de CRac ({@code TutorielApi.lancer}) ; dans le monde des bases, Varga donne
 *    les consignes une par une à la radio (en bas à gauche de l'écran, {@link Reseau.Radio}) et le suivi montre l'étape
 *    en cours de la cabane ;</li>
 *    <li>la cabane finie, Varga le laisse se débrouiller (« reviens me voir pour des missions ») : ses quêtes principales
 *    s'ouvrent comme avant.</li>
 * </ol>
 * Sans CRac (ou sans monde des bases), rien de tout cela : Varga donne ses quêtes comme avant. L'état du tutoriel est lu
 * chez CRac à chaque fois (rien n'est gardé ici, sauf la dernière étape vue, pour ne parler qu'aux changements).
 */
final class Accueil {
    /** Le PNJ qui accueille les nouveaux et guide le tutoriel des bases. */
    static final String PNJ = "varga";
    /** Secondes entre les mots de la fin de Varga (le premier pendant le tour de la base par la caméra). */
    private static final int FIN_ECART = 16;
    /** Les mots de la fin qui suivent le premier : raids et défenses, puis /home, /spawn et missions. */
    private static final String[] FINS = {"heraliz_quetes.tuto.radio.fin2", "heraliz_quetes.tuto.radio.fin3"};

    private record Etat(int tuto, int etape, boolean intro) {
    }

    private static final Map<UUID, Etat> DERNIER = new HashMap<>();
    /** Avancée vue en dernier (étape et pièces faites) et secondes depuis, pour l'aide de Varga. */
    private record Bloque(int etape, int fait, int secondes) {
    }

    private static final Map<UUID, Bloque> SUR_ETAPE = new HashMap<>();
    /** Secondes sans avancer avant que Varga donne un conseil de plus. */
    private static final int AIDE_APRES = 30;
    /** Prochain mot de la fin (rang dans FINS) et secondes avant lui. */
    private record Suite(int rang, int secondes) {
    }

    private static final Map<UUID, Suite> FIN = new HashMap<>();
    /** Le marchand des matériaux de construction, présenté par Varga après le tutoriel. */
    static final String BATISSEUR = "batisseur";
    /** Donnée persistante du joueur : le Bâtisseur lui a été présenté (une seule fois). */
    private static final String PRESENTE = "heraliz_batisseur_presente";
    /** Durée du tour de caméra autour du Bâtisseur (ticks), rayon (blocs), secondes de chaque mot de Varga, fiche après. */
    private static final int PRES_TICKS = 500, PRES_FICHE = 25;
    private static final float PRES_RAYON = 4.5F;
    /** 12 s par message : le temps qu'il s'écrive lettre par lettre (≈ 7 s) et qu'on le lise (retour du propriétaire). */
    private static final int[] PRES_MOTS = {0, 12};   // 20/10 : deux messages, 25 s en tout (46 s jugées trop longues)
    /** Secondes depuis le début de la présentation (et Bâtisseur trouvé ou non). */
    private record Pres(int secondes, boolean vu) {
    }

    private static final Map<UUID, Pres> PRES = new HashMap<>();
    /** Secondes depuis le dernier rappel « tape /spawn » (tutoriel fini, Bâtisseur pas encore présenté). */
    private static final Map<UUID, Integer> RAPPEL = new HashMap<>();
    /** Secondes passées au spawn avant la présentation (le temps que le Bâtisseur se charge). */
    private static final Map<UUID, Integer> ARRIVEE = new HashMap<>();
    private static final int RAPPEL_ECART = 15;
    /** Apparence de l'entité Varga vue en dernier (si son fichier n'en a pas : posée par /pnj apparence varga moi). */
    private static CompoundTag apparenceVue;

    /** Le guide du spawn (20/10) : après la cinématique, il fait visiter le spawn et présente chaque PNJ. */
    static final String GUIDE = "guide";
    /** Donnée persistante du joueur : la visite du guide est faite (ou passée). */
    private static final String VISITE_FAITE = "heraliz_guide_visite";
    /**
     * Les étapes de la visite, dans l'ordre : les PNJ, puis (28/10, demande du propriétaire) les points d'intérêt posés
     * par le staff ({@code @id}, {@link PointsGuide}) : casino et capsules, Table d'armurier, coffre « Safe Planque 1 »,
     * caisses, borne de départ, recycleur ; Varga en dernier (on finit chez lui). Phrase du guide : clé
     * {@code heraliz_quetes.guide.<id>}.
     */
    private static final String[] VISITE = {"mila", BATISSEUR, "jacqueline", "doyen", "jack", "@casino", "@armurier", "@coffre",
            "@caisses", "@borne", "@recycleur", PNJ};
    /** Donnée persistante du joueur : il a parlé au Commandant Varga (fin du guidage : traînée et surbrillance). */
    private static final String VARGA_PARLE = "heraliz_varga_parle";
    /** Secondes par PNJ (le temps que la phrase s'écrive et se lise) et durée du tour de caméra (ticks). */
    // 20/10 : le tour dure 1 s de plus que l'écart : le PNJ suivant arrive caméra encore partie, sans retour au joueur
    private static final int VISITE_ECART = 12, VISITE_TICKS = VISITE_ECART * 20 + 20;
    private static final float VISITE_RAYON = 4F;
    /** Visite en cours : rang du PNJ présenté et secondes depuis. */
    private record Visite(int rang, int secondes) {
    }

    private static final Map<UUID, Visite> VISITES = new HashMap<>();

    private Accueil() {
    }

    static void oublier(UUID u) {
        DERNIER.remove(u);
        FIN.remove(u);
        SUR_ETAPE.remove(u);
        PRES.remove(u);
        RAPPEL.remove(u);
        ARRIVEE.remove(u);
        VISITES.remove(u);
    }

    /** La visite du guide reste à faire (seulement si le guide existe dans les fichiers des PNJ). */
    static boolean visiteAFaire(ServerPlayer p) {
        return Catalogue.pnj(GUIDE) != null && !persiste(p).getBoolean(VISITE_FAITE);
    }

    /** Bouton « Fais-moi visiter » du guide : la caméra passe d'un PNJ à l'autre pendant que le guide les présente. */
    static void visite(ServerPlayer p) {
        VISITES.put(p.getUUID(), new Visite(-1, VISITE_ECART - 1));
        Moteur.suiviBientot(p);
    }

    /** Visite du Guide passée par le joueur (Espace maintenu) : notée faite, mot de la fin, suivi vers Varga. */
    static void passerVisite(ServerPlayer p) {
        if (VISITES.remove(p.getUUID()) == null) return;
        persiste(p).putBoolean(VISITE_FAITE, true);
        radio(p, GUIDE, 0, "heraliz_quetes.guide.fin");
        Moteur.suiviBientot(p);
    }

    /** Chaque seconde : PNJ suivant de la visite. */
    private static void visiteSeconde(ServerPlayer p) {
        Visite v = VISITES.get(p.getUUID());
        if (v == null) return;
        if (v.secondes() + 1 < VISITE_ECART) {
            VISITES.put(p.getUUID(), new Visite(v.rang(), v.secondes() + 1));
            return;
        }
        int rang = v.rang() + 1;
        if (rang >= VISITE.length) {
            VISITES.remove(p.getUUID());
            persiste(p).putBoolean(VISITE_FAITE, true);
            radio(p, GUIDE, 0, "heraliz_quetes.guide.fin");
            Moteur.suiviBientot(p);
            return;
        }
        String etape = VISITE[rang];
        if (etape.startsWith("@")) {
            String id = etape.substring(1);
            PointsGuide.Point pt = PointsGuide.point(id);
            Pnj e;
            if (pt != null && pt.monde().equals(p.level().dimension().location().toString())
                    && p.distanceToSqr(pt.x(), pt.y(), pt.z()) < 512 * 512) {
                // centre envoyé 1,4 bloc plus bas : la caméra du joueur vise « y + 1,4 » (les yeux d'un PNJ)
                Reseau.envoyer(p, new Reseau.Presentation(pt.x(), pt.y() - 1.4, pt.z(), pt.rayon(), VISITE_TICKS, pt.angle()));
            } else if ("recycleur".equals(id) && (e = pnj(p, "recycleur", 200)) != null) {
                // recycleur sans point posé : le PNJ recycleur, s'il existe
                Reseau.envoyer(p, new Reseau.Presentation(e.getX(), e.getY(), e.getZ(), VISITE_RAYON, VISITE_TICKS, Float.NaN));
            }
            // point pas posé par le staff (/guide point <id>) : la phrase seule, sans caméra
            radio(p, GUIDE, 0, "heraliz_quetes.guide." + id);
        } else {
            Pnj e = pnj(p, etape, 200);
            if (e != null) {
                Reseau.envoyer(p, new Reseau.Presentation(e.getX(), e.getY(), e.getZ(), VISITE_RAYON, VISITE_TICKS, Float.NaN));
            }
            radio(p, GUIDE, 0, "heraliz_quetes.guide." + etape);
        }
        VISITES.put(p.getUUID(), new Visite(rang, 0));
    }

    /** Le joueur doit d'abord faire le tutoriel des bases : Varga ne propose que « Faire ma base ». Jamais en créatif. */
    static boolean tutoAFaire(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator() && Ponts.tutoEtat(p) == 0;
    }

    /**
     * « Va voir le guide », puis « Va voir le Commandant Varga » : tutoriel à faire, cinématique finie, en survie, pas
     * encore de mission en cours (la première mission de Varga peut passer avant la base, choix du propriétaire du 20/10).
     */
    private static boolean intro(ServerPlayer p, int tuto) {
        return tuto == 0 && !p.isCreative() && !p.isSpectator() && Ponts.cinematiqueFinie(p)
                && Donnees.de(p.getUUID()).actives.isEmpty();
    }

    /** Le PNJ vers qui envoyer le nouveau joueur : le guide tant que la visite n'est pas faite, ensuite Varga. */
    private static String cible(ServerPlayer p) {
        return visiteAFaire(p) ? GUIDE : PNJ;
    }

    /** Bouton « Faire ma base » du dialogue de Varga. */
    static void lancer(ServerPlayer p) {
        if (Ponts.tutoLancer(p)) {
            Moteur.suiviBientot(p);
        }
    }

    /** Chaque seconde (ServeurQuetes) : suivi à jour pendant l'arrivée et le tutoriel, radio quand l'étape change. */
    static void seconde(ServerPlayer p) {
        int tuto = Ponts.tutoEtat(p);
        int etapes = Ponts.tutoEtapes();
        Suite fin = FIN.get(p.getUUID());
        if (fin != null) {
            if (fin.secondes() > 1) {
                FIN.put(p.getUUID(), new Suite(fin.rang(), fin.secondes() - 1));
            } else {
                radio(p, 0, fin.rang() == 0 && Ponts.tutoJours() <= 0 ? FINS[0] + "_np" : FINS[fin.rang()]);
                if (fin.rang() + 1 < FINS.length) {
                    FIN.put(p.getUUID(), new Suite(fin.rang() + 1, FIN_ECART));
                } else {
                    FIN.remove(p.getUUID());
                    Moteur.suiviBientot(p);
                    Moteur.notif(p, Texte.cle("heraliz_quetes.tuto.notif_fin"), 0x5FD08A);
                }
            }
        }
        // 20/10 : plus de présentation du Bâtisseur ni de rappel /spawn après le tutoriel (le guide présente tout le
        // monde avant) ; le code reste
        visiteSeconde(p);
        if (tuto < 0) {
            DERNIER.remove(p.getUUID());
            return;
        }
        boolean intro = intro(p, tuto);
        int[] pr = tuto == 2 ? Ponts.tutoProgression(p) : new int[]{0, 0, 0};
        int etape = pr[0];
        Etat avant = DERNIER.put(p.getUUID(), new Etat(tuto, etape, intro));
        boolean guide = intro || tuto == 1 || tuto == 2;
        if (guide || avant != null && (avant.intro() || avant.tuto() == 1 || avant.tuto() == 2)) {
            Moteur.suiviBientot(p);   // distance et compteurs à jour, ou retour au suivi des quêtes
        }
        if (tuto == 2 && etape >= 1 && etape <= etapes && (avant == null || avant.tuto() != 2 || avant.etape() != etape)) {
            radio(p, etape, "heraliz_quetes.tuto.radio." + etape);
            SUR_ETAPE.put(p.getUUID(), new Bloque(etape, pr[1], 0));
        } else if (tuto == 2 && etape >= 1 && etape <= etapes) {
            // 30 s sans rien poser ni améliorer : Varga explique autrement (une fois par étape)
            Bloque b = SUR_ETAPE.get(p.getUUID());
            if (b == null || b.etape() != etape) {
                b = new Bloque(etape, pr[1], 0);
            } else if (b.secondes() >= 0) {
                b = b.fait() != pr[1] ? new Bloque(etape, pr[1], 0) : new Bloque(etape, pr[1], b.secondes() + 1);
                if (b.secondes() >= AIDE_APRES) {
                    radio(p, etape, "heraliz_quetes.tuto.aide." + etape);
                    b = new Bloque(etape, pr[1], -1);   // déjà aidé sur cette étape
                }
            }
            SUR_ETAPE.put(p.getUUID(), b);
        } else if (tuto == 3 && avant != null && avant.tuto() == 2) {
            // le premier mot de la fin pendant que la caméra fait le tour de la base (CRac), le second après
            String premier = Ponts.tutoSas(p) ? "heraliz_quetes.tuto.radio.fin" : "heraliz_quetes.tuto.radio.fin_sans_sas";
            // 27/10 : plus de protection des nouvelles bases (CRac, [protection] automatique) : Varga n'en parle plus
            int jours = Ponts.tutoJours();
            radio(p, 0, jours > 0 ? premier : premier + "_np", Texte.brut(jours));
            FIN.put(p.getUUID(), new Suite(0, FIN_ECART));
        } else if (tuto != 2 && avant != null && avant.tuto() == 2) {
            // tutoriel arrêté autrement (staff : /tuto finir, rejouer) : la radio se range
            Reseau.envoyer(p, new Reseau.Radio(Texte.VIDE, new CompoundTag(), "", false, 0, etapes, Texte.VIDE));
        }
    }

    /** Données du joueur gardées à la mort (sous-étiquette PlayerPersisted de Forge). */
    private static CompoundTag persiste(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains("PlayerPersisted")) {
            d.put("PlayerPersisted", new CompoundTag());
        }
        return d.getCompound("PlayerPersisted");
    }

    /**
     * Au spawn : dans le monde du spawn, ou près du Bâtisseur (20/10 : le /spawn de CRac mène au point posé par le staff,
     * qui peut être hors du monde du spawn ; la présentation ne partait alors jamais).
     */
    private static boolean auSpawn(ServerPlayer p) {
        return "heralisz:spawn".equals(p.level().dimension().location().toString()) || pnj(p, BATISSEUR, 64) != null;
    }

    /** Tant que le joueur n'est pas revenu au spawn après le tutoriel : Varga lui redit « tape /spawn » toutes les 15 s. */
    private static void rappelSpawn(ServerPlayer p, int tuto) {
        if (!presenterBientot(p, tuto) || FIN.containsKey(p.getUUID()) || PRES.containsKey(p.getUUID()) || auSpawn(p)) {
            RAPPEL.remove(p.getUUID());
            return;
        }
        int s = RAPPEL.merge(p.getUUID(), 1, Integer::sum);
        if (s >= RAPPEL_ECART) {
            radio(p, 0, "heraliz_quetes.tuto.rappel_spawn");
            Moteur.suiviBientot(p);
            RAPPEL.put(p.getUUID(), 0);
        }
    }

    /** Le Bâtisseur pas encore présenté à ce joueur, tutoriel des bases fini. */
    private static boolean presenterBientot(ServerPlayer p, int tuto) {
        return tuto == 3 && !p.isSpectator() && !persiste(p).getBoolean(PRESENTE);   // aussi en créatif : le staff qui teste (/tuto lancer)
    }

    /**
     * Présentation du Bâtisseur (Quêtes 1.8.0, demande du propriétaire du 19/10) : à la première arrivée au spawn après
     * le tutoriel des bases (mots de la fin de Varga finis), la caméra tourne autour du Bâtisseur pendant que Varga
     * explique à la radio ce qu'il vend, ce qui se trouve sur la carte et /base info ; puis la fiche technique des bases
     * (CRac) s'ouvre. Une seule fois par joueur. Sans Bâtisseur posé au spawn : la radio seule, sans caméra.
     */
    private static void presentation(ServerPlayer p, int tuto) {
        if (tuto >= 0 && tuto <= 2 && persiste(p).getBoolean(PRESENTE)) {
            // tutoriel à faire ou en cours (/tuto rejouer, /tuto lancer) : la présentation sera revue à la fin. Avant
            // (20/10) seul l'état « à faire » l'effaçait, mais /tuto lancer et /tuto rejouer redonnent le kit dans la
            // même seconde : l'état 0 n'était jamais vu, et ni le rappel /spawn ni la présentation ne repartaient
            persiste(p).remove(PRESENTE);
        }
        Pres pr = PRES.get(p.getUUID());
        if (pr == null) {
            if (!presenterBientot(p, tuto) || FIN.containsKey(p.getUUID()) || !auSpawn(p)) {
                ARRIVEE.remove(p.getUUID());
                return;
            }
            // juste après /spawn, le Bâtisseur n'est souvent pas encore chargé : on l'attend jusqu'à 8 s (20/10 : sinon
            // Varga parlait sans la caméra)
            Pnj b = pnj(p, BATISSEUR, 160);
            int attente = ARRIVEE.merge(p.getUUID(), 1, Integer::sum);
            if (attente < 2 || b == null && attente < 8) return;
            ARRIVEE.remove(p.getUUID());
            persiste(p).putBoolean(PRESENTE, true);
            if (b != null) {
                Reseau.envoyer(p, new Reseau.Presentation(b.getX(), b.getY(), b.getZ(), PRES_RAYON, PRES_TICKS, Float.NaN));
            }
            pr = new Pres(0, b != null);
            Moteur.suiviBientot(p);
        } else {
            pr = new Pres(pr.secondes() + 1, pr.vu());
        }
        for (int i = 0; i < PRES_MOTS.length; i++) {
            if (pr.secondes() == PRES_MOTS[i]) {
                radio(p, 0, "heraliz_quetes.tuto.batisseur." + (i + 1) + (i == 0 && !pr.vu() ? "_sans" : ""));
            }
        }
        if (pr.secondes() >= PRES_FICHE) {
            PRES.remove(p.getUUID());
            Ponts.tutoFiche(p);
        } else {
            PRES.put(p.getUUID(), pr);
        }
    }

    /** Message radio de Varga : la consigne de l'étape (1 à 9), ou un mot de la fin (0). */
    private static void radio(ServerPlayer p, int etape, String cle, Texte... args) {
        radio(p, PNJ, etape, cle, args);
    }

    /** Message radio du PNJ {@code id} (son nom et son apparence en portrait). */
    private static void radio(ServerPlayer p, String id, int etape, String cle, Texte... args) {
        Defs.Pnj def = Catalogue.pnj(id);
        Texte nom = def != null && !def.nom.vide() ? def.nom : Texte.cle("heraliz_quetes.tuto.varga_nom");
        CompoundTag app = def != null && def.apparence != null ? def.apparence
                : PNJ.equals(id) && apparenceVue != null ? apparenceVue : new CompoundTag();
        Reseau.envoyer(p, new Reseau.Radio(nom, app, def == null ? "" : def.skin, def != null && def.brasFins, etape,
                Ponts.tutoEtapes(), Texte.cle(cle, args)));
    }

    /** Le suivi à droite pendant l'arrivée et le tutoriel ; null le reste du temps (la quête suivie, comme avant). */
    static Reseau.Suivi suivi(ServerPlayer p) {
        int tuto = Ponts.tutoEtat(p);
        if (intro(p, tuto)) {
            String id = cible(p);
            Defs.Pnj def = Catalogue.pnj(id);
            Texte nom = def != null && !def.nom.vide() ? def.nom : Texte.cle("heraliz_quetes.tuto.varga_nom");
            Pnj v = id.equals(PNJ) ? varga(p) : pnj(p, id, 4096);
            int d = v == null ? -1 : (int) Math.sqrt(v.distanceToSqr(p));
            String ligne = VISITES.containsKey(p.getUUID()) ? "heraliz_quetes.guide.suivi_visite"
                    : id.equals(GUIDE) ? "heraliz_quetes.guide.intro" : "heraliz_quetes.tuto.intro";
            return new Reseau.Suivi(nom, Texte.cle("heraliz_quetes.tuto.intro_titre"),
                    List.of(new Reseau.SLigne(Texte.cle(ligne), 0, 0, d, false, 1)));
        }
        Texte entete = Texte.cle("heraliz_quetes.tuto.suivi_pnj");
        Texte titre = Texte.cle("heraliz_quetes.tuto.titre");
        if (tuto == 1) {
            return new Reseau.Suivi(entete, titre, List.of(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.kit"), 0, 0, -1, false, 1)));
        }
        if (tuto != 2) return null;
        int[] pr = Ponts.tutoProgression(p);
        if (pr[0] < 1) {
            return new Reseau.Suivi(entete, titre, List.of(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.retour"), 0, 0, -1, false, 1)));
        }
        List<Reseau.SLigne> lignes = new ArrayList<>();
        int etapes = Ponts.tutoEtapes();
        int e = Math.min(pr[0], etapes);
        // l'étape d'avant (faite), puis celle en cours
        if (e > 1) lignes.add(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.etape." + (e - 1)), 0, 0, -1, false, 2));
        lignes.add(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.etape." + e), pr[1], pr[2], -1, false, pr[0] > etapes ? 2 : 1));
        return new Reseau.Suivi(entete, titre, lignes);
    }

    /** Le joueur parle au Commandant Varga (Dialogues) : le guidage vers lui (traînée, surbrillance) s'arrête. */
    static void vargaParle(ServerPlayer p) {
        // seulement visite faite : un joueur passé chez Varga avant la visite est guidé vers lui après
        if (!visiteAFaire(p) && !persiste(p).getBoolean(VARGA_PARLE)) persiste(p).putBoolean(VARGA_PARLE, true);
    }

    /**
     * Lueur au-dessus du PNJ à aller voir (le guide, puis Varga). Chez le joueur, elle met ce PNJ en avant : repère,
     * surbrillance et traînée verte au sol jusqu'à lui (28/10, client {@code Guidage}) ; vers Varga, seulement tant que le
     * joueur ne lui a pas parlé.
     */
    static void lueurs(ServerPlayer p, List<BlockPos> l) {
        if (!intro(p, Ponts.tutoEtat(p)) || VISITES.containsKey(p.getUUID())) return;
        String id = cible(p);
        if (id.equals(PNJ) && persiste(p).getBoolean(VARGA_PARLE)) return;
        Pnj v = id.equals(PNJ) ? varga(p) : pnj(p, id, 4096);
        // 20/10 : à toute distance (le repère du PNJ au spawn se met en avant, avec la distance)
        if (v != null) l.add(v.blockPosition().above(2));
    }

    /** Le PNJ {@code id} le plus proche dans le monde du joueur (chargé), à moins de {@code portee} blocs, ou null. */
    private static Pnj pnj(ServerPlayer p, String id, double portee) {
        Pnj best = null;
        double bd = portee * portee;
        for (Pnj e : p.serverLevel().getEntities(HeralizQuetes.PNJ.get(), x -> id.equals(x.pnjId()))) {
            double d = e.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Le Commandant Varga le plus proche dans le monde du joueur (chargé), ou null. */
    private static Pnj varga(ServerPlayer p) {
        Pnj best = null;
        double bd = Double.MAX_VALUE;
        for (Pnj e : p.serverLevel().getEntities(HeralizQuetes.PNJ.get(), x -> PNJ.equals(x.pnjId()))) {
            double d = e.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null && !best.apparence().isEmpty()) apparenceVue = best.apparence().copy();
        return best;
    }
}
