// Rendus hors ligne des camos (images à faire valider avant de toucher au mod) : node rendre.js travaux.json
// Chromium sans écran (Playwright) ouvre apercu.html, y charge la partie CAMO LIB d'un shader du mod et dessine des armes
// préparées par prep.py. travaux.json :
//   {"donnees": "dossier des armes préparées (nom.json + nom.png)", "armes": ["xm177", ...],
//    "passes": [{"shader": "chemin d'un camo_<id>.fsh ou du gabarit", "defines": "lignes #define en plus (facultatif)",
//                "ancien": false, "rendus": [{"arme": "xm177", "camo": 1, "sortie": "rendu.png", ...options}]}]}
// "ancien": true rend avec le main d'avant la refonte 2026-10 (pour les comparaisons avant / après).
// Options d'un rendu (voir rendre() dans apercu.html) : vue "orbite" (yaw, pitch, zoom), "fps" (envYaw, envPitch :
// direction où regarde le joueur) ou "icone" (icône d'inventaire : roule, marge), temps (CamoTime), w, h, relief (affiche
// la carte de relief). Camo 0 : l'arme sans camo.
const fs = require('fs');
const path = require('path');
const http = require('http');
const { chromium } = require('playwright');

const travaux = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const ICI = __dirname;
const DONNEES = path.resolve(travaux.donnees);

const serveur = http.createServer((req, res) => {
  const nom = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '');
  const f = nom === 'apercu.html' ? path.join(ICI, nom) : path.join(DONNEES, nom);
  if (!(f.startsWith(DONNEES) || f === path.join(ICI, 'apercu.html')) || !fs.existsSync(f)) {
    res.writeHead(404);
    res.end();
    return;
  }
  const ext = path.extname(f);
  res.writeHead(200, {'Content-Type': ext === '.html' ? 'text/html' : ext === '.png' ? 'image/png' : 'application/json'});
  fs.createReadStream(f).pipe(res);
});

(async () => {
  await new Promise(r => serveur.listen(0, '127.0.0.1', r));
  const port = serveur.address().port;
  // rendu logiciel (SwiftShader) : même résultat sur toutes les machines, pas besoin de carte graphique
  const navigateur = await chromium.launch({args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist']});
  const page = await navigateur.newPage();
  page.on('pageerror', e => console.log('[erreur]', e.message));
  await page.goto(`http://127.0.0.1:${port}/apercu.html`);
  for (const nom of travaux.armes) await page.evaluate(n => window.apercu.chargerArme(n), nom);
  let n = 0;
  const t0 = Date.now();
  for (const passe of travaux.passes) {
    const texte = fs.readFileSync(passe.shader, 'utf8');
    await page.evaluate(([t, d, a]) => window.apercu.chargerLib(t, d, a), [texte, passe.defines || '', !!passe.ancien]);
    for (const r of passe.rendus) {
      try {
        const url = await page.evaluate(([a, c, o]) => window.apercu.rendre(a, c, o), [r.arme, r.camo, r]);
        fs.mkdirSync(path.dirname(path.resolve(r.sortie)), {recursive: true});
        fs.writeFileSync(r.sortie, Buffer.from(url.split(',')[1], 'base64'));
        n++;
      } catch (e) {
        console.log('ECHEC', r.sortie, e.message.substring(0, 4000));
      }
    }
  }
  console.log(n + ' rendus en ' + ((Date.now() - t0) / 1000).toFixed(1) + ' s');
  await navigateur.close();
  serveur.close();
})();
