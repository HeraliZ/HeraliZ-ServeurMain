package fr.heralisz.grenade;

import net.minecraftforge.fml.common.Mod;

/**
 * heralisz_grenade : contenu du moteur heralisz_tactical (lancers : index, recettes, modèles, animations, sons ; C4 retiré le 16/10). Avant la fusion, c'était un mod de données seulement
 * (lowcodefml, sans code) ; dans le jar fusionné HeraliZ Tactique, chaque [[mods]] doit avoir sa classe @Mod (FML 47) :
 * cette classe ne fait rien d'autre. Les données restent sous leur espace de noms d'origine : heralisz_grenade:... (data/heralisz_grenade dans le
 * jar -serveur, assets/heralisz_grenade dans le jar -client), ids inchangés pour les packs, les gunpacks TaCZ et les autres mods.
 * Ses objets sont dans l'onglet « HeraliZ · Tactique » (groupes déclarés par fr.heraliz.tactique.HeralizTactique).
 */
@Mod(HeraliszGrenade.MODID)
public final class HeraliszGrenade {
   public static final String MODID = "heralisz_grenade";

   public HeraliszGrenade() {
   }
}
