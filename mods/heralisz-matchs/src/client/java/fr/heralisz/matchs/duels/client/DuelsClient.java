package fr.heralisz.matchs.duels.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.dev.MatchsHarness;
import fr.heralisz.matchs.duels.Registres;
import fr.heralisz.matchs.duels.bombe.ChargeObjet;
import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import fr.heralisz.matchs.duels.client.bombe.BombeRendu;
import fr.heralisz.matchs.duels.client.bombe.ChargeObjetRendu;
import fr.heralisz.matchs.duels.client.bombe.OrdinateurRendu;
import fr.heralisz.matchs.duels.client.bombe.PosesBombe;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cote client des duels (classe d'entree client de {@code Modules}) : etat recu du serveur (catalogue, classes, defis
 * recus, recherche et destruction), ouverture des ecrans, HUD ({@link DuelHud}), notification des defis recus
 * ({@link NotifDefis}, jamais d'ecran), rendus de la cible d'entrainement, de la bombe, de l'ordinateur pose et de
 * l'ordinateur en main (duels/client/bombe), poses des joueurs qui tapent ou piratent, etapes du banc d'essai. Fil client.
 */
public final class DuelsClient {
    private DuelsClient() {
    }

    private static Catalogue catalogue = Catalogue.VIDE;
    private static List<ClassePerso> classes = List.of();
    private static int derniere;
    private static int versionClasses;
    /**
     * Choix de classe en cours (Matchs 0.11.3) : tant que le joueur n'a pas choisi et que le temps n'est pas ecoule,
     * l'ecran de choix est rouvert a chaque tick s'il n'est plus a l'ecran (remplace par l'ecran de chargement d'un
     * changement de monde, par un autre ecran ou ferme) ; la classe par defaut du serveur ne vaut qu'a la fin du temps.
     */
    @Nullable
    private static PaquetsDuels.ChoixS2C choixActif;
    private static long choixDebutMs;
    private static boolean choixFait;
    private static int choixCourant;

    /** Appele par {@code Duels.construire} sur le client physique. */
    public static void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(Registres.CIBLE.get(), CibleRenderer::new);
            e.registerEntityRenderer(Registres.BOMBE.get(), BombeRendu::new);
            e.registerEntityRenderer(Registres.ORDINATEUR.get(), OrdinateurRendu::new);
        });
        // ordinateur ferme en main (GeckoLib), porte le long du corps comme une mallette
        ChargeObjet.RENDU_CLIENT.brancher(c -> c.accept(ORDINATEUR_EN_MAIN));
        // joueur qui tape ou pirate : mains cachees a la 1re personne, pose des bras (Player Animator s'il est la)
        OrdinateurDuel.TICK_CLIENT.brancher(PosesBombe::suivre);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PosesBombe::mains);
        MinecraftForge.EVENT_BUS.addListener(PosesBombe::deconnexion);
        // touches « accepter / refuser un defi » (sans touche par defaut)
        modBus.addListener(NotifDefis::enregistrerTouches);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, DuelsClient::masquerHud);
        MinecraftForge.EVENT_BUS.addListener(DuelHud::rendre);
        MinecraftForge.EVENT_BUS.addListener(NotifDefis::rendre);
        MinecraftForge.EVENT_BUS.addListener(DuelHud::matrices);
        MinecraftForge.EVENT_BUS.addListener(DuelsClient::tick);
        MinecraftForge.EVENT_BUS.addListener(DuelsClient::deconnexion);
        // amorcage / piratage : ni tir, ni visee, ni changement d'arme chez le joueur (le serveur decide)
        ActionClient.enregistrer();
        // HUD du moteur en petit pendant un duel (bandeau reduit, annonces en haut, jamais au milieu)
        fr.heralisz.matchs.moteur.client.HudMoteur.compact = DuelsClient::enDuel;
    }

    /**
     * Vrai pendant un duel : vue R&D recue, ou bandeau du moteur d'un duel (titre {@code duel.hud.*}, match a mort
     * compris). Sans allocation (lu a chaque image par le HUD du moteur).
     */
    static boolean enDuel() {
        if (DuelHud.vue() != null) return true;
        var b = fr.heralisz.matchs.moteur.client.MoteurClient.bandeau();
        return b != null && b.titre().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith("duel.hud.");
    }

    /** Rendu de l'ordinateur ferme en main (une instance : Forge appelle initializeClient une fois par objet). */
    private static final IClientItemExtensions ORDINATEUR_EN_MAIN = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return ChargeObjetRendu.instance();
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entite, InteractionHand main, ItemStack pile) {
            return HumanoidModel.ArmPose.EMPTY;
        }
    };

    // ------------------------------------------------------------------ lecture
    public static Catalogue catalogue() {
        return catalogue;
    }

    public static List<ClassePerso> classes() {
        return classes;
    }

    public static int derniere() {
        return derniere;
    }

    /** Change a chaque reception des classes (les ecrans s'y recalent). */
    public static int versionClasses() {
        return versionClasses;
    }

    /** Defi recu le plus recent encore en attente, ou null. */
    @Nullable
    public static Vues.DefiVue defi() {
        return NotifDefis.plusRecent();
    }

    // ------------------------------------------------------------------ reception
    public static void catalogue(Catalogue c) {
        catalogue = c;
        Rendu.oublierNoms();
    }

    public static void classes(List<ClassePerso> l, int d, boolean ouvrir) {
        classes = List.copyOf(l);
        derniere = d;
        versionClasses++;
        Minecraft mc = Minecraft.getInstance();
        if (ouvrir) mc.setScreen(new ClassesScreen(mc.screen instanceof DuelScreen ? mc.screen : null));
    }

    public static void ecranDuel(Vues.EcranDuel e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.entrant() != null) NotifDefis.ajouter(e.entrant(), false);
        if (mc.screen instanceof DuelScreen s) s.mettreAJour(e);
        else if (mc.screen == null || mc.screen instanceof ClassesScreen) mc.setScreen(new DuelScreen(e));
    }

    /**
     * Defi recu : notification discrete en haut a droite ({@link NotifDefis}) et son, JAMAIS d'ecran (on peut etre en
     * plein PvP). Fin d'un defi : sa notification sort ; {@code idFini} negatif = debut d'un duel, toutes sortent.
     */
    public static void defi(@Nullable Vues.DefiVue d, int idFini, String raison) {
        Minecraft mc = Minecraft.getInstance();
        if (d != null) {
            NotifDefis.ajouter(d, true);
            if (mc.player != null) mc.player.playNotifySound(Registres.DEFI.get(), SoundSource.MASTER, 0.9f, 1f);
            return;
        }
        if (idFini < 0) NotifDefis.vider(true);
        else NotifDefis.retirer(idFini);
        if (idFini < 0 && mc.screen instanceof DuelScreen) mc.setScreen(null);
        if (!raison.isEmpty()) DuelHud.toast(Component.translatable("duel.toast.defi"), Component.translatable(raison), false);
    }

    public static void choix(PaquetsDuels.ChoixS2C p) {
        Minecraft mc = Minecraft.getInstance();
        // debut de manche : les reperes A / B sont bien visibles pendant le choix et les premieres secondes du combat
        DuelHud.choix(p.secondes());
        if (p.secondes() <= 0) {
            choixActif = null;
            if (mc.screen instanceof ChoixClasseScreen) mc.setScreen(null);
            return;
        }
        // les classes de l'hote (duel) ne sont pas celles du joueur : elles ne remplacent jamais sa liste (ecran CLASSES)
        if (!p.classes().isEmpty() && p.hote().isEmpty()) {
            classes = List.copyOf(p.classes());
            versionClasses++;
        }
        choixActif = p;
        choixDebutMs = net.minecraft.Util.getMillis();
        choixFait = false;
        choixCourant = p.choisie();
        ouvrirChoix();
    }

    /** Classe choisie par le joueur (envoyee au serveur) : l'ecran n'est plus impose. */
    static void choixFait(int i) {
        choixFait = true;
        choixCourant = i;
    }

    /** Vrai si le joueur a deja choisi sa classe pour le choix en cours (ou s'il n'y a pas de choix en cours). */
    static boolean choixFait() {
        return choixActif == null || choixFait;
    }

    /**
     * (Re)ouvre l'ecran de choix si le choix est en cours, pas encore fait et pas fini. Attend (tick suivant) pendant un
     * chargement de monde (le duel commence souvent par un changement de dimension), le chat ouvert, l'ecran de mort ou
     * une killcam ; tout autre ecran est remplace : le choix est impose.
     */
    private static void ouvrirChoix() {
        PaquetsDuels.ChoixS2C p = choixActif;
        if (p == null) return;
        if (net.minecraft.Util.getMillis() - choixDebutMs >= p.secondes() * 1000L) {
            choixActif = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (choixFait || mc.player == null || mc.level == null || killcamActive()) return;
        var ecran = mc.screen;
        if (ecran instanceof ChoixClasseScreen || ecran instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || ecran instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                || ecran instanceof net.minecraft.client.gui.screens.ProgressScreen
                || ecran instanceof net.minecraft.client.gui.screens.GenericDirtMessageScreen
                || ecran instanceof net.minecraft.client.gui.screens.ChatScreen
                || ecran instanceof net.minecraft.client.gui.screens.DeathScreen) return;
        mc.setScreen(new ChoixClasseScreen(p, choixDebutMs, choixCourant));
    }

    public static void rd(@Nullable Vues.RdVue v) {
        DuelHud.rd(v);
    }

    public static void scores(Vues.Tableau t) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ScoresScreen(t));
    }

    public static void effet(int effet, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        switch (effet) {
            case PaquetsDuels.EffetS2C.EXPLOSION -> {
                DuelHud.flash();
                if (mc.level != null) {
                    mc.level.playLocalSound(x, y, z, Registres.EXPLOSION.get(), SoundSource.PLAYERS, 4f, 1f, false);
                    // boule de feu et fumee a la place de la bombe (elle n'est plus dessinee jusqu'a la manche suivante)
                    mc.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0);
                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI / 6;
                        mc.level.addParticle(ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * 0.6, y + 0.3, z + Math.sin(a) * 0.6, Math.cos(a) * 0.08, 0.06,
                                Math.sin(a) * 0.08);
                    }
                }
            }
            case PaquetsDuels.EffetS2C.POSEE -> {
                if (mc.level != null) mc.level.playLocalSound(x, y, z, Registres.POSE.get(), SoundSource.PLAYERS, 1.5f, 1f, false);
            }
            case PaquetsDuels.EffetS2C.DESAMORCEE -> {
                if (mc.level != null) mc.level.playLocalSound(x, y, z, Registres.DESAMORCE.get(), SoundSource.PLAYERS, 1.5f, 1f, false);
            }
            case PaquetsDuels.EffetS2C.NETTOYAGE -> {
                // arene remise au propre par le serveur : plus de nuage de fumee ni d'etincelles de la manche d'avant
                if (mc.level != null) mc.particleEngine.setLevel(mc.level);
            }
            case PaquetsDuels.EffetS2C.FIN -> {
                DuelHud.rd(null);
                choixActif = null;
                if (mc.screen instanceof ChoixClasseScreen) mc.setScreen(null);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ evenements
    private static void masquerHud(RenderGuiOverlayEvent.Pre e) {
        // nos ecrans masquent le HUD derriere eux (charte)
        if (Minecraft.getInstance().screen instanceof EcranDuels) e.setCanceled(true);
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        DuelHud.tick();
        NotifDefis.tick();
        ouvrirChoix();
    }

    private static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        catalogue = Catalogue.VIDE;
        classes = List.of();
        NotifDefis.vider(false);
        choixActif = null;
        DuelHud.vider();
    }

    /** Vrai pendant une killcam (les HUD des duels se taisent). */
    static boolean killcamActive() {
        try {
            return fr.heralisz.matchs.duels.killcam.client.KillcamClient.actif();
        } catch (LinkageError ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ banc d'essai
    /**
     * Etapes du banc : {@code "duelFaux": "classes|duel|defi|choix|scores|scores_mam|hud_rd|hud_porteur|aucun"} (ecrans et HUD
     * avec des donnees factices : catalogue de demonstration si le serveur n'en a pas envoye),
     * {@code "duelChamp": "principale|acc:scope|secondaire|melee|soin0|soin1|letale|tactique|tenue"} (ouvre le selecteur de
     * l'ecran CLASSES), {@code "duelEmplacement": n}, {@code "duelLog": true} (rectangles de l'ecran courant).
     */
    public static void etapesBanc(MatchsHarness.Registre r) {
        r.ajouter("duelFaux", DuelsClient::faux);
        r.ajouter("duelChamp", (mc, v, s) -> {
            if (mc.screen instanceof ClassesScreen c) c.ouvrirSelecteur(v.getAsString());
        });
        r.ajouter("duelEmplacement", (mc, v, s) -> {
            if (mc.screen instanceof ClassesScreen c) c.emplacement(v.getAsInt());
        });
        // maintient (ou relache) la touche s'accroupir, comme un joueur : pose / desamorcage de la charge
        r.ajouter("duelAccroupi", (mc, v, s) -> mc.options.keyShift.setDown(v.getAsBoolean()));
        // teleportation (commande) sur le site A, le site B ou la charge, d'apres la vue R&D recue
        r.ajouter("duelAller", (mc, v, s) -> {
            Vues.RdVue rv = DuelHud.vue();
            if (rv == null || mc.player == null) {
                HeraliszMatchs.LOG.warn("[duels] duelAller : aucune vue R&D");
                return;
            }
            String q = v.getAsString();
            double[] si = rv.sites();
            double x = q.equals("b") ? si[3] : q.equals("charge") ? rv.x() : si[0];
            double y = q.equals("b") ? si[4] : q.equals("charge") ? rv.y() : si[1];
            double z = q.equals("b") ? si[5] : q.equals("charge") ? rv.z() : si[2];
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", x, y - 0.5 + 0.01, z));
        });
        r.ajouter("duelLog", (mc, v, s) -> {
            if (mc.screen instanceof EcranDuels d) HeraliszMatchs.LOG.info("[duels] ecran {} : {}", mc.screen.getClass().getSimpleName(), d.journal());
            HeraliszMatchs.LOG.info("[duels] catalogue client v{} : {} armes ; classes {} ; HUD {} ; defis {}", catalogue.version(), catalogue.armes().size(),
                    classes.size(), DuelHud.etat(), NotifDefis.etat());
        });
    }

    private static void faux(Minecraft mc, JsonElement v, JsonObject etape) {
        String q = v.getAsString();
        if (catalogue.vide()) catalogue(Catalogue.factice());
        if (classes.isEmpty()) {
            List<ClassePerso> l = new ArrayList<>();
            for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) l.add(ClassePerso.parDefaut(i, catalogue));
            classes = l;
            versionClasses++;
        }
        UUID moi = mc.player != null ? mc.player.getUUID() : UUID.randomUUID();
        switch (q) {
            case "classes" -> mc.setScreen(new ClassesScreen(null));
            case "duel" -> {
                Vues.DefiVue in = new Vues.DefiVue(7, "Dziga", "Dev", "1v1", Vues.MODE_RD, "Nuketown", 21_000, List.of("Dziga"), List.of("Dev"));
                mc.setScreen(new DuelScreen(new Vues.EcranDuel(List.of(
                        new Vues.Candidat(UUID.randomUUID(), "Dziga", false, false, false),
                        new Vues.Candidat(UUID.randomUUID(), "Kaelis", true, false, false),
                        new Vues.Candidat(UUID.randomUUID(), "Moraz", true, true, false),
                        new Vues.Candidat(UUID.randomUUID(), "Nova_77", false, false, false),
                        new Vues.Candidat(UUID.randomUUID(), "Ravenn", true, false, true),
                        new Vues.Candidat(UUID.randomUUID(), "Sloane", false, false, false),
                        new Vues.Candidat(UUID.randomUUID(), "Vortex", false, false, false)), 2,
                        List.of(new Vues.CarteVue("duel_nuketown", "Nuketown", "Nuketown", List.of(Vues.MODE_RD, Vues.MODE_MAM), List.of("1v1", "2v2"))),
                        new Vues.StatsVue(14, 9, 212, 167, 48, 23), etape.has("entrant") ? in : null, null, null)));
            }
            case "defi" -> {
                // deux defis empiles : un 1v1 (plus ancien), puis un 2v2 (le plus recent, en haut, avec le rappel)
                mc.setScreen(null);
                NotifDefis.ajouter(new Vues.DefiVue(9, "Nova_77", "Dev", "1v1", Vues.MODE_RD, "Nuketown", 12_000, List.of("Nova_77"), List.of("Dev")), true);
                NotifDefis.ajouter(new Vues.DefiVue(8, "Vortex", "Dev", "2v2", Vues.MODE_MAM, "Nuketown", 30_000, List.of("Vortex", "Kaelis"),
                        List.of("Dev", "Ravenn")), true);
            }
            case "choix" -> mc.setScreen(new ChoixClasseScreen(new PaquetsDuels.ChoixS2C(5, classes, 1,
                    Component.translatable("duel.choix.manche", 2, Component.translatable("duel.role.attaque")), 0xE8C547, false, "Nova_77")));
            case "scores", "scores_mam" -> {
                boolean rd = q.equals("scores");
                mc.setScreen(new ScoresScreen(new Vues.Tableau(rd ? Vues.MODE_RD : Vues.MODE_MAM, "2v2", "Nuketown", "a",
                        List.of(new Vues.EquipeScore("a", "Dev & Kaelis", 0xE8C547, rd ? 4 : 30), new Vues.EquipeScore("b", "Vortex & Ravenn", 0x6FBF73, rd ? 2 : 24)),
                        List.of(new Vues.JoueurScore(moi, "Dev", "a", rd ? 9 : 17, rd ? 4 : 11, 5, false),
                                new Vues.JoueurScore(UUID.randomUUID(), "Kaelis", "a", rd ? 6 : 13, rd ? 5 : 13, 2, false),
                                new Vues.JoueurScore(UUID.randomUUID(), "Vortex", "b", rd ? 7 : 15, rd ? 8 : 15, 6, false),
                                new Vues.JoueurScore(UUID.randomUUID(), "Ravenn", "b", rd ? 2 : 9, rd ? 7 : 15, 1, false)),
                        rd ? List.of("a", "b", "a", "a", "b", "a") : List.of(), rd ? 4 : 30, 412, false)));
            }
            case "hud_rd" -> {
                mc.setScreen(null);
                double x = mc.player != null ? mc.player.getX() + 3 : 0, y = mc.player != null ? mc.player.getY() : 0, z = mc.player != null ? mc.player.getZ() : 0;
                DuelHud.rd(new Vues.RdVue(false, Vues.CHARGE_POSEE, "", false, "A", x, y, z, 32 * 20, Vues.ACTION_DESAMORCAGE, 50, 140, "",
                        new double[]{x, y, z, x - 12, y, z + 8}));
            }
            case "hud_porteur" -> {
                mc.setScreen(null);
                double x = mc.player != null ? mc.player.getX() : 0, y = mc.player != null ? mc.player.getY() : 0, z = mc.player != null ? mc.player.getZ() : 0;
                DuelHud.rd(new Vues.RdVue(true, Vues.CHARGE_PORTEE, "Dev", true, "", 0, 0, 0, 0, Vues.ACTION_AUCUNE, 0, 100, "poser",
                        new double[]{x + 10, y, z + 4, x - 12, y, z + 8}));
            }
            case "aucun" -> {
                DuelHud.vider();
                NotifDefis.vider(false);
                mc.setScreen(null);
            }
            default -> HeraliszMatchs.LOG.warn("[duels] duelFaux inconnu : {}", q);
        }
    }
}
