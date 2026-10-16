package fr.heraliz.loot;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Présentoirs protégés (Loot 0.9.0, section {@code [presentoirs]} de {@code heraliz_loot-server.toml}) : cadres et cadres
 * lumineux, porte-armures, mannequins de l'Armurerie (un {@code ArmorStand}), peintures, noeuds de laisse, et d'office
 * ({@code auto}) toute entité accrochée à un bloc et tout porte-armure d'un autre mod. Un joueur en survie ou en aventure :
 * <ul>
 *   <li>ne les frappe ni ne les casse (clic gauche, coups de mêlée de Tactique et couteaux : {@link AttackEntityEvent}) ;</li>
 *   <li>ne tourne ni ne prend l'objet d'un cadre, ne prend ni ne pose rien sur un porte-armure ou un mannequin, n'ouvre pas
 *   le menu du mannequin, ne détache pas une laisse (EntityInteract et EntityInteractSpecific) ;</li>
 *   <li>ne les touche pas avec ses projectiles (flèches, tridents, boules de neige, canne à pêche :
 *   {@link ProjectileImpactEvent}) ni avec les balles de TaCZ ({@code EntityHurtByGunEvent.Pre}, par réflexion) ;</li>
 *   <li>n'en pose pas hors d'une base de CRac ({@code poser}).</li>
 * </ul>
 * Aucune explosion (C4, grenades, TNT, balles explosives) ne les abîme ni ne casse le bloc qui porte un présentoir
 * accroché : ils sont retirés de la liste de l'explosion ({@link ExplosionEvent.Detonate}). Libres : créatif, spectateur,
 * staff (permission 2) ; dans un claim de CRac, les présentoirs sont libres par défaut ({@code permis_claims_crac}).
 */
final class Presentoirs {
    private Presentoirs() {
    }

    private static volatile Regles regles;

    record Regles(Set<EntityType<?>> types, Set<EntityType<?>> sauf, Set<Item> objets) {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Presentoirs::attaque);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Presentoirs::interaction);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Presentoirs::interactionPrecise);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Presentoirs::projectile);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, Presentoirs::explosion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Presentoirs::pose);
        ecouterBallesTacz();
    }

    static void invalider() {
        regles = null;
    }

    private static boolean actif() {
        return ConfigLoot.SPEC.isLoaded() && ConfigLoot.PRES_ACTIF.get();
    }

    /** Cette entité est-elle un présentoir protégé ? */
    static boolean protege(Entity e) {
        if (e == null || !actif()) return false;
        Regles r = regles();
        EntityType<?> t = e.getType();
        if (r.sauf().contains(t)) return false;
        if (r.types().contains(t)) return true;
        if (!ConfigLoot.PRES_AUTO.get()) return false;
        return e instanceof HangingEntity || e instanceof ArmorStand as && !as.isMarker();
    }

    /** Le joueur qui agit sur ce présentoir est-il arrêté ? (joueur libre, ou présentoir dans un claim permis : non) */
    private static boolean refuse(Entity cible, Entity acteur) {
        if (!(acteur instanceof ServerPlayer sp) || ConteneursVanilla.libre(sp) || !protege(cible)) return false;
        return !(ConfigLoot.PRES_CLAIMS.get() && cible.level() instanceof ServerLevel sl && ConteneursVanilla.dansClaimCrac(sl, cible.blockPosition()));
    }

    /** Coup venu d'autre chose qu'un joueur (créature, tireur inconnu) : refusé aussi, hors claim permis. */
    private static boolean refuseTireur(Entity cible, Entity tireur) {
        if (tireur instanceof ServerPlayer) return refuse(cible, tireur);
        if (!protege(cible)) return false;
        return !(ConfigLoot.PRES_CLAIMS.get() && cible.level() instanceof ServerLevel sl && ConteneursVanilla.dansClaimCrac(sl, cible.blockPosition()));
    }

    private static void attaque(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (refuse(e.getTarget(), e.getEntity())) {
            e.setCanceled(true);
            prevenir(e.getEntity());
        }
    }

    private static void interaction(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !refuse(e.getTarget(), e.getEntity())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(e.getEntity());
    }

    /** Porte-armure et mannequin : prendre, poser, menu du mannequin (interactAt). */
    private static void interactionPrecise(PlayerInteractEvent.EntityInteractSpecific e) {
        if (e.getLevel().isClientSide() || !refuse(e.getTarget(), e.getEntity())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(e.getEntity());
    }

    /** Flèches, tridents, boules de neige, hameçon : ils traversent le présentoir sans le toucher. */
    private static void projectile(ProjectileImpactEvent e) {
        Projectile p = e.getProjectile();
        if (p.level().isClientSide() || !(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (e.isCancelable() && refuseTireur(hit.getEntity(), p.getOwner())) e.setCanceled(true);
    }

    /** Aucune explosion n'abîme un présentoir ni ne casse le bloc qui porte un présentoir accroché. */
    private static void explosion(ExplosionEvent.Detonate e) {
        if (e.getLevel().isClientSide() || !actif()) return;
        List<Entity> touchees = e.getAffectedEntities();
        Set<BlockPos> supports = new HashSet<>();
        touchees.removeIf(en -> {
            if (!refuseTireur(en, null)) return false;
            if (en instanceof HangingEntity h) {
                BlockPos p = h.getPos();
                supports.add(p);
                if (!(h instanceof LeashFenceKnotEntity)) supports.add(p.relative(h.getDirection().getOpposite()));
            } else {
                supports.add(en.blockPosition().below());
            }
            return true;
        });
        if (!supports.isEmpty()) e.getAffectedBlocks().removeIf(supports::contains);
    }

    /** Poser un cadre, une peinture, un porte-armure ou un mannequin hors d'une base : refusé. */
    private static void pose(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer sp) || !actif() || !ConfigLoot.PRES_POSE.get()
                || ConteneursVanilla.libre(sp)) return;
        Item it = e.getItemStack().getItem();
        if (!(it instanceof HangingEntityItem || it instanceof ArmorStandItem || regles().objets().contains(it))) return;
        if (ConfigLoot.PRES_CLAIMS.get() && e.getLevel() instanceof ServerLevel sl) {
            BlockPos dest = e.getFace() == null ? e.getPos() : e.getPos().relative(e.getFace());
            if (ConteneursVanilla.dansClaimCrac(sl, e.getPos()) && ConteneursVanilla.dansClaimCrac(sl, dest)) return;
        }
        e.setUseItem(Event.Result.DENY);
        sp.containerMenu.broadcastFullState();
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp);
    }

    // ------------------------------------------------------------------ balles de TaCZ (facultatif, par réflexion)
    private static final String TACZ_TOUCHE = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void ecouterBallesTacz() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> c = Class.forName(TACZ_TOUCHE);
            Method touche = c.getMethod("getHurtEntity");
            Method tireur = c.getMethod("getAttacker");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class) c, ev -> {
                Event e = (Event) ev;
                try {
                    Entity cible = (Entity) touche.invoke(ev);
                    if (cible == null || cible.level().isClientSide()) return;
                    if (refuseTireur(cible, (Entity) tireur.invoke(ev)) && e.isCancelable()) e.setCanceled(true);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            });
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeralizLoot.LOG.warn("[presentoirs] évènement de tir de TaCZ introuvable : les balles abîment les présentoirs ({})", ex.toString());
        }
    }

    // ------------------------------------------------------------------ réglages
    private static void prevenir(net.minecraft.world.entity.player.Player p) {
        p.displayClientMessage(Component.translatableWithFallback("heraliz_loot.presentoir.protege", "Ce présentoir est protégé.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static Regles regles() {
        Regles r = regles;
        if (r == null) {
            Set<Item> objets = new HashSet<>();
            for (String brut : ConfigLoot.PRES_OBJETS.get()) {
                ResourceLocation rl = ResourceLocation.tryParse(brut.trim());
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) objets.add(BuiltInRegistries.ITEM.get(rl));
                else HeralizLoot.LOG.warn("[presentoirs] objet inconnu : {}", brut);
            }
            regles = r = new Regles(types(ConfigLoot.PRES_ENTITES.get()), types(ConfigLoot.PRES_EXCEPTIONS.get()), objets);
        }
        return r;
    }

    private static Set<EntityType<?>> types(List<? extends String> liste) {
        Set<EntityType<?>> s = new HashSet<>();
        for (String brut : liste) {
            ResourceLocation rl = ResourceLocation.tryParse(brut.trim());
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) s.add(BuiltInRegistries.ENTITY_TYPE.get(rl));
            else HeralizLoot.LOG.warn("[presentoirs] entité inconnue : {}", brut);
        }
        return s;
    }
}
