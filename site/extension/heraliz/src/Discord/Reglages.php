<?php

namespace Azuriom\Plugin\Heraliz\Discord;

/**
 * Réglages du Discord obligatoire (bêta) : application Discord (connexion « identify »), bot du serveur Discord, salon
 * privé où le staff valide les comptes. Écrits par site/vm/theme.sh depuis les secrets GitHub DISCORD_* dans
 * storage/app/heraliz/discord.json (lisible par le site seul, 600) : jamais dans le dépôt. Fichier absent ou incomplet :
 * le Discord n'est PAS exigé (rien ne bloque avant que le propriétaire ait tout réglé).
 */
final class Reglages
{
    private static ?array $valeurs = null;

    /** Le Discord est exigé : application, bot et serveur Discord réglés. */
    public static function actif(): bool
    {
        $v = self::valeurs();

        return $v['client_id'] !== '' && $v['client_secret'] !== '' && $v['bot_token'] !== '' && $v['guild_id'] !== '';
    }

    /**
     * Validation à la main par le staff (VALIDATION=manuelle de theme.sh, réglage heraliz.validation_manuelle). Sans
     * salon, clé publique ou rôles du staff, personne ne pourrait être accepté : la validation est alors automatique
     * (Discord lié et membre suffisent), et le résumé de l'envoi le signale.
     */
    public static function validationManuelle(): bool
    {
        return self::validationDemandee() && self::validationPossible();
    }

    public static function validationDemandee(): bool
    {
        return setting('heraliz.validation_manuelle', 'oui') !== 'non';
    }

    public static function validationPossible(): bool
    {
        $v = self::valeurs();

        return $v['salon_comptes'] !== '' && $v['public_key'] !== '' && self::rolesStaff() !== [];
    }

    public static function get(string $cle): string
    {
        return self::valeurs()[$cle] ?? '';
    }

    /** Rôles du serveur Discord dont les membres peuvent accepter ou refuser un compte. */
    public static function rolesStaff(): array
    {
        return array_values(array_filter(array_map('trim', explode(',', self::valeurs()['roles_staff'])),
            fn ($r) => preg_match('/^\d{5,25}$/', $r) === 1));
    }

    /** Invitation au serveur Discord montrée aux joueurs qui n'en sont pas membres. */
    public static function invitation(): string
    {
        $lien = self::valeurs()['invitation'];

        return preg_match('#^https://#i', $lien) ? $lien : 'https://discord.gg/heraliz';
    }

    /** Adresse de retour de Discord après la connexion : à déclarer telle quelle dans le portail des développeurs. */
    public static function retour(): string
    {
        return url('/heraliz/discord/retour');
    }

    private static function valeurs(): array
    {
        if (self::$valeurs === null) {
            $fichier = storage_path('app/heraliz/discord.json');
            $lu = is_readable($fichier) ? json_decode((string) file_get_contents($fichier), true) : null;
            $lu = is_array($lu) ? $lu : [];
            self::$valeurs = [];
            foreach (['client_id', 'client_secret', 'bot_token', 'public_key', 'guild_id', 'salon_comptes', 'roles_staff', 'invitation'] as $cle) {
                self::$valeurs[$cle] = is_scalar($lu[$cle] ?? null) ? trim((string) $lu[$cle]) : '';
            }
        }

        return self::$valeurs;
    }
}
