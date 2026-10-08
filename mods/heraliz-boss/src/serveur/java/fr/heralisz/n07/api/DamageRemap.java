package fr.heralisz.n07.api;

import net.minecraft.world.entity.player.Player;

/**
 * Remappage des degats du boss sur les joueurs (combat pilote). attack : id stable de l'attaque (voir N07Api.ATTACKS) ;
 * base : valeur de reference du boss (avant multiplicateurs) ; current : valeur que le boss infligerait (multiplicateur
 * du combat, +25 % en phase 3) ; rend les degats a infliger (0 = rien, mais les effets d'etat s'appliquent quand meme).
 */
@FunctionalInterface
public interface DamageRemap {
   float remap(Player player, String attack, float base, float current, int phase);
}
