package fr.heralisz.economie.vault;

import fr.heralisz.economie.EconomieConfig;
import fr.heralisz.economie.HeraliszEconomie;
import fr.heralisz.economie.api.EconomieApi;
import fr.heralisz.economie.server.ComptesStore;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont Vault : sur Mohist (Bukkit + plugin Vault presents), enregistre notre economie comme fournisseur
 * {@code net.milkbowl.vault.economy.Economy} (priorite Highest) par reflexion : un {@link Proxy} sur l'interface
 * Economy redirige getBalance/has/withdrawPlayer/depositPlayer/format/currencyName… vers {@link EconomieApi}
 * (lectures possibles depuis tout fil via le miroir des soldes ; ecritures refusees hors du fil serveur).
 * Tout est defensif : la moindre exception desactive le pont avec un message de journal, jamais de plantage.
 * Non testable en dev (pas de Bukkit) : code ecrit contre l'API Vault 1.7 (EconomyResponse(amount, balance,
 * ResponseType, errorMessage)).
 */
public final class VaultPont {
    private VaultPont() {
    }

    private static final String C_BUKKIT = "org.bukkit.Bukkit";
    private static final String C_ECONOMY = "net.milkbowl.vault.economy.Economy";
    private static final String C_REPONSE = "net.milkbowl.vault.economy.EconomyResponse";
    private static final String C_TYPE = "net.milkbowl.vault.economy.EconomyResponse$ResponseType";
    private static final String C_PRIORITE = "org.bukkit.plugin.ServicePriority";
    private static final String C_OFFLINE = "org.bukkit.OfflinePlayer";
    private static final int ESSAIS_MAX = 12, TICKS_ENTRE_ESSAIS = 100;

    private enum Etat { INACTIF, EN_ATTENTE, ACTIF, ECHEC }

    private static Etat etat = Etat.INACTIF;
    private static int essais, ticks;

    /** « inactif », « actif », « echec », « attente » (pour /eco info). */
    public static String etat() {
        return switch (etat) {
            case INACTIF -> "inactif";
            case EN_ATTENTE -> "attente";
            case ACTIF -> "actif";
            case ECHEC -> "echec";
        };
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        etat = Etat.INACTIF;
        essais = 0;
        ticks = 0;
        if (!EconomieConfig.PONT_VAULT.get()) {
            HeraliszEconomie.LOG.info("[economie] pont Vault desactive par la config");
            return;
        }
        if (!classePresente(C_BUKKIT)) {
            HeraliszEconomie.LOG.info("[economie] pas de Bukkit : pont Vault inutile (serveur Forge pur ou client)");
            return;
        }
        etat = Etat.EN_ATTENTE;
        essayer();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        etat = Etat.INACTIF;
    }

    /** Les plugins peuvent se charger apres nous : on reessaie quelques fois a 5 s d'intervalle. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || etat != Etat.EN_ATTENTE) return;
        if (++ticks < TICKS_ENTRE_ESSAIS) return;
        ticks = 0;
        essayer();
    }

    private static boolean classePresente(String nom) {
        try {
            Class.forName(nom, false, VaultPont.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            try {
                Class.forName(nom, false, Thread.currentThread().getContextClassLoader());
                return true;
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    private static Class<?> classe(String nom, @Nullable ClassLoader prefere) throws ClassNotFoundException {
        if (prefere != null) {
            try {
                return Class.forName(nom, true, prefere);
            } catch (ClassNotFoundException ignored) {
                // on essaie les autres chargeurs
            }
        }
        try {
            return Class.forName(nom, true, VaultPont.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            return Class.forName(nom, true, Thread.currentThread().getContextClassLoader());
        }
    }

    private static void essayer() {
        essais++;
        try {
            Class<?> bukkit = classe(C_BUKKIT, null);
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            Object vault = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "Vault");
            if (vault == null) {
                if (essais >= ESSAIS_MAX) {
                    etat = Etat.INACTIF;
                    HeraliszEconomie.LOG.info("[economie] plugin Vault absent : pont non enregistre");
                }
                return;
            }
            ClassLoader cl = vault.getClass().getClassLoader();
            Class<?> ecoClass = classe(C_ECONOMY, cl);
            Class<?> reponseClass = classe(C_REPONSE, cl);
            Class<?> typeClass = classe(C_TYPE, cl);
            Class<?> prioriteClass = classe(C_PRIORITE, cl);
            Class<?> offlineClass = classe(C_OFFLINE, cl);
            Class<?> pluginClass = classe("org.bukkit.plugin.Plugin", cl);

            Handler h = new Handler(reponseClass, typeClass, offlineClass);
            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{ecoClass}, h);

            Object services = bukkit.getMethod("getServicesManager").invoke(null);
            Object highest = enumConst(prioriteClass, "Highest");
            Method register = services.getClass().getMethod("register", Class.class, Object.class, pluginClass, prioriteClass);
            register.setAccessible(true);
            register.invoke(services, ecoClass, proxy, vault, highest);
            etat = Etat.ACTIF;
            HeraliszEconomie.LOG.info("[economie] pont Vault actif : HeraliZ Économie est le fournisseur Economy (priorite Highest)");
        } catch (Throwable t) {
            etat = Etat.ECHEC;
            HeraliszEconomie.LOG.warn("[economie] pont Vault desactive : {}", t.toString());
        }
    }

    private static Object enumConst(Class<?> e, String nom) {
        for (Object o : e.getEnumConstants()) {
            if (((Enum<?>) o).name().equalsIgnoreCase(nom)) return o;
        }
        throw new IllegalArgumentException(nom + " absent de " + e.getName());
    }

    // ------------------------------------------------------------------ proxy
    /**
     * Implementation de l'interface Economy par reflexion. Les lectures (getBalance, has, format…) marchent depuis
     * n'importe quel fil grace au miroir des soldes du store ; les ecritures (withdraw, deposit, createPlayerAccount)
     * hors du fil serveur sont refusees (FAILURE explicite) et le plugin fautif est journalise une fois, pile a
     * l'appui. Jamais de lookup reseau : un nom (String) inconnu des comptes = « Joueur inconnu ».
     */
    private static final class Handler implements InvocationHandler {
        private static final String HORS_FIL = "HeraliZ Économie : appel hors du fil serveur (le plugin doit passer par le scheduler synchrone)";
        private static final int PILE_MAX = 8, JOURNAL_MAX = 64;

        private final Class<?> reponseClass, typeClass, offlineClass;
        private final Constructor<?> ctorReponse;
        private final Object SUCCES, ECHEC, NON_IMPLEMENTE;
        private final Method getUniqueId;
        /** Cles « methode@classe appelante » deja journalisees, pour ne pas inonder le journal. */
        private final Set<String> journalises = ConcurrentHashMap.newKeySet();

        Handler(Class<?> reponseClass, Class<?> typeClass, Class<?> offlineClass) throws ReflectiveOperationException {
            this.reponseClass = reponseClass;
            this.typeClass = typeClass;
            this.offlineClass = offlineClass;
            ctorReponse = reponseClass.getConstructor(double.class, double.class, typeClass, String.class);
            SUCCES = enumConst(typeClass, "SUCCESS");
            ECHEC = enumConst(typeClass, "FAILURE");
            NON_IMPLEMENTE = enumConst(typeClass, "NOT_IMPLEMENTED");
            getUniqueId = offlineClass.getMethod("getUniqueId");
        }

        private Object reponse(double montant, double solde, Object type, @Nullable String erreur) throws ReflectiveOperationException {
            return ctorReponse.newInstance(montant, solde, type, erreur);
        }

        /** UUID depuis un OfflinePlayer, un UUID ou un nom (String : joueur en ligne ou compte connu). Null si inconnu. */
        @Nullable
        private UUID uuid(@Nullable Object cible) throws ReflectiveOperationException {
            if (cible == null) return null;
            if (cible instanceof UUID u) return u;
            if (offlineClass.isInstance(cible)) return (UUID) getUniqueId.invoke(cible);
            if (cible instanceof String nom) {
                ComptesStore s = ComptesStore.get();
                return s == null ? null : s.resoudre(nom);
            }
            return null;
        }

        /** Solde lisible depuis n'importe quel fil (miroir ; identique a l'API sur le fil serveur). */
        private static long solde(UUID u) {
            ComptesStore s = ComptesStore.get();
            return s == null ? 0 : s.soldeMiroir(u);
        }

        private static boolean valide(double d) {
            return !Double.isNaN(d) && !Double.isInfinite(d);
        }

        private static long entier(double d) {
            if (Double.isNaN(d) || d <= 0) return 0;
            return (long) Math.floor(d + 1e-9);
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] a) throws Throwable {
            String n = m.getName();
            Object[] args = a == null ? new Object[0] : a;
            try {
                switch (n) {
                    case "isEnabled":
                        return etat == Etat.ACTIF;
                    case "getName":
                        return "HeraliZ Économie";
                    case "hasBankSupport":
                        return false;
                    case "fractionalDigits":
                        return 0;
                    case "format":
                        return EconomieApi.format(entierSigne(((Number) args[0]).doubleValue()));
                    case "currencyNamePlural":
                        return EconomieApi.nom(true);
                    case "currencyNameSingular":
                        return EconomieApi.nom(false);
                    case "hasAccount":
                        return true;
                    case "createPlayerAccount": {
                        if (!EconomieApi.disponible()) {
                            journaliserHorsFil(n);
                            return false;
                        }
                        ComptesStore s = ComptesStore.get();
                        UUID u = uuid(args[0]);
                        if (s == null || u == null) return false;
                        s.compte(u, null);
                        return true;
                    }
                    case "getBalance": {
                        UUID u = uuid(args[0]);
                        return u == null ? 0.0 : (double) solde(u);
                    }
                    case "has": {
                        UUID u = uuid(args[0]);
                        double montant = ((Number) args[args.length - 1]).doubleValue();
                        return u != null && solde(u) >= entier(Math.ceil(montant));
                    }
                    case "withdrawPlayer": {
                        UUID u = uuid(args[0]);
                        double d = ((Number) args[args.length - 1]).doubleValue();
                        if (u == null) return reponse(0, 0, ECHEC, "Joueur inconnu");
                        if (!valide(d)) return reponse(0, solde(u), ECHEC, "Montant invalide");
                        if (d < 0) return reponse(d, solde(u), ECHEC, "Montant négatif");
                        if (!EconomieApi.disponible()) {
                            journaliserHorsFil(n);
                            return reponse(0, solde(u), ECHEC, HORS_FIL);
                        }
                        long montant = entier(Math.ceil(d));
                        if (montant == 0) return reponse(0, EconomieApi.solde(u), SUCCES, null);
                        boolean ok = EconomieApi.retirer(u, montant, "vault");
                        return reponse(montant, EconomieApi.solde(u), ok ? SUCCES : ECHEC, ok ? null : "Solde insuffisant");
                    }
                    case "depositPlayer": {
                        UUID u = uuid(args[0]);
                        double d = ((Number) args[args.length - 1]).doubleValue();
                        if (u == null) return reponse(0, 0, ECHEC, "Joueur inconnu");
                        if (!valide(d)) return reponse(0, solde(u), ECHEC, "Montant invalide");
                        if (d < 0) return reponse(d, solde(u), ECHEC, "Montant négatif");
                        if (!EconomieApi.disponible()) {
                            journaliserHorsFil(n);
                            return reponse(0, solde(u), ECHEC, HORS_FIL);
                        }
                        long montant = entier(d);
                        if (montant == 0) return reponse(0, EconomieApi.solde(u), SUCCES, null);
                        // contrat Vault : amount = ce qui a vraiment ete credite ; un depot qui depasserait le plafond est refuse en bloc
                        long place = EconomieApi.maxSolde() - EconomieApi.solde(u);
                        if (montant > place) {
                            return reponse(0, EconomieApi.solde(u), ECHEC, "Compte plein (place : " + EconomieApi.format(Math.max(0, place)) + ")");
                        }
                        EconomieApi.deposer(u, montant, "vault");
                        return reponse(montant, EconomieApi.solde(u), SUCCES, null);
                    }
                    case "getBanks":
                        return new ArrayList<String>();
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "HeraliZ Économie (pont Vault)";
                    default:
                        if (m.getReturnType() == reponseClass) return reponse(0, 0, NON_IMPLEMENTE, "Banques non gérées");
                        if (m.getReturnType() == boolean.class) return false;
                        if (m.getReturnType() == double.class) return 0.0;
                        if (m.getReturnType() == int.class) return 0;
                        if (m.getReturnType() == List.class) return new ArrayList<>();
                        return null;
                }
            } catch (Throwable t) {
                if (journalises.size() < JOURNAL_MAX && journalises.add("erreur:" + n)) {
                    HeraliszEconomie.LOG.warn("[economie] pont Vault : appel {} en echec (journalise une seule fois par methode)", n, t);
                }
                if (m.getReturnType() == reponseClass) return reponse(0, 0, ECHEC, "HeraliZ Économie : " + t.getMessage());
                if (m.getReturnType() == boolean.class) return false;
                if (m.getReturnType() == double.class) return 0.0;
                if (m.getReturnType() == int.class) return 0;
                if (m.getReturnType() == String.class) return "";
                return null;
            }
        }

        /** Journalise (une fois par methode et classe appelante) un plugin qui ecrit hors du fil serveur, avec une pile tronquee. */
        private void journaliserHorsFil(String methode) {
            StackTraceElement[] pile = Thread.currentThread().getStackTrace();
            int debut = 0;
            for (int i = 0; i < pile.length; i++) {
                if (externe(pile[i].getClassName())) {
                    debut = i;
                    break;
                }
            }
            String appelant = pile.length == 0 ? "?" : pile[debut].getClassName();
            if (journalises.size() >= JOURNAL_MAX || !journalises.add(methode + "@" + appelant)) return;
            StringBuilder sb = new StringBuilder(512);
            for (int i = debut; i < pile.length && i < debut + PILE_MAX; i++) sb.append("\n    ").append(pile[i]);
            HeraliszEconomie.LOG.warn("[economie] pont Vault : {} appele hors du fil serveur par {} sur le fil « {} » ; refuse (le plugin doit utiliser le scheduler synchrone). Pile :{}",
                    methode, appelant, Thread.currentThread().getName(), sb);
        }

        /** Vrai pour une classe qui n'est ni a nous, ni au JDK, ni a Vault/au proxy : le plugin appelant. */
        private static boolean externe(String classe) {
            return !(classe.startsWith("fr.heralisz.economie.") || classe.startsWith("java.") || classe.startsWith("jdk.")
                    || classe.startsWith("sun.") || classe.startsWith("com.sun.") || classe.startsWith("net.milkbowl.")
                    || classe.contains("$Proxy"));
        }

        private static long entierSigne(double d) {
            return (long) (d < 0 ? Math.ceil(d) : Math.floor(d + 1e-9));
        }
    }
}
