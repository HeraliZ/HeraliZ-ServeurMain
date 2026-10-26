package fr.heraliz.emote.client.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One object shown during an emote: a vanilla item or block, or one of the mod's box models,
 * attached to a body part and moved by its own keyframes (ticks of the emote timeline).
 *
 * <p>Attachments: {@code rightHand}/{@code leftHand} (the frame vanilla uses for held items),
 * {@code rightArm}, {@code leftArm}, {@code rightLeg}, {@code leftLeg}, {@code head},
 * {@code body} (model part frames, pixels, y down) and {@code root} (the whole model).
 */
public final class PropSpec {
    public final String attach;
    @Nullable
    public final String item;
    @Nullable
    public final String block;
    @Nullable
    public final String model;
    /** How an item is drawn: hand, fixed, ground or head. */
    public final String display;
    /** Light level 0-15 forced on the prop, -1 to use the player's. */
    public final int light;
    /** False for big scenery left out of the menu icons (a hoop far away, the sea under a surfboard). */
    public final boolean inIcon;
    /** Block props: ticks over which the block cracks, as when it is mined (null: never). */
    @Nullable
    public final int[] crack;
    @Nullable
    private final int[][] show;
    private final Key[] keys;

    public record Key(float t, float[] pos, float[] rot, float scale, String ease) {
    }

    private PropSpec(String attach, @Nullable String item, @Nullable String block, @Nullable String model, String display,
                     int light, boolean inIcon, @Nullable int[] crack, @Nullable int[][] show, Key[] keys) {
        this.attach = attach;
        this.item = item;
        this.block = block;
        this.model = model;
        this.display = display;
        this.light = light;
        this.inIcon = inIcon;
        this.crack = crack;
        this.show = show;
        this.keys = keys;
    }

    static PropSpec parse(JsonObject o) {
        int[][] show = null;
        if (o.has("show")) {
            JsonArray ranges = o.getAsJsonArray("show");
            show = new int[ranges.size()][2];
            for (int i = 0; i < ranges.size(); i++) {
                JsonArray r = ranges.get(i).getAsJsonArray();
                show[i][0] = r.get(0).getAsInt();
                show[i][1] = r.get(1).getAsInt();
            }
        }
        List<Key> keys = new ArrayList<>();
        if (o.has("keys")) {
            for (JsonElement el : o.getAsJsonArray("keys")) {
                JsonObject k = el.getAsJsonObject();
                keys.add(new Key(k.get("t").getAsFloat(),
                        EmoteCatalog.floats(k.has("pos") ? k.getAsJsonArray("pos") : null, 3, 0),
                        EmoteCatalog.floats(k.has("rot") ? k.getAsJsonArray("rot") : null, 3, 0),
                        k.has("scale") ? k.get("scale").getAsFloat() : 1.0F,
                        k.has("ease") ? k.get("ease").getAsString() : "linear"));
            }
        }
        if (keys.isEmpty()) keys.add(new Key(0, new float[3], new float[3], 1, "linear"));
        return new PropSpec(o.get("attach").getAsString(),
                o.has("item") ? o.get("item").getAsString() : null,
                o.has("block") ? o.get("block").getAsString() : null,
                o.has("model") ? o.get("model").getAsString() : null,
                o.has("display") ? o.get("display").getAsString() : "hand",
                o.has("light") ? o.get("light").getAsInt() : -1,
                !o.has("icon") || o.get("icon").getAsBoolean(),
                o.has("crack") ? new int[]{o.getAsJsonArray("crack").get(0).getAsInt(), o.getAsJsonArray("crack").get(1).getAsInt()} : null,
                show, keys.toArray(new Key[0]));
    }

    /** The crack stage (0-9) at {@code tick}, or -1 for none. */
    public int crackStage(float tick) {
        if (crack == null || tick < crack[0]) return -1;
        return Math.min(9, (int) ((tick - crack[0]) / Math.max(1, crack[1] - crack[0]) * 10));
    }

    public boolean visible(float tick) {
        if (show == null) return true;
        for (int[] r : show) {
            if (tick >= r[0] && tick < r[1]) return true;
        }
        return false;
    }

    /** Fills x, y, z (pixels), pitch, yaw, roll (degrees) and scale at {@code tick}. */
    public void sample(float tick, float[] out) {
        Key a = keys[0];
        Key b = null;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].t() <= tick) {
                a = keys[i];
                b = i + 1 < keys.length ? keys[i + 1] : null;
            }
        }
        if (tick < keys[0].t()) b = null;
        float f = 0;
        if (b != null && b.t() > a.t()) f = ease(a.ease(), (tick - a.t()) / (b.t() - a.t()));
        else b = a;
        for (int i = 0; i < 3; i++) {
            out[i] = a.pos()[i] + (b.pos()[i] - a.pos()[i]) * f;
            out[3 + i] = a.rot()[i] + (b.rot()[i] - a.rot()[i]) * f;
        }
        out[6] = a.scale() + (b.scale() - a.scale()) * f;
    }

    static float ease(String ease, float x) {
        x = Math.max(0, Math.min(1, x));
        return switch (ease) {
            case "constant" -> 0;
            case "inoutsine" -> (float) (-(Math.cos(Math.PI * x) - 1) / 2);
            case "outquad" -> 1 - (1 - x) * (1 - x);
            case "inquad" -> x * x;
            case "inoutquad" -> x < 0.5F ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
            case "outback" -> {
                float c1 = 1.70158F;
                float c3 = c1 + 1;
                yield 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
            }
            default -> x;
        };
    }
}
