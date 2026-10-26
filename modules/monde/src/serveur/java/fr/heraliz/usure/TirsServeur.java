package fr.heraliz.usure;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Partie SERVEUR de {@link Tirs} (docs/METHODE_CLIENT_SERVEUR.md §5) : les ecouteurs des tirs TaCZ, code d'origine deplace
 * tel quel (arme ruinee refusee, usure a chaque balle). Inscrits par la partie serveur (HeralizUsureServeur.init, appele
 * par serveur.PartieServeur), a la place d'origine dans l'ordre des ecouteurs. Les exemptions et le calcul de la perte
 * restent communs (Tirs.exemptee, Tirs.perte : le client lit aussi les exemptions).
 */
public final class TirsServeur {
    private TirsServeur() {
    }

    /** Cles NBT des accessoires montes sur l'arme ({@code Attachment<TYPE>}), calculees une fois. */
    private static final String[] CLES_ACCESSOIRES;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    static {
        AttachmentType[] types = AttachmentType.values();
        CLES_ACCESSOIRES = new String[types.length];
        for (int i = 0; i < types.length; i++) CLES_ACCESSOIRES[i] = "Attachment" + types[i].name();
    }

    static void enregistrer(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, false, GunShootEvent.class, TirsServeur::avantTir);
        bus.addListener(EventPriority.HIGH, false, GunFireEvent.class, TirsServeur::avantBalle);
        bus.addListener(EventPriority.LOWEST, false, GunFireEvent.class, TirsServeur::apresBalle);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() == LogicalSide.SERVER && e.getShooter() instanceof ServerPlayer p && bloquee(p, e.getGunItemStack())) {
            e.setCanceled(true);
        }
    }

    private static void avantBalle(GunFireEvent e) {
        if (e.getLogicalSide() == LogicalSide.SERVER && e.getShooter() instanceof ServerPlayer p && bloquee(p, e.getGunItemStack())) {
            e.setCanceled(true);
        }
    }

    /** Arme suivie et ruinee, entre les mains d'un joueur qui n'en est pas exempte. */
    static boolean bloquee(ServerPlayer p, ItemStack arme) {
        String id = Usure.gunId(arme);
        if (id == null) return false;
        Regles r = ReglesServeur.get();
        if (Tirs.exemptee(p, arme, r)) return false;
        Profil pr = r.profil(id);
        if (!pr.suivie) return false;
        Usure.migrer(arme, pr);
        return Usure.etat(arme, pr) <= 0;
    }

    private static void apresBalle(GunFireEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        ItemStack arme = e.getGunItemStack();
        String id = Usure.gunId(arme);
        if (id == null) return;
        Regles r = ReglesServeur.get();
        if (Tirs.exemptee(p, arme, r)) return;
        Profil pr = r.profil(id);
        if (!pr.suivie) return;
        Usure.migrer(arme, pr);
        int avant = Usure.etat(arme, pr);
        if (avant <= 0) return;
        float m = pr.usureMilliemes;
        if (m > 0) {
            if (p.isInWater()) m *= pr.multEau;
            else if (sousLaPluie(p)) m *= pr.multPluie;
            if (pr.multSilencieux != 1f && silencieux(p)) m *= pr.multSilencieux;
            if (!r.accessoires.isEmpty()) m *= 1f + surplusAccessoires(arme, r) / 100f;
            int perte = Tirs.perte(m, p.getRandom().nextFloat());
            if (perte > 0) {
                int apres = Math.max(0, avant - perte);
                Usure.ecrireEtat(arme, apres);
                if (apres == 0) ruinee(p, arme);
            }
        }
    }

    /** Pluie qui tombe sur le tireur (ciel visible, biome ou il pleut, pas de neige). */
    private static boolean sousLaPluie(ServerPlayer p) {
        Level l = p.level();
        if (!l.isRaining()) return false;
        POS.set(p.getX(), p.getEyeY(), p.getZ());
        if (!l.canSeeSky(POS)) return false;
        return l.getBiome(POS).value().getPrecipitationAt(POS) == Biome.Precipitation.RAIN;
    }

    /** Silencieux monte (propriete « silence » des accessoires TaCZ, deja calculee pour l'arme en main). */
    private static boolean silencieux(ServerPlayer p) {
        AttachmentCacheProperty cache = IGunOperator.fromLivingEntity(p).getCacheProperty();
        if (cache == null) return false;
        Object o = cache.getCache(SilenceModifier.ID);
        return o instanceof Pair<?, ?> pair && Boolean.TRUE.equals(pair.right());
    }

    /** Surplus (%) des accessoires montes qui ont un reglage. */
    private static float surplusAccessoires(ItemStack arme, Regles r) {
        CompoundTag t = arme.getTag();
        if (t == null) return 0;
        float s = 0;
        for (String cle : CLES_ACCESSOIRES) {
            if (t.get(cle) instanceof CompoundTag pile && pile.get("tag") instanceof CompoundTag tag && tag.get("AttachmentId") instanceof StringTag id) {
                s += r.surplusAccessoire(id.getAsString());
            }
        }
        return s;
    }

    private static void ruinee(ServerPlayer p, ItemStack arme) {
        p.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
        p.displayClientMessage(Component.translatable("heraliz_usure.message.ruinee", arme.getHoverName()).withStyle(ChatFormatting.RED), true);
    }
}
