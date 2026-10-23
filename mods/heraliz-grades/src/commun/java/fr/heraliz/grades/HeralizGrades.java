package fr.heraliz.grades;

import fr.heraliz.grades.pont.Parties;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * HeraliZ Grades : les grades du serveur (staff, boutique, Survivant pour tous), leur préfixe dans la liste Tab et le
 * chat, et les commandes /grade. Tout se décide côté serveur ; seule la console du serveur donne ou retire un grade.
 */
@Mod(HeralizGrades.MODID)
public class HeralizGrades {
   public static final String MODID = "heraliz_grades";

   /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
   public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

   public HeralizGrades() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ObjetRegistre.OBJETS.register(bus);
      ReseauGrades.enregistrer();
      fr.heraliz.grades.reseau.ReseauBoutique.enregistrer();
      // bons réglés par les op (2.5.0) : objet, effet « Bonus de loot », canal de l'éditeur
      fr.heraliz.grades.bon.ObjetBon.OBJETS.register(bus);
      fr.heraliz.grades.bon.ObjetBon.EFFETS.register(bus);
      fr.heraliz.grades.bon.ReseauBon.enregistrer();
      Parties.serveur(MODID, "fr.heraliz.grades.serveur.ServeurGrades", bus);
      Parties.client(MODID, "fr.heraliz.grades.client.ClientGrades", bus);
   }
}
