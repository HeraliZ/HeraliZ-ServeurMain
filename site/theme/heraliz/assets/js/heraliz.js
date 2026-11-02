/*
 * Thème HeraliZ : petits scripts des pages (le fond « carte tactique » dessiné ici n'était plus affiché nulle part :
 * retiré le 02/11).
 */

// bouton « Copier » de l'adresse du serveur (accueil)
document.addEventListener('click', function (ev) {
    const b = ev.target.closest('[data-copier]');
    if (!b) return;
    const ok = function () { b.textContent = 'Copié'; setTimeout(function () { b.textContent = 'Copier'; }, 1500); };
    try { navigator.clipboard.writeText(b.dataset.copier).then(ok, function () {}); } catch (e) { /* ancien navigateur */ }
});
