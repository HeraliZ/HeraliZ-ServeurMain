const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('heraliz', Object.freeze({
    getState: () => ipcRenderer.invoke('heraliz:state'),
    saveProfile: (username, acceptedRules) => ipcRenderer.invoke('heraliz:profile', { username, acceptedRules }),
    connexion: (email, password, code, acceptedRules) => ipcRenderer.invoke('heraliz:connexion', { email, password, code, acceptedRules }),
    verifierCompte: () => ipcRenderer.invoke('heraliz:compte'),
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
