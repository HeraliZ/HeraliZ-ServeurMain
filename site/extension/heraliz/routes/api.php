<?php

use Azuriom\Plugin\Heraliz\Controllers\A2fController;
use Azuriom\Plugin\Heraliz\Controllers\InscriptionController;
use Azuriom\Plugin\Heraliz\Controllers\JetonController;
use Azuriom\Plugin\Heraliz\Controllers\PackController;
use Azuriom\Plugin\Heraliz\Controllers\PbController;
use Illuminate\Support\Facades\Route;

// API des comptes HeraliZ pour le launcher, sous /api/heraliz (préfixe posé par RouteServiceProvider).
Route::get('/etat', [InscriptionController::class, 'etat'])->name('etat');
Route::post('/inscription', [InscriptionController::class, 'inscrire'])->middleware('throttle:heraliz-inscription')->name('inscription');
Route::post('/a2f/preparer', [A2fController::class, 'preparer'])->middleware('throttle:heraliz-a2f')->name('a2f.preparer');
Route::post('/a2f/activer', [A2fController::class, 'activer'])->middleware('throttle:heraliz-a2f')->name('a2f.activer');
Route::post('/jeton-jeu', [JetonController::class, 'creer'])->middleware('throttle:heraliz-jeton')->name('jeton');
Route::post('/pack', [PackController::class, 'distribution'])->middleware('throttle:heraliz-pack')->name('pack');

// Points Boutique pour le serveur de jeu (requêtes signées avec pb.cle, PbController)
Route::prefix('pb')->name('pb.')->middleware('throttle:heraliz-pb')->group(function () {
    foreach (['solde', 'debiter', 'crediter', 'transfert', 'statut', 'stats'] as $action) {
        Route::post('/'.$action, [PbController::class, $action])->name($action);
    }
});
