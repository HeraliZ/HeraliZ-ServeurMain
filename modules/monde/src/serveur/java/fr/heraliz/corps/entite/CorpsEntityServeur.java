package fr.heraliz.corps.entite;

import fr.heraliz.corps.CorpsConfig;
import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.compat.ClansCompat;
import fr.heraliz.corps.menu.CorpsMenu;
import fr.heraliz.corps.menu.Fouille;
import fr.heraliz.corps.registre.Registre;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link CorpsEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new CorpsEntity(...) » côté serveur.
 * <p>Contenu (n'existe que cote serveur), numero, dates, chute ; creation (preparer), regles (protection), minuteur et appui
 * au sol (tick), fouille, retrait du registre, sauvegarde. Les methodes d'origine sont reprises telles quelles.
 */
public class CorpsEntityServeur extends CorpsEntity {
    public CorpsEntityServeur(EntityType<? extends CorpsEntity> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ serveur
    @Nullable
    private CorpsContenu contenu;
    private int numero;
    private long creeLeMs;
    private long creeLe;
    private boolean enChute = true;
    private final net.minecraft.core.BlockPos.MutableBlockPos dessous = new net.minecraft.core.BlockPos.MutableBlockPos();
    private boolean premierTick = true;

    // ------------------------------------------------------------------ creation (serveur)
    /**
     * Prepare un nouveau corps pour ce joueur (pas encore ajoute au monde). {@code visibleApres} : ticks pendant lesquels
     * le corps reste invisible (la scene d'execution de Reanimation montre encore la victime).
     */
    public void preparer(ServerPlayer mort, CorpsContenu contenu, Component cause, int visibleApres) {
        long maintenant = level().getGameTime();
        this.proprietaire = mort.getUUID();
        this.nom = mort.getGameProfile().getName();
        this.cause = cause;
        this.creeLe = maintenant;
        this.creeLeMs = System.currentTimeMillis();
        this.protegeJusqua = maintenant + CorpsConfig.PROTECTION_S.get() * 20L;
        this.visibleDes = maintenant + Math.max(0, visibleApres);
        this.clan = ClansCompat.clanDe(mort.getUUID());
        this.apparence = apparenceCaraCrea(mort);
        if (this.apparence == null) {
            var p = mort.getGameProfile().getProperties().get("textures");
            for (var prop : p) {
                if (prop.getValue() != null && prop.getValue().length() < 8000) {
                    texturesValeur = prop.getValue();
                    texturesSignature = prop.getSignature() == null || prop.getSignature().length() > 8000 ? "" : prop.getSignature();
                }
                break;
            }
        }
        this.contenu = contenu;
        contenu.surChangement(this::contenuChange);
        this.entityData.set(DATA_EXPIRE, maintenant + CorpsConfig.DISPARITION_MIN.get() * 60L * 20L);
        this.entityData.set(DATA_VIDE, contenu.isEmpty());
        if (contenu.isEmpty()) raccourcirVide();
        majTenue();
    }

    /** Corps d'un joueur hors ligne (conversion d'un corps de l'ancien mod) : ni apparence ni skin, clan actuel. */
    public void preparerSansJoueur(UUID proprietaire, String nom, CorpsContenu contenu, long creeLeMs) {
        long maintenant = level().getGameTime();
        this.proprietaire = proprietaire;
        this.nom = nom == null ? "" : nom;
        this.cause = Component.empty();
        this.creeLe = maintenant;
        this.creeLeMs = creeLeMs > 0 ? creeLeMs : System.currentTimeMillis();
        this.protegeJusqua = maintenant;
        this.visibleDes = maintenant;
        this.clan = ClansCompat.clanDe(proprietaire);
        this.contenu = contenu;
        contenu.surChangement(this::contenuChange);
        this.entityData.set(DATA_EXPIRE, maintenant + CorpsConfig.DISPARITION_MIN.get() * 60L * 20L);
        this.entityData.set(DATA_VIDE, contenu.isEmpty());
        if (contenu.isEmpty()) raccourcirVide();
        majTenue();
    }

    /** Apparence CaraCrea lue directement dans les donnees du joueur (PlayerPersisted.caracrea), sans dependance au mod. */
    @Nullable
    private static CompoundTag apparenceCaraCrea(ServerPlayer p) {
        CompoundTag persiste = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persiste.contains("caracrea", 10)) return null;
        CompoundTag t = persiste.getCompound("caracrea").copy();
        t.remove("Chosen");
        return t.contains("Values") ? t : null;
    }

    public void definirNumero(int numero) {
        this.numero = numero;
    }

    public int numero() {
        return numero;
    }

    public long creeLeMs() {
        return creeLeMs;
    }

    public long creeLe() {
        return creeLe;
    }

    @Override
    @Nullable
    public CorpsContenu contenu() {
        return contenu;
    }

    // ------------------------------------------------------------------ regles (serveur)
    /** Ticks de protection restants pour ce joueur (0 = il peut fouiller). */
    public long protectionRestante(ServerPlayer p) {
        long reste = protegeJusqua - level().getGameTime();
        if (reste <= 0 || p.getUUID().equals(proprietaire)) return 0;
        if (p.isCreative() && p.hasPermissions(2)) return 0;
        if (CorpsConfig.PROTECTION_CLAN.get() && clan != null && clan.equals(ClansCompat.clanDe(p.getUUID()))) return 0;
        return reste;
    }

    /**
     * Appele a chaque modification du contenu (prise d'objet) : un corps vide disparait (28/10 : tout de suite par defaut).
     * <p>Cause du « corps vide qui reste » : la disparition n'etait verifiee que par le tick de l'entite, une fois par seconde.
     * Or sous Mohist (spigot.yml, {@code entity-activation-range.misc} = 16), une entite « divers » loin des joueurs n'est
     * plus du tout tickee : un corps vide reste jusqu'a ce que quelqu'un revienne a 16 blocs. Un corps cree ou recharge vide
     * gardait en plus son minuteur de 10 min (le minuteur n'etait raccourci que si le corps PASSAIT de plein a vide). La
     * disparition passe maintenant par une tache du serveur (tick suivant), qui ne depend pas du tick de l'entite.
     */
    private void contenuChange() {
        if (contenu == null || level().isClientSide) return;
        boolean vide = contenu.isEmpty();
        if (vide && !entityData.get(DATA_VIDE)) {
            entityData.set(DATA_VIDE, true);
            HeralizCorps.LOG.info("[corps] corps n{} de {} vide : disparition dans {} s", numero, nom, CorpsConfig.DISPARITION_VIDE_S.get());
            raccourcirVide();
        } else if (!vide && entityData.get(DATA_VIDE)) {
            entityData.set(DATA_VIDE, false);
        }
        majTenue();
        if (level() instanceof ServerLevel sl) {
            Registre r = Registre.de(sl.getServer());
            r.objets(getUUID(), contenu.nbObjets());
            r.expire(getUUID(), expire());
        }
    }

    /**
     * Corps vide : minuteur ramene a {@code disparition_vide_secondes}. Avec 0 (defaut), disparition planifiee par le
     * serveur lui-meme au tick suivant, sans attendre le tick de l'entite (voir contenuChange). Avec un delai, c'est le
     * tick de l'entite qui s'en charge (seconde) : une tache « dans N ticks » peut etre jouee plus tot par le serveur.
     */
    private void raccourcirVide() {
        long delai = CorpsConfig.DISPARITION_VIDE_S.get() * 20L;
        long fin = level().getGameTime() + delai;
        if (fin < expire()) entityData.set(DATA_EXPIRE, fin);
        if (delai > 0 || !(level() instanceof ServerLevel sl)) return;
        net.minecraft.server.MinecraftServer serveur = sl.getServer();
        serveur.tell(new net.minecraft.server.TickTask(serveur.getTickCount() + 1, this::retirerSiVide));
    }

    /** Tache planifiee (une seule fois) : retire le corps s'il est toujours vide. */
    private void retirerSiVide() {
        if (isRemoved() || contenu == null || !contenu.isEmpty()) return;
        disparaitre("vide");
    }

    /** Force la fin du minuteur (banc d'essai). */
    public void forcerExpire(long tempsDeJeu) {
        entityData.set(DATA_EXPIRE, tempsDeJeu);
        if (level() instanceof ServerLevel sl) Registre.de(sl.getServer()).expire(getUUID(), tempsDeJeu);
    }

    /** Vide le corps (commande d'administration) : il disparait comme un corps fouille. */
    public void vider() {
        if (contenu != null) contenu.clearContent();
    }

    /** Fait disparaitre le corps et ce qu'il contient (minuteur, plafond, commande). */
    public void disparaitre(String raison) {
        if (isRemoved()) return;
        int n = contenu == null ? 0 : contenu.nbObjets();
        if (n > 0) HeralizCorps.LOG.info("[corps] corps n{} de {} retire ({}) : {} objet(s) perdus", numero, nom, raison, n);
        else HeralizCorps.LOG.info("[corps] corps n{} de {} retire ({})", numero, nom, raison);
        discard();
    }

    // ------------------------------------------------------------------ tick
    /** Banc d'essai : mesure du temps de tick serveur des corps (desactivee en jeu). */
    public static boolean mesurer;
    public static long mesureNs, mesureAppels;

    @Override
    public void tick() {
        // pas de super.tick() : ni feu, ni eau, ni portail, ni poussee ; le client n'a rien a calculer
        if (level().isClientSide) return;
        if (mesurer) {
            long t0 = System.nanoTime();
            tickServeur();
            mesureNs += System.nanoTime() - t0;
            mesureAppels++;
        } else {
            tickServeur();
        }
    }

    private void tickServeur() {
        if (premierTick) {
            premierTick = false;
            if (!accueillir()) return;
        }
        if (enChute && !isNoGravity()) tomber();
        if ((tickCount + getId()) % 20 == 0) seconde();
    }

    /** Premier tick serveur (creation ou rechargement du chunk) : actions d'administration en attente, registre. */
    private boolean accueillir() {
        if (!(level() instanceof ServerLevel sl)) return true;
        if (contenu == null) {
            contenu = new CorpsContenu(0, 0);
            contenu.surChangement(this::contenuChange);
        }
        Registre r = Registre.de(sl.getServer());
        Registre.Action a = r.accueillir(this);
        if (a == Registre.Action.SUPPRIMER) {
            disparaitre("commande /corps supprimer");
            return false;
        }
        if (a == Registre.Action.VIDER) vider();
        if (contenu.isEmpty()) {
            // cree ou recharge vide : rien a fouiller, il disparait (tout de suite avec le reglage par defaut)
            entityData.set(DATA_VIDE, true);
            if (CorpsConfig.DISPARITION_VIDE_S.get() <= 0) {
                disparaitre("vide");
                return false;
            }
            raccourcirVide();
        }
        return true;
    }

    private void seconde() {
        long maintenant = level().getGameTime();
        if (maintenant >= expire()) {
            disparaitre(vide() ? "vide" : "minuteur");
            return;
        }
        if (!enChute && !isNoGravity() && sansAppui()) enChute = true;
    }

    /** Plus de bloc sous le centre du corps (lecture d'un seul etat de bloc, sans allocation) : il tombera, la chute s'arretant au premier appui. */
    private boolean sansAppui() {
        dessous.set(getX(), getY() - 0.05, getZ());
        return level().getBlockState(dessous).getCollisionShape(level(), dessous).isEmpty();
    }

    private void tomber() {
        Vec3 v = getDeltaMovement();
        double vy = Math.max(v.y - 0.04, -2.0) * 0.98;
        move(MoverType.SELF, new Vec3(0, vy, 0));
        hasImpulse = true;
        if (onGround()) {
            setDeltaMovement(Vec3.ZERO);
            enChute = false;
            if (level() instanceof ServerLevel sl) Registre.de(sl.getServer()).position(this);
        } else {
            setDeltaMovement(0, vy, 0);
            if (getY() < level().getMinBuildHeight()) {
                // vide : le corps reste en suspens au fond du monde (les administrateurs peuvent le retrouver)
                setPos(getX(), level().getMinBuildHeight() + 1, getZ());
                setDeltaMovement(Vec3.ZERO);
                setNoGravity(true);
                enChute = false;
            }
        }
    }

    // ------------------------------------------------------------------ interactions
    /** Clic droit sur le corps (CorpsEntity.interact, cote serveur) : la fouille. */
    @Override
    void ouvrirFouille(ServerPlayer sp) {
        Fouille.ouvrir(sp, this);
    }

    @Override
    public void remove(RemovalReason raison) {
        super.remove(raison);
        if (!level().isClientSide && raison.shouldDestroy() && level() instanceof ServerLevel sl) {
            Registre.de(sl.getServer()).retirer(getUUID());
            // les fouilles ouvertes se ferment (le menu n'est plus valide de toute facon)
            for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
                if (p.containerMenu instanceof CorpsMenu m && m.corps() == this) p.closeContainer();
            }
        }
    }

    // ------------------------------------------------------------------ donnees
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("Proprietaire")) proprietaire = t.getUUID("Proprietaire");
        nom = t.getString("Nom");
        Component c = null;
        try {
            c = Component.Serializer.fromJson(t.getString("Cause"));
        } catch (RuntimeException ignore) {
            // cause illisible : on l'oublie
        }
        cause = c == null ? Component.empty() : c;
        creeLe = t.getLong("CreeLe");
        creeLeMs = t.getLong("CreeLeMs");
        protegeJusqua = t.getLong("Protege");
        visibleDes = t.getLong("Visible");
        clan = t.hasUUID("Clan") ? t.getUUID("Clan") : null;
        apparence = t.contains("Apparence", 10) ? t.getCompound("Apparence") : null;
        texturesValeur = t.getString("TexV");
        texturesSignature = t.getString("TexS");
        numero = t.getInt("Numero");
        enChute = t.getBoolean("Chute");
        entityData.set(DATA_EXPIRE, t.getLong("Expire"));
        contenu = CorpsContenu.charger(t.getCompound("Contenu"));
        contenu.surChangement(this::contenuChange);
        entityData.set(DATA_VIDE, contenu.isEmpty());
        // corps recharge vide (sauvegarde d'avant le 28/10) : il part au premier tick (accueillir)
        majTenue();
    }

    /**
     * Tenue vue par les joueurs (CorpsEntity.DATA_TENUE) : armure et objets Curios encore sur le corps. Copies allegees :
     * le contenu d'un sac ou d'un conteneur ne quitte jamais le serveur (seul l'aspect compte pour le rendu).
     */
    private void majTenue() {
        CompoundTag t = new CompoundTag();
        if (contenu != null) {
            net.minecraft.nbt.ListTag armure = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 4; i++) {
                armure.add(allege(contenu.getItem(CorpsContenu.ARMURE + i)).save(new CompoundTag()));
            }
            t.put("A", armure);
            net.minecraft.nbt.ListTag curios = new net.minecraft.nbt.ListTag();
            ajouterCurios(curios, fr.heraliz.corps.compat.CuriosCompat.SAC, contenu.getItem(CorpsContenu.SAC));
            for (int k = 0; k < contenu.nbCurios(); k++) {
                ajouterCurios(curios, contenu.idCurios(k), contenu.getItem(CorpsContenu.CURIOS + k));
            }
            if (!curios.isEmpty()) t.put("C", curios);
        }
        if (!t.equals(entityData.get(DATA_TENUE))) entityData.set(DATA_TENUE, t);
    }

    private static void ajouterCurios(net.minecraft.nbt.ListTag l, String id, net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty() || id == null || id.isEmpty()) return;
        CompoundTag o = allege(s).save(new CompoundTag());
        o.putString("Id", id);
        l.add(o);
    }

    private static net.minecraft.world.item.ItemStack allege(net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.world.item.ItemStack c = s.copyWithCount(1);
        CompoundTag tag = c.getTag();
        if (tag != null) {
            for (String cle : new String[] {"Items", "Inventory", "BlockEntityTag", "spartan_uid"}) tag.remove(cle);
        }
        return c;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putUUID("Proprietaire", proprietaire);
        t.putString("Nom", nom);
        t.putString("Cause", Component.Serializer.toJson(cause));
        t.putLong("CreeLe", creeLe);
        t.putLong("CreeLeMs", creeLeMs);
        t.putLong("Expire", expire());
        t.putLong("Protege", protegeJusqua);
        t.putLong("Visible", visibleDes);
        if (clan != null) t.putUUID("Clan", clan);
        if (apparence != null) t.put("Apparence", apparence);
        if (!texturesValeur.isEmpty()) t.putString("TexV", texturesValeur);
        if (!texturesSignature.isEmpty()) t.putString("TexS", texturesSignature);
        t.putInt("Numero", numero);
        if (enChute) t.putBoolean("Chute", true);
        if (contenu != null) t.put("Contenu", contenu.sauver());
    }
}
