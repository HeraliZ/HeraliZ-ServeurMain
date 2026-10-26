// Spartan AntiCheat — intégration côté launcher (Node.js / Electron).
// Uniquement des modules natifs de Node : aucune dépendance à installer.
//
// Ce fichier fait trois choses :
//   1. verifyModsFolder : vérifie que le dossier mods contient EXACTEMENT les mods
//      officiels (rien en plus, rien de modifié) avant de lancer le jeu ;
//   2. modsHash         : calcule l'empreinte du dossier mods (même calcul que le mod) ;
//   3. createLaunchToken: fabrique le jeton signé à passer au jeu.
//
// Format et règles : voir docs/LAUNCHER.md. Doit rester identique à
// src/main/java/com/Spartan/integrity/LaunchToken.java (vérifié l'un contre l'autre).

'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const CHUNK = 1 << 20; // lecture par blocs de 1 Mio : pas de jar entier en mémoire
const MAX_LIFETIME_S = 30 * 24 * 3600; // le serveur refuse au-delà

/** SHA-256 d'un fichier, lu par blocs (gros jars sans saturer la mémoire). */
function sha256File(file) {
  const hash = crypto.createHash('sha256');
  const fd = fs.openSync(file, 'r');
  try {
    const buf = Buffer.allocUnsafe(CHUNK);
    let n;
    while ((n = fs.readSync(fd, buf, 0, CHUNK, null)) > 0) {
      hash.update(buf.subarray(0, n));
    }
  } finally {
    fs.closeSync(fd);
  }
  return hash.digest('hex');
}

function sha256Hex(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

/** Tri par code UTF-16, comme List.sort(null) en Java. */
function byCodeUnit(a, b) {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Type d'une entrée en suivant les liens : 'file', 'dir' ou null. */
function kindOf(p) {
  try {
    const st = fs.statSync(p);
    return st.isFile() ? 'file' : st.isDirectory() ? 'dir' : null;
  } catch (e) {
    return null;
  }
}

/**
 * Empreinte d'un dossier (mod « éclaté ») : lignes "chemin-relatif:sha256" de tous
 * ses fichiers, récursivement. Comme Files.walk en Java : un lien vers un FICHIER
 * est compté, un lien vers un DOSSIER n'est pas parcouru.
 */
function sha256Dir(dir) {
  const lines = [];
  (function walk(current) {
    for (const name of fs.readdirSync(current)) {
      const p = path.join(current, name);
      const isLink = fs.lstatSync(p).isSymbolicLink();
      const kind = kindOf(p);
      if (kind === 'file') {
        const rel = path.relative(dir, p).split(path.sep).join('/');
        lines.push(rel + ':' + sha256File(p));
      } else if (kind === 'dir' && !isLink) {
        walk(p);
      }
    }
  })(dir);
  lines.sort(byCodeUnit);
  return sha256Hex(lines.join('\n'));
}

/**
 * Entrées « .jar » du dossier mods (non récursif) : fichiers ET dossiers (mods
 * éclatés), liens suivis. [{ name (minuscules), file, kind }]
 */
function listJars(modsDir) {
  if (!fs.existsSync(modsDir)) return [];
  return fs.readdirSync(modsDir)
    .filter((n) => n.toLowerCase().endsWith('.jar'))
    .map((n) => ({ name: n.toLowerCase(), file: path.join(modsDir, n), kind: kindOf(path.join(modsDir, n)) }))
    .filter((j) => j.kind === 'file' || j.kind === 'dir');
}

function entryHash(j) {
  return j.kind === 'file' ? sha256File(j.file) : 'dir:' + sha256Dir(j.file);
}

/**
 * Empreinte du dossier mods : une ligne par entrée « .jar » —
 * "nom-en-minuscules:sha256" (fichier) ou "nom:dir:sha256" (dossier) —,
 * lignes triées, jointes par "\n", puis SHA-256 hexadécimal (UTF-8).
 */
function modsHash(modsDir) {
  const lines = listJars(modsDir).map((j) => j.name + ':' + entryHash(j));
  lines.sort(byCodeUnit);
  return sha256Hex(lines.join('\n'));
}

/**
 * Vérifie le dossier mods contre le manifeste officiel :
 * manifest = { "nom-du-mod.jar": "sha256hex", ... } (noms en minuscules).
 * Retourne { ok, unexpected, missing, modified, duplicates }.
 * « duplicates » : plusieurs fichiers dont le nom ne diffère que par la casse
 * (Mod.jar / mod.jar) — impossible dans un modpack officiel, donc refusé.
 */
function verifyModsFolder(modsDir, manifest) {
  const expected = new Map(Object.entries(manifest).map(([k, v]) => [k.toLowerCase(), v.toLowerCase()]));
  const found = new Map();
  const duplicates = [];
  for (const j of listJars(modsDir)) {
    if (found.has(j.name)) {
      duplicates.push(j.name);
      continue;
    }
    found.set(j.name, entryHash(j));
  }
  const unexpected = [...found.keys()].filter((n) => !expected.has(n));
  const missing = [...expected.keys()].filter((n) => !found.has(n));
  const modified = [...found.entries()]
    .filter(([n, h]) => expected.has(n) && expected.get(n) !== h)
    .map(([n]) => n);
  const ok = !unexpected.length && !missing.length && !modified.length && !duplicates.length;
  return { ok, unexpected, missing, modified, duplicates };
}

function b64url(buf) {
  return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Jeton v1 : base64url(payload) + "." + base64url(HMAC-SHA256(secret, payload))
 * payload  : "v1|<base64url(pseudo)>|<expiration epoch secondes>|<empreinte mods>|<aléa hex>"
 * Le pseudo est encodé (en crack, un pseudo peut contenir « | ») et sensible à la casse.
 */
function createLaunchToken(secret, pseudo, expiresEpochSeconds, modsHashHex) {
  const expires = Math.floor(Number(expiresEpochSeconds));
  const now = Math.floor(Date.now() / 1000);
  if (!Number.isFinite(expires) || expires <= now || expires - now > MAX_LIFETIME_S) {
    throw new Error('Expiration invalide : doit être dans le futur et à moins de 30 jours.');
  }
  const nonce = crypto.randomBytes(12).toString('hex');
  const payload = Buffer.from(
    ['v1', b64url(Buffer.from(pseudo, 'utf8')), String(expires), modsHashHex, nonce].join('|'), 'utf8');
  const sig = crypto.createHmac('sha256', Buffer.from(secret, 'utf8')).update(payload).digest();
  return b64url(payload) + '.' + b64url(sig);
}

/** Manifeste { "nom.jar": empreinte } du dossier (utilisé par make-manifest.js). */
function manifestOf(modsDir) {
  const out = {};
  for (const j of listJars(modsDir).sort((a, b) => byCodeUnit(a.name, b.name))) {
    out[j.name] = entryHash(j);
  }
  return out;
}

module.exports = { modsHash, verifyModsFolder, createLaunchToken, manifestOf };

// ---------------------------------------------------------------------------
// Exemple d'utilisation au moment de cliquer sur « Jouer » :
//
//   const spartan = require('./spartan-launcher');
//   const check = spartan.verifyModsFolder(modsDir, manifestTelechargeDepuisLeServeur);
//   if (!check.ok) { /* resynchroniser les mods ou refuser de lancer */ }
//   const token = spartan.createLaunchToken(SECRET, pseudo,
//       Math.floor(Date.now() / 1000) + 24 * 3600, spartan.modsHash(modsDir));
//   jvmArgs.push('-Dspartan.launchToken=' + token);
//   jvmArgs.push('-XX:+DisableAttachMechanism');   // anti-injection, voir docs/LAUNCHER.md
//
// Si le launcher a un serveur d'authentification, c'est LUI qui doit appeler
// createLaunchToken avec le pseudo de SA session authentifiée (le secret ne quitte
// jamais le serveur) : voir docs/LAUNCHER.md.
// ---------------------------------------------------------------------------
