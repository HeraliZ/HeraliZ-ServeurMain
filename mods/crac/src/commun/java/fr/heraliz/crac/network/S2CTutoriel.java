package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * L'étape du tutoriel des bases à afficher chez le joueur (carte en haut à gauche, {@code bases.HudTutoriel} du jar
 * joueur). Le serveur décide de tout (étape, avancée, textes) ; le joueur ne fait que dessiner.
 *
 * @param visible  faux : la carte disparaît (tutoriel fini, ou joueur hors du monde de l'étape)
 * @param numero   numéro de l'étape (1 à {@code total})
 * @param total    nombre d'étapes du parcours de ce joueur
 * @param fait     l'étape est terminée (moment « Étape terminée » avant la suivante)
 * @param titre    titre court (clé de langue avec le français en secours)
 * @param texte    une ou deux lignes de consigne
 * @param touches  touches à montrer : jeton de touche ({@code attaque}, {@code utiliser}, {@code tourner},
 *                 {@code inventaire}) ou commande commençant par « / »
 * @param legendes clé de langue de la légende de chaque touche (vide : sans légende)
 * @param compte   avancée d'une étape à compter (murs posés...), 0 sinon
 * @param objectif ce qu'il faut atteindre ; 0 : pas de compteur
 * @param lecture  ticks de lecture d'une étape qui avance seule (barre qui avance chez le joueur), 0 sinon
 */
public record S2CTutoriel(boolean visible, int numero, int total, boolean fait, Component titre, Component texte,
                          List<String> touches, List<String> legendes, int compte, int objectif, int lecture) {
    static final int MAX_TOUCHES = 6;
    static final int MAX_JETON = 64;

    public static S2CTutoriel cache() {
        return new S2CTutoriel(false, 0, 0, false, Component.empty(), Component.empty(), List.of(), List.of(), 0, 0, 0);
    }

    public static void encode(S2CTutoriel msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.visible);
        buf.writeVarInt(msg.numero);
        buf.writeVarInt(msg.total);
        buf.writeBoolean(msg.fait);
        buf.writeComponent(msg.titre);
        buf.writeComponent(msg.texte);
        int n = Math.min(MAX_TOUCHES, msg.touches.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(msg.touches.get(i), MAX_JETON);
            buf.writeUtf(i < msg.legendes.size() ? msg.legendes.get(i) : "", MAX_JETON);
        }
        buf.writeVarInt(msg.compte);
        buf.writeVarInt(msg.objectif);
        buf.writeVarInt(msg.lecture);
    }

    public static S2CTutoriel decode(FriendlyByteBuf buf) {
        boolean visible = buf.readBoolean();
        int numero = buf.readVarInt();
        int total = buf.readVarInt();
        boolean fait = buf.readBoolean();
        Component titre = buf.readComponent();
        Component texte = buf.readComponent();
        int n = PacketLimits.readCount(buf, MAX_TOUCHES, 2);
        List<String> touches = new ArrayList<>(n);
        List<String> legendes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            touches.add(buf.readUtf(MAX_JETON));
            legendes.add(buf.readUtf(MAX_JETON));
        }
        return new S2CTutoriel(visible, numero, total, fait, titre, texte, touches, legendes, buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt());
    }

    public static void handle(S2CTutoriel msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
