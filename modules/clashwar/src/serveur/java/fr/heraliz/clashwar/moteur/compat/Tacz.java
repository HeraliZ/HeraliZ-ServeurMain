package fr.heraliz.clashwar.moteur.compat;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.Kit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Fabrique des objets de match par reflexion tolerante (MATCHS_SPEC §1.3) :
 * <ul>
 *     <li>armes TaCZ par {@code GunItemBuilder} (id verifie : arme inconnue = pile vide), chargeur plein + balle chambree,
 *     reserve virtuelle {@code DummyAmmo}/{@code MaxDummyAmmo}, accessoires verrouilles {@code AttachmentLock} ;</li>
 *     <li>soins, grenades et melee heralisz_tactical : {@code heralisz_tactical:consumable|throwable|melee} avec
 *     {@code ConsumableId|ThrowableId|MeleeWeaponId}, id verifie contre les index de heralisz_tactical ;</li>
 *     <li>tout autre id : objet du registre (absent = pile vide).</li>
 * </ul>
 * Jamais de camo, skin, sticker ni pendentif. Fil serveur.
 */
public final class Tacz {
    private Tacz() {
    }

    private static boolean pret, present;
    private static Method creer, setId, setAmmo, setBarrel, setMode, putAtt, build, gunIndex, attIndex, getGunData, ammoAvecAcc;
    private static Class<?> fireMode, attType;

    private static boolean init() {
        if (pret) return present;
        pret = true;
        try {
            Class<?> b = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            fireMode = Class.forName("com.tacz.guns.api.item.gun.FireMode");
            attType = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            creer = b.getMethod("create");
            setId = b.getMethod("setId", ResourceLocation.class);
            setAmmo = b.getMethod("setAmmoCount", int.class);
            setBarrel = b.getMethod("setAmmoInBarrel", boolean.class);
            setMode = b.getMethod("setFireMode", fireMode);
            putAtt = b.getMethod("putAttachment", attType, ResourceLocation.class);
            build = b.getMethod("build");
            gunIndex = api.getMethod("getCommonGunIndex", ResourceLocation.class);
            attIndex = api.getMethod("getCommonAttachmentIndex", ResourceLocation.class);
            getGunData = Class.forName("com.tacz.guns.resource.index.CommonGunIndex").getMethod("getGunData");
            try {
                ammoAvecAcc = Class.forName("com.tacz.guns.util.AttachmentDataUtils").getMethod("getAmmoCountWithAttachment",
                        ItemStack.class, Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData"));
            } catch (ReflectiveOperationException e) {
                ammoAvecAcc = null;
            }
            present = true;
        } catch (ClassNotFoundException e) {
            present = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            HeralizClashWar.LOG.warn("[matchs] TaCZ present mais illisible : {}", e.toString());
            present = false;
        }
        return present;
    }

    /** Vrai si TaCZ est utilisable. */
    public static boolean presente() {
        return init();
    }

    /** Vrai si l'arme existe dans les packs TaCZ installes. */
    public static boolean armeExiste(String gunId) {
        if (!init()) return false;
        ResourceLocation rl = ResourceLocation.tryParse(gunId);
        if (rl == null) return false;
        try {
            return ((Optional<?>) gunIndex.invoke(null, rl)).isPresent();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Arme TaCZ du kit (chargeur plein, balle chambree, reserve virtuelle, accessoires verrouilles), ou pile vide si
     * TaCZ est absent ou l'arme inconnue (avertissement). Les accessoires inconnus sont ignores (avertissement).
     */
    public static ItemStack arme(Kit.Arme a) {
        if (!init()) {
            HeralizClashWar.LOG.warn("[matchs] arme {} ignoree : TaCZ absent", a.gunId());
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(a.gunId());
        if (rl == null || !armeExiste(a.gunId())) {
            HeralizClashWar.LOG.warn("[matchs] arme inconnue ignoree : {}", a.gunId());
            return ItemStack.EMPTY;
        }
        try {
            Object b = creer.invoke(null);
            setId.invoke(b, rl);
            setBarrel.invoke(b, true);
            if (a.modeTir() != null) {
                try {
                    setMode.invoke(b, enumDe(fireMode, a.modeTir().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    HeralizClashWar.LOG.warn("[matchs] mode de tir inconnu {} pour {}", a.modeTir(), a.gunId());
                }
            }
            for (Map.Entry<String, String> e : a.accessoires().entrySet()) {
                ResourceLocation acc = ResourceLocation.tryParse(e.getValue());
                Object type;
                try {
                    type = enumDe(attType, e.getKey().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    HeralizClashWar.LOG.warn("[matchs] emplacement d'accessoire inconnu {} ({})", e.getKey(), a.gunId());
                    continue;
                }
                if (acc == null || !((Optional<?>) attIndex.invoke(null, acc)).isPresent()) {
                    HeralizClashWar.LOG.warn("[matchs] accessoire inconnu ignore : {} ({})", e.getValue(), a.gunId());
                    continue;
                }
                putAtt.invoke(b, type, acc);
            }
            ItemStack st = (ItemStack) build.invoke(b);
            if (st.isEmpty()) {
                HeralizClashWar.LOG.warn("[matchs] arme {} non construite", a.gunId());
                return ItemStack.EMPTY;
            }
            CompoundTag t = st.getOrCreateTag();
            t.putInt("GunCurrentAmmoCount", chargeur(rl, st));
            t.putInt("DummyAmmo", a.reserve());
            t.putInt("MaxDummyAmmo", a.reserve());
            t.putBoolean("AttachmentLock", true);
            return st;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizClashWar.LOG.warn("[matchs] arme {} : erreur TaCZ {}", a.gunId(), e.toString());
            return ItemStack.EMPTY;
        }
    }

    /** Capacite du chargeur (accessoires compris), 30 si illisible. */
    private static int chargeur(ResourceLocation rl, ItemStack st) {
        try {
            Optional<?> idx = (Optional<?>) gunIndex.invoke(null, rl);
            if (idx.isEmpty()) return 30;
            Object data = getGunData.invoke(idx.get());
            if (ammoAvecAcc != null) return Math.max(1, (Integer) ammoAvecAcc.invoke(null, st, data));
            return Math.max(1, (Integer) data.getClass().getMethod("getAmmoAmount").invoke(data));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 30;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumDe(Class<?> c, String nom) {
        return Enum.valueOf((Class) c, nom);
    }

    // ------------------------------------------------------------------ objets tactiques et simples
    /**
     * Objet d'un kit : {@code heralisz_heal:<id>} (soin), {@code heralisz_grenade:<id>} (grenade),
     * {@code heralisz_melee:<id>} (melee) sont des contenus heralisz_tactical ; tout autre id est un objet du registre.
     * Rend une pile vide (avertissement) si l'id est inconnu.
     */
    public static ItemStack objet(String id, int nombre) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            HeralizClashWar.LOG.warn("[matchs] objet invalide ignore : {}", id);
            return ItemStack.EMPTY;
        }
        String porteur = switch (rl.getNamespace()) {
            case "heralisz_heal" -> "consumable";
            case "heralisz_grenade" -> "throwable";
            case "heralisz_melee" -> "melee";
            default -> null;
        };
        if (porteur != null) return tactique(rl, porteur, nombre);
        Item it = ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            HeralizClashWar.LOG.warn("[matchs] objet inconnu ignore : {}", id);
            return ItemStack.EMPTY;
        }
        return new ItemStack(it, Math.max(1, Math.min(it.getMaxStackSize(), nombre)));
    }

    @Nullable
    private static Method indexTactique(String porteur) {
        try {
            Class<?> api = Class.forName("fr.heralisz.tactical.api.HeraliszTacticalAPI");
            return api.getMethod(switch (porteur) {
                case "consumable" -> "getConsumableIndex";
                case "throwable" -> "getThrowableIndex";
                default -> "getMeleeIndex";
            }, ItemStack.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static ItemStack tactique(ResourceLocation contenu, String porteur, int nombre) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("heralisz_tactical", porteur));
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            HeralizClashWar.LOG.warn("[matchs] {} ignore : heralisz_tactical absent", contenu);
            return ItemStack.EMPTY;
        }
        ItemStack st = new ItemStack(it, Math.max(1, Math.min(it.getMaxStackSize(), nombre)));
        String cle = switch (porteur) {
            case "consumable" -> "ConsumableId";
            case "throwable" -> "ThrowableId";
            default -> "MeleeWeaponId";
        };
        st.getOrCreateTag().putString(cle, contenu.toString());
        Method idx = indexTactique(porteur);
        if (idx != null) {
            try {
                if (!((Optional<?>) idx.invoke(null, st)).isPresent()) {
                    HeralizClashWar.LOG.warn("[matchs] contenu heralisz_tactical inconnu ignore : {}", contenu);
                    return ItemStack.EMPTY;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // index illisible : on garde l'objet (le mod l'affichera vide au pire)
            }
        }
        return st;
    }
}
