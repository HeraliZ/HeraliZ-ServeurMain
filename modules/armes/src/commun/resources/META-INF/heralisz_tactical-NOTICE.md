# HeralisZ Tactical

Moteur tactique du serveur HeralisZ : armes de mêlée, soins et lancers. Le contenu vit dans les mods
`heralisz_melee`, `heralisz_heal` et `heralisz_grenade`.

Ce mod est une version modifiée de **LesRaisins Tactical Equipments 0.4.3** de **LesRaisins Studio**, distribué sous
licence **GNU GPL 3.0**. Il reste sous la même licence : en cas de distribution, fournir ce code source et la licence
GPL 3.0 (https://www.gnu.org/licenses/gpl-3.0.html).

Modifications HeralisZ (2026-09-27) :
- Renommage : paquet `fr.heralisz.tactical`, identifiant et espace de noms `heralisz_tactical`, textes « HeralisZ ».
- Le contenu d'origine (index et recettes de mêlée, soins, lancers) est retiré du moteur et repris dans nos mods de contenu.
- Les armes de mêlée sont rangées dans l'onglet Heavy Weapon de TaCZ, avec les couteaux ; le Flash Shield n'est plus proposé.
- Les icônes des onglets pointent sur nos objets.

Modifications HeraliZ (2026-10-13) :
- Le coup de mêlée sur une cible (`IMeleeWeapon.performAttack`) est sorti dans `IMeleeWeapon.attackTarget`, sans
  changement, pour donner les mêmes coups aux couteaux TaCZ ; réglages des couteaux dans la config serveur (`[couteaux]`).

Sources obtenues en décompilant le jar désobfusqué (Vineflower 1.12), voir `tools/fork_tactical.py`.
