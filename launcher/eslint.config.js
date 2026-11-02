// Relecture du code du launcher (npm run lint), ESLint 9 : mêmes règles qu'avant (ancien .eslintrc.json d'ESLint 8,
// qui n'est plus suivi). Outil de développement seulement : rien de ceci n'est emballé dans le launcher.
const js = require('@eslint/js')
const globals = require('globals')

module.exports = [
    { ignores: ['dist/**', 'node_modules/**'] },
    js.configs.recommended,
    {
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'commonjs',
            globals: { ...globals.node, ...globals.es2021 }
        },
        rules: {
            indent: ['error', 4, { SwitchCase: 1 }],
            'linebreak-style': 'off',
            quotes: ['error', 'single'],
            semi: ['error', 'never'],
            'no-var': 'error',
            'no-console': 'off',
            'no-control-regex': 'off',
            'no-unused-vars': ['error', { vars: 'all', args: 'none', caughtErrors: 'none', ignoreRestSiblings: false, argsIgnorePattern: 'reject' }],
            'no-async-promise-executor': 'off'
        }
    },
    // scripts en modules ES (ci/signer.mjs)
    { files: ['**/*.mjs'], languageOptions: { sourceType: 'module' } }
]
