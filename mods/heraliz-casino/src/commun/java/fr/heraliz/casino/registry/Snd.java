package fr.heraliz.casino.registry;

/**
 * Sons du casino (identifiants de sounds.json). Certains sont des sons du mod, d'autres renvoient vers des sons
 * de Minecraft (entrees "event" de sounds.json) : on peut les remplacer par des fichiers .ogg sans toucher au code.
 */
public enum Snd {
    CHIP("chip.place"),
    COINS("coins"),
    WIN_SMALL("win.small"),
    WIN_BIG("win.big"),
    WIN_JACKPOT("win.jackpot"),
    LOSE("lose"),
    LEVER("slot.lever"),
    REEL_START("slot.reel_start"),
    REEL_TICK("slot.reel_tick"),
    REEL_STOP("slot.reel_stop"),
    WHEEL_START("wheel.start"),
    WHEEL_TICK("wheel.tick"),
    WHEEL_STOP("wheel.stop"),
    BELL("bell"),
    COUNTDOWN("countdown"),
    WHOOSH("whoosh"),
    CARD_DEAL("card.deal"),
    CARD_FLIP("card.flip"),
    BALL_ROLL("ball.roll"),
    BALL_CLACK("ball.clack"),
    PEG("plinko.peg"),
    BUCKET("plinko.bucket"),
    DICE_SHAKE("dice.shake"),
    DICE_HIT("dice.hit"),
    ROCKET_LAUNCH("rocket.launch"),
    ROCKET_ENGINE("rocket.engine"),
    EXPLOSION("explosion"),
    GEM("mines.gem"),
    MINE("mines.boom"),
    COIN_FLIP("coin.flip"),
    COIN_LAND("coin.land"),
    GALLOP("race.gallop"),
    RACE_START("race.start"),
    SAFE("cashier.safe"),
    SPARKLE("sparkle"),
    FIREWORK("firework"),
    BUZZ("buzz"),
    CHANT("rps.chant"),
    SMASH("rps.smash"),
    SNIP("rps.snip"),
    RUSTLE("rps.paper"),
    CANNON("naval.fire"),
    SPLASH("naval.splash"),
    NAVAL_HIT("naval.hit"),
    SINK("naval.sink"),
    GLITCH("glitch"),
    ZOMBIE("rotten.zombie"),
    SWITCH("rotten.switch"),
    SCATTER("rotten.scatter"),
    BONUS("rotten.bonus"),
    THUNDER("olympe.thunder"),
    TUMBLE("olympe.tumble"),
    WIND("loup.wind"),
    HOWL("loup.howl"),
    OINK("loup.oink"),
    CRASH("loup.crash"),
    SUNK("naval.sunk"),
    HORN("naval.horn"),
    /** Suspense : battement de coeur, montee de tension, roulement de tambour, retombee (rate de peu). */
    HEARTBEAT("suspense.heartbeat"),
    TENSION("suspense.rise"),
    DRUM("suspense.drum"),
    MISS("suspense.miss"),
    /** Decor : feu du baril, bourdonnement des neons, neige de la tele. */
    FIRE("decor.fire"),
    HUM("decor.hum"),
    STATIC("decor.static"),
    /** La Niche : aboiement, grognement, gemissement, cadenas qui casse, grille qui s'ouvre, wild qui colle. */
    BARK("niche.bark"),
    GROWL("niche.growl"),
    WHINE("niche.whine"),
    LOCK_BREAK("niche.lock"),
    UNLOCK("niche.unlock"),
    STICK("niche.stick"),
    /** Musiques d'ambiance des machines a sous (boucles, lues pendant qu'on joue). */
    MUSIC_ROTTEN("music.rotten"),
    MUSIC_OLYMPE("music.olympe"),
    MUSIC_LOUP("music.loup"),
    MUSIC_NICHE("music.niche");

    public final String id;

    Snd(String id) {
        this.id = id;
    }
}
