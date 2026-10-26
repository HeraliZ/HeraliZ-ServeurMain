// Proposition : jumelles tenues à deux mains comme les armes TaCZ (vrais bras du joueur : PlayerRenderer.renderRightHand /
// renderLeftHand, comme TaCZ), tenue propre à la 1re et à la 3e personne. Chargé par apercu.html après le moteur.
'use strict';

// ------------------------------------------------------------------ réglages (repris tels quels dans le mod)
const R = {
  // centre des jumelles (pixels de l'objet) : entre les deux tubes, à hauteur de leur axe
  centre: [8, 6.4, 8],
  // prises des mains (pixels de l'objet) : côté extérieur de chaque tube, sous l'axe, vers l'avant
  priseD: [14.8, 4.2, 6.5], priseG: [1.2, 4.4, 6.0],
  // 1re personne : place du centre (blocs, repère de la caméra), angles (degrés : tangage, lacet, roulis), taille
  fpRepos: {pos: [0.28, -0.27, -0.95], rot: [24, -18, -5], s: 0.7},
  fpVise: {pos: [0, -0.04, -0.33], rot: [0, 0, 0], s: 0.7},
  fpRecharge: {pos: [0.22, -0.28, -1.0], rot: [46, 20, 4], s: 0.7},
  // épaules virtuelles (blocs, repère de la caméra) : les bras partent de là vers les mains (comme les bras de TaCZ,
  // allongés de moitié pour que l'épaule reste hors de l'écran)
  epauleD: [0.6, -1.05, 0.2], epauleG: [-0.45, -1.05, 0.2], epauleGTravail: [-0.6, -1.05, 0.3],
  etire: 1.5,
  // poche où la main gauche range et prend les cartouches (blocs, repère de la caméra)
  poche: [-0.6, -0.8, -0.35],
  // côté de la paume (repère de l'objet) : vers l'axe du tube
  paumeD: [-1, 0.6, 0], paumeG: [1, 0.6, 0],
};

// ------------------------------------------------------------------ outils
function poseJumelles(ps, d) {
  ps.translate(d.pos[0], d.pos[1], d.pos[2]);
  ps.rot(Q.xyz(d.rot[0] * RAD, d.rot[1] * RAD, d.rot[2] * RAD));
  ps.scale(d.s, d.s, d.s);
  ps.translate(-R.centre[0] / 16, -R.centre[1] / 16, -R.centre[2] / 16);
}
function melange(a, b, t) {
  return {pos: V.lerp(a.pos, b.pos, t), rot: V.lerp(a.rot, b.rot, t), s: lerp(t, a.s, b.s)};
}
// matrice dont les colonnes sont x, y, z et l'origine o
function repere(x, y, z, o) {
  const m = M.ident();
  m[0] = x[0]; m[1] = x[1]; m[2] = x[2];
  m[4] = y[0]; m[5] = y[1]; m[6] = y[2];
  m[8] = z[0]; m[9] = z[1]; m[10] = z[2];
  m[12] = o[0]; m[13] = o[1]; m[14] = o[2];
  return m;
}
// pose du bras après PlayerRenderer.renderHand (setupAnim à zéro, xRot = 0, balancement des bras à l'âge 0)
function poseBrasMain(cote) {
  return cote === 'right' ? {x: -5, y: 2, z: 0, xr: 0, yr: 0, zr: 0.1} : {x: 5, y: 2, z: 0, xr: 0, yr: 0, zr: -0.1};
}
/**
 * Dessine le bras « cote » comme TaCZ (renderRightHand / renderLeftHand) : le centre du bout de la main en G, le bras
 * vers D (de la main vers l'épaule), la paume tournée vers IN. G, D, IN dans le repère courant de ps (blocs).
 */
function brasTacz(ps, cote, G, D, IN, fin) {
  const pose = poseBrasMain(cote);
  // repère du bras dans le modèle du joueur : pivot + rotation, puis main (bout du bras), axes du bras
  const apart = M.mul(M.tr(pose.x / 16, pose.y / 16, pose.z / 16), M.quat(Q.zyx(pose.zr, pose.yr, pose.xr)));
  const cx = cote === 'right' ? (fin ? -0.5 : -1) : (fin ? 0.5 : 1);
  const inLocal = cote === 'right' ? [1, 0, 0] : [-1, 0, 0];
  const L = repere(inLocal, [0, -1, 0], V.cross(inLocal, [0, -1, 0]), [cx / 16, 10 / 16, 0]);
  const d = V.norm(D);
  let i = V.sub(IN, V.mul(d, V.dot(IN, d)));
  i = V.norm(i);
  const W = repere(i, V.mul(d, R.etire), V.cross(i, d), G);
  const passe = M.mul(W, M.mul(M.inv(L), M.inv(apart)));
  ps.push();
  ps.mul(passe);
  brasModele(ps, cote, pose, fin);
  ps.pop();
}
// niveau de chaque frappe (cartouches Légère, Lourde, Spéciale)
const NIVEAU = {gun_run: 'light', smoke: 'light', bombing: 'heavy', cluster: 'heavy', gas: 'heavy', precision: 'special',
  napalm: 'special'};
function modeleCartouche(type) {
  const n = 'cartridge_' + NIVEAU[type];
  return NIVEAU[type] && S.cartouches[n] ? n : type;
}
// jumelles seules (corps, trappe, voyant, écran, cartouches), sans les mains d'avant
function jumellesApres(ps, o, off, debut) {
  const p = o.p == null ? 1 : o.p, recharge = p < 1;
  const a = recharge ? animApres(p, !!o.ancienne, off, debut) : null;
  // modèle proposé (outils/modele/jumelles.py) s'il est préparé, sinon celui d'origine (o.modele = 'origine')
  const neuf = o.modele !== 'origine' && S.apres;
  const J = neuf ? S.apres : S.jumelles, tex = neuf ? S.tex.apres : S.tex.designator;
  ps.push();
  ps.scale(-1, -1, 1);
  let f = partie(ps, J.body); dessiner(J.body, ps.last(), tex, 0); f();
  f = partie(ps, J.hatch, HATCH_OPEN_DEG * hatchOpen(p) * RAD); dessiner(J.hatch, ps.last(), tex, 0); f();
  const led = recharge && p >= T.OPEN && p < T.CLICK ? [1, 0.6, 0.1, 1] : o.charge ? couleurType(o.charge, 0.95) : [1, 0.6, 0.1, 1];
  f = partie(ps, J.emit_led); dessiner(J.emit_led, ps.last(), tex, 2, led); f();
  ps.pop();
  ecran(ps, o.charge, o.ancienne, p);
  if (!recharge) { if (o.charge) cartouche(modeleCartouche(o.charge), BAY, null, ps); return null; }
  if (a.oldPos) cartouche(modeleCartouche(o.ancienne), a.oldPos, a.oldRot, ps);
  if (a.newPos && o.charge) cartouche(modeleCartouche(o.charge), a.newPos, a.newRot, ps);
  return a;
}
// trajet du rechargement façon TaCZ (pixels de l'objet) : la main gauche part de sa prise, appuie sur le loquet,
// rattrape l'ancienne cartouche, la retourne en l'air et la range (poche), revient avec la nouvelle, la montre en la
// faisant tourner, l'enfonce, ferme la trappe et retourne sur le tube gauche. Toujours du côté gauche de la trappe,
// pour que le bras ne cache pas la trappe. Mêmes instants que l'animation d'avant (trappe, écran, sons).
// point du dessus de la trappe à la distance d de la charnière (6,3 : bord libre)
function lidPoint(open, d) {
  const a = HATCH_OPEN_DEG * open * RAD, y = 0.2;
  return [8.0, HINGE[1] + y * Math.cos(a) + d * Math.sin(a), HINGE[2] - y * Math.sin(a) + d * Math.cos(a)];
}
function animApres(p, hasOld, off, debut) {
  const a = {};
  const popTop = add(BAY, 0, 3.0, 0.3), release = add(BAY, -2.0, 5.0, 1.0), catchPos = add(BAY, -3.2, 4.4, 1.6);
  const popRot = euler([-14, 0, 0]);
  if (hasOld && p < T.POCKET) {
    if (p < T.POP) { a.oldPos = [...BAY]; a.oldRot = Q.id(); }
    else if (p < T.CATCH) { const s = uu(p, T.POP, T.CATCH); a.oldPos = V.lerp(BAY, popTop, easeOutBack(s)); a.oldRot = euler([-14 * smooth(s), 0, 0]); }
    else if (p < T.RELEASE) { const s = smooth(uu(p, T.CATCH, T.RELEASE)); a.oldPos = V.lerp(popTop, release, s); a.oldRot = euler([-14 - 6 * s, 0, 0]); }
    else if (p < T.CATCH2) {
      const s = uu(p, T.RELEASE, T.CATCH2);
      a.oldPos = V.add(V.lerp(release, catchPos, s), [0, 3.0 * 4 * s * (1 - s), 0]);
      a.oldRot = euler([-20 - 360 * smooth(s), 30 * s, 0]);
    } else {
      const s = smooth(uu(p, T.CATCH2, T.POCKET));
      const r = euler([-20 - 40 * s, 30, 0]);
      const offCart = V.sub(off, Q.rot(r, GRIP));
      a.oldPos = track(p, stop(T.CATCH2, catchPos), k(T.CATCH2 + 0.04, add(catchPos, -2.0, -1.0, 0.8)), stop(T.POCKET, offCart));
      a.oldRot = r;
    }
  }
  let baseNew = null;
  if (p >= T.POCKET) {
    const show = add(BAY, -5.5, 6.0, 2.5), show2 = add(BAY, -4.0, 5.2, 2.0), nose = add(BAY, -0.6, 3.0, 1.2), slide = add(BAY, 0, 0.9, 0.4);
    const rotV = track(p, stop(T.POCKET, [-60, 0, 0]), k(T.SHOW, [30, 0, 0]), k(T.TWIRLED, [30, 0, 0]), k(T.NOSE, [-32, 0, 0]),
      k(T.SEATED - 0.03, [-8, 0, 0]), stop(T.SEATED, [0, 0, 0]));
    baseNew = euler(rotV);
    const spin = 360 * smooth(uu(p, T.SHOW - 0.01, T.TWIRLED));
    const offCart = V.sub(off, Q.rot(euler([-60, 0, 0]), GRIP));
    a.newPos = p < T.SEATED ? track(p, stop(T.POCKET, offCart), k(T.SHOW, show), k(T.TWIRLED, show2), k(T.NOSE, nose),
      k(T.SEATED - 0.03, slide), stop(T.SEATED, [...BAY])) : [...BAY];
    a.newRot = Q.mul(baseNew, Q.axe(0, 0, 1, spin * RAD));
  }
  const latchUp = add(LATCH, -4.0, 1.4, 1.0), latchPress = add(LATCH, -3.0, 0.2, 0.2), latchBack = add(LATCH, -4.2, 1.6, 1.2);
  const press = add(BAY, -1.2, 1.5, 0.3);
  const edgeOpen = V.add(lidPoint(1, 3.6), [-2.4, 0.6, 0.4]), edgeClosed = V.add(lidPoint(0, 3.6), [-2.4, 0.9, 0.2]);
  const debutK = debut || off;
  if (p < T.CATCH || (!hasOld && p < T.POCKET)) {
    a.hand = hasOld
      ? track(p, stop(0, debutK), k(0.07, latchUp), stop(T.LATCH, latchPress), k(T.OPEN + 0.01, latchBack),
        k(0.22, add(BAY, -4.0, 4.5, 1.0)), stop(T.CATCH, held(popTop, popRot)))
      : track(p, stop(0, debutK), k(0.07, latchUp), stop(T.LATCH, latchPress), k(T.OPEN + 0.01, latchBack),
        stop(0.30, off), stop(T.POCKET, off));
  } else if (p < T.RELEASE) {
    a.hand = held(a.oldPos, euler([-14 - 6 * smooth(uu(p, T.CATCH, T.RELEASE)), 0, 0]));
  } else if (p < T.CATCH2) {
    const s = uu(p, T.RELEASE, T.CATCH2);
    const from = held(release, euler([-20, 0, 0])), to = held(catchPos, euler([-20, 30, 0]));
    a.hand = V.add(V.lerp(from, to, smooth(s)), [0, -1.3 * Math.sin(s * Math.PI), 0.4 * Math.sin(s * Math.PI)]);
  } else if (p < T.POCKET) {
    a.hand = held(a.oldPos, euler([-20 - 40 * smooth(uu(p, T.CATCH2, T.POCKET)), 30, 0]));
  } else if (p < T.SEATED) {
    a.hand = held(a.newPos, baseNew);
  } else if (p < T.LID) {
    a.hand = track(p, stop(T.SEATED, held(BAY, Q.id())), k(T.SEATED + 0.02, add(BAY, -2.0, 3.4, 0.9)), stop(T.CLICK, press), stop(T.LID, edgeOpen));
  } else if (p < T.SLAM) {
    a.hand = V.add(lidPoint(hatchOpen(p), 3.6), [-2.4, 0.6 + 0.3 * uu(p, T.LID, T.SLAM), 0.4 - 0.2 * uu(p, T.LID, T.SLAM)]);
  } else {
    a.hand = track(p, stop(T.SLAM, edgeClosed), k(0.90, add(edgeClosed, -3.0, 3.0, 1.0)), stop(0.97, debutK), stop(1, debutK));
  }
  return a;
}

// ------------------------------------------------------------------ 1re personne
// réglages d'essai : o.R remplace une partie de R le temps d'un rendu
function avecReglages(o, f) {
  if (!o.R) return f();
  const garde = {};
  for (const [c, v] of Object.entries(o.R)) { garde[c] = R[c]; R[c] = v; }
  try { return f(); } finally { Object.assign(R, garde); }
}
fpsApres = function (ps, o) { return avecReglages(o, () => fpsApresR(ps, o)); };
function fpsApresR(ps, o) {
  // levée (clic droit, 5 ticks) et présentation pendant le rechargement (comme avant)
  let r = 0;
  if (o.etat === 'vise') { r = clamp01((o.tVise == null ? 5 : o.tVise) / 5); r = r * r * (3 - 2 * r); }
  const p = o.etat === 'recharge' ? o.p : 1;
  const w = presentation(p);
  let d = melange(R.fpRepos, R.fpVise, r);
  d = melange(d, R.fpRecharge, w);
  ps.push();
  poseJumelles(ps, d);
  if (w > 0) {
    const sw = sway(p, 1);
    // les secousses du rechargement autour du centre des jumelles
    ps.translate(R.centre[0] / 16, R.centre[1] / 16, R.centre[2] / 16);
    ps.translate(sw[0] / d.s, sw[1] / d.s, sw[2] / d.s);
    ps.xp(sw[3]); ps.yp(sw[4]); ps.zp(sw[5]);
    ps.translate(-R.centre[0] / 16, -R.centre[1] / 16, -R.centre[2] / 16);
  }
  const B = ps.last();
  const toItem = M.inv(B);
  // rechargement : la main droite tient les jumelles, la gauche fait le travail (comme avant) et repart de sa prise
  const off = V.mul(M.pt(toItem, R.poche), 16);
  const a = jumellesApres(ps, {...o, p}, off, R.priseG);
  ps.pop();
  const vue = p => M.pt(B, V.mul(p, 1 / 16));
  const dirVue = v => V.norm(M.dir(B, v));
  const gD = vue(R.priseD), gG = a ? vue(a.hand) : vue(R.priseG);
  if (o.sansBras) return;
  brasTacz(ps, 'right', gD, V.sub(R.epauleD, gD), dirVue(R.paumeD), o.fin);
  if (R.mainG === false && !a) return;
  const k = a ? presentation(p) : 0;
  const epG = V.lerp(R.epauleG, R.epauleGTravail, k);
  const inG = a ? V.norm(V.sub(vue([8, 11, 8]), gG)) : dirVue(R.paumeG);
  brasTacz(ps, 'left', gG, V.sub(epG, gG), inG, o.fin);
}

// référence : la main vide du jeu (ItemInHandRenderer.renderPlayerArm, sans geste)
function mainVide(ps, o) {
  const f = 1;
  ps.push();
  ps.translate(f * 0.64000005, -0.6, -0.71999997);
  ps.yp(f * 45);
  ps.translate(f * -1.0, 3.6, 3.5);
  ps.zp(f * 120);
  ps.xp(200);
  ps.yp(f * -135);
  ps.translate(f * 5.6, 0, 0);
  brasModele(ps, 'right', poseBrasMain('right'), o.fin);
  ps.pop();
}
const fpsApresBase = fpsApres;
fpsApres = function (ps, o) {
  if (o.etat === 'main_vide') { mainVide(ps, o); return; }
  fpsApresBase(ps, o);
};

// ------------------------------------------------------------------ référence : une arme TaCZ à la 1re personne, avec ses bras
const ARMES = {};
async function chargerArme(nom) {
  const d = await (await fetch(nom + '.json')).json();
  const parNom = {};
  for (const o of d.os) {
    o.enfants = [];
    parNom[o.nom] = o;
    if (o.pts.length) {
      const nrm = [];
      for (let i = 0; i < o.pts.length; i += 9) {
        const a = o.pts.slice(i, i + 3), b = o.pts.slice(i + 3, i + 6), c = o.pts.slice(i + 6, i + 9);
        let n = V.cross(V.sub(b, a), V.sub(c, a));
        n = V.len(n) < 1e-9 ? [0, 1, 0] : V.norm(n);
        nrm.push(...n, ...n, ...n);
      }
      o.m = maillage({pos: o.pts, nrm, uv: o.uv});
    }
  }
  const racines = [];
  for (const o of d.os) (o.parent ? parNom[o.parent].enfants : racines).push(o);
  ARMES[nom] = {d, parNom, racines, tex: await texture(nom + '.png')};
  return d.os.length;
}
function osTacz(ps, arme, o, fin) {
  const pose = arme.d.pose[o.nom];
  ps.push();
  if (pose && pose.position) ps.translate(pose.position[0] / 16, -pose.position[1] / 16, pose.position[2] / 16);
  ps.translate(o.pos[0] / 16, o.pos[1] / 16, o.pos[2] / 16);
  if (o.rot[2]) ps.rot(Q.axe(0, 0, 1, o.rot[2]));
  if (o.rot[1]) ps.rot(Q.axe(0, 1, 0, o.rot[1]));
  if (o.rot[0]) ps.rot(Q.axe(1, 0, 0, o.rot[0]));
  if (pose && pose.rotation) ps.rot(Q.zyx(pose.rotation[2] * RAD, pose.rotation[1] * RAD, pose.rotation[0] * RAD));
  if (pose && pose.scale) ps.scale(pose.scale[0], pose.scale[1], pose.scale[2]);
  if (o.nom === 'lefthand_pos' || o.nom === 'righthand_pos') {
    // LeftHandRender / RightHandRender : demi-tour autour de z, puis le bras du joueur (rotations remises à zéro par TaCZ)
    ps.zp(180);
    const cote = o.nom === 'lefthand_pos' ? 'left' : 'right';
    brasModele(ps, cote, {x: cote === 'left' ? 5 : -5, y: 2, z: 0}, fin);
  } else {
    if (o.m) {
      ps.push();
      ps.scale(1 / 16, 1 / 16, 1 / 16);
      dessiner(o.m, ps.last(), arme.tex, 0);
      ps.pop();
    }
    for (const e of o.enfants) osTacz(ps, arme, e, fin);
  }
  ps.pop();
}
function fpsTacz(ps, o) {
  const arme = ARMES[o.arme];
  // AnimateGeoItemRenderer.renderFirstPerson puis le placement par idle_view (FirstPersonRenderGunEvent)
  ps.translate(0, 1.5, 0);
  ps.zp(180);
  const chemin = [arme.parNom.view, arme.parNom.idle_view];
  let inv = M.ident();
  for (let i = chemin.length - 1; i >= 0; i--) {
    const p = chemin[i];
    inv = M.mul(inv, M.rx(-p.rot[0]));
    inv = M.mul(inv, M.ry(-p.rot[1]));
    inv = M.mul(inv, M.rz(-p.rot[2]));
    inv = M.mul(inv, p.parent ? M.tr(-p.pos[0] / 16, -p.pos[1] / 16, -p.pos[2] / 16) : M.tr(-p.pos[0] / 16, 1.5 - p.pos[1] / 16, -p.pos[2] / 16));
  }
  ps.translate(0, 1.5, 0);
  ps.mul(inv);
  ps.translate(0, -1.5, 0);
  for (const r of arme.racines) if (!['camera', 'positioning', 'view'].includes(r.nom)) osTacz(ps, arme, r, o.fin);
}
const fpsApresAvecRef = fpsApres;
fpsApres = function (ps, o) {
  if (o.etat === 'tacz') { fpsTacz(ps, o); return; }
  fpsApresAvecRef(ps, o);
};
window.apercu.chargerArme = chargerArme;

// ------------------------------------------------------------------ 3e personne
// Les jumelles sont placées par rapport à la tête (visée : contre les yeux) ou au torse (repos, rechargement), et les
// bras vont chercher leurs prises (on oriente chaque bras de l'épaule vers sa prise). Repère du modèle du joueur :
// pixels, y vers le bas, l'avant vers -z ; place = (pixel de l'objet E, point du repère C, angles, taille).
const R3 = {
  vise: {cadre: 'head', E: [8, 6.4, 15.4], C: [0, -4.0, -4.3], rot: [0, 0, 0], s: 0.55},
  repos: {cadre: 'body', E: [8, 6.4, 8], C: [0, 7.0, -6.0], rot: [-20, 0, 0], s: 0.55},
  recharge: {cadre: 'body', E: [8, 6.4, 8], C: [-0.5, 4.5, -6.5], rot: [-55, -15, 0], s: 0.55},
  priseD: [14.9, 4.2, 9.5], priseG: [1.1, 4.2, 9.5],
};
function partieMat(q) {
  return M.mul(M.tr(q.x / 16, q.y / 16, q.z / 16), M.quat(Q.zyx(q.zr, q.yr, q.xr)));
}
// objet (blocs) -> repère de la partie porteuse (blocs) : rotation de 180° autour de z (y de l'objet vers le haut)
function place3p(d) {
  let m = M.tr(d.C[0] / 16, d.C[1] / 16, d.C[2] / 16);
  m = M.mul(m, M.quat(Q.xyz(d.rot[0] * RAD, d.rot[1] * RAD, d.rot[2] * RAD)));
  m = M.mul(m, M.rz(Math.PI));
  m = M.mul(m, M.sc(d.s, d.s, d.s));
  return M.mul(m, M.tr(-d.E[0] / 16, -d.E[1] / 16, -d.E[2] / 16));
}
function etat3p(o) {
  const e = o.etat === 'vise' || o.etat === 'recharge' ? o.etat : 'repos';
  if (e !== 'recharge') return {d: R3[e], k: 0};
  // pendant le rechargement : on passe du repos à la présentation, comme à la 1re personne
  const k = presentation(o.p);
  const a = R3.repos, b = R3.recharge;
  return {d: {cadre: 'body', E: a.E, C: V.lerp(a.C, b.C, k), rot: V.lerp(a.rot, b.rot, k), s: a.s}, k};
}
// oriente le bras (pivot de l'épaule) vers le point G (repère du modèle, pixels) : xRot, yRot (zRot = 0)
function viser(bras, G) {
  const d = V.norm(V.sub(G, [bras.x, bras.y, bras.z]));
  const xr = -Math.acos(Math.max(-1, Math.min(1, d[1])));
  const s = Math.sin(xr);
  bras.xr = xr;
  bras.yr = Math.abs(s) < 1e-4 ? 0 : Math.atan2(d[0] / s, d[2] / s);
  bras.zr = 0;
}
function posesApres(P, o) {
  const {d} = etat3p(o);
  const F = M.mul(partieMat(P[d.cadre]), place3p(d));
  const enModele = g => V.mul(M.pt(F, V.mul(g, 1 / 16)), 16);
  viser(P.right_arm, enModele(R3.priseD));
  let g = R3.priseG;
  if (o.etat === 'recharge' && o.p < 1) {
    const a = animApres(o.p, !!o.ancienne, add(BAY, -14, -8, 6), R3.priseG);
    g = a.hand;
  }
  viser(P.left_arm, enModele(g));
}
POSES_APRES = {repos: posesApres, vise: posesApres, recharge: posesApres};
objetApres3p = function (ps, o, P) {
  // ps : repère de la main droite (ItemInHandLayer) ; on revient au repère du modèle, puis sur la tête ou le torse
  const q = P.right_arm;
  const A = M.mul(M.tr((q.x + (o.fin ? 0.5 : 0)) / 16, q.y / 16, q.z / 16), M.quat(Q.zyx(q.zr, q.yr, q.xr)));
  const L = M.mul(M.mul(M.rx(-90 * RAD), M.ry(180 * RAD)), M.tr(1 / 16, 0.125, -0.625));
  ps.mul(M.inv(M.mul(A, L)));
  const {d} = etat3p(o);
  ps.mul(partieMat(P[d.cadre]));
  ps.mul(place3p(d));
  jumellesApres(ps, {...o, p: o.etat === 'recharge' ? o.p : 1}, add(BAY, -14, -8, 6), R3.priseG);
};

// l'objet seul, version proposée (vue « objet »)
objetApres = function (ps, o) { jumellesApres(ps, {...o, p: o.p == null ? 1 : o.p}, [-12, 2, 15], null); };
guiApres = function (ps, o) {
  affichage(ps, AFFICHAGE_AVANT.gui, false);
  ps.translate(-0.5, -0.5, -0.5);
  jumellesApres(ps, {...o, p: 1}, [-12, 2, 15], null);
};
