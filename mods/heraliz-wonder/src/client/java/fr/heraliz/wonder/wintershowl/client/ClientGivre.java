package fr.heraliz.wonder.wintershowl.client;

import com.mojang.brigadier.Command;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Cote client du Winter's Howl : effets ({@link FxGivre}), couche de glace sur les creatures givrees ({@link CoucheGivre},
 * ajoutee a tous les rendus de creatures), branchements TaCZ ({@link TaczGivreClient}). Commande client
 * {@code /givrefx} : occupation des reserves et cout du rendu (journal).
 */
public final class ClientGivre {
    private ClientGivre() {
    }

    public static void init(IEventBus mod, IEventBus forge) {
        mod.addListener(ClientGivre::couches);
        forge.addListener(FxGivre::tick);
        forge.addListener(FxGivre::rendu);
        forge.addListener(EventPriority.HIGH, FxGivre::cacher);
        forge.addListener(EventPriority.LOWEST, FxGivre::vivantAvant);
        forge.addListener(EventPriority.LOWEST, FxGivre::vivantApres);
        forge.addListener(FxGivre::dechargement);
        forge.addListener(FxGivre::ecran);
        forge.addListener(ClientGivre::commandes);
        if (ModList.get().isLoaded("tacz")) TaczGivreClient.brancher(forge);
    }

    /** Couche de glace ajoutee a chaque rendu de creature (une fois, au chargement des rendus). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void couches(EntityRenderersEvent.AddLayers e) {
        int n = 0;
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            EntityRenderer<?> r;
            try {
                r = e.getRenderer((EntityType) type);
            } catch (ClassCastException pasUneCreature) {
                continue;
            }
            if (r instanceof LivingEntityRenderer lr) {
                lr.addLayer(new CoucheGivre<LivingEntity, EntityModel<LivingEntity>>(lr));
                n++;
            }
        }
        HeralizWonder.LOG.info("[wonder] couche de glace du Winter's Howl ajoutee a {} rendus de creatures", n);
    }

    private static void commandes(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("givrefx").executes(c -> {
            String r = FxGivre.stats();
            HeralizWonder.LOG.info("[wonder] fx {}", r);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Effets du givre : voir le journal"), true);
            return Command.SINGLE_SUCCESS;
        }));
    }
}
