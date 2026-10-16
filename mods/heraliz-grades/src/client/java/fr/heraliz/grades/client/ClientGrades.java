package fr.heraliz.grades.client;

import fr.heraliz.grades.pont.Partie;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client : le menu /kit (ouvert par le serveur), le nouveau tab et les textes des grades (lang/). */
public final class ClientGrades implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ReseauGrades.RECEPTION_CLIENT.brancher(EcranKits::recevoir);
      ReseauGrades.RECEPTION_TAB.brancher(TabHeraliz::recevoir);
      MinecraftForge.EVENT_BUS.addListener(TabHeraliz::avantOverlay);
   }
}
