package fr.heraliz.emote.serveur;

import fr.heraliz.emote.EmoteRules;
import fr.heraliz.emote.pont.Partie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie serveur : emotes débloquées ({@link Deblocages}), armes bloquées après une emote ({@link SansArme}), /emote. */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        EmoteRules.DEBLOQUEE = Deblocages::a;
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CommandeEmote.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Deblocages.envoyer(p);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangeGameModeEvent e) -> {
            // le nouveau mode n'est posé qu'après l'événement : envoyé au tick suivant
            if (e.getEntity() instanceof ServerPlayer p) p.server.execute(() -> Deblocages.envoyer(p));
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> SansArme.oublier(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) SansArme.tick(p);
        });
    }
}
