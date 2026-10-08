-- Pistolet à rayons Mk II (HeraliZ Wonder Weapons, généré par tools/wonder_armes.py).
-- Au Pack-a-Punch du mode zombies (NBT HeraliszZbPapNiveau / HeraliszZbPap de l'arme), « Mk II-Z » : chaque tir de la
-- rafale part en double, deux éclairs côte à côte (rafales de 6), chacun avec sa pleine puissance. Sans Pack-a-Punch :
-- comportement TaCZ normal (rien n'est changé).
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

function M.modify_property(api, id, value)
    if id == "bullet_amount" then
        if pap(api) > 0 then
            return 2
        end
    elseif id == "damage" then
        -- TaCZ partage les dégâts d'un tir entre ses balles (fusil à pompe) : on rend à chaque éclair sa pleine puissance
        if type(value) == "number" and pap(api) > 0 then
            return value * 2
        end
    end
    return value
end

-- dispersion : les deux éclairs partent en « V » serré, plus la dispersion normale de l'arme
function M.calcSpread(api, index, inaccuracy)
    if pap(api) <= 0 then
        return nil
    end
    local ecart = 0.16
    local x = ecart
    if index % 2 == 0 then
        x = -ecart
    end
    local r = math.tan(math.rad(inaccuracy)) * 8 * math.sqrt(math.random())
    local a = math.random() * 2 * math.pi
    return { x + r * math.cos(a), r * math.sin(a) }
end

return M
