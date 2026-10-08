package com.Spartan.Client;

/**
 * Détecte le shaderpack actif côté client, sans dépendre des mods de shaders.
 *
 * Tout passe par réflexion dans des try/catch : si Iris / Oculus / OptiFine ne
 * sont pas installés (cas d'un modpack sans shaders), les classes sont absentes
 * et la sonde renvoie simplement « aucun shader ». C'est best-effort : un client
 * modifié peut mentir. La vraie garantie reste de ne pas inclure de mod de
 * shaders dans le modpack officiel (le launcher empêche alors d'en ajouter un).
 */
public final class ShaderProbe {
    private ShaderProbe() {
    }

    /** Nom du shaderpack actif, ou null si aucun / indéterminable. */
    public static String activeShaderpack() {
        String iris = iris();
        if (iris != null) {
            return iris;
        }
        return optifine();
    }

    /** Iris / Oculus via leur API publique stable. */
    private static String iris() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            boolean inUse = (boolean) api.getMethod("isShaderPackInUse").invoke(instance);
            if (!inUse) {
                return null;
            }
            Object name = api.getMethod("getCurrentShaderpackName").invoke(instance);
            return name != null ? name.toString() : "shaderpack";
        } catch (Throwable ignoredNewApi) {
            // Ancien identifiant de package d'Oculus.
            try {
                Class<?> api = Class.forName("net.coderbot.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                boolean inUse = (boolean) api.getMethod("isShaderPackInUse").invoke(instance);
                return inUse ? "shaderpack" : null;
            } catch (Throwable ignoredOldApi) {
                return null;
            }
        }
    }

    /** OptiFine : nom du shaderpack courant (vide ou "OFF" = aucun). */
    private static String optifine() {
        try {
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
            Object name = shaders.getMethod("getShaderPackName").invoke(null);
            if (name == null) {
                return null;
            }
            String s = name.toString();
            return s.isBlank() || s.equalsIgnoreCase("off") ? null : s;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
