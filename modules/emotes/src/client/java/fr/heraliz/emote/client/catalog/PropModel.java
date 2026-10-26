package fr.heraliz.emote.client.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** A prop made of coloured boxes (pixels, same axes as the frame it is attached to). */
public record PropModel(List<Box> boxes) {

    public record Box(float[] from, float[] to, int color, float[] pivot, float[] rot, boolean glow) {
    }

    static PropModel parse(JsonObject o) {
        List<Box> boxes = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray("boxes")) {
            JsonObject b = el.getAsJsonObject();
            String c = b.has("color") ? b.get("color").getAsString() : "#ffffff";
            int color = (int) Long.parseLong(c.replace("#", ""), 16);
            boxes.add(new Box(EmoteCatalog.floats(b.getAsJsonArray("from"), 3, 0), EmoteCatalog.floats(b.getAsJsonArray("to"), 3, 0),
                    color, EmoteCatalog.floats(b.has("pivot") ? b.getAsJsonArray("pivot") : null, 3, 0),
                    EmoteCatalog.floats(b.has("rot") ? b.getAsJsonArray("rot") : null, 3, 0),
                    b.has("glow") && b.get("glow").getAsBoolean()));
        }
        return new PropModel(List.copyOf(boxes));
    }
}
