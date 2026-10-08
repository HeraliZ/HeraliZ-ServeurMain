package fr.heraliz.wonder.pont;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Point d'entrée d'une partie « serveur » ou « client » d'un mod (docs/METHODE_CLIENT_SERVEUR.md §4). La classe qui
 * l'implémente vit dans src/serveur ou src/client, a un constructeur sans argument, et n'est connue du code commun que par
 * son nom (une chaîne passée à {@link Parties}) : le compilateur ne peut donc pas lier commun à serveur ou à client.
 * <p>
 * {@link #init} est appelée PENDANT le constructeur du @Mod, avant les événements d'enregistrement : c'est le moment de
 * brancher les usines ({@link UsineEntite#serveur}, {@link UsineBlocEntite#serveur}, {@link Usine#serveur}), les crochets
 * réseau, et d'inscrire les écouteurs sur le bus du mod (busMod) ou sur MinecraftForge.EVENT_BUS.
 */
public interface Partie {
   void init(IEventBus busMod);
}
