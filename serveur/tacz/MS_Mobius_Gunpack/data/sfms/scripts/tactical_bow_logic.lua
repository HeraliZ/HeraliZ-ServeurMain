local M = {}

function M.shoot(api)
    if (api:getAimingProgress() >= 1) then
        if (api:getAttachment("MUZZLE") == "exception_armorer:skill_book_1") then
            api:shootOnce(api:isShootingNeedConsumeAmmo())
            if (api:consumeAmmoFromPlayer(1) == 1) then
                api:setAmmoInBarrel(true);
                api:safeAsyncTask(function ()
                    api:shootOnce(api:isShootingNeedConsumeAmmo())
                    if (api:consumeAmmoFromPlayer(1) == 1) then
                        api:setAmmoInBarrel(true);
                        api:safeAsyncTask(function ()
                            api:shootOnce(api:isShootingNeedConsumeAmmo())
                            return false
                        end,500,0,1)
                    end
                    return false
                end,500,0,1)
            end
        else
            api:shootOnce(api:isShootingNeedConsumeAmmo())
        end
    end
end

function M.start_bolt(api)
    if (api:getAttachment("MUZZLE") == "exception_armorer:skill_book_1") then
        if (api:consumeAmmoFromPlayer(1) == 1) then
            api:setAmmoInBarrel(true);
        end
        return false
    end
    return true
end

function M.tick_bolt(api)
    local params = api:getScriptParams()
    local total_bolt_time = params.bolt_time * 1000
    local bolt_feed_time = params.bolt_feed_time * 1000
    if (total_bolt_time == nil or bolt_feed_time == nil) then
        return false
    end
    local bolt_time = api:getBoltTime()
    if (bolt_time < bolt_feed_time) then
        return true
    else
        if (not api:hasAmmoInBarrel()) then
            if (api:consumeAmmoFromPlayer(1) == 1) then
                api:setAmmoInBarrel(true);
            end
        end
        return bolt_time < total_bolt_time
    end
end

return M