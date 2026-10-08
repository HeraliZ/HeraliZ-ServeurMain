package fr.heraliz.emote.client.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.EmoteSounds;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import fr.heraliz.emote.client.catalog.SoundSpec;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayer;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A copy of the player, kept out of the world, that plays an emote in a GUI: the menu preview.
 * Props are drawn too, since they are a layer of the player renderer.
 */
public class EmotePreview {
    private static EmotePreview nameless;

    @Nullable
    private RemotePlayer dummy;
    @Nullable
    private KeyframeAnimation animation;
    private int pause;
    private int lastTick = -1;
    private final Function<GameProfile, RemotePlayer> factory;
    /** Play the emote's sound effects (the menu does, the icon renders do not). */
    private final boolean sounds;

    public EmotePreview() {
        this(profile -> new RemotePlayer(Minecraft.getInstance().level, profile), true);
    }

    public EmotePreview(Function<GameProfile, RemotePlayer> factory) {
        this(factory, false);
    }

    private EmotePreview(Function<GameProfile, RemotePlayer> factory, boolean sounds) {
        this.factory = factory;
        this.sounds = sounds;
    }

    @Nullable
    private RemotePlayer dummy() {
        Minecraft mc = Minecraft.getInstance();
        if (dummy == null && mc.level != null && mc.player != null) {
            dummy = factory.apply(mc.player.getGameProfile());
            dummy.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
        return dummy;
    }

    @Nullable
    public KeyframeAnimation animation() {
        return animation;
    }

    /** Plays {@code anim} from {@code tick} (restarts it if it is the one already shown). */
    public void show(@Nullable KeyframeAnimation anim, int tick) {
        animation = anim;
        pause = 0;
        lastTick = -1;
        RemotePlayer d = dummy();
        if (d == null) return;
        IEmotePlayerEntity<?> holder = (IEmotePlayerEntity<?>) d;
        if (anim == null) {
            IEmotePlayer playing = holder.getEmote();
            if (playing != null) ((KeyframeAnimationPlayer) playing).stop();
        } else {
            holder.playEmote(anim, tick, false);
        }
    }

    /** One game tick: advance the animation, replay it after a short pause when it ends. */
    public void tick() {
        RemotePlayer d = dummy();
        if (d == null || animation == null) return;
        IEmotePlayer emote = ((IEmotePlayerEntity<?>) d).getEmote();
        if (emote != null && emote.isRunning()) {
            emote.tick();
            // the music is the emote's note block song, played by Emotecraft at the dummy's spot (the
            // player's); the sound effects are ours
            int tick = emote.getTick();
            EmoteCatalog.EmoteInfo info = sounds && tick != lastTick ? EmoteCatalog.get(animation.getUuid()) : null;
            if (info != null) {
                for (SoundSpec spec : info.sounds()) {
                    if (spec.t == tick) EmoteSounds.playUi(spec);
                }
            }
            lastTick = tick;
        } else if (++pause > 12) {
            show(animation, 0);
        }
        d.tickCount++;
    }

    /** Draws the dummy standing at (x, y) (feet), {@code size} pixels per block, turned by {@code yaw}. */
    public void render(GuiGraphics g, int x, int y, float size, float yaw, float pitch, float partialTick) {
        RemotePlayer d = dummy();
        if (d == null) return;
        d.yBodyRot = d.yBodyRotO = 180.0F;
        d.setYRot(180.0F);
        d.yRotO = 180.0F;
        d.yHeadRot = d.yHeadRotO = 180.0F;
        d.setXRot(0);
        d.xRotO = 0;
        g.pose().pushPose();
        g.pose().translate(x, y, 120.0);
        g.pose().mulPoseMatrix(new Matrix4f().scaling(size, size, -size));
        g.pose().mulPose(Axis.ZP.rotationDegrees(180.0F));
        g.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        g.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        nameless = this;
        RenderSystem.runAsFancy(() -> dispatcher.render(d, 0, 0, 0, 0, partialTick, g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT));
        g.flush();
        nameless = null;
        dispatcher.setRenderShadow(true);
        g.pose().popPose();
        Lighting.setupFor3DItems();
    }

    /** No name tag over the preview. */
    public static void onNameTag(RenderNameTagEvent event) {
        if (nameless != null && event.getEntity() == nameless.dummy) event.setResult(Event.Result.DENY);
    }
}
