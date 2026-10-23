package fr.heraliz.loot.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.OuvertureAccroupi;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * Fouiller avec un objet en main (0.9.16, demande du propriétaire). Une arme TaCZ en main, le jeu du joueur annule tout
 * clic droit (TaCZ : {@code ClientPreventGunClick}, le clic droit sert à viser), et les armes de mêlée prennent le clic
 * droit pour leur coup puissant : on ne pouvait rien fouiller sans ranger son arme. Ici, objet en main et viseur sur une
 * chose qu'on fouille (conteneur de loot T1 à T7 et salles à carte, coffre, caisse, meuble de rangement : voir
 * {@link OuvertureAccroupi#conteneur} ; cadavre ; caisse de largage), le clic droit OUVRE au lieu de viser ou de frapper :
 * l'appui est pris avant tout le monde ({@code MouseButton.Pre} annulé : ni visée de TaCZ, ni coup de mêlée) et
 * l'interaction est envoyée comme le fait Minecraft (même paquet, même portée, mêmes règles du serveur, qui décide
 * toujours : claims, joueur à terre, tutoriel...). Le tir reste au clic gauche. Touche « Utiliser » mise sur le clavier :
 * le refus de TaCZ est levé (secours, sur {@code InteractionKeyMappingTriggered}).
 * <p>Accroupi, la règle de {@link OuvertureAccroupi} s'applique comme avant (grenades : lancer doux, blocs : poser).</p>
 */
@Mod.EventBusSubscriber(modid = HeralizLoot.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FouilleObjetEnMain {
    /** Entités qu'on fouille par clic droit : cadavres (HeraliZ Utils), caisses de largage (Airstrike). */
    private static final Set<ResourceLocation> ENTITES = Set.of(
            new ResourceLocation("heraliz_corps", "corpse"),
            new ResourceLocation("airstrike", "supply_crate"));

    private FouilleObjetEnMain() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void souris(InputEvent.MouseButton.Pre e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) return;
        if (ouvrir(mc)) e.setCanceled(true);
    }

    /** Touche « Utiliser » au clavier : TaCZ a annulé le clic, on le rend quand on vise une chose à fouiller. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void touche(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || !e.isCanceled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) return;
        if (cible(mc) != null) {
            e.setCanceled(false);
            e.setSwingHand(true);
        }
    }

    /** Ouvre ce qui est visé ; vrai si le clic a servi (le reste du jeu ne le voit plus). */
    private static boolean ouvrir(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.screen != null || mc.getOverlay() != null) return false;
        if (p.isSpectator() || p.getMainHandItem().isEmpty()) return false;
        HitResult h = cible(mc);
        if (h == null) return false;
        for (InteractionHand main : InteractionHand.values()) {
            InteractionResult r;
            if (h instanceof BlockHitResult b) {
                r = mc.gameMode.useItemOn(p, main, b);
            } else {
                EntityHitResult eh = (EntityHitResult) h;
                r = mc.gameMode.interactAt(p, eh.getEntity(), eh, main);
                if (!r.consumesAction()) r = mc.gameMode.interact(p, eh.getEntity(), main);
            }
            if (r.consumesAction()) {
                if (r.shouldSwing()) p.swing(main);
                return true;
            }
        }
        // rien ne s'est ouvert (refus du jeu) : le clic est quand même pris, pour ne pas viser ni frapper la caisse
        return true;
    }

    /** Ce que vise le joueur, si c'est une chose à fouiller ; sinon null. */
    private static HitResult cible(Minecraft mc) {
        HitResult h = mc.hitResult;
        if (h == null || mc.level == null) return null;
        if (h.getType() == HitResult.Type.BLOCK && h instanceof BlockHitResult b) {
            return OuvertureAccroupi.fouillable(mc.level, b.getBlockPos()) ? b : null;
        }
        if (h.getType() == HitResult.Type.ENTITY && h instanceof EntityHitResult eh) {
            Entity en = eh.getEntity();
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(en.getType());
            return id != null && ENTITES.contains(id) ? eh : null;
        }
        return null;
    }
}
