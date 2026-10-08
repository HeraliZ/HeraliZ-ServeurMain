package fr.heralisz.matchs.moteur;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du moteur (generes par {@code tools/matchs/gen_sons_moteur.py}, aucun son extrait d'un jeu) : annonces
 * (normale, victoire, defaite, finale, manche), compte a rebours (tic, depart), fil des eliminations, invitation,
 * choix de classe, fin. Ids {@code heralisz_matchs:moteur_*} (voir aussi les constantes {@code Annonces.SON_*}).
 */
public final class MoteurSons {
    private MoteurSons() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszMatchs.MODID);

    public static final RegistryObject<SoundEvent> ANNONCE = son("moteur_annonce");
    public static final RegistryObject<SoundEvent> VICTOIRE = son("moteur_victoire");
    public static final RegistryObject<SoundEvent> DEFAITE = son("moteur_defaite");
    public static final RegistryObject<SoundEvent> FINALE = son("moteur_finale");
    public static final RegistryObject<SoundEvent> MANCHE = son("moteur_manche");
    public static final RegistryObject<SoundEvent> COMPTE = son("moteur_compte");
    public static final RegistryObject<SoundEvent> DEPART = son("moteur_depart");
    public static final RegistryObject<SoundEvent> ELIMINATION = son("moteur_elimination");
    public static final RegistryObject<SoundEvent> INVITATION = son("moteur_invitation");
    public static final RegistryObject<SoundEvent> CHOIX = son("moteur_choix");
    public static final RegistryObject<SoundEvent> FIN = son("moteur_fin");

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(HeraliszMatchs.id(id)));
    }

    public static void enregistrer(IEventBus modBus) {
        SONS.register(modBus);
    }
}
