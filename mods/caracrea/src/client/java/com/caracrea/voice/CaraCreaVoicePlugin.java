package com.caracrea.voice;

import com.caracrea.CaraCrea;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Simple Voice Chat integration: the microphone of the local player and
 * the voices received from other players feed {@link VoiceActivity}, so
 * mouths move with the voice; and while SVC is connected, the built-in
 * voice chat keeps away from the audio devices ({@link VoiceGate}). Loaded by Simple Voice Chat only when it is
 * installed; nothing else references this class.
 */
@ForgeVoicechatPlugin
public class CaraCreaVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return CaraCrea.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, event -> VoiceActivity.reportLocal(event.getRawAudio(), event.isWhispering()));
        // While Simple Voice Chat is connected it owns the microphone and the speakers: ours never opens them.
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> VoiceGate.svcConnected = event.isConnected());
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class,
                event -> VoiceActivity.report(event.getEntityId(), event.getRawAudio(), event.isWhispering()));
        // Group voices arrive as static sounds; their id is the speaker's UUID, so nearby group members' lips move too.
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class,
                event -> VoiceActivity.report(event.getId(), event.getRawAudio(), false));
        CaraCrea.LOGGER.info("Simple Voice Chat detected: mouths will follow voices");
    }
}
