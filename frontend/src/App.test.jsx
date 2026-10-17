// F-2 화면 테스트: 라우팅·로그인 상태·로그인/회원가입 (02 문서 5.8절, 03 문서 5.6절).
// 화면 테스트는 fetch가 아니라 src/api 모듈 함수를 가짜로 바꾼다 (HD-29). CSRF·연장은 client.test.js가 검증한다.
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import * as authApi from './api/auth.js';
import { ApiError } from './api/client.js';

vi.mock('./api/auth.js', () => ({
  me: vi.fn(),
  login: vi.fn(),
  signup: vi.fn(),
  logout: vi.fn(),
}));

// 로그아웃 알림(onSessionExpired)은 구독자를 붙잡아 두고 테스트에서 직접 울린다.
let sessionExpiredListener = null;
vi.mock('./api/client.js', async (importOriginal) => ({
  ...(await importOriginal()),
  onSessionExpired: (listener) => {
    sessionExpiredListener = listener;
    return () => {
      sessionExpiredListener = null;
    };
  },
}));

const USER = { id: 7, displayName: 'a***@example.com' };

function unauthorized() {
  return new ApiError({
    status: 401,
    code: 'UNAUTHORIZED',
    fields: [],
    message: '로그인이 필요합니다.',
  });
}

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

function fillCredentials({ email = 'a@example.com', password = 'password-123' } = {}) {
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: password } });
}

beforeEach(() => {
  sessionExpiredListener = null;
  authApi.me.mockRejectedValue(unauthorized());
});

describe('앱 시작 시 로그인 상태 (AUTH-05)', () => {
  it('/me가 성공하면 상단에 마스킹된 표시명과 로그아웃 버튼이 보인다', async () => {
    authApi.me.mockResolvedValue(USER);

    renderAt('/');

    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
  });

  it('/me가 401이면 로그인·회원가입 링크가 보인다', async () => {
    renderAt('/');

    expect(await screen.findByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: '회원가입' })).toHaveAttribute('href', '/signup');
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('표시명은 HTML로 해석하지 않고 글자 그대로 보여 준다 (SEC-08)', async () => {
    const script = '<img src=x onerror="window.__xss=1">';
    authApi.me.mockResolvedValue({ id: 7, displayName: script });

    const { container } = renderAt('/');

    expect(await screen.findByText(script)).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it('로그아웃 알림(연장 거절)이 오면 로그아웃 상태로 바뀐다 (T-19)', async () => {
    authApi.me.mockResolvedValue(USER);
    renderAt('/');
    await screen.findByText('a***@example.com');

    act(() => sessionExpiredListener());

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(screen.queryByText('a***@example.com')).not.toBeInTheDocument();
  });
});

describe('로그인 (AUTH-02, AUTH-06)', () => {
  it('성공하면 목록(/)으로 가고 상단에 표시명이 보인다', async () => {
    authApi.login.mockResolvedValue(USER);
    renderAt('/login');
    await screen.findByRole('heading', { name: '로그인' });

    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '로그인' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '게시글' })).toBeInTheDocument();
  });

  it('"로그인 상태 유지"를 체크하면 rememberMe: true로 보낸다', async () => {
    authApi.login.mockResolvedValue(USER);
    renderAt('/login');
    await screen.findByRole('heading', { name: '로그인' });

    fillCredentials();
    fireEvent.click(screen.getByLabelText('로그인 상태 유지'));
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    await screen.findByText('a***@example.com');
    expect(authApi.login).toHaveBeenCalledWith({
      email: 'a@example.com',
      password: 'password-123',
      rememberMe: true,
    });
  });

  it('체크하지 않으면 rememberMe: false로 보낸다', async () => {
    authApi.login.mockResolvedValue(USER);
    renderAt('/login');
    await screen.findByRole('heading', { name: '로그인' });

    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    await screen.findByText('a***@example.com');
    expect(authApi.login).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: false }));
  });

  it('실패하면 서버가 준 문장을 보여 주고 로그인 화면에 남는다', async () => {
    authApi.login.mockRejectedValue(
      new ApiError({
        status: 401,
        code: 'INVALID_CREDENTIALS',
        fields: [],
        message: '이메일 또는 비밀번호가 올바르지 않습니다.',
      }),
    );
    renderAt('/login');
    await screen.findByRole('heading', { name: '로그인' });

    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이메일 또는 비밀번호가 올바르지 않습니다.',
    );
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toHaveValue('');
  });

  it('비밀번호 칸은 가려지고 브라우저 자동완성 용도가 맞다', async () => {
    renderAt('/login');
    await screen.findByRole('heading', { name: '로그인' });

    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('autocomplete', 'current-password');
  });
});

describe('회원가입 (AUTH-01)', () => {
  it('성공하면 바로 로그인 상태로 목록(/)에 간다', async () => {
    authApi.signup.mockResolvedValue(USER);
    renderAt('/signup');
    await screen.findByRole('heading', { name: '회원가입' });

    fillCredentials();
    fireEvent.click(screen.getByLabelText('로그인 상태 유지'));
    fireEvent.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '게시글' })).toBeInTheDocument();
    expect(authApi.signup).toHaveBeenCalledWith({
      email: 'a@example.com',
      password: 'password-123',
      rememberMe: true,
    });
  });

  it('이미 가입된 이메일이면 서버 문장을 보여 준다', async () => {
    authApi.signup.mockRejectedValue(
      new ApiError({
        status: 409,
        code: 'EMAIL_ALREADY_USED',
        fields: [],
        message: '이미 가입된 이메일입니다.',
      }),
    );
    renderAt('/signup');
    await screen.findByRole('heading', { name: '회원가입' });

    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 가입된 이메일입니다.');
  });

  it('입력 검증 실패(400 fields)면 해당 칸을 잘못된 입력으로 표시한다', async () => {
    authApi.signup.mockRejectedValue(
      new ApiError({
        status: 400,
        code: 'VALIDATION_FAILED',
        fields: ['password'],
        message: '입력값을 확인해 주세요.',
      }),
    );
    renderAt('/signup');
    await screen.findByRole('heading', { name: '회원가입' });

    fillCredentials({ password: 'short' });
    fireEvent.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요.');
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('이메일')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('비밀번호 칸은 새 비밀번호용 자동완성이다', async () => {
    renderAt('/signup');
    await screen.findByRole('heading', { name: '회원가입' });

    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('autocomplete', 'new-password');
  });
});

describe('로그아웃 (AUTH-03)', () => {
  it('로그아웃하면 서버에 요청하고 로그인 링크가 보인다', async () => {
    authApi.me.mockResolvedValue(USER);
    authApi.logout.mockResolvedValue(null);
    renderAt('/');
    await screen.findByText('a***@example.com');

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(authApi.logout).toHaveBeenCalledTimes(1);
  });

  it('로그아웃 요청이 실패하면 로그인 상태를 유지하고 오류를 보여 준다 (쿠키가 남아 있으므로)', async () => {
    authApi.me.mockResolvedValue(USER);
    authApi.logout.mockRejectedValue(
      new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        fields: [],
        message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
      }),
    );
    renderAt('/');
    await screen.findByText('a***@example.com');

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다');
    expect(screen.getByText('a***@example.com')).toBeInTheDocument();
  });
});

describe('라우팅', () => {
  it('없는 경로는 "찾을 수 없음"을 보여 준다', async () => {
    renderAt('/no-such-page');

    expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument();
  });

  it('상단 제목은 목록(/)으로 가는 링크다', async () => {
    renderAt('/login');

    expect(await screen.findByRole('link', { name: '커뮤니티' })).toHaveAttribute('href', '/');
  });
});
