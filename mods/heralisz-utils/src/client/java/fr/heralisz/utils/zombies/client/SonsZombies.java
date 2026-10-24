package fr.heralisz.utils.zombies.client;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Bruit des zombies plus bas (Utils 2.27.0, demande du propriétaire du 25/10, après l'ouverture : « les zombies font trop
 * de bruit ») : chez le joueur seulement, les sons des zombies de la survie (zombie de Minecraft :
 * {@code minecraft:entity.zombie.*}) et de Mutants &amp; Zombies (qui prend les sons du zombie et du husk de Minecraft :
 * {@code minecraft:entity.husk.*}) sont joués à {@link #VOLUME} de leur volume et ne s'entendent plus qu'à {@link #PORTEE}
 * blocs (Minecraft : 16), de moins en moins fort avec la distance. L'atténuation est calculée ici (le moteur audio n'en
 * applique aucune aux fichiers stéréo) ; la direction du son est gardée. Rien ne change dans les mondes des matchs (carte
 * des Zombies : sons du mode de jeu), ni pour les sons en boucle ou qui suivent l'entité de près (laissés tels quels).
 * Le serveur n'est pas touché.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class SonsZombies {
    /** Part du volume d'origine gardée tout près du zombie. */
    static final float VOLUME = 0.5F;
    /** Portée en blocs (Minecraft : 16). */
    static final double PORTEE = 11.0;

    private SonsZombies() {
    }

    private static boolean sonDeZombie(ResourceLocation id) {
        if (!"minecraft".equals(id.getNamespace())) return false;
        String p = id.getPath();
        return p.startsWith("entity.zombie.") || p.startsWith("entity.husk.");
    }

    // après les autres mods qui coupent des sons (dayzhotbar, Emote) : le son peut déjà être null
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void son(PlaySoundEvent e) {
        SoundInstance son = e.getSound();
        if (son == null || son.isRelative() || son instanceof TickableSoundInstance || son instanceof SonAttenue) return;
        if (son.getSource() != SoundSource.HOSTILE || !sonDeZombie(son.getLocation())) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer moi = mc.player;
        if (moi == null || "heralisz_matchs".equals(moi.level().dimension().location().getNamespace())) return;
        double dx = son.getX() - moi.getX(), dy = son.getY() - moi.getY(), dz = son.getZ() - moi.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance >= PORTEE) {
            e.setSound(null);
            return;
        }
        e.setSound(new SonAttenue(son, VOLUME * (float) (1.0 - distance / PORTEE)));
    }

    /** Le même son (même fichier, même hauteur, même place), volume multiplié, atténuation déjà comptée. */
    private static final class SonAttenue implements SoundInstance {
        private final SoundInstance son;
        private final float facteur;

        SonAttenue(SoundInstance son, float facteur) {
            this.son = son;
            this.facteur = facteur;
        }

        @Override
        public ResourceLocation getLocation() {
            return son.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return son.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return son.getSound();
        }

        @Override
        public SoundSource getSource() {
            return son.getSource();
        }

        @Override
        public boolean isLooping() {
            return son.isLooping();
        }

        @Override
        public boolean isRelative() {
            return son.isRelative();
        }

        @Override
        public int getDelay() {
            return son.getDelay();
        }

        @Override
        public float getVolume() {
            return son.getVolume() * facteur;
        }

        @Override
        public float getPitch() {
            return son.getPitch();
        }

        @Override
        public double getX() {
            return son.getX();
        }

        @Override
        public double getY() {
            return son.getY();
        }

        @Override
        public double getZ() {
            return son.getZ();
        }

        @Override
        public SoundInstance.Attenuation getAttenuation() {
            return SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return son.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return son.canPlaySound();
        }
    }
}
