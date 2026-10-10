/* global window */
/*
 * Nouveautés de l'accueil du launcher, la plus récente en premier : seules les trois premières sont affichées, en
 * rectangles non cliquables (pas d'onglet dédié, choix du propriétaire). Images dans assets/images/nouveautes/
 * (960 × 540). « une » : mise en avant (bord rouge).
 */
window.HERALIZ_NOUVEAUTES = Object.freeze([
    {
        image: 'createur-personnage.jpg', etiquette: 'Mise à jour', une: true,
        titre: 'Nouveau créateur de personnage',
        texte: 'Visage, cicatrices, tenues : crée ton survivant.'
    },
    {
        image: 'crac-plan-marteau.jpg', etiquette: 'Construction',
        titre: 'Plan et marteau repensés',
        texte: 'Roues plus claires, coûts lisibles.'
    },
    {
        image: 'caisses-hd.jpg', etiquette: 'Caisses',
        titre: 'Caisses HD et roue de butin',
        texte: 'Ouvre-les en jeu avec tes clés.'
    }
])
