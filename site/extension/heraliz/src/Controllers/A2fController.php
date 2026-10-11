<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\ActionLog;
use Azuriom\Models\User;
use Azuriom\Support\QrCodeRenderer;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;
use PragmaRX\Google2FA\Google2FA;

/**
 * Double authentification (A2F) depuis le launcher, comme la page « Sécurité » du site : un QR code à scanner avec une
 * application (Google Authenticator, Aegis...), puis le code affiché pour l'activer. Le mot de passe est redemandé
 * (le site, lui, le redemande avant cette page).
 */
class A2fController extends Controller
{
    public function preparer(Request $request)
    {
        $user = $this->compte($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }

        if ($user->hasTwoFactorAuth()) {
            return response()->json(['active' => true]);
        }

        $google2fa = new Google2FA();
        $secret = Cache::get($this->cle($user)) ?? $google2fa->generateSecretKey();
        Cache::put($this->cle($user), $secret, now()->addMinutes(15));
        $url = $google2fa->getQRCodeUrl(site_name(), $user->email, $secret);

        return response()->json([
            'active' => false,
            'secret' => $secret,
            'qr' => QrCodeRenderer::render($url, 240),
        ]);
    }

    public function activer(Request $request)
    {
        $this->validate($request, ['code' => ['required', 'string', 'max:20'], 'password' => ['required', 'string']]);

        $user = $this->compte($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }

        if ($user->hasTwoFactorAuth()) {
            return response()->json(['active' => true, 'codes' => null]);
        }

        if (! Hash::check($request->input('password'), $user->password)) {
            return response()->json([
                'status' => 'error',
                'reason' => 'invalid_password',
                'message' => 'Mot de passe incorrect.',
            ], 422);
        }

        $secret = Cache::get($this->cle($user));
        $code = Str::remove(' ', $request->input('code'));

        if ($secret === null || ! (new Google2FA())->verifyKey($secret, $code)) {
            return response()->json([
                'status' => 'error',
                'reason' => 'invalid_2fa',
                'message' => 'Code incorrect : vérifie l’heure de ton téléphone et entre le code affiché maintenant.',
            ], 422);
        }

        $codes = $user->generateRecoveryCodes();
        $user->forceFill(['two_factor_secret' => $secret, 'two_factor_recovery_codes' => $codes])->save();
        Cache::forget($this->cle($user));

        ActionLog::create([
            'user_id' => $user->id,
            'action' => 'users.2fa.enabled',
            'target_id' => null,
            'data' => ['ip' => $request->ip(), 'launcher' => true],
        ]);

        return response()->json(['active' => true, 'codes' => $codes]);
    }

    /**
     * Le compte du jeton de l'API d'authentification, ou la réponse d'erreur à rendre.
     */
    private function compte(Request $request): User|JsonResponse
    {
        if (! setting('auth_api', false)) {
            return response()->json(['status' => 'error', 'message' => 'Auth API is not enabled'], 400);
        }

        $this->validate($request, ['access_token' => ['required', 'string']]);
        $user = User::firstWhere('access_token', $request->input('access_token'));

        if ($user === null) {
            return response()->json(['status' => 'error', 'reason' => 'invalid_token', 'message' => 'Invalid token'], 401);
        }

        if ($user->isBanned()) {
            return response()->json(['status' => 'error', 'reason' => 'user_banned', 'message' => 'User banned'], 403);
        }

        return $user;
    }

    private function cle(User $user): string
    {
        return 'heraliz.a2f.'.$user->id;
    }
}
