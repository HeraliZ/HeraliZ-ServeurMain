package fr.heraliz.clashwar;

import com.mojang.brigadier.CommandDispatcher;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Branchement des sous-paquets sans dependance de compilation : chaque module a une classe d'entree a nom fixe (liste
 * {@link #COMMUNS}) qui peut definir, au choix, ces methodes <b>publiques statiques</b> :
 * <ul>
 *     <li>{@code construire(IEventBus modBus)} : registres (DeferredRegister), appele dans le constructeur du mod ;</li>
 *     <li>{@code paquets(MatchsNet.Registre r)} : paquets reseau, appele par {@link MatchsNet#init()} ;</li>
 *     <li>{@code commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c)} : commandes serveur.</li>
 * </ul>
 * Une classe absente est ignoree (journal debug) ; une methode qui
 * leve une exception est journalisee en erreur et n'empeche pas les autres modules.
 * <p>L'ordre de la liste fixe l'ordre des identifiants de paquets : il est identique des deux cotes (meme jar). Pour
 * ajouter un module, ajouter une ligne a la fin (fichier du socle : modification minimale, signalee dans NOTES_DEV).
 */
public final class Modules {
    private Modules() {
    }

    /** Classes d'entree communes (serveur et client), dans l'ordre d'enregistrement. */
    public static final List<String> COMMUNS = List.of(
            "fr.heraliz.clashwar.moteur.Moteur",
            "fr.heraliz.clashwar.tournoi.ClashWar");

    /**
     * Parties serveur des modules (docs/METHODE_CLIENT_SERVEUR.md), dans le MEME ordre que {@link #COMMUNS} : absentes du
     * jar -client. {@code construire} de chacune est appelé juste après celui de sa partie commune (même ordre des
     * écouteurs qu'avant la séparation) ; les commandes sont toutes côté serveur.
     */
    public static final List<String> SERVEURS = List.of(
            "fr.heraliz.clashwar.moteur.MoteurLogique",
            "fr.heraliz.clashwar.tournoi.ClashWarLogique");

    /** Classes d'entree client (chargees seulement sur le client physique). */
    public static final List<String> CLIENTS = List.of(
            "fr.heraliz.clashwar.moteur.client.MoteurClient",
            "fr.heraliz.clashwar.tournoi.client.ClashWarClient");

    public static void construire(IEventBus modBus) {
        for (int i = 0; i < COMMUNS.size(); i++) {
            appeler(List.of(COMMUNS.get(i)), "construire", new Class<?>[]{IEventBus.class}, modBus);
            appeler(List.of(SERVEURS.get(i)), "construire", new Class<?>[]{IEventBus.class}, modBus);
        }
    }

    /** Rend le nombre de modules qui ont enregistre des paquets. */
    public static int paquets(MatchsNet.Registre r) {
        return appeler(COMMUNS, "paquets", new Class<?>[]{MatchsNet.Registre.class}, r);
    }

    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        appeler(SERVEURS, "commandes", new Class<?>[]{CommandDispatcher.class, CommandBuildContext.class}, d, c);
    }

    /**
     * Appelle {@code nom(args)} sur chaque classe presente de la liste qui definit cette methode publique statique.
     * Rend le nombre de modules appeles.
     */
    public static int appeler(List<String> classes, String nom, Class<?>[] types, Object... args) {
        int n = 0;
        for (String c : classes) {
            Class<?> k;
            try {
                k = Class.forName(c, true, Modules.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                HeralizClashWar.LOG.debug("[matchs] module absent : {}", c);
                continue;
            } catch (LinkageError ex) {
                HeralizClashWar.LOG.error("[matchs] module {} illisible", c, ex);
                continue;
            }
            Method m;
            try {
                m = k.getMethod(nom, types);
            } catch (NoSuchMethodException ex) {
                continue;
            }
            try {
                m.invoke(null, args);
                n++;
            } catch (InvocationTargetException ex) {
                HeralizClashWar.LOG.error("[matchs] {}.{} a echoue", c, nom, ex.getCause());
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeralizClashWar.LOG.error("[matchs] {}.{} inaccessible (doit etre public static)", c, nom, ex);
            }
        }
        return n;
    }
}
