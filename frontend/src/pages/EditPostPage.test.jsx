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

function post(overrides = {}) {
  return {
    id: 12,
    title: '원래 제목',
    content: '원래 본문',
    authorName: 'ab***@example.com',
    createdAt: '2026-10-24T01:02:03Z',
    updatedAt: null,
    canEdit: true,
    canDelete: true,
    ...overrides,
  };
}

function serve({ loggedIn, routes = {} }) {
  api.get.mockImplementation(async (path) => {
    if (path === '/api/auth/me') {
      if (loggedIn) {
        return ME;
      }
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

describe('글 수정 화면 (POST-04)', () => {
  it('비로그인이면 /login으로 보낸다 — 글도 요청하지 않는다', async () => {
    serve({ loggedIn: false });
    renderAt('/posts/12/edit');

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(postPaths()).toEqual([]);
    expect(api.put).not.toHaveBeenCalled();
  });

  it('상세를 불러와 제목·본문을 채우고, 수정하면 PUT /api/posts/12 후 상세로 이동한다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/12': post() } });
    api.put.mockResolvedValue(post({ title: '고친 제목', content: '고친 본문' }));
    renderAt('/posts/12/edit');

    const title = await screen.findByLabelText('제목');
    expect(title).toHaveValue('원래 제목');
    expect(screen.getByLabelText('본문')).toHaveValue('원래 본문');

    fireEvent.change(title, { target: { value: '고친 제목' } });
    fireEvent.change(screen.getByLabelText('본문'), { target: { value: '고친 본문' } });
    // 이동한 상세 화면이 새로 불러올 응답
    serve({
      loggedIn: true,
      routes: { '/api/posts/12': post({ title: '고친 제목', content: '고친 본문' }) },
    });
    fireEvent.click(screen.getByRole('button', { name: '수정' }));

    expect(await screen.findByRole('heading', { name: '고친 제목' })).toBeInTheDocument();
    expect(api.put).toHaveBeenCalledWith('/api/posts/12', {
      title: '고친 제목',
      content: '고친 본문',
    });
  });

  it('canEdit이 false이면 폼을 보이지 않고 상세 화면으로 보낸다 (관리자가 타인 글에 접근)', async () => {
    serve({
      loggedIn: true,
      routes: { '/api/posts/12': post({ canEdit: false, canDelete: true }) },
    });
    renderAt('/posts/12/edit');

    expect(await screen.findByRole('heading', { name: '원래 제목' })).toBeInTheDocument();
    expect(screen.queryByLabelText('제목')).not.toBeInTheDocument();
    expect(api.put).not.toHaveBeenCalled();
  });

  it('없는·삭제된 글(404)은 "찾을 수 없음"을 보인다', async () => {
    serve({
      loggedIn: true,
      routes: {
        '/api/posts/99': new ApiError({
          status: 404,
          code: 'NOT_FOUND',
          fields: [],
          message: '요청한 대상을 찾을 수 없습니다.',
        }),
      },
    });
    renderAt('/posts/99/edit');

    expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument();
  });

  it.each(['abc', '0', '-1', '012'])(
    '/posts/%s/edit 처럼 양의 정수가 아닌 주소는 요청 없이 "찾을 수 없음"을 보인다',
    async (value) => {
      serve({ loggedIn: true });
      renderAt(`/posts/${value}/edit`);

      expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument();
      expect(postPaths()).toEqual([]);
    },
  );

  it('검증 실패(400)는 서버 message를 보이고 fields의 입력칸만 오류로 표시한다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/12': post() } });
    api.put.mockRejectedValue(
      new ApiError({
        status: 400,
        code: 'VALIDATION_FAILED',
        fields: ['content'],
        message: '입력값을 확인해 주세요.',
      }),
    );
    renderAt('/posts/12/edit');

    fireEvent.change(await screen.findByLabelText('본문'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '수정' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요.');
    expect(screen.getByLabelText('본문')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('제목')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('서버가 거절하면(403) 서버 message를 보이고 수정 화면에 머문다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/12': post() } });
    api.put.mockRejectedValue(
      new ApiError({
        status: 403,
        code: 'FORBIDDEN',
        fields: [],
        message: '권한이 없습니다.',
      }),
    );
    renderAt('/posts/12/edit');

    await screen.findByLabelText('제목');
    fireEvent.click(screen.getByRole('button', { name: '수정' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('권한이 없습니다.');
    expect(screen.getByLabelText('제목')).toBeInTheDocument();
  });

  it('제출하는 동안 버튼이 비활성화되어 두 번 보내지 않는다', async () => {
    serve({ loggedIn: true, routes: { '/api/posts/12': post() } });
    let finish = () => {};
    api.put.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve(post());
        }),
    );
    renderAt('/posts/12/edit');

    await screen.findByLabelText('제목');
    const button = screen.getByRole('button', { name: '수정' });
    fireEvent.click(button);
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(api.put).toHaveBeenCalledTimes(1);

    finish();
    await screen.findByRole('heading', { name: '원래 제목' });
  });
});
