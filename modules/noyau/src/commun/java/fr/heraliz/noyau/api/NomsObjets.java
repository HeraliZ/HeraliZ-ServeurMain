package fr.heraliz.noyau.api;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nom d'un objet tel que le joueur le voit, pour tout message que le SERVEUR envoie (03/11, retour du propriétaire : « quand
 * on chope un objet, par exemple dans une caisse, il y a écrit dans le chat item.tacz.modern_kinetic_gun »).
 * <p>
 * Pourquoi : sur le serveur, {@code ItemStack.getHoverName()} d'une arme, d'une munition ou d'un accessoire de TaCZ rend la
 * clé générique de l'objet ({@code item.tacz.modern_kinetic_gun}) : TaCZ ne trouve le vrai nom (celui du gunpack) que dans
 * son index du JOUEUR. Le texte envoyé tel quel s'affiche donc chez le joueur avec la clé brute. Ici, le nom vient de
 * l'index commun de TaCZ (même clé de traduction que chez le joueur : {@code GunIndexPOJO.getName()}, lu par réflexion,
 * TaCZ reste facultatif) ; un nom donné à l'objet (enclume, bon, staff) passe avant ; tout autre objet garde son nom
 * habituel. Le joueur traduit la clé dans sa langue.
 * <p>
 * Une seule aide pour tout le serveur, dans le noyau que tous les modules voient (03/11 : d'abord dans HeraliZ Grades,
 * déplacée ici pour la Tactique, le Monde et CRac) : Grades, Caisses, Casino, Quêtes, Usure, objets à vie, zones,
 * véhicules, tutoriel des bases ; {@code fr.heraliz.grades.serveur.GradesApi.nomObjet} y renvoie. Ne jamais renommer.
 * Depuis n'importe quel fil (méthodes de TaCZ gardées une fois trouvées).
 */
public final class NomsObjets {
    private static final Logger LOG = LogUtils.getLogger();
    /** Étiquette de l'objet TaCZ qui porte son identifiant -> méthode de TimelessAPI qui rend son index commun. */
    private static final String[][] TACZ = {{"GunId", "getCommonGunIndex"}, {"AmmoId", "getCommonAmmoIndex"},
            {"AttachmentId", "getCommonAttachmentIndex"}};
    private static final Map<String, Method> INDEX = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> POJO = new ConcurrentHashMap<>(), NOM = new ConcurrentHashMap<>();
    private static volatile boolean taczCherche, taczAbsent;

    private NomsObjets() {
    }

    /** Le nom de l'objet (sans quantité), à mettre dans un message. Vide pour une pile vide. */
    public static Component nom(ItemStack st) {
        if (st == null || st.isEmpty()) return Component.empty();
        if (st.hasCustomHoverName()) return st.getHoverName();
        String cle = cleTacz(st);
        return cle != null ? Component.translatable(cle) : st.getHoverName();
    }

    /**
     * Comme {@code ItemStack.getDisplayName()} (« [nom] » dans la couleur de la rareté de Minecraft, l'objet montré au
     * survol), avec le vrai nom de {@link #nom}.
     */
    public static MutableComponent nomCrochets(ItemStack st) {
        MutableComponent n = Component.empty().append(nom(st));
        if (st != null && st.hasCustomHoverName()) n.withStyle(ChatFormatting.ITALIC);
        MutableComponent c = ComponentUtils.wrapInSquareBrackets(n);
        if (st != null && !st.isEmpty()) {
            c.withStyle(st.getRarity().getStyleModifier())
                    .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(st))));
        }
        return c;
    }

    /** Clé de traduction du vrai nom d'un objet de TaCZ (arme, munition, accessoire), ou null. */
    private static String cleTacz(ItemStack st) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (id == null || !"tacz".equals(id.getNamespace()) || st.getTag() == null || !chercherTacz()) return null;
        for (String[] t : TACZ) {
            if (!st.getTag().contains(t[0], net.minecraft.nbt.Tag.TAG_STRING)) continue;
            ResourceLocation rid = ResourceLocation.tryParse(st.getTag().getString(t[0]));
            Method m = INDEX.get(t[0]);
            if (rid == null || m == null) return null;
            try {
                Object o = m.invoke(null, rid);
                if (!(o instanceof Optional<?> opt) || opt.isEmpty()) return null;
                Object index = opt.get();
                Method pojo = POJO.computeIfAbsent(index.getClass(), NomsObjets::methodePojo);
                Object p = pojo == null ? null : pojo.invoke(index);
                Method nom = p == null ? null : NOM.computeIfAbsent(p.getClass(), NomsObjets::methodeNom);
                Object n = nom == null ? null : nom.invoke(p);
                return n instanceof String s && !s.isBlank() ? s : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static synchronized boolean chercherTacz() {
        if (taczCherche) return !taczAbsent;
        taczCherche = true;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            for (String[] t : TACZ) INDEX.put(t[0], api.getMethod(t[1], ResourceLocation.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            taczAbsent = true;
            LOG.info("[noyau] TaCZ absent ou différent ({}) : noms des objets sans index d'armes", e.toString());
        }
        return !taczAbsent;
    }

    private static Method methodePojo(Class<?> c) {
        try {
            return c.getMethod("getPojo");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method methodeNom(Class<?> c) {
        try {
            return c.getMethod("getName");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
