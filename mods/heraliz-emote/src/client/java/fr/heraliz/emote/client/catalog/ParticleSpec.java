package fr.heraliz.emote.client.catalog;

/**
 * Particles an emote gives off: which particle, from which body part, and when (ticks of the
 * emote timeline). Filled by Gson from the catalog, so the fields keep their JSON names.
 */
public final class ParticleSpec {
    /** Particle id, e.g. minecraft:note; "minecraft:dust" takes {@link #color} and {@link #size}. */
    public String type = "minecraft:happy_villager";
    /** Same attachments as the props, the point being {@link #pos} in that frame (pixels). */
    public String attach = "root";
    public float[] pos = {0, 0, 0};
    /** Exact ticks to emit on, or else every {@link #every} ticks between {@link #from} and {@link #to}. */
    public int[] ticks;
    public int every = 0;
    public int from = 0;
    public int to = Integer.MAX_VALUE;
    public int count = 1;
    /** Random offset around the point, blocks. */
    public float[] spread = {0, 0, 0};
    /** Velocity in the player's frame (x right, y up, z forward), blocks per tick, plus a random part. */
    public float[] velocity = {0, 0, 0};
    public float speed = 0;
    public float[] color;
    public float size = 1;
    /** For "minecraft:block" (block bits, a block id) and "minecraft:item" (item bits, an item id). */
    public String block;
    public String item;

    public boolean emitsAt(int tick) {
        if (ticks != null) {
            for (int t : ticks) {
                if (t == tick) return true;
            }
            return false;
        }
        return every > 0 && tick >= from && tick < to && (tick - from) % every == 0;
    }
}
