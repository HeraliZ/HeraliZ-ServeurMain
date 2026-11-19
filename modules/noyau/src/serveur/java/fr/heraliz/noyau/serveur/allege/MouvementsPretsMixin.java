package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouvements vers un tronçon pas prêt refusés (19/11, audit des lenteurs, point C4 ; comme le réglage
 * {@code prevent-moving-into-unloaded-chunks} de Paper) : un joueur qui avance plus vite que la lecture des tronçons (vol
 * rapide du staff en créatif ou spectateur, effets de vitesse) entrait dans un tronçon encore en lecture ; son déplacement
 * (collisions, puis Forge qui charge le tronçon de la nouvelle position) le faisait attendre par tout le serveur (gels sans
 * commande de 0,6 à 2,5 s du 18/11, cause probable). Ici, au début de {@code handleMovePlayer} sur le fil du serveur : si
 * la boîte du joueur étirée jusqu'à sa nouvelle position (et un bloc autour, la portée des collisions de Minecraft) touche
 * un tronçon pas encore complet ({@code getChunkNow}), le paquet est refusé et le joueur renvoyé à sa place (comme pour
 * « moved too quickly » : il réessaie dès que le tronçon est prêt, une fraction de seconde plus tard). Paquet sans
 * position, joueur monté, téléportation en attente, premier tick, changement de monde : Minecraft. Réglage :
 * {@code [perf] mouvements_troncons_prets}. Aucun objet créé.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MouvementsPretsMixin {
   @Shadow
   public ServerPlayer player;
   @Shadow
   private int tickCount;
   @Shadow
   private Vec3 awaitingPositionFromClient;

   @Shadow
   public abstract void teleport(double x, double y, double z, float lacet, float tangage);

   @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
   private void heraliz$versTronconPret(ServerboundMovePlayerPacket paquet, CallbackInfo ci) {
      ServerPlayer p = this.player;
      if (!NoyauConfig.mouvementsTronconsPrets || p == null || !paquet.hasPosition() || !p.server.isSameThread()) {
         return;   // hors du fil du serveur : Minecraft renvoie d'abord le paquet sur ce fil (et on repasse ici)
      }
      if (this.tickCount == 0 || this.awaitingPositionFromClient != null || p.isPassenger() || p.wonGame || p.isChangingDimension()) {
         return;
      }
      double x0 = p.getX(), z0 = p.getZ();
      double x = paquet.getX(x0), z = paquet.getZ(z0);
      if (!(Math.abs(x) < 3.0E7) || !(Math.abs(z) < 3.0E7) || x == x0 && z == z0) {
         return;   // valeurs folles (Minecraft déconnecte), ou rien d'horizontal
      }
      AABB b = p.getBoundingBox();
      double dx = x - x0, dz = z - z0;
      int cx0 = Mth.floor(Math.min(b.minX, b.minX + dx) - 1.0) >> 4, cx1 = Mth.floor(Math.max(b.maxX, b.maxX + dx) + 1.0) >> 4;
      int cz0 = Mth.floor(Math.min(b.minZ, b.minZ + dz) - 1.0) >> 4, cz1 = Mth.floor(Math.max(b.maxZ, b.maxZ + dz) + 1.0) >> 4;
      if ((cx1 - cx0 + 1) * (cz1 - cz0 + 1) > 64) {
         return;   // saut énorme (téléportation par le jeu du joueur) : Minecraft juge (« moved too quickly »)
      }
      ServerChunkCache c = p.serverLevel().getChunkSource();
      for (int cx = cx0; cx <= cx1; cx++) {
         for (int cz = cz0; cz <= cz1; cz++) {
            if (c.getChunkNow(cx, cz) == null) {
               Allege.mouvementsRefuses++;
               this.teleport(x0, p.getY(), z0, p.getYRot(), p.getXRot());
               ci.cancel();
               return;
            }
         }
      }
   }
}
