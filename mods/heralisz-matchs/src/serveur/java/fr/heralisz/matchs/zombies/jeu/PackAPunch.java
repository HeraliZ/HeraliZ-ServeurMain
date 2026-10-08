package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.jeu.api.PlanZombies;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Machine Pack-a-Punch (ZOMBIES_V2_SPEC §2), une par carte, a trois niveaux facon CoD Cold War : chaque passage monte
 * l'arme tenue d'un niveau (1 -> 2 -> 3, refus au-dela), pour 5 000 / 15 000 / 30 000 points par defaut
 * ({@code regles.json:pack_a_punch.niveaux}), courant requis. L'arme disparait de l'inventaire, la machine travaille
 * {@link ZombiesApi#PAP_TICKS} ticks, puis l'arme amelioree sort et attend son proprietaire (touche F) ; passe le delai,
 * elle lui est rendue d'office (jamais perdue). Amelioration reelle : marqueurs {@link ZombiesApi#NBT_PAP} et
 * {@link ZombiesApi#NBT_PAP_NIVEAU}, degats x2 / x3 / x4 cote serveur pour cette arme (voir {@code PartieZombies.surTir}),
 * meilleur chargeur grande capacite compatible monte des le niveau 1 si l'emplacement est libre, reserve agrandie et
 * pleine a chaque niveau, nom « ★ ... » / « ★★ ... » / « ★★★ ... », camo {@code pap_1..3} de heralisz_camo s'il est
 * installe et connait ce camo (sinon lueur). Fil serveur.
 */
final class PackAPunch {
    enum Etat { ETEINTE, PRETE, AMELIORATION, OFFERTE }

    /** Chargeurs grande capacite essayes dans l'ordre (le premier compatible avec l'arme est monte). */
    private static final List<String> CHARGEURS = List.of(
            "tacz:extended_mag_3", "tacz:light_extended_mag_3", "tacz:sniper_extended_mag_3", "wmags:wmag_rpk", "wmags:wmag_12g",
            "wmags:wmag_pistols", "maxstuff:dmr_extended_mag_3", "tacz:extended_mag_2", "tacz:light_extended_mag_2",
            "tacz:sniper_extended_mag_2", "maxstuff:speed_mag", "tacz:extended_mag_1", "tacz:light_extended_mag_1", "tacz:sniper_extended_mag_1");
    /** Couleur du nom des armes ameliorees, par niveau (lavande, violet, magenta). */
    private static final int[] COULEURS = {0xE59BFF, 0xC77DFF, 0xFF5FD2};
    /** Nom d'origine de l'arme (JSON d'un composant, sans etoile), garde a la premiere amelioration. */
    private static final String NBT_NOM = "HeraliszZbPapNom";
    /** Etiquette du camo de heralisz_camo (voir {@code fr.heralisz.camo.CamoData.KEY}). */
    private static final String NBT_CAMO = "HeraliszCamo";
    /** Camos connus de heralisz_camo (id -> present), resolus une fois par reflexion. */
    private static final Map<String, Boolean> CAMOS = new ConcurrentHashMap<>();
    @Nullable
    private static Method camoParId;
    private static boolean camoCherche;
    /** Banc d'essai : fait comme si heralisz_camo connaissait les camos du Pack-a-Punch (le mod n'est pas charge au banc). */
    static volatile boolean camoForce;

    private final PartieZombies partie;
    final PlanZombies.PackAPunch machine;
    Etat etat = Etat.ETEINTE;
    @Nullable
    UUID proprietaire;
    ItemStack arme = ItemStack.EMPTY;
    int slot = -1;
    int ticks;
    /** Niveau que prendra l'arme en cours d'amelioration. */
    int niveauCible;
    @Nullable
    private Display.ItemDisplay affichage;

    PackAPunch(PartieZombies partie, PlanZombies.PackAPunch machine) {
        this.partie = partie;
        this.machine = machine;
    }

    /** Point de l'interaction (devant la machine, a hauteur de poitrine). */
    Vec3 centre() {
        return new Vec3(machine.pos().getX() + 0.5 + machine.face().getStepX() * 0.5, machine.pos().getY() + 1.0,
                machine.pos().getZ() + 0.5 + machine.face().getStepZ() * 0.5);
    }

    /** Cout pour amener une arme au niveau {@code niveau} (1..{@link #niveauMax()}) ; le niveau 1 peut venir de la carte. */
    int cout(int niveau) {
        List<ConfigZombies.NiveauPap> l = ConfigZombies.niveauxPap();
        int n = Math.max(1, Math.min(l.size(), niveau));
        if (n == 1 && machine.cout() > 0 && !ConfigZombies.coutPapConfigure()) return machine.cout();
        return l.get(n - 1).cout();
    }

    /** Niveau le plus haut (3 par defaut). */
    static int niveauMax() {
        return Math.min(ZombiesApi.PAP_NIVEAU_MAX, ConfigZombies.niveauxPap().size());
    }

    /** Multiplicateur de degats de l'arme selon son niveau (1 si elle n'est pas amelioree). */
    static double multiplicateur(ItemStack arme) {
        int n = ZombiesApi.niveauPap(arme);
        if (n <= 0) return 1.0;
        List<ConfigZombies.NiveauPap> l = ConfigZombies.niveauxPap();
        return l.get(Math.min(l.size(), n) - 1).degats();
    }

    /** Chiffre romain d'un niveau (I, II, III). */
    static String romain(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    int dureeTicks() {
        return Math.max(20, (int) Math.round(ConfigZombies.reel("pack_a_punch", "secondes", ZombiesApi.PAP_TICKS / 20.0) * 20));
    }

    int offreTicks() {
        return Math.max(60, ConfigZombies.entier("pack_a_punch", "offre_secondes", 15) * 20);
    }

    boolean libre() {
        return etat == Etat.PRETE;
    }

    /** Vrai si l'arme amelioree de ce joueur attend dans la machine. */
    boolean offerteA(UUID joueur) {
        return etat == Etat.OFFERTE && joueur.equals(proprietaire);
    }

    /** Vrai si l'arme de ce joueur est en cours d'amelioration. */
    boolean enCoursPour(UUID joueur) {
        return etat == Etat.AMELIORATION && joueur.equals(proprietaire);
    }

    /** Progression 0..100 de l'amelioration en cours. */
    int progression() {
        return etat == Etat.AMELIORATION ? Math.min(100, ticks * 100 / dureeTicks()) : -1;
    }

    /** Courant retabli ou coupe. */
    void courant(boolean actif) {
        if (actif && etat == Etat.ETEINTE) {
            etat = Etat.PRETE;
            decor(ZombiesDecor.EtatPap.PRETE, ItemStack.EMPTY);
        } else if (!actif && etat == Etat.PRETE) {
            etat = Etat.ETEINTE;
            decor(ZombiesDecor.EtatPap.ETEINTE, ItemStack.EMPTY);
        }
    }

    /** Debut de partie : machine posee eteinte (ou allumee si la carte n'a pas de courant). */
    void installer(boolean courant) {
        etat = courant ? Etat.PRETE : Etat.ETEINTE;
        decor(courant ? ZombiesDecor.EtatPap.PRETE : ZombiesDecor.EtatPap.ETEINTE, ItemStack.EMPTY);
    }

    /** L'arme du joueur entre dans la machine (points deja payes, arme deja retiree de l'emplacement {@code slot}). */
    void inserer(ServerPlayer p, ItemStack original, int slot) {
        proprietaire = p.getUUID();
        arme = original;
        this.slot = slot;
        ticks = 0;
        niveauCible = ZombiesApi.niveauPap(original) + 1;
        etat = Etat.AMELIORATION;
        decor(ZombiesDecor.EtatPap.AMELIORATION, original);
        if (!ZombiesApi.decor().packAPunchAfficheArme()) {
            Vec3 c = centre();
            affichage = Affichages.objet(partie.niveau(), c.x, c.y, c.z, original, 0.75f);
            Affichages.lueur(affichage, 0x8A5CFF);
        }
        partie.son(ZombiesApi.Sons.PAP_AMELIORATION, centre().x, centre().y, centre().z, 1.2f);
    }

    void tick() {
        switch (etat) {
            case AMELIORATION -> {
                ticks++;
                // l'arme recule dans la machine puis revient
                if (affichage != null) {
                    double t = Math.min(1.0, ticks / (double) dureeTicks());
                    double recul = Math.sin(Math.PI * t) * 0.55;
                    Vec3 c = centre();
                    Affichages.placer(affichage, c.x - machine.face().getStepX() * recul, c.y, c.z - machine.face().getStepZ() * recul);
                }
                if (ticks >= dureeTicks()) sortir();
            }
            case OFFERTE -> {
                ticks++;
                if (ticks >= offreTicks()) {
                    // jamais perdue : rendue d'office a son proprietaire
                    ServerPlayer p = proprietaire == null ? null : partie.joueur(proprietaire);
                    if (p != null) rendre(p);
                    else vider();
                }
            }
            default -> {
            }
        }
    }

    private void sortir() {
        arme = ameliorer(arme);
        etat = Etat.OFFERTE;
        ticks = 0;
        if (affichage != null) {
            Affichages.changer(affichage, arme);
            Affichages.lueur(affichage, couleur(ZombiesApi.niveauPap(arme)));
        }
        decor(ZombiesDecor.EtatPap.OFFERTE, arme);
        partie.son(ZombiesApi.Sons.PAP_PRETE, centre().x, centre().y, centre().z, 1.2f);
    }

    /** Le proprietaire reprend son arme amelioree. */
    void rendre(ServerPlayer p) {
        if (etat != Etat.OFFERTE || !p.getUUID().equals(proprietaire)) return;
        ItemStack a = arme;
        int s = slot;
        vider();
        partie.rendreArmePap(p, a, s);
    }

    private void vider() {
        Affichages.retirer(affichage);
        affichage = null;
        arme = ItemStack.EMPTY;
        proprietaire = null;
        slot = -1;
        ticks = 0;
        niveauCible = 0;
        etat = partie.courant() ? Etat.PRETE : Etat.ETEINTE;
        decor(ZombiesDecor.EtatPap.REPRISE, ItemStack.EMPTY);
        if (etat == Etat.PRETE) decor(ZombiesDecor.EtatPap.PRETE, ItemStack.EMPTY);
    }

    /** Fin de partie : rend l'arme en cours a son proprietaire s'il est la, retire l'affichage. */
    void nettoyer() {
        if (etat == Etat.AMELIORATION) arme = ameliorer(arme);
        if ((etat == Etat.AMELIORATION || etat == Etat.OFFERTE) && proprietaire != null) {
            ServerPlayer p = partie.joueur(proprietaire);
            if (p != null) {
                etat = Etat.OFFERTE;
                rendre(p);
            }
        }
        Affichages.retirer(affichage);
        affichage = null;
    }

    private void decor(ZombiesDecor.EtatPap e, ItemStack a) {
        try {
            ZombiesApi.decor().packAPunch(partie.instance(), machine, e, a);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.packAPunch", ex);
        }
    }

    // ------------------------------------------------------------------ amelioration
    private static int couleur(int niveau) {
        return COULEURS[Math.max(0, Math.min(COULEURS.length, niveau) - 1)];
    }

    /**
     * Copie de l'arme montee d'un niveau : marqueurs, chargeur grande capacite (si l'emplacement est libre ou tient un
     * simple chargeur), reserve du niveau pleine, nom « ★★ ... » (nom d'origine sans ses anciennes etoiles), lore,
     * camo du niveau ou lueur. Sans effet sur une arme deja au niveau maximal. Une ancienne arme qui n'a que
     * {@link ZombiesApi#NBT_PAP} est au niveau 1 : elle passe au niveau 2.
     */
    static ItemStack ameliorer(ItemStack original) {
        if (original.isEmpty()) return original;
        int avant = ZombiesApi.niveauPap(original);
        int n = avant + 1;
        if (n > niveauMax()) return original;
        ItemStack a = original.copy();
        Component nom = nomOrigine(a);
        String em = Tacz.accessoire(a, "extended_mag");
        if (em == null || !em.contains("ammo_mod")) {
            for (String c : CHARGEURS) {
                if (c.equals(em)) break;
                if (Tacz.monter(a, c)) break;
            }
        }
        List<ConfigZombies.NiveauPap> niveaux = ConfigZombies.niveauxPap();
        Tacz.reserve(a, niveaux.get(Math.min(niveaux.size(), n) - 1).chargeurs());
        Tacz.remplir(a);
        CompoundTag t = a.getOrCreateTag();
        t.putBoolean(ZombiesApi.NBT_PAP, true);
        t.putInt(ZombiesApi.NBT_PAP_NIVEAU, n);
        t.putString(NBT_NOM, Component.Serializer.toJson(nom));
        int coul = couleur(n);
        // les etoiles dans la police par defaut (les polices DM Sans des HUD n'ont pas ce glyphe), le nom suit la police du HUD
        MutableComponent nn = Component.empty().append(Component.literal("★".repeat(n) + " ").withStyle(s -> s.withFont(Style.DEFAULT_FONT)))
                .append(nom).withStyle(s -> s.withColor(coul).withItalic(false).withBold(true));
        a.setHoverName(nn);
        CompoundTag display = a.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        String mult = String.format(Locale.ROOT, "%s", niveaux.get(Math.min(niveaux.size(), n) - 1).degats()).replaceAll("\\.0$", "");
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("zombies.jeu.pap.lore_niveau", romain(n), mult)
                .withStyle(s -> s.withColor(coul).withItalic(false)))));
        display.put("Lore", lore);
        // camo du niveau (heralisz_camo), sinon lueur d'enchantement
        List<String> camos = ConfigZombies.camosPap();
        String camo = n - 1 < camos.size() ? camos.get(n - 1) : "";
        if (camoDisponible(camo)) {
            t.putString(NBT_CAMO, camo);
            t.remove("Enchantments");
        } else if (!t.contains(NBT_CAMO) || camo.isEmpty()) {
            ListTag ench = new ListTag();
            ench.add(new CompoundTag());
            t.put("Enchantments", ench);
        }
        HeraliszMatchs.LOG.info("[zombies] Pack-a-Punch : {} niveau {} -> {} (degats x{}, chargeur {}, reserve {}, camo {})", Tacz.gunId(a), avant, n,
                mult, Tacz.chargeur(a), Tacz.reserveActuelle(a), t.contains(NBT_CAMO) ? t.getString(NBT_CAMO) : "aucun (lueur)");
        return a;
    }

    /**
     * Conversion a l'etabli d'une arme deja amelioree : son nom d'origine devient {@code origine} (nom de la nouvelle arme)
     * et le nom affiche « ★… » est refait au meme niveau, dans la meme presentation.
     */
    static void renommer(ItemStack a, Component origine) {
        int n = ZombiesApi.niveauPap(a);
        CompoundTag t = a.getOrCreateTag();
        t.putString(NBT_NOM, Component.Serializer.toJson(origine));
        if (n <= 0) return;
        int coul = couleur(n);
        a.setHoverName(Component.empty().append(Component.literal("★".repeat(n) + " ").withStyle(s -> s.withFont(Style.DEFAULT_FONT)))
                .append(origine).withStyle(s -> s.withColor(coul).withItalic(false).withBold(true)));
    }

    /** Nom de l'arme avant toute amelioration : garde en NBT, sinon le nom affiche sans ses etoiles (anciennes armes). */
    private static Component nomOrigine(ItemStack a) {
        CompoundTag t = a.getTag();
        if (t != null && t.contains(NBT_NOM)) {
            try {
                Component c = Component.Serializer.fromJson(t.getString(NBT_NOM));
                if (c != null) return c;
            } catch (RuntimeException ignored) {
            }
        }
        if (ZombiesApi.estAmelioree(a)) return Component.literal(a.getHoverName().getString().replace("★", "").trim());
        return a.getHoverName().copy();
    }

    /**
     * Vrai si heralisz_camo est charge et connait ce camo ({@code fr.heralisz.camo.Camo.byId(id) != null}, par reflexion
     * douce, resultat garde). Au banc, {@link #camoForce} le simule.
     */
    static boolean camoDisponible(@Nullable String id) {
        if (id == null || id.isEmpty()) return false;
        Boolean b = CAMOS.get(id);
        if (b == null) {
            b = chercherCamo(id);
            CAMOS.put(id, b);
        }
        return b || camoForce;
    }

    private static synchronized boolean chercherCamo(String id) {
        try {
            if (ModList.get() == null || !ModList.get().isLoaded("heralisz_camo")) return false;
            if (!camoCherche) {
                camoCherche = true;
                Class<?> c = Class.forName("fr.heralisz.camo.Camo");
                camoParId = c.getMethod("byId", String.class);
            }
            return camoParId != null && camoParId.invoke(null, id) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[zombies] heralisz_camo illisible ({}) : lueur a la place du camo {}", ex.toString(), id);
            return false;
        }
    }
}
