# Brouiller nos mods (protection contre la décompilation)

But : rendre le code de **nos** mods pénible à lire si quelqu'un ouvre les jars. On ne peut pas empêcher la
décompilation à 100 % sur Forge, mais on peut effacer les numéros de ligne et les noms des variables, et renommer les
parties privées : le code décompilé devient illisible.

**Important** : un brouillage mal réglé empêche le serveur de démarrer. On le fait **un mod à la fois**, et on
**essaie le mod en jeu** avant de le mettre en ligne.

## Ce qui est épargné (sinon ça casse)

- aucune **classe** n'est renommée (les mods s'appellent entre eux par le nom des classes : API, Mixins, GeckoLib,
  `@Mod`, enregistrements Forge) ;
- le **code des autres auteurs** n'est jamais touché (Tactical, HBM, Backpacked, Emotecraft, PlayerAnimator, Concentus) ;
- **Minecraft et Forge** sont donnés comme « bibliothèque » : jamais renommés, donc `verif_reobf` reste vert.

Seuls les **champs et méthodes privés de notre code** sont renommés, et les numéros de ligne / noms de variables effacés.

## Comment faire (sur le PC, pas sur GitHub)

Pour un mod, par exemple **airstrike** (c'est le mod d'essai, le plus simple) :

```
python outils/construire.py airstrike            # 1. fabrique les jars normaux
python outils/brouiller.py  airstrike --verifier  # 2. brouille + recontrôle la réobfuscation
python outils/verifier.py   airstrike            # 3. tous les contrôles (0 problème exigé)
```

Puis **essayer airstrike en jeu** (frappes aériennes, caisses de ravitaillement, menu créatif). Si tout marche :
`python outils/construire.py --sans-build` range les jars brouillés dans `livraison/`, et on met en ligne comme
d'habitude (`git add`, `git commit`, `git push`).

Si quelque chose cloche en jeu : `python outils/brouiller.py airstrike --annuler` remet les jars d'avant le brouillage
(gardés dans `build/libs/.avant_brouillage/`).

## Faire les autres mods

On recommence mod par mod, dans l'ordre du plus simple au plus relié. À chaque mod : fabriquer, brouiller, vérifier,
**essayer en jeu**, puis mettre en ligne. Si un mod pose souci, on peut lui ajouter des règles dans un fichier
`outils/brouillage/<mod>.pro` (voir `airstrike.pro`), ou le laisser non brouillé.

**À ne jamais brouiller** (code d'autres auteurs, licences — CLAUDE.md §11) : le code tiers DANS heraliz-tactique,
heralisz-bunker, heralisz-backpack, heraliz-emote et caracrea est déjà épargné automatiquement par `commun.pro`, donc
ces mods peuvent être brouillés sans risque pour les licences (seule notre part est renommée). Si un doute, laisser
le mod non brouillé.

## Fichiers ici

- `commun.pro` — les règles communes à tous les mods (ce qui est gardé / renommé) ;
- `<mod>.pro` — règles en plus pour un mod précis (facultatif ; `airstrike.pro` existe) ;
- `.proguard/` — ProGuard téléchargé tout seul au premier lancement (hors Git).

Le brouillage n'est **pas** branché dans la fabrication automatique ni dans les workflows : c'est exprès, pour qu'il
reste manuel et essayé en jeu mod par mod.
