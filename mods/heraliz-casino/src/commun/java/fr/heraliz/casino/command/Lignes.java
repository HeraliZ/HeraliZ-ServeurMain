package fr.heraliz.casino.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texte d'une ligne de simulation (/casino roll, page « Probabilites » de la Tablette du Directeur) : lu des deux cotes
 * (la commande cote serveur, l'ecran des operateurs cote client). Corps d'origine de CasinoCommand.rollLine.
 */
public final class Lignes {
    private Lignes() {
    }

    public static MutableComponent rollLine(fr.heraliz.casino.game.Simulator.Line l) {
        Object[] args = new Object[l.args().length];
        for (int i = 0; i < args.length; i++) {
            String a = l.args()[i];
            args[i] = a.startsWith("@") ? Component.translatable("roll.heralizcasino.name." + a.substring(1)) : a;
        }
        return Component.translatable("roll.heralizcasino." + l.key(), args);
    }
}
