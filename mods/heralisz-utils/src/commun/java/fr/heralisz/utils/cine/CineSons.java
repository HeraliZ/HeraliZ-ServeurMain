package fr.heralisz.utils.cine;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons de la cinématique d'arrivée (registre commun). Aucun son vanilla : tous sont synthétisés sur mesure par
 * tools/gen_cine_sons.py (OGG mono dans assets/heralisz_utils/sounds/cine, entrées « cine.* » de sounds.json). Les
 * entrées à variantes (PAS, PAS_COURSE, FRAPPE_VITRE, IMPACT_VITRE, GOULE_RALE) tirent un fichier au hasard à chaque
 * lecture. Boucles parfaites : COEUR_RAPIDE (4 s), MASTICATION (5 s), LAMPE_GRESILLE (3 s), HORDE (6 s), ESSOUFFLEMENT (4 s),
 * SOUFFLE_COURSE (3 s).
 */
public final class CineSons {
    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszUtils.MODID);
    public static final RegistryObject<SoundEvent> PAS = son("cine.pas"), FRAPPE_VITRE = son("cine.frappe_vitre"),
            IMPACT_VITRE = son("cine.impact_vitre"), VITRE_FISSURE = son("cine.vitre_fissure"),
            GOULE_HURLEMENT = son("cine.goule_hurlement"), GOULE_RALE = son("cine.goule_rale"),
            RACLEMENT_VITRE = son("cine.raclement_vitre"), DECHIREMENT = son("cine.dechirement"),
            CHOC_PILIER = son("cine.choc_pilier"), CHUTE_BANQUETTE = son("cine.chute_banquette"),
            COEUR_RAPIDE = son("cine.coeur_rapide"), COEUR_LENT = son("cine.coeur_lent"),
            SOUFFLE_PANIQUE = son("cine.souffle_panique"), SOUFFLE_REVEIL = son("cine.souffle_reveil"),
            TISSU = son("cine.tissu"), PORTE_COULISSANTE = son("cine.porte_coulissante"),
            PORTE_CABINE = son("cine.porte_cabine"), MASTICATION = son("cine.mastication"),
            LAMPE_GRESILLE = son("cine.lampe_gresille"), CRAQUEMENT_COU = son("cine.craquement_cou"),
            HURLEMENT_CHAUFFEUR = son("cine.hurlement_chauffeur"), GRAVE_TERMINUS = son("cine.grave_terminus"),
            KLAXON_PROCHE = son("cine.klaxon_proche"), DERAILLEMENT = son("cine.deraillement"),
            CRASH = son("cine.crash"), EXPLOSION = son("cine.explosion"), DEBRIS = son("cine.debris"),
            EJECTION = son("cine.ejection"), ACOUPHENE = son("cine.acouphene"), CARILLON_TITRE = son("cine.carillon_titre"),
            HORDE = son("cine.horde"), PAS_COURSE = son("cine.pas_course"), ESSOUFFLEMENT = son("cine.essoufflement"),
            PORTE_PNEUMATIQUE = son("cine.porte_pneumatique"), DEMARRAGE = son("cine.demarrage"),
            SAUT_ZOMBIE = son("cine.saut_zombie"), SOUFFLE_COURSE = son("cine.souffle_course"),
            BATTEMENT = son("cine.battement"), GOULE_VITRE_CRI = son("cine.goule_vitre_cri"),
            GOULE_VITRE_RALE = son("cine.goule_vitre_rale");

    private CineSons() {}

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszUtils.MODID, id)));
    }
}
