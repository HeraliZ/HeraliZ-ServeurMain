package fr.heraliz.grades.bon;

import fr.heraliz.grades.HeralizGrades;
import fr.heraliz.grades.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;

/**
 * Canal des bons ({@code heraliz_grades:bon}, version 1) : l'éditeur des op. Le serveur ouvre l'éditeur (avec les données
 * du bon tenu en main, s'il y en a un) ; l'op ne fait que DEMANDER la création : droit, valeurs et objet sont décidés et
 * revérifiés par le serveur.
 */
public final class ReseauBon {
    private ReseauBon() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizGrades.MODID, "bon"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Serveur -> op : ouvrir l'éditeur (données de départ, null : bon neuf), ou réponse à une création (message). */
    public record Editeur(boolean ouvrir, @Nullable Bon.Donnees depart, String message, boolean ok) {
    }

    /** Op -> serveur : créer ce bon. */
    public record Creer(Bon.Donnees donnees) {
    }

    public record CreerRecu(ServerPlayer joueur, Bon.Donnees donnees) {
    }

    public static final Crochet<Editeur> RECEPTION_EDITEUR = new Crochet<>();
    public static final Crochet<CreerRecu> RECEPTION_CREER = new Crochet<>();

    public static void enregistrer() {
        CANAL.messageBuilder(Editeur.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauBon::ecrire)
                .decoder(ReseauBon::lire)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_EDITEUR.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(Creer.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> Bon.ecrire(b, m.donnees()))
                .decoder(b -> new Creer(Bon.lire(b)))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_CREER.accept(new CreerRecu(p, m.donnees()));
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    private static void ecrire(Editeur m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        b.writeBoolean(m.depart() != null);
        if (m.depart() != null) Bon.ecrire(b, m.depart());
        b.writeUtf(m.message(), 300);
        b.writeBoolean(m.ok());
    }

    private static Editeur lire(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        Bon.Donnees d = b.readBoolean() ? Bon.lire(b) : null;
        return new Editeur(ouvrir, d, b.readUtf(300), b.readBoolean());
    }
}
