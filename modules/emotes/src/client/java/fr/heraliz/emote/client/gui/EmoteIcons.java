package fr.heraliz.emote.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.HeralizEmote;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Emote icons as textures, from the icon data Emotecraft keeps with each emote. */
public final class EmoteIcons {
    public record Icon(ResourceLocation texture, int width, int height) {
    }

    private static final Map<UUID, Icon> CACHE = new HashMap<>();
    private static final Icon NONE = new Icon(new ResourceLocation("missingno"), 0, 0);

    private EmoteIcons() {
    }

    @Nullable
    public static Icon get(KeyframeAnimation anim) {
        Icon icon = CACHE.computeIfAbsent(anim.getUuid(), uuid -> load(uuid, anim));
        return icon == NONE ? null : icon;
    }

    private static Icon load(UUID uuid, KeyframeAnimation anim) {
        Object data = anim.extraData.get("iconData");
        if (!(data instanceof ByteBuffer buffer)) return NONE;
        try {
            ByteBuffer copy = buffer.duplicate();
            copy.rewind();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            ResourceLocation id = new ResourceLocation(HeralizEmote.MODID, "icon/" + uuid);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return new Icon(id, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            return NONE;
        }
    }
}
