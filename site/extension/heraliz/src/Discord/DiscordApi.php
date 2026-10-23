<?php

namespace Azuriom\Plugin\Heraliz\Discord;

use Illuminate\Http\Client\PendingRequest;
use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Appels à l'API de Discord : connexion du joueur (OAuth2, « identify » : identifiant, pseudo) et bot du serveur
 * Discord (membre ? banni ? messages du salon de validation). Une panne, un refus ou une limite de Discord rend null :
 * l'appelant garde alors le dernier état connu.
 */
final class DiscordApi
{
    private const BASE = 'https://discord.com/api/v10';

    /** Adresse de la page de connexion Discord où le joueur autorise l'application (scope identify). */
    public static function autorisation(string $etat): string
    {
        return 'https://discord.com/oauth2/authorize?'.http_build_query([
            'client_id' => Reglages::get('client_id'),
            'response_type' => 'code',
            'scope' => 'identify',
            'redirect_uri' => Reglages::retour(),
            'state' => $etat,
        ], '', '&', PHP_QUERY_RFC3986);
    }

    /** Le Discord du joueur ({id, username, global_name...}) à partir du code rendu par Discord, ou null. */
    public static function utilisateur(string $code): ?array
    {
        $jeton = self::essai(fn () => Http::asForm()->timeout(10)->acceptJson()->post(self::BASE.'/oauth2/token', [
            'client_id' => Reglages::get('client_id'),
            'client_secret' => Reglages::get('client_secret'),
            'grant_type' => 'authorization_code',
            'code' => $code,
            'redirect_uri' => Reglages::retour(),
        ]));
        $acces = $jeton?->successful() ? $jeton->json('access_token') : null;
        if (! is_string($acces) || $acces === '') {
            return null;
        }

        $moi = self::essai(fn () => Http::withToken($acces)->timeout(10)->acceptJson()->get(self::BASE.'/users/@me'));
        $donnees = $moi?->successful() ? $moi->json() : null;

        return is_array($donnees) && preg_match('/^\d{5,25}$/', (string) ($donnees['id'] ?? '')) ? $donnees : null;
    }

    /** Membre du serveur Discord : true, false (inconnu du serveur), null (Discord injoignable ou bot mal réglé). */
    public static function membre(string $discordId): ?bool
    {
        $r = self::essai(fn () => self::bot()->get(self::BASE.'/guilds/'.Reglages::get('guild_id').'/members/'.$discordId));

        // 404 seulement avec le code « Unknown Member » (10007) : un 404 « Unknown Guild » (10004, mauvais serveur,
        // bot retiré) ou un 403 (permissions) est un bot mal réglé, jamais « pas membre » pour tout le monde
        return match (true) {
            $r?->status() === 200 => true,
            $r?->status() === 404 && self::code($r) === 10007 => false,
            default => self::panne('membre', $r),
        };
    }

    /** Banni du serveur Discord (le bot doit avoir la permission « Bannir des membres ») : true, false ou null. */
    public static function banni(string $discordId): ?bool
    {
        $r = self::essai(fn () => self::bot()->get(self::BASE.'/guilds/'.Reglages::get('guild_id').'/bans/'.$discordId));

        // 404 « Unknown Ban » (10026) = pas banni ; 403 (bot sans « Bannir des membres ») ou « Unknown Guild » : panne
        return match (true) {
            $r?->status() === 200 => true,
            $r?->status() === 404 && self::code($r) === 10026 => false,
            default => self::panne('ban', $r),
        };
    }

    /**
     * Contrôle de la configuration du bot (étape « theme ») : null si tout va bien, sinon ce qui manque. Lit le serveur
     * Discord (bot présent ?) et la liste des bans (permission « Bannir des membres »).
     */
    public static function controleBot(): ?string
    {
        $guilde = self::essai(fn () => self::bot()->get(self::BASE.'/guilds/'.Reglages::get('guild_id')));
        if ($guilde?->status() !== 200) {
            return 'serveur Discord illisible (DISCORD_GUILD_ID faux, bot absent du serveur ou jeton faux ; code '.($guilde?->status() ?? 'aucun').')';
        }
        $bans = self::essai(fn () => self::bot()->get(self::BASE.'/guilds/'.Reglages::get('guild_id').'/bans', ['limit' => 1]));
        if ($bans?->status() !== 200) {
            return 'le bot n’a pas la permission « Bannir des membres » (bans illisibles : un Discord banni ne serait pas vu ; code '.($bans?->status() ?? 'aucun').')';
        }

        return null;
    }

    /** Envoie un message dans un salon par le bot ; rend l'identifiant du message, ou null. */
    public static function message(string $salon, array $corps): ?string
    {
        $corps['allowed_mentions'] = ['parse' => []];
        $r = self::essai(fn () => self::bot()->post(self::BASE.'/channels/'.$salon.'/messages', $corps));
        $id = $r?->successful() ? $r->json('id') : null;
        if ($id === null) {
            self::panne('message', $r);
        }

        return is_string($id) ? $id : null;
    }

    /** Date de création d'un compte Discord, lue dans son identifiant (« snowflake »), en secondes. */
    public static function creeLe(string $discordId): int
    {
        return intdiv(((int) $discordId >> 22) + 1420070400000, 1000);
    }

    private static function bot(): PendingRequest
    {
        return Http::withHeaders([
            'Authorization' => 'Bot '.Reglages::get('bot_token'),
            'User-Agent' => 'DiscordBot (https://heraliz.fr, 1.0)',
        ])->timeout(8)->acceptJson();
    }

    private static function essai(callable $appel): ?Response
    {
        try {
            return $appel();
        } catch (Throwable $e) {
            Log::warning('HeraliZ Discord : appel impossible ('.class_basename($e).')');

            return null;
        }
    }

    /** Code d'erreur JSON de Discord (10007, 10026…), ou 0. */
    private static function code(Response $r): int
    {
        $code = $r->json('code');

        return is_int($code) ? $code : 0;
    }

    private static function panne(string $quoi, ?Response $r): null
    {
        // jamais le contenu de la réponse (il pourrait citer un jeton) : le code seulement
        Log::warning('HeraliZ Discord : réponse inattendue ('.$quoi.', code '.($r?->status() ?? 'aucun').')');

        return null;
    }
}
