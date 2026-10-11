const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('heraliz', Object.freeze({
    getState: () => ipcRenderer.invoke('heraliz:state'),
    majLauncher: () => ipcRenderer.invoke('heraliz:maj-launcher'),
    onMaj: callback => {
        const listener = (_event, etat) => callback(etat)
        ipcRenderer.on('heraliz:maj', listener)
        return () => ipcRenderer.removeListener('heraliz:maj', listener)
    },
    saveProfile: (username, acceptedRules) => ipcRenderer.invoke('heraliz:profile', { username, acceptedRules }),
    connexion: (email, password, code, acceptedRules) => ipcRenderer.invoke('heraliz:connexion', { email, password, code, acceptedRules }),
    verifierCompte: () => ipcRenderer.invoke('heraliz:compte'),
    etatComptes: () => ipcRenderer.invoke('heraliz:etat-comptes'),
    inscription: (pseudo, email, password, confirmation, acceptedRules) => ipcRenderer.invoke('heraliz:inscription', { pseudo, email, password, confirmation, acceptedRules }),
    a2fPreparer: () => ipcRenderer.invoke('heraliz:a2f-preparer'),
    a2fActiver: (code, password) => ipcRenderer.invoke('heraliz:a2f-activer', { code, password }),
    copierCodes: codes => ipcRenderer.invoke('heraliz:copier-codes', codes),
    logout: () => ipcRenderer.invoke('heraliz:logout'),
    saveSettings: settings => ipcRenderer.invoke('heraliz:settings', settings),
    launch: () => ipcRenderer.invoke('heraliz:launch'),
    serverStatus: () => ipcRenderer.invoke('heraliz:status'),
    openLink: name => ipcRenderer.invoke('heraliz:link', name),
    windowAction: action => ipcRenderer.invoke('heraliz:window', action),
    onProgress: callback => {
        const listener = (_event, progress) => callback(progress)
        ipcRenderer.on('heraliz:progress', listener)
        return () => ipcRenderer.removeListener('heraliz:progress', listener)
    }
}))
