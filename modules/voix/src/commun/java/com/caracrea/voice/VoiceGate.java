package com.caracrea.voice;

/**
 * When the built-in voice chat may touch audio devices, decided in one place (no Minecraft, tested by
 * {@code VoiceNetCheck}):
 * <ul>
 *   <li>the server runs it only in {@code voix.actif = OUI}, or in {@code AUTO} without Simple Voice Chat;</li>
 *   <li>a client opens a microphone or a sound output only if the server runs it, the player has it on, Simple Voice
 *   Chat is not connected on this client (it then owns the microphone and the speakers), and this is not a silent
 *   test run;</li>
 *   <li>the microphone test (hearing yourself) stops by itself after 30 seconds.</li>
 * </ul>
 */
public final class VoiceGate {
    /** Longest microphone test (loopback) before it stops by itself. */
    public static final long TEST_LIMIT_NS = 30_000_000_000L;
    /**
     * Test runs (-Dcaracrea.voice.headless=true, set by the test bench): never open a real microphone nor a real sound
     * output; everything else runs, and is measured in code.
     */
    public static final boolean HEADLESS = Boolean.getBoolean("caracrea.voice.headless");
    /** Simple Voice Chat says, through its API, that it is connected on this client. */
    public static volatile boolean svcConnected;

    private VoiceGate() {
    }

    /** Server: does our voice chat run? {@code mode}: AUTO, OUI or NON ({@code voix.actif}). */
    public static boolean serverRuns(String mode, boolean svcInstalled) {
        return switch (mode) {
            case "OUI" -> true;
            case "NON" -> false;
            default -> !svcInstalled;
        };
    }

    /** Client: may the voice chat open its microphone and sound output? */
    public static boolean devicesAllowed(boolean serverRunsOurs, boolean playerEnabled, boolean svcActive) {
        return serverRunsOurs && playerEnabled && !svcActive;
    }

    /** Whether the device to open is a real one (false in silent test runs). */
    public static boolean realDevices() {
        return !HEADLESS;
    }

    /** Whether a microphone test started at {@code started} must stop now. */
    public static boolean testExpired(long started, long now) {
        return started != 0 && now - started >= TEST_LIMIT_NS;
    }
}
