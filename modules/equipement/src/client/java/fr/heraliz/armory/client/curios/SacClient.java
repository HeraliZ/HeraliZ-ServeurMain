package fr.heraliz.armory.client.curios;

import fr.heraliz.armory.HeralizArmory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.fml.ModList;

/**
 * Sac à dos (HeraliZ Backpack, mod « backpacked ») vu de nos écrans, sans dépendance au réseau de ce mod : sa touche
 * (B par défaut) et la demande d'ouverture du sac porté (le même paquet que sa touche ; le serveur vérifie tout, comme
 * pour la touche). Par réflexion, préparée une fois ; sans le mod (ou s'il change), rien.
 */
final class SacClient {
    private SacClient() {
    }

    private static boolean pret, absent;
    private static Object reseau;
    private static Method envoyer;
    private static Constructor<?> message;
    private static KeyMapping touche;

    /** Touche du sac (null sans le mod). */
    static KeyMapping touche() {
        return preparer() ? touche : null;
    }

    /** Demande au serveur d'ouvrir le sac porté. */
    static void ouvrir() {
        if (!preparer()) return;
        try {
            envoyer.invoke(reseau, message.newInstance());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            absent = true;
            pret = false;
            HeralizArmory.LOGGER.warn("[armurerie] ouverture du sac depuis l'inventaire impossible ({})", ex.toString());
        }
    }

    private static boolean preparer() {
        if (pret) return true;
        if (absent) return false;
        try {
            if (!ModList.get().isLoaded("backpacked")) {
                absent = true;
                return false;
            }
            Method getPlay = Class.forName("com.mrcrayfish.backpacked.network.Network").getMethod("getPlay");
            reseau = getPlay.invoke(null);
            envoyer = getPlay.getReturnType().getMethod("sendToServer", Object.class);
            message = Class.forName("com.mrcrayfish.backpacked.network.message.MessageOpenBackpack").getConstructor();
            touche = (KeyMapping) Class.forName("com.mrcrayfish.backpacked.client.Keys").getField("KEY_BACKPACK").get(null);
            pret = reseau != null && touche != null;
            absent = !pret;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            absent = true;
            HeralizArmory.LOGGER.info("[armurerie] sac à dos introuvable depuis l'inventaire ({})", ex.toString());
        }
        return pret;
    }
}
