package fr.heralisz.economie.api;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Poste sur le bus Forge (MinecraftForge.EVENT_BUS, fil serveur) apres chaque changement de solde. Informatif :
 * non annulable, le changement est deja fait et journalise.
 */
public final class SoldeChangeEvent extends Event {
    private final UUID uuid;
    private final long avant, apres;
    private final String motif;

    public SoldeChangeEvent(UUID uuid, long avant, long apres, String motif) {
        this.uuid = uuid;
        this.avant = avant;
        this.apres = apres;
        this.motif = motif == null ? "" : motif;
    }

    public UUID uuid() {
        return uuid;
    }

    public long avant() {
        return avant;
    }

    public long apres() {
        return apres;
    }

    /** apres - avant. */
    public long delta() {
        return apres - avant;
    }

    public String motif() {
        return motif;
    }
}
