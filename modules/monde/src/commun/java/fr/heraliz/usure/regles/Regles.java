package fr.heraliz.usure.regles;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Toutes les regles de l'usure, lues du fichier {@code serverconfig/heraliz_usure-server.toml} (voir
 * {@code fr.heraliz.usure.UsureConfig}, partie serveur) et envoyees telles quelles aux joueurs (quelques centaines
 * d'octets, a la connexion et a chaque changement). Une instance publiee n'est plus modifiee : l'ecran d'administration
 * travaille sur une {@link #copie()}.
 */
public final class Regles {
    public static final int NB_KITS = 3;

    // ---------------------------------------------------------------- general
    public boolean actif = true;
    /** Usure aussi en mode creatif (non par defaut : les admins testent librement). */
    public boolean usureCreatif = false;
    /** Aucune usure pendant un match (drapeau {@code heralisz:en_match}) ni sur une arme pretee. */
    public boolean sansUsureEnMatch = true;
    /** Le petit indicateur de l'arme en main (en bas a droite) est permis (chaque joueur peut aussi le couper chez lui). */
    public boolean hudAutorise = true;
    public List<String> dimensionsSansUsure = new ArrayList<>(List.of("heraliz_clashwar:arenes"));
    /** Munitions des armes « corps a corps » (couteaux faits en armes TaCZ) : ces armes vont dans la categorie Corps a corps. */
    public List<String> munitionsMelee = new ArrayList<>(List.of("arkaniaz:melee_weapon"));

    // ---------------------------------------------------------------- categories (index = Categorie.ordinal())
    public final float[] usure = new float[Categorie.TOUTES.length];
    public final float[] pluie = new float[Categorie.TOUTES.length];
    public final float[] eau = new float[Categorie.TOUTES.length];
    public final float[] silencieux = new float[Categorie.TOUTES.length];

    // ---------------------------------------------------------------- etats
    /** Seuils en % d'etat : Neuf a partir de, Bon a partir de, Use a partir de (en dessous : Abime ; 0 % : Ruine). */
    public float seuilNeuf = 90, seuilBon = 65, seuilUse = 35;

    // ---------------------------------------------------------------- reparation (kits : fortune, standard, armurier)
    // 26/10 (demande du proprietaire) : fortune +7 % x 1, kit +18 % x 2, armurier +45 % x 3, jusqu'a 100 %, armes ruinees
    // comprises (avant : +15 / +35 / +60 %, plafonds 65 / 90 / 100 %, 2 / 3 / 5 utilisations)
    public final float[] kitGain = {7, 18, 45};
    public final float[] kitPlafond = {100, 100, 100};
    public final int[] kitUtilisations = {1, 2, 3};
    public final boolean[] kitRuinee = {true, true, true};
    /** Points d'etat maximal perdus a chaque reparation (0 = jamais), et plancher de l'etat maximal. */
    public float perteMaxParReparation = 4, maxMinimum = 40;

    // ---------------------------------------------------------------- armes et accessoires
    public final List<Surcharge> surcharges = new ArrayList<>();
    /** Surplus d'usure (%) par accessoire monte (identifiant d'accessoire TaCZ). */
    public final Map<String, Float> accessoires = new LinkedHashMap<>();

    /** Profils deja calcules, par identifiant d'arme (texte du NBT « GunId »). */
    private final HashMap<String, Profil> cache = new HashMap<>();

    public Regles() {
        for (Categorie c : Categorie.TOUTES) {
            int i = c.ordinal();
            usure[i] = c.usure;
            pluie[i] = c.pluie;
            eau[i] = c.eau;
            silencieux[i] = c.silencieux;
        }
    }

    /** Regles par defaut, avec les surcharges de depart (couteaux et armes des zombies exclus, quelques DMR). */
    public static Regles defauts() {
        Regles r = new Regles();
        for (String s : SURCHARGES_DEFAUT) r.surcharges.add(Surcharge.lire(s));
        return r;
    }

    public static final List<String> SURCHARGES_DEFAUT = List.of(
            "heralisz_skins:*__* = exclue",
            "tacz:mk14 = categorie:dmr",
            "tacz:spr15hb = categorie:dmr",
            "maxstuff:hk417 = categorie:dmr");

    // ---------------------------------------------------------------- profils
    /** Profil d'une arme par le texte de son identifiant (NBT « GunId »). Fil unique par cote (serveur ou client). */
    public Profil profil(String gunId) {
        Profil p = cache.get(gunId);
        if (p != null) return p;
        p = calculer(gunId);
        if (p != null) {
            cache.put(gunId, p);
            return p;
        }
        return Profil.EXCLUE;
    }

    /** Oublie les profils (packs d'armes recharges). */
    public void viderCache() {
        cache.clear();
    }

    /** Surcharge qui s'applique a l'arme : celle qui la nomme exactement, sinon le premier groupe (« * ») qui la couvre. */
    public Surcharge surchargePour(String gunId) {
        for (Surcharge s : surcharges) if (!s.groupe() && s.motif.equals(gunId)) return s;
        for (Surcharge s : surcharges) if (s.groupe() && s.couvre(gunId)) return s;
        return null;
    }

    /** null si l'arme n'est pas (encore) connue de TaCZ : rien n'est mis en cache. */
    private Profil calculer(String gunId) {
        ResourceLocation id = ResourceLocation.tryParse(gunId);
        CommonGunIndex index = id == null ? null : TimelessAPI.getCommonGunIndex(id).orElse(null);
        if (index == null) return null;
        String type = index.getType() == null ? "" : index.getType().toLowerCase(Locale.ROOT);
        GunData data = index.getGunData();
        Surcharge s = surchargePour(gunId);
        Categorie cat = s != null && s.categorie != null ? s.categorie : categorieAuto(type, data);
        int c = cat.ordinal();
        return new Profil(cat, s != null && s.exclue,
                val(s == null ? Float.NaN : s.usure, usure[c]),
                val(s == null ? Float.NaN : s.pluie, pluie[c]),
                val(s == null ? Float.NaN : s.eau, eau[c]),
                val(s == null ? Float.NaN : s.silencieux, silencieux[c]),
                maxGundb(gunId, type));
    }

    private static float val(float surcharge, float categorie) {
        return Float.isNaN(surcharge) ? categorie : surcharge;
    }

    /** Categorie deduite du type TaCZ et des donnees de l'arme (munitions, culasse, modes de tir). */
    public Categorie categorieAuto(String type, GunData data) {
        if (data != null && data.getAmmoId() != null && munitionsMelee.contains(data.getAmmoId().toString())) return Categorie.MELEE;
        return switch (type) {
            case "pistol" -> Categorie.PISTOLET;
            case "smg" -> Categorie.PM;
            case "shotgun" -> Categorie.POMPE;
            case "mg" -> Categorie.MITRAILLEUSE;
            case "rpg" -> Categorie.LANCEUR;
            // precision semi-automatique (culasse fermee, ni verrou ni coup par coup a l'ancienne) : DMR
            case "sniper" -> data != null && data.getBolt() == Bolt.CLOSED_BOLT ? Categorie.DMR : Categorie.PRECISION;
            // fusil qui ne tire qu'au coup par coup : DMR
            case "rifle" -> data != null && coupParCoupSeul(data.getFireModeSet()) ? Categorie.DMR : Categorie.FUSIL;
            default -> Categorie.FUSIL;
        };
    }

    private static boolean coupParCoupSeul(List<FireMode> modes) {
        if (modes == null || modes.isEmpty()) return false;
        for (FireMode m : modes) if (m != FireMode.SEMI) return false;
        return true;
    }

    /**
     * Durabilite maximale de Gun Durability 2.2.2 (reglages d'origine, aucun pack de donnees ne les changeait sur
     * HeraliZ) : par type TaCZ, et quelques armes a part.
     */
    public static int maxGundb(String gunId, String type) {
        switch (gunId) {
            case "tacz:minigun":
                return 2000;
            case "tacz:springfield1873":
                return 600;
            default:
                break;
        }
        return switch (type) {
            case "pistol" -> 800;
            case "smg" -> 1200;
            case "sniper" -> 600;
            case "shotgun" -> 750;
            case "rpg" -> 256;
            default -> 1000; // rifle, mg
        };
    }

    /** Surplus d'usure (%) d'un accessoire, 0 s'il n'en a pas. */
    public float surplusAccessoire(String id) {
        Float f = accessoires.get(id);
        return f == null ? 0 : f;
    }

    /** Dimensions sans usure, lues une fois (comparaison sans allocation). */
    private java.util.Set<ResourceLocation> dimensions;

    public boolean dimensionSansUsure(ResourceLocation dimension) {
        java.util.Set<ResourceLocation> d = dimensions;
        if (d == null) {
            d = new java.util.HashSet<>();
            for (String s : dimensionsSansUsure) {
                ResourceLocation rl = ResourceLocation.tryParse(s.trim());
                if (rl != null) d.add(rl);
            }
            dimensions = d;
        }
        return !d.isEmpty() && d.contains(dimension);
    }

    // ---------------------------------------------------------------- reseau / copie
    public void ecrire(FriendlyByteBuf b) {
        b.writeBoolean(actif);
        b.writeBoolean(usureCreatif);
        b.writeBoolean(sansUsureEnMatch);
        b.writeBoolean(hudAutorise);
        ecrireListe(b, dimensionsSansUsure);
        ecrireListe(b, munitionsMelee);
        b.writeVarInt(Categorie.TOUTES.length);
        for (int i = 0; i < Categorie.TOUTES.length; i++) {
            b.writeFloat(usure[i]);
            b.writeFloat(pluie[i]);
            b.writeFloat(eau[i]);
            b.writeFloat(silencieux[i]);
        }
        b.writeFloat(seuilNeuf);
        b.writeFloat(seuilBon);
        b.writeFloat(seuilUse);
        for (int i = 0; i < NB_KITS; i++) {
            b.writeFloat(kitGain[i]);
            b.writeFloat(kitPlafond[i]);
            b.writeVarInt(kitUtilisations[i]);
            b.writeBoolean(kitRuinee[i]);
        }
        b.writeFloat(perteMaxParReparation);
        b.writeFloat(maxMinimum);
        b.writeVarInt(surcharges.size());
        for (Surcharge s : surcharges) b.writeUtf(s.ecrire(), 512);
        b.writeVarInt(accessoires.size());
        for (Map.Entry<String, Float> e : accessoires.entrySet()) {
            b.writeUtf(e.getKey(), 256);
            b.writeFloat(e.getValue());
        }
    }

    public static Regles lire(FriendlyByteBuf b) {
        Regles r = new Regles();
        r.actif = b.readBoolean();
        r.usureCreatif = b.readBoolean();
        r.sansUsureEnMatch = b.readBoolean();
        r.hudAutorise = b.readBoolean();
        r.dimensionsSansUsure = lireListe(b);
        r.munitionsMelee = lireListe(b);
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            float u = b.readFloat(), p = b.readFloat(), e = b.readFloat(), s = b.readFloat();
            if (i >= Categorie.TOUTES.length) continue;
            r.usure[i] = u;
            r.pluie[i] = p;
            r.eau[i] = e;
            r.silencieux[i] = s;
        }
        r.seuilNeuf = b.readFloat();
        r.seuilBon = b.readFloat();
        r.seuilUse = b.readFloat();
        for (int i = 0; i < NB_KITS; i++) {
            r.kitGain[i] = b.readFloat();
            r.kitPlafond[i] = b.readFloat();
            r.kitUtilisations[i] = b.readVarInt();
            r.kitRuinee[i] = b.readBoolean();
        }
        r.perteMaxParReparation = b.readFloat();
        r.maxMinimum = b.readFloat();
        int ns = Math.min(b.readVarInt(), 1024);
        for (int i = 0; i < ns; i++) {
            Surcharge s = Surcharge.lire(b.readUtf(512));
            if (s != null) r.surcharges.add(s);
        }
        int na = Math.min(b.readVarInt(), 1024);
        for (int i = 0; i < na; i++) r.accessoires.put(b.readUtf(256), b.readFloat());
        r.corriger();
        return r;
    }

    private static void ecrireListe(FriendlyByteBuf b, List<String> l) {
        b.writeVarInt(l.size());
        for (String s : l) b.writeUtf(s, 256);
    }

    private static List<String> lireListe(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 256);
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(b.readUtf(256));
        return l;
    }

    /** Copie complete (l'ecran d'administration la modifie, puis l'envoie au serveur). */
    public Regles copie() {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ecrire(b);
            return lire(b);
        } finally {
            b.release();
        }
    }

    /** Memes regles (comparaison de la forme envoyee). */
    public boolean memesQue(Regles autre) {
        if (autre == null) return false;
        FriendlyByteBuf a = new FriendlyByteBuf(Unpooled.buffer()), o = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ecrire(a);
            autre.ecrire(o);
            return a.equals(o);
        } finally {
            a.release();
            o.release();
        }
    }

    /** Ramene chaque valeur dans des bornes sensees (valeurs recues d'un ecran ou d'un fichier modifie a la main). */
    public Regles corriger() {
        for (int i = 0; i < Categorie.TOUTES.length; i++) {
            usure[i] = borne(usure[i], 0, 100);
            pluie[i] = borne(pluie[i], 0, 1000);
            eau[i] = borne(eau[i], 0, 1000);
            silencieux[i] = borne(silencieux[i], 0, 1000);
        }
        seuilNeuf = borne(seuilNeuf, 0.1f, 100);
        seuilBon = borne(seuilBon, 0.1f, seuilNeuf);
        seuilUse = borne(seuilUse, 0.1f, seuilBon);
        for (int i = 0; i < NB_KITS; i++) {
            kitGain[i] = borne(kitGain[i], 0, 100);
            kitPlafond[i] = borne(kitPlafond[i], 1, 100);
            kitUtilisations[i] = Math.max(1, Math.min(1000, kitUtilisations[i]));
        }
        perteMaxParReparation = borne(perteMaxParReparation, 0, 100);
        maxMinimum = borne(maxMinimum, 1, 100);
        surcharges.removeIf(s -> s == null || s.motif.isEmpty());
        accessoires.entrySet().removeIf(e -> e.getKey().isEmpty() || e.getValue() == null || !Float.isFinite(e.getValue()));
        accessoires.replaceAll((k, v) -> borne(v, 0, 1000));
        dimensionsSansUsure.removeIf(String::isBlank);
        munitionsMelee.removeIf(String::isBlank);
        cache.clear();
        dimensions = null;
        return this;
    }

    private static float borne(float v, float min, float max) {
        if (!Float.isFinite(v)) return min;
        return Math.max(min, Math.min(max, v));
    }

    /** Nombre lisible pour le fichier : 0.08, 25, 1.5 (point decimal, sans zeros inutiles). */
    public static String texte(float v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    public String toString() {
        return "Regles{actif=" + actif + ", usure=" + Arrays.toString(usure) + ", surcharges=" + surcharges.size() + "}";
    }
}
