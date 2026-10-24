package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.network.FouilleEtatPacket;
import fr.heraliz.airstrike.network.FouillePacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Fouille des morceaux d'épave (serveur, décide de tout) : clic droit tenu {@code fouilleSecondes} sans bouger, à portée,
 * l'épave refroidie ; le jeu du joueur envoie l'appui puis un « maintien » toutes les 4 ticks (FouillePacket) : rien reçu
 * pendant 12 ticks = relâché. Interrompue si le joueur bouge, s'éloigne, est touché, meurt ou se déconnecte. Un seul
 * joueur à la fois par morceau ; à la fin, une pièce tirée au sort (PieceHelico) et le morceau n'est plus fouillable.
 * Geste : bras qui fouille (vu de tous) toutes les 10 ticks, bruit de ferraille chaque seconde.
 */
public final class Fouilles {
    private static final class Fouille {
        final int entite;
        final int debut;
        final Vec3 depart;
        int dernier;

        Fouille(int entite, int debut, Vec3 depart) {
            this.entite = entite;
            this.debut = debut;
            this.depart = depart;
            this.dernier = debut;
        }
    }

    private static final Map<UUID, Fouille> EN_COURS = new HashMap<>();
    private static final double PORTEE = 5.0;

    private Fouilles() {
    }

    static int duree() {
        return AirstrikeConfig.HELICO_FOUILLE.get() * 20;
    }

    static void message(ServerPlayer p, String cle, String fr, ChatFormatting couleur, Object... args) {
        p.displayClientMessage(Component.translatableWithFallback(cle, fr, args).withStyle(couleur), true);
    }

    static void chaud(ServerPlayer p) {
        message(p, "airstrike.helico.chaud", "Encore trop chaud... attendez que le feu s'éteigne.", ChatFormatting.GOLD);
    }

    private static boolean aPortee(ServerPlayer p, Entity e) {
        AABB b = e.getBoundingBox();
        Vec3 y = p.getEyePosition();
        double dx = Math.max(0, Math.max(b.minX - y.x, y.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - y.y, y.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - y.z, y.z - b.maxZ));
        return dx * dx + dy * dy + dz * dz <= PORTEE * PORTEE;
    }

    /** Appui, maintien ou relâchement sur une entité de l'hélicoptère (paquet du joueur ou clic de Minecraft). */
    static void clic(ServerPlayer p, Entity e, byte etape) {
        if (etape == FouillePacket.RELACHE) {
            arreter(p, false);
            return;
        }
        if (e instanceof CaisseHelicoEntityServeur c) {
            if (etape == FouillePacket.APPUI && aPortee(p, c)) c.ouvrir(p);
            return;
        }
        if (!(e instanceof EpaveHelicoEntityServeur ep)) return;
        int now = p.server.getTickCount();
        Fouille f = EN_COURS.get(p.getUUID());
        if (f != null && f.entite == ep.getId()) {
            f.dernier = now;
            return;
        }
        if (f != null) arreter(p, false);
        boolean appui = etape == FouillePacket.APPUI;
        if (!p.isAlive() || p.isSpectator() || !ep.isAlive() || !aPortee(p, ep)) return;
        if (!ep.fouillable()) {
            if (appui) message(p, "airstrike.helico.vide", "Plus rien à récupérer sur ce morceau.", ChatFormatting.GRAY);
            return;
        }
        if (ep.brule()) {
            if (appui) chaud(p);
            return;
        }
        for (Fouille autre : EN_COURS.values()) {
            if (autre.entite == ep.getId()) {
                if (appui) message(p, "airstrike.helico.occupe", "Quelqu'un fouille déjà ce morceau.", ChatFormatting.GRAY);
                return;
            }
        }
        EN_COURS.put(p.getUUID(), new Fouille(ep.getId(), now, p.position()));
        Network.sendTo(p, new FouilleEtatPacket(0f, duree()));
        p.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Arrête la fouille du joueur (interrompue : message ; relâchée : rien). */
    static void arreter(ServerPlayer p, boolean interrompue) {
        if (EN_COURS.remove(p.getUUID()) == null) return;
        Network.sendTo(p, new FouilleEtatPacket(FouilleEtatPacket.ARRET, duree()));
        if (interrompue) message(p, "airstrike.helico.interrompu", "Fouille interrompue.", ChatFormatting.RED);
    }

    static boolean enCours(ServerPlayer p) {
        return EN_COURS.containsKey(p.getUUID());
    }

    static void deconnexion(UUID id) {
        EN_COURS.remove(id);
    }

    static void vider() {
        EN_COURS.clear();
    }

    /** Chaque tick du serveur (seulement s'il y a des fouilles en cours). */
    static void tick(MinecraftServer srv) {
        if (EN_COURS.isEmpty()) return;
        int now = srv.getTickCount();
        int duree = duree();
        Iterator<Map.Entry<UUID, Fouille>> it = EN_COURS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Fouille> en = it.next();
            Fouille f = en.getValue();
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Entity e = p.level().getEntity(f.entite);
            boolean bouge = horizontal(p.position(), f.depart) > 0.35 || Math.abs(p.getY() - f.depart.y) > 0.6;
            if (!(e instanceof EpaveHelicoEntityServeur ep) || !ep.isAlive() || !ep.fouillable() || !p.isAlive()
                    || bouge || !aPortee(p, ep) || now - f.dernier > 12) {
                it.remove();
                Network.sendTo(p, new FouilleEtatPacket(FouilleEtatPacket.ARRET, duree));
                if (bouge && p.isAlive()) message(p, "airstrike.helico.interrompu", "Fouille interrompue.", ChatFormatting.RED);
                continue;
            }
            int ecoule = now - f.debut;
            if (ecoule % 10 == 0) p.swing(InteractionHand.MAIN_HAND, true);
            if (ecoule % 20 == 0) {
                p.level().playSound(null, ep.getX(), ep.getY() + 0.5, ep.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS,
                        0.8f, 0.7f + p.getRandom().nextFloat() * 0.3f);
            }
            if (ecoule >= duree) {
                it.remove();
                ep.vider();
                PieceHelico piece = PieceHelico.tirer(p.getRandom());
                ItemStack st = new ItemStack(ModItems.piece(piece));
                Component nom = st.getHoverName();
                if (!p.getInventory().add(st)) p.drop(st, false);
                Network.sendTo(p, new FouilleEtatPacket(FouilleEtatPacket.FINI, duree));
                message(p, "airstrike.helico.recupere", "Récupéré : %s", ChatFormatting.GREEN, nom);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
                AirstrikeMod.LOGGER.info("[helico] {} récupère {} sur l'épave", p.getGameProfile().getName(), piece.objet());
            } else if (ecoule % 5 == 0) {
                Network.sendTo(p, new FouilleEtatPacket(ecoule / (float) duree, duree));
            }
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
