package fr.heraliz.corps.mort;

import fr.heraliz.corps.CorpsConfig;
import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.compat.ReanimationCompat;
import fr.heraliz.corps.entite.CorpsContenu;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.entite.CorpsEntityServeur;
import fr.heraliz.corps.registre.Registre;
import fr.heraliz.corps.registre.Registres;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La mort d'un joueur devient un corps.
 * <ol>
 *     <li>{@code LivingDeathEvent} LOWEST (mort non annulee : ni mise a terre Reanimation, ni match) : photo de ce que
 *     porte le joueur ({@link Instantane}) et cause de la mort ;</li>
 *     <li>{@code LivingDropsEvent} LOWEST (apres Curios qui ajoute ses objets en HIGHEST) : chaque objet tombe est range
 *     a sa place sur le corps, puis la liste des objets au sol est videe. Les memes objets passent du joueur au corps :
 *     rien n'est copie, rien n'est perdu. Si le corps ne peut pas etre cree (erreur, monde qui refuse l'entite), les objets
 *     tombent normalement au sol.</li>
 * </ol>
 * Pas de corps : joueur en match (drapeau {@code heralisz:en_match}), dimension exclue (arenes et zombies des matchs),
 * spectateur, faux joueur, mod desactive. Sous Mohist, l'ordre est le meme : {@code die} poste {@code LivingDeathEvent},
 * puis {@code dropAllDeathLoot} poste {@code LivingDropsEvent}.
 */
public final class CaptureMort {
    private CaptureMort() {
    }

    /** Drapeau de HeraliZ Matchs dans {@code persistentData} d'un joueur en match. */
    public static final String DRAPEAU_EN_MATCH = "heralisz:en_match";

    /** Photos en attente de la chute des objets (meme tick ; videes en fin de tick si rien n'est tombe). */
    private static final Map<UUID, Instantane> INSTANTANES = new HashMap<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CaptureMort::mort);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CaptureMort::chute);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && !INSTANTANES.isEmpty()) INSTANTANES.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> INSTANTANES.clear());
    }

    /** Vrai si la mort de ce joueur laisse un corps. */
    public static boolean eligible(ServerPlayer p) {
        if (p instanceof FakePlayer || p.isSpectator() || p.getTags().contains("heraliz_bot")) return false;
        if (!CorpsConfig.ACTIF.get()) return false;
        if (p.getPersistentData().contains(DRAPEAU_EN_MATCH)) return false;
        return !CorpsConfig.dimensionExclue(p.level().dimension().location().toString());
    }

    private static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        INSTANTANES.remove(p.getUUID());
        if (!eligible(p)) return;
        try {
            INSTANTANES.put(p.getUUID(), Instantane.prendre(p, e.getSource()));
        } catch (RuntimeException ex) {
            HeralizCorps.LOG.error("[corps] photo de la mort de {} impossible : objets au sol", p.getGameProfile().getName(), ex);
        }
    }

    private static void chute(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        Instantane s = INSTANTANES.remove(p.getUUID());
        if (s == null) return;
        Collection<ItemEntity> tombes = e.getDrops();
        try {
            if (creerCorps(p, s, tombes)) tombes.clear();
        } catch (RuntimeException ex) {
            HeralizCorps.LOG.error("[corps] corps de {} impossible : objets laisses au sol", p.getGameProfile().getName(), ex);
        }
    }

    /** Range les objets tombes sur un nouveau corps et l'ajoute au monde. Vrai si le corps existe (objets a retirer du sol). */
    static boolean creerCorps(ServerPlayer p, Instantane s, Collection<ItemEntity> tombes) {
        ServerLevel level = p.serverLevel();
        ItemStack[] fixes = new ItemStack[CorpsContenu.CURIOS];
        ItemStack[] curios = new ItemStack[s.curiosIds.size()];
        List<ItemStack> autres = new ArrayList<>();
        int total = 0;
        for (ItemEntity ie : tombes) {
            if (ie == null) continue;
            ItemStack st = ie.getItem();
            if (st.isEmpty()) continue;
            total++;
            Integer place = s.retrouver(st);
            if (place == null) {
                autres.add(st);
            } else if (place < CorpsContenu.CURIOS) {
                if (fixes[place] == null) fixes[place] = st;
                else autres.add(st);
            } else {
                int k = place - CorpsContenu.CURIOS;
                if (k < curios.length && curios[k] == null) curios[k] = st;
                else autres.add(st);
            }
        }
        // objet tenu au curseur et grille d'artisanat 2x2 : ils tomberaient au sol plus tard ; ils vont aussi sur le corps
        List<Runnable> rendre = new ArrayList<>();
        AbstractContainerMenu menu = p.containerMenu;
        ItemStack curseur = menu.getCarried();
        if (!curseur.isEmpty()) {
            autres.add(curseur);
            menu.setCarried(ItemStack.EMPTY);
            rendre.add(() -> menu.setCarried(curseur));
            total++;
        }
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grille.getContainerSize(); i++) {
            ItemStack g = grille.getItem(i);
            if (g.isEmpty()) continue;
            grille.removeItemNoUpdate(i);
            autres.add(g);
            int j = i;
            rendre.add(() -> grille.setItem(j, g));
            total++;
        }
        if (total == 0) return false;
        try {
            if (poser(p, s, level, fixes, curios, autres, total)) return true;
        } catch (RuntimeException ex) {
            rendre.forEach(Runnable::run);
            throw ex;
        }
        rendre.forEach(Runnable::run);
        return false;
    }

    private static boolean poser(ServerPlayer p, Instantane s, ServerLevel level, ItemStack[] fixes, ItemStack[] curios, List<ItemStack> autres,
                                 int total) {
        int nbCurios = 0;
        for (ItemStack c : curios) if (c != null) nbCurios++;
        CorpsContenu contenu = new CorpsContenu(nbCurios, autres.size());
        for (int i = 0; i < fixes.length; i++) if (fixes[i] != null) contenu.poser(i, fixes[i]);
        int k = 0;
        for (int i = 0; i < curios.length; i++) {
            if (curios[i] == null) continue;
            contenu.definirIdCurios(k, s.curiosIds.get(i));
            contenu.poser(CorpsContenu.CURIOS + k, curios[i]);
            k++;
        }
        for (int i = 0; i < autres.size(); i++) contenu.poser(contenu.debutAutres() + i, autres.get(i));

        if (!(Registres.CORPS.get().create(level) instanceof CorpsEntityServeur corps)) return false;
        double x = p.getX(), y = Math.max(p.getY(), level.getMinBuildHeight() + 1), z = p.getZ();
        corps.moveTo(x, y, z, p.yBodyRot, 0);
        // Mort dans un bloc (étouffement, porte…) : seul le MILIEU du corps compte (un corps qui touche un mur ou un meuble
        // à côté reste où il est), et il ne remonte que d'un bloc au plus. Avant, toute la largeur (1,2) comptait et le
        // corps montait jusqu'à 4 blocs : il finissait sur le toit, à l'étage ou pris dans un plafond (« disparu »).
        // Milieu = carré de 0,3 bloc au centre (la boîte du corps suit maintenant sa longueur : elle ne sert pas ici).
        net.minecraft.world.phys.AABB milieu = new net.minecraft.world.phys.AABB(x - 0.15, corps.getY(), z - 0.15, x + 0.15,
                corps.getY() + corps.getBbHeight(), z + 0.15);
        if (level.getBlockCollisions(corps, milieu).iterator().hasNext()) {
            double haut = Math.floor(y) + 1;
            if (!level.getBlockCollisions(corps, milieu.move(0, haut - corps.getY(), 0)).iterator().hasNext()) {
                corps.setPos(x, haut, z);
            }
        }
        horsDesMurs(level, corps);
        corps.preparer(p, contenu, s.cause, s.execution ? ReanimationCompat.SCENE_APRES_COUP : 0);

        plafonds(level, corps);
        if (!level.addFreshEntity(corps)) {
            HeralizCorps.LOG.warn("[corps] le monde a refuse le corps de {} : objets laisses au sol", p.getGameProfile().getName());
            return false;
        }
        Registre.Fiche f = Registre.de(level.getServer()).ajouter(corps);
        HeralizCorps.LOG.info("[corps] {} mort en {} {} {} {} : corps n{} ({} objet(s) dont {} accessoire(s), {} autre(s), sac {})",
                p.getGameProfile().getName(), level.dimension().location(), (int) Math.floor(corps.getX()), (int) Math.floor(corps.getY()),
                (int) Math.floor(corps.getZ()), f.numero, total, nbCurios, autres.size(), fixes[CorpsContenu.SAC] != null ? "oui" : "non");
        return true;
    }

    /** Pas des décalages essayés sur chaque axe (blocs), en plus du milieu du bloc (voir decalages). */
    private static final double[] PAS = {0.0, 0.25, -0.25, 0.5, -0.5};

    /**
     * Décalages essayés pour un corps en (x, z), du plus petit au plus grand : les pas de {@link #PAS} et, sur chaque axe,
     * le milieu du bloc (04/11, relecture : dans une embrasure ou un couloir d'un bloc de large, le corps ne tient qu'en son
     * milieu, à 0,02 près). Jamais plus d'un demi-bloc sur chaque axe (la demi-largeur du corps) : sa boîte couvre toujours
     * le point de mort.
     */
    private static double[][] decalages(double x, double z) {
        double mx = Math.floor(x) + 0.5 - x, mz = Math.floor(z) + 0.5 - z;
        double[] ax = java.util.Arrays.copyOf(PAS, PAS.length + 1), az = java.util.Arrays.copyOf(PAS, PAS.length + 1);
        ax[PAS.length] = mx;
        az[PAS.length] = mz;
        List<double[]> l = new ArrayList<>();
        for (double dx : ax) for (double dz : az) l.add(new double[]{dx, dz});
        l.sort((a, b) -> Double.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        return l.toArray(new double[0][]);
    }

    /**
     * 04/11 (retour du propriétaire : « que les cadavres ne dépassent pas des murs, pour ne pas les fouiller à travers ») :
     * le corps couché (1 x 1,9 bloc) ne doit entrer dans aucun bloc. On garde la position et le sens du mort s'ils vont ; sinon
     * on essaie le corps droit (nord-sud ou est-ouest, au plus près du sens du mort), puis tourné d'un quart, en le poussant
     * d'un demi-bloc au plus (milieu du bloc compris). Un demi-bloc est la demi-largeur du corps : sa boîte couvre toujours
     * le point de mort, il ne peut donc jamais passer de l'autre côté d'un mur, d'une porte ou d'une vitre. Rien ne va : il
     * reste au point de mort, mais droit (un corps en biais dépasse plus), et la fouille demande de toute façon de le voir
     * (Fouille.visible, qui ne compte que la partie du corps du même côté que son milieu).
     */
    private static void horsDesMurs(ServerLevel level, CorpsEntity corps) {
        double x = corps.getX(), y = corps.getY(), z = corps.getZ();
        float lacet = corps.getYRot();
        if (libre(level, corps)) return;
        float droit = Math.round(lacet / 90.0F) * 90.0F;
        double[][] decalages = decalages(x, z);
        for (float essai : new float[]{droit, droit + 90.0F}) {
            for (double[] d : decalages) {
                corps.moveTo(x + d[0], y, z + d[1], essai, 0);
                if (libre(level, corps)) return;
            }
        }
        corps.moveTo(x, y, z, droit, 0);
    }

    /**
     * La boîte du corps n'entre dans aucun bloc (un peu rentrée : poser sur le sol ou toucher un mur est permis ; ses 0,15
     * premiers blocs de haut ne comptent pas : un tapis, une couche de neige ou une marche à côté ne déplace pas le corps).
     */
    private static boolean libre(ServerLevel level, CorpsEntity corps) {
        net.minecraft.world.phys.AABB b = corps.getBoundingBox().deflate(0.02);
        return !level.getBlockCollisions(corps, new net.minecraft.world.phys.AABB(b.minX, b.minY + 0.15, b.minZ, b.maxX, b.maxY, b.maxZ))
                .iterator().hasNext();
    }

    /** Filets anti-lag : trop de corps dans ce chunk ou sur le serveur -> le plus ancien disparait (journalise). */
    private static void plafonds(ServerLevel level, CorpsEntity nouveau) {
        ChunkPos cp = new ChunkPos(nouveau.blockPosition());
        AABB zone = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX() + 1, level.getMaxBuildHeight(),
                cp.getMaxBlockZ() + 1);
        List<CorpsEntity> ici = level.getEntities(Registres.CORPS.get(), zone, c -> !c.isRemoved());
        int max = CorpsConfig.MAX_PAR_CHUNK.get();
        while (ici.size() >= max) {
            CorpsEntityServeur vieux = null;
            for (CorpsEntity c : ici) if (c instanceof CorpsEntityServeur s && (vieux == null || s.creeLe() < vieux.creeLe())) vieux = s;
            if (vieux == null) break;
            vieux.disparaitre("plus de " + max + " corps dans ce chunk");
            ici.remove(vieux);
        }
        MinecraftServer serveur = level.getServer();
        Registre r = Registre.de(serveur);
        r.purger(level.getGameTime());
        int maxMonde = CorpsConfig.MAX_PAR_MONDE.get();
        int garde = 0;
        while (r.nbActifs() >= maxMonde && garde++ < 64) {
            Registre.Fiche f = r.plusAncienne(null);
            if (f == null) break;
            Entity e = null;
            ServerLevel ou = serveur.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(f.dimension)));
            if (ou != null) e = ou.getEntity(f.entite);
            if (e instanceof CorpsEntityServeur c) c.disparaitre("plus de " + maxMonde + " corps sur le serveur");
            else {
                r.programmer(f, Registre.Action.SUPPRIMER);
                HeralizCorps.LOG.info("[corps] corps n{} de {} (chunk non charge) sera retire : plus de {} corps sur le serveur", f.numero, f.nom,
                        maxMonde);
            }
        }
    }

    /** Pour le banc d'essai : niveau d'une fiche. */
    public static ServerLevel niveau(MinecraftServer serveur, String dimension) {
        ServerLevel l = serveur.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension)));
        return l == null ? serveur.getLevel(Level.OVERWORLD) : l;
    }
}
