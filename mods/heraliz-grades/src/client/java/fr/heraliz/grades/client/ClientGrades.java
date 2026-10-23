package fr.heraliz.grades.client;

import fr.heraliz.grades.pont.Partie;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client : les menus /kit et /warp (ouverts par le serveur), le nouveau tab, l'éditeur des grades des op et les textes (lang/). */
public final class ClientGrades implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ReseauGrades.RECEPTION_CLIENT.brancher(EcranKits::recevoir);
      ReseauGrades.RECEPTION_TAB.brancher(TabHeraliz::recevoir);
      ReseauGrades.RECEPTION_WARPS.brancher(EcranWarps::recevoir);
      // grades réglés en jeu (2.0.0) : définitions du serveur, éditeur des op
      fr.heraliz.grades.Grade.LANGUE = () -> net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
      ReseauGrades.RECEPTION_DEFINITIONS.brancher(d -> fr.heraliz.grades.Grade.appliquer(d.grades(), true));
      ReseauGrades.RECEPTION_EDITEUR.brancher(EcranEditeurGrades::recevoir);
      // Points Boutique : /boutique et /trade
      fr.heraliz.grades.reseau.ReseauBoutique.RECEPTION_BOUTIQUE.brancher(EcranBoutique::recevoir);
      fr.heraliz.grades.reseau.ReseauBoutique.RECEPTION_ECHANGE.brancher(EcranEchange::recevoir);
      MinecraftForge.EVENT_BUS.addListener(TabHeraliz::avantOverlay);
      // bons (2.5.0) : éditeur des op, effet « Bonus de loot » de l'inventaire (avant l'enregistrement de l'effet)
      fr.heraliz.grades.bon.EffetBonusLoot.AFFICHAGE = new AffichageBonusLoot();
      fr.heraliz.grades.bon.ReseauBon.RECEPTION_EDITEUR.brancher(EcranBon::recevoir);
   }
}
