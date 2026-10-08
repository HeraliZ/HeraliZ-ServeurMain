package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Entites d'affichage vanilla ({@code item_display}, {@code text_display}) utilisees par le jeu : arme de la roulette de
 * la boite mystere, peluche, rendu de secours des bonus (texte DM Sans en couleur). Configurees par NBT (les setters
 * sont prives en 1.20.1). Chaque entite porte {@link ZombiesApi#TAG_DECOR} (nettoyage apres crash). Fil serveur.
 */
final class Affichages {
    private Affichages() {
    }

    /** Pose un {@code item_display} tournant face au joueur (billboard vertical), ou null en cas d'echec. */
    @Nullable
    static Display.ItemDisplay objet(ServerLevel niveau, double x, double y, double z, ItemStack pile, float echelle) {
        Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(niveau);
        if (d == null) return null;
        try {
            CompoundTag t = d.saveWithoutId(new CompoundTag());
            t.putString("billboard", "vertical");
            t.putString("item_display", "fixed");
            t.put("transformation", transformation(echelle));
            t.putInt("interpolation_duration", 2);
            t.put("item", pile.save(new CompoundTag()));
            d.load(t);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] item_display : {}", ex.toString());
        }
        d.moveTo(x, y, z, 0f, 0f);
        d.getPersistentData().putBoolean(ZombiesApi.TAG_DECOR, true);
        niveau.addFreshEntity(d);
        return d;
    }

    /** Change l'objet affiche. */
    static void changer(@Nullable Display.ItemDisplay d, ItemStack pile) {
        if (d == null || d.isRemoved()) return;
        d.getSlot(0).set(pile);
    }

    /**
     * Contour lumineux (lueur vanilla des entites) de cette couleur, ou aucun si {@code rgb} &lt; 0. Sert a la couleur de
     * rarete de la roulette et a l'arme du Pack-a-Punch.
     */
    static void lueur(@Nullable Display d, int rgb) {
        if (d == null || d.isRemoved()) return;
        try {
            if (rgb < 0) {
                d.setGlowingTag(false);
                return;
            }
            CompoundTag t = d.saveWithoutId(new CompoundTag());
            if (t.getInt("glow_color_override") == (rgb & 0xFFFFFF) && d.isCurrentlyGlowing()) return;
            t.putInt("glow_color_override", rgb & 0xFFFFFF);
            d.load(t);
            d.setGlowingTag(true);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[zombies] lueur d'affichage : {}", ex.toString());
        }
    }

    /** Change le texte d'un {@code text_display}. */
    static void changerTexte(@Nullable Display.TextDisplay d, Component texte) {
        if (d == null || d.isRemoved()) return;
        try {
            CompoundTag t = d.saveWithoutId(new CompoundTag());
            t.putString("text", Component.Serializer.toJson(texte));
            d.load(t);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[zombies] texte d'affichage : {}", ex.toString());
        }
    }

    /** Deplace l'affichage (interpolation cote client). */
    static void placer(@Nullable Display d, double x, double y, double z) {
        if (d != null && !d.isRemoved()) d.setPos(x, y, z);
    }

    /** Pose un {@code text_display} (billboard centre, fond transparent, ombre), ou null. */
    @Nullable
    static Display.TextDisplay texte(ServerLevel niveau, double x, double y, double z, Component texte, float echelle) {
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(niveau);
        if (d == null) return null;
        try {
            CompoundTag t = d.saveWithoutId(new CompoundTag());
            t.putString("billboard", "center");
            t.putString("text", Component.Serializer.toJson(texte));
            t.putInt("background", 0);
            t.putBoolean("shadow", true);
            t.putBoolean("see_through", false);
            t.put("transformation", transformation(echelle));
            d.load(t);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] text_display : {}", ex.toString());
        }
        d.moveTo(x, y, z, 0f, 0f);
        d.getPersistentData().putBoolean(ZombiesApi.TAG_DECOR, true);
        niveau.addFreshEntity(d);
        return d;
    }

    private static CompoundTag transformation(float s) {
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", quaternion());
        tr.put("right_rotation", quaternion());
        net.minecraft.nbt.ListTag tl = new net.minecraft.nbt.ListTag();
        tl.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        tl.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        tl.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        tr.put("translation", tl);
        net.minecraft.nbt.ListTag sc = new net.minecraft.nbt.ListTag();
        sc.add(net.minecraft.nbt.FloatTag.valueOf(s));
        sc.add(net.minecraft.nbt.FloatTag.valueOf(s));
        sc.add(net.minecraft.nbt.FloatTag.valueOf(s));
        tr.put("scale", sc);
        return tr;
    }

    private static net.minecraft.nbt.ListTag quaternion() {
        net.minecraft.nbt.ListTag q = new net.minecraft.nbt.ListTag();
        q.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        q.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        q.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        q.add(net.minecraft.nbt.FloatTag.valueOf(1f));
        return q;
    }

    /** Retire l'entite si elle existe. */
    static void retirer(@Nullable Entity e) {
        if (e != null && !e.isRemoved()) e.discard();
    }
}
