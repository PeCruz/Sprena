import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    languageOptions: {
      parserOptions: { project: './tsconfig.json' },
    },
    rules: {
      // A trilha de auditoria e os passos da exclusão são logados de propósito.
      'no-console': 'off',
    },
  },
  { ignores: ['lib/**', 'node_modules/**', 'test/**'] },
);
