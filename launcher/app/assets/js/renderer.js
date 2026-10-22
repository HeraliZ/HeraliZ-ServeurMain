/* global document, window */
/*
 * Interface du launcher (maquettes validées) : démarrage -> connexion ou création du compte HeraliZ (celui du site
 * heraliz.fr), avec proposition de la double authentification -> menu principal avec ses onglets Accueil, Règlement et
 * Boutique. Tout passe par l'API étroite du preload (window.heraliz) ; aucun texte distant injecté en HTML (le QR code
 * de la double authentification est une image).
 */
const $ = id => document.getElementById(id)
const api = window.heraliz
const NOUVEAUTES = Array.isArray(window.HERALIZ_NOUVEAUTES) ? window.HERALIZ_NOUVEAUTES : []
const IMAGE = /^[\w-]+\.(jpg|png)$/
const PSEUDO = /^[A-Za-z0-9_]{3,16}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const SPLASH_MIN_MS = 1800
const ERREUR_VISIBLE_MS = 15000
let state
let toastTimeout
let erreurTimeout
let launchPending = false
// ce que permet le site (création de compte, longueur du mot de passe, conditions), demandé une fois
let etatComptes = null
// mot de passe tapé juste avant, gardé seulement le temps de proposer la double authentification
let motDePasseA2F = null
let apresA2F = null
// nouvelle version du launcher à télécharger à la main (Mac, Linux en .deb), montrée une fois l'accueil affiché
let nouvelleVersion = null

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

function el(tag, classe, texte) {
    const e = document.createElement(tag)
    if (classe) e.className = classe
    if (texte != null) e.textContent = texte
    return e
}

async function call(promise) {
    const result = await promise
    if (!result.ok) throw Object.assign(new Error(result.error), result.champs ? { champs: result.champs } : {})
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
    if (name === 'identite') setTimeout(focusFormulaire, 450)
}

function focusFormulaire() {
    if ($('profile-form').dataset.mode === 'inscription') $('new-pseudo').focus()
    else $(!$('code-champ').hidden ? 'code' : $('email').value ? 'password' : 'email').focus()
}

/* ---------- connexion ou création de compte ---------- */

function changerMode(mode) {
    const inscription = mode === 'inscription'
    $('profile-form').dataset.mode = inscription ? 'inscription' : 'connexion'
    for (const b of document.querySelectorAll('.bascule [data-mode]')) b.setAttribute('aria-selected', String(b.dataset.mode === $('profile-form').dataset.mode))
    $('form-titre').textContent = inscription ? 'Crée ton compte' : 'Connecte-toi'
    $('form-intro').replaceChildren(...(inscription
        ? [document.createTextNode('Un seul compte pour le launcher et '), el('b', null, 'heraliz.fr'), document.createTextNode(' (boutique, votes). Ton pseudo sera ton nom en jeu.')]
        : [document.createTextNode('Avec ton compte HeraliZ, le même que sur '), el('b', null, 'heraliz.fr'), document.createTextNode('. Ton pseudo en jeu est celui de ton compte.')]))
    $('save-profile-label').textContent = inscription ? 'CRÉER MON COMPTE' : 'ENTRER DANS LA ZONE'
    effacerErreurs()
    feedback('profile-feedback', '')
    if (inscription) chargerEtatComptes()
    focusFormulaire()
}

// Longueur du mot de passe et conditions du site ; création de compte fermée si le site ne la propose pas.
async function chargerEtatComptes() {
    if (!etatComptes || etatComptes.injoignable) {
        etatComptes = await call(api.etatComptes()).catch(() => ({ inscription: false, motDePasseMin: 12, conditions: null, injoignable: true }))
    }
    const min = etatComptes.motDePasseMin || 12
    $('new-password-aide').textContent = `Au moins ${min} caractères. Pas le même que ton /login en jeu.`
    $('conditions-site').hidden = !etatComptes.conditions
    if ($('profile-form').dataset.mode === 'inscription' && !etatComptes.inscription) {
        feedback('profile-feedback', etatComptes.injoignable
            ? 'Le site HeraliZ ne répond pas. Vérifie ta connexion internet et réessaie.'
            : 'La création de compte depuis le launcher n’est pas encore ouverte : crée ton compte sur heraliz.fr.', true)
    }
}

const AIDES = { pseudo: '3 à 16 caractères : lettres, chiffres ou _. C’est ton nom en jeu.', email: '', motDePasse: null }

function effacerErreurs() {
    for (const c of document.querySelectorAll('.mode-inscription .champ.erreur')) c.classList.remove('erreur')
    for (const a of document.querySelectorAll('.mode-inscription .aide')) {
        a.classList.remove('erreur')
        const defaut = AIDES[a.dataset.aide]
        if (defaut !== null && defaut !== undefined) a.textContent = defaut
    }
    if (etatComptes) $('new-password-aide').textContent = `Au moins ${etatComptes.motDePasseMin || 12} caractères. Pas le même que ton /login en jeu.`
}

// Erreurs par champ (contrôle du launcher ou réponse du site) sous les champs concernés.
function afficherErreurs(champs) {
    effacerErreurs()
    let premier = null
    for (const [nom, message] of Object.entries(champs || {})) {
        const cible = nom === 'confirmation' ? 'motDePasse' : nom
        const champ = document.querySelector(`.mode-inscription [data-champ="${nom}"]`)
        const aide = document.querySelector(`.mode-inscription [data-aide="${cible}"]`)
        if (nom === 'pseudo') $('new-pseudo-ok').hidden = true
        if (champ) champ.classList.add('erreur')
        if (aide) { aide.textContent = message; aide.classList.add('erreur') }
        premier = premier || champ?.querySelector('input')
    }
    premier?.focus()
}

function verifierInscription() {
    const min = etatComptes?.motDePasseMin || 12
    const champs = {}
    if (!PSEUDO.test($('new-pseudo').value)) champs.pseudo = 'Le pseudo doit faire 3 à 16 caractères : lettres, chiffres ou _.'
    if (!EMAIL.test($('new-email').value.trim())) champs.email = 'Entre une adresse e-mail valide.'
    if ($('new-password').value.length < min) champs.motDePasse = `Le mot de passe doit faire au moins ${min} caractères.`
    else if ($('new-password').value !== $('new-password2').value) champs.confirmation = 'Les deux mots de passe ne sont pas identiques.'
    return champs
}

/* ---------- double authentification ---------- */

function svgEnImage(svg) {
    const octets = new TextEncoder().encode(svg)
    let binaire = ''
    for (const o of octets) binaire += String.fromCharCode(o)
    return `data:image/svg+xml;base64,${window.btoa(binaire)}`
}

// Propose la double authentification (QR code) ; false si elle est déjà active. motDePasse : celui tapé juste avant.
async function ouvrirA2F({ motDePasse = null, depuisMenu = false } = {}) {
    const r = await call(api.a2fPreparer())
    if (r.active) {
        if (depuisMenu) toast('La double authentification est déjà active sur ton compte.')
        return false
    }
    motDePasseA2F = motDePasse
    $('a2f-qr').src = svgEnImage(r.qr)
    $('a2f-secret').textContent = r.secret.replace(/(.{4})(?=.)/g, '$1 ')
    $('a2f-code').value = ''
    $('a2f-mdp').value = ''
    $('a2f-mdp-champ').hidden = Boolean(motDePasse)
    $('a2f-etape-qr').hidden = false
    $('a2f-etape-codes').hidden = true
    feedback('a2f-feedback', '')
    $('a2f-dialog').showModal()
    setTimeout(() => $('a2f-code').focus(), 50)
    return true
}

// Après une connexion ou une création de compte : proposer la double authentification, puis aller au menu.
async function apresConnexion(motDePasse, nouveauCompte) {
    const versMenu = () => { showTab('accueil'); showScreen('principal') }
    try {
        apresA2F = versMenu
        if (await ouvrirA2F({ motDePasse })) return
    } catch (error) {
        // extension du site absente ou site lent : la proposition attendra, le joueur entre quand même
        if (nouveauCompte) toast(`Compte créé. ${error.message}`)
    }
    apresA2F = null
    versMenu()
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
    return Boolean(state?.profile?.username && state.profile.rulesAccepted && (!state.comptesSite || state.profile.compte))
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
    for (const id of ['email', 'password', 'code', 'new-pseudo', 'new-email', 'new-password', 'new-password2', 'accept-rules', 'save-profile', 'site-account', 'securite', 'logout', 'confirm-logout', 'memory', 'fullscreen']) $(id).disabled = progress.busy
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
    $('memory-desc').textContent = `${state.settings.totalRAM} Go détectés sur ton PC · une partie reste au système.`
    $('fullscreen').checked = state.settings.fullscreen
    $('identity-back').hidden = !profileReady()
    afficherMemoire()
    renderProgress(state.progress)
}

/* Formulaire de connexion : les mots de passe et le code ne restent jamais affichés d'une fois sur l'autre. */
function restoreProfileForm() {
    $('password').value = ''
    $('code').value = ''
    $('code-champ').hidden = true
    $('new-password').value = ''
    $('new-password2').value = ''
    effacerErreurs()
    afficherMotDePasse(false)
    $('accept-rules').checked = state?.profile?.rulesAccepted || false
    feedback('profile-feedback', state && !state.comptesSite ? 'Les comptes HeraliZ ne sont pas encore ouverts.' : '')
}

function afficherMotDePasse(visible) {
    $('password').type = visible ? 'text' : 'password'
    $('password-toggle').classList.toggle('actif', visible)
    $('password-toggle').setAttribute('aria-label', visible ? 'Cacher le mot de passe' : 'Afficher le mot de passe')
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
$('identity-back').addEventListener('click', () => { restoreProfileForm(); showScreen('principal') })
$('logout-dialog').addEventListener('close', () => feedback('logout-feedback', ''))
$('settings-dialog').addEventListener('close', () => { feedback('settings-feedback', ''); ouvrirPanneau('jeu') })
$('memory').addEventListener('input', afficherMemoire)
$('password-toggle').addEventListener('click', () => afficherMotDePasse($('password').type === 'password'))
for (const id of ['email', 'password', 'code', 'new-email', 'new-password', 'new-password2']) $(id).addEventListener('input', () => feedback('profile-feedback', ''))
for (const b of document.querySelectorAll('#profile-form [data-mode]')) b.addEventListener('click', () => changerMode(b.dataset.mode))
$('new-pseudo').addEventListener('input', () => {
    $('new-pseudo-ok').hidden = !PSEUDO.test($('new-pseudo').value)
    feedback('profile-feedback', '')
})
$('securite').addEventListener('click', () => {
    fermerMenu()
    apresA2F = null
    ouvrirA2F({ depuisMenu: true }).catch(error => toast(error.message))
})
$('a2f-plus-tard').addEventListener('click', () => $('a2f-dialog').close())
for (const id of ['a2f-code', 'a2f-mdp']) {
    $(id).addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); $('a2f-activer').click() } })
}
$('a2f-fini').addEventListener('click', () => $('a2f-dialog').close())
$('a2f-dialog').addEventListener('close', () => {
    motDePasseA2F = null
    $('a2f-code').value = ''
    $('a2f-mdp').value = ''
    $('a2f-qr').removeAttribute('src')
    $('a2f-codes').replaceChildren()
    const suite = apresA2F
    apresA2F = null
    if (suite) suite()
})
$('a2f-activer').addEventListener('click', async () => {
    const code = $('a2f-code').value.replace(/\s+/g, '')
    if (!/^\d{6}$/.test(code)) {
        feedback('a2f-feedback', 'Entre le code à 6 chiffres de ton application.', true)
        $('a2f-code').focus()
        return
    }
    const motDePasse = motDePasseA2F || $('a2f-mdp').value
    if (!motDePasse) {
        feedback('a2f-feedback', 'Entre ton mot de passe.', true)
        $('a2f-mdp').focus()
        return
    }
    $('a2f-activer').disabled = true
    feedback('a2f-feedback', 'Activation…')
    try {
        const { codes } = await call(api.a2fActiver(code, motDePasse))
        motDePasseA2F = null
        if (codes.length === 0) {
            // déjà activée entre-temps (depuis le site) : pas de nouveaux codes de secours
            $('a2f-dialog').close()
            toast('La double authentification est active sur ton compte.')
            return
        }
        $('a2f-codes').replaceChildren(...codes.map(c => el('code', null, c)))
        $('a2f-etape-qr').hidden = true
        $('a2f-etape-codes').hidden = false
        feedback('a2f-codes-feedback', '')
    } catch (error) {
        feedback('a2f-feedback', error.message, true)
    } finally { $('a2f-activer').disabled = false }
})
// les codes de secours ne disparaissent pas par Échap : seulement par « C'est noté »
$('a2f-dialog').addEventListener('cancel', event => { if (!$('a2f-etape-codes').hidden) event.preventDefault() })
$('a2f-copier').addEventListener('click', async () => {
    const codes = [...$('a2f-codes').children].map(c => c.textContent).join('\n')
    try {
        await call(api.copierCodes(codes))
        feedback('a2f-codes-feedback', 'Codes copiés : colle-les dans un endroit sûr.')
    } catch { feedback('a2f-codes-feedback', 'Copie impossible : recopie les codes à la main.', true) }
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
    if ($('profile-form').dataset.mode === 'inscription') return creerCompte()
    if (!$('email').value.trim() || !$('password').value) {
        feedback('profile-feedback', 'Entre ton e-mail (ou ton pseudo) et ton mot de passe.', true)
        $(!$('email').value.trim() ? 'email' : 'password').focus()
        return
    }
    if (!$('code-champ').hidden && !$('code').value.trim()) {
        feedback('profile-feedback', 'Entre le code de ton application de double authentification.', true)
        $('code').focus()
        return
    }
    if (!$('accept-rules').checked) {
        feedback('profile-feedback', 'Tu dois accepter le règlement pour continuer.', true)
        return
    }
    $('save-profile').disabled = true
    feedback('profile-feedback', 'Connexion à ton compte HeraliZ…')
    const motDePasse = $('password').value
    try {
        const next = await call(api.connexion($('email').value.trim(), motDePasse, $('code-champ').hidden ? '' : $('code').value, true))
        if (next.a2f) {
            $('code-champ').hidden = false
            feedback('profile-feedback', 'Entre le code de ton application de double authentification.')
            $('code').focus()
            return
        }
        // double authentification déjà active si le code a été demandé : rien à proposer
        const codeDemande = !$('code-champ').hidden
        applyState(next)
        restoreProfileForm()
        if (codeDemande) { showTab('accueil'); showScreen('principal') }
        else await apresConnexion(motDePasse, false)
    } catch (error) { feedback('profile-feedback', error.message, true) }
    finally { $('save-profile').disabled = Boolean(state?.progress.busy) }
})

async function creerCompte() {
    const champs = verifierInscription()
    if (Object.keys(champs).length > 0) {
        afficherErreurs(champs)
        feedback('profile-feedback', Object.values(champs)[0], true)
        return
    }
    if (!$('accept-rules').checked) {
        feedback('profile-feedback', 'Tu dois accepter le règlement pour continuer.', true)
        return
    }
    effacerErreurs()
    $('save-profile').disabled = true
    feedback('profile-feedback', 'Création de ton compte HeraliZ…')
    const motDePasse = $('new-password').value
    try {
        const next = await call(api.inscription($('new-pseudo').value, $('new-email').value.trim(), motDePasse, $('new-password2').value, true))
        applyState(next)
        restoreProfileForm()
        $('new-pseudo').value = ''
        $('new-email').value = ''
        $('new-pseudo-ok').hidden = true
        changerMode('connexion')
        toast(`Compte créé : bienvenue, ${next.profile?.username || 'survivant'} !`)
        await apresConnexion(motDePasse, true)
    } catch (error) {
        if (error.champs) afficherErreurs(error.champs)
        feedback('profile-feedback', error.message, true)
    } finally { $('save-profile').disabled = Boolean(state?.progress.busy) }
}

$('settings-form').addEventListener('submit', async event => {
    event.preventDefault()
    try {
        const next = await call(api.saveSettings({ ramGB: Number($('memory').value), fullscreen: $('fullscreen').checked }))
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
        // Session expirée : le compte a été oublié, retour à la connexion.
        if (state.comptesSite) {
            const next = await call(api.getState()).catch(() => null)
            if (next && !next.profile) { applyState(next); restoreProfileForm(); showScreen('identite'); toast(error.message) }
        }
    }
    finally { launchPending = false; updatePlay() }
})

// Mise à jour du launcher au démarrage (version installée) : true si elle s'installe, le launcher redémarre alors seul.
async function majLauncher() {
    let finir
    const fin = new Promise(resolve => { finir = resolve })
    // abonné avant la demande : aucun état perdu, même si le téléchargement échoue tout de suite
    const arreter = api.onMaj(etat => {
        if (etat?.etat === 'telechargement') splashStep(etat.pourcent, `Mise à jour du launcher : ${etat.pourcent} %`)
        else finir(etat?.etat)
    })
    try {
        splashStep(20, 'Recherche de mises à jour')
        const maj = await call(api.majLauncher()).catch(() => null)
        if (maj?.bloquee) toast(`La mise à jour ${maj.version} du launcher ne s’installe pas : retélécharge le launcher sur heraliz.fr.`)
        if (maj?.manuelle) nouvelleVersion = maj.version
        if (!maj?.disponible) return false
        splashStep(0, `Mise à jour du launcher (version ${maj.version})…`)
        const etat = await fin
        if (etat === 'installation') {
            splashStep(100, 'Installation de la mise à jour : le launcher redémarre tout seul…')
            return true
        }
        if (etat === 'erreur') toast('La mise à jour du launcher n’a pas pu se faire : elle sera retentée au prochain démarrage.')
        return false
    } finally { arreter() }
}

// Accord pour les captures d'écran de l'anticheat : demandé dès l'ouverture, avant tout le reste. Échap ne ferme pas la
// fenêtre ; « Je refuse » ferme le launcher. true quand le joueur a accepté.
function demanderAccord() {
    if (!api || state?.accordCapture) return Promise.resolve(true)
    return new Promise(resolve => {
        const fenetre = $('accord-dialog')
        fenetre.addEventListener('cancel', event => event.preventDefault())
        $('accord-oui').addEventListener('click', async () => {
            $('accord-oui').disabled = true
            try {
                applyState(await call(api.accordCapture(true)))
                fenetre.close()
                resolve(true)
            } catch (error) { toast(error.message); $('accord-oui').disabled = false }
        })
        $('accord-non').addEventListener('click', () => { api.windowAction('close'); resolve(false) })
        fenetre.showModal()
    })
}

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
            if (await majLauncher()) return
            splashStep(35, 'Chargement du profil')
            applyState(await call(api.getState()))
            api.onProgress(renderProgress)
            if (state.profile?.compte) {
                splashStep(50, 'Vérification du compte')
                // Un site lent ne doit pas bloquer le démarrage : le compte sera revérifié avant la partie.
                const compte = await Promise.race([call(api.verifierCompte()).catch(() => null), wait(5000).then(() => null)])
                if (compte) applyState(compte)
                if (compte?.sessionExpiree) toast('Ta session a expiré : reconnecte-toi avec ton compte HeraliZ.')
            }
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
    if (!(await demanderAccord())) return
    if (nouvelleVersion) proposerNouvelleVersion(nouvelleVersion)
}

// Mac et Linux (.deb) : le launcher ne se remplace pas seul, le joueur télécharge la nouvelle version (même lien que
// sur le site) et l'installe par-dessus l'ancienne. Compte et jeu sont gardés.
const CONSIGNE_MAJ = {
    darwin: 'Télécharge-la, ouvre le fichier .dmg et glisse HeraliZ Launcher dans Applications pour remplacer l’ancienne version.',
    linux: 'Télécharge-la et installe le nouveau paquet .deb par-dessus l’ancien (double-clic sur le fichier).'
}
function proposerNouvelleVersion(version) {
    $('maj-version').textContent = version
    $('maj-consigne').textContent = CONSIGNE_MAJ[state?.plateforme] || 'Télécharge-la sur heraliz.fr.'
    $('maj-dialog').showModal()
}
$('maj-telecharger').addEventListener('click', () => $('maj-dialog').close())
init()
