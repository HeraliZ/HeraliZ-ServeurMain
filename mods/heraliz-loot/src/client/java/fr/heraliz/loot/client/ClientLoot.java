package fr.heraliz.loot.client;

import fr.heraliz.loot.ConteneurBlock;
import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.Rarete;
import fr.heraliz.loot.RaretesObjets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import fr.heraliz.loot.client.rendu.conteneurs.Conteneurs;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Côté client : écran des coffres, rendu animé, modèles des pièces, rareté dans l'infobulle, titre des conteneurs. */
public final class ClientLoot {
    private ClientLoot() {}

    @Mod.EventBusSubscriber(modid = HeralizLoot.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mods {
        private Mods() {}

        @SubscribeEvent
        public static void installer(FMLClientSetupEvent e) {
            e.enqueueWork(() -> MenuScreens.register(HeralizLoot.COFFRE_MENU.get(), CoffreEcran::new));
        }

        @SubscribeEvent
        public static void rendus(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(HeralizLoot.COFFRE_BE.get(), CoffreRendu::new);
        }

        /** Conteneurs T2-T6 refaits : leur modèle de blocs est remplacé par le maillage cuit (une fois, au chargement). */
        @SubscribeEvent
        public static void conteneurs(ModelEvent.ModifyBakingResult e) {
            int n = 0;
            for (String nom : Conteneurs.noms()) {
                ResourceLocation rl = new ResourceLocation(HeralizLoot.MODID, nom);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    ModelResourceLocation cle = new ModelResourceLocation(rl, "facing=" + d.getName());
                    BakedModel ancien = e.getModels().get(cle);
                    if (ancien != null) {
                        e.getModels().put(cle, new ModeleConteneur(ancien, nom, -d.toYRot(), false));
                        n++;
                    }
                }
                ModelResourceLocation objet = new ModelResourceLocation(rl, "inventory");
                BakedModel ancien = e.getModels().get(objet);
                if (ancien != null) e.getModels().put(objet, new ModeleConteneur(ancien, nom, -Direction.NORTH.toYRot(), true));
            }
            HeralizLoot.LOG.info("{} vues de conteneurs remplacées par les modèles HD", n);
        }
    }

    @Mod.EventBusSubscriber(modid = HeralizLoot.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Jeu {
        private Jeu() {}

        @SubscribeEvent
        public static void infobulleLoot(ItemTooltipEvent e) {
            // rareté inscrite sur l'objet (coffres T7) ou celle de sa sorte ; Commun n'est pas rappelé
            Rarete r = RaretesObjets.de(e.getItemStack());
            if (r == null) return;
            nomEnCouleur(e, r);
            if (r != Rarete.COMMUN || Rarete.de(e.getItemStack()) != null) {
                // 21/10 (choix du propriétaire) : plus d'étoile devant la rareté
                e.getToolTip().add(Math.min(1, e.getToolTip().size()), r.texte()
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(r.couleur))));
            }
        }

        /**
         * Nom de l'objet dans la couleur de sa rareté (21/10, choix du propriétaire : un viseur Légendaire s'affichait en
         * violet, couleur « épique » de Minecraft). Commun : blanc. Gardent leurs couleurs les noms qui en ont dans leur
         * texte (codes §, skins de couteaux colorés par leur rang).
         */
        private static void nomEnCouleur(ItemTooltipEvent e, Rarete r) {
            if (e.getToolTip().isEmpty()) return;
            // nom en couleur choisie par le staff (bons de HeraliZ Grades, étiquette HeralizCouleurLibre) : gardée
            if (e.getItemStack().hasTag() && e.getItemStack().getTag().getBoolean("HeralizCouleurLibre")) return;
            // seulement une rareté choisie pour cet objet (inscrite, ou de sa sorte), pas la rareté par défaut
            if (Rarete.de(e.getItemStack()) == null && RaretesObjets.propre(e.getItemStack()) == null) return;
            Component nom = e.getToolTip().get(0);
            if (nom.getString().indexOf('\u00A7') >= 0) return;
            int couleur = r == Rarete.COMMUN ? 0xFFFFFF : r.couleur;
            e.getToolTip().set(0, nom.copy().withStyle(st -> st.withColor(TextColor.fromRgb(couleur))));
        }

        /**
         * Inventaire d'un conteneur de loot : le serveur envoie le nom du bloc (« [T2] Caisse à outils », dans la langue du
         * joueur) ; l'étiquette de tiers est retirée ici, comme avant la traduction, et remplacée par le badge du palier
         * (« T3 », lu dans le nom du bloc, {@link EcranConteneur}). Seulement l'écran de coffre vanilla.
         */
        @SubscribeEvent
        public static void titreConteneur(ScreenEvent.Opening e) {
            if (e.getNewScreen() == null || e.getNewScreen().getClass() != ContainerScreen.class) return;
            ContainerScreen s = (ContainerScreen) e.getNewScreen();
            if (!(s.getTitle().getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith(PREFIXE_BLOC)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            String nom = s.getTitle().getString().replaceFirst("^\\[[^\\]]*\\]\\s*", "");
            int palier = ConteneurBlock.palier(tc.getKey().substring(PREFIXE_BLOC.length()));
            e.setNewScreen(new EcranConteneur(s.getMenu(), mc.player.getInventory(), Component.literal(nom), palier));
        }

        private static final String PREFIXE_BLOC = "block." + HeralizLoot.MODID + ".";
    }
}
