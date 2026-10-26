package fr.heraliz.noyau.net;

import fr.heraliz.noyau.HeralizNoyau;
import fr.heraliz.noyau.api.EtatClient;
import fr.heraliz.noyau.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Canal réseau {@code heraliz_noyau:main}. Les paquets serveur -> joueur ne font que remplir {@link EtatClient} (aucune
 * classe client ici). Changer un paquet = monter {@link #VERSION} (jars joueurs et serveur de la même fabrication).
 */
public final class NoyauNet {
   private NoyauNet() {}

   public static final String VERSION = "2";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(HeralizNoyau.id("main"),
         () -> VERSION, VERSION::equals, VERSION::equals);

   private static boolean initialise;

   public static void init() {
      if (initialise) return;
      initialise = true;
      CANAL.messageBuilder(CombatS2C.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(CombatS2C::write).decoder(CombatS2C::read).consumerMainThread(CombatS2C::handle).add();
      CANAL.messageBuilder(DiagDemandeS2C.class, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(DiagDemandeS2C::write).decoder(DiagDemandeS2C::read).consumerMainThread(DiagDemandeS2C::handle).add();
      CANAL.messageBuilder(DiagRapportC2S.class, 2, NetworkDirection.PLAY_TO_SERVER)
            .encoder(DiagRapportC2S::write).decoder(DiagRapportC2S::read).consumerMainThread(DiagRapportC2S::handle).add();
   }

   public static void versServeur(Object paquet) {
      CANAL.sendToServer(paquet);
   }

   /** Demande reçue du serveur (/diag client) : la partie client y branche l'enregistrement. */
   public static final Crochet<DiagDemandeS2C> DIAG_DEMANDE = new Crochet<>();
   /** Rapport reçu d'un joueur : la partie serveur y branche la vérification et l'écriture (joueur, rapport). */
   public static final Crochet<java.util.Map.Entry<ServerPlayer, DiagRapportC2S>> DIAG_RAPPORT = new Crochet<>();

   /** Taille maximale d'un rapport de joueur (caractères). */
   public static final int RAPPORT_MAX = 60_000;

   /** Le serveur demande au jeu du joueur d'enregistrer {@code secondes} s ; {@code jeton} le retrouve à la réponse. */
   public record DiagDemandeS2C(int secondes, int jeton) {
      public void write(FriendlyByteBuf buf) {
         buf.writeVarInt(secondes);
         buf.writeVarInt(jeton);
      }

      public static DiagDemandeS2C read(FriendlyByteBuf buf) {
         return new DiagDemandeS2C(Math.max(5, Math.min(120, buf.readVarInt())), buf.readVarInt());
      }

      public static void handle(DiagDemandeS2C p, Supplier<NetworkEvent.Context> ctx) {
         DIAG_DEMANDE.accept(p);
         ctx.get().setPacketHandled(true);
      }
   }

   /** Rapport du jeu du joueur : une ligne de résumé et le texte complet (plafonnés). */
   public record DiagRapportC2S(int jeton, String resume, String texte) {
      public void write(FriendlyByteBuf buf) {
         buf.writeVarInt(jeton);
         buf.writeUtf(resume, 400);
         buf.writeUtf(texte, RAPPORT_MAX);
      }

      public static DiagRapportC2S read(FriendlyByteBuf buf) {
         return new DiagRapportC2S(buf.readVarInt(), buf.readUtf(400), buf.readUtf(RAPPORT_MAX));
      }

      public static void handle(DiagRapportC2S p, Supplier<NetworkEvent.Context> ctx) {
         ServerPlayer de = ctx.get().getSender();
         if (de != null) DIAG_RAPPORT.accept(java.util.Map.entry(de, p));
         ctx.get().setPacketHandled(true);
      }
   }

   public static void versJoueur(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }

   /** Indicateur « EN COMBAT » : secondes restantes (0 = masqué). Le client décompte seul. */
   public record CombatS2C(int secondes) {
      public void write(FriendlyByteBuf buf) {
         buf.writeVarInt(secondes);
      }

      public static CombatS2C read(FriendlyByteBuf buf) {
         return new CombatS2C(Math.max(0, Math.min(3600, buf.readVarInt())));
      }

      public static void handle(CombatS2C p, Supplier<NetworkEvent.Context> ctx) {
         EtatClient.combat(p.secondes());
         ctx.get().setPacketHandled(true);
      }
   }
}
