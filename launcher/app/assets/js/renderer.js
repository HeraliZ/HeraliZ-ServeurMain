/* global document, window */
/*
 * Interface du launcher (maquettes validées) : démarrage -> pseudo (la première fois) -> menu principal avec ses onglets
 * Accueil, Règlement et Boutique. Tout passe par l'API étroite du preload (window.heraliz) ; aucun contenu
 * distant, aucun texte injecté en HTML.
 */
const $ = id => document.getElementById(id)
const api = window.heraliz
const NOUVEAUTES = Array.isArray(window.HERALIZ_NOUVEAUTES) ? window.HERALIZ_NOUVEAUTES : []
const PSEUDO = /^[A-Za-z0-9_]{3,16}$/
const IMAGE = /^[\w-]+\.(jpg|png)$/
const SPLASH_MIN_MS = 1800
const ERREUR_VISIBLE_MS = 15000
let state
let toastTimeout
let erreurTimeout
let launchPending = false

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

function el(tag, classe, texte) {
    const e = document.createElement(tag)
    if (classe) e.className = classe
    if (texte != null) e.textContent = texte
    return e
}

async function call(promise) {
    const result = await promise
    if (!result.ok) throw new Error(result.error)
    return result.data
}

function toast(message) {
    $('toast').textContent = message
    $('toast').hidden = false
    clearTimeout(toastTimeout)
    toastTimeout = setTimeout(() => { $('toast').hidden = true }, 6500)
}

function feedback(id, message, error = false) {
    $(id).textContent = message
    $(id).classList.toggle('error', error)
}

/* ---------- écrans et onglets ---------- */

function showScreen(name) {
    document.body.dataset.screen = name
    if (name === 'identite') setTimeout(() => $('username').focus(), 450)
}

function showTab(name) {
    document.body.dataset.tab = name
    fermerMenu()
}

function splashStep(percent, text) {
    $('splash-fill').style.width = `${percent}%`
    $('splash-percent').textContent = `${percent} %`
    if (text) $('splash-text').textContent = text
}

function profileReady() {
    return Boolean(state?.profile?.username && state.profile.rulesAccepted)
}

/* Tête de survivant en pixels, purement décorative (le launcher ne connaît pas le skin du joueur). */
function dessinerTete(canvas) {
    const g = canvas.getContext('2d')
    g.fillStyle = '#B07A55'; g.fillRect(0, 0, 8, 8)
    g.fillStyle = '#3B2414'; g.fillRect(0, 0, 8, 2); g.fillRect(0, 2, 1, 2); g.fillRect(7, 2, 1, 2)
    g.fillStyle = '#fff'; g.fillRect(1, 4, 2, 1); g.fillRect(5, 4, 2, 1)
    g.fillStyle = '#3A5F8F'; g.fillRect(2, 4, 1, 1); g.fillRect(5, 4, 1, 1)
    g.fillStyle = '#7A4A30'; g.fillRect(3, 6, 2, 1)
    g.fillStyle = '#5A3220'; g.fillRect(2, 7, 4, 1)
}

/* ---------- nouveautés ---------- */

function carteActu(n) {
    const carte = el('article', 'carte')
    if (n.une) carte.classList.add('une')
    if (typeof n.image === 'string' && IMAGE.test(n.image)) {
        const img = el('img')
        img.src = `assets/images/nouveautes/${n.image}`
        img.alt = ''
        img.draggable = false
        carte.append(img)
    }
    const txt = el('div', 'txt')
    if (n.etiquette) txt.append(el('span', n.une ? 'tag rouge' : 'tag', n.etiquette))
    txt.append(el('div', 't', n.titre || ''), el('div', 'd', n.texte || ''))
    carte.append(txt)
    return carte
}

/* Les trois nouveautés les plus récentes, sur l'accueil, non cliquables (choix du propriétaire : pas d'onglet dédié). */
function afficherActus() {
    $('actus-cartes').replaceChildren(...NOUVEAUTES.slice(0, 3).map(carteActu))
    $('actus-cartes').closest('.actus').hidden = NOUVEAUTES.length === 0
}

/* ---------- menu du profil ---------- */

function fermerMenu() {
    $('menu-profil').hidden = true
    $('profil-bouton').setAttribute('aria-expanded', 'false')
}

function basculerMenu() {
    const ouvert = $('menu-profil').hidden
    $('menu-profil').hidden = !ouvert
    $('profil-bouton').setAttribute('aria-expanded', String(ouvert))
}

/* ---------- paramètres ---------- */

function positionRam(valeur) {
    const r = $('memory')
    const min = Number(r.min), max = Number(r.max)
    return max === min ? 0 : (valeur - min) / (max - min)
}

/* Le pouce du curseur fait 21 px : son centre va de 10,5 px à (largeur - 10,5 px). */
function placeRam(p) {
    return `calc(10.5px + (100% - 21px) * ${p})`
}

function afficherMemoire() {
    const r = $('memory')
    const min = Number(r.min), max = Number(r.max), valeur = Number(r.value)
    $('memory-value').replaceChildren(document.createTextNode(`${valeur} `), el('small', null, 'Go'))
    $('memory-fill').style.width = placeRam(positionRam(valeur))
    const bas = Math.min(5, max), haut = Math.min(8, max)
    $('memory-reco').style.left = placeRam(positionRam(bas))
    $('memory-reco').style.width = `calc((100% - 21px) * ${positionRam(haut) - positionRam(bas)} + 10px)`
    $('memory-reco').style.marginLeft = '-5px'
    $('memory-reco-txt').lastChild.textContent = bas === haut ? `Recommandé pour HeraliZ : ${haut} Go` : `Recommandé pour HeraliZ : ${bas} à ${haut} Go`
    const pas = max - min > 8 ? 2 : 1
    const graduations = []
    for (let v = min; v <= max; v += pas) graduations.push(v)
    if (graduations[graduations.length - 1] !== max) graduations.push(max)
    $('memory-grad').replaceChildren(...graduations.map((v, i) => {
        const s = el('span', null, i === 0 || i === graduations.length - 1 ? `${v} Go` : String(v))
        s.style.left = placeRam(positionRam(v))
        return s
    }))
}

function ouvrirPanneau(nom) {
    for (const b of document.querySelectorAll('.modale .menu button')) b.classList.toggle('on', b.dataset.panneau === nom)
    for (const p of document.querySelectorAll('.modale .panneau')) p.hidden = p.dataset.panneau !== nom
}

/* ---------- préparation et partie ---------- */

const ETAPES = ['prepare', 'java', 'fichiers', 'install', 'launch']
const ETAPE_DE = { prepare: 'prepare', java: 'java', verify: 'fichiers', download: 'fichiers', install: 'install', launch: 'launch' }

function updatePlay() {
    const p = state?.progress
    const enJeu = p?.phase === 'running'
    const occupe = launchPending || Boolean(p?.busy)
    $('play').disabled = !state || occupe
    $('play').classList.toggle('en-jeu', enJeu)
    $('play').classList.toggle('occupe', occupe && !enJeu)
    $('play').querySelector('.spin').hidden = !occupe || enJeu
    $('play-label').textContent = enJeu ? 'EN JEU' : occupe ? (p?.percent != null ? `${p.percent} %` : '…') : 'JOUER'
}

function renderProgress(progress) {
    state.progress = progress
    const erreur = progress.phase === 'error'
    const enJeu = progress.phase === 'running'
    const visible = launchPending || progress.busy || erreur
    clearTimeout(erreurTimeout)
    if (erreur) {
        erreurTimeout = setTimeout(() => {
            if (state.progress.phase === 'error' && !state.progress.busy) { $('prep').hidden = true; $('serveur-infos').hidden = false }
        }, ERREUR_VISIBLE_MS)
    }
    $('prep').hidden = !visible
    $('serveur-infos').hidden = visible
    $('prep').classList.toggle('erreur', erreur)
    $('prep').classList.toggle('en-jeu', enJeu)
    $('progress-message').textContent = progress.message
    $('progress-detail').textContent = progress.detail || (progress.percent != null && !enJeu ? `${progress.percent} %` : '')
    $('progress-bar').classList.toggle('indefini', progress.percent == null && !enJeu)
    $('progress-fill').style.width = `${progress.percent ?? 0}%`
    const actuelle = enJeu ? ETAPES.length : ETAPES.indexOf(ETAPE_DE[progress.phase] ?? '')
    for (const span of $('etapes').children) {
        const i = ETAPES.indexOf(span.dataset.etape)
        span.classList.toggle('ok', actuelle >= 0 && i < actuelle)
        span.classList.toggle('cours', i === actuelle)
    }
    for (const id of ['username', 'accept-rules', 'save-profile', 'change-profile', 'logout', 'confirm-logout', 'memory', 'fullscreen', 'auto-connect']) $(id).disabled = progress.busy
    $('settings-form').querySelector('[type=submit]').disabled = progress.busy
    updatePlay()
}

function applyState(next) {
    state = next
    $('app-version').textContent = state.version
    $('about-version').textContent = `v${state.version}`
    $('splash-version').textContent = state.version
    $('current-user').textContent = state.profile?.username || ''
    $('logout-user').textContent = state.profile?.username || ''
    $('memory').max = state.settings.maxRAM
    $('memory').value = state.settings.ramGB
    $('memory-desc').textContent = `${state.settings.totalRAM} Go détectés sur ton PC · une partie reste à Windows.`
    $('fullscreen').checked = state.settings.fullscreen
    $('auto-connect').checked = state.settings.autoConnect
    $('identity-back').hidden = !profileReady()
    afficherMemoire()
    renderProgress(state.progress)
}

/* Le formulaire du pseudo reprend le profil enregistré. */
function restoreProfileForm() {
    $('username').value = state?.profile?.username || ''
    $('accept-rules').checked = state?.profile?.rulesAccepted || false
    $('username-ok').hidden = !PSEUDO.test($('username').value)
    feedback('profile-feedback', '')
}

async function refreshStatus() {
    try {
        const s = await call(api.serverStatus())
        const enLigne = s.state === 'online'
        $('server-dot').className = `point${enLigne ? ' vert' : s.state === 'unreachable' ? ' rouge' : ''}`
        $('server-status').textContent = enLigne ? 'En ligne' : s.state === 'unconfigured' ? 'En préparation' : 'Hors ligne'
        $('server-players').textContent = enLigne ? String(s.online) : '—'
        $('server-max').textContent = enLigne ? `/ ${s.max}` : ''
        $('server-gauge').style.width = enLigne && s.max > 0 ? `${Math.min(100, s.online / s.max * 100)}%` : '0%'
        $('server-ping').textContent = enLigne && Number.isFinite(s.ping) ? String(s.ping) : '—'
    } catch {
        $('server-status').textContent = 'Indisponible'
    }
}

/* ---------- branchements ---------- */

for (const b of document.querySelectorAll('[data-window]')) b.addEventListener('click', () => call(api.windowAction(b.dataset.window)).catch(error => toast(error.message)))
for (const b of document.querySelectorAll('[data-dialog]')) b.addEventListener('click', () => { fermerMenu(); $(b.dataset.dialog).showModal() })
for (const b of document.querySelectorAll('.dialog-close')) b.addEventListener('click', () => b.closest('dialog').close())
for (const b of document.querySelectorAll('[data-link]')) b.addEventListener('click', () => call(api.openLink(b.dataset.link)).catch(error => toast(error.message)))
for (const b of document.querySelectorAll('.onglet[data-tab]')) b.addEventListener('click', () => showTab(b.dataset.tab))
for (const b of document.querySelectorAll('.modale .menu button')) b.addEventListener('click', () => ouvrirPanneau(b.dataset.panneau))
for (const zone of document.querySelectorAll('[data-regles]')) zone.append($('regles-modele').content.cloneNode(true))

$('profil-bouton').addEventListener('click', event => { event.stopPropagation(); basculerMenu() })
document.addEventListener('click', event => { if (!$('menu-profil').hidden && !event.target.closest('.profil-zone')) fermerMenu() })
document.addEventListener('keydown', event => { if (event.key === 'Escape') fermerMenu() })
$('change-profile').addEventListener('click', () => { fermerMenu(); restoreProfileForm(); showScreen('identite') })
$('identity-back').addEventListener('click', () => { restoreProfileForm(); showScreen('principal') })
$('logout-dialog').addEventListener('close', () => feedback('logout-feedback', ''))
$('settings-dialog').addEventListener('close', () => { feedback('settings-feedback', ''); ouvrirPanneau('jeu') })
$('memory').addEventListener('input', afficherMemoire)
$('username').addEventListener('input', () => {
    $('username-ok').hidden = !PSEUDO.test($('username').value)
    feedback('profile-feedback', '')
})

$('confirm-logout').addEventListener('click', async () => {
    $('confirm-logout').disabled = true
    try {
        applyState(await call(api.logout()))
        restoreProfileForm()
        $('logout-dialog').close()
        showScreen('identite')
    } catch (error) { feedback('logout-feedback', error.message, true) }
    finally { $('confirm-logout').disabled = Boolean(state?.progress.busy) }
})

$('profile-form').addEventListener('submit', async event => {
    event.preventDefault()
    if (!PSEUDO.test($('username').value)) {
        feedback('profile-feedback', 'Le pseudo doit contenir 3 à 16 caractères : lettres, chiffres ou _.', true)
        $('username').focus()
        return
    }
    if (!$('accept-rules').checked) {
        feedback('profile-feedback', 'Tu dois accepter le règlement pour continuer.', true)
        return
    }
    $('save-profile').disabled = true
    try {
        applyState(await call(api.saveProfile($('username').value, true)))
        feedback('profile-feedback', '')
        showTab('accueil')
        showScreen('principal')
    } catch (error) { feedback('profile-feedback', error.message, true) }
    finally { $('save-profile').disabled = Boolean(state?.progress.busy) }
})

$('settings-form').addEventListener('submit', async event => {
    event.preventDefault()
    try {
        const next = await call(api.saveSettings({ ramGB: Number($('memory').value), fullscreen: $('fullscreen').checked, autoConnect: $('auto-connect').checked }))
        state.settings = next.settings
        feedback('settings-feedback', 'Paramètres enregistrés.')
    } catch (error) { feedback('settings-feedback', error.message, true) }
})

$('play').addEventListener('click', async () => {
    if (!profileReady()) {
        restoreProfileForm()
        showScreen('identite')
        return
    }
    if (launchPending || state.progress.busy) return
    launchPending = true
    renderProgress({ busy: false, phase: 'prepare', message: 'Chargement du pack HeraliZ…', percent: null })
    try { await call(api.launch()) }
    catch (error) {
        launchPending = false
        renderProgress({ busy: false, phase: 'error', message: error.message, percent: null })
    }
    finally { launchPending = false; updatePlay() }
})

async function init() {
    dessinerTete(document.querySelector('.profil .tete'))
    afficherActus()
    document.fonts.ready.then(() => window.HeralizTopo?.suivre($('topo')))
    const minSplash = wait(SPLASH_MIN_MS)
    window.requestAnimationFrame(() => splashStep(15))
    if (!api) {
        $('play').disabled = true
        $('server-status').textContent = 'Ouvre cette interface avec le launcher HeraliZ.'
    } else {
        try {
            splashStep(35, 'Chargement du profil')
            applyState(await call(api.getState()))
            api.onProgress(renderProgress)
            splashStep(70, 'Contact du serveur')
            // Un serveur lent ne doit pas bloquer le démarrage.
            await Promise.race([refreshStatus(), wait(4000)])
            setInterval(refreshStatus, 60000)
        } catch (error) { toast(error.message); $('play').disabled = true }
    }
    splashStep(90)
    await minSplash
    splashStep(100, 'Prêt')
    await wait(400)
    restoreProfileForm()
    showScreen(profileReady() ? 'principal' : 'identite')
}
init()
