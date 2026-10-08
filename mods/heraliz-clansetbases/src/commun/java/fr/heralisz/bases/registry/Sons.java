package fr.heralisz.bases.registry;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du mod (fichiers sounds/*.ogg et sounds.json produits par tools/bases/gen_sons.py, jamais extraits d'un jeu).
 * Les ids sont les chemins : heralisz_bases:cloison...
 */
public final class Sons {
    private Sons() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszBases.MODID);

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(HeraliszBases.id(id)));
    }

    /** Cloison de coque qui se desagrege (deblocage d'une salle). */
    public static final RegistryObject<SoundEvent> CLOISON = son("cloison");
    /** Teleportation : charge (debut du compte a rebours). */
    public static final RegistryObject<SoundEvent> TP_CHARGE = son("tp_charge");
    /** Teleportation : arrivee dans le sas / retour. */
    public static final RegistryObject<SoundEvent> TP_ARRIVEE = son("tp_arrivee");
    /** Teleportation annulee, action refusee. */
    public static final RegistryObject<SoundEvent> REFUS = son("refus");
    /** Alerte : base attaquee (toast des membres). */
    public static final RegistryObject<SoundEvent> ALERTE = son("alerte");
    /** Notification positive : base creee, salle debloquee. */
    public static final RegistryObject<SoundEvent> SUCCES = son("succes");
    /** Pas et coups sur la coque. */
    public static final RegistryObject<SoundEvent> COQUE_PAS = son("coque_pas");
    public static final RegistryObject<SoundEvent> COQUE_COUP = son("coque_coup");

    /** Sons de bloc de la coque et de la sortie (aucun son vanilla). */
    public static final ForgeSoundType COQUE = new ForgeSoundType(1.0f, 1.0f, COQUE_COUP, COQUE_PAS, COQUE_COUP, COQUE_COUP, COQUE_PAS);

    /** Son par chemin (NoticeS2C.son), null si inconnu. */
    public static SoundEvent parChemin(String chemin) {
        if (chemin == null || chemin.isEmpty()) return null;
        return ForgeRegistries.SOUND_EVENTS.getValue(HeraliszBases.id(chemin));
    }
}
