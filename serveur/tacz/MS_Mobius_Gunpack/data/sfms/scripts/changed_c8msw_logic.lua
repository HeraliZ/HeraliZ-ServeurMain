local M = {}
--切勿相信未经大量实验信息验证稳定性的架构

local function getShotgunMaxAmmo(api) --用于获取param中所写的副弹仓扩容等级对应的弹药量，返回当前状态下的满弹药值
    local param = api:getScriptParams()
    local level = api:getMagExtentLevel()
    local shotgun_max_ammo = param.shotgun_max_ammo
    if (level == 1) then
        shotgun_max_ammo = param.shotgun_xmag1_ammo
    elseif (level == 2) then
        shotgun_max_ammo = param.shotgun_xmag2_ammo
    elseif (level == 3) then
        shotgun_max_ammo = param.shotgun_xmag3_ammo
    end
    return shotgun_max_ammo
end

local function transformAmmoDeploy(api) --在切换形态时重新分配弹药量和热量
    local shotgun_max_ammo = getShotgunMaxAmmo(api) + 1
    local max_ammo = api:getMaxAmmoCount()+1 --枪管里也有一发
    local max_heat = api:getHeatMax()
    local heat_to_ammo = max_ammo/max_heat --过热值乘此值为转化弹药量
    local ammo_to_heat = max_heat/shotgun_max_ammo --弹药量乘此值为转化过热值
    if (api:getFireMode()==SEMI) then --同上但是进入下挂后所需转化比值不同
        heat_to_ammo = shotgun_max_ammo/max_heat
        ammo_to_heat = max_heat/max_ammo
    end

    local ammo = api:removeAmmoFromMagazine(api:getMaxAmmoCount()) --尽量移除所有子弹并记录移除的数量
    if (api:hasAmmoInBarrel())then --如果枪管里有
        ammo = ammo + 1
        api:setAmmoInBarrel(false)
    end
    local HeatFromAmmo = ammo * ammo_to_heat --计算出对应过热值

    local heat = api:getHeatAmount() --获取热量值，不用清空，待会直接覆盖
    local AmmoFromHeat = math.floor(heat * heat_to_ammo + 0.5) --计算出对应弹药量(round)四舍五入

    api:setHeatAmount(HeatFromAmmo) --覆盖过热值为计算后结果
    if AmmoFromHeat>=1 then
        api:setAmmoInBarrel(true)
        AmmoFromHeat = AmmoFromHeat - 1
    end
    api:putAmmoInMagazine(AmmoFromHeat)
end

function M.handle_shoot_heat(api)
    --绕过
end

function M.tick_heat(api, heatTimestamp)
    api:setHeatAmount(math.min(api:getHeatAmount(),api:getHeatMax()))
    api:setOverheatLocked(false)--绝对不会过热
    local param = api:getScriptParams()
    --不用管热量，只用检测模式切换
    local cache = api:getCachedScriptData()
    if (cache == nil) then
        cache = {
            mag = 0, --弹匣等级，专门用来防止手欠在单发模式+创造下换弹匣配件导致弹药量溢出既定规划
            max_ammo = 0,
            max_shotgun_ammo = 0,
            mode = AUTO, -- 当前模式 默认掏出时为AUTO
            init = false --初始化
        }
    end
    if (cache.init)then --仅在已初始化后执行判断避免在SEMI状态下掏出时才执行
        if not (cache.mode==api:getFireMode())then --检测到两帧之间模式发生变化，触发弹药分配
            transformAmmoDeploy(api)
        end
        if (cache.mag~=api:getMagExtentLevel())and(cache.mode==SEMI)then
            if (not api:isReloadingNeedConsumeAmmo()) then --检测到弹匣更换，如果是单发模式且为创造模式,就把弹药量先扔干净然后拉至既定规划内的最大值
                local shotgun_max_ammo = getShotgunMaxAmmo(api)
                api:removeAmmoFromMagazine(api:getAmmoAmount())
                api:putAmmoInMagazine(shotgun_max_ammo)
            end
            --无论如何先把热值等比缩放一下免得刷弹药
            local multi = cache.max_ammo/(api:getMaxAmmoCount()+1)
            local heat = api:getHeatAmount()
            api:setHeatAmount(heat*multi)
        end
        if (cache.mag~=api:getMagExtentLevel())and(cache.mode==AUTO)then
            --无论如何先把热值等比缩放一下免得刷弹药
            local multi = cache.max_shotgun_ammo/(getShotgunMaxAmmo(api)+1)
            local heat = api:getHeatAmount()
            api:setHeatAmount(heat*multi)
        end
    else
        cache.init = true
    end
    cache.mode = api:getFireMode()
    cache.mag = api:getMagExtentLevel()
    cache.max_ammo = api:getMaxAmmoCount()+1
    cache.max_shotgun_ammo = getShotgunMaxAmmo(api)+1
    api:cacheScriptData(cache)
end

function M.shoot(api)
    local param = api:getScriptParams()
    local bullet_count = param.bullet_count - 1
    if(api:getFireMode() == AUTO)then
        api:shootOnce(api:isShootingNeedConsumeAmmo())
    elseif(api:getFireMode() == SEMI)then
        api:shootOnce(api:isShootingNeedConsumeAmmo())
        for i = 0,bullet_count,1 do
            api:shootOnce(false)
        end
    end
end

function M.calcSpread(api,num,spread)
    local param = api:getScriptParams()
    local final_spread = spread
    if (api:getFireMode() == SEMI) then -- 如果是单发模式，就用data参数中的对应值覆盖输入的spread，否则绕过
        final_spread = param.shotgun_spread
    end
    final_spread = final_spread/8 -- 用来避免使用者产生不适
    local angle = (2*(math.pi)*math.random()) -- 弧度制角度随机值
    local final_spread = (final_spread*math.random()) -- 偏移随机插值
    return{math.sin(angle)*final_spread,math.cos(angle)*final_spread}
end
--前有原版逻辑

-- 当开始换弹的时候会调用一次
function M.start_reload(api)
    return true
end

-- 这是个 lua 函数，用来从枪 data 文件里获取装弹相关的动画时间点，由于 lua 内的时间是毫秒，所以要和 1000 做乘算
local function getReloadTimingFromParam(param)
    local reload_feed = param.reload_feed
    local reload_cooldown = param.reload_cooldown
    local empty_feed = param.empty_feed
    local empty_cooldown = param.empty_cooldown
    if (reload_feed == nil or reload_cooldown == nil or empty_feed == nil or empty_cooldown == nil) then
        return nil, nil, nil, nil
    end
    reload_feed = reload_feed * 1000
    reload_cooldown = reload_cooldown * 1000
    empty_feed = empty_feed * 1000
    empty_cooldown = empty_cooldown * 1000

    -- 顺序返回获取到的这 4 个数组
    return reload_feed, reload_cooldown, empty_feed, empty_cooldown
end

-- 判断这个状态是否是空仓换弹过程中的其中一个阶段。包括空仓换弹的收尾阶段
local function isReloadingEmpty(stateType)
    return stateType == EMPTY_RELOAD_FEEDING or stateType == EMPTY_RELOAD_FINISHING
end

-- 判断这个状态是否是战术换弹过程中的其中一个阶段。包括战术换弹的收尾阶段
local function isReloadingTactical(stateType)
    return stateType == TACTICAL_RELOAD_FEEDING or stateType == TACTICAL_RELOAD_FINISHING
end

-- 判断这个状态是否是任意换弹过程中的其中一个阶段。包括任意换弹的收尾阶段
local function isReloading(stateType)
    return isReloadingEmpty(stateType) or isReloadingTactical(stateType)
end

-- 判断这个状态是否是任意换弹过程中的的收尾阶段
local function isReloadFinishing(stateType)
    return stateType == EMPTY_RELOAD_FINISHING or stateType == TACTICAL_RELOAD_FINISHING
end

local function finishReload(api, is_tactical) ----已被爆改
    local shotgun_max_ammo = getShotgunMaxAmmo(api) --获取霰弹枪弹容量
    local needAmmoCount = api:getNeededAmmoAmount()
    if (api:getFireMode() == SEMI) then
        needAmmoCount = shotgun_max_ammo - api:getAmmoAmount() -- 如果是单发模式（下挂）则用下挂所需装入的弹药数量填进弹匣
    end
    if (api:isReloadingNeedConsumeAmmo()) then
        -- 需要消耗弹药（生存或冒险）的话就消耗换弹所需的弹药并将消耗的数量装填进弹匣
        api:putAmmoInMagazine(api:consumeAmmoFromPlayer(needAmmoCount))
    else
        -- 不需要消耗弹药（创造）的话就直接把弹匣塞满
        api:putAmmoInMagazine(needAmmoCount)
    end
    if not is_tactical then
        local i = api:removeAmmoFromMagazine(1);
        if i ~= 0 then
            api:setAmmoInBarrel(true)
        end
    end
end

function M.tick_reload(api)

    -- 从枪 data 文件中获取所有需要传入逻辑机的参数，注意此时的 param 是个列表，还不能直接拿来用
    local param = api:getScriptParams();
    -- 调用刚才的 lua 函数，把 param 里包含的八个参数依次赋值给我们新定义的变量
    local reload_feed, reload_cooldown, empty_feed, empty_cooldown = getReloadTimingFromParam(param)
    -- 照例检查是否有参数缺失
    if (reload_feed == nil or reload_cooldown == nil or empty_feed == nil or empty_cooldown == nil) then
        return NOT_RELOADING, -1
    end

    -- 获取当前弹匣等级，我们假设最多 3 级
    local mag_level = math.min(api:getMagExtentLevel(), 3) + 1

    local countDown = -1
    local stateType = NOT_RELOADING
    local oldStateType = api:getReloadStateType()

    -- 获取换弹时间，在玩家按下 R 的一瞬间作为零点，单位是毫秒。假设玩家在一秒前按下了 R ，那么此时这个时间就是 1000
    local progressTime = api:getReloadTime()

    if isReloadingEmpty(oldStateType) then
        local feed_time = empty_feed
        local finishing_time = empty_cooldown
        if progressTime < feed_time then
            stateType = EMPTY_RELOAD_FEEDING
            countDown = feed_time - progressTime
        elseif progressTime < finishing_time then
            stateType = EMPTY_RELOAD_FINISHING
            countDown = finishing_time - progressTime
        else
            stateType = NOT_RELOADING;
            countDown = -1
        end
    elseif isReloadingTactical(oldStateType) then
        local feed_time = reload_feed
        local finishing_time = reload_cooldown
        if progressTime < feed_time then
            stateType = TACTICAL_RELOAD_FEEDING
            countDown = feed_time - progressTime
        elseif progressTime < finishing_time then
            stateType = TACTICAL_RELOAD_FINISHING
            countDown = finishing_time - progressTime
        else
            stateType = NOT_RELOADING;
            countDown = -1
        end
    else
        stateType = NOT_RELOADING;
        countDown = -1
    end

    if oldStateType == EMPTY_RELOAD_FEEDING and oldStateType ~= stateType then
        finishReload(api,false);
    end

    if oldStateType == TACTICAL_RELOAD_FEEDING and oldStateType ~= stateType then
        finishReload(api, true);
    end

    return stateType, countDown
end

return M