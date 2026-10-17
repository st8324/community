// 린트 규칙 자체의 회귀 테스트.
// 누군가 eslint.config.js에서 보안 규칙을 끄거나 약하게 바꾸면 이 테스트가 실패한다.
// (규칙을 바꾸는 것이 맞다면 이 테스트도 같은 PR에서 이유와 함께 바꾼다.)
import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

const eslint = new ESLint({ cwd: process.cwd() });
// 이 파일 자체가 no-script-url에 걸리지 않도록 조립해서 쓴다.
const SCRIPT_URL = ['javascript', 'void(0)'].join(':');

async function ruleIdsFor(code, filePath = 'src/features/Example.jsx') {
  const [result] = await eslint.lintText(code, { filePath });
  return result.messages.map((m) => m.ruleId);
}

const cases = [
  {
    name: 'dangerouslySetInnerHTML 금지 (SEC-08)',
    code: 'export const A = ({ html }) => <div dangerouslySetInnerHTML={{ __html: html }} />;',
    rule: 'react/no-danger',
  },
  {
    name: 'innerHTML 직접 대입 금지 (SEC-08)',
    code: 'export function f(el, s) { el.innerHTML = s; }',
    rule: 'no-unsanitized/property',
  },
  {
    name: 'insertAdjacentHTML 금지 (SEC-08)',
    code: "export function f(el, s) { el.insertAdjacentHTML('beforeend', s); }",
    rule: 'no-unsanitized/method',
  },
  {
    name: '스크립트 URL(javascript 스킴) 금지',
    code: `export const A = () => <a href="${SCRIPT_URL}">x</a>;`,
    rule: 'react/jsx-no-script-url',
  },
  {
    name: 'localStorage 금지 (SEC-05)',
    code: "export function f(t) { localStorage.setItem('token', t); }",
    rule: 'no-restricted-globals',
  },
  {
    name: 'sessionStorage 금지 (SEC-05)',
    code: "export function f(t) { sessionStorage.setItem('token', t); }",
    rule: 'no-restricted-globals',
  },
  {
    name: 'src/api 밖에서 fetch 금지 (SEC-05)',
    code: "export function f() { return fetch('/api/posts'); }",
    rule: 'no-restricted-globals',
  },
  {
    name: 'eval 금지',
    code: 'export function f(s) { return eval(s); }',
    rule: 'no-eval',
  },
  {
    name: 'new Function 금지',
    code: "export const f = new Function('return 1');",
    rule: 'no-new-func',
  },
  {
    name: 'console.log 금지 (SEC-11)',
    code: 'export function f(x) { console.log(x); }',
    rule: 'no-console',
  },
  {
    name: '느슨한 비교 금지 (타입 보완)',
    code: 'export function f(a, b) { return a == b; }',
    rule: 'eqeqeq',
  },
];

describe('ESLint 보안·하네스 규칙이 살아 있다', () => {
  it.each(cases)('$name', async ({ code, rule }) => {
    expect(await ruleIdsFor(code)).toContain(rule);
  });

  it('src/api 안에서는 fetch를 허용한다', async () => {
    const ids = await ruleIdsFor(
      "export function f() { return fetch('/api/posts'); }",
      'src/api/client.js',
    );
    expect(ids).not.toContain('no-restricted-globals');
  });

  it('src/api 안에서도 localStorage는 금지한다', async () => {
    const ids = await ruleIdsFor(
      "export function f(t) { localStorage.setItem('k', t); }",
      'src/api/client.js',
    );
    expect(ids).toContain('no-restricted-globals');
  });

  it('인라인 eslint-disable 주석으로 규칙을 끌 수 없다', async () => {
    const code = '// eslint-disable-next-line no-eval\nexport function f(s) { return eval(s); }';
    expect(await ruleIdsFor(code)).toContain('no-eval');
  });

  // (v0.7, HD-31) no-restricted-globals는 전역 "이름"만 잡는다. 전역 객체를 거치는 우회 경로를 막는다.
  it.each([
    ['window.fetch', "export function f() { return window.fetch('/api/posts'); }"],
    ['globalThis.fetch', "export function f() { return globalThis.fetch('/api/posts'); }"],
    ['self.fetch', "export function f() { return self.fetch('/api/posts'); }"],
    ['구조 분해 { fetch } = window', 'const { fetch: f } = window;\nexport default f;'],
    ['window.localStorage', "export function f(t) { window.localStorage.setItem('k', t); }"],
    [
      'globalThis.sessionStorage',
      "export function f(t) { globalThis.sessionStorage.setItem('k', t); }",
    ],
    ['document.cookie 읽기', 'export function f() { return document.cookie; }'],
    ['document.cookie 쓰기', "export function f() { document.cookie = 'a=b'; }"],
  ])('src/api 밖에서 %s 금지 (SEC-05)', async (_name, code) => {
    expect(await ruleIdsFor(code)).toContain('no-restricted-properties');
  });

  it('src/api 안에서는 window.fetch와 document.cookie를 허용한다', async () => {
    const code = "export function f() { return [window.fetch('/api/x'), document.cookie]; }";
    const ids = await ruleIdsFor(code, 'src/api/client.js');
    expect(ids).not.toContain('no-restricted-properties');
  });

  it('src/api 안에서도 window.localStorage는 금지한다', async () => {
    const code = "export function f(t) { window.localStorage.setItem('k', t); }";
    expect(await ruleIdsFor(code, 'src/api/client.js')).toContain('no-restricted-properties');
  });

  it.each([
    ["vi.stubGlobal('fetch')", "import { vi } from 'vitest';\nvi.stubGlobal('fetch', vi.fn());"],
    [
      "vi.spyOn(globalThis, 'fetch')",
      "import { vi } from 'vitest';\nvi.spyOn(globalThis, 'fetch');",
    ],
  ])('src/api 밖의 테스트에서 %s 금지 (HD-29)', async (_name, code) => {
    const ids = await ruleIdsFor(code, 'src/features/Example.test.jsx');
    expect(ids).toContain('no-restricted-syntax');
  });

  it("src/api 테스트에서는 vi.stubGlobal('fetch')를 허용하고 .only는 여전히 금지한다", async () => {
    const code =
      "import { it, vi } from 'vitest';\nvi.stubGlobal('fetch', vi.fn());\nit.only('x', () => {});";
    const ids = await ruleIdsFor(code, 'src/api/client.test.js');
    expect(ids).not.toContain('no-restricted-syntax');
    expect(ids).toContain('no-restricted-properties');
  });

  it('src/api 밖의 테스트 파일에서도 .only 금지와 window.fetch 금지가 함께 살아 있다', async () => {
    const code =
      "import { it } from 'vitest';\nit.only('x', () => {});\nexport const f = () => window.fetch('/api/x');";
    const ids = await ruleIdsFor(code, 'src/features/Example.test.jsx');
    expect(ids.filter((id) => id === 'no-restricted-properties')).toHaveLength(2);
  });

  it('테스트 파일에서 .only / .skip을 금지한다', async () => {
    const code = "import { it } from 'vitest';\nit.only('x', () => {});\nit.skip('y', () => {});";
    const ids = await ruleIdsFor(code, 'src/features/Example.test.jsx');
    expect(ids.filter((id) => id === 'no-restricted-properties')).toHaveLength(2);
  });
});
