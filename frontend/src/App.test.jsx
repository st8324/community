import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import { ApiError, api, onSessionExpired } from './api/client.js';

// 화면 테스트는 fetch가 아니라 API 모듈 함수를 가짜로 바꾼다 (03 문서 HD-29).
vi.mock('./api/client.js', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() },
    onSessionExpired: vi.fn(),
  };
});

const ME = { id: 1, displayName: 'ab***@example.com' };

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

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
});

describe('앱 시작과 상단 표시 (AUTH-05)', () => {
  it('목록 자리(/)에 제목이 보인다', async () => {
    api.get.mockResolvedValue(ME);
    renderAt('/');
    expect(await screen.findByRole('heading', { name: '커뮤니티' })).toBeInTheDocument();
  });

  it('시작하면 /api/auth/me로 로그인 상태를 확인하고 표시명과 로그아웃 버튼을 보인다', async () => {
    api.get.mockResolvedValue(ME);
    renderAt('/');

    expect(await screen.findByText('ab***@example.com')).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith('/api/auth/me');
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
  });

  it('/me가 401이면 로그인·회원가입 링크를 보인다', async () => {
    api.get.mockRejectedValue(unauthorized());
    renderAt('/');

    expect(await screen.findByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: '회원가입' })).toHaveAttribute('href', '/signup');
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('/me가 일시 오류(5xx·네트워크)여도 앱은 뜨고 비회원 화면이다 — 로그아웃을 부르지 않는다', async () => {
    api.get.mockRejectedValue(
      new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        fields: [],
        message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
      }),
    );
    renderAt('/');

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('표시명은 HTML이 아니라 글자 그대로 보인다 (SEC-08)', async () => {
    const hostile = '<img src=x onerror=alert(1)>';
    api.get.mockResolvedValue({ id: 2, displayName: hostile });
    const { container } = renderAt('/');

    expect(await screen.findByText(hostile)).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });
});

describe('없는 경로', () => {
  it('알 수 없는 경로는 "찾을 수 없음"을 보인다', async () => {
    api.get.mockRejectedValue(unauthorized());
    renderAt('/no/such/page');
    expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument();
  });
});

describe('로그아웃 (AUTH-03)', () => {
  it('로그아웃 버튼은 POST /api/auth/logout을 보내고 비회원 화면이 된다', async () => {
    api.get.mockResolvedValue(ME);
    api.post.mockResolvedValue(null);
    renderAt('/');

    fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }));

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/api/auth/logout');
    expect(screen.queryByText('ab***@example.com')).not.toBeInTheDocument();
  });

  it('로그아웃 요청이 실패하면 로그인 상태를 유지하고 서버 message를 보인다', async () => {
    // 목록 화면(F-3)이 /api/posts도 요청하므로 경로별로 답한다 (오류 알림이 이 테스트의 알림 하나뿐이도록).
    api.get.mockImplementation(async (path) =>
      path === '/api/auth/me'
        ? ME
        : { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    );
    api.post.mockRejectedValue(
      new ApiError({
        status: 500,
        code: 'INTERNAL_ERROR',
        fields: [],
        message: '일시적인 오류가 발생했습니다.',
      }),
    );
    renderAt('/');

    fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(screen.getByText('ab***@example.com')).toBeInTheDocument();
  });
});

describe('로그인 상태 유지 실패 알림 (AUTH-06, 02 T-19)', () => {
  it('연장이 거절돼 알림이 오면 비회원 화면이 된다', async () => {
    let notify = () => {};
    onSessionExpired.mockImplementation((listener) => {
      notify = listener;
      return () => {};
    });
    api.get.mockResolvedValue(ME);
    renderAt('/');
    expect(await screen.findByText('ab***@example.com')).toBeInTheDocument();

    act(() => notify());

    await waitFor(() => expect(screen.queryByText('ab***@example.com')).not.toBeInTheDocument());
    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
  });

  it('화면이 사라지면 구독을 해제한다', async () => {
    const unsubscribe = vi.fn();
    onSessionExpired.mockImplementation(() => unsubscribe);
    api.get.mockResolvedValue(ME);
    const { unmount } = renderAt('/');
    await screen.findByText('ab***@example.com');

    unmount();

    expect(unsubscribe).toHaveBeenCalled();
  });
});
