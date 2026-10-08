-- Paralyzer (HeraliZ Wonder Weapons) : logique de l'arme, générée par tools/wonder/paralyzer.py (pz_lua.py).
-- Le rayon ne tire aucune balle : le mod heraliz_wonder reçoit chaque impulsion (GunShootEvent de TaCZ, 10 par seconde),
-- calcule le rayon et ses effets côté serveur et le dessine chez les joueurs. Ici, seulement la charge :
--   * la « chaleur » de TaCZ est la jauge : chaque impulsion la fait monter ; pleine = surchauffe (arme verrouillée) ;
--   * elle redescend seule quand on lâche (après un court délai), plus lentement après une surchauffe ;
--   * le chargeur affiché suit la charge (100 = pleine charge) ;
--   * recharge (R) = tours de manivelle : la charge monte pendant les tours (même courbe que l'animation) ; à vide, le
--     couteau de contact donne les derniers 15 % et fait sauter le verrou de surchauffe ;
--   * Pack-a-Punch (Petrifier, NBT HeraliszZbPapNiveau / HeraliszZbPap du mode zombies) : charge plus longue, recharge plus rapide.
local M = {}

local RECHARGE = 4.0
local RECHARGE_PAP = 3.0
local RECHARGE_SURCHAUFFE = 3.4
local FACTEURS_PAP = { 0.62, 0.56, 0.5 }

-- recharge à la manivelle (secondes depuis l'appui sur R ; mêmes temps que les animations)
local RT = { debut = 0.42, plein = 1.72, fin = 2.35 }
local RV = { debut = 1.12, plein = 2.7, contact = 3.02, fin = 3.7, reste = 0.15 }
-- fraction remontée (0..1) à intervalles réguliers entre le début et la fin des tours de manivelle
local COURBE_T = { 0.0, 0.0021, 0.0148, 0.0443, 0.0916, 0.1524, 0.2183, 0.2843, 0.3503, 0.4162, 0.4822, 0.5482, 0.6142, 0.6802, 0.7462, 0.8122, 0.8781, 0.9374, 0.9781, 0.9969, 1.0 }
local COURBE_V = { 0.0, 0.0006, 0.0044, 0.014, 0.0315, 0.0582, 0.095, 0.1418, 0.1983, 0.2632, 0.3348, 0.4108, 0.4882, 0.5657, 0.6431, 0.7206, 0.798, 0.8755, 0.9488, 0.9918, 1.0 }

local function pap(api)
    local nbt = api:getNbt()
    if nbt == nil then
        return 0
    end
    local n = nbt:getInt("HeraliszZbPapNiveau")
    if n > 0 then
        return math.min(n, 3)
    end
    if nbt:contains("HeraliszZbPap") then
        return 1
    end
    return 0
end

-- chargeur affiché = charge restante (arrondie au-dessus : 1 tant que l'arme n'a pas surchauffé)
local function maj_charge(api)
    local max = api:getMaxAmmoCount()
    local hmax = api:getHeatMax()
    if max <= 0 or hmax <= 0 then
        return
    end
    local voulu = math.ceil(max * (1 - api:getHeatAmount() / hmax) - 0.0001)
    if voulu < 0 then
        voulu = 0
    end
    if api:isOverheatLocked() then
        voulu = math.min(voulu, max - 1)
    end
    local actuel = api:getAmmoCountInMagazine()
    if voulu > actuel then
        api:putAmmoInMagazine(voulu - actuel)
    elseif voulu < actuel then
        api:removeAmmoFromMagazine(actuel - voulu)
    end
end

local function courbe(tab, u)
    if u <= 0 then
        return 0
    end
    if u >= 1 then
        return 1
    end
    local x = u * (#tab - 1)
    local i = math.floor(x)
    local f = x - i
    return tab[i + 1] + (tab[i + 2] - tab[i + 1]) * f
end

-- une impulsion du rayon : aucune balle, seulement la charge
function M.shoot(api)
    local hmax = api:getHeatMax()
    local par = api:getHeatPerShot()
    local n = pap(api)
    if n > 0 then
        par = par * FACTEURS_PAP[n]
    end
    local h = math.min(api:getHeatAmount() + par, hmax)
    api:setHeatAmount(h)
    if h >= hmax then
        api:setOverheatLocked(true)
    end
    maj_charge(api)
end

-- chaque tick (serveur, arme en main) : recharge
function M.tick_heat(api, heatTimestamp)
    local h = api:getHeatAmount()
    local hmax = api:getHeatMax()
    if h > 0 then
        local dt = api:getCurrentTimestamp() - heatTimestamp
        if api:isOverheatLocked() then
            if dt >= api:getOverheatTime() then
                h = h - hmax / (RECHARGE_SURCHAUFFE * 20)
            end
        elseif dt >= api:getCoolingDelay() then
            local t = RECHARGE
            if pap(api) > 0 then
                t = RECHARGE_PAP
            end
            h = h - hmax / (t * 20)
        end
        if h <= 0 then
            h = 0
            api:setOverheatLocked(false)
        end
        api:setHeatAmount(h)
    elseif api:isOverheatLocked() then
        api:setOverheatLocked(false)
    end
    maj_charge(api)
end

-- recharge à la manivelle : refusée si la charge est déjà pleine
function M.start_reload(api)
    local h = api:getHeatAmount()
    if api:getHeatMax() <= 0 or h <= 0 then
        return false
    end
    local vide = api:getReloadStateType() == EMPTY_RELOAD_FEEDING
    api:cacheScriptData({ h0 = h, vide = vide, arret = -1 })
    return true
end

-- chaque tick de la recharge : la charge suit les tours de manivelle de l'animation (jamais elle ne redescend)
function M.tick_reload(api)
    local cache = api:getCachedScriptData()
    if cache == nil or cache.arret ~= -1 then
        return NOT_RELOADING, -1
    end
    local t = api:getReloadTime() / 1000
    local h = api:getHeatAmount()
    local voulu
    if cache.vide then
        voulu = cache.h0 * (1 - (1 - RV.reste) * courbe(COURBE_V, (t - RV.debut) / (RV.plein - RV.debut)))
        if t >= RV.contact then
            voulu = 0
        end
    else
        voulu = cache.h0 * (1 - courbe(COURBE_T, (t - RT.debut) / (RT.plein - RT.debut)))
        if t >= RT.plein then
            voulu = 0
        end
    end
    if voulu < h then
        h = voulu
        api:setHeatAmount(h)
    end
    if h <= 0 then
        api:setOverheatLocked(false)
    end
    maj_charge(api)
    if cache.vide then
        if t < RV.contact then
            return EMPTY_RELOAD_FEEDING, math.floor((RV.contact - t) * 1000)
        elseif t < RV.fin then
            return EMPTY_RELOAD_FINISHING, math.floor((RV.fin - t) * 1000)
        end
    else
        if t < RT.plein then
            return TACTICAL_RELOAD_FEEDING, math.floor((RT.plein - t) * 1000)
        elseif t < RT.fin then
            return TACTICAL_RELOAD_FINISHING, math.floor((RT.fin - t) * 1000)
        end
    end
    return NOT_RELOADING, -1
end

-- recharge interrompue (changement d'arme...) : la charge gagnée reste acquise
function M.interrupt_reload(api)
    local cache = api:getCachedScriptData()
    if cache ~= nil and cache.arret == -1 then
        cache.arret = api:getReloadTime()
        api:cacheScriptData(cache)
    end
end

return M
