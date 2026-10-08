-- Jet Gun (HeraliZ Wonder Weapons, généré par tools/wonder/jetgun.py).
-- Pas de munitions : la limite, c'est la chaleur (données « heat » de l'arme, jauge et verrou de TaCZ).
--   tir      : +per_shot de chaleur (x0,72 / x0,64 / x0,56 au Pack-a-Punch du mode zombies : « surchauffe réduite ») ;
--   relâché  : après cooling_delay, refroidissement de refroid (fraction de la jauge par seconde), +refroid_acc par seconde
--              écoulée (jusqu'à 2 s) ; x1,3 au Pack-a-Punch ;
--   100 %    : l'arme cale (verrou TaCZ : plus de tir) pendant over_heat_time (x pap_verrou au Pack-a-Punch), puis la
--              chaleur retombe en 1,2 s et le verrou saute ;
--   purge    : touche de rechargement ; possible si chaleur >= seuil_purge et pas de surchauffe ; la vapeur sort à
--              0,45 s et vide purge (fraction de la jauge) par tick.
local M = {}

local function pap(api)
    local nbt = api:getNbt()
    if nbt == nil then
        return 0
    end
    local n = nbt:getInt("HeraliszZbPapNiveau")
    if n > 0 then
        return n
    end
    if nbt:contains("HeraliszZbPap") then
        return 1
    end
    return 0
end

local function param(api, cle, defaut)
    local p = api:getScriptParams()
    if p ~= nil then
        local v = p[cle]
        if v ~= nil then
            return v
        end
    end
    return defaut
end

local function facteur(api)
    local n = pap(api)
    if n <= 0 then
        return 1.0
    end
    return math.max(0.45, 0.80 - 0.08 * n)
end

function M.shoot(api)
    api:shootOnce(false)
end

function M.handle_shoot_heat(api)
    local max = api:getHeatMax()
    local h = math.min(api:getHeatAmount() + api:getHeatPerShot() * facteur(api), max)
    api:setHeatAmount(h)
    if h >= max then
        api:setOverheatLocked(true)
    end
end

local function purge_possible(api, h, max)
    return (not api:isOverheatLocked()) and h >= max * param(api, "seuil_purge", 0.08)
end

function M.tick_heat(api, ts)
    if not api:hasHeatData() then
        return
    end
    local max = api:getHeatMax()
    local h = api:getHeatAmount()
    local now = api:getCurrentTimestamp()
    local n = pap(api)
    local purge = api:getReloadStateType() ~= 0
    if api:isOverheatLocked() then
        local verrou = api:getOverheatTime()
        if n > 0 then
            verrou = verrou * param(api, "pap_verrou", 0.55)
        end
        if now - ts >= verrou then
            h = h - max / 24
            if h <= 0 then
                h = 0
                api:setOverheatLocked(false)
            end
            api:setHeatAmount(h)
        end
    elseif purge then
        if api:getReloadTime() >= 450 and h > 0 then
            h = math.max(0, h - max * param(api, "purge", 0.055))
            api:setHeatAmount(h)
        end
    elseif h > 0 then
        local dt = now - ts - api:getCoolingDelay()
        if dt >= 0 then
            local t = math.min(dt / 1000, 2.0)
            local v = param(api, "refroid", 0.22) + param(api, "refroid_acc", 0.20) * t
            if n > 0 then
                v = v * 1.3
            end
            h = math.max(0, h - max * v / 20)
            api:setHeatAmount(h)
        end
    end
    if purge then
        return
    end
    local plein = api:getMaxAmmoCount()
    local cur = api:getAmmoCountInMagazine()
    local possible = purge_possible(api, h, max)
    if possible and cur >= plein then
        api:removeAmmoFromMagazine(1)
    elseif (not possible) and cur < plein then
        api:putAmmoInMagazine(plein - cur)
    end
end

function M.start_reload(api)
    return purge_possible(api, api:getHeatAmount(), api:getHeatMax())
end

return M
