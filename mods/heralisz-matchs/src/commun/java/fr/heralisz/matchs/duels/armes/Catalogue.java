package fr.heralisz.matchs.duels.armes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Armurerie des duels (MATCHS_SPEC §3, classes personnalisees) : tout ce qu'un joueur peut mettre dans une classe, lu
 * au demarrage du serveur dans les index reels de TaCZ, heralisz_tactical et HeraliZ Armurerie ({@link Armurerie}).
 * Immuable, partage serveur / client (envoye au client a l'ouverture de l'ecran CLASSES, voir {@code PaquetsDuels}).
 * <p>Aucun skin : les armes et armes de melee de l'espace de noms {@code heralisz_skins} sont exclues a la lecture, les
 * lanceurs et armes a balles explosives aussi.
 *
 * @param version     empreinte du contenu (le client garde la derniere recue)
 * @param armes       armes a feu (principales : toutes ; secondaires : celles de type {@link #TYPE_PISTOLET})
 * @param accessoires tous les accessoires utilisables (non caches)
 * @param melees      armes de melee (index heralisz_tactical)
 * @param soins       consommables de soin
 * @param letales     grenades letales (explosives, collantes, nuage)
 * @param tactiques   grenades tactiques (fumigene, etourdissante)
 * @param tenues      tenues completes de HeraliZ Armurerie (id = chemin de l'objet {@code heraliz_armory:kit_<ensemble>},
 *                    sans espace de noms : {@code kit_siberie})
 */
public final class Catalogue {
    /** Tenue par défaut des kits (reprise par moteur/compat/Armures, serveur). */
    public static final String TENUE_DEFAUT = "kit_siberie";

    /** Type TaCZ des armes de poing (seules armes secondaires). */
    public static final String TYPE_PISTOLET = "pistol";
    /** Emplacements d'accessoires TaCZ (minuscules, ordre d'affichage). */
    public static final List<String> EMPLACEMENTS = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");
    /** Catalogue vide (TaCZ absent, ou avant la premiere lecture). */
    public static final Catalogue VIDE = new Catalogue(0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    /**
     * Arme a feu.
     *
     * @param id          id TaCZ ("tacz:ak47")
     * @param nom         cle de traduction du nom (pack d'armes, resolue cote client)
     * @param type        type TaCZ (pistol, rifle, smg, shotgun, sniper, mg...)
     * @param icone       pile d'affichage (arme TaCZ portant son GunId), vide si inconnue
     * @param rpm         cadence (coups par minute)
     * @param degats      degats d'une balle
     * @param chargeur    capacite du chargeur
     * @param accessoires indices dans {@link Catalogue#accessoires()} des accessoires autorises pour cette arme
     */
    public record Arme(String id, String nom, String type, ItemStack icone, int rpm, float degats, int chargeur, List<Integer> accessoires) {
        public Arme {
            accessoires = List.copyOf(accessoires);
        }

        public boolean pistolet() {
            return TYPE_PISTOLET.equals(type);
        }
    }

    /** Accessoire TaCZ : id, cle de nom, emplacement ({@link #EMPLACEMENTS}), pile d'affichage. */
    public record Accessoire(String id, String nom, String emplacement, ItemStack icone) {
    }

    /** Objet simple (melee, soin, grenade, tenue) : id, cle de nom, pile d'affichage. */
    public record Objet(String id, String nom, ItemStack icone) {
    }

    private final int version;
    private final List<Arme> armes;
    private final List<Accessoire> accessoires;
    private final List<Objet> melees, soins, letales, tactiques, tenues;
    private final Map<String, Arme> parId = new HashMap<>();

    public Catalogue(int version, List<Arme> armes, List<Accessoire> accessoires, List<Objet> melees, List<Objet> soins,
                     List<Objet> letales, List<Objet> tactiques, List<Objet> tenues) {
        this.version = version;
        this.armes = List.copyOf(armes);
        this.accessoires = List.copyOf(accessoires);
        this.melees = List.copyOf(melees);
        this.soins = List.copyOf(soins);
        this.letales = List.copyOf(letales);
        this.tactiques = List.copyOf(tactiques);
        this.tenues = List.copyOf(tenues);
        for (Arme a : this.armes) parId.put(a.id(), a);
    }

    public int version() {
        return version;
    }

    public List<Arme> armes() {
        return armes;
    }

    public List<Accessoire> accessoires() {
        return accessoires;
    }

    public List<Objet> melees() {
        return melees;
    }

    public List<Objet> soins() {
        return soins;
    }

    public List<Objet> letales() {
        return letales;
    }

    public List<Objet> tactiques() {
        return tactiques;
    }

    public List<Objet> tenues() {
        return tenues;
    }

    // ------------------------------------------------------------------ recherches
    /** Arme par id, ou null. */
    @Nullable
    public Arme arme(String id) {
        return id == null || id.isEmpty() ? null : parId.get(id);
    }

    /** Vrai si l'id est une arme principale permise (toute arme du catalogue). */
    public boolean principale(String id) {
        return arme(id) != null;
    }

    /** Vrai si l'id est une arme secondaire permise (arme de poing). */
    public boolean secondaire(String id) {
        Arme a = arme(id);
        return a != null && a.pistolet();
    }

    /** Accessoires autorises pour cette arme dans cet emplacement (nouvelle liste, ordre du catalogue). */
    public List<Accessoire> accessoiresDe(String gunId, String emplacement) {
        Arme a = arme(gunId);
        List<Accessoire> l = new ArrayList<>();
        if (a == null) return l;
        for (int i : a.accessoires()) {
            if (i < 0 || i >= accessoires.size()) continue;
            Accessoire ac = accessoires.get(i);
            if (ac.emplacement().equals(emplacement)) l.add(ac);
        }
        return l;
    }

    /** Vrai si l'accessoire est permis sur cette arme a cet emplacement. */
    public boolean accessoirePermis(String gunId, String emplacement, String accessoireId) {
        Arme a = arme(gunId);
        if (a == null) return false;
        for (int i : a.accessoires()) {
            if (i < 0 || i >= accessoires.size()) continue;
            Accessoire ac = accessoires.get(i);
            if (ac.emplacement().equals(emplacement) && ac.id().equals(accessoireId)) return true;
        }
        return false;
    }

    /** Accessoire par id, ou null. */
    @Nullable
    public Accessoire accessoire(String id) {
        for (Accessoire a : accessoires) if (a.id().equals(id)) return a;
        return null;
    }

    /**
     * Tenue par defaut d'une classe : Siberie ({@link Armures#TENUE_DEFAUT}) si elle est au catalogue, sinon la premiere
     * tenue, sinon "" (aucune). Sert aussi de repli pour une tenue sauvegardee qui n'existe plus (ancien kit heralisz_armor).
     */
    public String tenueParDefaut() {
        if (chercher(tenues, TENUE_DEFAUT) != null) return TENUE_DEFAUT;
        return tenues.isEmpty() ? "" : tenues.get(0).id();
    }

    /** Objet d'une liste par id, ou null. */
    @Nullable
    public static Objet chercher(List<Objet> l, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Objet o : l) if (o.id().equals(id)) return o;
        return null;
    }

    public boolean vide() {
        return armes.isEmpty();
    }

    // ------------------------------------------------------------------ reseau
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeVarInt(armes.size());
        for (Arme a : armes) {
            buf.writeUtf(a.id(), 128);
            buf.writeUtf(a.nom(), 160);
            buf.writeUtf(a.type(), 32);
            buf.writeItem(a.icone());
            buf.writeVarInt(a.rpm());
            buf.writeFloat(a.degats());
            buf.writeVarInt(a.chargeur());
            buf.writeVarInt(a.accessoires().size());
            for (int i : a.accessoires()) buf.writeVarInt(i);
        }
        buf.writeVarInt(accessoires.size());
        for (Accessoire a : accessoires) {
            buf.writeUtf(a.id(), 128);
            buf.writeUtf(a.nom(), 160);
            buf.writeUtf(a.emplacement(), 32);
            buf.writeItem(a.icone());
        }
        ecrire(buf, melees);
        ecrire(buf, soins);
        ecrire(buf, letales);
        ecrire(buf, tactiques);
        ecrire(buf, tenues);
    }

    public static Catalogue read(FriendlyByteBuf buf) {
        int version = buf.readVarInt();
        int na = Math.min(2048, buf.readVarInt());
        List<Arme> armes = new ArrayList<>(na);
        for (int i = 0; i < na; i++) {
            String id = buf.readUtf(128), nom = buf.readUtf(160), type = buf.readUtf(32);
            ItemStack icone = buf.readItem();
            int rpm = buf.readVarInt();
            float degats = buf.readFloat();
            int chargeur = buf.readVarInt();
            int nacc = Math.min(4096, buf.readVarInt());
            List<Integer> acc = new ArrayList<>(nacc);
            for (int k = 0; k < nacc; k++) acc.add(buf.readVarInt());
            armes.add(new Arme(id, nom, type, icone, rpm, degats, chargeur, acc));
        }
        int nc = Math.min(4096, buf.readVarInt());
        List<Accessoire> accessoires = new ArrayList<>(nc);
        for (int i = 0; i < nc; i++) accessoires.add(new Accessoire(buf.readUtf(128), buf.readUtf(160), buf.readUtf(32), buf.readItem()));
        return new Catalogue(version, armes, accessoires, lire(buf), lire(buf), lire(buf), lire(buf), lire(buf));
    }

    private static void ecrire(FriendlyByteBuf buf, List<Objet> l) {
        buf.writeVarInt(l.size());
        for (Objet o : l) {
            buf.writeUtf(o.id(), 128);
            buf.writeUtf(o.nom(), 160);
            buf.writeItem(o.icone());
        }
    }

    private static List<Objet> lire(FriendlyByteBuf buf) {
        int n = Math.min(1024, buf.readVarInt());
        List<Objet> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Objet(buf.readUtf(128), buf.readUtf(160), buf.readItem()));
        return l;
    }

    /** Catalogue de démonstration (écrans du client sans serveur, banc) : corps d'origine de Armurerie.factice. */
    public static Catalogue factice() {
        List<Accessoire> acc = List.of(
                new Accessoire("tacz:sight_rmr_dot", "RMR", "scope", ItemStack.EMPTY),
                new Accessoire("tacz:scope_acog_ta31", "ACOG", "scope", ItemStack.EMPTY),
                new Accessoire("tacz:muzzle_silencer_mirage", "Silencieux", "muzzle", ItemStack.EMPTY),
                new Accessoire("tacz:muzzle_compensator_trident", "Compensateur", "muzzle", ItemStack.EMPTY),
                new Accessoire("tacz:grip_vertical_ranger", "Poignee verticale", "grip", ItemStack.EMPTY),
                new Accessoire("tacz:stock_militech_b5", "Crosse B5", "stock", ItemStack.EMPTY),
                new Accessoire("tacz:light_laser_ptl", "Laser", "laser", ItemStack.EMPTY),
                new Accessoire("tacz:extended_mag_1", "Chargeur etendu", "extended_mag", ItemStack.EMPTY));
        List<Integer> tous = List.of(0, 1, 2, 3, 4, 5, 6, 7), poing = List.of(0, 2, 6);
        List<Arme> armes = List.of(
                new Arme("tacz:ak47", "AK-47", "rifle", ItemStack.EMPTY, 600, 6.5f, 30, tous),
                new Arme("tacz:hk416d", "HK416D", "rifle", ItemStack.EMPTY, 800, 5.5f, 30, tous),
                new Arme("tacz:aug", "AUG", "rifle", ItemStack.EMPTY, 700, 5.5f, 30, tous),
                new Arme("tacz:vector45", "Vector", "smg", ItemStack.EMPTY, 1100, 4.5f, 25, tous),
                new Arme("tacz:hk_mp5a5", "MP5", "smg", ItemStack.EMPTY, 800, 4.5f, 30, tous),
                new Arme("tacz:m870", "M870", "shotgun", ItemStack.EMPTY, 70, 3f, 6, List.of(0, 6)),
                new Arme("tacz:ai_awp", "AWP", "sniper", ItemStack.EMPTY, 40, 22f, 5, List.of(1, 2, 5)),
                new Arme("tacz:m249", "M249", "mg", ItemStack.EMPTY, 750, 5.5f, 100, List.of(0, 1, 4)),
                new Arme("tacz:glock_17", "Glock 17", "pistol", ItemStack.EMPTY, 450, 4.5f, 17, poing),
                new Arme("tacz:deagle", "Desert Eagle", "pistol", ItemStack.EMPTY, 200, 9f, 7, poing));
        return new Catalogue(424242, armes, acc,
                List.of(new Objet("heralisz_tactical:knife", "Couteau", ItemStack.EMPTY),
                        new Objet("heralisz_tactical:machete", "Machette", ItemStack.EMPTY)),
                List.of(new Objet("heralisz_tactical:bandage", "Pansement", ItemStack.EMPTY),
                        new Objet("heralisz_tactical:blood_bag", "Poche de sang", ItemStack.EMPTY),
                        new Objet("heralisz_tactical:stim", "Stimulant", ItemStack.EMPTY)),
                List.of(new Objet("heralisz_tactical:frag", "Grenade a fragmentation", ItemStack.EMPTY),
                        new Objet("heralisz_tactical:c4", "Grenade collante", ItemStack.EMPTY)),
                List.of(new Objet("heralisz_tactical:smoke", "Fumigene", ItemStack.EMPTY),
                        new Objet("heralisz_tactical:flash", "Grenade aveuglante", ItemStack.EMPTY)),
                List.of(new Objet("kit_citadin", "Citadin", ItemStack.EMPTY),
                        new Objet(TENUE_DEFAUT, "Sibérie", ItemStack.EMPTY),
                        new Objet("kit_titan", "Titan", ItemStack.EMPTY)));
    }
}
