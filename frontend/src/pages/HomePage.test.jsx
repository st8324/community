import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { ApiError, api, onSessionExpired } from '../api/client.js';

// 화면 테스트는 fetch가 아니라 API 모듈 함수를 가짜로 바꾼다 (03 문서 HD-29).
vi.mock('../api/client.js', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() },
    onSessionExpired: vi.fn(),
  };
});

function unauthorized() {
  return new ApiError({
    status: 401,
    code: 'UNAUTHORIZED',
    fields: [],
    message: '로그인이 필요합니다.',
  });
}

function item(id, title, overrides = {}) {
  return {
    id,
    title,
    authorName: 'ab***@example.com',
    createdAt: '2026-10-24T01:02:03Z',
    ...overrides,
  };
}

function listOf(items, { page = 0, totalPages = 1 } = {}) {
  return { items, page, size: 20, totalElements: items.length, totalPages };
}

/** GET 요청을 경로별로 답하는 가짜. 비회원(/me → 401)으로 시작한다. */
function serve(routes) {
  api.get.mockImplementation(async (path) => {
    if (path === '/api/auth/me') {
      throw unauthorized();
    }
    if (path in routes) {
      const answer = routes[path];
      if (answer instanceof Error) {
        throw answer;
      }
      return answer;
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

const postPaths = () =>
  api.get.mock.calls.map(([path]) => path).filter((p) => p.startsWith('/api/posts'));

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
});

describe('게시글 목록 화면 (POST-02)', () => {
  it('항목마다 제목(상세 링크)·작성자 표시명·작성 시각을 보인다', async () => {
    serve({
      '/api/posts?page=0': listOf([
        item(12, '첫 글'),
        item(11, '둘째 글', { authorName: 'cd***@example.com' }),
      ]),
    });
    renderAt('/');

    const link = await screen.findByRole('link', { name: '첫 글' });
    expect(link).toHaveAttribute('href', '/posts/12');
    expect(screen.getByRole('link', { name: '둘째 글' })).toHaveAttribute('href', '/posts/11');
    expect(screen.getByText('cd***@example.com')).toBeInTheDocument();
    expect(document.querySelector('time[datetime="2026-10-24T01:02:03Z"]')).not.toBeNull();
  });

  it('주소에 page가 없으면 0페이지를 요청한다', async () => {
    serve({ '/api/posts?page=0': listOf([item(1, '글')]) });
    renderAt('/');

    await screen.findByRole('link', { name: '글' });
    expect(postPaths()).toEqual(['/api/posts?page=0']);
  });

  it.each(['-1', 'abc', '1.5', '', '1e2', '2abc'])(
    '?page=%s 처럼 0 이상 정수가 아니면 0페이지로 보고 요청한다',
    async (value) => {
      serve({ '/api/posts?page=0': listOf([item(1, '글')]) });
      renderAt(`/?page=${value}`);

      await screen.findByRole('link', { name: '글' });
      expect(postPaths()).toEqual(['/api/posts?page=0']);
    },
  );

  it('?page=2는 2페이지를 요청하고 이전·다음 링크와 현재 위치를 보인다', async () => {
    serve({ '/api/posts?page=2': listOf([item(5, '가운데 글')], { page: 2, totalPages: 5 }) });
    renderAt('/?page=2');

    await screen.findByRole('link', { name: '가운데 글' });
    expect(screen.getByRole('link', { name: '이전' })).toHaveAttribute('href', '/?page=1');
    expect(screen.getByRole('link', { name: '다음' })).toHaveAttribute('href', '/?page=3');
    expect(screen.getByText('3 / 5')).toBeInTheDocument();
  });

  it('첫 페이지에는 이전, 마지막 페이지에는 다음 링크가 없다', async () => {
    serve({
      '/api/posts?page=0': listOf([item(1, '처음')], { page: 0, totalPages: 2 }),
      '/api/posts?page=1': listOf([item(2, '끝')], { page: 1, totalPages: 2 }),
    });
    const first = renderAt('/');
    await screen.findByRole('link', { name: '처음' });
    expect(screen.queryByRole('link', { name: '이전' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '다음' })).toBeInTheDocument();
    first.unmount();

    renderAt('/?page=1');
    await screen.findByRole('link', { name: '끝' });
    expect(screen.queryByRole('link', { name: '다음' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '이전' })).toBeInTheDocument();
  });

  it('다음 링크를 누르면 다음 페이지를 요청해 항목을 바꾼다', async () => {
    serve({
      '/api/posts?page=0': listOf([item(1, '처음')], { page: 0, totalPages: 2 }),
      '/api/posts?page=1': listOf([item(2, '다음 쪽 글')], { page: 1, totalPages: 2 }),
    });
    renderAt('/');

    fireEvent.click(await screen.findByRole('link', { name: '다음' }));

    expect(await screen.findByRole('link', { name: '다음 쪽 글' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '처음' })).not.toBeInTheDocument();
    expect(postPaths()).toEqual(['/api/posts?page=0', '/api/posts?page=1']);
  });

  it('글이 없으면 안내 문장을 보인다', async () => {
    serve({ '/api/posts?page=0': listOf([], { totalPages: 0 }) });
    renderAt('/');

    expect(await screen.findByText('아직 글이 없습니다.')).toBeInTheDocument();
  });

  it('범위를 넘은 페이지(빈 목록)에는 첫 페이지로 가는 링크를 보인다', async () => {
    serve({ '/api/posts?page=9': listOf([], { page: 9, totalPages: 2 }) });
    renderAt('/?page=9');

    expect(await screen.findByText('아직 글이 없습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '첫 페이지로' })).toHaveAttribute('href', '/');
  });

  it('불러오는 동안 "불러오는 중…"을 보인다', async () => {
    api.get.mockImplementation((path) =>
      path === '/api/auth/me' ? Promise.reject(unauthorized()) : new Promise(() => {}),
    );
    renderAt('/');

    const status = await screen.findByText('글을 불러오는 중…');
    expect(status).toBeInTheDocument();
  });

  it('서버 오류는 서버 message를 보인다', async () => {
    serve({
      '/api/posts?page=0': new ApiError({
        status: 500,
        code: 'INTERNAL_ERROR',
        fields: [],
        message: '일시적인 오류가 발생했습니다.',
      }),
    });
    renderAt('/');

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다.');
  });

  it('응답 모양이 예상과 다르면 내용을 보이지 않고 고정 문장을 보인다', async () => {
    serve({ '/api/posts?page=0': { items: 'not-a-list', secret: 'internal-detail' } });
    renderAt('/');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(alert).not.toHaveTextContent('internal-detail');
  });

  it('제목은 HTML이 아니라 글자 그대로 보이고 스크립트는 실행되지 않는다 (SEC-08)', async () => {
    const script = '<script>window.__pwned = true</script>';
    const image = '<img src=x onerror="window.__pwned = true">';
    serve({ '/api/posts?page=0': listOf([item(1, script), item(2, image)]) });
    const { container } = renderAt('/');

    const list = await screen.findByRole('list');
    expect(within(list).getByText(script)).toBeInTheDocument();
    expect(within(list).getByText(image)).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(window.__pwned).toBeUndefined();
  });
});
