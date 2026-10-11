/*
 * Thème HeraliZ : fond « carte tactique » (courbes de niveau d'un relief inventé, quadrillage et coordonnées, comme
 * le launcher) dessiné dans chaque <canvas data-topo>. Le relief est calculé
 * dans l'espace 1180 × 780 de la maquette puis étiré au cadre ; il n'est redessiné que si la taille du cadre change :
 * rien d'animé, aucun coût pendant la lecture de la page.
 */
(function () {
    'use strict';

    const W0 = 1180, H0 = 780, PAS = 6, ECART = 0.18, NIVEAUX = 40;

    function relief(x, y) {
        const d = Math.hypot(x - 860, y - 330);
        return Math.sin(x * 0.006 + 1.3) * 1.3 + Math.cos(y * 0.008 - 0.4) * 1.1 + Math.sin((x + y) * 0.004) * 1.6
            + Math.sin(x * 0.017 - y * 0.011) * 0.45 + Math.cos(d * 0.02) * 1.2 * Math.exp(-d / 260);
    }

    // hauteurs aux coins de la grille, calculées une seule fois pour tous les cadres
    const nx = Math.ceil(W0 / PAS) + 1, ny = Math.ceil(H0 / PAS) + 1;
    let hauteurs = null;

    function grille() {
        if (hauteurs === null) {
            hauteurs = new Float32Array(nx * ny);
            for (let j = 0; j < ny; j++) {
                for (let i = 0; i < nx; i++) {
                    hauteurs[j * nx + i] = relief(i * PAS, j * PAS);
                }
            }
        }
        return hauteurs;
    }

    function dessiner(canvas) {
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        const w = Math.max(1, Math.round(canvas.clientWidth * dpr));
        const h = Math.max(1, Math.round(canvas.clientHeight * dpr));
        if (canvas.width === w && canvas.height === h && canvas.dataset.pret === '1') {
            return;
        }
        canvas.width = w;
        canvas.height = h;
        const g = canvas.getContext('2d');
        const sx = w / W0, sy = h / H0;
        const v = grille();
        g.fillStyle = '#07080b';
        g.fillRect(0, 0, w, h);

        // une seule passe sur les cases (marching squares), un seul trait par niveau
        const chemins = new Map();
        const pts = [];
        for (let j = 0; j < ny - 1; j++) {
            for (let i = 0; i < nx - 1; i++) {
                const a = v[j * nx + i], b = v[j * nx + i + 1], c = v[(j + 1) * nx + i + 1], d = v[(j + 1) * nx + i];
                const k0 = Math.max(-NIVEAUX, Math.ceil(Math.min(a, b, c, d) / ECART));
                const k1 = Math.min(NIVEAUX, Math.floor(Math.max(a, b, c, d) / ECART));
                const x = i * PAS, y = j * PAS;
                for (let k = k0; k <= k1; k++) {
                    const lv = k * ECART;
                    pts.length = 0;
                    const bord = function (p, q, x0, y0, x1, y1) {
                        if ((p < lv) !== (q < lv)) {
                            const t = (lv - p) / (q - p);
                            pts.push((x0 + (x1 - x0) * t) * sx, (y0 + (y1 - y0) * t) * sy);
                        }
                    };
                    bord(a, b, x, y, x + PAS, y);
                    bord(b, c, x + PAS, y, x + PAS, y + PAS);
                    bord(c, d, x + PAS, y + PAS, x, y + PAS);
                    bord(d, a, x, y + PAS, x, y);
                    if (pts.length < 4) {
                        continue;
                    }
                    let chemin = chemins.get(k);
                    if (!chemin) {
                        chemin = new Path2D();
                        chemins.set(k, chemin);
                    }
                    chemin.moveTo(pts[0], pts[1]);
                    chemin.lineTo(pts[2], pts[3]);
                    if (pts.length === 8) {
                        chemin.moveTo(pts[4], pts[5]);
                        chemin.lineTo(pts[6], pts[7]);
                    }
                }
            }
        }
        chemins.forEach(function (chemin, k) {
            const majeur = k % 5 === 0;
            g.strokeStyle = majeur ? 'rgba(236,238,242,0.13)' : 'rgba(236,238,242,0.055)';
            g.lineWidth = (majeur ? 1.1 : 0.8) * dpr;
            g.stroke(chemin);
        });

        // quadrillage tous les 98 px de la maquette, lettres en haut et numéros à gauche
        g.strokeStyle = 'rgba(236,238,242,0.04)';
        g.lineWidth = dpr;
        g.beginPath();
        for (let x = 0; x < W0; x += 98) {
            g.moveTo(Math.round(x * sx) + 0.5, 0);
            g.lineTo(Math.round(x * sx) + 0.5, h);
        }
        for (let y = 0; y < H0; y += 98) {
            g.moveTo(0, Math.round(y * sy) + 0.5);
            g.lineTo(w, Math.round(y * sy) + 0.5);
        }
        g.stroke();
        g.fillStyle = 'rgba(236,238,242,0.18)';
        g.font = '700 ' + (9 * dpr) + 'px "DM Sans", sans-serif';
        'ABCDEFGHIJKL'.split('').forEach(function (l, i) {
            g.fillText(l, (i * 98 + 6) * sx, 46 * sy);
        });
        for (let i = 1; i < 8; i++) {
            g.fillText(String(i).padStart(2, '0'), 6 * sx, (i * 98 + 14) * sy);
        }
        canvas.dataset.pret = '1';
    }

    function toutDessiner() {
        document.querySelectorAll('canvas[data-topo]').forEach(dessiner);
    }

    let attente;
    window.addEventListener('resize', function () {
        clearTimeout(attente);
        attente = setTimeout(toutDessiner, 200);
    });

    function demarrer() {
        toutDessiner();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', demarrer);
    } else {
        demarrer();
    }
    // la police des coordonnées peut arriver après le premier dessin
    if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(function () {
            document.querySelectorAll('canvas[data-topo]').forEach(function (c) {
                c.dataset.pret = '0';
            });
            toutDessiner();
        });
    }
})();
