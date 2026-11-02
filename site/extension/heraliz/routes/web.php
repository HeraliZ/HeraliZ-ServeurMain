<?php

use Azuriom\Plugin\Heraliz\Controllers\ClicVoteController;
use Azuriom\Plugin\Heraliz\Controllers\DiscordController;
use Azuriom\Plugin\Heraliz\Controllers\RobotController;
use Illuminate\Support\Facades\Route;

// Pages du lien Discord, sous /heraliz (préfixe posé par RouteServiceProvider). « lier » et « verifier » marchent avec
// le jeton donné au launcher (compte/etat) ou avec le compte connecté au site ; « retour » est l'adresse de retour
// déclarée dans le portail des développeurs Discord (https://heraliz.fr/heraliz/discord/retour).
Route::get('/discord', [DiscordController::class, 'index'])->middleware('auth')->name('discord.index');
Route::get('/discord/lier', [DiscordController::class, 'lier'])->middleware('throttle:heraliz-compte')->name('discord.lier');
Route::get('/discord/verifier', [DiscordController::class, 'verifier'])->middleware('throttle:heraliz-compte')->name('discord.verifier');
Route::get('/discord/retour', [DiscordController::class, 'retour'])->middleware('throttle:heraliz-compte')->name('discord.retour');

// Clic sur « Voter » de la page de vote (anti-robot, Votes\Clic), envoyé par la page avant d'ouvrir le site de vote
Route::post('/vote/{site}/clic', [ClicVoteController::class, 'clic'])->middleware(['auth', 'throttle:heraliz-vote-clic'])->name('vote.clic');

// Anti-robot de la création de compte depuis le launcher (Turnstile du site) : page ouverte par le launcher dans une
// petite fenêtre, puis page de fin interceptée par le launcher (RobotController)
Route::get('/robot', [RobotController::class, 'page'])->middleware('throttle:heraliz-compte')->name('robot');
Route::get('/robot/fait', [RobotController::class, 'fait'])->middleware('throttle:heraliz-compte')->name('robot.fait');
