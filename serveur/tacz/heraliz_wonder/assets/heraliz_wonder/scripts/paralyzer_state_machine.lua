-- Paralyzer (HeraliZ Wonder Weapons) : machine d'états des animations, générée par tools/wonder/paralyzer.py (pz_lua.py).
-- Hérite de celle de TaCZ (require "tacz_default_state_machine", jamais modifiée) et ajoute :
--   * le rayon : fire_start (la manivelle se lance), fire_loop rejoué tant que les impulsions arrivent, fire_end ;
--   * la surchauffe : over_heat (sursaut), puis static_over_heating en boucle (on remonte la manivelle), over_heat_end ;
--     la recharge (R) l'interrompt (la pose basse s'efface, l'animation de recharge prend la main) ;
--   * l'aiguille du cadran : static_charge, progression = chaleur de TaCZ, sur un ressort (elle monte, dépasse, revient) ;
--   * pas d'inspection pendant la surchauffe.
local default = require("tacz_default_state_machine")
local STATIC_TRACK_LINE = default.STATIC_TRACK_LINE
local BLENDING_TRACK_LINE = default.BLENDING_TRACK_LINE
local MAIN_TRACK = default.MAIN_TRACK
local CHARGE_TRACK = default.PRE_PARALLEL_TRACK_1
local BEAM_TRACK = default.BLEND_TRACK_1
local OVER_HEATING_TRACK = default.OVER_HEATING_TRACK

local INPUT_BEAM_STOP = "pz_beam_stop"
local INPUT_PZ_RETOUR = "pz_retour_surchauffe"

-- aiguille : ressort (raideur, amortissement), débord au-delà des butées, marge de l'animation static_charge
local K = 140.0
local C = 9.9
local DEBORD = 0.06
local MARGE = 0.1

-- ---------------------------------------------------------------- aiguille du cadran
local charge_states = { normal = {} }

function charge_states.normal.entry(this, context)
    context:runAnimation("static_charge", context:getTrack(STATIC_TRACK_LINE, CHARGE_TRACK), false, PLAY_ONCE_STOP, 0)
    this.pz_p = nil
end

function charge_states.normal.update(this, context)
    local track = context:getTrack(STATIC_TRACK_LINE, CHARGE_TRACK)
    if context:isStopped(track) then
        context:runAnimation("static_charge", track, false, PLAY_ONCE_STOP, 0)
    end
    local cible = context:getHeatProgress()
    local now = context:getCurrentTimestamp()
    if this.pz_p == nil then
        this.pz_p = cible
        this.pz_v = 0
        this.pz_t = now
    end
    local dt = (now - this.pz_t) / 1000
    this.pz_t = now
    if dt > 0.1 then
        dt = 0.1
    end
    if dt > 0 then
        local n = math.ceil(dt * 240)
        local h = dt / n
        for i = 1, n do
            local a = K * (cible - this.pz_p) - C * this.pz_v
            this.pz_v = this.pz_v + a * h
            this.pz_p = this.pz_p + this.pz_v * h
        end
    end
    if this.pz_p < -DEBORD then
        this.pz_p = -DEBORD
        this.pz_v = 0
    elseif this.pz_p > 1 + DEBORD then
        this.pz_p = 1 + DEBORD
        this.pz_v = 0
    end
    context:setAnimationProgress(track, (MARGE + this.pz_p) / (1 + 2 * MARGE), true)
end

function charge_states.normal.transition(this, context, input)
    return nil
end

-- ---------------------------------------------------------------- rayon
local beam_states = { idle = {}, fire = {} }

function beam_states.idle.transition(this, context, input)
    if input == INPUT_SHOOT and not context:isOverHeat() then
        return this.beam_states.fire
    end
    if input == INPUT_RELOAD then
        -- la recharge tourne la manivelle elle-même : on coupe la fin du rayon (piste de mélange)
        context:stopAnimation(context:getTrack(BLENDING_TRACK_LINE, BEAM_TRACK))
    end
end

function beam_states.fire.entry(this, context)
    context:runAnimation("fire_start", context:getTrack(BLENDING_TRACK_LINE, BEAM_TRACK), true, PLAY_ONCE_HOLD, 0.05)
end

function beam_states.fire.update(this, context)
    if context:isOverHeat() then
        context:trigger(INPUT_BEAM_STOP)
        return
    end
    local track = context:getTrack(BLENDING_TRACK_LINE, BEAM_TRACK)
    if context:isHolding(track) then
        if context:getCurrentTimestamp() - context:getLastShootTimestamp() > context:getShootInterval() + 120 then
            context:trigger(INPUT_BEAM_STOP)
        else
            context:runAnimation("fire_loop", track, true, PLAY_ONCE_HOLD, 0)
        end
    end
end

function beam_states.fire.transition(this, context, input)
    local track = context:getTrack(BLENDING_TRACK_LINE, BEAM_TRACK)
    if input == INPUT_BEAM_STOP then
        if context:isOverHeat() then
            context:stopAnimation(track)
        else
            context:runAnimation("fire_end", track, true, PLAY_ONCE_STOP, 0)
        end
        return this.beam_states.idle
    end
    if input == INPUT_PUT_AWAY or input == INPUT_RELOAD then
        context:stopAnimation(track)
        return this.beam_states.idle
    end
end

-- ---------------------------------------------------------------- surchauffe
local over_heat_states = { normal = {}, over_heat = {}, recharge = {}, retour = {} }

function over_heat_states.normal.update(this, context)
    if context:isOverHeat() then
        context:trigger(this.INPUT_OVER_HEAT)
    end
end

function over_heat_states.normal.transition(this, context, input)
    if input == this.INPUT_OVER_HEAT then
        return this.over_heat_states.over_heat
    end
end

function over_heat_states.over_heat.entry(this, context)
    context:stopAnimation(context:getTrack(BLENDING_TRACK_LINE, BEAM_TRACK))
    context:runAnimation("over_heat", context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK), true, PLAY_ONCE_HOLD, 0)
end

function over_heat_states.over_heat.update(this, context)
    if not context:isOverHeat() then
        context:trigger(this.INPUT_COOLING_HEAT)
        return
    end
    local track = context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK)
    if context:isHolding(track) then
        context:runAnimation("static_over_heating", track, true, LOOP, 0)
    end
end

function over_heat_states.over_heat.transition(this, context, input)
    if input == this.INPUT_COOLING_HEAT then
        context:runAnimation("over_heat_end", context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK), true, PLAY_ONCE_STOP, 0.3)
        return this.over_heat_states.normal
    end
    if input == INPUT_RELOAD then
        -- redémarrage d'urgence : la pose basse de la surchauffe s'efface, l'animation de recharge prend la main
        context:runAnimation("over_heat_end", context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK), true, PLAY_ONCE_STOP, 0.25)
        return this.over_heat_states.recharge
    end
end

function over_heat_states.recharge.entry(this, context)
    this.pz_debut_recharge = context:getCurrentTimestamp()
end

function over_heat_states.recharge.update(this, context)
    if not context:isOverHeat() then
        context:trigger(this.INPUT_COOLING_HEAT)
        return
    end
    -- recharge finie ou annulée alors que l'arme est encore verrouillée : retour à la pose de surchauffe
    if context:getCurrentTimestamp() - this.pz_debut_recharge > 400 and context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)) then
        context:trigger(INPUT_PZ_RETOUR)
    end
end

function over_heat_states.recharge.transition(this, context, input)
    if input == this.INPUT_COOLING_HEAT then
        return this.over_heat_states.normal
    end
    if input == INPUT_PZ_RETOUR then
        return this.over_heat_states.retour
    end
end

function over_heat_states.retour.entry(this, context)
    context:runAnimation("static_over_heating", context:getTrack(BLENDING_TRACK_LINE, OVER_HEATING_TRACK), true, LOOP, 0.3)
end

over_heat_states.retour.update = over_heat_states.over_heat.update
over_heat_states.retour.transition = over_heat_states.over_heat.transition

-- ---------------------------------------------------------------- pas d'inspection pendant la surchauffe
local idle_state = setmetatable({}, { __index = default.main_track_states.idle })

function idle_state.transition(this, context, input)
    if input == INPUT_INSPECT and context:isOverHeat() then
        return nil
    end
    return default.main_track_states.idle.transition(this, context, input)
end

local M = setmetatable({
    main_track_states = setmetatable({ idle = idle_state }, { __index = default.main_track_states }),
    over_heat_states = over_heat_states,
    beam_states = beam_states,
    charge_states = charge_states,
}, { __index = default })

function M:initialize(context)
    default.initialize(self, context)
end

function M:states()
    return {
        self.base_track_state,
        self.over_heat_states.normal,
        self.main_track_states.start,
        self.gun_kick_state,
        self.movement_track_states.idle,
        self.ADS_states.normal,
        self.slide_states.normal,
        self.beam_states.idle,
        self.charge_states.normal
    }
end

return M
