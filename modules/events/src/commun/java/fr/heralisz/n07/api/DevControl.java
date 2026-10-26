package fr.heralisz.n07.api;

/** Commandes d'admin /boss communes à tous les boss (tests, réglages). */
public interface DevControl {
   /** Déclenche l'attaque nommée (nom de l'action, insensible à la casse). @return false si inconnue */
   boolean devAction(String name);

   void devPhase(int n);

   void setDevPassive(boolean on);
}
