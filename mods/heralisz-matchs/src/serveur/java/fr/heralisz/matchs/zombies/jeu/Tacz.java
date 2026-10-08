package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Acces a TaCZ par reflexion tolerante (TaCZ absent = armes ignorees, jamais de plantage) : construction d'une arme
 * avec accessoires <b>verifies</b> (compatibilite de l'index TaCZ : type autorise par l'arme et etiquettes
 * {@code allow_attachments}) et reserve virtuelle ({@code DummyAmmo}/{@code MaxDummyAmmo}), lecture de l'id d'arme,
 * recharge complete, taille du chargeur (accessoires compris), type (pistol, rifle...), et ecoute des tirs
 * ({@code EntityHurtByGunEvent.Pre} : tete, degats de base modifiables) et des impacts sur les blocs
 * ({@code AmmoHitBlockEvent}). Fil serveur.
 */
final class Tacz {
    private Tacz() {
    }

    /**
     * Ecouteur d'une touche par balle, avant les degats : rend la nouvelle valeur des degats de base (ou {@code base}
     * pour ne rien changer).
     */
    interface Touche {
        float touche(Entity touchee, @Nullable LivingEntity tireur, @Nullable String gunId, boolean tete, float base, float multiplicateurTete);
    }

    /** Ecouteur d'un impact de balle sur un bloc (niveau, case touchee, face touchee, tireur ou null). */
    interface Impact {
        void impact(Level niveau, BlockPos pos, Direction face, @Nullable Entity tireur);
    }

    private static boolean charge;
    private static boolean present;
    private static Method mCreate, mSetId, mSetAmmo, mSetBarrel, mPutAtt, mBuild;
    private static Method mIGun, mGetGunId, mSetCurrent, mGetCurrent, mSetDummy, mGetDummy, mSetMaxDummy, mGetMaxDummy, mSetBullet;
    private static Method mAllowAtt, mAllowType, mGetAttId, mInstallAtt;
    private static Method mIndex, mAttIndex, mAttType, mGunData, mAmmoAmount, mType, mAmmoAvecAcc;
    private static Method mAttCreate, mAttSetId, mAttBuild, mMatch;
    private static Class<?> cAttType;
    private static final Map<String, Integer> CHARGEURS = new HashMap<>();
    private static final Map<String, String> TYPES = new HashMap<>();
    private static final Map<String, String> TYPES_ACC = new HashMap<>();

    private static synchronized void charger() {
        if (charge) return;
        charge = true;
        try {
            Class<?> b = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            cAttType = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            mCreate = b.getMethod("create");
            mSetId = b.getMethod("setId", ResourceLocation.class);
            mSetAmmo = b.getMethod("setAmmoCount", int.class);
            mSetBarrel = b.getMethod("setAmmoInBarrel", boolean.class);
            mPutAtt = b.getMethod("putAttachment", cAttType, ResourceLocation.class);
            mBuild = b.getMethod("build");
            Class<?> ig = Class.forName("com.tacz.guns.api.item.IGun");
            mIGun = ig.getMethod("getIGunOrNull", ItemStack.class);
            mGetGunId = ig.getMethod("getGunId", ItemStack.class);
            mSetCurrent = ig.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
            mGetCurrent = ig.getMethod("getCurrentAmmoCount", ItemStack.class);
            mSetDummy = ig.getMethod("setDummyAmmoAmount", ItemStack.class, int.class);
            mGetDummy = ig.getMethod("getDummyAmmoAmount", ItemStack.class);
            mSetMaxDummy = ig.getMethod("setMaxDummyAmmoAmount", ItemStack.class, int.class);
            mGetMaxDummy = ig.getMethod("getMaxDummyAmmoAmount", ItemStack.class);
            mSetBullet = ig.getMethod("setBulletInBarrel", ItemStack.class, boolean.class);
            mAllowAtt = ig.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
            mAllowType = ig.getMethod("allowAttachmentType", ItemStack.class, cAttType);
            mGetAttId = ig.getMethod("getAttachmentId", ItemStack.class, cAttType);
            mInstallAtt = ig.getMethod("installAttachment", ItemStack.class, ItemStack.class);
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            mIndex = api.getMethod("getCommonGunIndex", ResourceLocation.class);
            mAttIndex = api.getMethod("getCommonAttachmentIndex", ResourceLocation.class);
            mAttType = Class.forName("com.tacz.guns.resource.index.CommonAttachmentIndex").getMethod("getType");
            Class<?> idx = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            mGunData = idx.getMethod("getGunData");
            mType = idx.getMethod("getType");
            Class<?> gd = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            mAmmoAmount = gd.getMethod("getAmmoAmount");
            try {
                mAmmoAvecAcc = Class.forName("com.tacz.guns.util.AttachmentDataUtils").getMethod("getAmmoCountWithAttachment", ItemStack.class, gd);
            } catch (ReflectiveOperationException ex) {
                mAmmoAvecAcc = null;
            }
            Class<?> ab = Class.forName("com.tacz.guns.api.item.builder.AttachmentItemBuilder");
            mAttCreate = ab.getMethod("create");
            mAttSetId = ab.getMethod("setId", ResourceLocation.class);
            mAttBuild = ab.getMethod("build");
            try {
                mMatch = Class.forName("com.tacz.guns.util.AllowAttachmentTagMatcher").getMethod("match", ResourceLocation.class, ResourceLocation.class);
            } catch (ReflectiveOperationException ex) {
                mMatch = null;
            }
            present = true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            present = false;
            HeraliszMatchs.LOG.warn("[zombies] TaCZ introuvable : armes desactivees ({})", ex.toString());
        }
    }

    static boolean present() {
        charger();
        return present;
    }

    @Nullable
    private static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id);
    }

    @Nullable
    private static Object index(String gunId) {
        if (!present()) return null;
        ResourceLocation r = rl(gunId);
        if (r == null) return null;
        try {
            Object o = mIndex.invoke(null, r);
            return o instanceof Optional<?> op ? op.orElse(null) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static Object indexAcc(String id) {
        if (!present()) return null;
        ResourceLocation r = rl(id);
        if (r == null) return null;
        try {
            Object o = mAttIndex.invoke(null, r);
            return o instanceof Optional<?> op ? op.orElse(null) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** Vrai si l'arme existe dans les packs TaCZ charges. */
    static boolean existe(String gunId) {
        return index(gunId) != null;
    }

    /** Vrai si l'accessoire existe dans les packs TaCZ charges. */
    static boolean accessoireExiste(String id) {
        return indexAcc(id) != null;
    }

    /** Emplacement TaCZ de l'accessoire ({@code scope}, {@code muzzle}, {@code extended_mag}...), "" si inconnu. */
    static String typeAccessoire(String id) {
        String t = TYPES_ACC.get(id);
        if (t != null) return t;
        t = "";
        Object i = indexAcc(id);
        if (i != null) {
            try {
                Object o = mAttType.invoke(i);
                if (o != null) t = o.toString().toLowerCase(Locale.ROOT);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        TYPES_ACC.put(id, t);
        return t;
    }

    /** Taille du chargeur de base (30 par defaut si inconnue). */
    static int chargeur(String gunId) {
        Integer c = CHARGEURS.get(gunId);
        if (c != null) return c;
        int v = 30;
        Object i = index(gunId);
        if (i != null) {
            try {
                v = Math.max(1, (int) mAmmoAmount.invoke(mGunData.invoke(i)));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        CHARGEURS.put(gunId, v);
        return v;
    }

    /** Taille du chargeur de cette pile, accessoires (chargeur grande capacite) compris. */
    static int chargeur(ItemStack s) {
        String g = gunId(s);
        if (g == null) return 30;
        Object i = index(g);
        if (i != null && mAmmoAvecAcc != null) {
            try {
                return Math.max(1, (int) mAmmoAvecAcc.invoke(null, s, mGunData.invoke(i)));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return chargeur(g);
    }

    /** Type TaCZ de l'arme ("pistol", "rifle", "smg", "shotgun", "sniper", "mg", "rpg"...), "" si inconnu. */
    static String type(String gunId) {
        String t = TYPES.get(gunId);
        if (t != null) return t;
        t = "";
        Object i = index(gunId);
        if (i != null) {
            try {
                Object o = mType.invoke(i);
                if (o != null) t = o.toString();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        TYPES.put(gunId, t);
        return t;
    }

    /**
     * Construit l'arme chargee, avec {@code chargeursReserve} chargeurs en reserve virtuelle. Rend
     * {@link ItemStack#EMPTY} si TaCZ est absent ou l'arme inconnue.
     */
    static ItemStack arme(String gunId, int chargeursReserve) {
        return arme(gunId, Map.of(), chargeursReserve);
    }

    /**
     * Construit l'arme avec ses accessoires (emplacement -> id d'accessoire). Chaque accessoire est verifie contre
     * l'index TaCZ (existence, emplacement, {@code allow_attachments} de l'arme) : un accessoire incompatible est ignore
     * (journal). Chargeur plein (accessoires compris), balle chambree, reserve virtuelle, accessoires verrouilles.
     */
    static ItemStack arme(String gunId, Map<String, String> accessoires, int chargeursReserve) {
        if (!present() || !existe(gunId)) return ItemStack.EMPTY;
        try {
            Object b = mCreate.invoke(null);
            mSetId.invoke(b, rl(gunId));
            mSetAmmo.invoke(b, chargeur(gunId));
            mSetBarrel.invoke(b, true);
            ItemStack nu = (ItemStack) mBuild.invoke(b);
            if (nu == null || nu.isEmpty()) return ItemStack.EMPTY;
            if (!accessoires.isEmpty()) {
                Map<String, String> ok = compatibles(nu, gunId, accessoires);
                if (!ok.isEmpty()) {
                    Object b2 = mCreate.invoke(null);
                    mSetId.invoke(b2, rl(gunId));
                    mSetAmmo.invoke(b2, chargeur(gunId));
                    mSetBarrel.invoke(b2, true);
                    for (Map.Entry<String, String> e : ok.entrySet()) mPutAtt.invoke(b2, enumAcc(e.getKey()), rl(e.getValue()));
                    ItemStack avec = (ItemStack) mBuild.invoke(b2);
                    if (avec != null && !avec.isEmpty()) nu = avec;
                }
            }
            ItemStack s = nu;
            Object ig = mIGun.invoke(null, s);
            if (ig != null) {
                int mag = chargeur(s);
                mSetCurrent.invoke(ig, s, mag);
                int reserve = Math.max(0, chargeursReserve) * mag;
                mSetMaxDummy.invoke(ig, s, reserve);
                mSetDummy.invoke(ig, s, reserve);
            }
            s.getOrCreateTag().putBoolean("AttachmentLock", true);
            return s;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] arme {} impossible a construire : {}", gunId, ex.toString());
            return ItemStack.EMPTY;
        }
    }

    /**
     * Accessoires compatibles avec l'arme (pile nue de reference) : existe, emplacement annonce = emplacement de l'index,
     * et {@code IGun.allowAttachment} (type autorise + etiquettes {@code allow_attachments}). Ordre conserve.
     */
    static Map<String, String> compatibles(ItemStack armeNue, String gunId, Map<String, String> accessoires) {
        Map<String, String> ok = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : accessoires.entrySet()) {
            String emp = e.getKey().toLowerCase(Locale.ROOT), id = e.getValue();
            if (compatible(armeNue, gunId, id) && emp.equals(typeAccessoire(id))) ok.put(emp, id);
            else HeraliszMatchs.LOG.debug("[zombies] accessoire {} incompatible avec {} ({})", id, gunId, emp);
        }
        return ok;
    }

    /** Vrai si l'accessoire existe et peut se monter sur cette arme (verification de l'index TaCZ). */
    static boolean compatible(ItemStack armeNue, String gunId, String accessoire) {
        if (!present() || !accessoireExiste(accessoire)) return false;
        try {
            Object ig = mIGun.invoke(null, armeNue);
            if (ig == null) return false;
            ItemStack acc = pileAccessoire(accessoire);
            if (acc.isEmpty()) return false;
            // l'emplacement doit etre autorise par les donnees de l'arme (allow_attachment_types)...
            String type = typeAccessoire(accessoire);
            if (type.isEmpty() || !(boolean) mAllowType.invoke(ig, armeNue, enumAcc(type))) return false;
            // ... et l'accessoire par ses etiquettes (allow_attachments)
            if (!(boolean) mAllowAtt.invoke(ig, armeNue, acc)) return false;
            return mMatch == null || (boolean) mMatch.invoke(null, rl(gunId), rl(accessoire));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** Vrai si l'accessoire peut se monter sur cette arme (construit une pile nue de reference). */
    static boolean compatible(String gunId, String accessoire) {
        if (!present() || !existe(gunId)) return false;
        try {
            Object b = mCreate.invoke(null);
            mSetId.invoke(b, rl(gunId));
            ItemStack nu = (ItemStack) mBuild.invoke(b);
            return nu != null && !nu.isEmpty() && compatible(nu, gunId, accessoire);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static ItemStack pileAccessoire(String id) {
        try {
            Object b = mAttCreate.invoke(null);
            mAttSetId.invoke(b, rl(id));
            ItemStack s = (ItemStack) mAttBuild.invoke(b);
            return s == null ? ItemStack.EMPTY : s;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumAcc(String emplacement) {
        return Enum.valueOf((Class) cAttType, emplacement.toUpperCase(Locale.ROOT));
    }

    /** Id de l'accessoire monte a cet emplacement, ou null. */
    @Nullable
    static String accessoire(ItemStack arme, String emplacement) {
        if (!present() || gunId(arme) == null) return null;
        try {
            Object ig = mIGun.invoke(null, arme);
            Object r = mGetAttId.invoke(ig, arme, enumAcc(emplacement));
            if (r == null) return null;
            String s = r.toString();
            return s.isEmpty() || s.equals("tacz:empty") ? null : s;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Monte l'accessoire sur l'arme si c'est compatible (verrou des accessoires leve le temps du montage). Rend vrai si
     * c'est fait.
     */
    static boolean monter(ItemStack arme, String accessoire) {
        String g = gunId(arme);
        if (g == null || !compatible(g, accessoire)) return false;
        try {
            CompoundTag t = arme.getOrCreateTag();
            boolean verrou = t.getBoolean("AttachmentLock");
            t.putBoolean("AttachmentLock", false);
            Object ig = mIGun.invoke(null, arme);
            mInstallAtt.invoke(ig, arme, pileAccessoire(accessoire));
            t.putBoolean("AttachmentLock", verrou);
            return accessoire.equals(accessoire(arme, typeAccessoire(accessoire)));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] montage de {} sur {} impossible : {}", accessoire, g, ex.toString());
            return false;
        }
    }

    /** Id d'arme TaCZ de la pile, ou null si ce n'est pas une arme. */
    @Nullable
    static String gunId(ItemStack s) {
        if (s == null || s.isEmpty() || !present()) return null;
        try {
            Object ig = mIGun.invoke(null, s);
            if (ig == null) return null;
            Object r = mGetGunId.invoke(ig, s);
            return r == null ? null : r.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** Recharge completement l'arme (chargeur plein, reserve au maximum). Rend vrai si c'est une arme. */
    static boolean remplir(ItemStack s) {
        String g = gunId(s);
        if (g == null) return false;
        try {
            Object ig = mIGun.invoke(null, s);
            int mag = chargeur(s);
            int max = (int) mGetMaxDummy.invoke(ig, s);
            if (max <= 0) {
                max = mag * 4;
                mSetMaxDummy.invoke(ig, s, max);
            }
            mSetDummy.invoke(ig, s, max);
            mSetCurrent.invoke(ig, s, mag);
            mSetBullet.invoke(ig, s, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** Reserve virtuelle maximale de l'arme (balles), -1 si ce n'est pas une arme TaCZ. */
    static int reserveMax(ItemStack s) {
        if (gunId(s) == null) return -1;
        try {
            return (int) mGetMaxDummy.invoke(mIGun.invoke(null, s), s);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    /** Reserve virtuelle actuelle de l'arme (balles), -1 si ce n'est pas une arme TaCZ. */
    static int reserveActuelle(ItemStack s) {
        if (gunId(s) == null) return -1;
        try {
            return (int) mGetDummy.invoke(mIGun.invoke(null, s), s);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    static void reserve(ItemStack s, int chargeurs) {
        if (gunId(s) == null) return;
        try {
            Object ig = mIGun.invoke(null, s);
            int max = Math.max(0, chargeurs) * chargeur(s);
            mSetMaxDummy.invoke(ig, s, max);
            mSetDummy.invoke(ig, s, max);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /**
     * Rechargement « en coulisse » (atout Rechargement eclair) : complete le chargeur depuis la reserve virtuelle. Rend
     * vrai si quelque chose a change.
     */
    static boolean rechargerDepuisReserve(ItemStack s) {
        if (gunId(s) == null) return false;
        try {
            Object ig = mIGun.invoke(null, s);
            int mag = chargeur(s);
            int cur = (int) mGetCurrent.invoke(ig, s);
            int res = (int) mGetDummy.invoke(ig, s);
            if (cur >= mag || res <= 0) return false;
            int n = Math.min(mag - cur, res);
            mSetCurrent.invoke(ig, s, cur + n);
            mSetDummy.invoke(ig, s, res - n);
            mSetBullet.invoke(ig, s, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ evenements
    /** Branche l'ecoute des touches par balle (tete, degats de base). Sans effet si TaCZ est absent. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void ecouter(Touche ecouteur) {
        if (!present()) return;
        try {
            Class<?> c = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Method hurt = c.getMethod("getHurtEntity");
            Method att = c.getMethod("getAttacker");
            Method tete = c.getMethod("isHeadShot");
            Method side = c.getMethod("getLogicalSide");
            Method gun = c.getMethod("getGunId");
            Method base = c.getMethod("getBaseAmount");
            Method setBase = c.getMethod("setBaseAmount", float.class);
            Method multTete = c.getMethod("getHeadshotMultiplier");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class) c, e -> {
                try {
                    Object s = side.invoke(e);
                    if (s == null || !"SERVER".equals(s.toString())) return;
                    Entity h = (Entity) hurt.invoke(e);
                    if (h == null) return;
                    Object g = gun.invoke(e);
                    float b = (float) base.invoke(e);
                    float nb = ecouteur.touche(h, (LivingEntity) att.invoke(e), g == null ? null : g.toString(), (boolean) tete.invoke(e), b,
                            (float) multTete.invoke(e));
                    if (nb != b) setBase.invoke(e, nb);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            });
            HeraliszMatchs.LOG.info("[zombies] ecoute des tirs TaCZ branchee");
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[zombies] evenement de tir TaCZ introuvable : tirs a la tete non detectes ({})", ex.toString());
        }
    }

    /** Branche l'ecoute des impacts de balle sur les blocs (feu des balles incendiaires). Sans effet si TaCZ est absent. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void ecouterImpacts(Impact ecouteur) {
        if (!present()) return;
        try {
            Class<?> c = Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
            Method niveau = c.getMethod("getLevel");
            Method hit = c.getMethod("getHitResult");
            Method m0;
            try {
                m0 = c.getMethod("getAmmo");
            } catch (NoSuchMethodException ex) {
                m0 = null;
            }
            final Method balle = m0;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class) c, e -> {
                try {
                    Object l = niveau.invoke(e);
                    Object h = hit.invoke(e);
                    if (l instanceof Level lv && !lv.isClientSide && h instanceof BlockHitResult bh) {
                        Object b = balle == null ? null : balle.invoke(e);
                        Entity tireur = b instanceof net.minecraft.world.entity.projectile.Projectile pr ? pr.getOwner() : null;
                        ecouteur.impact(lv, bh.getBlockPos(), bh.getDirection(), tireur);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            });
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeraliszMatchs.LOG.debug("[zombies] evenement d'impact TaCZ introuvable ({})", ex.toString());
        }
    }
}
