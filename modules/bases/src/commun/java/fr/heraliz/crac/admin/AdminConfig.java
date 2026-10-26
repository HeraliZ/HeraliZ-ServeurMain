package fr.heraliz.crac.admin;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/**
 * Admin console settings (config/crac-admin.toml). This is a COMMON config on purpose: Forge sends the raw file of
 * every SERVER config (crac-server.toml, crac-building.toml) to each client at login, so a secret must never live there.
 * The server only reads this file; clients never receive it. It is also hidden from the admin panel's config editor.
 */
public final class AdminConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ConfigValue<String> consolePassword;
    public static final IntValue permissionLevel;
    public static final IntValue maxFailedLogins;
    public static final IntValue lockoutSeconds;

    private AdminConfig() {
    }

    static {
        Builder b = new Builder();
        b.push("admin");
        consolePassword = b.comment(
                "Password of the in-game admin console (~ key). Empty = console disabled (default).",
                "Either the password itself or \"sha256:<64 hex digits>\", the SHA-256 of the UTF-8 password",
                "(for example: printf '%s' 'my password' | sha256sum). Changing it logs every admin out."
            )
            .define("consolePassword", "");
        permissionLevel = b.comment(
                "Operator level a player needs to log in to the console, checked again on every admin action.",
                "4 = full operator (default). 0 lets any player try the password."
            )
            .defineInRange("permissionLevel", 4, 0, 4);
        maxFailedLogins = b.comment("Wrong passwords allowed per player before a lockout.").defineInRange("maxFailedLogins", 5, 1, 100);
        lockoutSeconds = b.comment("How long a player is locked out of the console after too many wrong passwords.")
            .defineInRange("lockoutSeconds", 300, 1, 86400);
        b.pop();
        SPEC = b.build();
    }
}
