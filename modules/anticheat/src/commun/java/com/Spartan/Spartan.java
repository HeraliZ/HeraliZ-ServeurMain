package com.Spartan;

import com.Spartan.Networks.ModMessages;
import com.Spartan.pont.Parties;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Point d'entrée commun, volontairement réduit : le canal réseau et le chargement des deux parties.
 * <ul>
 *    <li>partie serveur ({@code com.Spartan.serveur.PartieServeur}, jar -serveur et -complet) : config, détections,
 *    anti-ESP, défis, contrôles d'intégrité, bans, alertes, commandes ;</li>
 *    <li>partie client ({@code com.Spartan.Client.PartieClient}, jar -client et -complet) : infos de connexion,
 *    réponses aux défis, contrôles d'intégrité demandés par le serveur.</li>
 * </ul>
 * Le jar des joueurs ne contient donc AUCUNE détection serveur ni aucun seuil : rien à étudier pour contourner.
 */
@Mod(Spartan.MOD_ID)
public class Spartan {
    public static final String MOD_ID = "spartan";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Spartan() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModMessages.register();
        Parties.serveur(MOD_ID, "com.Spartan.serveur.PartieServeur", modBus);
        Parties.client(MOD_ID, "com.Spartan.Client.PartieClient", modBus);
    }
}
