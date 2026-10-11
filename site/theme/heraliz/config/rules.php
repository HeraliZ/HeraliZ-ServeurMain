<?php

// Règles des réglages du thème HeraliZ (administration → Thèmes → Configurer).
return [
    'discord' => ['nullable', 'url', 'max:200'],
    'launcher' => ['nullable', 'url', 'max:300'],
    'saison' => ['nullable', 'string', 'max:40'],
    'version' => ['nullable', 'string', 'max:40'],
    'saison_debut' => ['nullable', 'date'],
    'places' => ['nullable', 'integer', 'min:1', 'max:10000'],
];
