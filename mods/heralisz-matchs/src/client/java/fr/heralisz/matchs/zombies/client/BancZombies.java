package fr.heralisz.matchs.zombies.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.dev.MatchsHarness;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Etapes du banc d'essai pour la validation en jeu des zombies (client de dev seulement, enregistrees par
 * {@link ZombiesClient#etapesBanc}) :
 * <ul>
 *     <li>{@code "zbPack": "file/arkaniaz_pack7.zip"} : active le pack de textures du serveur (sans l'ecrire dans les
 *     options) et recharge les ressources ;</li>
 *     <li>{@code "zbSons": "on"|"off"|"bilan"} : journal des sons {@code heralisz_matchs:*} joues chez le client (volume,
 *     hauteur, categorie, distance, portee, gain estime), puis bilan par son (nombre, gain max) ;</li>
 *     <li>{@code "zbViser": "tete"|"corps"} : tourne la camera vers la creature de la partie la plus proche ;</li>
 *     <li>{@code "zbTirer": n} : n tirs TaCZ (API client {@code IClientPlayerGunOperator}, par reflexion) ;</li>
 *     <li>{@code "zbRecharger": true} : rechargement TaCZ ;</li>
 *     <li>{@code "zbFrapper": true} : coup de melee sur l'entite visee (comme un clic gauche) ;</li>
 *     <li>{@code "zbSlot": n} : case de la barre d'action selectionnee ;</li>
 *     <li>{@code "zbEtat": true} : main, vie et creatures proches dans le journal.</li>
 * </ul>
 * Rien n'est alloue par image : ces etapes ne tournent qu'a la demande du scenario.
 */
public final class BancZombies {
    private BancZombies() {
    }

    private static boolean journalSons;
    private static final Map<String, float[]> BILAN = new TreeMap<>();

    static void enregistrer(MatchsHarness.Registre r) {
        r.ajouter("zbPack", BancZombies::pack);
        r.ajouter("zbSons", BancZombies::sons);
        r.ajouter("zbViser", BancZombies::viser);
        r.ajouter("zbTirer", BancZombies::tirer);
        r.ajouter("zbRecharger", (mc, v, e) -> tacz(mc, "reload"));
        r.ajouter("zbFrapper", BancZombies::frapper);
        r.ajouter("zbSlot", (mc, v, e) -> {
            if (mc.player != null) mc.player.getInventory().selected = Mth.clamp(v.getAsInt(), 0, 8);
        });
        r.ajouter("zbEtat", BancZombies::etat);
        r.ajouter("zbLumiere", BancZombies::lumiere);
        MinecraftForge.EVENT_BUS.addListener(BancZombies::sonJoue);
    }

    // ------------------------------------------------------------------ pack du serveur
    private static void pack(Minecraft mc, JsonElement v, JsonObject etape) {
        String id = v.getAsString();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        if (!repo.isAvailable(id)) {
            HeraliszMatchs.LOG.warn("[zombies] banc : pack {} introuvable (disponibles : {})", id, repo.getAvailableIds());
            return;
        }
        List<String> sel = new ArrayList<>(repo.getSelectedIds());
        if (!sel.contains(id)) sel.add(id);
        repo.setSelected(sel);
        HeraliszMatchs.LOG.info("[zombies] banc : packs actifs {}", sel);
        mc.reloadResourcePacks();
    }

    // ------------------------------------------------------------------ journal des sons
    private static void sons(Minecraft mc, JsonElement v, JsonObject etape) {
        switch (v.getAsString()) {
            case "on" -> {
                journalSons = true;
                synchronized (BILAN) {
                    BILAN.clear();
                }
                brancherReseau(mc);
            }
            case "off" -> journalSons = false;
            case "bilan" -> {
                synchronized (BILAN) {
                    HeraliszMatchs.LOG.info("[zombies] sons bilan : {} sons differents", BILAN.size());
                    for (Map.Entry<String, float[]> en : BILAN.entrySet()) {
                        float[] b = en.getValue();
                        HeraliszMatchs.LOG.info(String.format(Locale.ROOT, "[zombies] sons bilan %-32s x%-4d gain max %.2f  volume max %.2f",
                                en.getKey(), (int) b[0], b[1], b[2]));
                    }
                }
            }
            default -> HeraliszMatchs.LOG.warn("[zombies] zbSons inconnu : {}", v);
        }
    }

    /**
     * Sons joues cote client (UI, boucle d'ambiance). Ne se declenche que si le moteur audio tourne : la machine du banc
     * n'a souvent aucun peripherique audio (moteur coupe), d'ou aussi l'ecoute reseau ci-dessous.
     */
    private static void sonJoue(PlaySoundEvent e) {
        if (!journalSons || e.getSound() == null) return;
        SoundInstance s = e.getSound();
        ResourceLocation id = s.getLocation();
        if (!HeraliszMatchs.MODID.equals(id.getNamespace())) return;
        Minecraft mc = Minecraft.getInstance();
        if (s.resolve(mc.getSoundManager()) == null || s.getSound() == null) return;
        boolean lineaire = s.getAttenuation() == SoundInstance.Attenuation.LINEAR && !s.isRelative();
        noter("client", id, s.getSource(), s.getVolume(), s.getPitch(), lineaire, s.getX(), s.getY(), s.getZ(),
                s.getSound().getAttenuationDistance() * Math.max(s.getVolume(), 1f));
    }

    /**
     * Ecoute des paquets de son du serveur (ClientboundSoundPacket / ClientboundSoundEntityPacket) dans le tuyau reseau
     * du client : fonctionne meme sans peripherique audio. Chaque son {@code heralisz_matchs:*} est note sur le fil
     * principal (position du joueur a cet instant).
     */
    private static void brancherReseau(Minecraft mc) {
        if (mc.getConnection() == null) return;
        io.netty.channel.Channel ch = mc.getConnection().getConnection().channel();
        if (ch == null || ch.pipeline().get("heralisz_zb_sons") != null) return;
        String avant = ch.pipeline().get("packet_handler") != null ? "packet_handler" : null;
        io.netty.channel.ChannelInboundHandlerAdapter h = new io.netty.channel.ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
                try {
                    if (journalSons) paquet(msg);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.warn("[zombies] banc : paquet de son illisible : {}", ex.toString());
                }
                super.channelRead(ctx, msg);
            }
        };
        if (avant != null) ch.pipeline().addBefore(avant, "heralisz_zb_sons", h);
        else ch.pipeline().addLast("heralisz_zb_sons", h);
        HeraliszMatchs.LOG.info("[zombies] banc : ecoute des sons du serveur branchee ({})", ch.pipeline().names());
    }

    private static void paquet(Object msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket p) {
            ResourceLocation id = p.getSound().value().getLocation();
            if (!HeraliszMatchs.MODID.equals(id.getNamespace())) return;
            float portee = p.getSound().value().getRange(p.getVolume());
            mc.execute(() -> noter("serveur", id, p.getSource(), p.getVolume(), p.getPitch(), true, p.getX(), p.getY(), p.getZ(), portee));
        } else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSoundEntityPacket p) {
            ResourceLocation id = p.getSound().value().getLocation();
            if (!HeraliszMatchs.MODID.equals(id.getNamespace())) return;
            float portee = p.getSound().value().getRange(p.getVolume());
            mc.execute(() -> {
                Entity en = mc.level == null ? null : mc.level.getEntity(p.getId());
                if (en == null) noter("entite?", id, p.getSource(), p.getVolume(), p.getPitch(), false, 0, 0, 0, portee);
                else noter("entite", id, p.getSource(), p.getVolume(), p.getPitch(), true, en.getX(), en.getY(), en.getZ(), portee);
            });
        }
    }

    /** Ligne de journal et bilan : gain estime = volume (borne a 1) x categorie x attenuation lineaire. */
    private static void noter(String origine, ResourceLocation id, net.minecraft.sounds.SoundSource source, float vol, float pitch,
                              boolean positionne, double x, double y, double z, float portee) {
        Minecraft mc = Minecraft.getInstance();
        double dist = 0;
        if (positionne && mc.player != null) dist = Math.sqrt(mc.player.getEyePosition().distanceToSqr(x, y, z));
        float categorie = mc.options.getSoundSourceVolume(source);
        float gain = Mth.clamp(vol, 0f, 1f) * categorie * (positionne ? (float) Math.max(0, 1 - dist / Math.max(1f, portee)) : 1f);
        HeraliszMatchs.LOG.info(String.format(Locale.ROOT, "[zombies] son %s (%s) categorie=%s volume=%.2f hauteur=%.2f distance=%.1f portee=%.0f gain=%.2f",
                id.getPath(), origine, source.getName(), vol, pitch, dist, portee, gain));
        synchronized (BILAN) {
            float[] b = BILAN.computeIfAbsent(id.getPath(), k -> new float[3]);
            b[0]++;
            b[1] = Math.max(b[1], gain);
            b[2] = Math.max(b[2], vol);
        }
    }

    // ------------------------------------------------------------------ armes
    private static void viser(Minecraft mc, JsonElement v, JsonObject etape) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        // la plus proche en vue directe, sinon la plus proche (derriere une fenetre barricadee, les balles passent)
        Entity best = null, secours = null;
        double bd = 48 * 48, sd = 48 * 48;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || en == p || !le.isAlive()) continue;
            if (!en.getType().builtInRegistryHolder().key().location().getNamespace().equals(HeraliszMatchs.MODID)) continue;
            double d = en.distanceToSqr(p);
            if (d < bd && p.hasLineOfSight(en)) {
                bd = d;
                best = en;
            }
            if (d < sd) {
                sd = d;
                secours = en;
            }
        }
        if (best == null) {
            best = secours;
            bd = sd;
        }
        if (best == null) {
            HeraliszMatchs.LOG.info("[zombies] banc : aucune creature visible a viser");
            return;
        }
        double y = "tete".equals(v.getAsString()) ? best.getEyeY() : best.getY() + best.getBbHeight() * 0.5;
        Vec3 o = p.getEyePosition();
        double dx = best.getX() - o.x, dy = y - o.y, dz = best.getZ() - o.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        HeraliszMatchs.LOG.info(String.format(Locale.ROOT, "[zombies] banc : vise %s a %.1f blocs (%s)", best.getType().getDescriptionId(),
                Math.sqrt(bd), v.getAsString()));
    }

    private static void tirer(Minecraft mc, JsonElement v, JsonObject etape) {
        int n = Math.max(1, v.getAsInt());
        for (int i = 0; i < n; i++) tacz(mc, "shoot");
    }

    private static void tacz(Minecraft mc, String methode) {
        if (mc.player == null) return;
        try {
            Class<?> c = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Object op = c.getMethod("fromLocalPlayer", LocalPlayer.class).invoke(null, mc.player);
            Object r = c.getMethod(methode).invoke(op);
            HeraliszMatchs.LOG.info("[zombies] banc : TaCZ {} -> {}", methode, r);
        } catch (ClassNotFoundException ex) {
            HeraliszMatchs.LOG.info("[zombies] banc : TaCZ absent, {} ignore", methode);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] banc : TaCZ {} en erreur : {}", methode, ex.toString());
        }
    }

    private static void frapper(Minecraft mc, JsonElement v, JsonObject etape) {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.hitResult instanceof EntityHitResult eh) {
            mc.gameMode.attack(mc.player, eh.getEntity());
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            HeraliszMatchs.LOG.info("[zombies] banc : coup sur {}", eh.getEntity().getType().getDescriptionId());
        } else {
            HeraliszMatchs.LOG.info("[zombies] banc : coup dans le vide");
        }
    }

    /**
     * Lumiere vue par le client autour du joueur (rayon 10) : lumiere de bloc aux yeux, moyenne et repartition sur les
     * cases d'air au niveau des yeux, et nombre de sources lumineuses (emission > 0) par bloc. Sert a juger l'eclairage
     * d'une zone de carte autrement qu'a l'oeil sur une capture.
     */
    private static void lumiere(Minecraft mc, JsonElement v, JsonObject etape) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        var lvl = mc.level;
        net.minecraft.core.BlockPos oeil = net.minecraft.core.BlockPos.containing(p.getEyePosition());
        int[] hist = new int[16];
        int n = 0, somme = 0;
        Map<String, Integer> sources = new TreeMap<>();
        var bp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -3; dy <= 6; dy++) {
                    bp.set(oeil.getX() + dx, oeil.getY() + dy, oeil.getZ() + dz);
                    var st = lvl.getBlockState(bp);
                    int em = st.getLightEmission(lvl, bp);
                    if (em > 0) sources.merge(st.getBlock().builtInRegistryHolder().key().location() + "=" + em, 1, Integer::sum);
                    if (dy == 0 && st.isAir()) {
                        int l = lvl.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, bp);
                        hist[l]++;
                        somme += l;
                        n++;
                    }
                }
            }
        }
        StringBuilder h = new StringBuilder();
        for (int i = 0; i < 16; i++) if (hist[i] > 0) h.append(i).append(':').append(hist[i]).append(' ');
        HeraliszMatchs.LOG.info(String.format(Locale.ROOT, "[zombies] banc lumiere %s : yeux %d, moyenne %.1f sur %d cases d'air | %s| sources %s",
                v.isJsonPrimitive() ? v.getAsString() : "", lvl.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, oeil),
                n == 0 ? 0f : somme / (float) n, n, h, sources));
    }

    private static void etat(Minecraft mc, JsonElement v, JsonObject etape) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        int n = 0;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en.getPersistentData().contains(ZombiesApi.TAG_PARTIE) || en.getType().getDescriptionId().contains("zb_")) n++;
        }
        StringBuilder inv = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            var st = p.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            inv.append(i).append('=').append(st.getItem().builtInRegistryHolder().key().location());
            var tag = st.getTag();
            if (tag != null) {
                for (String cle : new String[]{"GunId", "MeleeWeaponId", "ThrowableId", "ConsumableId"}) {
                    if (tag.contains(cle)) inv.append('[').append(tag.getString(cle)).append(']');
                }
            }
            if (st.getCount() > 1) inv.append('x').append(st.getCount());
            inv.append(' ');
        }
        HeraliszMatchs.LOG.info(String.format(Locale.ROOT, "[zombies] banc etat : vie %.1f/%.1f mode %s case %d | barre %s| F %b | creatures %d | pos %.1f %.1f %.1f",
                p.getHealth(), p.getMaxHealth(), mc.gameMode == null ? "?" : mc.gameMode.getPlayerMode(),
                p.getInventory().selected, inv, mc.options.keySwapOffhand.isDown(), n, p.getX(), p.getY(), p.getZ()));
    }
}
