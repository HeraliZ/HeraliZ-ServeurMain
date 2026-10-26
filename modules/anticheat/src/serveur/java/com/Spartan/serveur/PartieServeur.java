package com.Spartan.serveur;

import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.Spartan.SpartanCommand;
import com.Spartan.ban.BanCommands;
import com.Spartan.check.CheckManager;
import com.Spartan.check.block.FastBreakCheck;
import com.Spartan.check.block.NukerCheck;
import com.Spartan.check.combat.AutoClickerCheck;
import com.Spartan.check.combat.KillAuraCheck;
import com.Spartan.check.combat.ReachCheck;
import com.Spartan.check.movement.BadPacketsCheck;
import com.Spartan.check.movement.FlyCheck;
import com.Spartan.check.movement.IllegalEffectCheck;
import com.Spartan.check.movement.NoFallCheck;
import com.Spartan.check.movement.NoSlowCheck;
import com.Spartan.check.movement.SpeedCheck;
import com.Spartan.check.movement.TimerCheck;
import com.Spartan.check.movement.VelocityCheck;
import com.Spartan.check.movement.VerticalCheck;
import com.Spartan.config.SpartanConfig;
import com.Spartan.esp.Allies;
import com.Spartan.pont.Partie;
import com.Spartan.serveur.compat.HeralizCompat;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;

/**
 * Partie serveur de Spartan (jar -serveur et -complet) : tout ce qui juge et sanctionne. Rien de tout cela n'est dans
 * le jar des joueurs. Les écouteurs du jeu (SpartanEvents) s'inscrivent d'eux-mêmes (@Mod.EventBusSubscriber).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // Config COMMON : Forge ne l'envoie jamais aux clients, contrairement au type SERVER qui est synchronisé à
        // chaque connexion et exposerait les webhooks Discord et les seuils de détection.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpartanConfig.SPEC);

        // Détections côté serveur : basées sur ce que le joueur FAIT, pas sur ce que son client déclare.
        CheckManager.register(new FlyCheck());
        CheckManager.register(new SpeedCheck());
        CheckManager.register(new VerticalCheck());
        CheckManager.register(new NoFallCheck());
        CheckManager.register(new NoSlowCheck());
        CheckManager.register(new TimerCheck());
        CheckManager.register(new VelocityCheck());
        CheckManager.register(new BadPacketsCheck());
        CheckManager.register(new ReachCheck());
        CheckManager.register(new KillAuraCheck());
        CheckManager.register(new AutoClickerCheck());
        CheckManager.register(new NukerCheck());
        CheckManager.register(new FastBreakCheck());
        CheckManager.register(new IllegalEffectCheck());

        ModMessages.RECEPTION_SERVEUR.brancher(ReceptionServeur::recevoir);
        busMod.addListener(PartieServeur::imc);
        busMod.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(HeralizCompat::brancher));
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::commandes);
    }

    /**
     * Messages d'autres mods. « ally_predicate » : un mod de clan fournit son test d'alliance
     * (BiPredicate&lt;ServerPlayer, ServerPlayer&gt;) ; l'anti-ESP ne cache jamais des alliés.
     */
    @SuppressWarnings("unchecked")
    private static void imc(InterModProcessEvent event) {
        event.getIMCStream("ally_predicate"::equals).forEach(msg -> {
            Object o = msg.messageSupplier().get();
            if (o instanceof BiPredicate<?, ?> p) {
                Allies.register((BiPredicate<ServerPlayer, ServerPlayer>) p);
            } else {
                Spartan.LOGGER.warn("[Spartan] Message IMC ally_predicate de {} ignoré : BiPredicate<ServerPlayer, ServerPlayer> attendu.",
                        msg.senderModId());
            }
        });
    }

    private static void commandes(RegisterCommandsEvent event) {
        SpartanCommand.register(event.getDispatcher());
        BanCommands.register(event.getDispatcher());
    }
}
