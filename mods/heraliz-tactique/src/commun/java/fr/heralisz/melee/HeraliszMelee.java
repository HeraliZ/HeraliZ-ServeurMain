package fr.heralisz.melee;

import net.minecraftforge.fml.common.Mod;

/**
 * heralisz_melee : contenu du moteur heralisz_tactical (armes de mêlée (index, recettes, modèles, animations, sons)). Avant la fusion, c'était un mod de données seulement
 * (lowcodefml, sans code) ; dans le jar fusionné HeraliZ Tactique, chaque [[mods]] doit avoir sa classe @Mod (FML 47) :
 * cette classe ne fait rien d'autre. Les données restent sous leur espace de noms d'origine : heralisz_melee:... (data/heralisz_melee dans le
 * jar -serveur, assets/heralisz_melee dans le jar -client), ids inchangés pour les packs, les gunpacks TaCZ et les autres mods.
 * Ses objets sont dans l'onglet « HeraliZ · Tactique » (groupes déclarés par fr.heraliz.tactique.HeralizTactique).
 */
@Mod(HeraliszMelee.MODID)
public final class HeraliszMelee {
   public static final String MODID = "heralisz_melee";

   public HeraliszMelee() {
   }
}
