package fr.heralisz.matchs.moteur.net;

import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Paquets du moteur commun. S2C : bandeau, tag de combat, annonces, compte a rebours, fil des eliminations, spectateur,
 * groupe, invitation, ecran de choix de classe (ouverture, etat, fermeture). C2S : spectateur (cible suivante), groupe
 * (actions de l'ecran), choix de classe. Les traitements client passent par {@link MoteurClient} (jamais charge sur un
 * serveur dedie) ; les C2S revalident tout cote serveur (cadence, droits, etat).
 */
public final class PaquetsMoteur {
    /** Réception côté client des paquets serveur -> client (client/ReceptionPaquetsMoteur) : branchée par la partie client. */
    public static final fr.heralisz.matchs.pont.Crochet<Object> RECEPTION_CLIENT = new fr.heralisz.matchs.pont.Crochet<>();

    private PaquetsMoteur() {
    }

    /** Appele par {@code Moteur.paquets} : ordre fixe (ajouts en fin de liste seulement). */
    public static void enregistrer(MatchsNet.Registre r) {
        r.s2c(HudS2C.class, HudS2C::write, HudS2C::read, HudS2C::handle);
        r.s2c(CombatS2C.class, CombatS2C::write, CombatS2C::read, CombatS2C::handle);
        r.s2c(AnnonceS2C.class, AnnonceS2C::write, AnnonceS2C::read, AnnonceS2C::handle);
        r.s2c(CompteS2C.class, CompteS2C::write, CompteS2C::read, CompteS2C::handle);
        r.s2c(KillFeedS2C.class, KillFeedS2C::write, KillFeedS2C::read, KillFeedS2C::handle);
        r.s2c(SpectateurS2C.class, SpectateurS2C::write, SpectateurS2C::read, SpectateurS2C::handle);
        r.c2s(SpectateurC2S.class, SpectateurC2S::write, SpectateurC2S::read, SpectateurC2S::handle);
        r.s2c(GroupeS2C.class, GroupeS2C::write, GroupeS2C::read, GroupeS2C::handle);
        r.s2c(InvitationS2C.class, InvitationS2C::write, InvitationS2C::read, InvitationS2C::handle);
        r.c2s(GroupeC2S.class, GroupeC2S::write, GroupeC2S::read, GroupeC2S::handle);
        r.s2c(ChoixOuvrirS2C.class, ChoixOuvrirS2C::write, ChoixOuvrirS2C::read, ChoixOuvrirS2C::handle);
        r.s2c(ChoixEtatS2C.class, ChoixEtatS2C::write, ChoixEtatS2C::read, ChoixEtatS2C::handle);
        r.c2s(ChoixC2S.class, ChoixC2S::write, ChoixC2S::read, ChoixC2S::handle);
        r.s2c(EtatMatchS2C.class, EtatMatchS2C::write, EtatMatchS2C::read, EtatMatchS2C::handle);
    }

    /** Prefixe des messages de chat du mod : « HeraliZ Matchs » en accent + separateur. */
    public static MutableComponent prefixe() {
        return Component.translatableWithFallback("matchs.nom", "HeraliZ Matchs")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x6FA8FF)).withBold(true))
                .append(Component.literal("  ").withStyle(Style.EMPTY.withBold(false)));
    }

    @Nullable
    static ServerPlayer emetteur(Supplier<NetworkEvent.Context> ctx) {
        return ctx.get().getSender();
    }

    /** Bandeau de match ; null = effacer. */
    public record HudS2C(@Nullable Hud.Bandeau bandeau) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(bandeau != null);
            if (bandeau != null) bandeau.write(buf);
        }

        public static HudS2C read(FriendlyByteBuf buf) {
            return new HudS2C(buf.readBoolean() ? Hud.Bandeau.read(buf) : null);
        }

        public static void handle(HudS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Secondes restantes du tag de combat (0 = masquer). */
    public record CombatS2C(int secondes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(secondes);
        }

        public static CombatS2C read(FriendlyByteBuf buf) {
            return new CombatS2C(buf.readVarInt());
        }

        public static void handle(CombatS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    public record AnnonceS2C(Annonces.Annonce annonce) {
        public void write(FriendlyByteBuf buf) {
            annonce.write(buf);
        }

        public static AnnonceS2C read(FriendlyByteBuf buf) {
            return new AnnonceS2C(Annonces.Annonce.read(buf));
        }

        public static void handle(AnnonceS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    public record CompteS2C(int secondes, Component titre) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(secondes);
            buf.writeComponent(titre);
        }

        public static CompteS2C read(FriendlyByteBuf buf) {
            return new CompteS2C(buf.readVarInt(), buf.readComponent());
        }

        public static void handle(CompteS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    public record KillFeedS2C(KillFeed.Ligne ligne) {
        public void write(FriendlyByteBuf buf) {
            ligne.write(buf);
        }

        public static KillFeedS2C read(FriendlyByteBuf buf) {
            return new KillFeedS2C(KillFeed.Ligne.read(buf));
        }

        public static void handle(KillFeedS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ spectateur
    /** Etat du mode spectateur : actif, nom et couleur de la cible ("" = vue libre), nombre de cibles possibles. */
    public record SpectateurS2C(boolean actif, String cible, int couleur, int nombre) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(actif);
            buf.writeUtf(cible, 48);
            buf.writeInt(couleur);
            buf.writeVarInt(nombre);
        }

        public static SpectateurS2C read(FriendlyByteBuf buf) {
            return new SpectateurS2C(buf.readBoolean(), buf.readUtf(48), buf.readInt(), Math.min(64, buf.readVarInt()));
        }

        public static void handle(SpectateurS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Demande de cible suivante (+1) ou precedente (-1). */
    public record SpectateurC2S(int sens) {
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(sens);
        }

        public static SpectateurC2S read(FriendlyByteBuf buf) {
            return new SpectateurC2S(buf.readByte());
        }

        public static void handle(SpectateurC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsMoteurServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    // ------------------------------------------------------------------ groupes
    /** Membre affiche dans l'ecran du groupe. */
    public record MembreVue(String nom, boolean chef, boolean enLigne) {
    }

    /** Invitation recue (nom de l'invitant, secondes restantes). */
    public record InvitVue(String de, int secondes) {
    }

    /**
     * Etat du groupe du joueur : ouvrir l'ecran ou non, membres (vide = pas de groupe), suis-je chef, invitations
     * recues encore valides.
     */
    public record GroupeS2C(boolean ouvrir, List<MembreVue> membres, boolean chef, List<InvitVue> invitations) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(ouvrir);
            buf.writeVarInt(membres.size());
            for (MembreVue m : membres) {
                buf.writeUtf(m.nom, 48);
                buf.writeBoolean(m.chef);
                buf.writeBoolean(m.enLigne);
            }
            buf.writeBoolean(chef);
            buf.writeVarInt(invitations.size());
            for (InvitVue i : invitations) {
                buf.writeUtf(i.de, 48);
                buf.writeVarInt(i.secondes);
            }
        }

        public static GroupeS2C read(FriendlyByteBuf buf) {
            boolean ouvrir = buf.readBoolean();
            int n = Math.min(8, buf.readVarInt());
            List<MembreVue> m = new ArrayList<>(n);
            for (int i = 0; i < n; i++) m.add(new MembreVue(buf.readUtf(48), buf.readBoolean(), buf.readBoolean()));
            boolean chef = buf.readBoolean();
            int ni = Math.min(16, buf.readVarInt());
            List<InvitVue> inv = new ArrayList<>(ni);
            for (int i = 0; i < ni; i++) inv.add(new InvitVue(buf.readUtf(48), buf.readVarInt()));
            return new GroupeS2C(ouvrir, m, chef, inv);
        }

        public static void handle(GroupeS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Toast d'invitation a un groupe. */
    public record InvitationS2C(String de, int secondes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(de, 48);
            buf.writeVarInt(secondes);
        }

        public static InvitationS2C read(FriendlyByteBuf buf) {
            return new InvitationS2C(buf.readUtf(48), buf.readVarInt());
        }

        public static void handle(InvitationS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Action de l'ecran du groupe ({@code GroupesService.ACTION_*}) avec un nom de joueur eventuel. */
    public record GroupeC2S(int action, String cible) {
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(action);
            buf.writeUtf(cible, 32);
        }

        public static GroupeC2S read(FriendlyByteBuf buf) {
            return new GroupeC2S(buf.readByte(), buf.readUtf(32));
        }

        public static void handle(GroupeC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsMoteurServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    // ------------------------------------------------------------------ choix de classe
    /**
     * Ouverture de l'ecran de choix de classe : titre, secondes, couleur d'equipe, kits proposes et leur apercu (arme
     * principale, pile vide si indisponible), limites par classe et par equipe.
     */
    public record ChoixOuvrirS2C(Component titre, int secondes, int couleur, List<Kit> kits, List<ItemStack> apercus,
                                 Map<String, Integer> limites) {
        public void write(FriendlyByteBuf buf) {
            buf.writeComponent(titre);
            buf.writeVarInt(secondes);
            buf.writeInt(couleur);
            buf.writeVarInt(kits.size());
            for (int i = 0; i < kits.size(); i++) {
                kits.get(i).write(buf);
                buf.writeItem(i < apercus.size() ? apercus.get(i) : ItemStack.EMPTY);
            }
            buf.writeVarInt(limites.size());
            limites.forEach((k, v) -> {
                buf.writeUtf(k, 32);
                buf.writeVarInt(v);
            });
        }

        public static ChoixOuvrirS2C read(FriendlyByteBuf buf) {
            Component titre = buf.readComponent();
            int s = buf.readVarInt(), c = buf.readInt();
            int n = Math.min(12, buf.readVarInt());
            List<Kit> kits = new ArrayList<>(n);
            List<ItemStack> ap = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                kits.add(Kit.read(buf));
                ap.add(buf.readItem());
            }
            int nl = Math.min(16, buf.readVarInt());
            Map<String, Integer> lim = new LinkedHashMap<>();
            for (int i = 0; i < nl; i++) lim.put(buf.readUtf(32), buf.readVarInt());
            return new ChoixOuvrirS2C(titre, s, c, kits, ap, lim);
        }

        public static void handle(ChoixOuvrirS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Choix d'un coequipier : classe et nom. */
    public record Pris(String classe, String kit, String nom) {
    }

    /**
     * Etat du choix : mon kit ("" = aucun), choix de mes coequipiers, fermeture (fin du choix : l'ecran se ferme).
     */
    public record ChoixEtatS2C(String monKit, List<Pris> pris, boolean fermer) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(monKit, 96);
            buf.writeVarInt(pris.size());
            for (Pris p : pris) {
                buf.writeUtf(p.classe, 32);
                buf.writeUtf(p.kit, 96);
                buf.writeUtf(p.nom, 48);
            }
            buf.writeBoolean(fermer);
        }

        public static ChoixEtatS2C read(FriendlyByteBuf buf) {
            String mon = buf.readUtf(96);
            int n = Math.min(16, buf.readVarInt());
            List<Pris> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Pris(buf.readUtf(32), buf.readUtf(96), buf.readUtf(48)));
            return new ChoixEtatS2C(mon, l, buf.readBoolean());
        }

        public static void handle(ChoixEtatS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Le joueur choisit un kit (revalide : session, kit propose, limite de sa classe dans son equipe). */
    public record ChoixC2S(String kit) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(kit, 96);
        }

        public static ChoixC2S read(FriendlyByteBuf buf) {
            return new ChoixC2S(buf.readUtf(96));
        }

        public static void handle(ChoixC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsMoteurServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    // ------------------------------------------------------------------ etat en match
    /**
     * Le joueur entre dans un match ou en sort ({@code moteur.combat.RestrictionsMatch}) : en match, le client masque la
     * mini-carte de Xaero et refuse l'ouverture de sa carte du monde si {@code masquerCartes}.
     */
    public record EtatMatchS2C(boolean enMatch, boolean masquerCartes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enMatch);
            buf.writeBoolean(masquerCartes);
        }

        public static EtatMatchS2C read(FriendlyByteBuf buf) {
            return new EtatMatchS2C(buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(EtatMatchS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }
}
