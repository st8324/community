import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { ApiError, api, onSessionExpired } from '../api/client.js';

vi.mock('../api/client.js', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() },
    onSessionExpired: vi.fn(),
  };
});

const ME = { id: 7, displayName: 'ab***@example.com' };
const EMAIL = 'ab@example.com';
// 테스트 비밀번호도 저장소에 고정 문자열로 두지 않는다 (CLAUDE.md 2-5).
const PASSWORD = `pw-${crypto.randomUUID()}`;

function unauthorized() {
  return new ApiError({
    status: 401,
    code: 'UNAUTHORIZED',
    fields: [],
    message: '로그인이 필요합니다.',
  });
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <App />
    </MemoryRouter>,
  );
}

async function fillAndSubmit({ remember = false } = {}) {
  fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: EMAIL } });
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: PASSWORD } });
  if (remember) {
    fireEvent.click(screen.getByLabelText('로그인 상태 유지'));
  }
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
}

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
  api.get.mockRejectedValue(unauthorized()); // 기본: 비회원으로 시작
});

describe('로그인 화면 (AUTH-02, AUTH-06)', () => {
  it('비밀번호 입력칸은 password 형식이다', async () => {
    renderLogin();
    expect(await screen.findByLabelText('비밀번호')).toHaveAttribute('type', 'password');
  });

  it('제출하면 POST /api/auth/login을 보내고 성공하면 목록으로 이동해 표시명을 보인다', async () => {
    api.post.mockResolvedValue(ME);
    renderLogin();

    await fillAndSubmit();

    expect(await screen.findByText('ab***@example.com')).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/api/auth/login', {
      email: EMAIL,
      password: PASSWORD,
      rememberMe: false,
    });
    expect(screen.getByRole('heading', { name: '커뮤니티' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '로그인' })).not.toBeInTheDocument();
  });

  it('"로그인 상태 유지"를 체크하면 rememberMe: true로 보낸다', async () => {
    api.post.mockResolvedValue(ME);
    renderLogin();

    await fillAndSubmit({ remember: true });

    await screen.findByText('ab***@example.com');
    expect(api.post).toHaveBeenCalledWith('/api/auth/login', {
      email: EMAIL,
      password: PASSWORD,
      rememberMe: true,
    });
  });

  it('실패하면 서버 message를 보이고 로그인 화면에 머문다', async () => {
    api.post.mockRejectedValue(
      new ApiError({
        status: 401,
        code: 'INVALID_CREDENTIALS',
        fields: [],
        message: '이메일 또는 비밀번호가 올바르지 않습니다.',
      }),
    );
    renderLogin();

    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이메일 또는 비밀번호가 올바르지 않습니다.',
    );
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
  });

  it('ApiError가 아닌 예외는 내용을 보이지 않고 고정 문장을 보인다 (SEC-10)', async () => {
    api.post.mockRejectedValue(new Error('TypeError at secret/internal/path.js:12'));
    renderLogin();

    await fillAndSubmit();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(alert).not.toHaveTextContent('secret/internal');
  });

  it('제출하는 동안 버튼이 비활성화되어 두 번 보내지 않는다', async () => {
    let finish = () => {};
    api.post.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve(ME);
        }),
    );
    renderLogin();

    await fillAndSubmit();
    const button = screen.getByRole('button', { name: '로그인' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(api.post).toHaveBeenCalledTimes(1);

    finish();
    await screen.findByText('ab***@example.com');
  });

  it('이미 로그인한 상태로 /login에 오면 목록으로 보낸다', async () => {
    api.get.mockReset();
    api.get.mockResolvedValue(ME);
    renderLogin();

    expect(await screen.findByRole('heading', { name: '커뮤니티' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '로그인' })).not.toBeInTheDocument();
  });
});
