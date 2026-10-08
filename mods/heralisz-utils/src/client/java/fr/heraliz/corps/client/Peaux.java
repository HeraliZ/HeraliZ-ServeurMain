package fr.heraliz.corps.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import fr.heraliz.corps.entite.CorpsEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Peau d'un corps, resolue une fois puis gardee dans l'entite :
 * <ol>
 *     <li>CaraCrea (le corps porte l'apparence du mort, ~30 octets) : le skin peint par CaraCrea, meme si le mort est
 *     deconnecte ; peint une fois en arriere-plan par CaraCrea puis partage par tous les corps de meme apparence ;</li>
 *     <li>sinon le skin vanilla : celui du joueur s'il est en ligne, sinon celui du profil enregistre a sa mort ;</li>
 *     <li>sinon le skin par defaut de son UUID.</li>
 * </ol>
 */
public final class Peaux {
    private Peaux() {
    }

    private static Boolean caraCrea;

    /** Peau d'un corps (rendu : appel a chaque image, sans allocation une fois resolue). */
    static final class Peau implements AutoCloseable {
        ResourceLocation texture;
        boolean fin;
        /** Plus rien a verifier (skin vanilla charge ou par defaut). */
        boolean finale;
        @Nullable
        UUID cleCaraCrea;
        boolean demande;

        @Override
        public void close() {
            if (cleCaraCrea != null) CaraCreaPeaux.liberer(cleCaraCrea);
            cleCaraCrea = null;
        }
    }

    static Peau de(CorpsEntity e) {
        Peau p = e.peauCache instanceof Peau q ? q : null;
        if (p == null) {
            p = new Peau();
            p.texture = DefaultPlayerSkin.getDefaultSkin(e.proprietaire());
            p.fin = "slim".equals(DefaultPlayerSkin.getSkinModelName(e.proprietaire()));
            e.peauCache = p;
            if (caraCrea() && e.apparence() != null) {
                p.cleCaraCrea = CaraCreaPeaux.retenir(e.proprietaire(), e.apparence());
                if (p.cleCaraCrea != null) p.fin = CaraCreaPeaux.fin(p.cleCaraCrea);
            }
        }
        if (p.cleCaraCrea != null) {
            p.texture = CaraCreaPeaux.texture(p.cleCaraCrea, p.texture);
            return p;
        }
        if (!p.finale) vanilla(e, p);
        return p;
    }

    private static void vanilla(CorpsEntity e, Peau p) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener co = mc.getConnection();
        PlayerInfo info = co == null ? null : co.getPlayerInfo(e.proprietaire());
        if (info != null) {
            p.texture = info.getSkinLocation();
            p.fin = "slim".equals(info.getModelName());
            if (info.isSkinLoaded()) p.finale = true;
            return;
        }
        if (p.demande) return;
        p.demande = true;
        if (e.texturesValeur().isEmpty()) {
            p.finale = true;
            return;
        }
        GameProfile profil = new GameProfile(e.proprietaire(), e.nom());
        profil.getProperties().put("textures", new Property("textures", e.texturesValeur(),
                e.texturesSignature().isEmpty() ? null : e.texturesSignature()));
        Peau cible = p;
        mc.getSkinManager().registerSkins(profil, (type, lieu, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                cible.texture = lieu;
                cible.fin = "slim".equals(texture.getMetadata("model"));
                cible.finale = true;
            }
        }, false);
    }

    private static boolean caraCrea() {
        if (caraCrea == null) caraCrea = ModList.get() != null && ModList.get().isLoaded("caracrea");
        return caraCrea;
    }

    static void vider() {
        if (caraCrea()) CaraCreaPeaux.toutLiberer();
    }
}
