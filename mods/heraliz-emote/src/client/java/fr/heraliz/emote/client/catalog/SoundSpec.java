package fr.heraliz.emote.client.catalog;

import javax.annotation.Nullable;

/**
 * One sound of an emote (a sound event id, on one tick of its timeline). The music of an emote is a
 * note block song Emotecraft plays itself ({@code heraliz_<id>.nbs}); these are the other sounds:
 * a block breaking, a splash, a whoosh... Filled by Gson from the catalog.
 */
public final class SoundSpec {
    public int t;
    public String id = "minecraft:ui.button.click";
    public float volume = 1;
    public float pitch = 1;
    /** Where the sound comes from (same attachments as the props), the player's feet if unset. */
    @Nullable
    public String attach;
    public float[] pos = {0, 0, 0};
}
