package fr.heraliz.grades.hdv;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Règles de l'hôtel des ventes lues des deux côtés : ce qui ne se vend pas (le serveur décide, l'écran ne fait que griser
 * les cases), la rareté (HeraliZ Loot, par réflexion), la catégorie d'une offre et les frais des ventes en PB. Mêmes refus
 * que les échanges des marchands ({@code Echanges.interdit} de HeraliZ Quêtes) et que {@code /trade} : objets à vie,
 * prêtés par un match, à camo à vie, de quête, sacs pleins, clés de caisse, registres du staff.
 */
public final class RegleHdv {
    private RegleHdv() {
    }

    /** Raisons de refus (index des textes {@code gui.heraliz_grades.hdv.refus.N}) ; 0 = se vend. */
    public static final int OK = 0, VIDE = 1, A_VIE = 2, MATCH = 3, QUETE = 4, CAMO = 5, SAC_PLEIN = 6, CLE = 7, STAFF = 8, CONTENU = 9;
    /** Catégories (index des textes {@code gui.heraliz_grades.hdv.cat.N}) : 0 = tout. */
    public static final int CAT_TOUT = 0, CAT_ARMES = 1, CAT_ACCESSOIRES = 2, CAT_ARMURES = 3, CAT_SOINS = 4, CAT_COMPOSANTS = 5, CAT_DIVERS = 6, CATEGORIES = 7;
    /** Devises : 0 hérals, 1 PB. */
    public static final int HERALS = 0, PB = 1;
    /** Raretés de HeraliZ Loot (Commun … Unique), -1 : aucune. */
    public static final int RARETES = 6;

    private static Method estAVie, rarete;
    private static boolean aVieCherche, rareteCherche;

    /** Raison qui empêche de mettre cet objet en vente, ou {@link #OK}. */
    public static int refus(ItemStack s) {
        if (s == null || s.isEmpty()) return VIDE;
        CompoundTag t = s.getTag();
        if (t != null && t.contains("HeraliszMatch")) return MATCH;
        if (t != null && t.contains("HeraliszCamoProprio")) return CAMO;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String ns = id == null ? "" : id.getNamespace();
        if (ns.equals("heraliz_quetes")) return QUETE;
        if (s.getItem() instanceof fr.heraliz.crates.item.CrateKeyItem) return CLE;
        if (id != null && (id.toString().equals("heraliz_grades:registre_grades") || id.getPath().startsWith("registre"))) return STAFF;
        if (sacPlein(s, ns)) return SAC_PLEIN;
        if (t != null && t.contains("BlockEntityTag", Tag.TAG_COMPOUND) && !t.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND).isEmpty()) return CONTENU;
        if (aVie(s)) return A_VIE;
        return OK;
    }

    /** Sac à dos qui contient quelque chose (liste « Items » écrite dans l'objet à chaque changement) : son contenu partirait avec lui. */
    private static boolean sacPlein(ItemStack s, String ns) {
        if (!ns.equals("backpacked") || !s.hasTag()) return false;
        ListTag l = s.getTag().getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) if (!ItemStack.of(l.getCompound(i)).isEmpty()) return true;
        return false;
    }

    /** Objet à vie (couteaux, skins à vie) d'après HeraliZ Tactique, par réflexion ; sans le mod : non. */
    private static boolean aVie(ItemStack s) {
        if (!aVieCherche) {
            aVieCherche = true;
            try {
                estAVie = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("estAVie", ItemStack.class);
            } catch (Throwable ignore) {
                estAVie = null;
            }
        }
        try {
            return estAVie != null && (Boolean) estAVie.invoke(null, s);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Rareté de l'objet (0 Commun … 5 Unique) d'après HeraliZ Loot ({@code RaretesObjets.de}), -1 : aucune ou Loot absent. */
    public static int rarete(ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        if (!rareteCherche) {
            rareteCherche = true;
            try {
                rarete = Class.forName("fr.heraliz.loot.RaretesObjets").getMethod("de", ItemStack.class);
            } catch (Throwable ignore) {
                rarete = null;
            }
        }
        try {
            Object r = rarete == null ? null : rarete.invoke(null, s);
            return r instanceof Enum<?> e ? Math.min(RARETES - 1, e.ordinal()) : -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    /** Catégorie de l'objet (jamais {@link #CAT_TOUT}). */
    public static int categorie(ItemStack s) {
        CompoundTag t = s.getTag();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String ns = id == null ? "" : id.getNamespace();
        if (t != null && t.contains("GunId", Tag.TAG_STRING)) return CAT_ARMES;
        if (ns.equals("heralisz_melee") || ns.equals("heralisz_grenade")) return CAT_ARMES;
        if (t != null && t.contains("AttachmentId", Tag.TAG_STRING)) return CAT_ACCESSOIRES;
        if (t != null && t.contains("ConsumableId", Tag.TAG_STRING) || ns.equals("heralisz_heal")) return CAT_SOINS;
        if (ns.equals("heraliz_armory") || ns.equals("backpacked") || s.getItem() instanceof ArmorItem) return CAT_ARMURES;
        if (ns.equals("heraliz_composants")) return CAT_COMPOSANTS;
        // cartes d'accès, fragments, munitions, objets à revendre… : « Cartes et divers »
        return CAT_DIVERS;
    }

    /** Ce que paie l'acheteur : le prix du vendeur, plus les frais (pour cent, arrondis au supérieur) des ventes en PB. */
    public static long total(int devise, long prix, int fraisPct) {
        if (devise != PB || fraisPct <= 0) return prix;
        return prix + (prix * fraisPct + 99) / 100;
    }

    /** Texte de recherche d'un objet : identifiants (objet, arme, accessoire, munition, soin) et nom donné, en minuscules. */
    public static String cles(ItemStack s) {
        StringBuilder b = new StringBuilder();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id != null) b.append(id).append(' ');
        CompoundTag t = s.getTag();
        if (t != null) {
            for (String k : new String[]{"GunId", "AttachmentId", "AmmoId", "ConsumableId"}) {
                if (t.contains(k, Tag.TAG_STRING)) b.append(t.getString(k)).append(' ');
            }
        }
        if (s.hasCustomHoverName()) b.append(s.getHoverName().getString()).append(' ');
        return normaliser(b.toString());
    }

    /** Minuscules sans accents, « _ » et « - » en espaces : « Carte d'accès » et « carte_d_acces » se ressemblent. */
    public static String normaliser(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    }
}
