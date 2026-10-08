package fr.heralisz.tags.client;

/** Langue du jeu cote client (code d'origine de util.Langue.Client) : francais si le client est en fr_*. */
final class LangueClient {
    private LangueClient() {
    }

    static boolean francais() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.options == null) return true;
            String code = mc.options.languageCode;
            return code == null || code.toLowerCase(java.util.Locale.ROOT).startsWith("fr");
        } catch (Throwable t) {
            return true;
        }
    }
}
