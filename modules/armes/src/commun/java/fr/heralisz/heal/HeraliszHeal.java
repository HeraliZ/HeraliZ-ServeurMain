package fr.heralisz.heal;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

/**
 * heralisz_heal : contenu du moteur heralisz_tactical (soins (index, recettes, modèles, animations, sons)). Avant la fusion, c'était un mod de données seulement
 * (lowcodefml, sans code) ; dans le jar fusionné HeraliZ Tactique, chaque [[mods]] doit avoir sa classe @Mod (FML 47) :
 * cette classe ne fait rien d'autre. Les données restent sous leur espace de noms d'origine : heralisz_heal:... (data/heralisz_heal dans le
 * jar -serveur, assets/heralisz_heal dans le jar -client), ids inchangés pour les packs, les gunpacks TaCZ et les autres mods.
 * Ses objets sont dans l'onglet « HeraliZ · Tactique » (groupes déclarés par fr.heraliz.tactique.HeralizTactique).
 */
@Mod(HeraliszHeal.MODID)
public final class HeraliszHeal {
   public static final String MODID = "heralisz_heal";

   /**
    * Soins retirés du jeu : identifiant figé (CLAUDE.md, règle 8), toujours enregistré et utilisable par ceux qui en ont,
    * mais plus montrés dans l'onglet « HeraliZ · Tactique » (ModItems.fillConsumables). Ni loot, ni fabrication, ni
    * marchand, ni caisse : même liste dans outils/donnees/objets_retires.json, lue par les outils qui écrivent ces fichiers.
    * 03/11 (choix du propriétaire) : « Attelle et bandage », remplacé par le Bandage et l'Attelle séparés.
    */
   public static final Set<ResourceLocation> RETIRES = Set.of(new ResourceLocation(MODID, "attelle_bandage"));

   public HeraliszHeal() {
   }
}
