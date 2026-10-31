# Essais en jeu du 31/10 : taxes de l'hôtel des ventes

Rien de ce qui suit n'a été testé en jeu ni compilé par Gradle ici (la fabrication de GitHub le fera). Le code PHP de
l'extension du site a seulement passé `php -l`.

Grades 2.10.1, Boutique 1.3.1, extension du site HeraliZ 1.3.2. Canal `heraliz_grades:hdv` en protocole 3 : jars
joueurs et serveur de la même fabrication, sinon le joueur est refusé à la connexion.

**Mise en ligne** : relancer « Site HeraliZ », choix « theme », avant (ou en même temps que) la mise en ligne du serveur.
Dans le mauvais ordre rien n'est perdu ni payé deux fois : seule la ligne « PB détruits par la taxe » de `/pb stats`
oublie la taxe de l'hôtel des ventes tant que le site n'est pas à jour.

## Règle

- Vendre : taxe de mise en vente inchangée, 12,5 % du prix en hérals, payée à la publication, jamais rendue.
- Acheter en PB : l'acheteur paie le prix en PB + 10 % (arrondi au PB supérieur), le vendeur reçoit le prix affiché, les
  10 % sont brûlés. Réglage `frais_pb_pourcent` de `config/heraliz_grades/hdv.json` (relu toutes les 10 s).
- Acheter en hérals : le prix affiché, rien de plus.
- Aucune autre taxe sur une vente en PB (le vendeur ne paie rien en PB).

## À vérifier

- [ ] Offre à 100 PB, « Payer en PB » : lignes « Prix du vendeur 100 PB », « Taxe PB (10 %) + 10 PB », « Tu paies 110 PB » ;
      bouton « Acheter · 110 PB » puis « Confirmer · 110 PB ». Après l'achat : 110 PB en moins pour l'acheteur, 100 PB
      en plus pour le vendeur (site), l'objet chez l'acheteur.
- [ ] Offre à 15 PB : l'acheteur paie 17 PB (1,5 arrondi à 2), le vendeur reçoit 15.
- [ ] Même offre, « Payer en hérals » : « Taxe (hérals : aucune) 0 », l'acheteur paie exactement le prix en hérals.
- [ ] Onglet Vendre avec « Hérals + PB », prix 100 PB : « L'acheteur paie … · 110 PB », « Tu reçois … ou 100 PB » ;
      taxe de mise en vente toujours 12,5 % du prix en hérals.
- [ ] Changer `frais_pb_pourcent` (10 -> 20) pendant que l'écran d'un acheteur est ouvert, attendre 10 s, confirmer
      l'achat sans rouvrir : refus rouge « La taxe des achats en PB a changé… », rien n'est débité, la liste se remet à
      jour (120 PB affichés), un nouvel achat paie 120. Remettre 10 ensuite.
- [ ] `/pb stats` (staff) : « PB détruits par la taxe » augmente de 10 après l'achat à 110 PB (site à jour).
- [ ] Journal `heraliz_grades/hdv/journal.log` : « pour 110 PB dont 10 de taxe (débit demandé) ».
- [ ] Solde PB trop bas pour le total (ex. 105 PB pour 110) : bouton « solde trop bas », rien ne part.
- [ ] Double-clic rapide, deux fenêtres : un seul débit, un seul objet.
- [ ] (Staff, serveur d'essai) site coupé pendant un achat en PB : l'achat attend, puis se termine ou s'annule ; jamais
      deux débits, jamais d'objet sans paiement.

**Non-régression** : achats en hérals, anciennes offres seulement en PB, « Mes ventes », versement du vendeur en PB,
`/trade` (sa taxe de 10 % sur ce que reçoit le destinataire ne change pas), achats de la boutique en PB.
