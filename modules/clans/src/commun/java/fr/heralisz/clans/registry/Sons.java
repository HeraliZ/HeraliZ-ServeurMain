package fr.heralisz.clans.registry;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du mod (fichiers sounds/*.ogg et sounds.json produits par tools/clans/gen_sounds.py). Les ids sont les
 * chemins : heralisz_clans:ui_clic...
 */
public final class Sons {
    private Sons() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszClans.MODID);

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(HeraliszClans.id(id)));
    }

    public static final RegistryObject<SoundEvent> UI_CLIC = son("ui_clic");
    public static final RegistryObject<SoundEvent> UI_OUVRIR = son("ui_ouvrir");
    public static final RegistryObject<SoundEvent> UI_FERMER = son("ui_fermer");
    public static final RegistryObject<SoundEvent> INVITATION = son("invitation");
    public static final RegistryObject<SoundEvent> QUETE_FINIE = son("quete_finie");
    public static final RegistryObject<SoundEvent> NIVEAU_SUP = son("niveau_sup");
    public static final RegistryObject<SoundEvent> CAPTURE_DEBUT = son("capture_debut");
    public static final RegistryObject<SoundEvent> CAPTURE_REUSSIE = son("capture_reussie");
    public static final RegistryObject<SoundEvent> ZONE_PERDUE = son("zone_perdue");
    public static final RegistryObject<SoundEvent> SIGNAL_FUSEE = son("signal_fusee");
    public static final RegistryObject<SoundEvent> AVION_MOTEUR = son("avion_moteur");
    public static final RegistryObject<SoundEvent> PARACHUTE_OUVERT = son("parachute_ouvert");
    public static final RegistryObject<SoundEvent> CAISSE_ATTERRIT = son("caisse_atterrit");
    public static final RegistryObject<SoundEvent> BANQUE = son("banque");

    /** Son par chemin (NoticeS2C.son), null si inconnu. */
    public static SoundEvent parChemin(String chemin) {
        if (chemin == null || chemin.isEmpty()) return null;
        return ForgeRegistries.SOUND_EVENTS.getValue(HeraliszClans.id(chemin));
    }
}
