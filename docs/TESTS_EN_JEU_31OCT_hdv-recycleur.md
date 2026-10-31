# Essais en jeu du 31/10 : hôtel des ventes (taxe, prix en hérals) et recycleur de la carte

Rien de ce qui suit n'a été testé en jeu ni compilé par Gradle ici (la fabrication de GitHub le fera).

## Hôtel des ventes (Grades 2.10.0, Boutique 1.3.0)

Canal `heraliz_grades:hdv` passé en protocole 2 : jars joueurs et serveur de la même fabrication, sinon le joueur est
refusé à la connexion. Réglage : `config/heraliz_grades/hdv.json`, `taxe_mise_en_vente_pourcent` (12.5 par défaut, relu
toutes les 10 s).

**Prix en hérals obligatoire (15.1)**
- [ ] Onglet Vendre : « Monnaie » propose « Hérals » ou « Hérals + PB ». Avec « Hérals + PB », deux cases de prix (hérals à
      gauche, PB à droite ; Tab passe de l'une à l'autre). Sans prix en hérals : bouton grisé et texte rouge « Le prix en
      hérals est obligatoire, même avec un prix en PB ».
- [ ] Une vente « Hérals + PB » apparaît dans la liste avec les deux prix (le second en petit) ; filtre « PB » : le prix en
      PB en premier ; filtre « Hérals » : elle y est aussi.
- [ ] L'acheteur choisit « Payer en hérals » ou « Payer en PB » dans le détail de l'offre ; il paie dans la monnaie choisie,
      le vendeur reçoit son prix dans cette même monnaie (hérals dans « Mes ventes », PB sur le site).
- [ ] Une ancienne vente seulement en PB (mise avant cette version) reste achetable en PB jusqu'à sa fin, puis revient au
      vendeur comme avant.

**Taxe de mise en vente (15.2 / 15.3)**
- [ ] Prix 10 000 hérals : ligne « Taxe de mise en vente (12,5 %) : 1 250 hérals » avant de confirmer. Premier clic sur
      « Mettre en vente » : le bouton devient « Confirmer · taxe 1 250 hérals » ; second clic dans les 5 s : l'objet part,
      le solde baisse de 1 250 tout de suite, message gris dans le chat.
- [ ] Avec « Hérals + PB », la taxe est la même (calculée sur le prix en hérals).
- [ ] Pas assez de hérals pour la taxe : taxe en rouge, bouton « Pas assez de hérals pour la taxe », rien ne bouge.
- [ ] Retirer la vente, ou la laisser expirer : l'objet revient, la taxe n'est PAS rendue.
- [ ] Double-clic rapide, Entrée tenue, deux fenêtres : une seule taxe, une seule vente.
- [ ] Changer le taux dans `hdv.json` pendant que l'écran est ouvert, puis confirmer : refus « La taxe a changé »,
      la nouvelle taxe s'affiche, rien n'est débité.
- [ ] Vente « Hérals + PB » sans compte sur le site : refusée, aucune taxe prélevée.
- [ ] Journal `heraliz_grades/hdv/journal.log` : « taxe N hérals payée » sur chaque mise en vente ; journal du compte de
      hérals : motif `hdv:taxe:<offre>`.
- [ ] (Staff, serveur d'essai) arrêt brutal du serveur pendant une mise en vente : au retour, soit l'objet est en vente et
      la taxe payée, soit l'objet est resté dans l'inventaire et la taxe attend dans « Mes ventes » (« Taxe rendue »).
      Jamais les deux, jamais aucun.

**Non-régression** : achats en hérals et en PB, « Tout récupérer », objets grisés, plafonds PB par rareté, 5 ventes au plus,
`/wipe`, refus hors du spawn, en combat, à terre, en match.
