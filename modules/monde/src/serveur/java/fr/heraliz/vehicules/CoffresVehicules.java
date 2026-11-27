package fr.heraliz.vehicules;

import com.google.common.collect.MapMaker;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.noyau.api.Matchs;
import fr.heraliz.vehicules.menu.CoffreVehiculeMenu;
import fr.heralisz.utils.coffres.CoffresPersonnels;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.ywzj.vehicle.api.event.VehicleAttackEvent;
import org.ywzj.vehicle.entity.misc.VehiclePart;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.item.VehicleItem;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Coffre HeraliZ des véhicules (27/11, demande du propriétaire : « pendant les raids, il faut amener un véhicule pour
 * emporter le butin »). Maj + clic droit sur un véhicule ouvre son coffre : moto 9 x 3 (27 cases), buggy 12 x 6 (72),
 * fourgon 15 x 6 (90), camion 24 x 6 (144) (réglage {@code [coffre] tailles} de {@code heraliz_vehicules-server.toml}).
 * <ul>
 *   <li><b>Ouverture</b> (clic sur l'entité, priorité haute, jamais après un refus de Spartan) : même en combat ; refusée
 *   à terre, en match (ou inventaire tenu par un match), en étant à bord d'un véhicule, sur une épave, véhicule qui roule,
 *   trop loin. Le clic est annulé pour les deux mains (aucune montée dans le véhicule) ; un objet du moteur en main
 *   (jerrican, chalumeau...) garde son usage d'origine. Spectateur : rien.</li>
 *   <li><b>Stockage</b> : {@link CoffreVehicule}, attaché à chaque véhicule du serveur (hors débris {@code VehiclePart}) et
 *   sauvegardé avec lui (« ForgeCaps ») : il suit le véhicule (tronçon déchargé, changement de monde, rechargement
 *   {@code /ywzj_vehicle reload}). Ce n'est PAS le coffre de 54 cases du moteur (toujours retiré : CoffreRetire) ; aucun
 *   entonnoir ni autre mod n'y a accès.</li>
 *   <li><b>Anti-duplication</b> : tous les écrans d'un véhicule pointent sur le MÊME objet conteneur ; l'écran reste ouvert
 *   tant que le véhicule n'est ni retiré ni détruit, que son conteneur est toujours celui-là, que le joueur est vivant,
 *   dans le même monde, à portée (un bloc de plus qu'à l'ouverture), pas à bord, ni à terre, ni en match, et que le
 *   véhicule est arrêté (Minecraft relit ces règles à chaque tick ET avant chaque clic dans l'écran). Véhicule qui quitte
 *   son monde (toutes raisons) ou détruit : les écrans ouverts sont fermés au tick suivant. Rien n'est jamais posé au sol
 *   quand le véhicule est retiré par le moteur ({@code DISCARDED} : le rechargement des véhicules recrée le même véhicule
 *   avec son contenu) ; seulement quand il est détruit ou tué.</li>
 *   <li><b>Destruction</b> (sans mixin sur AbstractVehicle : un mixin sur cette classe avait empêché le serveur de
 *   démarrer le 14/11) : chaque véhicule touché (évènement {@code VehicleAttackEvent} du moteur, envoyé au début de
 *   chaque coup) est revu au début ou à la fin du tick qui suit ; détruit (épave), son contenu est sorti et posé au sol à côté de lui (objets
 *   déplacés, sans limite de temps, journal du serveur : CoffreRetire.poser). Un coup sur une épave qui a encore quelque
 *   chose vide d'abord le coffre (une épave à 0 PV est retirée par le moteur dans le même coup). Filets : épave non vide
 *   vidée au contrôle de chaque seconde ({@link SuiviVehicules}) ; véhicule tué ({@code /kill}) : contenu posé au sol ;
 *   véhicule retiré d'un monde sans véhicules ({@link Regles}) : contenu posé avec lui.</li>
 *   <li><b>Objets refusés</b> : objets prêtés par un match ({@code HeraliszMatch}), objets à vie (Tactique, par réflexion :
 *   perdus si le véhicule était détruit), sacs à dos pleins (comme la planque).</li>
 *   <li>Le joueur n'est pas enregistré à la fermeture : le contenu est dans le véhicule, sauvegardé avec son tronçon.</li>
 * </ul>
 */
public final class CoffresVehicules {
    private static final ResourceLocation CLE = HeralizVehicules.id("coffre");

    /**
     * Coffre de chaque véhicule chargé côté serveur (rempli à la création de l'entité). Clés comparées par IDENTITÉ et
     * tenues faiblement (Guava) : Minecraft compare deux entités par leur numéro, et la copie d'un véhicule (rechargement,
     * changement de monde) est une autre entité avec son propre coffre.
     */
    private static final Map<AbstractVehicle, CoffreVehicule> COFFRES = new MapMaker().weakKeys().makeMap();
    /** Véhicules touchés pendant ce tick : revus à la fin du tick (détruits ?). Même comparaison par identité. */
    private static final Set<AbstractVehicle> TOUCHES = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());
    /** Coffres dont les écrans ouverts doivent être fermés au prochain passage. */
    private static final Set<CoffreVehicule> A_FERMER = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Objets déjà sortis d'un coffre, à poser au sol au prochain passage (jamais pendant un retrait d'entité). */
    private static final List<Depot> A_POSER = new ArrayList<>();

    private record Depot(ServerLevel level, Vec3 pos, List<ItemStack> objets, ResourceLocation vehicule) {
    }

    private CoffresVehicules() {
    }

    /** Écouteurs Forge (pendant la construction du mod : aucune classe du moteur des véhicules chargée par eux). */
    static void enregistrer(IEventBus bus) {
        bus.addGenericListener(Entity.class, CoffresVehicules::onAttache);
        bus.addListener(EventPriority.HIGH, false, PlayerInteractEvent.EntityInteract.class, CoffresVehicules::onClic);
        bus.addListener(CoffresVehicules::onDepart);
        bus.addListener(CoffresVehicules::onTick);
        bus.addListener(CoffresVehicules::onArret);
        bus.addListener(CoffresVehicules::onFin);
    }

    /** Évènement du moteur des véhicules : inscrit au démarrage du serveur (HeralizVehiculesServeur.auDemarrage). */
    static void enregistrerMoteur(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, false, VehicleAttackEvent.class, Moteur::onCoup);
    }

    /** Écouteur de l'évènement du moteur, dans sa propre classe (chargée seulement au démarrage du serveur). */
    private static final class Moteur {
        private static void onCoup(VehicleAttackEvent e) {
            CoffresVehicules.onCoup(e.getVehicle());
        }
    }

    // ------------------------------------------------------------------ réglages

    public static boolean actif() {
        try {
            return VehiculesConfig.COFFRE_ACTIF.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.COFFRE_ACTIF.getDefault();
        }
    }

    private static double distance() {
        try {
            return VehiculesConfig.COFFRE_DISTANCE.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.COFFRE_DISTANCE.getDefault();
        }
    }

    private static double vitesseMax() {
        try {
            return VehiculesConfig.COFFRE_VITESSE_MAX_KMH.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.COFFRE_VITESSE_MAX_KMH.getDefault();
        }
    }

    /** Colonnes et lignes du coffre de ce véhicule ({@code [coffre] tailles}), null : pas de coffre. */
    @Nullable
    static int[] taille(@Nullable ResourceLocation id) {
        if (id == null) return null;
        List<? extends String> l;
        try {
            l = VehiculesConfig.COFFRE_TAILLES.get();
        } catch (IllegalStateException pasEncoreCharge) {
            l = VehiculesConfig.COFFRE_TAILLES.getDefault();
        }
        String cle = id.toString();
        for (String s : l) {
            int eg = s.indexOf('=');
            if (eg < 0 || !s.substring(0, eg).trim().equals(cle)) continue;
            String[] p = s.substring(eg + 1).trim().toLowerCase(Locale.ROOT).split("x");
            if (p.length != 2) return null;
            try {
                int c = Integer.parseInt(p[0].trim()), r = Integer.parseInt(p[1].trim());
                if (c < 1 || r < 1 || c > CoffreVehiculeMenu.COLONNES_MAX || r > CoffreVehiculeMenu.LIGNES_MAX
                        || c * r > CoffreVehicule.CASES_MAX) {
                    return null;
                }
                return new int[]{c, r};
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ objets refusés

    private static Method aVie;
    private static boolean aVieCherche;

    /** Objet qui n'entre pas dans le coffre d'un véhicule : prêté par un match, à vie, sac à dos plein. */
    public static boolean interdit(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.hasTag() && s.getTag().contains(Matchs.NBT_PRET)) return true;
        if (CoffresPersonnels.estSacPlein(s)) return true;
        return estAVie(s);
    }

    private static boolean estAVie(ItemStack s) {
        if (!aVieCherche) {
            aVieCherche = true;
            try {
                aVie = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("estAVie", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError absent) {
                aVie = null;
            }
        }
        if (aVie == null) return false;
        try {
            return Boolean.TRUE.equals(aVie.invoke(null, s));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return true; // doute : refusé (un objet à vie perdu avec un véhicule détruit serait pire)
        }
    }

    // ------------------------------------------------------------------ stockage

    private static void onAttache(AttachCapabilitiesEvent<Entity> e) {
        if (!(e.getObject() instanceof AbstractVehicle v) || v instanceof VehiclePart) return;
        Entity ent = v;
        if (ent.level() == null || ent.level().isClientSide()) return;
        CoffreVehicule.Fournisseur f = new CoffreVehicule.Fournisseur();
        e.addCapability(CLE, f);
        COFFRES.put(v, f.coffre);
    }

    /** Le coffre de ce véhicule (serveur), null s'il n'en a pas (débris, copie du joueur). */
    @Nullable
    static CoffreVehicule coffre(AbstractVehicle v) {
        return COFFRES.get(v);
    }

    /** Sort tout le coffre du véhicule (objets rendus, jamais copiés) ; liste vide s'il n'en a pas. */
    static List<ItemStack> sortir(AbstractVehicle v) {
        CoffreVehicule c = COFFRES.get(v);
        if (c == null || c.vide()) return new ArrayList<>();
        A_FERMER.add(c);
        return c.sortir();
    }

    // ------------------------------------------------------------------ ouverture

    private static void onClic(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof AbstractVehicle v) || v instanceof VehiclePart) return;
        if (!p.isSecondaryUseActive() || !actif()) return;
        if (p.getMainHandItem().getItem() instanceof VehicleItem) return; // jerrican, chalumeau... : usage d'origine
        int[] t = taille(v.getVehicleId());
        CoffreVehicule c = COFFRES.get(v);
        if (t == null || c == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND || p.isSpectator()) return;
        String refus = refus(p, v, distance());
        if (refus != null) {
            p.displayClientMessage(texteRefus(refus), true);
            return;
        }
        ouvrir(p, v, c, t[0], t[1]);
    }

    /** Raison du refus (suffixe de clé), null si le joueur peut ouvrir le coffre de ce véhicule. */
    @Nullable
    private static String refus(ServerPlayer p, AbstractVehicle v, double portee) {
        if (v.isDestroyed()) return "epave";
        if (p.isPassenger()) return "a_bord";
        if (ATerre.aTerre(p)) return "a_terre";
        if (Matchs.inventaireRetenu(p)) return "match";
        if (SortieVitesse.vitesseKmh(v) > vitesseMax()) return "roule";
        if (!aPortee(p, v, portee)) return "loin";
        return null;
    }

    private static Component texteRefus(String refus) {
        String cle = "heraliz_vehicules.coffre.refus." + refus;
        return switch (refus) {
            case "epave" -> Component.translatableWithFallback(cle, "Ce véhicule est détruit : plus de coffre.");
            case "a_bord" -> Component.translatableWithFallback(cle, "Descends du véhicule pour ouvrir son coffre.");
            case "a_terre" -> Component.translatableWithFallback(cle, "Impossible d'ouvrir un coffre à terre.");
            case "match" -> Component.translatableWithFallback(cle, "Impossible d'ouvrir le coffre d'un véhicule en match.");
            case "roule" -> Component.translatableWithFallback(cle, "Le véhicule doit être arrêté pour ouvrir son coffre.");
            default -> Component.translatableWithFallback(cle, "Trop loin du véhicule.");
        };
    }

    /** Distance des yeux du joueur au point le plus proche de la boîte du véhicule. */
    private static boolean aPortee(Player p, AbstractVehicle v, double portee) {
        Entity ent = v;
        if (ent.isRemoved() || !p.isAlive() || p.level() != ent.level()) return false;
        AABB b = ent.getBoundingBox();
        Vec3 o = p.getEyePosition();
        double dx = Math.max(Math.max(b.minX - o.x, o.x - b.maxX), 0.0);
        double dy = Math.max(Math.max(b.minY - o.y, o.y - b.maxY), 0.0);
        double dz = Math.max(Math.max(b.minZ - o.z, o.z - b.maxZ), 0.0);
        return dx * dx + dy * dy + dz * dz <= portee * portee;
    }

    private static void ouvrir(ServerPlayer p, AbstractVehicle v, CoffreVehicule c, int colonnes, int lignes) {
        int n = colonnes * lignes;
        ranger(c, n);
        Component titre = titre(v.getVehicleId());
        NetworkHooks.openScreen(p, new SimpleMenuProvider(
                (id, inv, joueur) -> new CoffreVehiculeMenu(id, inv, c, colonnes, lignes, j -> garde(j, v, c)), titre), buf -> {
            buf.writeVarInt(colonnes);
            buf.writeVarInt(lignes);
        });
    }

    /**
     * Taille baissée par le réglage : les objets des cases cachées (au-delà de {@code n}) sont DÉPLACÉS dans les cases
     * libres montrées ; ce qui ne tient pas reste gardé, caché (posé au sol à la destruction).
     */
    private static void ranger(CoffreVehicule c, int n) {
        int libre = 0;
        for (int i = n; i < c.getContainerSize(); i++) {
            if (c.getItem(i).isEmpty()) continue;
            while (libre < n && !c.getItem(libre).isEmpty()) libre++;
            if (libre >= n) return;
            c.setItem(libre, c.removeItemNoUpdate(i));
        }
    }

    private static Component titre(@Nullable ResourceLocation id) {
        String v = id == null ? "" : id.toString();
        return switch (v) {
            case "ywzj_vehicle:motorcycle" -> Component.translatableWithFallback("heraliz_vehicules.coffre.titre.moto", "Coffre de la moto");
            case "ywzj_vehicle:atv_mrzr4" -> Component.translatableWithFallback("heraliz_vehicules.coffre.titre.buggy", "Coffre du buggy");
            case "ywzj_vehicle:hiace" -> Component.translatableWithFallback("heraliz_vehicules.coffre.titre.fourgon", "Coffre du fourgon");
            case "ywzj_vehicle:dump_truck" -> Component.translatableWithFallback("heraliz_vehicules.coffre.titre.camion", "Coffre du camion");
            default -> Component.translatableWithFallback("heraliz_vehicules.coffre.titre", "Coffre du véhicule");
        };
    }

    /** Règles pour garder l'écran ouvert (relues à chaque tick et avant chaque clic). */
    private static boolean garde(Player j, AbstractVehicle v, CoffreVehicule c) {
        if (!(j instanceof ServerPlayer sp)) return false;
        Entity ent = v;
        if (ent.isRemoved() || v.isDestroyed() || COFFRES.get(v) != c || A_FERMER.contains(c) || !actif()) return false;
        if (!sp.isAlive() || sp.isSpectator() || sp.isPassenger()) return false;
        if (ATerre.aTerre(sp) || Matchs.inventaireRetenu(sp)) return false;
        if (SortieVitesse.vitesseKmh(v) > vitesseMax()) return false;
        return aPortee(sp, v, distance() + 1.0);
    }

    // ------------------------------------------------------------------ destruction, départ

    /** Début d'un coup sur un véhicule (évènement non annulé : priorité la plus basse). */
    private static void onCoup(@Nullable AbstractVehicle v) {
        if (v == null || v instanceof VehiclePart) return;
        Entity ent = v;
        if (!(ent.level() instanceof ServerLevel)) return;
        if (v.isDestroyed()) {
            // coup sur une épave : le moteur la retire dans ce coup si ses PV tombent à 0 ; le coffre est vidé avant
            viderAuSol(v);
        } else {
            TOUCHES.add(v);
        }
    }

    /** Épave encore pleine (contrôle de chaque seconde, SuiviVehicules) : vidée au sol. */
    static void entretenir(AbstractVehicle v) {
        if (v.isDestroyed()) viderAuSol(v);
    }

    /** Sort tout le coffre maintenant et le pose au sol à côté du véhicule au prochain passage. */
    private static void viderAuSol(AbstractVehicle v) {
        Entity ent = v;
        if (!(ent.level() instanceof ServerLevel level)) return;
        CoffreVehicule c = COFFRES.get(v);
        if (c == null) return;
        A_FERMER.add(c);
        if (c.vide()) return;
        List<ItemStack> objets = c.sortir();
        A_POSER.add(new Depot(level, CoffreRetire.aCote(v), objets, v.getVehicleId()));
    }

    private static void onDepart(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractVehicle v) || v instanceof VehiclePart) return;
        CoffreVehicule c = COFFRES.get(v);
        if (c == null) return;
        A_FERMER.add(c);
        // tué (/kill) : plus jamais sauvegardé, le contenu tombe ; retiré par le moteur (DISCARDED : rechargement des
        // véhicules, épave finie), déchargé ou changé de monde : le contenu reste avec la copie sauvegardée du véhicule
        Entity ent = v;
        if (ent.getRemovalReason() == Entity.RemovalReason.KILLED) viderAuSol(v);
    }

    private static void onTick(TickEvent.ServerTickEvent e) {
        if (!TOUCHES.isEmpty()) { // début et fin du tick : un coup reçu entre deux ticks (tir d'un joueur) est vu au début
            for (AbstractVehicle v : new ArrayList<>(TOUCHES)) {
                Entity ent = v;
                if (!ent.isRemoved() && v.isDestroyed()) viderAuSol(v);
            }
            TOUCHES.clear();
        }
        if (!A_FERMER.isEmpty()) fermer(e.getServer());
        if (!A_POSER.isEmpty()) poser();
    }

    /** Ferme les écrans ouverts sur les coffres de {@link #A_FERMER}. */
    private static void fermer(@Nullable MinecraftServer srv) {
        List<CoffreVehicule> liste = new ArrayList<>(A_FERMER);
        A_FERMER.clear();
        if (srv == null) return;
        boolean ouverts = false;
        for (CoffreVehicule c : liste) ouverts |= c.ouverts() > 0;
        if (!ouverts) return;
        for (ServerPlayer sp : new ArrayList<>(srv.getPlayerList().getPlayers())) {
            if (sp.containerMenu instanceof CoffreVehiculeMenu m && liste.stream().anyMatch(c -> c == m.conteneur())) {
                sp.closeContainer();
            }
        }
    }

    private static void poser() {
        List<Depot> liste = new ArrayList<>(A_POSER);
        A_POSER.clear();
        for (Depot d : liste) CoffreRetire.poser(d.level(), d.pos(), d.objets(), d.vehicule());
    }

    /** Arrêt du serveur : ce qui attend d'être posé l'est avant la dernière sauvegarde des mondes. */
    private static void onArret(ServerStoppingEvent e) {
        if (!A_POSER.isEmpty()) poser();
    }

    private static void onFin(ServerStoppedEvent e) {
        COFFRES.clear();
        TOUCHES.clear();
        A_FERMER.clear();
        A_POSER.clear();
    }
}
