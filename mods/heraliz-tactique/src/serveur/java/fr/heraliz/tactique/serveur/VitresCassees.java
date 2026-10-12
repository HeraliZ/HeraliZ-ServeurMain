package fr.heraliz.tactique.serveur;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Vitres cassées par les balles, à remettre (VitresServeur) : une liste par dimension, gardée dans les données du monde
 * ({@code <monde>/<dimension>/data/heraliz_vitres.dat}) pour survivre aux redémarrages. Rien n'est écrit dans les blocs
 * de la carte : seule la vitre elle-même est reposée, à son heure.
 * <p>
 * Une entrée : position, état complet du bloc juste avant la casse (vitres teintées, vitres fines et leurs connexions,
 * orientation...), heure de la casse (temps réel, en millisecondes).
 */
final class VitresCassees extends SavedData {
   static final String NOM = "heraliz_vitres";

   /** Une vitre à remettre. */
   record Vitre(BlockState etat, long casseeA) {
   }

   /** Position (BlockPos.asLong) -> vitre ; dans l'ordre des casses. */
   private final Map<Long, Vitre> vitres = new LinkedHashMap<>();

   static VitresCassees de(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(VitresCassees::lire, VitresCassees::new, NOM);
   }

   boolean vide() {
      return this.vitres.isEmpty();
   }

   int taille() {
      return this.vitres.size();
   }

   /** Les vitres à remettre (modifiable : retirer une entrée remise, puis setDirty). */
   Map<Long, Vitre> vitres() {
      return this.vitres;
   }

   /** Note une vitre cassée (une vitre déjà notée au même endroit est remplacée). */
   void noter(BlockPos pos, BlockState etat, long casseeA) {
      this.vitres.put(pos.asLong(), new Vitre(etat, casseeA));
      this.setDirty();
   }

   private static VitresCassees lire(CompoundTag tag) {
      VitresCassees d = new VitresCassees();
      ListTag liste = tag.getList("vitres", Tag.TAG_COMPOUND);
      for (int i = 0; i < liste.size(); i++) {
         CompoundTag v = liste.getCompound(i);
         // bloc d'un mod retiré : état « air », l'entrée est jetée à son heure (VitresServeur.passe)
         BlockState etat = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), v.getCompound("etat"));
         d.vitres.put(v.getLong("pos"), new Vitre(etat, v.getLong("cassee")));
      }
      return d;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag liste = new ListTag();
      for (Map.Entry<Long, Vitre> e : this.vitres.entrySet()) {
         CompoundTag v = new CompoundTag();
         v.putLong("pos", e.getKey());
         v.put("etat", NbtUtils.writeBlockState(e.getValue().etat()));
         v.putLong("cassee", e.getValue().casseeA());
         liste.add(v);
      }
      tag.put("vitres", liste);
      return tag;
   }
}
