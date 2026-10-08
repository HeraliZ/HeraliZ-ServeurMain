package fr.heralisz.clans.territory;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Etat persistant d'une zone (SavedData heralisz_clans_territoires) : proprietaire, date de capture, prochain
 * ravitaillement possible, fin de la protection, progression de la capture en cours et clan qui capture. Les dates
 * sont en ms epoch (independantes du temps de jeu, survivent aux redemarrages).
 */
public final class EtatTerritoire {
    @Nullable
    public UUID proprio;
    public long captureLe;
    public long prochainRavitaillement;
    public long protegeeJusque;
    /** Progression du clan capteur, 0..1. */
    public float progression;
    @Nullable
    public UUID capteur;

    public boolean libre() {
        return proprio == null;
    }

    /** Retour a l'etat libre (reset admin, clan disparu). */
    public void liberer() {
        proprio = null;
        captureLe = 0;
        prochainRavitaillement = 0;
        protegeeJusque = 0;
        progression = 0;
        capteur = null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (proprio != null) t.putUUID("proprio", proprio);
        t.putLong("captureLe", captureLe);
        t.putLong("prochainRavitaillement", prochainRavitaillement);
        t.putLong("protegeeJusque", protegeeJusque);
        t.putFloat("progression", progression);
        if (capteur != null) t.putUUID("capteur", capteur);
        return t;
    }

    public static EtatTerritoire load(CompoundTag t) {
        EtatTerritoire e = new EtatTerritoire();
        if (t.hasUUID("proprio")) e.proprio = t.getUUID("proprio");
        e.captureLe = t.getLong("captureLe");
        e.prochainRavitaillement = t.getLong("prochainRavitaillement");
        e.protegeeJusque = t.getLong("protegeeJusque");
        e.progression = Math.max(0, Math.min(1, t.getFloat("progression")));
        if (t.hasUUID("capteur")) e.capteur = t.getUUID("capteur");
        return e;
    }
}
