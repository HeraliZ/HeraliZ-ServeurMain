package fr.heraliz.clashwar.tournoi;

import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du ClashWar (generes par {@code tools/matchs/sons/clashwar.py}, aucun son extrait d'un jeu). Les annonces du
 * moteur les designent par leur id texte ({@link #id}).
 */
public final class CwSons {
    private CwSons() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(Registries.SOUND_EVENT, HeralizClashWar.MODID);

    /** Annonce de l'evenement (H-15, ouverture des inscriptions). */
    public static final RegistryObject<SoundEvent> ANNONCE = son("cw_annonce");
    /** Inscription confirmee / joueur inscrit. */
    public static final RegistryObject<SoundEvent> INSCRIPTION = son("cw_inscription");
    /** Appel d'un match (depart vers l'arene). */
    public static final RegistryObject<SoundEvent> APPEL = son("cw_appel");
    /** Debut du combat. */
    public static final RegistryObject<SoundEvent> COMBAT = son("cw_combat");
    /** Elimination. */
    public static final RegistryObject<SoundEvent> ELIMINATION = son("cw_elimination");
    /** Mort subite : la zone se resserre. */
    public static final RegistryObject<SoundEvent> ZONE = son("cw_zone");
    /** Victoire d'un match. */
    public static final RegistryObject<SoundEvent> VICTOIRE = son("cw_victoire");
    /** Defaite d'un match. */
    public static final RegistryObject<SoundEvent> DEFAITE = son("cw_defaite");
    /** Annonce de la finale. */
    public static final RegistryObject<SoundEvent> FINALE = son("cw_finale");
    /** Sacre du vainqueur du tournoi. */
    public static final RegistryObject<SoundEvent> SACRE = son("cw_sacre");
    /** Classe choisie. */
    public static final RegistryObject<SoundEvent> CHOIX = son("cw_choix");

    private static RegistryObject<SoundEvent> son(String nom) {
        return SONS.register(nom, () -> SoundEvent.createVariableRangeEvent(HeralizClashWar.id(nom)));
    }

    /** Id texte d'un son pour {@code Annonces.Annonce} ("heraliz_clashwar:cw_finale"). */
    public static String id(RegistryObject<SoundEvent> son) {
        return son.getId().toString();
    }
}
