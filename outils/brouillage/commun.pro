# ------------------------------------------------------------------------------------------------------------------
# Réglage ProGuard COMMUN à tous nos mods — brouillage PRUDENT (« modéré », CLAUDE.md §11).
#
# Ce que ce réglage fait :
#   - il GARDE le nom de TOUTES les classes (aucune classe renommée) : les mods s'appellent entre eux par le nom des
#     classes (API par réflexion, Mixins, GeckoLib, enregistrements Forge, @Mod) — renommer une classe casse le serveur ;
#   - il garde aussi tous les membres PUBLICS et PROTÉGÉS de notre code (API appelées de l'extérieur) ;
#   - il RENOMME seulement les champs et méthodes PRIVÉS de notre code, et il EFFACE les numéros de ligne, le nom du
#     fichier source et le nom des variables locales → la décompilation devient illisible, sans aucun renommage risqué ;
#   - il NE raccourcit RIEN et n'OPTIMISE RIEN (c'est ça qui supprimerait du code utilisé par réflexion / Mixin).
#
# Le Minecraft et le Forge en noms SRG sont passés en -libraryjars par outils/brouiller.py : ProGuard ne les renomme
# jamais, donc verif_reobf.py reste vert (les références à Minecraft gardent leurs noms SRG f_123_ / m_123_).
#
# Pour brouiller DAVANTAGE un mod précis (renommer aussi ses classes internes une fois l'essai en jeu concluant), on
# ajoute des règles dans son fichier outils/brouillage/<mod>.pro ; par défaut, ici, aucune classe n'est renommée.
# ------------------------------------------------------------------------------------------------------------------

# --- jamais raccourcir ni optimiser (seul le renommage est sûr sur Forge) ---
-dontshrink
-dontoptimize
-dontwarn
-ignorewarnings
-dontnote
-keeppackagenames
-forceprocessing

# --- aucune classe renommée (partout) : seuls les membres privés de notre code seront renommés ---
-keepnames class **

# --- attributs indispensables à Forge, aux Mixins et à GeckoLib (annotations, génériques, classes internes…) ---
# NB : on NE garde PAS SourceFile / LineNumberTable / LocalVariable(Type)Table → ils sont effacés (but du brouillage).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,Deprecated,RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault,PermittedSubclasses,Record,BootstrapMethods,NestHost,NestMembers

# ------------------------------------------------------------------------------------------------------------------
# CODE DES AUTRES AUTEURS — jamais touché (licences à garder lisibles, CLAUDE.md §3.2 et §11).
# Gardé EN ENTIER (membres privés compris) : aucun renommage, aucune suppression.
# ------------------------------------------------------------------------------------------------------------------
-keep class fr.heralisz.tactical.** { *; }      # LesRaisins Tactical (GPL), dans heraliz-tactique
-keep class com.hbm.**            { *; }         # HBM Modernized (GPL), dans heralisz-bunker
-keep class com.mrcrayfish.**     { *; }         # MrCrayfish Backpacked (LGPL), dans heralisz-backpack
-keep class dev.kosmx.**          { *; }         # PlayerAnimator (MIT), dans heraliz-emote
-keep class io.github.kosmx.**    { *; }         # Emotecraft (CC-BY-4.0), dans heraliz-emote
-keep class org.concentus.**      { *; }         # Concentus (BSD), dans caracrea

# ------------------------------------------------------------------------------------------------------------------
# NOTRE CODE — classes gardées par leur nom (ci-dessus), membres publics/protégés gardés, membres privés renommés.
# ------------------------------------------------------------------------------------------------------------------
-keepclassmembers class fr.heraliz.**,fr.heralisz.**,com.heraliz.**,com.caracrea.** {
    public    *;
    protected *;
}

# --- Mixins : une classe @Mixin est gardée ENTIÈRE (ses @Shadow / @Accessor portent le nom SRG de la cible) ---
-keep @org.spongepowered.asm.mixin.Mixin class * { *; }

# --- méthodes d'évènement Forge gardées (trouvées par annotation + appelées par le bus) ---
-keepclassmembers class * {
    @net.minecraftforge.eventbus.api.SubscribeEvent <methods>;
}

# --- enums : values() / valueOf() sont parfois appelés par réflexion ou lus par nom (NBT) ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- constructeurs/usines d'entités, blocs-entités, paquets : gardés par les règles publiques ci-dessus ---
# (rien de plus ici : Forge enregistre par chaîne de caractères, pas par nom de champ)
