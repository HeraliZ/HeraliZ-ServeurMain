package fr.heralisz.matchs.zombies.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.dev.MatchsHarness;
import fr.heralisz.matchs.zombies.client.boisson.Boisson;
import fr.heralisz.matchs.zombies.client.hud.EffetsHud;
import fr.heralisz.matchs.zombies.client.musique.MusiqueZombies;
import fr.heralisz.matchs.zombies.client.musique.PackMusiques;
import fr.heralisz.matchs.zombies.client.rendu.ModelesZb;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import fr.heralisz.matchs.zombies.jeu.api.MotifPoints;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Classe client d'entree des zombies (appelee par {@code Modules}) : etapes du banc d'essai. Le reste du client
 * (rendus, HUD, particules, musique, animation de boisson) est branche par annotations.
 * <ul>
 *     <li>{@code "zbHud"} : remplit le HUD avec un etat factice, sans partie (le module jeu ecrase cet etat des qu'il
 *     envoie le sien). Valeurs : {@code partie} (4 joueurs, manche 7, atouts, bonus, invite, objectif, materiaux,
 *     plaques), {@code manche}, {@code fin}, {@code speciale}, {@code points}, {@code bonus}, {@code courant},
 *     {@code refus}, {@code achat}, {@code reparer}, {@code aterre}, {@code reanimation}, {@code mort},
 *     {@code coequipier}, {@code objectif} (nouvel objectif apres une coche), {@code intro} (lore), {@code materiaux},
 *     {@code boite} (tirage legendaire), {@code pap}, {@code easter}, {@code aucun} ;</li>
 *     <li>{@code "zbBoisson"} : {@code "<atout>@<ms>"} fige l'animation de boisson a cet instant (captures),
 *     {@code "<atout>"} la joue, {@code "aucune"} la libere ;</li>
 *     <li>{@code "zbEchelle"} : echelle d'interface (1 a 6, 0 = auto) ;</li>
 *     <li>{@code "zbChat"} : faux = chat masque et vide (captures propres), vrai = chat de nouveau visible ;</li>
 *     <li>{@code "zbModeles"} : journal des modeles zombies v2 charges, des musiques trouvees et des sons integres ;</li>
 *     <li>{@code "zbMusique"} : moment musical simule ({@code manche_debut}, {@code silence}...).</li>
 * </ul>
 */
public final class ZombiesClient {
    private ZombiesClient() {
    }

    private static final UUID VORTEX = UUID.nameUUIDFromBytes("zb-vortex".getBytes()), DZIGA = UUID.nameUUIDFromBytes("zb-dziga".getBytes()),
            NOVA = UUID.nameUUIDFromBytes("zb-nova".getBytes());
    private static int manche = 7;
    private static boolean speciale;
    private static int etatMoi = HudZombies.DEBOUT, etatVortex = HudZombies.DEBOUT;
    private static int materiaux = 7;
    private static int[] bonus = new int[TypeBonus.values().length];

    public static void etapesBanc(MatchsHarness.Registre r) {
        r.ajouter("zbHud", ZombiesClient::hud);
        r.ajouter("zbBoisson", ZombiesClient::boisson);
        r.ajouter("zbEchelle", (mc, v, e) -> {
            mc.options.guiScale().set(Math.max(0, Math.min(6, v.getAsInt())));
            mc.resizeDisplay();
            HeraliszMatchs.LOG.info("[zombies] banc : echelle d'interface {} ({} x {})", v.getAsInt(), mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
        });
        // vol (creatif) : la camera reste ou le scenario la teleporte
        r.ajouter("zbVol", (mc, v, e) -> {
            if (mc.player == null) return;
            mc.player.getAbilities().flying = v.getAsBoolean() && mc.player.getAbilities().mayfly;
            mc.player.onUpdateAbilities();
        });
        // chat masque (et vide) : les retours de commandes ne recouvrent plus les captures
        r.ajouter("zbChat", (mc, v, e) -> {
            ZbClientForge.chatMasque = !v.getAsBoolean();
            if (!v.getAsBoolean()) mc.gui.getChat().clearMessages(false);
        });
        r.ajouter("zbModeles", (mc, v, e) -> HeraliszMatchs.LOG.info("[zombies] banc : {} modeles v2 charges ; musiques {} (dossier {}) ; sons integres : {}",
                ModelesZb.nombre(), PackMusiques.presents(), PackMusiques.dossier(), MusiqueZombies.etatIntegres()));
        r.ajouter("zbMusique", (mc, v, e) -> {
            try {
                MusiqueZombies.moment(MomentMusique.valueOf(v.getAsString().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                HeraliszMatchs.LOG.warn("[zombies] zbMusique inconnu : {}", v);
            }
        });
        BancZombies.enregistrer(r);
    }

    private static void boisson(Minecraft mc, JsonElement valeur, JsonObject etape) {
        String v = valeur.getAsString();
        if ("aucune".equals(v)) {
            Boisson.liberer();
            return;
        }
        String[] p = v.split("@");
        TypeAtout t = TypeAtout.parId(p[0]);
        if (t == null) t = TypeAtout.BLINDAGE;
        if (p.length > 1) Boisson.figer(t, Integer.parseInt(p[1].trim()));
        else Boisson.boire(t);
    }

    private static void hud(Minecraft mc, JsonElement valeur, JsonObject etape) {
        String v = valeur.getAsString();
        EffetsHud e = EffetsHud.INSTANCE;
        UUID moi = mc.player == null ? UUID.randomUUID() : mc.player.getUUID();
        HeraliszMatchs.LOG.info("[zombies] banc zbHud {} : objectif \"{}\" ({} ms), intro {} ligne(s)", v, HudZombies.objectif().getString(),
                HudZombies.objectifDepuisMs() > 0 ? System.currentTimeMillis() - HudZombies.objectifDepuisMs() : -1, HudZombies.intro().size());
        switch (v) {
            case "aucun" -> {
                HudZombies.vider();
                return;
            }
            case "partie" -> {
                manche = 7;
                speciale = false;
                etatMoi = etatVortex = HudZombies.DEBOUT;
                materiaux = 7;
                bonus = new int[TypeBonus.values().length];
                bonus[TypeBonus.POINTS_DOUBLES.ordinal()] = 24;
                bonus[TypeBonus.MORT_INSTANTANEE.ordinal()] = 4;
                e.mancheApres = manche;
                envoyer(moi);
                HudZombies.invite(Component.translatable("zombies.hud.banc.porte"), 750, true, -1);
                HudZombies.objectif("courant", Component.literal("Trouvez la salle de contrôle et rétablissez le courant"));
                return;
            }
            case "manche" -> {
                manche++;
                speciale = false;
                envoyer(moi);
                e.manche(manche, false, true);
            }
            case "fin" -> e.manche(manche, false, false);
            case "speciale" -> {
                manche++;
                speciale = true;
                envoyer(moi);
                e.manche(manche, true, true);
            }
            case "points" -> {
                e.points(moi, 10, MotifPoints.TOUCHE);
                e.points(moi, 60, MotifPoints.KILL);
                e.points(VORTEX, 100, MotifPoints.TETE);
                e.points(DZIGA, 130, MotifPoints.COUTEAU);
                e.points(NOVA, -950, MotifPoints.ACHAT);
            }
            case "bonus" -> e.bonus(TypeBonus.MORT_INSTANTANEE, "Vortex");
            case "courant" -> e.courant();
            case "refus" -> e.refus(Component.translatable("zombies.hud.banc.refus"));
            case "achat" -> e.achat(Component.literal("MK14"), 500);
            case "reparer" -> HudZombies.invite(Component.translatable("zombies.hud.banc.reparer"), 0, true, 55);
            case "aterre" -> {
                etatMoi = HudZombies.A_TERRE;
                envoyer(moi);
                HudZombies.invite(null, 0, false, -1);
            }
            case "reanimation" -> HudZombies.invite(null, 0, false, 62);
            case "mort" -> {
                etatMoi = HudZombies.MORT;
                envoyer(moi);
            }
            case "coequipier" -> {
                etatMoi = HudZombies.DEBOUT;
                etatVortex = HudZombies.A_TERRE;
                envoyer(moi);
                e.etatJoueur(VORTEX, "Vortex", HudZombies.A_TERRE);
            }
            case "objectif" -> {
                e.objectif(Component.literal("Améliorez une arme au Pack-a-Punch"), true);
                HudZombies.objectif("pack_a_punch", Component.literal("Améliorez une arme au Pack-a-Punch"));
            }
            case "intro" -> HudZombies.intro(List.of(Component.literal("Laboratoire N-07, sous la centrale. Le courant est coupé depuis trois jours."),
                    Component.literal("Ce qui grattait derrière les portes a fini par sortir."),
                    Component.literal("Tenez les fenêtres. Trouvez le courant.")));
            case "materiaux" -> {
                materiaux += 3;
                envoyer(moi);
                e.materiaux(moi, 3);
            }
            case "boite" -> e.boite(Rarete.LEGENDAIRE, Component.literal("AK-47 « Lame rouge »"));
            case "pap" -> e.packAPunch(moi, Component.literal("★ AK-47"));
            case "easter" -> e.easterEgg(2, 4, false);
            default -> HeraliszMatchs.LOG.warn("[zombies] etape zbHud inconnue : {}", v);
        }
    }

    private static void envoyer(UUID moi) {
        Minecraft mc = Minecraft.getInstance();
        String nom = mc.player == null ? "Moi" : mc.player.getGameProfile().getName();
        List<HudZombies.Joueur> js = new ArrayList<>();
        int tous = 0;
        for (TypeAtout a : TypeAtout.values()) tous |= a.bit();
        js.add(new HudZombies.Joueur(moi, nom, 0xF2F2F2, 4310, 58, 21, etatMoi == HudZombies.DEBOUT ? tous : 0, etatMoi,
                etatMoi == HudZombies.A_TERRE ? 23 : 0, materiaux, 1, 2));
        js.add(new HudZombies.Joueur(VORTEX, "Vortex", 0x5BA8FF, 2870, 41, 12, TypeAtout.VIGUEUR.bit(), etatVortex,
                etatVortex == HudZombies.A_TERRE ? 17 : 0, 3, 0, 0));
        js.add(new HudZombies.Joueur(DZIGA, "Dziga", 0xF2C14E, 1990, 30, 5, 0, HudZombies.DEBOUT, 0, 0, 0, 0));
        js.add(new HudZombies.Joueur(NOVA, "Nova", 0x6BD66B, 760, 9, 1, 0, HudZombies.DEBOUT, 0, 0, 0, 0));
        HudZombies.etat(true, manche, speciale, 24, true, js, bonus);
    }
}
