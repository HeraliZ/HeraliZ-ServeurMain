-- Machine à états du Jet Gun (HeraliZ Wonder Weapons, générée par tools/wonder/jetgun.py), d'après celles de TaCZ.
-- Pistes statiques : base (static_idle), chaleur (jauge « chaleur » en pause, réglée sur la chaleur de l'arme), visée,
-- principale (draw, put_away, purge = reload_tactical, inspect). Pistes de mélange : mouvement, tir (fire_start ->
-- fire_loop -> fire_end), surchauffe (over_heat + static_over_heating, puis over_heat_fin).
local track_line_top = {value = 0}
local static_track_top = {value = 0}
local blending_track_top = {value = 0}
local function increment(obj)
    obj.value = obj.value + 1
    return obj.value - 1
end

local STATIC_TRACK_LINE = increment(track_line_top)
local BASE_TRACK = increment(static_track_top)
local HEAT_TRACK = increment(static_track_top)
local ADS_TRACK = increment(static_track_top)
local MAIN_TRACK = increment(static_track_top)

local BLENDING_TRACK_LINE = increment(track_line_top)
local MOVEMENT_TRACK = increment(blending_track_top)
local FIRE_TRACK = increment(blending_track_top)
local OVER_HEAT_TRACK = increment(blending_track_top)
local OVER_HEATING_TRACK = increment(blending_track_top)

local DELAI_ARRET = 230

local function isOverHeat(context)
    return context:isOverHeat()
end

-- ---------------------------------------------------------------- base
local base_track_state = {}

function base_track_state.entry(this, context)
    context:runAnimation("static_idle", context:getTrack(STATIC_TRACK_LINE, BASE_TRACK), false, LOOP, 0)
end

-- ---------------------------------------------------------------- chaleur
local heat_state = {lisse = 0}

local function relancer_jauge(context, track)
    context:runAnimation("chaleur", track, false, PLAY_ONCE_HOLD, 0)
    context:pauseAnimation(track)
end

function heat_state.entry(this, context)
    local track = context:getTrack(STATIC_TRACK_LINE, HEAT_TRACK)
    relancer_jauge(context, track)
    this.heat_state.lisse = context:getHeatProgress()
    context:setAnimationProgress(track, math.min(0.999, this.heat_state.lisse), true)
end

function heat_state.update(this, context)
    local track = context:getTrack(STATIC_TRACK_LINE, HEAT_TRACK)
    local s = this.heat_state
    local cible = context:getHeatProgress()
    s.lisse = s.lisse + (cible - s.lisse) * 0.2
    if math.abs(cible - s.lisse) < 0.002 then
        s.lisse = cible
    end
    if context:isStopped(track) then
        relancer_jauge(context, track)
    end
    context:setAnimationProgress(track, math.min(0.999, math.max(0, s.lisse)), true)
end

-- ---------------------------------------------------------------- tir
local fire_states = {idle = {}, start = {}, loop = {}, dernier = 0}

local function marquer(this, context)
    this.fire_states.dernier = context:getCurrentTimestamp()
end

local function tirEnCours(this, context)
    return context:getCurrentTimestamp() - this.fire_states.dernier < DELAI_ARRET
end

function fire_states.idle.transition(this, context, input)
    if input == INPUT_SHOOT then
        marquer(this, context)
        return this.fire_states.start
    end
end

function fire_states.start.entry(this, context)
    context:runAnimation("fire_start", context:getTrack(BLENDING_TRACK_LINE, FIRE_TRACK), true, PLAY_ONCE_HOLD, 0.06)
end

function fire_states.start.update(this, context)
    local track = context:getTrack(BLENDING_TRACK_LINE, FIRE_TRACK)
    if context:isHolding(track) then
        if tirEnCours(this, context) then
            context:trigger(this.INPUT_FIRE_LOOP)
        else
            context:trigger(this.INPUT_FIRE_STOP)
        end
    end
end

local function fire_transition(this, context, input)
    if input == INPUT_SHOOT then
        marquer(this, context)
        return nil
    end
    if input == this.INPUT_FIRE_STOP then
        context:runAnimation("fire_end", context:getTrack(BLENDING_TRACK_LINE, FIRE_TRACK), true, PLAY_ONCE_STOP, 0.08)
        return this.fire_states.idle
    end
    if input == this.INPUT_FIRE_CUT then
        context:stopAnimation(context:getTrack(BLENDING_TRACK_LINE, FIRE_TRACK))
        return this.fire_states.idle
    end
    return nil
end

function fire_states.start.transition(this, context, input)
    if input == this.INPUT_FIRE_LOOP then
        return this.fire_states.loop
    end
    return fire_transition(this, context, input)
end

function fire_states.loop.entry(this, context)
    context:runAnimation("fire_loop", context:getTrack(BLENDING_TRACK_LINE, FIRE_TRACK), true, LOOP, 0)
end

function fire_states.loop.update(this, context)
    if not tirEnCours(this, context) then
        context:trigger(this.INPUT_FIRE_STOP)
    end
end

function fire_states.loop.transition(this, context, input)
    return fire_transition(this, context, input)
end

-- ---------------------------------------------------------------- surchauffe
local over_heat_states = {normal = {}, over_heat = {}}

function over_heat_states.normal.update(this, context)
    if isOverHeat(context) then
        context:trigger(this.INPUT_OVER_HEAT)
    end
end

function over_heat_states.normal.transition(this, context, input)
    if input == this.INPUT_OVER_HEAT then
        return this.over_heat_states.over_heat
    end
end

function over_heat_states.over_heat.entry(this, context)
    context:trigger(this.INPUT_FIRE_CUT)
    context:trigger(this.INPUT_INSPECT_RETREAT)
    context:runAnimation("over_heat", context:getTrack(BLENDING_TRACK_LINE, OVER_HEAT_TRACK), true, PLAY_ONCE_STOP, 0.05)
    context:runAnimation("static_over_heating", context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK), true, LOOP, 0.5)
end

function over_heat_states.over_heat.update(this, context)
    if not isOverHeat(context) then
        context:trigger(this.INPUT_COOLING_HEAT)
    end
end

function over_heat_states.over_heat.transition(this, context, input)
    if input == this.INPUT_COOLING_HEAT then
        context:runAnimation("over_heat_fin", context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK), true, PLAY_ONCE_STOP, 0.25)
        return this.over_heat_states.normal
    end
end

-- ---------------------------------------------------------------- piste principale
local main_track_states = {start = {}, idle = {}, inspect = {}, final = {}}

local function runPutAwayAnimation(context)
    local put_away_time = context:getPutAwayTime()
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
    context:runAnimation("put_away", track, false, PLAY_ONCE_HOLD, put_away_time * 0.75)
    context:setAnimationProgress(track, 1, true)
    context:adjustAnimationProgress(track, -put_away_time, false)
end

function main_track_states.start.transition(this, context, input)
    if input == INPUT_DRAW then
        context:runAnimation("draw", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0)
        return this.main_track_states.idle
    end
end

function main_track_states.idle.transition(this, context, input)
    if input == INPUT_PUT_AWAY then
        runPutAwayAnimation(context)
        return this.main_track_states.final
    end
    if input == INPUT_RELOAD then
        context:trigger(this.INPUT_FIRE_CUT)
        -- purge lourde au-dessus de 60 % de chaleur (coup sec, tapes sur le carter), purge rapide en dessous (trois crans)
        local nom = "reload_tactical"
        if context:getHeatProgress() >= 0.6 then
            nom = "reload_empty"
        end
        context:runAnimation(nom, context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.15)
        return this.main_track_states.idle
    end
    if input == INPUT_INSPECT and not isOverHeat(context) and not tirEnCours(this, context) then
        context:runAnimation("inspect", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.inspect
    end
end

function main_track_states.inspect.entry(this, context)
    context:setShouldHideCrossHair(true)
end

function main_track_states.inspect.exit(this, context)
    context:setShouldHideCrossHair(false)
end

function main_track_states.inspect.update(this, context)
    if context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)) then
        context:trigger(this.INPUT_INSPECT_RETREAT)
    end
end

function main_track_states.inspect.transition(this, context, input)
    if input == this.INPUT_INSPECT_RETREAT then
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))
        return this.main_track_states.idle
    end
    if input == INPUT_SHOOT then
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))
        return this.main_track_states.idle
    end
    return this.main_track_states.idle.transition(this, context, input)
end

-- ---------------------------------------------------------------- mouvement (repris de TaCZ)
local movement_track_states = {idle = {}, run = {mode = -1}, walk = {mode = -1}}

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
    local state = this.movement_track_states.run
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

-- ---------------------------------------------------------------- visée (reprise de TaCZ)
local ADS_states = {aiming_progress = 0, normal = {}, aiming = {}}

function ADS_states.normal.entry(this, context)
    this.ADS_states.normal.update(this, context)
end

function ADS_states.normal.update(this, context)
    if ((context:getAimingProgress() > this.ADS_states.aiming_progress or context:getAimingProgress() == 1)
            and context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
        context:trigger(this.INPUT_AIM)
    else
        this.ADS_states.aiming_progress = context:getAimingProgress()
    end
end

function ADS_states.normal.transition(this, context, input)
    if (input == this.INPUT_AIM) then
        return this.ADS_states.aiming
    end
end

function ADS_states.aiming.entry(this, context)
    context:runAnimation("aim_start", context:getTrack(STATIC_TRACK_LINE, ADS_TRACK), false, PLAY_ONCE_HOLD, 0.2)
    context:trigger(this.INPUT_INSPECT_RETREAT)
end

function ADS_states.aiming.update(this, context)
    local track = context:getTrack(STATIC_TRACK_LINE, ADS_TRACK)
    if (context:isHolding(track)) then
        context:runAnimation("aim", track, false, PLAY_ONCE_HOLD, 0.2)
    end
    if (context:getAimingProgress() < this.ADS_states.aiming_progress or not context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
        context:trigger(this.INPUT_AIM_RETREAT)
    else
        this.ADS_states.aiming_progress = context:getAimingProgress()
    end
end

function ADS_states.aiming.transition(this, context, input)
    local track = context:getTrack(STATIC_TRACK_LINE, ADS_TRACK)
    if (input == this.INPUT_AIM_RETREAT) then
        context:runAnimation("aim_end", track, false, PLAY_ONCE_STOP, 0.2)
        context:setAnimationProgress(track, 1 - context:getAimingProgress(), true)
        return this.ADS_states.normal
    end
end

local M = {
    track_line_top = track_line_top,
    STATIC_TRACK_LINE = STATIC_TRACK_LINE,
    BLENDING_TRACK_LINE = BLENDING_TRACK_LINE,
    static_track_top = static_track_top,
    BASE_TRACK = BASE_TRACK,
    HEAT_TRACK = HEAT_TRACK,
    ADS_TRACK = ADS_TRACK,
    MAIN_TRACK = MAIN_TRACK,
    blending_track_top = blending_track_top,
    MOVEMENT_TRACK = MOVEMENT_TRACK,
    FIRE_TRACK = FIRE_TRACK,
    OVER_HEAT_TRACK = OVER_HEAT_TRACK,
    OVER_HEATING_TRACK = OVER_HEATING_TRACK,
    base_track_state = base_track_state,
    heat_state = heat_state,
    fire_states = fire_states,
    over_heat_states = over_heat_states,
    main_track_states = main_track_states,
    movement_track_states = movement_track_states,
    ADS_states = ADS_states,
    INPUT_FIRE_LOOP = "jet_fire_loop",
    INPUT_FIRE_STOP = "jet_fire_stop",
    INPUT_FIRE_CUT = "jet_fire_cut",
    INPUT_OVER_HEAT = "over_heat",
    INPUT_COOLING_HEAT = "cooling_heat",
    INPUT_INSPECT_RETREAT = "inspect_retreat",
    INPUT_AIM = "aim",
    INPUT_AIM_RETREAT = "aim_retreat"
}

function M:initialize(context)
    context:ensureTrackLineSize(track_line_top.value)
    context:ensureTracksAmount(STATIC_TRACK_LINE, static_track_top.value)
    context:ensureTracksAmount(BLENDING_TRACK_LINE, blending_track_top.value)
    self.movement_track_states.run.mode = -1
    self.movement_track_states.walk.mode = -1
    self.fire_states.dernier = 0
end

function M:exit(context)
end

function M:states()
    return {
        self.base_track_state,
        self.heat_state,
        self.main_track_states.start,
        self.fire_states.idle,
        self.over_heat_states.normal,
        self.movement_track_states.idle,
        self.ADS_states.normal
    }
end

return M
