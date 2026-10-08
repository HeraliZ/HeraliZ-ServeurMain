local track_line_top = {value = 0}
local static_track_top = {value = 0}
local blending_track_top = {value = 0}
local function increment(obj)
    obj.value = obj.value + 1
    return obj.value - 1
end

local STATIC_TRACK_LINE = increment(track_line_top)
local BASE_TRACK = increment(static_track_top)
local BOLT_CAUGHT_TRACK = increment(static_track_top)
local MAIN_TRACK = increment(static_track_top)

local GUN_KICK_TRACK_LINE = increment(track_line_top)

local BLENDING_TRACK_LINE = increment(track_line_top)
local MOVEMENT_TRACK = increment(blending_track_top)

local ADS_TRACK_LINE = increment(track_line_top)
local ADS_TRACK = increment(blending_track_top)

local function isNoAmmo(context)
    return (not context:hasBulletInBarrel()) and (context:getAmmoCount() <= 0)
end

local function runInspectAnimation(context)
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
    context:runAnimation("inspect", track, false, PLAY_ONCE_STOP, 0.2)
end

local base_track_state = {}

function base_track_state.entry(this, context)
    context:runAnimation("static_idle", context:getTrack(STATIC_TRACK_LINE, BASE_TRACK), false, LOOP, 0)
end

local bolt_caught_states = {
    normal = {},
    bolt_caught = {}
}

function bolt_caught_states.normal.entry(this, context)
    this.bolt_caught_states.normal.update(this, context)
end

function bolt_caught_states.normal.update(this, context)
    if (isNoAmmo(context)) then
        context:trigger(this.INPUT_BOLT_CAUGHT)
    end
end

function bolt_caught_states.normal.transition(this, context, input)
    if (input == this.INPUT_BOLT_CAUGHT) then
        return this.bolt_caught_states.bolt_caught
    end
end

function bolt_caught_states.bolt_caught.entry(this, context)
    context:runAnimation("static_bolt_caught", context:getTrack(STATIC_TRACK_LINE, BOLT_CAUGHT_TRACK), false, LOOP, 0)
end

function bolt_caught_states.bolt_caught.update(this, context)
    if (not isNoAmmo(context)) then
        context:trigger(this.INPUT_BOLT_NORMAL)
    end
end

function bolt_caught_states.bolt_caught.transition(this, context, input)
    if (input == this.INPUT_BOLT_NORMAL) then
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, BOLT_CAUGHT_TRACK))
        return this.bolt_caught_states.normal
    end
end

local ads_state = {
    aimming = {},
    not_aim = {},
    shot = 0,
    is_aim = 0
}

function ads_state.not_aim.update(this, context)
    if (context:isAiming()) then
        context:trigger(this.INPUT_AIM_IN)
    end
end

function ads_state.not_aim.transition(this, context, input)
    if (input == this.INPUT_AIM_IN) then
        return this.ads_state.aimming
    end
end

function ads_state.aimming.entry(this, context)
    local track = context:getTrack(ADS_TRACK_LINE, ADS_TRACK)
    ads_state.is_aim = 1
    if (ads_state.shot == 1) then
        context:runAnimation("pull_in_delay", track, true, PLAY_ONCE_HOLD, 0.2)
        ads_state.shot = 0
    else
        context:runAnimation("pull_in", track, true, PLAY_ONCE_HOLD, 0.2)
    end
end

function ads_state.aimming.update(this, context)
    if (not context:isAiming()) then
        context:trigger(this.INPUT_AIM_OUT)
    end
end

function ads_state.aimming.transition(this, context, input)
    if (context:getAttachment("MUZZLE") == "exception_armorer:skill_book_1") then
        if (input == this.INPUT_AIM_OUT) then
            ads_state.is_aim = 0
            return this.ads_state.not_aim
        elseif (input == INPUT_SHOOT) then
            ads_state.shot = 1
            return this.ads_state.aimming
        end
    else
        if (input == this.INPUT_AIM_OUT) then
            ads_state.is_aim = 0
            return this.ads_state.not_aim
        elseif (input == INPUT_SHOOT or input == INPUT_BOLT) then
            ads_state.shot = 1
            return this.ads_state.aimming
        end
    end
end

function ads_state.aimming.exit(this, context)
    local track = context:getTrack(ADS_TRACK_LINE, ADS_TRACK)
    if (ads_state.is_aim == 0) then
        context:runAnimation("pull_exit", track, true, PLAY_ONCE_STOP, 0.2)
    end
end

local main_track_states = {
    start = {},
    idle = {},
    inspect = {},
    final = {},
    bayonet_counter = 0
}

function main_track_states.start.transition(this, context, input)
    if (input == INPUT_DRAW) then
        context:runAnimation("draw", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0)
        return this.main_track_states.idle
    end
end

function main_track_states.idle.transition(this, context, input)
    if (input == INPUT_PUT_AWAY) then
        return this.main_track_states.final
    end
    if (input == INPUT_RELOAD) then
        return this.main_track_states.idle
    end
    if (input == INPUT_SHOOT) then
        context:popShellFrom(0)
        return this.main_track_states.idle
    end
    if (input == INPUT_BOLT) then
        if (context:getAttachment("MUZZLE") == "exception_armorer:skill_book_1") then
        else
            context:runAnimation("bolt", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        end
        return this.main_track_states.idle
    end
    if (input == INPUT_INSPECT) then
        runInspectAnimation(context)
        return this.main_track_states.inspect
    end
    if (input == INPUT_BAYONET_MUZZLE) then
        local counter = this.main_track_states.bayonet_counter
        local animationName = "melee_bayonet_" .. tostring(counter + 1)
        this.main_track_states.bayonet_counter = (counter + 1) % 3
        context:runAnimation(animationName, context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.idle
    end
    if (input == INPUT_BAYONET_STOCK) then
        context:runAnimation("melee_stock", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.idle
    end
    if (input == INPUT_BAYONET_PUSH) then
        context:runAnimation("melee_push", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.idle
    end
end

function main_track_states.inspect.entry(this, context)
    context:setShouldHideCrossHair(true)
end

function main_track_states.inspect.exit(this, context)
    context:setShouldHideCrossHair(false)
end

function main_track_states.inspect.update(this, context)
    if (context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
        context:trigger(this.INPUT_INSPECT_RETREAT)
    end
end

function main_track_states.inspect.transition(this, context, input)
    if (input == this.INPUT_INSPECT_RETREAT) then
        return this.main_track_states.idle
    end
    if (input == INPUT_SHOOT) then
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))
        return this.main_track_states.idle
    end
    return this.main_track_states.idle.transition(this, context, input)
end

local gun_kick_state = {}

function gun_kick_state.transition(this, context, input)
    if (input == INPUT_SHOOT) then
        if (context:getAimingProgress() >= 1) then
            if (context:getAttachment("MUZZLE") == "exception_armorer:skill_book_1") then
                local track = context:findIdleTrack(GUN_KICK_TRACK_LINE, false)
                context:runAnimation("shoot_skill", track, true, PLAY_ONCE_STOP, 0)
                context:runAnimation("bolt_delay", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
            else
                local track = context:findIdleTrack(GUN_KICK_TRACK_LINE, false)
                context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
            end
        end
    end
    return nil
end

local movement_track_states = {
    idle = {},
    run = {
        mode = -1
    },
    walk = {
        mode = -1
    }
}

function movement_track_states.idle.update(this, context)
    local track = context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK)
    if (context:isStopped(track) or context:isHolding(track)) then
        context:runAnimation("idle", track, true, LOOP, 0)
    end
end

function movement_track_states.idle.transition(this, context, input)
    if (input == INPUT_RUN) then
        return this.movement_track_states.run
    elseif (input == INPUT_WALK) then
        return this.movement_track_states.walk
    end
end

function movement_track_states.run.entry(this, context)
    this.movement_track_states.run.mode = -1
    context:runAnimation("run_start", context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK), true, PLAY_ONCE_HOLD, 0.2)
end

function movement_track_states.run.exit(this, context)
    context:runAnimation("run_end", context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK), true, PLAY_ONCE_HOLD, 0.3)
end

function movement_track_states.run.update(this, context)
    local track = context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK)
    local state = this.movement_track_states.run;
    if (context:isHolding(track)) then
        context:runAnimation("run", track, true, LOOP, 0.2)
        state.mode = 0
        context:anchorWalkDist()
    end
    if (state.mode ~= -1) then
        if (not context:isOnGround()) then
            if (state.mode ~= 1) then
                state.mode = 1
                context:runAnimation("run_hold", track, true, LOOP, 0.6)
            end
        else
            if (state.mode ~= 0) then
                state.mode = 0
                context:runAnimation("run", track, true, LOOP, 0.2)
            end
            context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
        end
    end
end

function movement_track_states.run.transition(this, context, input)
    if (input == INPUT_IDLE) then
        return this.movement_track_states.idle
    elseif (input == INPUT_WALK) then
        return this.movement_track_states.walk
    end
end

function movement_track_states.walk.entry(this, context)
    this.movement_track_states.walk.mode = -1
end

function movement_track_states.walk.exit(this, context)
    context:runAnimation("idle", context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK), true, PLAY_ONCE_HOLD, 0.4)
end

function movement_track_states.walk.update(this, context)
    local track = context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK)
    local state = this.movement_track_states.walk
    if (context:getShootCoolDown() > 0) then
        if (state.mode ~= 0) then
            state.mode = 0
            context:runAnimation("idle", track, true, LOOP, 0.3)
        end
    elseif (not context:isOnGround()) then
        if (state.mode ~= 0) then
            state.mode = 0
            context:runAnimation("idle", track, true, LOOP, 0.6)
        end
    elseif (context:getAimingProgress() > 0.5) then
        if (state.mode ~= 1) then
            state.mode = 1
            context:runAnimation("walk_aiming", track, true, LOOP, 0.3)
        end
    elseif (context:isInputUp()) then
        if (state.mode ~= 2) then
            state.mode = 2
            context:runAnimation("walk_forward", track, true, LOOP, 0.4)
            context:anchorWalkDist()
        end
    elseif (context:isInputDown()) then
        if (state.mode ~= 3) then
            state.mode = 3
            context:runAnimation("walk_backward", track, true, LOOP, 0.4)
            context:anchorWalkDist()
        end
    elseif (context:isInputLeft() or context:isInputRight()) then
        if (state.mode ~= 4) then
            state.mode = 4
            context:runAnimation("walk_sideway", track, true, LOOP, 0.4)
            context:anchorWalkDist()
        end
    end
    if (state.mode >= 1 and state.mode <= 4) then
        context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
    end
end

function movement_track_states.walk.transition(this, context, input)
    if (input == INPUT_IDLE) then
        return this.movement_track_states.idle
    elseif (input == INPUT_RUN) then
        return this.movement_track_states.run
    end
end

local M = {
    track_line_top = track_line_top,
    STATIC_TRACK_LINE = STATIC_TRACK_LINE,
    GUN_KICK_TRACK_LINE = GUN_KICK_TRACK_LINE,
    BLENDING_TRACK_LINE = BLENDING_TRACK_LINE,

    static_track_top = static_track_top,
    BASE_TRACK = BASE_TRACK,
    BOLT_CAUGHT_TRACK = BOLT_CAUGHT_TRACK,
    MAIN_TRACK = MAIN_TRACK,

    blending_track_top = blending_track_top,
    MOVEMENT_TRACK = MOVEMENT_TRACK,

    ADS_TRACK_LINE = ADS_TRACK_LINE,
    ADS_TRACK = ADS_TRACK,

    base_track_state = base_track_state,
    bolt_caught_states = bolt_caught_states,
    main_track_states = main_track_states,
    gun_kick_state = gun_kick_state,
    movement_track_states = movement_track_states,
    ads_state = ads_state,

    INPUT_BOLT_CAUGHT = "bolt_caught",
    INPUT_BOLT_NORMAL = "bolt_normal",
    INPUT_INSPECT_RETREAT = "inspect_retreat",
    INPUT_AIM_IN = "aim_in",
    INPUT_AIM_OUT = "aim_out"
}

function M:initialize(context)
    context:ensureTrackLineSize(track_line_top.value)
    context:ensureTracksAmount(STATIC_TRACK_LINE, static_track_top.value)
    context:ensureTracksAmount(BLENDING_TRACK_LINE, blending_track_top.value)
    context:ensureTracksAmount(ADS_TRACK_LINE, blending_track_top.value)
    self.movement_track_states.run.mode = -1
    self.movement_track_states.walk.mode = -1
end

function M:exit(context)
end

function M:states()
    return {
        self.base_track_state,
        self.ads_state.not_aim,
        self.bolt_caught_states.normal,
        self.main_track_states.start,
        self.gun_kick_state,
        self.movement_track_states.idle
    }
end

return M