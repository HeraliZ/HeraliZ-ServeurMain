package fr.heraliz.tactique.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import fr.heraliz.noyau.client.kit.Anim;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * Munitions de l'arme en main (26/10, demande du propriétaire : « plus d'icône ni de nom sous les munitions, au même
 * niveau que le reste de l'interface ») : remplace, à l'affichage seulement, l'interface d'arme de TaCZ
 * ({@code tacz:tac_gun_hud_overlay} : chiffres agrandis, image de l'arme, version de TaCZ en petit dessous).
 * <p>UNE ligne sur le même verre penché que l'indicateur d'usure (HeraliZ Utils, {@code HudUsure} : hauteur 11, 8 unités
 * du bord droit, inclinaison 12°), posée juste au-dessus de lui : chargeur (Oswald 8, rouge sous un quart du chargeur,
 * comme TaCZ), « / réserve » atténué, mode de tir en petites capitales. Largeur fixe (le verre ne bouge pas à chaque
 * tir). Les chiffres sont ceux de TaCZ : la réserve vient de son propre calcul ({@code GunHudOverlay.handleCacheCount},
 * lu une fois par réflexion : inventaire, boîtes de munitions, une fois toutes les quelques images) ; sans lui, la
 * réserve s'affiche « — ». Couteaux : rien (déjà le cas, {@code KnifeHud}). Le réglage « GunHUDEnable » de TaCZ est
 * respecté. Rien ne change côté serveur.
 */
public final class HudMunitions {
    private HudMunitions() {
    }

    /** Interface d'arme de TaCZ, masquée pour toute arme. */
    private static final ResourceLocation TACZ_GUN_HUD = new ResourceLocation("tacz", "tac_gun_hud_overlay");
    /** Mêmes cotes que HudUsure (HeraliZ Utils) : hauteur, bord droit, milieu des cases DayZ depuis le bas. */
    static final float H = 11f, DROITE = 8f, BARRE_MILIEU = 12f;
    /** Écart avec l'indicateur d'usure posé dessous, marge intérieure, écarts entre les textes, inclinaison. */
    static final float ECART_USURE = 3f, PAD = 6f, ECART = 3f, ECART_MODE = 6f, INCLINAISON = 12f;
    static final float MODE_ESPACE = 0.8f;
    static final int VERRE_ALPHA = 150;
    private static final float PENTE = Formes.pente(INCLINAISON);

    private static final Anim.Fondu FONDU = new Anim.Fondu();
    private static final TexteHud CHARGEUR = new TexteHud(PoliceHud.CHIFFRE_PETIT);
    private static final TexteHud RESERVE = new TexteHud(PoliceHud.MINI);
    private static final TexteHud MODE = new TexteHud(PoliceHud.MINI_GRAS, MODE_ESPACE);
    /** Largeur des places réservées (« 000 », « / 0000 », plus long mode de tir), refaites si la langue change. */
    private static String langue;
    private static float lChargeur, lReserve, lMode;
    /** Dernières valeurs écrites (textes recalculés seulement si elles changent). */
    private static int chargeur = -1, reserve = -2;
    private static FireMode mode;
    private static boolean bas;

    /** Calcul de la réserve par TaCZ (réflexion, cherché une fois). */
    private static MethodHandle calcul;
    private static VarHandle reserveTacz, maxTacz;
    private static boolean cherche;

    /** Branché par la partie client du mod porteur (ClientTactique). */
    public static void brancher(IEventBus busMod) {
        busMod.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("munitions", HudMunitions::rendre));
        MinecraftForge.EVENT_BUS.addListener(HudMunitions::masquerTacz);
    }

    private static void masquerTacz(RenderGuiOverlayEvent.Pre e) {
        if (!TACZ_GUN_HUD.equals(e.getOverlay().id())) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && IGun.getIGunOrNull(p.getMainHandItem()) != null) e.setCanceled(true);
    }

    private static void rendre(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean montre = p != null && !p.isSpectator() && RenderConfig.GUN_HUD_ENABLE.get() && lire(p);
        float a = FONDU.maj(montre);
        if (!FONDU.visible() || a <= 0f || p == null || chargeur < 0 || mc.options.hideGui || mc.options.renderDebug) return;
        textes();

        float lw = (float) Math.ceil(PAD + lChargeur + ECART + lReserve + ECART_MODE + lMode + PAD);
        float x1 = w - DROITE;
        float x0 = Formes.caler(x1 - lw);
        float y0 = Formes.caler(h - BARRE_MILIEU - H / 2f - ECART_USURE - H), cy = y0 + H / 2f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        Formes.verrePenche(vc, m, x0, y0, x0 + lw, y0 + H, PENTE, VERRE_ALPHA, a);
        g.flush();

        float xc = x0 + PAD + lChargeur;   // chiffres du chargeur alignés à droite de leur place
        CHARGEUR.ajouter(g, xc, cy, Palette.argb(bas ? Palette.DANGER : Palette.TEXTE, a), TexteHud.DROITE, true);
        RESERVE.ajouter(g, xc + ECART, cy, Palette.argb(Palette.ATTENUE, a), TexteHud.GAUCHE, true);
        if (!MODE.vide()) MODE.ajouter(g, x0 + lw - PAD, cy, Palette.argb(Palette.clair(Palette.INFO, 0.3f), a), TexteHud.DROITE, true);
        g.flush();
    }

    /** Lit l'arme en main ; vrai s'il faut l'afficher. */
    private static boolean lire(LocalPlayer p) {
        ItemStack st = p.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(st);
        if (gun == null) return false;
        ResourceLocation id = gun.getGunId(st);
        if (id == null) return false;
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(id).orElse(null);
        if (index == null || COUTEAU.equals(index.getPojo().getData())) return false;
        GunData data = index.getGunData();
        boolean inventaire = gun.useInventoryAmmo(st);
        int res = reserveTacz(p, st, data, gun, inventaire);
        boolean balleChambre = gun.hasBulletInBarrel(st) && data.getBolt() != Bolt.OPEN_BOLT;
        int c = (inventaire ? Math.max(0, res) : gun.getCurrentAmmoCount(st)) + (balleChambre ? 1 : 0);
        c = Math.min(c, 999);
        int max = maxTacz();
        boolean infini = !inventaire && data.getReloadData().isInfinite();
        int r = inventaire ? -3 : infini ? -1 : res;
        FireMode fm = gun.getFireMode(st);
        if (c != chargeur) CHARGEUR.texte(Integer.toString(c));
        if (r != reserve) RESERVE.texte(r == -3 || r == -1 ? "" : r < 0 ? "/ —" : "/ " + Math.min(r, 9999));
        if (fm != mode) MODE.texte(nomMode(fm));
        chargeur = c;
        reserve = r;
        mode = fm;
        bas = max > 0 && c < max * 0.25;
        return true;
    }

    private static final ResourceLocation COUTEAU = new ResourceLocation("arkaniaz", "knife_data");

    /** Réserve comptée par TaCZ (-2 si son calcul est introuvable). */
    private static int reserveTacz(LocalPlayer p, ItemStack st, GunData data, IGun gun, boolean inventaire) {
        if (!cherche) {
            cherche = true;
            try {
                MethodHandles.Lookup l = MethodHandles.privateLookupIn(GunHudOverlay.class, MethodHandles.lookup());
                calcul = l.findStatic(GunHudOverlay.class, "handleCacheCount", MethodType.methodType(void.class,
                        LocalPlayer.class, ItemStack.class, GunData.class, IGun.class, boolean.class));
                reserveTacz = l.findStaticVarHandle(GunHudOverlay.class, "cacheInventoryAmmoCount", int.class);
                maxTacz = l.findStaticVarHandle(GunHudOverlay.class, "cacheMaxAmmoCount", int.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                calcul = null;
            }
        }
        if (calcul == null) return -2;
        try {
            calcul.invokeExact(p, st, data, gun, inventaire);
            return (int) reserveTacz.get();
        } catch (Throwable ex) {
            calcul = null;
            return -2;
        }
    }

    private static int maxTacz() {
        if (calcul == null || maxTacz == null) return 0;
        try {
            return (int) maxTacz.get();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String nomMode(FireMode fm) {
        if (fm == null || fm == FireMode.UNKNOWN) return "";
        return Language.getInstance().getOrDefault("hud.heraliz_tactique.tir." + fm.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Places réservées, refaites seulement si la langue change. */
    private static void textes() {
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        if (l.equals(langue)) return;
        langue = l;
        TexteHud t = new TexteHud(PoliceHud.CHIFFRE_PETIT);
        lChargeur = t.texte("000").largeur();
        t = new TexteHud(PoliceHud.MINI);
        lReserve = t.texte("/ 0000").largeur();
        lMode = 0f;
        TexteHud tm = new TexteHud(PoliceHud.MINI_GRAS, MODE_ESPACE);
        for (FireMode fm : FireMode.values()) lMode = Math.max(lMode, tm.texte(nomMode(fm)).largeur());
        MODE.oublier();
        mode = null;
    }
}
