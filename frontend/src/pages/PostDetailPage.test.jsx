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

function unauthorized() {
  return new ApiError({
    status: 401,
    code: 'UNAUTHORIZED',
    fields: [],
    message: '로그인이 필요합니다.',
  });
}

function post(overrides = {}) {
  return {
    id: 12,
    title: '첫 글',
    content: '본문입니다.',
    authorName: 'ab***@example.com',
    createdAt: '2026-10-24T01:02:03Z',
    updatedAt: null,
    canEdit: false,
    canDelete: false,
    ...overrides,
  };
}

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
  api.put.mockReset();
  api.del.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
});

describe('게시글 상세 화면 (POST-03)', () => {
  it('제목·본문·작성자 표시명·작성 시각을 보인다', async () => {
    serve({ '/api/posts/12': post() });
    renderAt('/posts/12');

    expect(await screen.findByRole('heading', { name: '첫 글' })).toBeInTheDocument();
    expect(screen.getByText('본문입니다.')).toBeInTheDocument();
    expect(screen.getByText('ab***@example.com')).toBeInTheDocument();
    expect(document.querySelector('time[datetime="2026-10-24T01:02:03Z"]')).not.toBeNull();
    expect(postPaths()).toEqual(['/api/posts/12']);
  });

  it('수정한 적이 없으면(updatedAt null) "수정됨"을 보이지 않고, 수정했으면 보인다', async () => {
    serve({ '/api/posts/12': post() });
    const first = renderAt('/posts/12');
    await screen.findByRole('heading', { name: '첫 글' });
    expect(screen.queryByText(/수정됨/)).not.toBeInTheDocument();
    first.unmount();

    serve({ '/api/posts/12': post({ updatedAt: '2026-10-25T05:06:07Z' }) });
    renderAt('/posts/12');
    await screen.findByRole('heading', { name: '첫 글' });
    expect(screen.getByText(/수정됨/)).toBeInTheDocument();
    expect(document.querySelector('time[datetime="2026-10-25T05:06:07Z"]')).not.toBeNull();
  });

  it('줄바꿈은 CSS(white-space)로 보여 주고 본문 글자는 그대로다', async () => {
    serve({ '/api/posts/12': post({ content: '첫 줄\n둘째 줄' }) });
    renderAt('/posts/12');

    const content = await screen.findByText(/첫 줄/);
    expect(content).toHaveClass('post-content');
    expect(content.textContent).toBe('첫 줄\n둘째 줄');
  });

  it('제목·본문의 스크립트·HTML은 글자 그대로 보이고 실행되지 않는다 (SEC-08)', async () => {
    const script = '<script>window.__pwned = true</script>';
    const image = '<img src=x onerror="window.__pwned = true">';
    serve({ '/api/posts/12': post({ title: script, content: image }) });
    const { container } = renderAt('/posts/12');

    expect(await screen.findByRole('heading', { name: script })).toBeInTheDocument();
    expect(screen.getByText(image)).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(window.__pwned).toBeUndefined();
  });

  it('작성자(canEdit·canDelete 둘 다 true)에게는 수정 링크와 삭제 버튼이 보인다', async () => {
    serve({ '/api/posts/12': post({ canEdit: true, canDelete: true }) });
    renderAt('/posts/12');

    expect(await screen.findByRole('link', { name: '수정' })).toHaveAttribute(
      'href',
      '/posts/12/edit',
    );
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument();
  });

  it('관리자가 타인 글을 볼 때(canEdit false, canDelete true)는 삭제 버튼만 보인다', async () => {
    serve({ '/api/posts/12': post({ canEdit: false, canDelete: true }) });
    renderAt('/posts/12');

    expect(await screen.findByRole('button', { name: '삭제' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '수정' })).not.toBeInTheDocument();
  });

  it('권한이 없으면(둘 다 false) 수정·삭제 버튼이 없다', async () => {
    serve({ '/api/posts/12': post() });
    renderAt('/posts/12');

    await screen.findByRole('heading', { name: '첫 글' });
    expect(screen.queryByRole('link', { name: '수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();
  });

  it('목록으로 돌아가는 링크가 있다', async () => {
    serve({ '/api/posts/12': post() });
    renderAt('/posts/12');

    expect(await screen.findByRole('link', { name: '목록으로' })).toHaveAttribute('href', '/');
  });

  it('없는·삭제된 글(404)은 "찾을 수 없음"을 보인다', async () => {
    serve({
      '/api/posts/99': new ApiError({
        status: 404,
        code: 'NOT_FOUND',
        fields: [],
        message: '요청한 대상을 찾을 수 없습니다.',
      }),
    });
    renderAt('/posts/99');

    expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument();
  });

  it('그 밖의 서버 오류는 서버 message를 보인다', async () => {
    serve({
      '/api/posts/12': new ApiError({
        status: 500,
        code: 'INTERNAL_ERROR',
        fields: [],
        message: '일시적인 오류가 발생했습니다.',
      }),
    });
    renderAt('/posts/12');

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(screen.queryByRole('heading', { name: '찾을 수 없음' })).not.toBeInTheDocument();
  });

  it('응답 모양이 예상과 다르면 내용을 보이지 않고 고정 문장을 보인다', async () => {
    serve({ '/api/posts/12': { title: 7, secret: 'internal-detail' } });
    renderAt('/posts/12');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(alert).not.toHaveTextContent('internal-detail');
  });

  it.each(['abc', '0', '-1', '1.5', '12abc', '012'])(
    '/posts/%s 처럼 양의 정수가 아닌 주소는 요청 없이 "찾을 수 없음"을 보인다',
    async (value) => {
      serve({});
      renderAt(`/posts/${value}`);

      expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument();
      expect(postPaths()).toEqual([]);
    },
  );

  it('다른 글로 이동하면 새 글을 요청한다 (목록 → 상세)', async () => {
    serve({ '/api/posts/12': post(), '/api/posts/13': post({ id: 13, title: '다른 글' }) });
    const { unmount } = renderAt('/posts/12');
    await screen.findByRole('heading', { name: '첫 글' });
    unmount();

    renderAt('/posts/13');
    expect(await screen.findByRole('heading', { name: '다른 글' })).toBeInTheDocument();
    expect(postPaths()).toEqual(['/api/posts/12', '/api/posts/13']);
  });
});

describe('게시글 삭제 (POST-05)', () => {
  const owner = () => post({ canEdit: true, canDelete: true });

  it('삭제 버튼을 누르면 바로 지우지 않고 확인을 먼저 묻는다', async () => {
    serve({ '/api/posts/12': owner() });
    renderAt('/posts/12');

    fireEvent.click(await screen.findByRole('button', { name: '삭제' }));

    expect(screen.getByText('이 글을 삭제할까요?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제 확인' })).toBeInTheDocument();
    expect(api.del).not.toHaveBeenCalled();
  });

  it('취소하면 확인 문구가 사라지고 삭제 요청은 없다', async () => {
    serve({ '/api/posts/12': owner() });
    renderAt('/posts/12');

    fireEvent.click(await screen.findByRole('button', { name: '삭제' }));
    fireEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(screen.queryByText('이 글을 삭제할까요?')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument();
    expect(api.del).not.toHaveBeenCalled();
  });

  it('삭제 확인을 누르면 DELETE /api/posts/12 후 목록으로 이동한다', async () => {
    serve({
      '/api/posts/12': owner(),
      '/api/posts?page=0': { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
    api.del.mockResolvedValue(null);
    renderAt('/posts/12');

    fireEvent.click(await screen.findByRole('button', { name: '삭제' }));
    fireEvent.click(screen.getByRole('button', { name: '삭제 확인' }));

    expect(await screen.findByText('아직 글이 없습니다.')).toBeInTheDocument();
    expect(api.del).toHaveBeenCalledWith('/api/posts/12');
  });

  it('서버가 거절하면(403) 서버 message를 보이고 상세 화면에 머문다', async () => {
    serve({ '/api/posts/12': owner() });
    api.del.mockRejectedValue(
      new ApiError({ status: 403, code: 'FORBIDDEN', fields: [], message: '권한이 없습니다.' }),
    );
    renderAt('/posts/12');

    fireEvent.click(await screen.findByRole('button', { name: '삭제' }));
    fireEvent.click(screen.getByRole('button', { name: '삭제 확인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('권한이 없습니다.');
    expect(screen.getByRole('heading', { name: '첫 글' })).toBeInTheDocument();
  });

  it('삭제하는 동안 확인 버튼이 비활성화되어 두 번 보내지 않는다', async () => {
    serve({
      '/api/posts/12': owner(),
      '/api/posts?page=0': { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
    let finish = () => {};
    api.del.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve(null);
        }),
    );
    renderAt('/posts/12');

    fireEvent.click(await screen.findByRole('button', { name: '삭제' }));
    const confirm = screen.getByRole('button', { name: '삭제 확인' });
    fireEvent.click(confirm);
    expect(confirm).toBeDisabled();
    fireEvent.click(confirm);
    expect(api.del).toHaveBeenCalledTimes(1);

    finish();
    await screen.findByText('아직 글이 없습니다.');
  });
});
