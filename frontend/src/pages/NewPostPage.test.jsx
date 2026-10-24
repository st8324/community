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

const ME = { id: 1, displayName: 'ab***@example.com' };

function unauthorized() {
  return new ApiError({
    status: 401,
    code: 'UNAUTHORIZED',
    fields: [],
    message: '로그인이 필요합니다.',
  });
}

function created(overrides = {}) {
  return {
    id: 31,
    title: '새 글',
    content: '새 본문',
    authorName: 'ab***@example.com',
    createdAt: '2026-10-24T01:02:03Z',
    updatedAt: null,
    canEdit: true,
    canDelete: true,
    ...overrides,
  };
}

/** loggedIn이 true면 /me가 ME를 돌려주고, 아니면 401. 그 밖의 GET은 routes에서 찾는다. */
function serve({ loggedIn, routes = {} }) {
  api.get.mockImplementation(async (path) => {
    if (path === '/api/auth/me') {
      if (loggedIn) {
        return ME;
      }
      throw unauthorized();
    }
    if (path in routes) {
      return routes[path];
    }
    throw new Error(`예상하지 못한 요청: ${path}`);
  });
}

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

async function fillAndSubmit(title = '새 글', content = '새 본문') {
  fireEvent.change(await screen.findByLabelText('제목'), { target: { value: title } });
  fireEvent.change(screen.getByLabelText('본문'), { target: { value: content } });
  fireEvent.click(screen.getByRole('button', { name: '등록' }));
}

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  api.put.mockReset();
  api.del.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
});

describe('글쓰기 진입', () => {
  it('로그인 상태에서는 상단에 글쓰기 링크가 보이고, 비회원에게는 없다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts?page=0': { items: [], totalPages: 0 } } });
    const first = renderAt('/');
    expect(await screen.findByRole('link', { name: '글쓰기' })).toHaveAttribute(
      'href',
      '/posts/new',
    );
    first.unmount();

    serve({ loggedIn: false, routes: { '/api/posts?page=0': { items: [], totalPages: 0 } } });
    renderAt('/');
    await screen.findByRole('link', { name: '로그인' });
    expect(screen.queryByRole('link', { name: '글쓰기' })).not.toBeInTheDocument();
  });
});

describe('글 작성 화면 (POST-01)', () => {
  it('비로그인이면 /login으로 보낸다 — 작성 요청은 보내지 않는다', async () => {
    serve({ loggedIn: false });
    renderAt('/posts/new');

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(screen.queryByLabelText('제목')).not.toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('제목·본문을 보내면 POST /api/posts, 성공하면 새 글 상세로 이동한다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/31': created() } });
    api.post.mockResolvedValue(created());
    renderAt('/posts/new');

    await fillAndSubmit('새 글', '새 본문');

    expect(await screen.findByRole('heading', { name: '새 글' })).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/api/posts', { title: '새 글', content: '새 본문' });
    expect(screen.getByText('새 본문')).toBeInTheDocument();
  });

  it('입력값은 다듬지 않고 그대로 보낸다 — 검증·공백 제거는 서버가 한다 (SEC-01)', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/31': created() } });
    api.post.mockResolvedValue(created());
    renderAt('/posts/new');

    await fillAndSubmit('  앞뒤 공백  ', '첫 줄\n둘째 줄  ');

    await screen.findByRole('heading', { name: '새 글' });
    expect(api.post).toHaveBeenCalledWith('/api/posts', {
      title: '  앞뒤 공백  ',
      content: '첫 줄\n둘째 줄  ',
    });
  });

  it('검증 실패(400)는 서버 message를 보이고 fields에 있는 입력칸만 오류로 표시한다', async () => {
    serve({ loggedIn: true });
    api.post.mockRejectedValue(
      new ApiError({
        status: 400,
        code: 'VALIDATION_FAILED',
        fields: ['title'],
        message: '입력값을 확인해 주세요.',
      }),
    );
    renderAt('/posts/new');

    await fillAndSubmit('', '본문');

    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요.');
    expect(screen.getByLabelText('제목')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('본문')).not.toHaveAttribute('aria-invalid', 'true');
    // 입력한 내용은 지우지 않는다
    expect(screen.getByLabelText('본문')).toHaveValue('본문');
  });

  it('ApiError가 아닌 예외는 내용을 숨기고 고정 문장을 보인다 (SEC-10)', async () => {
    serve({ loggedIn: true });
    api.post.mockRejectedValue(new Error('TypeError at secret/internal/path.js:12'));
    renderAt('/posts/new');

    await fillAndSubmit();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(alert).not.toHaveTextContent('secret/internal');
  });

  it('응답에 글 번호가 없으면 이동하지 않고 고정 문장을 보인다', async () => {
    serve({ loggedIn: true });
    api.post.mockResolvedValue({ unexpected: true });
    renderAt('/posts/new');

    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(screen.getByLabelText('제목')).toBeInTheDocument();
  });

  it('제출하는 동안 버튼이 비활성화되어 두 번 보내지 않는다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/31': created() } });
    let finish = () => {};
    api.post.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve(created());
        }),
    );
    renderAt('/posts/new');

    await fillAndSubmit();
    const button = screen.getByRole('button', { name: '등록' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(api.post).toHaveBeenCalledTimes(1);

    finish();
    await screen.findByRole('heading', { name: '새 글' });
  });
});
