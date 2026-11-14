package fr.heraliz.loot.client;

import fr.heraliz.loot.ConteneurBlock;
import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.Rarete;
import fr.heraliz.loot.RaretesObjets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraftforge.eventbus.api.EventPriority;
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

        /**
         * Nom et rareté de tout objet. 14/11 (retour du propriétaire : « il y a écrit 3 fois le mot epic ou épique sur le
         * même objet ; il faut juste une fois le petit encadré avec écrit EPIC et c'est tout ») : la rareté n'est plus
         * qu'UN badge ({@link Rarete#badge}), juste sous le nom ; plus de ligne « Épique » écrite ici, et les autres mods
         * n'en écrivent plus (l'Armurerie mettait sa propre ligne « badge + Épique » sous la nôtre). Appelé en dernier
         * (LOWEST) : les lignes des autres mods sont déjà là, le badge se pose toujours juste sous le nom, et un badge déjà
         * posé par un autre mod (camos, stickers…, par exemple si le staff choisit leur rareté avec /rarete) est retiré :
         * il n'y en a jamais deux.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void infobulleLoot(ItemTooltipEvent e) {
            List<Component> l = e.getToolTip();
            // nom dans la couleur de sa rareté, même règle que le nom affiché au-dessus de la barre d'objets (NomsRarete ;
            // 21/10 : un viseur Légendaire s'affichait en violet ; 11/11 : aussi les noms à codes § des packs d'armes et
            // ceux qui ont leur propre couleur, plus jamais le rose de Minecraft)
            if (!l.isEmpty()) l.set(0, NomsRarete.nom(e.getItemStack(), l.get(0)));
            // rareté choisie pour l'objet : inscrite sur l'objet (coffres T7), sinon celle de sa sorte (raretes.json, /rarete).
            // 14/11 (retour du propriétaire : « sur beaucoup d'accessoires il n'y a pas écrit de rareté, exemple Bipod V9
            // Grip ») : Commun aussi. Avant, Commun n'était jamais rappelé : 47 accessoires, 5 armes, 2 munitions, 6 armes
            // de mêlée et 9 composants n'avaient aucune rareté dans l'infobulle. Un objet sans rareté choisie (seulement celle
            // par défaut : blocs, objets de Minecraft…) n'en montre toujours pas.
            Rarete r = RaretesObjets.choisie(e.getItemStack());
            if (r == null) return;
            for (int i = l.size() - 1; i >= 1; i--) {
                if (Rarete.estBadge(l.get(i))) l.remove(i);
            }
            l.add(Math.min(1, l.size()), r.badge());
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!(s.getTitle().getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith(PREFIXE_BLOC)) {
                // autre coffre de base (planque, coffre VIP, coffre du clan…) : même écran, titre lisible, sans badge
                e.setNewScreen(new EcranConteneur(s.getMenu(), mc.player.getInventory(), s.getTitle(), 0));
                return;
            }
            String nom = s.getTitle().getString().replaceFirst("^\\[[^\\]]*\\]\\s*", "");
            int palier = ConteneurBlock.palier(tc.getKey().substring(PREFIXE_BLOC.length()));
            e.setNewScreen(new EcranConteneur(s.getMenu(), mc.player.getInventory(), Component.literal(nom), palier));
        }

        private static final String PREFIXE_BLOC = "block." + HeralizLoot.MODID + ".";
    }
}
