package fr.heralisz.bases.pillage;

import fr.heralisz.bases.blindage.BlindageRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * POINT D'ENTREE du module pillage (BASES_SPEC §4 pillage, §5 blindage et C4), appele une fois par le constructeur du
 * mod ({@code HeraliszBases}) APRES l'enregistrement du reseau du coeur :
 * <ul>
 *   <li>{@code ReglesPillage} branche sur le coeur ({@code BasesApi.pillage}) : droits des pillards, {@code /base piller},
 *   liste PILLER, attaquants, sortie, sous-commandes {@code /base admin coups|c4|pillages|pillage|entrees|protection} ;</li>
 *   <li>registres du blindage ({@code blindage_1..8}, sons, onglet creatif) ;</li>
 *   <li>config {@code heralisz_bases-pillage.toml} ({@link PillageConfig}) ;</li>
 *   <li>paquets {@link PillageNet} (apres ceux du coeur) ;</li>
 *   <li>evenements : {@code PillageEvents} (fenetre, mort), {@code Blindage} (explosions, coups, fissures, infobulle,
 *   table), {@code C4Garde} (C4 gardes charges).</li>
 * </ul>
 * Etat : sessions / raids en memoire ({@code Pillages}), journal {@code Base.pillages} et entrees du jour dans bases.json,
 * coups dans la SavedData {@code heralisz_bases_coups} de la dimension.
 */
public final class PillageModule {
    private PillageModule() {
    }

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PillageConfig.SPEC, PillageConfig.FICHIER);
        BlindageRegistry.register(modBus);
        PillageNet.register();
        // regles des pillards (BasesApi.pillage(new ReglesPillage())), PillageEvents, Blindage et C4Garde : partie
        // serveur du mod ({@code bases.serveur.PartieServeur#pillage}), juste apres les ecouteurs du coeur
    }
}
