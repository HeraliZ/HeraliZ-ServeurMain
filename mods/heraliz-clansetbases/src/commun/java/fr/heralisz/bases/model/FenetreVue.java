package fr.heralisz.bases.model;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Etat de la fenetre de pillage pour les ecrans : ouverte ou non, heures configurees, temps restant avant le prochain
 * changement (ouverture si fermee, fermeture si ouverte ; -1 si forcee sans fin), mode force par un admin.
 */
public record FenetreVue(boolean ouverte, int debutH, int finH, long msAvantChangement, Mode mode) {
    public enum Mode {AUTO, OUVERTE, FERMEE}

    public void write(FriendlyByteBuf b) {
        b.writeBoolean(ouverte);
        b.writeByte(debutH);
        b.writeByte(finH);
        b.writeLong(msAvantChangement);
        b.writeByte(mode.ordinal());
    }

    public static FenetreVue read(FriendlyByteBuf b) {
        boolean o = b.readBoolean();
        int d = b.readByte(), f = b.readByte();
        long ms = b.readLong();
        int m = b.readByte();
        return new FenetreVue(o, d, f, ms, m >= 0 && m < Mode.values().length ? Mode.values()[m] : Mode.AUTO);
    }
}
