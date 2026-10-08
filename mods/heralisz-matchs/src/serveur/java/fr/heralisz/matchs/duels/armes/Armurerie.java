package fr.heralisz.matchs.duels.armes;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.moteur.compat.Armures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lecture du catalogue des duels dans les index <b>reels</b> des mods installes, par reflexion tolerante (un mod absent
 * = categorie vide, jamais de plantage) :
 * <ul>
 *     <li>TaCZ : {@code TimelessAPI.getAllCommonGunIndex()} (toutes les armes sauf lanceurs, balles explosives et
 *     espaces de noms de skins), {@code getAllCommonAttachmentIndex()} (accessoires non caches), autorisation par
 *     {@code AllowAttachmentTagMatcher.match(arme, accessoire)} + type d'emplacement permis par l'arme ;</li>
 *     <li>heralisz_tactical : {@code HeraliszTacticalAPI.getMeleeIndexes/getConsumableIndexes/getThrowableIndexes}
 *     (soins = consommables qui soignent ; grenades letales = explosives, collantes, nuage ; tactiques = le reste) ;</li>
 *     <li>HeraliZ Armurerie : tenues completes {@code heraliz_armory:kit_<ensemble>} (au moins une piece
 *     {@code heraliz_armory:<ensemble>_<piece>} dans le registre, voir {@link Armures}), par niveau NV1..NV23.</li>
 * </ul>
 * Le catalogue est construit a la premiere demande apres le demarrage ou un rechargement des donnees
 * ({@link #invalider()}), puis garde. Fil serveur.
 */
public final class Armurerie {
    private Armurerie() {
    }

    private static Catalogue courant;

    /** Catalogue courant (construit si besoin). Jamais null. */
    public static Catalogue catalogue() {
        if (courant == null) {
            long t0 = System.nanoTime();
            try {
                courant = construire();
            } catch (RuntimeException | LinkageError ex) {
                HeraliszMatchs.LOG.error("[duels] lecture du catalogue en erreur", ex);
                courant = Catalogue.VIDE;
            }
            if (courant.vide() && DuelsConfig.banc()) {
                HeraliszMatchs.LOG.warn("[duels] banc d'essai sans TaCZ : catalogue factice");
                courant = factice();
            }
            HeraliszMatchs.LOG.info("[duels] catalogue v{} : {} armes, {} accessoires, {} melees, {} soins, {} letales, {} tactiques, {} tenues ({} ms)",
                    courant.version(), courant.armes().size(), courant.accessoires().size(), courant.melees().size(), courant.soins().size(),
                    courant.letales().size(), courant.tactiques().size(), courant.tenues().size(), (System.nanoTime() - t0) / 1_000_000);
        }
        return courant;
    }

    /** Oublie le catalogue (rechargement des donnees, arret du serveur). */
    public static void invalider() {
        courant = null;
    }

    // ------------------------------------------------------------------ construction
    private static Catalogue construire() {
        List<Catalogue.Accessoire> accessoires = new ArrayList<>();
        List<Catalogue.Arme> armes = new ArrayList<>();
        if (charge("tacz")) lireTacz(armes, accessoires);
        List<Catalogue.Objet> melees = new ArrayList<>(), soins = new ArrayList<>(), letales = new ArrayList<>(), tactiques = new ArrayList<>();
        if (charge("heralisz_tactical")) lireTactical(melees, soins, letales, tactiques);
        List<Catalogue.Objet> tenues = lireTenues();
        int version = 1 + Math.abs((armes.size() * 31 + accessoires.size()) * 31 + melees.size() * 7 + soins.size() * 5 + letales.size() * 3
                + tactiques.size() + tenues.size() * 11 + (int) (System.currentTimeMillis() / 1000 % 100000));
        return new Catalogue(version, armes, accessoires, melees, soins, letales, tactiques, tenues);
    }

    private static boolean charge(String mod) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(mod);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void lireTacz(List<Catalogue.Arme> armes, List<Catalogue.Accessoire> accessoires) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> matcher = Class.forName("com.tacz.guns.util.AllowAttachmentTagMatcher");
            Method match = matcher.getMethod("match", ResourceLocation.class, ResourceLocation.class);
            // accessoires (ordre de TaCZ : tri par "sort" puis id)
            List<Object[]> accIdx = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Object> e : (Set<Map.Entry<ResourceLocation, Object>>) api.getMethod("getAllCommonAttachmentIndex").invoke(null)) {
                Object idx = e.getValue();
                Object pojo = appel(idx, "getPojo");
                if (pojo != null && Boolean.TRUE.equals(appel(pojo, "isHidden"))) continue;
                Object type = appel(idx, "getType");
                if (type == null) continue;
                String emp = ((Enum<?>) type).name().toLowerCase(Locale.ROOT);
                if (!Catalogue.EMPLACEMENTS.contains(emp)) continue;
                if (exclu(e.getKey())) continue;
                accIdx.add(new Object[]{e.getKey(), idx, emp, pojo, entier(appel(idx, "getSort"))});
            }
            accIdx.sort(Comparator.<Object[]>comparingInt(o -> Catalogue.EMPLACEMENTS.indexOf((String) o[2])).thenComparingInt(o -> (Integer) o[4])
                    .thenComparing(o -> o[0].toString()));
            for (Object[] a : accIdx) {
                ResourceLocation id = (ResourceLocation) a[0];
                String nom = a[3] != null ? chaine(appel(a[3], "getName")) : "";
                accessoires.add(new Catalogue.Accessoire(id.toString(), nom.isEmpty() ? id.toString() : nom, (String) a[2], iconeAccessoire(id)));
            }
            // armes
            List<Object[]> gunIdx = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Object> e : (Set<Map.Entry<ResourceLocation, Object>>) api.getMethod("getAllCommonGunIndex").invoke(null)) {
                ResourceLocation id = e.getKey();
                Object idx = e.getValue();
                String type = chaine(appel(idx, "getType")).toLowerCase(Locale.ROOT);
                if (exclu(id) || DuelsConfig.TYPES_EXCLUS.contains(type)) continue;
                Object data = appel(idx, "getGunData");
                Object bullet = data != null ? appel(data, "getBulletData") : null;
                Object explosion = bullet != null ? appel(bullet, "getExplosionData") : null;
                if (explosion != null && Boolean.TRUE.equals(appel(explosion, "isExplode"))) continue;
                gunIdx.add(new Object[]{id, idx, type, data, bullet, entier(appel(idx, "getSort"))});
            }
            gunIdx.sort(Comparator.<Object[]>comparingInt(o -> ordreType((String) o[2])).thenComparingInt(o -> (Integer) o[5])
                    .thenComparing(o -> o[0].toString()));
            for (Object[] g : gunIdx) {
                ResourceLocation id = (ResourceLocation) g[0];
                Object idx = g[1], data = g[3], bullet = g[4];
                Object pojo = appel(idx, "getPojo");
                String nom = pojo != null ? chaine(appel(pojo, "getName")) : "";
                List<String> types = new ArrayList<>();
                Object permis = data != null ? appel(data, "getAllowAttachments") : null;
                if (permis instanceof Collection<?> c) for (Object t : c) if (t instanceof Enum<?> en) types.add(en.name().toLowerCase(Locale.ROOT));
                Object exclusifs = data != null ? appel(data, "getExclusiveAttachments") : null;
                List<Integer> autorises = new ArrayList<>();
                for (int i = 0; i < accessoires.size(); i++) {
                    Catalogue.Accessoire ac = accessoires.get(i);
                    if (!types.contains(ac.emplacement())) continue;
                    ResourceLocation aid = new ResourceLocation(ac.id());
                    boolean ok = exclusifs instanceof Map<?, ?> m && m.containsKey(aid);
                    if (!ok) {
                        try {
                            ok = Boolean.TRUE.equals(match.invoke(null, id, aid));
                        } catch (ReflectiveOperationException | RuntimeException ex) {
                            ok = false;
                        }
                    }
                    if (ok) autorises.add(i);
                }
                int rpm = data != null ? entier(appel(data, "getRoundsPerMinute")) : 0;
                int chargeur = data != null ? entier(appel(data, "getAmmoAmount")) : 0;
                float degats = bullet != null ? flottant(appel(bullet, "getDamageAmount")) : 0f;
                armes.add(new Catalogue.Arme(id.toString(), nom.isEmpty() ? id.toString() : nom, (String) g[2], iconeArme(id), rpm, degats, chargeur, autorises));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] index TaCZ illisible : {}", ex.toString());
        }
    }

    /** Ordre d'affichage des types d'armes. */
    public static int ordreType(String type) {
        return switch (type) {
            case "rifle" -> 0;
            case "smg" -> 1;
            case "shotgun" -> 2;
            case "sniper" -> 3;
            case "mg" -> 4;
            case "pistol" -> 5;
            default -> 6;
        };
    }

    @SuppressWarnings("unchecked")
    private static void lireTactical(List<Catalogue.Objet> melees, List<Catalogue.Objet> soins, List<Catalogue.Objet> letales,
                                     List<Catalogue.Objet> tactiques) {
        try {
            Class<?> api = Class.forName("fr.heralisz.tactical.api.HeraliszTacticalAPI");
            for (Object idx : (Collection<Object>) api.getMethod("getMeleeIndexes").invoke(null)) {
                Catalogue.Objet o = objetTactical(idx);
                if (o != null) melees.add(o);
            }
            for (Object idx : (Collection<Object>) api.getMethod("getConsumableIndexes").invoke(null)) {
                Object data = appel(idx, "getData");
                float soin = data != null ? flottant(appel(data, "getHeal")) : 0f;
                int faim = data != null ? entier(appel(data, "getFood")) : 0;
                Object effets = data != null ? appel(data, "getEffects") : null;
                boolean aEffets = effets instanceof Collection<?> c && !c.isEmpty();
                if (soin <= 0 && !(aEffets && faim == 0)) continue;
                Catalogue.Objet o = objetTactical(idx);
                if (o != null) soins.add(o);
            }
            for (Object idx : (Collection<Object>) api.getMethod("getThrowableIndexes").invoke(null)) {
                Object data = appel(idx, "getData");
                String cls = data == null ? "" : data.getClass().getSimpleName();
                Catalogue.Objet o = objetTactical(idx);
                if (o == null) continue;
                if (cls.contains("Explode") || cls.contains("EffectCloud")) letales.add(o);
                else tactiques.add(o);
            }
            Comparator<Catalogue.Objet> parId = Comparator.comparing(Catalogue.Objet::id);
            melees.sort(parId);
            soins.sort(parId);
            letales.sort(parId);
            tactiques.sort(parId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] index heralisz_tactical illisibles : {}", ex.toString());
        }
    }

    private static Catalogue.Objet objetTactical(Object idx) {
        Object id = appel(idx, "getId");
        if (!(id instanceof ResourceLocation rl) || exclu(rl)) return null;
        String nom = chaine(appel(idx, "getDescriptionId"));
        Object pile = appel(idx, "createItemStack");
        return new Catalogue.Objet(rl.toString(), nom.isEmpty() ? rl.toString() : nom, pile instanceof ItemStack s ? s : ItemStack.EMPTY);
    }

    /**
     * Tenues completes de HeraliZ Armurerie : objets {@code heraliz_armory:kit_*} dont l'ensemble a au moins une piece,
     * tries par niveau (NV1..NV23, lu par reflexion tolerante sur {@code SetKitItem.set().level()}) puis par id. Id =
     * chemin de l'objet ({@code kit_siberie}), nom = cle de traduction de l'objet. Armurerie absente = aucune tenue.
     */
    private static List<Catalogue.Objet> lireTenues() {
        if (!charge(Armures.ARMURERIE)) return List.of();
        List<Object[]> l = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation rl = e.getKey().location();
            if (!rl.getNamespace().equals(Armures.ARMURERIE) || !rl.getPath().startsWith("kit_")) continue;
            if (Armures.piecesArmurerie(rl.getPath()).isEmpty()) continue;
            Item it = e.getValue();
            l.add(new Object[]{new Catalogue.Objet(rl.getPath(), it.getDescriptionId(), new ItemStack(it)), niveauTenue(it)});
        }
        l.sort(Comparator.<Object[]>comparingInt(o -> (Integer) o[1]).thenComparing(o -> ((Catalogue.Objet) o[0]).id()));
        List<Catalogue.Objet> r = new ArrayList<>(l.size());
        for (Object[] o : l) r.add((Catalogue.Objet) o[0]);
        return r;
    }

    /** Niveau de la tenue (NV1..NV23) ; 999 si illisible (rangee a la fin). */
    private static int niveauTenue(Item kit) {
        Object set = appel(kit, "set");
        Object niveau = set == null ? null : appel(set, "level");
        return niveau instanceof Number n ? n.intValue() : 999;
    }

    private static boolean exclu(ResourceLocation id) {
        return DuelsConfig.ESPACES_EXCLUS.contains(id.getNamespace()) || id.getPath().contains("skin");
    }

    // ------------------------------------------------------------------ piles d'affichage
    /** Pile d'affichage d'une arme TaCZ (objet arme portant le GunId), vide si TaCZ est absent. */
    public static ItemStack iconeArme(ResourceLocation gunId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putString("GunId", gunId.toString());
        return s;
    }

    private static ItemStack iconeAccessoire(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "attachment"));
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putString("AttachmentId", id.toString());
        return s;
    }

    // ------------------------------------------------------------------ nettoyage des skins
    /**
     * Retire recursivement d'une pile les cles NBT de camo, decor, StatTrak et skins ({@link DuelsConfig#NBT_INTERDITS}).
     * Rend le nombre de cles retirees.
     */
    public static int sansSkin(ItemStack pile) {
        CompoundTag t = pile.getTag();
        return t == null ? 0 : nettoyer(t, 0);
    }

    private static int nettoyer(CompoundTag t, int profondeur) {
        if (profondeur > 6) return 0;
        int n = 0;
        for (String k : DuelsConfig.NBT_INTERDITS) {
            if (t.contains(k)) {
                t.remove(k);
                n++;
            }
        }
        for (String k : List.copyOf(t.getAllKeys())) {
            if (t.get(k) instanceof CompoundTag sous) n += nettoyer(sous, profondeur + 1);
        }
        return n;
    }

    // ------------------------------------------------------------------ banc
    /** Catalogue de demonstration du banc d'essai (client de dev sans TaCZ) : ids TaCZ reels, sans icone. */
    public static Catalogue factice() {
        return Catalogue.factice();   // commun : aussi l'écran des classes du client
    }

    // ------------------------------------------------------------------ reflexion
    /** Appelle une methode publique sans argument ; null si absente ou en erreur. */
    static Object appel(Object o, String methode) {
        if (o == null) return null;
        try {
            Method m = o.getClass().getMethod(methode);
            return m.invoke(o);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static String chaine(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int entier(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static float flottant(Object o) {
        return o instanceof Number n ? n.floatValue() : 0f;
    }
}
