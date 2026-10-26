package fr.heralisz.tactical.api.melee;

public enum AttackResult {
   HIT(true, false, false),
   CRIT(true, true, false),
   KILL(true, false, true),
   CRIT_KILL(true, true, true),
   MISS(false, false, false);

   final boolean hit;
   final boolean crit;
   final boolean kill;

   AttackResult(boolean hit, boolean crit, boolean kill) {
      this.hit = hit;
      this.crit = crit;
      this.kill = kill;
   }

   public boolean hit() {
      return this.hit;
   }

   public boolean crit() {
      return this.crit;
   }

   public boolean kill() {
      return this.kill;
   }
}
