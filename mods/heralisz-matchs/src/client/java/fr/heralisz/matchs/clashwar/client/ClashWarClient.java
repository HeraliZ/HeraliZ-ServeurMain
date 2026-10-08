package fr.heralisz.matchs.clashwar.client;

import com.google.gson.JsonArray;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.clashwar.CwRegistres;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.clashwar.net.VueTableau;
import fr.heralisz.matchs.dev.MatchsHarness;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cote client du ClashWar (classe d'entree client de {@code Modules}) : rendu du mur « tableau du tournoi », texture
 * du tableau repeinte en debut d'image, HUD (toasts, zone), HUD vanilla masque derriere nos ecrans, et etapes du banc
 * d'essai ({@code cw*}).
 */
public final class ClashWarClient {
    private ClashWarClient() {
    }

    private static String langue = "";
    /** Banc : point de relachement du dernier glisser tenu. */
    private static int relacheX, relacheY;

    /** Appele par {@code ClashWar.construire} sur le client physique. */
    public static void initMod(IEventBus modBus) {
        modBus.addListener(ClashWarClient::rendus);
        MinecraftForge.EVENT_BUS.addListener(ClashWarClient::debutImage);
        MinecraftForge.EVENT_BUS.addListener(CwHud::rendreGui);
        MinecraftForge.EVENT_BUS.addListener(CwHud::rendreMonde);
        MinecraftForge.EVENT_BUS.addListener(ClashWarClient::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(ClashWarClient::masquerHud);
    }

    private static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(CwRegistres.TABLEAU_BE.get(), RenduMur::new);
    }

    private static void debutImage(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        String l = mc.getLanguageManager().getSelected();
        if (!l.equals(langue)) {
            langue = l;
            TextureTableau.invalider();
        }
        TextureTableau.avantImage();
    }

    private static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        CwClient.vider();
        TextureTableau.vider();
    }

    private static void masquerHud(RenderGuiOverlayEvent.Pre e) {
        var s = Minecraft.getInstance().screen;
        if (s instanceof EcranTableau || s instanceof EcranInscription) e.setCanceled(true);
    }

    // ================================================================== banc d'essai
    /**
     * Etapes du banc : {@code cwEcran} ("tableau" | "inscription_faux"), {@code cwGlisser} ([membre, equipe] : vrai
     * glisser-deposer souris dans l'ecran d'inscription ; {@code "tenir": true} = sans relacher, puis {@code cwRelacher}), {@code cwToast}, {@code cwZone} (zone factice autour du joueur),
     * {@code cwLog} (etat du tableau recu).
     */
    public static void etapesBanc(MatchsHarness.Registre r) {
        r.ajouter("cwEcran", (mc, v, etape) -> {
            String e = v.getAsString();
            if (e.equals("tableau")) mc.setScreen(new EcranTableau());
            else if (e.equals("inscription_faux")) mc.setScreen(new EcranInscription(inscriptionFactice(mc)));
        });
        r.ajouter("cwGlisser", (mc, v, etape) -> {
            if (!(mc.screen instanceof EcranInscription ecran)) return;
            JsonArray a = v.getAsJsonArray();
            int[] p = ecran.positions(a.get(0).getAsInt(), a.get(1).getAsInt());
            ecran.mouseClicked(p[0], p[1], 0);
            for (int i = 1; i <= 8; i++) {
                double x = p[0] + (p[2] - p[0]) * i / 8.0, y = p[1] + (p[3] - p[1]) * i / 8.0;
                ecran.mouseDragged(x, y, 0, 0, 0);
            }
            MatchsHarness.souris(mc, p[2], p[3]);
            relacheX = p[2];
            relacheY = p[3];
            if (!etape.has("tenir")) ecran.mouseReleased(p[2], p[3], 0);
            HeraliszMatchs.LOG.info("[clashwar] banc : glisser {} -> {} ({} {} -> {} {})", a.get(0), a.get(1), p[0], p[1], p[2], p[3]);
        });
        r.ajouter("cwEnregistrer", (mc, v, etape) -> {
            if (!(mc.screen instanceof EcranInscription ecran)) return;
            int[] b = ecran.boutonEnregistrerBanc();
            if (b == null) {
                HeraliszMatchs.LOG.warn("[clashwar] banc : pas de bouton d'inscription (lecture seule)");
                return;
            }
            MatchsHarness.souris(mc, b[0], b[1]);
            ecran.mouseClicked(b[0], b[1], 0);
            ecran.mouseReleased(b[0], b[1], 0);
            HeraliszMatchs.LOG.info("[clashwar] banc : clic sur Inscrire ({} {})", b[0], b[1]);
        });
        r.ajouter("cwRelacher", (mc, v, etape) -> {
            if (mc.screen instanceof EcranInscription ecran) ecran.mouseReleased(relacheX, relacheY, 0);
        });
        r.ajouter("cwToast", (mc, v, etape) -> CwHud.toast(Component.translatable("clashwar.toast.ouverture"),
                Component.translatable("clashwar.toast.ouverture.texte", "3v3"), 0x6FA8FF));
        r.ajouter("cwZone", (mc, v, etape) -> {
            if (mc.player == null) return;
            double rayon = v.isJsonPrimitive() ? v.getAsDouble() : 10;
            CwHud.zone(new CwPaquets.ZoneS2C(true, mc.player.getX() + 4, mc.player.getZ() - 6, mc.player.getY(), (float) rayon, (float) rayon, 200));
        });
        r.ajouter("cwSansZone", (mc, v, etape) -> CwHud.zone(CwPaquets.ZoneS2C.AUCUNE));
        r.ajouter("cwLog", (mc, v, etape) -> log());
    }

    private static void log() {
        VueTableau v = CwClient.vue();
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < v.tours().size(); t++) {
            sb.append(" T").append(t).append('[');
            for (VueTableau.Rencontre r : v.tours().get(t)) sb.append(r.a).append('v').append(r.b).append(':').append(r.statut).append('>').append(r.gagnant).append(' ');
            sb.append(']');
        }
        StringBuilder eq = new StringBuilder();
        for (VueTableau.Equipe e : v.equipes()) eq.append(e.libelle()).append(e.eliminee() ? "(x) " : " ");
        HeraliszMatchs.LOG.info("[clashwar] vue : edition {} etat {} format {} equipes {} [{}] tours{} champion {} texture {}", v.edition(), v.etat(),
                v.format(), v.equipes().size(), eq.toString().trim(), sb, v.champion() == null ? "-" : v.champion().libelle(), TextureTableau.prete());
    }

    /** Inscription factice (banc) : le joueur local + 6 membres inventes, equipe 1 deja formee. */
    private static CwPaquets.InscriptionS2C inscriptionFactice(Minecraft mc) {
        UUID moi = mc.player != null ? mc.player.getUUID() : UUID.randomUUID();
        String monNom = mc.player != null ? mc.player.getGameProfile().getName() : "Dev";
        String[] noms = {"Vortex", "Dziga", "Kali", "Orion", "Sable", "Nyx"};
        String[] grades = {"grade.haut_grade", "grade.membre", "grade.membre", "grade.recrue", "grade.membre", "grade.recrue"};
        List<CwPaquets.Membre> membres = new ArrayList<>();
        membres.add(new CwPaquets.Membre(moi, monNom, "grade.chef"));
        for (int i = 0; i < noms.length; i++) {
            membres.add(new CwPaquets.Membre(UUID.nameUUIDFromBytes(("cw-banc-" + noms[i]).getBytes(StandardCharsets.UTF_8)), noms[i], grades[i]));
        }
        List<List<UUID>> equipes = new ArrayList<>();
        List<UUID> e1 = new ArrayList<>();
        e1.add(moi);
        e1.add(membres.get(1).uuid());
        equipes.add(e1);
        List<Banniere> b = fr.heralisz.matchs.clashwar.BannieresBanc.bannieres();
        Banniere banniere = b.isEmpty() ? Banniere.DEFAUT : b.get(0);
        return new CwPaquets.InscriptionS2C(true, 12, 3, "3v3", 7 * 60_000L + 42_000L, true, "NOVA", "Nova", banniere.accent(), banniere, membres,
                equipes, 4, 7, 32, "", false);
    }
}
