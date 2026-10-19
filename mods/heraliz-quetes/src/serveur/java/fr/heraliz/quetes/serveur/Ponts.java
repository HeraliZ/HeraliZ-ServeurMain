package fr.heraliz.quetes.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Liens avec nos autres mods, tous FACULTATIFS (réflexion : le mod de quêtes démarre sans eux) :
 * <ul>
 *    <li>hérals : {@code fr.heralisz.economie.api.EconomieApi} (clansetbases) ;</li>
 *    <li>rareté d'un objet : {@code fr.heraliz.loot.RaretesObjets.de(ItemStack)} (HeraliZ Loot) ;</li>
 *    <li>famille d'une arme TaCZ (« pompe », « fusil_556 »...) : {@code config/heraliz_tactique/armes.json} (écrit par
 *    outils/equilibrage_armes.py) ;</li>
 *    <li>usure d'une arme : étiquette {@code HzUsureEtat} de HeraliZ Utils (millièmes, absente = neuve) ;</li>
 *    <li>rareté des composants et objets de valeur : la rareté Minecraft {@code HERALIZ_*} de HeraliZ Composants ;</li>
 *    <li>frappes aériennes en cours d'un joueur : {@code fr.heraliz.airstrike.strike.StrikeManager.countActive(UUID)} ;</li>
 *    <li>joueur à terre : {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)} (Réanimation) ;</li>
 *    <li>tutoriel des bases : {@code fr.heraliz.crac.bases.TutorielApi} (CRac : état, étape de la cabane, lancement) ;</li>
 *    <li>cinématique d'arrivée finie : donnée persistante {@code heraliz_cinematique_finie} de HeraliZ Utils.</li>
 * </ul>
 */
final class Ponts {
    private static final Logger LOG = LogUtils.getLogger();
    static final List<String> RARETES = List.of("commun", "peu_commun", "rare", "epique", "legendaire", "unique");
    /** Raretés de HeraliZ Composants, dans le même ordre (Mythique = le 6e rang, comme Unique). */
    private static final List<String> RARETES_COMPOSANTS = List.of("HERALIZ_COMMON", "HERALIZ_UNCOMMON", "HERALIZ_RARE",
            "HERALIZ_EPIC", "HERALIZ_LEGENDARY", "HERALIZ_MYTHIC");

    private Ponts() {
    }

    // ------------------------------------------------------------------ hérals
    private static Class<?> eco;
    private static boolean ecoAbsente;

    private static Class<?> eco() {
        if (eco == null && !ecoAbsente) {
            try {
                eco = Class.forName("fr.heralisz.economie.api.EconomieApi");
            } catch (Throwable t) {
                ecoAbsente = true;
                LOG.warn("[heraliz_quetes] HeraliZ Économie absente : les récompenses en hérals ne seront pas versées");
            }
        }
        return eco;
    }

    static boolean economie() {
        Class<?> c = eco();
        if (c == null) return false;
        try {
            return (boolean) c.getMethod("disponible").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean deposer(UUID joueur, long montant, String motif) {
        Class<?> c = eco();
        if (c == null || montant <= 0) return false;
        try {
            if (!(boolean) c.getMethod("disponible").invoke(null)) return false;
            c.getMethod("deposer", UUID.class, long.class, String.class).invoke(null, joueur, montant, motif);
            return true;
        } catch (Throwable t) {
            LOG.error("[heraliz_quetes] versement de {} hérals à {} : {}", montant, joueur, t.toString());
            return false;
        }
    }

    static long solde(UUID joueur) {
        Class<?> c = eco();
        if (c == null) return 0;
        try {
            return (long) c.getMethod("solde", UUID.class).invoke(null, joueur);
        } catch (Throwable t) {
            return 0;
        }
    }

    static boolean retirer(UUID joueur, long montant, String motif) {
        Class<?> c = eco();
        if (c == null || montant <= 0) return false;
        try {
            if (!(boolean) c.getMethod("disponible").invoke(null)) return false;
            return (boolean) c.getMethod("retirer", UUID.class, long.class, String.class).invoke(null, joueur, montant, motif);
        } catch (Throwable t) {
            return false;
        }
    }

    /** « 1 500 » (séparateur des milliers du serveur). */
    static String nombre(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    // ------------------------------------------------------------------ raretés
    private static Method rarete;
    private static boolean rareteAbsente;

    /** Rang de rareté de l'objet (0 Commun ... 5 Unique), -1 si aucune ou si HeraliZ Loot est absent. */
    static int rarete(ItemStack s) {
        int c = RARETES_COMPOSANTS.indexOf(s.getRarity().name());
        if (c >= 0) return c;
        if (rareteAbsente) return -1;
        try {
            if (rarete == null) rarete = Class.forName("fr.heraliz.loot.RaretesObjets").getMethod("de", ItemStack.class);
            Object r = rarete.invoke(null, s);
            return r instanceof Enum<?> e ? e.ordinal() : -1;
        } catch (Throwable t) {
            rareteAbsente = true;
            return -1;
        }
    }

    static int rangRarete(String id) {
        if (id == null) return -1;
        String r = id.toLowerCase(Locale.ROOT);
        return r.equals("mythique") ? 5 : RARETES.indexOf(r);
    }

    /**
     * « tete » (casque), « corps » (gilet, veste, combinaison), « tete_ou_corps ». Armure Minecraft, ou pièce de
     * l'Armurerie (interface {@code fr.heraliz.armory.item.PieceItem} : les gilets se portent dans un emplacement Curios).
     */
    static boolean armure(ItemStack s, String voulu) {
        String piece = piece(s);
        boolean tete = piece != null ? piece.equals("head")
                : s.getItem() instanceof ArmorItem a && a.getType() == ArmorItem.Type.HELMET;
        boolean corps = piece != null ? piece.equals("vest") || piece.equals("chest") || piece.equals("suit")
                : s.getItem() instanceof ArmorItem a && a.getType() == ArmorItem.Type.CHESTPLATE;
        return switch (voulu.toLowerCase(Locale.ROOT)) {
            case "tete" -> tete;
            case "corps" -> corps;
            case "tete_ou_corps" -> tete || corps;
            default -> piece != null || s.getItem() instanceof ArmorItem;
        };
    }

    private static Class<?> pieceItem;
    private static Method pieceDef, pieceNom;
    private static boolean armurerieAbsente;

    /** Genre d'une pièce de l'Armurerie (« head », « vest », « chest »...), null pour un autre objet. */
    static String piece(ItemStack s) {
        if (armurerieAbsente || s.isEmpty()) return null;
        try {
            if (pieceItem == null) {
                pieceItem = Class.forName("fr.heraliz.armory.item.PieceItem");
                pieceDef = pieceItem.getMethod("def");
                pieceNom = Class.forName("fr.heraliz.armory.armor.PieceDef").getMethod("piece");
            }
            if (!pieceItem.isInstance(s.getItem())) return null;
            return (String) pieceNom.invoke(pieceDef.invoke(s.getItem()));
        } catch (Throwable t) {
            armurerieAbsente = true;
            return null;
        }
    }

    private static Method curiosInventaire, curiosPortes;
    private static boolean curiosAbsent;

    /** Objets portés dans les emplacements Curios (gilet, masque, sac...) ; vide sans Curios. */
    static List<ItemStack> curios(Player p) {
        List<ItemStack> l = new java.util.ArrayList<>();
        if (curiosAbsent) return l;
        try {
            if (curiosInventaire == null) {
                curiosInventaire = Class.forName("top.theillusivec4.curios.api.CuriosApi")
                        .getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            }
            var lo = (net.minecraftforge.common.util.LazyOptional<?>) curiosInventaire.invoke(null, p);
            Object h = lo.resolve().orElse(null);
            if (h == null) return l;
            if (curiosPortes == null) curiosPortes = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                    .getMethod("getEquippedCurios");
            var portes = (net.minecraftforge.items.IItemHandler) curiosPortes.invoke(h);
            for (int i = 0; i < portes.getSlots(); i++) l.add(portes.getStackInSlot(i));
        } catch (Throwable t) {
            curiosAbsent = true;
            LOG.warn("[heraliz_quetes] Curios absent ou différent ({}) : « montrer » ne regarde pas ses emplacements", t.toString());
        }
        return l;
    }

    // ------------------------------------------------------------------ frappes aériennes, joueurs à terre
    private static Method frappes;
    private static boolean frappesAbsentes;

    /** Frappes aériennes en cours appelées par ce joueur ; -1 si Airstrike est absent. */
    static int frappes(UUID joueur) {
        if (frappesAbsentes) return -1;
        try {
            if (frappes == null) frappes = Class.forName("fr.heraliz.airstrike.strike.StrikeManager").getMethod("countActive", UUID.class);
            return (int) frappes.invoke(null, joueur);
        } catch (Throwable t) {
            frappesAbsentes = true;
            LOG.warn("[heraliz_quetes] Airstrike absent ou différent ({}) : les étapes « frappe » n'avanceront pas", t.toString());
            return -1;
        }
    }

    private static Method aTerre;
    private static boolean aTerreAbsent;

    static boolean aTerre(Entity e) {
        if (aTerreAbsent) return false;
        try {
            if (aTerre == null) aTerre = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", Entity.class);
            return (boolean) aTerre.invoke(null, e);
        } catch (Throwable t) {
            aTerreAbsent = true;
            LOG.warn("[heraliz_quetes] Réanimation absente ou différente ({}) : les étapes « executer » n'avanceront pas", t.toString());
            return false;
        }
    }

    /** Le joueur a-t-il ouvert la table de fabrication de TaCZ (Table d'armurier) ? */
    static boolean tableArmes(Player p) {
        return p.containerMenu != null && p.containerMenu.getClass().getName().equals("com.tacz.guns.inventory.GunSmithTableMenu");
    }

    /** Nombre d'armes TaCZ (couteaux exclus) dans l'inventaire. */
    static int armes(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            String g = gunId(s);
            if (g != null && !couteau(s)) n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ armes TaCZ
    private static Map<String, String> familles;

    static void relireArmes() {
        Map<String, String> m = new HashMap<>();
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_tactique").resolve("armes.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("armes") && o.get("armes").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("armes").entrySet()) {
                        if (e.getValue().isJsonObject()) m.put(e.getKey(), Defs.chaine(e.getValue().getAsJsonObject(), "famille", ""));
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("[heraliz_quetes] lecture de {} : {}", f, ex.getMessage());
        }
        familles = m;
    }

    /** Identifiant d'arme TaCZ (« tacz:ak47 ») ou null. */
    static String gunId(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains("GunId", Tag.TAG_STRING) ? t.getString("GunId") : null;
    }

    /** Étiquettes où le mod tactique range le vrai nom de ses objets (soins, grenades, armes de mêlée). */
    private static final String[] IDS_TACTIQUE = {"ConsumableId", "ThrowableId", "MeleeWeaponId"};

    /**
     * Nom « logique » d'un objet du mod tactique : tous ses soins sont le même objet Minecraft
     * ({@code heralisz_tactical:consumable}), leur vrai nom ({@code heralisz_heal:carfak}...) est dans leurs données. Null
     * pour un autre objet.
     */
    static String idTactique(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return null;
        for (String k : IDS_TACTIQUE) if (t.contains(k, Tag.TAG_STRING)) return t.getString(k);
        return null;
    }

    private static Method estCouteau;
    private static boolean estCouteauAbsent;

    /**
     * Couteau TaCZ : arme dont la fiche technique est arkaniaz:knife_data, comme le mod tactique la reconnaît pour ses coups
     * de mêlée ({@code fr.heraliz.tactique.onglets.Couteaux.estCouteau}, par réflexion) ; à défaut, d'après le nom.
     */
    static boolean couteau(ItemStack s) {
        String g = gunId(s);
        if (g == null) return false;
        if (!estCouteauAbsent) {
            try {
                if (estCouteau == null) {
                    estCouteau = Class.forName("fr.heraliz.tactique.onglets.Couteaux").getMethod("estCouteau", ItemStack.class);
                }
                if ((boolean) estCouteau.invoke(null, s)) return true;
            } catch (Throwable t) {
                estCouteauAbsent = true;
            }
        }
        String p = g.toLowerCase(Locale.ROOT);
        String nom = p.substring(p.indexOf(':') + 1);
        return p.contains("knife") || p.contains("karambit") || p.contains("butterfly") || p.contains("bayonet")
                || nom.equals("m9") || nom.startsWith("m9_");
    }

    static String famille(String gunId) {
        if (gunId == null) return "";
        if (familles == null) relireArmes();
        return familles.getOrDefault(gunId, "");
    }

    static boolean silencieux(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null || !t.contains("AttachmentMUZZLE")) return false;
        String m = t.get("AttachmentMUZZLE").toString().toLowerCase(Locale.ROOT);
        return m.contains("silencer") || m.contains("suppressor") || m.contains("silence");
    }

    /**
     * L'arme correspond-elle ? « feu » (toute arme TaCZ sauf couteaux), « melee » (mains nues, arme de mêlée, couteau),
     * « couteau », ou le début d'une famille (« pompe », « fusil », « precision », « pistolet », « smg »...).
     */
    static boolean arme(ItemStack main, String voulu) {
        if (voulu == null || voulu.isEmpty()) return true;
        String g = gunId(main);
        return switch (voulu.toLowerCase(Locale.ROOT)) {
            case "feu" -> g != null && !couteau(main);
            case "melee" -> g == null || couteau(main);
            case "couteau" -> couteau(main);
            default -> g != null && famille(g).startsWith(voulu.toLowerCase(Locale.ROOT));
        };
    }

    /** Usure de l'arme en pour cent (100 = neuve) ; 100 si ce n'est pas une arme. */
    static int usure(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null || gunId(s) == null || !t.contains("HzUsureEtat", Tag.TAG_ANY_NUMERIC)) return 100;
        return Math.max(0, Math.min(100, t.getInt("HzUsureEtat") / 10));
    }

    // ------------------------------------------------------------------ tutoriel des bases (CRac)
    private static Method tutoEtat, tutoProgression, tutoLancer, tutoEtapes, tutoJours, tutoSas;
    private static boolean tutoAbsent;

    private static boolean tuto() {
        if (tutoAbsent) return false;
        if (tutoEtat != null) return true;
        try {
            Class<?> c = Class.forName("fr.heraliz.crac.bases.TutorielApi");
            tutoEtat = c.getMethod("etat", ServerPlayer.class);
            tutoProgression = c.getMethod("progression", ServerPlayer.class);
            tutoLancer = c.getMethod("lancer", ServerPlayer.class);
            try {
                tutoEtapes = c.getMethod("etapes");
                tutoJours = c.getMethod("joursProtection");
            } catch (NoSuchMethodException ancien) {
                tutoEtapes = tutoJours = null;   // CRac 1.7 : 6 étapes, 4 jours
            }
            try {
                tutoSas = c.getMethod("sas", ServerPlayer.class);
            } catch (NoSuchMethodException ancien) {
                tutoSas = null;   // avant CRac 1.8.4 : pas de sas posé par Varga
            }
            return true;
        } catch (Throwable t) {
            tutoAbsent = true;
            LOG.warn("[heraliz_quetes] CRac absent ou différent ({}) : pas de tutoriel des bases par le Commandant Varga", t.toString());
            return false;
        }
    }

    /** -1 : pas de tutoriel (CRac absent, monde des bases absent) ; 0 : à faire ; 1 : kit reçu ; 2 : en cours ; 3 : fini. */
    static int tutoEtat(ServerPlayer p) {
        if (!tuto()) return -1;
        try {
            return (int) tutoEtat.invoke(null, p);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Nombre d'étapes de la cabane du tutoriel (CRac 1.8 : 9). */
    static int tutoEtapes() {
        if (!tuto() || tutoEtapes == null) return 6;
        try {
            return (int) tutoEtapes.invoke(null);
        } catch (Throwable t) {
            return 6;
        }
    }

    /** Varga vient de poser le sas (2e porte) devant la cabane de ce joueur. */
    static boolean tutoSas(ServerPlayer p) {
        if (!tuto() || tutoSas == null) return false;
        try {
            return (boolean) tutoSas.invoke(null, p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Jours de protection de la base à la fin du tutoriel (réglage de CRac). */
    static int tutoJours() {
        if (!tuto() || tutoJours == null) return 4;
        try {
            return (int) tutoJours.invoke(null);
        } catch (Throwable t) {
            return 4;
        }
    }

    /** {étape 1 à N (N + 1 = tout est fait), fait, total} de la cabane du tutoriel ; {0, 0, 0} hors du monde des bases. */
    static int[] tutoProgression(ServerPlayer p) {
        if (!tuto()) return new int[]{0, 0, 0};
        try {
            return (int[]) tutoProgression.invoke(null, p);
        } catch (Throwable t) {
            return new int[]{0, 0, 0};
        }
    }

    static boolean tutoLancer(ServerPlayer p) {
        if (!tuto()) return false;
        try {
            return (boolean) tutoLancer.invoke(null, p);
        } catch (Throwable t) {
            LOG.error("[heraliz_quetes] lancement du tutoriel des bases impossible", t);
            return false;
        }
    }

    /** La cinématique d'arrivée de HeraliZ Utils est finie (vue ou passée) ; vrai sans HeraliZ Utils. */
    static boolean cinematiqueFinie(Player p) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("heralisz_utils")) return true;
        CompoundTag root = p.getPersistentData();
        return root.getCompound(Player.PERSISTED_NBT_TAG).getBoolean("heraliz_cinematique_finie");
    }
}
