<?php

namespace Azuriom\Plugin\Heraliz;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Schema;
use Throwable;

/**
 * Adresses IP et temps de jeu effacés au bout d'un an (audit du 02/11, point 41), comme le promet la page
 * Confidentialité (« journaux de connexion : 1 an ») : tâche planifiée une fois par jour (HeralizServiceProvider).
 *  - heraliz_ips_comptes : adresses vues pour chaque compte, pas revues depuis un an ;
 *  - heraliz_votes : votes (avec leur adresse) de plus d'un an ;
 *  - heraliz_temps_jeu : temps de jeu des comptes qui n'ont pas joué depuis un an ;
 *  - heraliz_pb_adresses : adresses qui se sont servies de la clé des PB, pas revues depuis un an ;
 *  - users.last_login_ip (Azuriom) : vidée pour les comptes qui ne se sont pas connectés depuis un an.
 * Gardées : les adresses bloquées par un ban (heraliz_ips_bannies : sanctions, 3 à 5 ans selon la page Confidentialité)
 * et le journal des PB (achats : 10 ans).
 */
final class Purge
{
    public const JOURS = 365;

    public static function balayer(): void
    {
        $limite = now()->subDays(self::JOURS);
        $effaces = [];
        foreach ([['heraliz_ips_comptes', 'vu_le'], ['heraliz_votes', 'cree_le'], ['heraliz_temps_jeu', 'vu_le'],
                     ['heraliz_pb_adresses', 'derniere_le']] as [$table, $colonne]) {
            try {
                if (Schema::hasTable($table)) {
                    $effaces[$table] = DB::table($table)->where($colonne, '<', $limite)->delete();
                }
            } catch (Throwable $e) {
                Log::warning('HeraliZ purge : '.$table.' pas nettoyée ('.class_basename($e).')');
            }
        }
        try {
            if (Schema::hasColumn('users', 'last_login_ip') && Schema::hasColumn('users', 'last_login_at')) {
                $effaces['users.last_login_ip'] = DB::table('users')->whereNotNull('last_login_ip')
                    ->where(fn ($q) => $q->where('last_login_at', '<', $limite)->orWhereNull('last_login_at'))
                    ->where('created_at', '<', $limite)
                    ->update(['last_login_ip' => null]);
            }
        } catch (Throwable $e) {
            Log::warning('HeraliZ purge : users.last_login_ip pas nettoyée ('.class_basename($e).')');
        }
        $total = array_sum($effaces);
        if ($total > 0) {
            Log::info('HeraliZ purge (plus d’un an) : '.collect($effaces)->filter()->map(fn ($n, $t) => $t.' '.$n)->implode(', '));
        }
    }
}
