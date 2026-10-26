package fr.heraliz.loot.pont;

import java.util.function.Consumer;

/**
 * Point d'accroche simple : le code commun l'appelle, une partie (client ou serveur) y branche son traitement au
 * chargement ; sans branchement, il ne fait rien (valeur neutre). Sert surtout aux paquets réseau et aux tickers client
 * (docs/METHODE_CLIENT_SERVEUR.md §6).
 * <pre>
 *    // commun : le paquet ne connaît pas le code de dessin
 *    public static final Crochet&lt;Packet&gt; RECEPTION_CLIENT = new Crochet&lt;&gt;();
 *    void handle(Supplier&lt;NetworkEvent.Context&gt; ctx) {
 *       ctx.get().enqueueWork(() -&gt; RECEPTION_CLIENT.accept(this));
 *       ctx.get().setPacketHandled(true);
 *    }
 *    // client (PartieClient.init)
 *    Vfx.RECEPTION_CLIENT.brancher(VfxManager::receive);
 * </pre>
 */
public final class Crochet<T> implements Consumer<T> {
   private volatile Consumer<T> cible;

   public void brancher(Consumer<T> traitement) {
      this.cible = traitement;
   }

   public boolean branche() {
      return cible != null;
   }

   @Override
   public void accept(T t) {
      Consumer<T> c = cible;
      if (c != null) c.accept(t);
   }
}
