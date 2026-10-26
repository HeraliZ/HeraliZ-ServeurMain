package com.caracrea.client.gui;

import com.caracrea.character.Appearance;
import com.caracrea.client.ClientAppearances;
import com.caracrea.client.FaceAnimator;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.UUID;

/**
 * A stand-in player rendered in the creator with the look being edited. It
 * is never added to the level; it only exists to be drawn, with its own
 * UUID so its skin is separate from the real player's.
 */
public final class PreviewPlayer extends RemotePlayer {
    private final UUID previewId;

    public PreviewPlayer(ClientLevel level, Appearance appearance) {
        this(level, UUID.randomUUID(), appearance);
    }

    private PreviewPlayer(ClientLevel level, UUID id, Appearance appearance) {
        super(level, new GameProfile(id, "caracrea_preview"));
        this.previewId = id;
        ClientAppearances.setPreview(id, appearance);
        var local = Minecraft.getInstance().player;
        FaceAnimator.animateAlso(id, local == null ? id : local.getUUID());
        // Far from any camera, so the renderer never draws a name tag over the preview.
        setPos(0, -30000, 0);
    }

    public void setAppearance(Appearance appearance) {
        ClientAppearances.setPreview(previewId, appearance);
    }

    /** Advances the idle animation without running the player logic (the dummy is not in the world). */
    public void idleTick() {
        tickCount++;
        xo = getX();
        yo = getY();
        zo = getZ();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        yRotO = getYRot();
        xRotO = getXRot();
        walkAnimation.update(0, 0.4F);
    }

    /** Turns the whole character. */
    public void face(float bodyYaw, float headYaw, float pitch) {
        yBodyRot = bodyYaw;
        yBodyRotO = bodyYaw;
        yHeadRot = headYaw;
        yHeadRotO = headYaw;
        setYRot(headYaw);
        yRotO = headYaw;
        setXRot(pitch);
        xRotO = pitch;
    }

    /** Every layer of the skin is shown, whatever the real player's settings. */
    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return true;
    }

    public void dispose() {
        FaceAnimator.stopAnimating(previewId);
        ClientAppearances.removePreview(previewId);
    }
}
