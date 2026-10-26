package fr.heraliz.loot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caisses d'autres mods ouvertes comme un coffre T7 (25/10, demande du propriétaire : « que le contenu du largage soit
 * découvert comme les coffres T7, pour le suspense ») : même écran ({@link CoffreMenu}, sans le badge du palier), objets
 * cachés « ? » (le vrai objet ne quitte jamais le serveur) et identifiés un par un, d'autant plus longtemps qu'ils sont
 * rares ({@link Rarete#duree}, rareté de {@code raretes.json}), seulement pendant que quelqu'un regarde ; on ne peut rien
 * y déposer. La caisse reste le conteneur (ses objets, sa sauvegarde) : l'identification n'est gardée qu'en mémoire (après
 * un redémarrage, ce qui reste se redécouvre). API appelée PAR RÉFLEXION (caisse de largage des frappes aériennes) : ne
 * jamais renommer cette classe ni changer {@link #ouvrir}. Fil serveur seulement ; rien ne tourne sans caisse ouverte.
 */
public final class RevelationCaisses {
    private RevelationCaisses() {
    }

    private static final Map<UUID, Etat> ETATS = new HashMap<>();

    /**
     * Ouvre la caisse {@code entite} (conteneur {@code c} de 27 cases) pour ce joueur, en mode révélation. Rend faux si ce
     * n'est pas possible (autre taille) : la caisse s'ouvre alors comme avant.
     */
    public static boolean ouvrir(ServerPlayer p, Entity entite, Container c, Component titre) {
        if (c.getContainerSize() != CoffreBlockEntity.TAILLE) return false;
        Etat e = ETATS.computeIfAbsent(entite.getUUID(), k -> new Etat(entite, c));
        e.spectateurs.add(p.getUUID());
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new CaisseMenu(id, inv, e), titre), buf -> {
            buf.writeBlockPos(entite.blockPosition());
            buf.writeBoolean(false);   // pas de badge « T7 »
        });
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ETATS.isEmpty()) return;
        for (Iterator<Etat> it = ETATS.values().iterator(); it.hasNext(); ) {
            Etat e = it.next();
            e.tick();
            if (e.spectateurs.isEmpty() && (e.entite.isRemoved() || !e.entite.isAlive())) it.remove();
        }
    }

    @SubscribeEvent
    public static void arret(ServerStoppingEvent ev) {
        ETATS.clear();
    }

    /** Menu d'une caisse : celui des coffres T7, branché sur la caisse. */
    static final class CaisseMenu extends CoffreMenu {
        CaisseMenu(int id, Inventory inv, Etat e) {
            super(id, inv, e, e.entite.blockPosition(), false, e, e.donnees);
        }
    }

    /** Une caisse en cours de révélation : la vue « ? » de son contenu, et l'identification. */
    static final class Etat implements CoffreMenu.Source, CoffreMenu.Vue {
        final Entity entite;
        final Container c;
        final boolean[] identifie = new boolean[CoffreBlockEntity.TAILLE];
        final Set<UUID> spectateurs = new HashSet<>();
        int courant = -1, progres;
        final ContainerData donnees = new Donnees();

        Etat(Entity entite, Container c) {
            this.entite = entite;
            this.c = c;
        }

        void tick() {
            // spectateurs partis sans fermer proprement (déconnexion, mort...)
            spectateurs.removeIf(id -> {
                Player p = entite.level().getPlayerByUUID(id);
                return p == null || !(p.containerMenu instanceof CoffreMenu m) || m.coffre != this;
            });
            if (spectateurs.isEmpty()) {
                progres = 0;   // personne ne regarde : l'objet en cours repart de zéro (comme les coffres T7)
                return;
            }
            if (courant < 0 || identifie[courant] || c.getItem(courant).isEmpty()) {
                courant = prochain();
                progres = 0;
            }
            if (courant >= 0 && ++progres >= duree(courant)) {
                identifie[courant] = true;
                courant = prochain();
                progres = 0;
            }
        }

        int prochain() {
            for (int i = 0; i < CoffreBlockEntity.TAILLE; i++) {
                if (!c.getItem(i).isEmpty() && !identifie[i]) return i;
            }
            return -1;
        }

        int restants() {
            int n = 0;
            for (int i = 0; i < CoffreBlockEntity.TAILLE; i++) {
                if (!c.getItem(i).isEmpty() && !identifie[i]) n++;
            }
            return n;
        }

        int duree(int i) {
            if (i < 0) return 20;
            Rarete r = RaretesObjets.de(c.getItem(i));
            return r == null ? 20 : r.duree;
        }

        // ------------------------------------------------------------------ source du menu
        @Override
        public boolean valide(Player p) {
            return !entite.isRemoved() && entite.isAlive() && p.distanceToSqr(entite) <= 36;   // 6 blocs, comme les coffres
        }

        @Override
        public void fermer(Player p) {
            spectateurs.remove(p.getUUID());
        }

        // ------------------------------------------------------------------ vue « ? » du contenu
        @Override
        public boolean identifie(int i) {
            return identifie[i];
        }

        @Override
        public int getContainerSize() {
            return CoffreBlockEntity.TAILLE;
        }

        @Override
        public boolean isEmpty() {
            return c.isEmpty();
        }

        @Override
        public ItemStack getItem(int i) {
            ItemStack st = c.getItem(i);
            return st.isEmpty() || identifie[i] ? st : new ItemStack(HeralizLoot.OBJET_INCONNU.get());
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            return identifie[i] ? c.removeItem(i, n) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return identifie[i] ? c.removeItemNoUpdate(i) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int i, ItemStack st) {
            if (identifie[i]) c.setItem(i, st);
        }

        @Override
        public void setChanged() {
            c.setChanged();
        }

        @Override
        public boolean stillValid(Player p) {
            return true;
        }

        @Override
        public void clearContent() {
        }

        /** Données de l'identification envoyées à l'écran (mêmes que celles des coffres T7). */
        final class Donnees implements ContainerData {
            @Override
            public int get(int i) {
                return switch (i) {
                    case CoffreMenu.D_COURANT -> courant;
                    case CoffreMenu.D_PROGRES -> progres;
                    case CoffreMenu.D_DUREE -> duree(courant);
                    case CoffreMenu.D_RESTANTS -> restants();
                    default -> {
                        int m = 0, b0 = (i - CoffreMenu.D_MASQUE) * 9;
                        for (int k = 0; k < 9; k++) if (identifie[b0 + k]) m |= 1 << k;
                        yield m;
                    }
                };
            }

            @Override
            public void set(int i, int v) {
            }

            @Override
            public int getCount() {
                return CoffreMenu.N_DONNEES;
            }
        }
    }
}
