package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.ReglagesGardes;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.network.ConfigSync;
import net.minecraftforge.network.HandshakeMessages;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Envoi des réglages SERVER aux joueurs à la connexion (audit du 02/11, point 11, §3 de split) : Forge construit la
 * liste de tous les fichiers SERVER ({@code ConfigSync.syncConfigs}, une fois par connexion) ; ceux de
 * {@link ReglagesGardes} en sont retirés, les autres partent comme avant. Facultatif (config sans « required ») : s'il ne
 * s'applique pas, tout part comme avant et le journal du démarrage le dit ({@code ReglagesGardes.verifier}).
 */
@Mixin(value = ConfigSync.class, remap = false)
public abstract class ReglagesGardesMixin {
   @Inject(method = "syncConfigs", at = @At("RETURN"), cancellable = true)
   private void heraliz$garderAuServeur(boolean local, CallbackInfoReturnable<List<Pair<String, HandshakeMessages.S2CConfigData>>> cir) {
      ReglagesGardes.vu();
      List<Pair<String, HandshakeMessages.S2CConfigData>> envoi = cir.getReturnValue();
      if (envoi == null || envoi.isEmpty()) return;
      List<Pair<String, HandshakeMessages.S2CConfigData>> reste = new ArrayList<>(envoi.size());
      for (Pair<String, HandshakeMessages.S2CConfigData> p : envoi) {
         if (p == null || p.getRight() == null || !ReglagesGardes.garde(p.getRight().getFileName())) reste.add(p);
      }
      if (reste.size() != envoi.size()) cir.setReturnValue(reste);
   }
}
