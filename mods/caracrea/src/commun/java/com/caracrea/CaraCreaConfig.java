package com.caracrea;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Server rules (who may edit, when the creator opens, the voice chat) and client comfort options. */
public final class CaraCreaConfig {
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private CaraCreaConfig() {
    }

    /** {@code voix.actif}: AUTO turns our voice chat on by itself when Simple Voice Chat is absent. */
    public enum VoiceMode { AUTO, OUI, NON }

    /** Who hears spectators (outside matches). */
    public enum SpectatorVoice { MUETS, ENTRE_EUX, TOUS }

    /** How the client sends and receives voices. */
    public enum Transport { AUTO, TCP }

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue freeEdit;
        public final ForgeConfigSpec.BooleanValue openOnFirstJoin;
        public final ForgeConfigSpec.IntValue editCooldownSeconds;

        public final ForgeConfigSpec.EnumValue<VoiceMode> voiceMode;
        public final ForgeConfigSpec.DoubleValue voiceRange;
        public final ForgeConfigSpec.DoubleValue whisperRange;
        public final ForgeConfigSpec.DoubleValue shoutRange;
        public final ForgeConfigSpec.DoubleValue crouchMultiplier;
        public final ForgeConfigSpec.BooleanValue allowGroups;
        public final ForgeConfigSpec.BooleanValue allowRecording;
        public final ForgeConfigSpec.EnumValue<SpectatorVoice> spectators;
        public final ForgeConfigSpec.BooleanValue downedMuffled;
        public final ForgeConfigSpec.IntValue bitrate;
        public final ForgeConfigSpec.BooleanValue udp;
        public final ForgeConfigSpec.IntValue udpPort;
        public final ForgeConfigSpec.ConfigValue<String> bindAddress;
        public final ForgeConfigSpec.ConfigValue<String> publicAddress;
        public final ForgeConfigSpec.BooleanValue isolateMatches;
        public final ForgeConfigSpec.BooleanValue matchTeamOnly;
        public final ForgeConfigSpec.BooleanValue matchSpectatorsTogether;

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Character creator rules").push("creator");
            freeEdit = builder
                    .comment("Players may reopen the creator themselves (/caracrea or /perso) and change their look whenever they want.",
                            "When false, only operators can open it for them (/caracrea open <player>) and the first-join creator.")
                    .define("freeEdit", true);
            openOnFirstJoin = builder
                    .comment("Open the creator automatically the first time a player joins.")
                    .define("openOnFirstJoin", true);
            editCooldownSeconds = builder
                    .comment("Minimum delay between two saves by the same player, in seconds (0 = none).")
                    .defineInRange("editCooldownSeconds", 0, 0, 86400);
            builder.pop();

            builder.comment("Chat vocal de proximite integre (CaraCrea 1.1). Voir docs/specs/VOIX_SPEC.md.").push("voix");
            voiceMode = builder
                    .comment("AUTO : le chat vocal s'allume tout seul si Simple Voice Chat n'est pas installe (sinon il reste eteint et",
                            "c'est SVC qui transporte les voix). OUI : allume meme avec SVC (deconseille). NON : eteint.")
                    .defineEnum("actif", VoiceMode.AUTO);
            voiceRange = builder
                    .comment("Distance, en blocs, a laquelle on entend encore quelqu'un (de plus en plus bas avec la distance).")
                    .defineInRange("portee", 48.0, 4.0, 256.0);
            whisperRange = builder
                    .comment("Portee d'un chuchotement, en blocs.")
                    .defineInRange("porteeChuchotement", 24.0, 1.0, 128.0);
            // Mode « crier » (touche « changer de mode ») : entendu plus loin que la voix normale.
            shoutRange = builder
                    .comment("Portee d'un cri, en blocs (mode crier : entendu plus loin que la voix normale ; 96 = deux fois la",
                            "portee normale de 48).")
                    .defineInRange("porteeCri", 96.0, 4.0, 512.0);
            crouchMultiplier = builder
                    .comment("Portee de la voix accroupi, en part de la portee normale (1 = pas de changement, 0.5 = moitie).")
                    .defineInRange("accroupi", 1.0, 0.0, 1.0);
            allowGroups = builder
                    .comment("Les joueurs peuvent creer et rejoindre des groupes vocaux (parler au groupe ou qu'il soit).")
                    .define("groupes", true);
            allowRecording = builder
                    .comment("Les joueurs peuvent enregistrer sur leur ordinateur ce qu'ils entendent (fichiers WAV).")
                    .define("enregistrement", false);
            spectators = builder
                    .comment("Spectateurs (hors match) : MUETS (ne parlent pas, entendent tout), ENTRE_EUX (ne sont entendus que par les",
                            "autres spectateurs), TOUS (comme les autres joueurs).")
                    .defineEnum("spectateurs", SpectatorVoice.ENTRE_EUX);
            downedMuffled = builder
                    .comment("Un joueur a terre (Reanimation) peut parler, avec une voix etouffee.")
                    .define("aTerreEtouffe", true);
            bitrate = builder
                    .comment("Debit de la voix (Opus), en bits par seconde : 16000 economise la bande passante, 32000 est plus clair.")
                    .defineInRange("debit", 24000, 12000, 64000);

            builder.comment("Reseau de la voix").push("reseau");
            udp = builder
                    .comment("Canal UDP dedie (comme Simple Voice Chat) : la voix ne passe plus par la connexion du jeu et ne saccade pas",
                            "quand celle-ci rame. Si un joueur ne peut pas l'atteindre, il passe tout seul par la connexion du jeu.")
                    .define("udp", true);
            udpPort = builder
                    .comment("Port UDP de la voix, a ouvrir chez l'hebergeur (UDP). 24454 = celui de Simple Voice Chat.",
                            "-1 = le meme numero que le port du jeu (deconseille si enable-query=true).")
                    .defineInRange("port", 24454, -1, 65535);
            bindAddress = builder
                    .comment("Adresse d'ecoute du port UDP (vide = toutes les adresses de la machine).")
                    .define("adresseEcoute", "");
            publicAddress = builder
                    .comment("Adresse que les joueurs utilisent pour la voix (vide = celle avec laquelle ils se connectent au jeu).",
                            "A remplir seulement si la voix passe par une autre adresse (ex. proxy). Exemple : voix.monserveur.fr")
                    .define("adressePublique", "");
            builder.pop();

            builder.comment("Matchs HeraliZ (heralisz_matchs)").push("matchs");
            isolateMatches = builder
                    .comment("En match, on n'entend que les joueurs du meme match (et les groupes vocaux sont suspendus).")
                    .define("isoler", true);
            matchTeamOnly = builder
                    .comment("En match, on n'entend que son equipe.")
                    .define("equipeSeulement", false);
            matchSpectatorsTogether = builder
                    .comment("Les spectateurs d'un match ne s'entendent qu'entre eux, partout dans le match ; les joueurs ne les entendent pas.")
                    .define("spectateursEntreEux", true);
            builder.pop();
            builder.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue blink;
        public final ForgeConfigSpec.BooleanValue voiceMouth;
        public final ForgeConfigSpec.IntValue animationDistance;
        public final ForgeConfigSpec.DoubleValue voiceSensitivity;
        public final ForgeConfigSpec.BooleanValue detailedBody;
        public final ForgeConfigSpec.BooleanValue hideHairUnderHelmet;
        public final ForgeConfigSpec.BooleanValue voiceChat;
        public final ForgeConfigSpec.BooleanValue pushToTalk;
        public final ForgeConfigSpec.DoubleValue activationThreshold;
        public final ForgeConfigSpec.ConfigValue<String> microphone;
        public final ForgeConfigSpec.DoubleValue micGain;
        public final ForgeConfigSpec.DoubleValue voiceVolume;
        public final ForgeConfigSpec.BooleanValue muted;
        public final ForgeConfigSpec.BooleanValue deafened;
        public final ForgeConfigSpec.BooleanValue denoise;
        public final ForgeConfigSpec.IntValue denoiseStrength;
        public final ForgeConfigSpec.BooleanValue automaticGain;
        public final ForgeConfigSpec.BooleanValue automaticActivation;
        public final ForgeConfigSpec.ConfigValue<String> speaker;
        public final ForgeConfigSpec.IntValue jitterBufferMs;
        public final ForgeConfigSpec.BooleanValue occlusion;
        public final ForgeConfigSpec.EnumValue<Transport> transport;
        public final ForgeConfigSpec.BooleanValue icons;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Appearance").push("model");
            detailedBody = builder
                    .comment("Draw characters with the 3D body (shaped torso, hands, feet, hair and clothes in volume).",
                            "When false, characters use the classic cubic Minecraft body with the same painted skin.")
                    .define("detailedBody", true);
            hideHairUnderHelmet = builder
                    .comment("Hide the 3D hair when a helmet is worn (otherwise it can poke through).")
                    .define("hideHairUnderHelmet", true);
            builder.pop();

            builder.comment("Face animation").push("animation");
            blink = builder.comment("Characters blink.").define("blink", true);
            voiceMouth = builder.comment("Mouths take the shape of the words spoken in the voice chat.").define("voiceMouth", true);
            animationDistance = builder
                    .comment("Faces further than this many blocks from the camera are not animated.")
                    .defineInRange("animationDistance", 24, 4, 128);
            voiceSensitivity = builder
                    .comment("How wide the mouth opens for a given voice volume (1 = normal).")
                    .defineInRange("voiceSensitivity", 1.0, 0.2, 5.0);
            builder.pop();

            builder.comment("Built-in proximity voice chat (also in game: /voix, or the voice menu key then Settings)").push("voice");
            voiceChat = builder
                    .comment("Talk to and hear the players around (when the server has the voice chat on).")
                    .define("enabled", true);
            pushToTalk = builder
                    .comment("true: talk while holding the push-to-talk key. false: the microphone sends as soon as you speak.")
                    .define("pushToTalk", true);
            activationThreshold = builder
                    .comment("Voice activation: how loud you must speak for the microphone to send (0 to 1).")
                    .defineInRange("activationThreshold", 0.35, 0.0, 1.0);
            microphone = builder
                    .comment("Name of the microphone to use; empty for the system's default.")
                    .define("microphone", "");
            micGain = builder
                    .comment("Microphone amplification (1 = unchanged).")
                    .defineInRange("micGain", 1.0, 0.0, 4.0);
            voiceVolume = builder
                    .comment("Volume of the other players' voices (also follows the game's master and voice volumes).")
                    .defineInRange("volume", 1.0, 0.0, 2.0);
            muted = builder
                    .comment("Microphone muted (key M).")
                    .define("muted", false);
            // « Couper tout le monde » : ne coupe que ce que le joueur entend, pas son micro.
            deafened = builder
                    .comment("Everyone muted: you hear no voice at all, the others still hear you (key \"mute everyone\").")
                    .define("deafened", false);
            denoise = builder
                    .comment("Remove the steady background noise (fan, computer, street) from your microphone.")
                    .define("denoise", true);
            denoiseStrength = builder
                    .comment("Strength of the noise suppression: 1 light, 2 normal, 3 strong.")
                    .defineInRange("denoiseStrength", 2, 1, 3);
            automaticGain = builder
                    .comment("Bring your voice to a steady level whatever the microphone's volume.")
                    .define("automaticGain", true);
            automaticActivation = builder
                    .comment("Voice activation: detect your voice by itself instead of using the threshold.")
                    .define("automaticActivation", true);
            speaker = builder
                    .comment("Name of the sound output for the voices; empty for the system's default.")
                    .define("speaker", "");
            jitterBufferMs = builder
                    .comment("Jitter buffer: 0 = automatic (adapts to your connection), otherwise a fixed delay in milliseconds (40 to 200).")
                    .defineInRange("jitterBufferMs", 0, 0, 200);
            occlusion = builder
                    .comment("Walls muffle the voices behind them.")
                    .define("occlusion", false);
            transport = builder
                    .comment("AUTO: dedicated UDP channel, the game connection when UDP is blocked. TCP: always the game connection.")
                    .defineEnum("transport", Transport.AUTO);
            icons = builder
                    .comment("Show the voice icons above heads and in the player list.")
                    .define("icons", true);
            builder.pop();
        }
    }
}
