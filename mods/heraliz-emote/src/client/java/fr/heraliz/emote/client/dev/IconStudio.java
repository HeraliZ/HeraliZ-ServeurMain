package fr.heraliz.emote.client.dev;

import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.gui.EmotePreview;
import fr.heraliz.emote.client.render.PropRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Development only: draws Steve frozen in an emote's icon pose on a flat green background, so CI
 * screenshots can be keyed into the icons (tools/icons.py).
 */
public class IconStudio extends Screen {
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/player/wide/steve.png");
    private final EmotePreview preview = new EmotePreview(profile -> new RemotePlayer(Minecraft.getInstance().level,
            new GameProfile(UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"), "Steve")) {
        @Override
        public ResourceLocation getSkinTextureLocation() {
            return STEVE;
        }

        @Override
        public String getModelName() {
            return "default";
        }
    });

    public IconStudio() {
        super(Component.literal("icons"));
    }

    public void pose(KeyframeAnimation anim, int tick) {
        preview.show(anim, tick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF00FF00);
        float size = (height - 40) / 2.6F;
        PropRenderer.iconRender = true;
        try {
            preview.render(g, width / 2, height - 24 - (int) (size * 0.35F), size, -24, -6, 0);
        } finally {
            PropRenderer.iconRender = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
