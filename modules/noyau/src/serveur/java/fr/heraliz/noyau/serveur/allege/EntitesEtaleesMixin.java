package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.SauvegardesEtalees;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Sauvegarde automatique des entités étalée (19/11, audit des lenteurs, point C2 ; voir {@link SauvegardesEtalees}) :
 * {@code autoSave} (sauvegarde automatique seulement) prend la liste des tronçons à écrire comme Minecraft
 * ({@code getAllChunksToSave}), puis la traite par tranches aux ticks suivants, chaque tronçon exactement comme Minecraft
 * (caché : déchargé ; sinon ses entités écrites). {@code saveAll} (flush, arrêt, fermeture du monde) écrit tout et efface
 * la tranche en cours. Réglage {@code [perf] entites_etalees} coupé, autre sauvegarde ou cible absente (require = 0) :
 * comme Minecraft.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class EntitesEtaleesMixin implements SauvegardesEtalees.Entites {
   @Shadow
   @Final
   private Long2ObjectMap<Visibility> chunkVisibility;

   @Shadow
   protected abstract LongSet getAllChunksToSave();

   @Shadow
   protected abstract boolean processChunkUnload(long troncon);

   @Shadow
   protected abstract boolean storeChunkSections(long troncon, Consumer<?> apres);

   @Unique
   private LongArrayList heraliz$tour;
   @Unique
   private int heraliz$position;

   @Inject(method = "autoSave", at = @At("HEAD"), cancellable = true)
   private void heraliz$etaler(CallbackInfo ci) {
      if (heraliz$tour != null) {
         heraliz$tour.clear();   // nouveau tour (ou sauvegarde de Minecraft) : tout est repris
         heraliz$position = 0;
      }
      if (!SauvegardesEtalees.entitesEtalables()) {
         return;
      }
      if (heraliz$tour == null) {
         heraliz$tour = new LongArrayList();
      }
      heraliz$tour.addAll(getAllChunksToSave());
      if (!heraliz$tour.isEmpty()) {
         SauvegardesEtalees.entitesAEtaler(this, heraliz$tour.size());
      }
      ci.cancel();
   }

   @Inject(method = "saveAll", at = @At("HEAD"))
   private void heraliz$toutDeSuite(CallbackInfo ci) {
      if (heraliz$tour != null) {
         heraliz$tour.clear();
         heraliz$position = 0;
      }
   }

   @Override
   public int heraliz$tranche(int max) {
      LongArrayList t = heraliz$tour;
      if (t == null) {
         return 0;
      }
      int n = 0;
      while (n < max && heraliz$position < t.size()) {
         long troncon = t.getLong(heraliz$position++);
         if (chunkVisibility.get(troncon) == Visibility.HIDDEN) {
            processChunkUnload(troncon);
         } else {
            storeChunkSections(troncon, SauvegardesEtalees.RIEN);
         }
         n++;
      }
      int reste = t.size() - heraliz$position;
      if (reste <= 0) {
         t.clear();
         heraliz$position = 0;
      }
      return reste;
   }
}
