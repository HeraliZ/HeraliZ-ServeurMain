package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Plus aucune musique de Minecraft (23/10, demande du propriétaire) : ni à l'écran titre ni en jeu. Seuls les sons de la
 * catégorie Musique venant de Minecraft ({@code minecraft:music.*}) sont coupés ; les musiques et sons des mods, les
 * disques des juke-box (catégorie Disques) ne changent pas. Le gestionnaire de musique de Minecraft continue de
 * « choisir » un morceau, qui n'est jamais joué. Une musique HeraliZ pourra être ajoutée plus tard (fichier à fournir).
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class SansMusique {
    private SansMusique() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void son(PlaySoundEvent e) {
        SoundInstance s = e.getSound(); // peut être null : un autre mod a pu l'annuler avant
        if (s == null || s.getSource() != SoundSource.MUSIC) return;
        if ("minecraft".equals(s.getLocation().getNamespace())) {
            e.setSound(null);
        }
    }
}
