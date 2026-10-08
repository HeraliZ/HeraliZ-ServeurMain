/* global document, window */
const $ = id => document.getElementById(id)
const api = window.heraliz
let state
let toastTimeout
let launchPending = false

const SPLASH_MIN_MS = 2200
const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

/* Screens: splash -> identity (first time) -> main. */
function showScreen(name) {
    document.body.dataset.screen = name
    if (name === 'identity') setTimeout(() => $('username').focus(), 450)
}

function splashStep(percent, text) {
    $('splash-fill').style.width = `${percent}%`
    if (text) $('splash-text').textContent = text
}

function profileReady() {
    return Boolean(state?.profile?.username && state.profile.rulesAccepted && state.profile.passwordSet)
}

/* Put the saved profile back in the identity form. Passwords are never read back. */
function restoreProfileForm() {
    $('username').value = state?.profile?.username || ''
    $('accept-rules').checked = state?.profile?.rulesAccepted || false
    $('password').value = ''
    $('password-confirm').value = ''
    feedback('profile-feedback', '')
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

function profileMatches() {
    return state?.profile?.username === $('username').value && $('accept-rules').checked && state.profile.rulesAccepted && state.profile.passwordSet
}

function updatePlay() {
    $('play').disabled = !state || launchPending || state.progress.busy
    $('play').textContent = state?.progress.phase === 'running' ? 'EN JEU' : state?.progress.busy || launchPending ? 'PRÉPARATION' : 'JOUER'
}

function renderProgress(progress) {
    state.progress = progress
    $('progress-area').hidden = progress.phase === 'idle'
    $('progress-area').classList.toggle('error', progress.phase === 'error')
    $('progress-message').textContent = progress.message
    $('progress-percent').textContent = progress.percent == null ? '' : `${progress.percent} %`
    $('progress').hidden = ['error', 'running', 'idle'].includes(progress.phase)
    if (progress.percent == null) $('progress').removeAttribute('value')
    else $('progress').value = progress.percent
    for (const id of ['username', 'password', 'password-confirm', 'accept-rules', 'save-profile', 'change-profile', 'logout', 'confirm-logout', 'memory', 'fullscreen', 'auto-connect']) $(id).disabled = progress.busy
    $('settings-form').querySelector('[type=submit]').disabled = progress.busy
    updatePlay()
}

function applyState(next) {
    state = next
    $('app-version').textContent = `v${state.version}`
    $('username').value = state.profile?.username || ''
    $('accept-rules').checked = state.profile?.rulesAccepted || false
    $('memory').max = state.settings.maxRAM
    $('memory').value = state.settings.ramGB
    $('memory-value').value = `${state.settings.ramGB} Go`
    $('fullscreen').checked = state.settings.fullscreen
    $('auto-connect').checked = state.settings.autoConnect
    $('current-user').textContent = state.profile?.username || ''
    $('logout-user').textContent = state.profile?.username || ''
    $('identity-back').hidden = !profileReady()
    $('password-missing').hidden = !state.profile || state.profile.passwordSet
    if (!state.discordConfigured) $('discord-button').title = 'Le lien de la communauté arrive bientôt.'
    renderProgress(state.progress)
}

async function refreshStatus() {
    $('refresh-status').disabled = true
    try {
        const status = await call(api.serverStatus())
        $('server-dot').classList.toggle('online', status.state === 'online')
        $('server-status').textContent = status.state === 'online' ? `En ligne · ${status.online} / ${status.max} joueurs` : status.state === 'unconfigured' ? 'Serveur en préparation' : 'Serveur hors ligne'
    } catch { $('server-status').textContent = 'Statut indisponible' }
    finally { $('refresh-status').disabled = false }
}

for (const button of document.querySelectorAll('[data-window]')) button.addEventListener('click', () => call(api.windowAction(button.dataset.window)).catch(error => toast(error.message)))
for (const button of document.querySelectorAll('[data-dialog]')) button.addEventListener('click', () => $(button.dataset.dialog).showModal())
for (const button of document.querySelectorAll('.dialog-close')) button.addEventListener('click', () => button.closest('dialog').close())
for (const button of document.querySelectorAll('[data-link]')) button.addEventListener('click', () => call(api.openLink(button.dataset.link)).catch(error => toast(error.message)))
$('change-profile').addEventListener('click', () => { restoreProfileForm(); showScreen('identity') })
$('identity-back').addEventListener('click', () => { restoreProfileForm(); updatePlay(); showScreen('main') })
$('logout-dialog').addEventListener('close', () => feedback('logout-feedback', ''))
$('confirm-logout').addEventListener('click', async () => {
    $('confirm-logout').disabled = true
    try {
        applyState(await call(api.logout()))
        restoreProfileForm()
        $('logout-dialog').close()
        showScreen('identity')
    } catch (error) { feedback('logout-feedback', error.message, true) }
    finally { $('confirm-logout').disabled = Boolean(state?.progress.busy) }
})
$('game-folder').addEventListener('click', () => call(api.openGameFolder()).catch(error => toast(error.message)))
$('refresh-status').addEventListener('click', refreshStatus)
$('memory').addEventListener('input', () => { $('memory-value').value = `${$('memory').value} Go` })
for (const id of ['username', 'password', 'password-confirm', 'accept-rules']) $(id).addEventListener('input', () => { feedback('profile-feedback', ''); updatePlay() })

$('profile-form').addEventListener('submit', async event => {
    event.preventDefault()
    if ($('password').value !== $('password-confirm').value) {
        feedback('profile-feedback', 'Les deux mots de passe ne correspondent pas.', true)
        $('password-confirm').focus()
        return
    }
    $('save-profile').disabled = true
    try {
        applyState(await call(api.saveProfile($('username').value, $('accept-rules').checked, $('password').value)))
        $('password').value = ''
        $('password-confirm').value = ''
        feedback('profile-feedback', 'Profil validé.')
        await wait(500)
        showScreen('main')
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
    if (!profileMatches()) {
        restoreProfileForm()
        showScreen('identity')
        feedback('profile-feedback', 'Valide ton pseudo et le règlement pour continuer.')
        return
    }
    if (launchPending || state.progress.busy) return
    launchPending = true
    updatePlay()
    try { await call(api.launch()) }
    catch (error) { renderProgress({ busy: false, phase: 'error', message: error.message, percent: null }) }
    finally { launchPending = false; updatePlay() }
})

async function init() {
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
            splashStep(70, 'Connexion au serveur')
            // A slow server must not hold the splash forever.
            await Promise.race([refreshStatus(), wait(4000)])
            setInterval(refreshStatus, 60000)
        } catch (error) { toast(error.message); $('play').disabled = true }
    }
    splashStep(90)
    await minSplash
    splashStep(100, 'Prêt')
    await wait(450)
    showScreen(profileReady() ? 'main' : 'identity')
}
init()
