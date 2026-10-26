package fr.heraliz.emote.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings, {@code config/heraliz_emote-client.toml}; also editable from the mods list. */
public final class HeralizConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue BORROW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue EMPTY_HAND_ONLY;
    public static final ForgeConfigSpec.BooleanValue SYNC_EMOTE;
    public static final ForgeConfigSpec.BooleanValue BORROW_SOUND;
    public static final ForgeConfigSpec.BooleanValue SHOW_MESSAGES;
    public static final ForgeConfigSpec.IntValue HOLD_SECONDS;
    public static final ForgeConfigSpec.IntValue REACH;
    public static final ForgeConfigSpec.BooleanValue OTHER_PLAYERS_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue HIDE_EMOTECRAFT_EMOTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Borrowing the emote of another player (Shift + right click on them by default)").push("borrow");
        BORROW_ENABLED = b.comment("Turns borrowing on or off. The keys stay bound but do nothing when off.")
                .define("enabled", true);
        EMPTY_HAND_ONLY = b.comment("Borrow only with an empty main hand, so the click still uses the item you hold.")
                .define("emptyHandOnly", true);
        SYNC_EMOTE = b.comment("Looping emotes start at the same moment as the other player's, so you dance in step.")
                .define("syncEmote", true);
        BORROW_SOUND = b.comment("Keep the music of borrowed emotes. Holding the Reverse sound key flips this for one borrow.")
                .define("borrowSound", true);
        SHOW_MESSAGES = b.comment("Show a message above the hotbar when you borrow an emote.")
                .define("showMessages", true);
        HOLD_SECONDS = b.comment("How long a borrowed emote waits for you to stop crouching before it is dropped (seconds).")
                .defineInRange("holdSeconds", 3, 1, 10);
        REACH = b.comment("How far away the player you borrow from can be (blocks).")
                .defineInRange("reach", 6, 2, 16);
        b.pop();
        b.comment("The Heraliz emotes").push("emotes");
        OTHER_PLAYERS_SOUNDS = b.comment("Hear the music and sound effects of the emotes other players play (off: only yours).")
                .define("otherPlayersSounds", true);
        HIDE_EMOTECRAFT_EMOTES = b.comment("Leave out the emotes that come with Emotecraft (waving, clap, backflip...): only your own",
                        "emotes and the Heraliz ones are listed. Takes effect when Emotecraft loads its emotes again (restart).")
                .define("hideEmotecraftEmotes", true);
        b.pop();
        SPEC = b.build();
    }

    private HeralizConfig() {
    }

    /** Whether Emotecraft's own emotes are left out; Emotecraft may load them before this config is read. */
    public static boolean hideEmotecraftEmotes() {
        return !SPEC.isLoaded() || HIDE_EMOTECRAFT_EMOTES.get();
    }
}
