// ESLint flat config — 02 문서 6.2절 규칙 + 03 문서 4.2절 하네스 보강 규칙
// 규칙을 약하게 바꾸려면 PR에서 이유를 적고, src/__harness__/eslint-rules.test.js도 함께 바꿔야 CI가 통과한다.
import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import noUnsanitized from 'eslint-plugin-no-unsanitized';
import prettier from 'eslint-config-prettier';

const restrictedGlobals = [
  { name: 'fetch', message: 'API 호출은 src/api/ 모듈만 사용한다 (CSRF 헤더 누락 방지, SEC-05).' },
  { name: 'localStorage', message: '브라우저 저장소에 토큰·개인정보를 두지 않는다 (SEC-05).' },
  { name: 'sessionStorage', message: '브라우저 저장소에 토큰·개인정보를 두지 않는다 (SEC-05).' },
];

const focusedOrSkippedTests = ['describe', 'it', 'test'].flatMap((object) =>
  ['only', 'skip'].map((property) => ({
    object,
    property,
    message:
      '.only/.skip은 다른 테스트를 조용히 빼버린다. 실패하는 테스트는 고치거나 PR에서 논의한다.',
  })),
);

export default [
  { ignores: ['dist/**', 'coverage/**'] },
  {
    // 인라인 eslint-disable 주석 금지: 예외는 이 파일에서만, 리뷰를 거쳐 만든다.
    linterOptions: { noInlineConfig: true, reportUnusedDisableDirectives: 'error' },
  },
  js.configs.recommended,
  react.configs.flat.recommended,
  react.configs.flat['jsx-runtime'],
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'no-unsanitized': noUnsanitized,
    },
    rules: {
      // 기본
      'no-unused-vars': 'error',
      // 타입 보완 (TypeScript 대신)
      eqeqeq: ['error', 'always'],
      'no-implicit-coercion': 'error',
      'array-callback-return': 'error',
      'default-param-last': 'error',
      'no-param-reassign': 'error',
      // 가독성
      'prefer-const': 'error',
      'no-var': 'error',
      curly: ['error', 'all'],
      // React
      'react/prop-types': 'off',
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      'react-refresh/only-export-components': 'warn',
      // 보안
      'react/no-danger': 'error',
      'react/jsx-no-script-url': 'error',
      'no-script-url': 'error',
      'react/jsx-no-target-blank': 'error',
      'no-eval': 'error',
      'no-implied-eval': 'error',
      'no-new-func': 'error',
      'no-unsanitized/method': 'error',
      'no-unsanitized/property': 'error',
      'no-restricted-globals': ['error', ...restrictedGlobals],
      'no-console': ['error', { allow: ['warn', 'error'] }],
    },
  },
  {
    // API 모듈만 fetch 허용 (저장소 금지는 유지)
    files: ['src/api/**/*.js'],
    rules: {
      'no-restricted-globals': ['error', ...restrictedGlobals.filter((g) => g.name !== 'fetch')],
    },
  },
  {
    files: ['**/*.test.{js,jsx}', 'src/test/**/*.js'],
    rules: {
      'no-restricted-properties': ['error', ...focusedOrSkippedTests],
    },
  },
  {
    // 설정 파일과 하네스 테스트는 Node 환경
    files: ['*.config.js', 'src/__harness__/**/*.js'],
    languageOptions: { globals: { ...globals.node } },
  },
  prettier,
];
