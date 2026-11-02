package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Atelier de réparation, partie serveur (26/10) : l'aperçu ({@link ReparationServeur#apercu}) est recalculé quand l'objet
 * posé ou le kit change ; « Réparer » revérifie tout (kit du même niveau toujours dans la case de la barre, objet
 * réparable) puis répare comme avant ({@link ReparationServeur#appliquer} : règles du serveur, utilisation du kit, son,
 * message). Fermeture : l'objet posé est rendu dans l'inventaire. Le menu se ferme seul si le kit quitte la main.
 * <p>
 * 02/11 (audit n° 8 et mods-2 §13) : l'objet posé n'est plus jamais jeté au sol ni perdu. Minecraft enregistre le joueur
 * AVANT de fermer son écran à la déconnexion : l'objet est donc rendu dans l'inventaire dès l'annonce de la déconnexion
 * (avant l'enregistrement) ; à la mort, il revient dans l'inventaire avant que celui-ci tombe (corps, objets à vie gardés
 * comme le reste). Tant qu'il est dans l'atelier, une copie est gardée dans les données du joueur ({@link #CLE}, dans
 * {@code PlayerPersisted}), enregistrée avec lui : après un plantage, il est rendu à la connexion. Inventaire plein : il
 * reste gardé là et revient dès qu'il y a de la place (contrôle toutes les 5 s, à la connexion et à la réapparition ;
 * l'atelier ne s'ouvre pas tant qu'il attend).
 */
final class MenuReparationServeur extends MenuReparation {
    /** Objet posé dans l'atelier, ou qui attend de la place dans l'inventaire (données persistantes du joueur). */
    static final String CLE = "HeralizAtelierReparation";

    private final ServerPlayer joueur;
    private final int niveau;
    private boolean fini;
    /** Dernier état vu (objet posé, kit) : l'aperçu n'est recalculé que s'il change. */
    private ItemStack vuCible = ItemStack.EMPTY, vuKit = ItemStack.EMPTY;
    private int tick;

    MenuReparationServeur(int id, Inventory inv, int caseKit, int niveau) {
        this(id, inv, caseKit, niveau, new SimpleContainer(1));
    }

    private MenuReparationServeur(int id, Inventory inv, int caseKit, int niveau, SimpleContainer c) {
        super(id, inv, c, new SimpleContainerData(NB_DONNEES), caseKit);
        this.joueur = (ServerPlayer) inv.player;
        this.niveau = niveau;
        donnees.set(D_NIVEAU, niveau);
        c.addListener(x -> miroir());
        majApercu(true);
    }

    private ItemStack kit() {
        ItemStack k = joueur.getInventory().getItem(caseKit);
        return k.getItem() instanceof KitReparation kr && kr.niveau == niveau ? k : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.isAlive() && !kit().isEmpty() && p.getInventory().selected == caseKit;
    }

    @Override
    public void broadcastChanges() {
        if (!fini && ++tick % 4 == 0) majApercu(false);
        super.broadcastChanges();
    }

    private void majApercu(boolean force) {
        ItemStack c = cible.getItem(0), k = kit();
        if (!force && ItemStack.isSameItemSameTags(c, vuCible) && c.getCount() == vuCible.getCount()
                && ItemStack.isSameItemSameTags(k, vuKit) && k.getCount() == vuKit.getCount()) return;
        vuCible = c.copy();
        vuKit = k.copy();
        Regles r = ReglesServeur.get();
        int total = r.kitUtilisations[niveau];
        donnees.set(D_TOTAL, total);
        donnees.set(D_RESTANTES, k.isEmpty() ? 0 : Math.max(0, total - (k.hasTag() ? k.getTag().getInt(KitReparation.UTILISEES) : 0)));
        donnees.set(D_GAIN, Math.round(r.kitGain[niveau] * 10f));
        int[] a = ReparationServeur.apercu(c, niveau);
        int code = k.isEmpty() && a[0] != VIDE ? SANS_KIT : a[0];
        donnees.set(D_CODE, code);
        donnees.set(D_GENRE, a[1]);
        donnees.set(D_AVANT, a[2]);
        donnees.set(D_APRES, a[3]);
        donnees.set(D_MAX_AVANT, a[4]);
        donnees.set(D_MAX_APRES, a[5]);
    }

    @Override
    public boolean clickMenuButton(Player p, int bouton) {
        if (bouton != BOUTON_REPARER || fini) return false;
        ItemStack k = kit(), c = cible.getItem(0);
        if (k.isEmpty() || c.isEmpty() || !KitReparation.reparable(c)) return false;
        if (ReparationServeur.apercu(c, niveau)[0] != OK) return false;
        ReparationServeur.appliquer(joueur, k, c, niveau);
        cible.setChanged();
        majApercu(true);
        return true;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (fini) return;
        if (p instanceof ServerPlayer sp && sp.isAlive() && !sp.hasDisconnected()) {
            rendre(sp, false);
            return;
        }
        // mort ou départ sans passer par mort() ni deconnexion() (ne devrait pas arriver) : comme avant, au sol
        fini = true;
        gardes(joueur).remove(CLE);
        clearContainer(p, cible);
    }

    /** L'objet posé recopié dans les données du joueur à chaque changement : enregistré avec lui, jamais perdu. */
    private void miroir() {
        if (fini) return;
        ItemStack s = cible.getItem(0);
        if (s.isEmpty()) gardes(joueur).remove(CLE);
        else gardes(joueur).put(CLE, s.save(new CompoundTag()));
    }

    /**
     * Rend l'objet posé dans l'inventaire et ferme l'atelier ; ce qui n'y rentre pas reste gardé ({@link #CLE}), jamais au
     * sol. À la mort ({@code mort}), ce qui ne rentre pas tombe avec le reste, comme avant (pas d'objet sauvé de la mort
     * par l'atelier). Fil serveur.
     */
    private void rendre(ServerPlayer sp, boolean mort) {
        if (fini) return;
        fini = true;
        ItemStack s = cible.removeItemNoUpdate(0);
        if (!s.isEmpty()) sp.getInventory().add(s);
        if (s.isEmpty() || mort) {
            gardes(sp).remove(CLE);
            if (!s.isEmpty()) sp.drop(s, true, false);
        } else {
            gardes(sp).put(CLE, s.save(new CompoundTag()));
            sp.sendSystemMessage(Component.translatableWithFallback("heraliz_usure.atelier.en_attente",
                    "Ton objet de l'atelier de réparation t'attend : fais de la place dans ton inventaire.").withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ objet gardé (déconnexion, mort, plantage)
    /** Données persistantes du joueur (copiées par Forge à la réapparition). */
    private static CompoundTag gardes(ServerPlayer sp) {
        CompoundTag racine = sp.getPersistentData();
        if (!racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) racine.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return racine.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /**
     * Objet gardé (atelier fermé) rendu dans l'inventaire s'il y a la place. Vrai s'il attend encore. Jamais pendant que
     * l'atelier est ouvert : la copie gardée est alors celle de l'objet posé dedans.
     */
    static boolean rendreGarde(ServerPlayer sp, boolean prevenir) {
        if (sp.containerMenu instanceof MenuReparationServeur) return false;
        CompoundTag g = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!g.contains(CLE, Tag.TAG_COMPOUND)) return false;
        ItemStack s = ItemStack.of(g.getCompound(CLE));
        if (s.isEmpty()) {
            g.remove(CLE);
            return false;
        }
        sp.getInventory().add(s);
        if (s.isEmpty()) {
            g.remove(CLE);
            sp.sendSystemMessage(Component.translatableWithFallback("heraliz_usure.atelier.rendu",
                    "Ton objet de l'atelier de réparation est revenu dans ton inventaire.").withStyle(ChatFormatting.GREEN));
            return false;
        }
        g.put(CLE, s.save(new CompoundTag()));
        if (prevenir) {
            sp.sendSystemMessage(Component.translatableWithFallback("heraliz_usure.atelier.en_attente",
                    "Ton objet de l'atelier de réparation t'attend : fais de la place dans ton inventaire.").withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    /** Déconnexion annoncée : l'objet revient dans l'inventaire AVANT que Minecraft enregistre le joueur. */
    static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.containerMenu instanceof MenuReparationServeur m) m.rendre(sp, false);
    }

    /** Mort (non annulée) : l'objet revient dans l'inventaire avant que celui-ci tombe, comme s'il n'avait pas été posé. */
    static void mort(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.containerMenu instanceof MenuReparationServeur m) m.rendre(sp, true);
    }

    static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) rendreGarde(sp, true);
    }

    static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) rendreGarde(sp, true);
    }

    /** Toutes les 5 s, seulement pour un joueur dont un objet attend de la place. */
    static void tickJoueur(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.tickCount % 100 != 0 || !(e.player instanceof ServerPlayer sp)) return;
        CompoundTag racine = sp.getPersistentData();
        if (racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND) && racine.getCompound(Player.PERSISTED_NBT_TAG).contains(CLE)) {
            rendreGarde(sp, false);
        }
    }
}
