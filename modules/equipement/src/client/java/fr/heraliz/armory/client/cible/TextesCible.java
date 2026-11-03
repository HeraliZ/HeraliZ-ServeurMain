package fr.heraliz.armory.client.cible;

import fr.heraliz.armory.cible.Tenues;
import fr.heraliz.armory.network.PaquetsCible;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Textes de la cible d'essai (03/11) : nom de la tenue, zones, nombres (virgule en français et en russe). */
final class TextesCible {
    private TextesCible() {}

    /** Couleurs des raretés (mêmes que les objets de l'Armurerie et HeraliZ Loot). */
    static int couleur(String rarete) {
        return switch (rarete) {
            case "uncommon" -> 0x55FF55;
            case "rare" -> 0x5599FF;
            case "epic" -> 0xC060FF;
            case "legendary" -> 0xFFAA00;
            case "unique" -> 0xFF3B3B;
            case "common" -> 0xFFFFFF;
            default -> 0x8F95A0;
        };
    }

    /** « Rempart (Rare) », « Sans armure ». */
    static MutableComponent tenue(String id) {
        Tenues.Tenue t = Tenues.de(id);
        MutableComponent c = t.nom().copy();
        if (t.rang() > 0) c.append(" (").append(Component.translatable("rarity.heraliz_armory." + t.rarete())).append(")");
        return c;
    }

    /** Nom affiché au-dessus de la cible visée. */
    static Component etiquette(String id) {
        return Component.translatable("gui.heraliz_armory.cible.etiquette", tenue(id));
    }

    static Component zone(byte zone) {
        return Component.translatable(switch (zone) {
            case PaquetsCible.TETE -> "gui.heraliz_armory.cible.zone.tete";
            case PaquetsCible.MELEE -> "gui.heraliz_armory.cible.zone.melee";
            case PaquetsCible.EXPLOSION -> "gui.heraliz_armory.cible.zone.explosion";
            default -> "gui.heraliz_armory.cible.zone.corps";
        });
    }

    /** Un nombre à une décimale, virgule en français et en russe : « 7,5 ». */
    static String nombre(float v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        String langue = Minecraft.getInstance().getLanguageManager().getSelected();
        return langue != null && (langue.startsWith("fr") || langue.startsWith("ru")) ? s.replace('.', ',') : s;
    }

    /** Part prise par l'armure, en % entier. */
    static String pourcent(float arme, float recu) {
        if (arme <= 0.0F) return "0";
        return String.valueOf(Math.round(Math.max(0.0F, 1.0F - recu / arme) * 100.0F));
    }
}
