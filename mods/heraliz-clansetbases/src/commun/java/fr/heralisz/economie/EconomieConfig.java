package fr.heralisz.economie;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages communs (config/heralisz_economie-common.toml). Lus cote serveur ; le client recoit ce qui lui est utile
 * (noms, symbole, HUD permanent) par le paquet ReglagesS2C a la connexion.
 */
public final class EconomieConfig {
    private EconomieConfig() {
    }

    public static final ForgeConfigSpec SPEC;

    /** Nom de la monnaie (D-04x) : les « hérals », du nom de HeraliZ. Ancien nom « jeton(s) » converti au chargement. */
    public static final String DEFAUT_SINGULIER = "héral", DEFAUT_PLURIEL = "hérals";

    public static final ForgeConfigSpec.ConfigValue<String> NOM_SINGULIER;
    public static final ForgeConfigSpec.ConfigValue<String> NOM_PLURIEL;
    public static final ForgeConfigSpec.ConfigValue<String> SYMBOLE;
    public static final ForgeConfigSpec.LongValue SOLDE_DEPART;
    public static final ForgeConfigSpec.LongValue MAX_SOLDE;
    public static final ForgeConfigSpec.BooleanValue HUD_PERMANENT;
    public static final ForgeConfigSpec.LongValue PAYER_MIN;
    public static final ForgeConfigSpec.IntValue PAYER_CADENCE_S;
    public static final ForgeConfigSpec.BooleanValue PONT_VAULT;
    public static final ForgeConfigSpec.LongValue SAUVEGARDE_IMMEDIATE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Monnaie du serveur HeraliZ").push("monnaie");
        // Bornes = limites des chaines du paquet ReglagesS2C ; une valeur trop longue est remise au defaut par Forge.
        NOM_SINGULIER = b.comment("Nom de l'unite au singulier, aussi pour 0 (« 0 héral », « 1 héral »), 32 caracteres max").define("nom_singulier", DEFAUT_SINGULIER, o -> texte(o, 32));
        NOM_PLURIEL = b.comment("Nom de l'unite au pluriel, a partir de 2 (« 250 hérals »), 32 caracteres max").define("nom_pluriel", DEFAUT_PLURIEL, o -> texte(o, 32));
        SYMBOLE = b.comment("Symbole affiche apres les montants (8 caracteres max) ; vide = le mot (héral/hérals)").define("symbole", "", o -> texte(o, 8));
        SOLDE_DEPART = b.comment("Solde d'un nouveau compte").defineInRange("solde_depart", 0L, 0L, Long.MAX_VALUE / 4);
        MAX_SOLDE = b.comment("Plafond d'un compte (les depots au-dela sont tronques)").defineInRange("max_solde", 10_000_000L, 1L, Long.MAX_VALUE / 4);
        b.pop();

        b.comment("Paiements entre joueurs (/payer)").push("paiements");
        PAYER_MIN = b.comment("Montant minimal d'un paiement").defineInRange("payer_min", 1L, 1L, Long.MAX_VALUE / 4);
        PAYER_CADENCE_S = b.comment("Secondes minimales entre deux paiements d'un meme joueur").defineInRange("payer_cadence_s", 2, 0, 3600);
        b.pop();

        b.comment("Affichage").push("affichage");
        HUD_PERMANENT = b.comment("true : le solde reste affiche en petit dans le coin (sinon seulement 3 s a chaque changement)")
                .define("hud_permanent", false);
        b.pop();

        b.comment("Stockage et integration").push("systeme");
        SAUVEGARDE_IMMEDIATE = b.comment("Toute transaction d'un montant superieur ou egal declenche une sauvegarde immediate (sinon autosave 60 s)")
                .defineInRange("sauvegarde_immediate", 1000L, 0L, Long.MAX_VALUE / 4);
        PONT_VAULT = b.comment("true : sur Mohist, enregistre cette economie comme fournisseur Vault pour les plugins")
                .define("pont_vault", true);
        b.pop();
        SPEC = b.build();
    }

    /**
     * Conversion de l'ancien nom de la monnaie : une config qui dit encore « jeton » / « jetons » (valeurs par defaut
     * des versions 0.1.0) passe a « héral » / « hérals ». Un nom choisi a la main (autre que l'ancien defaut) est garde.
     * Appele aux evenements Loading / Reloading de la config ; ne reecrit le fichier que s'il y a eu un changement.
     */
    static void convertirAncienNom() {
        boolean change = false;
        if ("jeton".equals(NOM_SINGULIER.get())) {
            NOM_SINGULIER.set(DEFAUT_SINGULIER);
            change = true;
        }
        if ("jetons".equals(NOM_PLURIEL.get())) {
            NOM_PLURIEL.set(DEFAUT_PLURIEL);
            change = true;
        }
        if (change) {
            NOM_SINGULIER.save();
            HeraliszEconomie.LOG.info("[economie] nom de la monnaie converti : « jetons » -> « {} / {} »", NOM_SINGULIER.get(), NOM_PLURIEL.get());
        }
    }

    /** Validateur : chaine d'au plus max caracteres. */
    private static boolean texte(Object o, int max) {
        return o instanceof String s && s.length() <= max;
    }
}
