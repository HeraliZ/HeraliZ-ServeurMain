package fr.heraliz.armory.network;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ReglagesArmurerie;
import fr.heraliz.armory.pont.Crochet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau du mod : le client demande de baisser / relever les lunettes de vision nocturne ; le serveur envoie au
 * porteur de la tenue de plongee la reserve de sa bouteille (0.9.0, protocole 2), et a chaque joueur les reglages dont son
 * jeu a besoin (02/11, protocole 3 : ReglagesJoueur) ; cible d'essai (03/11, protocole 4 : PaquetsCible).
 */
public final class ArmoryNetwork {
    private ArmoryNetwork() {}

    private static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeralizArmory.id("main"), () -> VERSION, VERSION::equals,
            VERSION::equals);

    /** Message vide : "bascule mes lunettes". */
    public record ToggleNvg() {}

    /** Reserve de la bouteille de plongee (ticks) et capacite. */
    public record TankSync(int tank, int max) {}

    /**
     * Reglages du serveur dont le jeu du joueur a besoin (audit du 02/11, point 11) : bonus de nage des palmes, bouteille
     * d'air et sa recharge (secondes), slots Curios bloques par une combinaison. Recopies dans ReglagesArmurerie.
     */
    public record ReglagesJoueur(double nage, int bouteille, int recharge, List<String> slots) {
        public static ReglagesJoueur actuels() {
            return new ReglagesJoueur(ReglagesArmurerie.nage(), ReglagesArmurerie.bouteilleSecondes(), ReglagesArmurerie.rechargeSecondes(),
                    List.copyOf(ReglagesArmurerie.slotsBloques()));
        }
    }

    /** « Bascule mes lunettes » recu par le serveur : branche par la partie serveur (nvg.NightVisionServeur.toggle). */
    public static final Crochet<ServerPlayer> RECEPTION_SERVEUR = new Crochet<>();
    /** Reserve de la bouteille recue par le client : branche par la partie client (client.DivingClient.sync). */
    public static final Crochet<TankSync> RECEPTION_CLIENT = new Crochet<>();

    public static void register() {
        CHANNEL.messageBuilder(ToggleNvg.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, buf) -> {})
                .decoder(buf -> new ToggleNvg())
                .consumerMainThread((m, ctx) -> {
                    var player = ctx.get().getSender();
                    if (player != null) RECEPTION_SERVEUR.accept(player);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(TankSync.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, buf) -> {
                    buf.writeVarInt(m.tank());
                    buf.writeVarInt(m.max());
                })
                .decoder(buf -> new TankSync(buf.readVarInt(), buf.readVarInt()))
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_CLIENT.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(ReglagesJoueur.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, buf) -> {
                    buf.writeDouble(m.nage());
                    buf.writeVarInt(m.bouteille());
                    buf.writeVarInt(m.recharge());
                    List<String> slots = m.slots().size() > ReglagesArmurerie.MAX_SLOTS ? m.slots().subList(0, ReglagesArmurerie.MAX_SLOTS) : m.slots();
                    buf.writeVarInt(slots.size());
                    for (String s : slots) {
                        buf.writeUtf(s.length() > ReglagesArmurerie.MAX_LETTRES ? s.substring(0, ReglagesArmurerie.MAX_LETTRES) : s,
                                ReglagesArmurerie.MAX_LETTRES);
                    }
                })
                .decoder(buf -> {
                    double nage = buf.readDouble();
                    int bouteille = buf.readVarInt(), recharge = buf.readVarInt();
                    int n = Math.min(ReglagesArmurerie.MAX_SLOTS, buf.readVarInt());
                    List<String> slots = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) slots.add(buf.readUtf(ReglagesArmurerie.MAX_LETTRES));
                    return new ReglagesJoueur(nage, bouteille, recharge, slots);
                })
                .consumerMainThread((m, ctx) -> {
                    ReglagesArmurerie.definirJoueur(m.nage(), m.bouteille(), m.recharge(), m.slots());
                    ctx.get().setPacketHandled(true);
                })
                .add();
        // 03/11 : cible d'essai (numeros 3, 4, 5)
        PaquetsCible.enregistrer(CHANNEL);
    }
}
