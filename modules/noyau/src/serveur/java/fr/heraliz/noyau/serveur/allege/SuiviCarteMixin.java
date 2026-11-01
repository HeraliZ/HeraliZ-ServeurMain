package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.SuiviParTroncon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Point 1 de l'étape 2 du chantier « Minecraft allégé » (01/11) : voir {@link SuiviParTroncon}.
 * <ul>
 *   <li>{@code move(joueur)} (chaque paquet de mouvement) : la boucle sur toutes les entités suivies est faite par l'index
 *   (entités proches ou déjà vues) ; si l'index n'est pas prêt, la boucle de Minecraft.</li>
 *   <li>{@code addEntity} / {@code removeEntity} : l'index suit les entrées du suivi.</li>
 *   <li>{@code tick()} : les joueurs qui ont changé de section ne sont plus jugés par toutes les entités, seulement par
 *   les leurs (le test {@code liste.isEmpty()} de Minecraft répond « vide » quand c'est fait).</li>
 * </ul>
 */
@Mixin(ChunkMap.class)
public abstract class SuiviCarteMixin implements SuiviParTroncon.AvecIndex {
   @Unique
   private SuiviParTroncon heraliz$index;
   @Unique
   private boolean heraliz$indexEssaye;
   /** Le joueur de {@code move}, du début de la méthode à sa boucle (sinon : boucle de Minecraft). */
   @Unique
   private ServerPlayer heraliz$joueurBouge;

   /** L'index de cette carte, ou null si l'accès à la carte manque (alors : Minecraft partout). */
   @Override
   public SuiviParTroncon heraliz$index() {
      if (!heraliz$indexEssaye) {
         heraliz$indexEssaye = true;
         heraliz$index = SuiviParTroncon.pour((ChunkMap) (Object) this);
      }
      return heraliz$index;
   }

   @Inject(method = "move", at = @At("HEAD"))
   private void heraliz$debutMouvement(ServerPlayer joueur, CallbackInfo ci) {
      heraliz$joueurBouge = joueur;
   }

   @Redirect(method = "move", at = @At(value = "INVOKE",
         target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;", ordinal = 0,
         remap = false))
   private ObjectCollection<?> heraliz$entitesAJuger(Int2ObjectMap<?> suivis) {
      ServerPlayer joueur = heraliz$joueurBouge;
      heraliz$joueurBouge = null;
      SuiviParTroncon index = joueur != null ? heraliz$index() : null;
      if (index != null && index.bouger(suivis, joueur)) {
         return ObjectLists.emptyList();
      }
      return suivis.values();
   }

   @Inject(method = "addEntity", at = @At("RETURN"))
   private void heraliz$entiteSuivie(Entity entite, CallbackInfo ci) {
      SuiviParTroncon index = heraliz$index();
      if (index != null) {
         index.ajoute(entite);
      }
   }

   @Inject(method = "removeEntity", at = @At("RETURN"))
   private void heraliz$entitePlusSuivie(Entity entite, CallbackInfo ci) {
      SuiviParTroncon index = heraliz$index();
      if (index != null) {
         index.retire(entite);
      }
   }

   @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0, remap = false))
   private boolean heraliz$joueursChanges(List<?> joueurs) {
      SuiviParTroncon index = heraliz$index();
      if (index != null && index.joueursChanges(joueurs)) {
         return true;
      }
      return joueurs.isEmpty();
   }
}
