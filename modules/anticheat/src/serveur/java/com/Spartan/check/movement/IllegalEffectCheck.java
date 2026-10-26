package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.config.SpartanConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Détection d'effets illégaux (« se donne des effets »), côté serveur.
 *
 * Les effets sont gérés par le serveur : un client de triche ne peut pas se
 * donner un VRAI effet. S'il en a un, il l'a obtenu par une commande, une
 * faille d'un mod ou un exploit — c'est donc grave et à signaler.
 *
 * Seuls les effets BÉNÉFIQUES sont examinés (un malus infligé par un mob ou
 * une zone n'avantage personne).
 * Signale : un niveau d'effet supérieur au maximum configuré, ou un effet
 * infini / anormalement long. Les membres du staff (op 2+) sont ignorés, ainsi
 * que les effets listés dans ignoredEffects (effets longs normaux du modpack).
 * Vérifié une fois par seconde, et chaque effet n'est signalé qu'une fois.
 * Correction : l'effet illégal est retiré immédiatement (removeIllegalEffects).
 */
public class IllegalEffectCheck extends MovementCheck {
    private static final int INTERVAL_TICKS = 20;

    private final Map<UUID, Set<String>> reported = new ConcurrentHashMap<>();

    public IllegalEffectCheck() {
        super("IllegalEffect", "effect");
    }

    @Override
    public void check(ServerPlayer p) {
        if (!SpartanConfig.CHECK_EFFECTS.get() || p.tickCount % INTERVAL_TICKS != 0 || p.hasPermissions(2)) {
            return;
        }
        int maxAmplifier = SpartanConfig.EFFECT_MAX_LEVEL.get() - 1;
        int maxTicks = SpartanConfig.EFFECT_MAX_MINUTES.get() * 60 * 20;
        List<String> ignored = SpartanConfig.getIgnoredEffects();

        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (!e.getEffect().isBeneficial()) {
                continue; // malus (mob, zone, piège) : n'avantage pas le joueur
            }
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect());
            String id = key != null ? key.toString() : "?";
            if (ignored.contains(id)) {
                continue;
            }
            String problem = null;
            String kind = null;
            if (e.getAmplifier() > maxAmplifier) {
                problem = "niveau " + (e.getAmplifier() + 1);
                kind = "niveau";
            } else if (e.isInfiniteDuration()) {
                problem = "durée infinie";
                kind = "durée";
            } else if (e.getDuration() > maxTicks) {
                problem = "durée " + (e.getDuration() / 1200) + " min";
                kind = "durée";
            }
            if (problem == null) {
                continue;
            }
            // Seul un NIVEAU impossible est retiré. Une durée longue ou infinie peut être
            // normale depuis la 1.19.4 (quêtes, objets de mods) : alerte seulement.
            boolean remove = "niveau".equals(kind) && SpartanConfig.MITIGATE_EFFECTS.get();
            if (remove) {
                toRemove.add(e.getEffect());
            }
            String tag = id + ":" + kind; // stable : pas de ré-alerte à chaque minute
            if (reported.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(tag)) {
                flag(p, "effet " + id + " (" + problem + ")" + (remove ? " — retiré" : ""));
            }
        }
        for (MobEffect effect : toRemove) {
            p.removeEffect(effect);
        }
    }

    @Override
    public void onGrace(UUID uuid) {
        // Un tp ne change pas les effets : on garde la mémoire des effets déjà signalés.
    }

    @Override
    public void clear(UUID uuid) {
        reported.remove(uuid);
    }
}
