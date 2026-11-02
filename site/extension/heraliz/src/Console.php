<?php

namespace Azuriom\Plugin\Heraliz;

use Illuminate\Validation\ValidationException;

/**
 * Commandes que le site peut faire jouer à la console du serveur de jeu (audit du 02/11, point 15). Le site les envoie
 * par AzLink (articles de la boutique, récompenses des votes, ban Discord) et la console du serveur peut tout faire
 * (grades, PB, op…) : un compte admin du site piraté pouvait donc commander le serveur. Seules les formes ci-dessous
 * passent, toujours pour le joueur concerné ({player}) et avec des quantités bornées :
 *  - à l'enregistrement d'un article, d'une récompense de vote ou des commandes communes de la boutique et des votes
 *    (administration) : refusé avec un message clair, rien n'est enregistré ;
 *  - au départ vers le serveur (ServerCommand d'Azuriom, la seule porte vers AzLink) : refusée et signalée au staff.
 * Pour permettre une nouvelle commande : l'ajouter ici (dépôt), jamais depuis l'administration du site.
 */
final class Console
{
    /** Le joueur visé : {player} (ou {name}, qu'AzLink remplace par {player}). */
    private const JOUEUR = '\{(?:player|name)\}';

    private static function permises(): array
    {
        $j = self::JOUEUR;

        return [
            // clés de caisses (votes, boutique) : 99 au plus
            '/^heraliz givekey '.$j.' [a-z0-9_]{1,32} (?:[1-9]\d?|\{quantity\})$/i',
            // hérals (votes) : 10 000 au plus
            '/^eco donner '.$j.' (?:[1-9]\d{0,3}|10000)(?: [\w -]{1,40})?$/iu',
            // Points Boutique (lots vendus sur le site) : 20 000 au plus
            '/^pb donner '.$j.' (?:[1-9]\d{0,3}|1\d{4}|20000)(?: [\w-]{1,32})?$/i',
            // grade de la boutique pour une durée (jours) ou à vie
            '/^grade acheter '.$j.' [a-z0-9_]{1,32} (?:[1-9]\d{0,2}|vie)$/i',
            // emote débloquée
            '/^emote debloquer '.$j.' [a-z0-9_.:-]{1,64}$/i',
            // ban compte + IP + PC de Spartan pour un Discord banni (Discord\Comptes, pseudo réel du compte)
            '/^banpc [A-Za-z0-9_]{3,16} def [^\r\n]{1,150}$/u',
        ];
    }

    public static function permise(mixed $commande): bool
    {
        if (! is_string($commande) || $commande === '' || strlen($commande) > 255) {
            return false;
        }
        foreach (self::permises() as $forme) {
            if (preg_match($forme, trim($commande)) === 1) {
                return true;
            }
        }

        return false;
    }

    /** Les commandes refusées parmi celles d'un article, d'une récompense ou d'un réglage (forme quelconque). */
    public static function refusees(mixed $commandes): array
    {
        return array_values(array_filter(self::textes($commandes), fn (string $c) => trim($c) !== '' && ! self::permise($c)));
    }

    /**
     * Branche les contrôles sur les modèles d'Azuriom et de ses extensions (HeralizServiceProvider::boot). Une classe
     * absente (extension pas installée) est simplement ignorée.
     */
    public static function brancher(): void
    {
        if (class_exists(\Azuriom\Models\ServerCommand::class)) {
            \Azuriom\Models\ServerCommand::creating(function ($commande) {
                if (self::permise($commande->command)) {
                    return null;
                }
                Alerte::staff('Console du serveur de jeu : commande du site **refusée** (pas dans la liste permise, '
                    .'site/extension/heraliz/src/Console.php) : `'.str_replace('`', 'ˋ', mb_substr((string) $commande->command, 0, 200))
                    .'` (compte n° '.($commande->user_id ?? '?').'). Si elle vient d’un article ou d’une récompense que le staff '
                    .'n’a pas créés, un compte admin du site est peut-être piraté.', 'console:'.sha1((string) $commande->command), 3600);

                return false;
            });
        }
        foreach ([\Azuriom\Plugin\Shop\Models\Package::class, \Azuriom\Plugin\Vote\Models\Reward::class] as $modele) {
            if (class_exists($modele)) {
                $modele::saving(fn ($m) => self::exiger($m->commands));
            }
        }
        if (class_exists(\Azuriom\Models\Setting::class)) {
            \Azuriom\Models\Setting::saving(function ($reglage) {
                if (in_array($reglage->name, ['shop.commands', 'vote.commands'], true) && is_string($reglage->value)) {
                    self::exiger(json_decode($reglage->value, true));
                }
            });
        }
    }

    /** Refuse l'enregistrement (message montré dans l'administration) si une commande n'est pas permise. */
    private static function exiger(mixed $commandes): void
    {
        $refusees = self::refusees($commandes);
        if ($refusees === []) {
            return;
        }
        Alerte::staff('Administration du site : commande pour la console du serveur refusée à l’enregistrement : `'
            .str_replace('`', 'ˋ', mb_substr($refusees[0], 0, 200)).'`.', 'console-admin:'.sha1($refusees[0]), 3600);

        throw ValidationException::withMessages([
            'commands' => 'Commande refusée par HeraliZ : « '.mb_substr($refusees[0], 0, 120).' ». Seules quelques commandes '
                .'sont permises depuis le site (clés, hérals, PB, grades, emotes, pour {player}) : demander l’ajout dans le dépôt '
                .'(site/extension/heraliz/src/Console.php).',
        ]);
    }

    /** Textes des commandes, quelle que soit leur forme (liste de textes, [{command|commands, trigger…}]…). */
    private static function textes(mixed $valeur): array
    {
        if (is_string($valeur)) {
            return [$valeur];
        }
        if (! is_array($valeur)) {
            return [];
        }
        if (array_is_list($valeur)) {
            return array_merge([], ...array_map(fn ($v) => self::textes($v), $valeur));
        }

        return array_merge(self::textes($valeur['command'] ?? null), self::textes($valeur['commands'] ?? null));
    }
}
