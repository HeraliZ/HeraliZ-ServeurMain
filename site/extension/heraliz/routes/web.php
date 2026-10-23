<?php

use Azuriom\Plugin\Heraliz\Controllers\DiscordController;
use Illuminate\Support\Facades\Route;

// Pages du lien Discord, sous /heraliz (préfixe posé par RouteServiceProvider). « lier » et « verifier » marchent avec
// le jeton donné au launcher (compte/etat) ou avec le compte connecté au site ; « retour » est l'adresse de retour
// déclarée dans le portail des développeurs Discord (https://heraliz.fr/heraliz/discord/retour).
Route::get('/discord', [DiscordController::class, 'index'])->middleware('auth')->name('discord.index');
Route::get('/discord/lier', [DiscordController::class, 'lier'])->middleware('throttle:heraliz-compte')->name('discord.lier');
Route::get('/discord/verifier', [DiscordController::class, 'verifier'])->middleware('throttle:heraliz-compte')->name('discord.verifier');
Route::get('/discord/retour', [DiscordController::class, 'retour'])->middleware('throttle:heraliz-compte')->name('discord.retour');
