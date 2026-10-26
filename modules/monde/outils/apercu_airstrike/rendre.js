// Rendus hors ligne des jumelles (images à faire valider avant de toucher au mod) : node rendre.js travaux.json
// Chromium sans écran (Playwright) ouvre apercu.html, charge les modèles préparés par prep.py et rend chaque image.
// travaux.json : {"donnees": "dossier de prep.py", "rendus": [{"sortie": "image.png", ...options}]}
// Options d'un rendu : voir rendre() dans apercu.html (vue fps / tps / gui, version avant / apres, état, recharge...).
const fs = require('fs');
const path = require('path');
const http = require('http');
const { chromium } = require('playwright');

const travaux = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const ICI = __dirname;
const DONNEES = path.resolve(travaux.donnees);
const PAGES = ['apercu.html', 'apres.js'];

const serveur = http.createServer((req, res) => {
  const nom = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '');
  const f = PAGES.includes(nom) ? path.join(ICI, nom) : path.join(DONNEES, nom);
  if (!(PAGES.includes(nom) || f.startsWith(DONNEES + path.sep)) || !fs.existsSync(f)) {
    res.writeHead(404);
    res.end();
    return;
  }
  const ext = path.extname(f);
  const types = {'.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png'};
  res.writeHead(200, {'Content-Type': types[ext] || 'application/json'});
  fs.createReadStream(f).pipe(res);
});

(async () => {
  await new Promise(r => serveur.listen(0, '127.0.0.1', r));
  const port = serveur.address().port;
  // rendu logiciel (SwiftShader) : même résultat sur toutes les machines, pas besoin de carte graphique
  const navigateur = await chromium.launch({args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist']});
  const page = await navigateur.newPage();
  page.on('pageerror', e => console.log('[erreur]', e.message));
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.log('[console]', m.text()); });
  await page.goto(`http://127.0.0.1:${port}/apercu.html`);
  console.log('modèles :', await page.evaluate(() => window.apercu.charger('')));
  for (const a of travaux.armes || []) console.log(a, await page.evaluate(n => window.apercu.chargerArme(n), a));
  let n = 0;
  const t0 = Date.now();
  for (const r of travaux.rendus) {
    try {
      const url = await page.evaluate(o => window.apercu.rendre(o), r);
      fs.mkdirSync(path.dirname(path.resolve(r.sortie)), {recursive: true});
      fs.writeFileSync(r.sortie, Buffer.from(url.split(',')[1], 'base64'));
      n++;
    } catch (e) {
      console.log('ECHEC', r.sortie, e.message.substring(0, 4000));
    }
  }
  console.log(n + ' rendus en ' + ((Date.now() - t0) / 1000).toFixed(1) + ' s');
  await navigateur.close();
  serveur.close();
})();
