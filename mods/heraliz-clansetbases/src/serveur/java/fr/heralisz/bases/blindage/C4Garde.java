package fr.heralisz.bases.blindage;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.pillage.PillageConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Le C4 pose dans une base ne disparait pas au dechargement de son chunk (BASES_SPEC §5). Le moteur heralisz_tactical
 * declare {@code StickyGrenadeEntity} en {@code noSave()} : decharge, le C4 est perdu (et le detonateur ne trouve plus
 * rien). Sans toucher a ce mod, on garde le chunk du C4 charge (ticket de region, niveau « entites actives ») tant que la
 * fenetre de pillage est ouverte ou qu'un joueur est dans la base, au plus {@code c4_maintien_min} minutes ; le ticket
 * expire seul 60 s apres le dernier renouvellement (C4 explose ou retire). Un redemarrage du serveur efface toujours
 * les C4 poses : correctif propose dans heralisz_tactical (voir docs/C4.md, « Persistance »).
 */
public final class C4Garde {
    private C4Garde() {
    }

    /** Type d'entite du C4 colle (seul explosif collant du moteur). */
    public static final ResourceLocation TYPE_C4 = new ResourceLocation("heralisz_tactical", "sticky_grenade_entity");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("heralisz_bases_c4", Comparator.comparingLong(ChunkPos::toLong), 20 * 60);

    private record Suivi(Entity entite, long depuis) {
    }

    private static final List<Suivi> SUIVIS = new ArrayList<>();
    private static int seconde;

    @SubscribeEvent
    public static void onArrivee(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !BasesApi.dansBunkers(e.getLevel())) return;
        Entity ent = e.getEntity();
        if (!TYPE_C4.equals(ForgeRegistries.ENTITY_TYPES.getKey(ent.getType()))) return;
        for (Suivi s : SUIVIS) if (s.entite() == ent) return;
        SUIVIS.add(new Suivi(ent, System.currentTimeMillis()));
        HeraliszBases.LOG.debug("[bases] C4 suivi en {}", ent.blockPosition());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SUIVIS.isEmpty() || ++seconde < 20) return;
        seconde = 0;
        long max = PillageConfig.C4_MAINTIEN_MIN.get() * 60_000L, now = System.currentTimeMillis();
        boolean fenetre = BasesApi.fenetreOuverte();
        for (int i = SUIVIS.size() - 1; i >= 0; i--) {
            Suivi s = SUIVIS.get(i);
            Entity ent = s.entite();
            if (ent.isRemoved() || !(ent.level() instanceof ServerLevel level) || now - s.depuis() > max) {
                SUIVIS.remove(i);
                continue;
            }
            if (!fenetre && !joueurDansBase(level, ent)) continue;
            ChunkPos cp = ent.chunkPosition();
            level.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
        }
    }

    private static boolean joueurDansBase(ServerLevel level, Entity ent) {
        Base b = BasesApi.baseA(level, ent.blockPosition());
        if (b == null) return false;
        for (ServerPlayer p : level.players()) {
            if (BasesApi.baseA(level, p.blockPosition()) == b) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        SUIVIS.clear();
        seconde = 0;
    }

    public static int suivis() {
        return SUIVIS.size();
    }
}
