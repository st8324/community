import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
const COMMENTS = '/api/posts/12/comments';

function apiError(status, code, message, fields = []) {
  return new ApiError({ status, code, fields, message });
}

function post() {
  return {
    id: 12,
    title: '첫 글',
    content: '본문입니다.',
    authorName: 'ab***@example.com',
    createdAt: '2026-10-24T01:02:03Z',
    updatedAt: null,
    canEdit: false,
    canDelete: false,
  };
}

function comment(id, overrides = {}) {
  return {
    id,
    content: `댓글 ${id}`,
    authorName: 'cd***@example.com',
    createdAt: '2026-10-24T02:03:04Z',
    canDelete: false,
    ...overrides,
  };
}

/** commentsAnswer는 값 또는 함수(호출 때마다 다른 답) 또는 Error. */
function serve({ loggedIn = false, commentsAnswer = { items: [] } }) {
  api.get.mockImplementation(async (path) => {
    if (path === '/api/auth/me') {
      if (loggedIn) {
        return ME;
      }
      throw apiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.');
    }
    if (path === '/api/posts/12') {
      return post();
    }
    if (path === COMMENTS) {
      const answer = typeof commentsAnswer === 'function' ? commentsAnswer() : commentsAnswer;
      if (answer instanceof Error) {
        throw answer;
      }
      return answer;
    }
    throw new Error(`예상하지 못한 요청: ${path}`);
  });
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/posts/12']}>
      <App />
    </MemoryRouter>,
  );
}

const commentCalls = () => api.get.mock.calls.filter(([path]) => path === COMMENTS).length;

async function submitComment(text = '새 댓글') {
  fireEvent.change(await screen.findByLabelText('댓글 내용'), { target: { value: text } });
  fireEvent.click(screen.getByRole('button', { name: '댓글 등록' }));
}

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  api.put.mockReset();
  api.del.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
});

describe('댓글 목록 (CMT-02)', () => {
  it('글과 따로 불러와 서버가 준 순서대로 보여 준다', async () => {
    serve({ commentsAnswer: { items: [comment(1), comment(2), comment(3)] } });
    renderDetail();

    const items = await screen.findAllByRole('listitem');
    expect(items.map((li) => within(li).getByText(/^댓글 \d$/).textContent)).toEqual([
      '댓글 1',
      '댓글 2',
      '댓글 3',
    ]);
    expect(screen.getAllByText('cd***@example.com')).toHaveLength(3);
    expect(commentCalls()).toBe(1);
  });

  it('댓글이 없으면 안내 문구를 보인다', async () => {
    serve({ commentsAnswer: { items: [] } });
    renderDetail();

    expect(await screen.findByText('아직 댓글이 없습니다.')).toBeInTheDocument();
  });

  it('댓글을 불러오지 못해도 글은 그대로 보이고, 댓글 영역에만 오류를 보인다', async () => {
    serve({
      commentsAnswer: apiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.'),
    });
    renderDetail();

    expect(await screen.findByRole('heading', { name: '첫 글' })).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('로그인이 필요합니다.');
    expect(screen.getByText('본문입니다.')).toBeInTheDocument();
  });

  it('응답 모양이 약속과 다르면 내용을 쓰지 않고 고정 문장을 보인다 (SEC-10)', async () => {
    serve({ commentsAnswer: { items: [{ id: 'x', content: 3 }] } });
    renderDetail();

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다.');
  });

  it('<script> 댓글은 글자 그대로 보이고 실행 요소가 만들어지지 않는다 (SEC-08)', async () => {
    const hostile = '<script>alert(1)</script><img src=x onerror=alert(1)>';
    serve({ commentsAnswer: { items: [comment(1, { content: hostile })] } });
    const { container } = renderDetail();

    expect(await screen.findByText(hostile)).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
  });
});

describe('댓글 작성 (CMT-01)', () => {
  it('비로그인이면 입력칸 대신 로그인 안내 링크를 보인다', async () => {
    serve({ loggedIn: false });
    renderDetail();

    const link = await screen.findByRole('link', { name: '로그인하면 댓글을 쓸 수 있어요' });
    expect(link).toHaveAttribute('href', '/login');
    expect(screen.queryByLabelText('댓글 내용')).not.toBeInTheDocument();
  });

  it('등록하면 POST 후 목록을 다시 불러오고 입력칸을 비운다', async () => {
    let answer = { items: [] };
    serve({ loggedIn: true, commentsAnswer: () => answer });
    api.post.mockImplementation(async () => {
      answer = { items: [comment(7, { content: '새 댓글', canDelete: true })] };
      return answer.items[0];
    });
    renderDetail();

    await submitComment('새 댓글');

    expect(await screen.findByText('새 댓글', { selector: 'p' })).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith(COMMENTS, { content: '새 댓글' });
    expect(commentCalls()).toBe(2);
    expect(screen.getByLabelText('댓글 내용')).toHaveValue('');
  });

  it('입력값은 다듬지 않고 그대로 보낸다 — 검증·공백 제거는 서버가 한다 (SEC-01)', async () => {
    serve({ loggedIn: true });
    api.post.mockResolvedValue(comment(7));
    renderDetail();

    await submitComment('  앞뒤 공백  ');

    await screen.findByText('아직 댓글이 없습니다.');
    expect(api.post).toHaveBeenCalledWith(COMMENTS, { content: '  앞뒤 공백  ' });
  });

  it('검증 실패(400)는 서버 message를 보이고 content 입력칸을 오류로 표시한다 — 입력은 남긴다', async () => {
    serve({ loggedIn: true });
    api.post.mockRejectedValue(
      apiError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', ['content']),
    );
    renderDetail();

    await submitComment('   ');

    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요.');
    expect(screen.getByLabelText('댓글 내용')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('댓글 내용')).toHaveValue('   ');
  });

  it('ApiError가 아닌 예외는 내용을 숨기고 고정 문장을 보인다 (SEC-10)', async () => {
    serve({ loggedIn: true });
    api.post.mockRejectedValue(new Error('TypeError at secret/internal/path.js:12'));
    renderDetail();

    await submitComment();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('일시적인 오류가 발생했습니다.');
    expect(alert).not.toHaveTextContent('secret/internal');
  });

  it('제출하는 동안 버튼이 비활성화되어 두 번 보내지 않는다', async () => {
    serve({ loggedIn: true });
    let finish = () => {};
    api.post.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve(comment(7));
        }),
    );
    renderDetail();

    await submitComment();
    const button = screen.getByRole('button', { name: '댓글 등록' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(api.post).toHaveBeenCalledTimes(1);

    finish();
    await screen.findByText('아직 댓글이 없습니다.');
  });
});

describe('댓글 삭제 (CMT-03)', () => {
  function listWithOwn() {
    return {
      items: [
        comment(1, { content: '남의 댓글' }),
        comment(2, { content: '내 댓글', canDelete: true }),
      ],
    };
  }

  it('삭제 버튼은 canDelete가 true인 댓글에만 보인다', async () => {
    serve({ loggedIn: true, commentsAnswer: listWithOwn() });
    renderDetail();

    const [other, mine] = await screen.findAllByRole('listitem');
    expect(within(other).queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();
    expect(within(mine).getByRole('button', { name: '삭제' })).toBeInTheDocument();
  });

  it('삭제를 누르면 바로 지우지 않고 확인을 먼저 묻고, 취소하면 요청이 없다', async () => {
    serve({ loggedIn: true, commentsAnswer: listWithOwn() });
    renderDetail();

    const mine = (await screen.findAllByRole('listitem'))[1];
    fireEvent.click(within(mine).getByRole('button', { name: '삭제' }));
    expect(within(mine).getByText('이 댓글을 삭제할까요?')).toBeInTheDocument();
    expect(api.del).not.toHaveBeenCalled();

    fireEvent.click(within(mine).getByRole('button', { name: '취소' }));
    expect(within(mine).queryByText('이 댓글을 삭제할까요?')).not.toBeInTheDocument();
    expect(api.del).not.toHaveBeenCalled();
  });

  it('삭제 확인을 누르면 DELETE /api/comments/2 후 목록을 다시 불러온다', async () => {
    let answer = listWithOwn();
    serve({ loggedIn: true, commentsAnswer: () => answer });
    api.del.mockImplementation(async () => {
      answer = { items: [answer.items[0]] };
      return null;
    });
    renderDetail();

    const mine = (await screen.findAllByRole('listitem'))[1];
    fireEvent.click(within(mine).getByRole('button', { name: '삭제' }));
    fireEvent.click(within(mine).getByRole('button', { name: '삭제 확인' }));

    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(1));
    expect(screen.queryByText('내 댓글')).not.toBeInTheDocument();
    expect(api.del).toHaveBeenCalledWith('/api/comments/2');
    expect(commentCalls()).toBe(2);
  });

  it('서버가 거절하면(403) 서버 message를 보이고 목록은 그대로다', async () => {
    serve({ loggedIn: true, commentsAnswer: listWithOwn() });
    api.del.mockRejectedValue(apiError(403, 'FORBIDDEN', '권한이 없습니다.'));
    renderDetail();

    const mine = (await screen.findAllByRole('listitem'))[1];
    fireEvent.click(within(mine).getByRole('button', { name: '삭제' }));
    fireEvent.click(within(mine).getByRole('button', { name: '삭제 확인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('권한이 없습니다.');
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });

  it('삭제하는 동안 확인 버튼이 비활성화되어 두 번 보내지 않는다', async () => {
    serve({ loggedIn: true, commentsAnswer: listWithOwn() });
    let finish = () => {};
    api.del.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve(null);
        }),
    );
    renderDetail();

    const mine = (await screen.findAllByRole('listitem'))[1];
    fireEvent.click(within(mine).getByRole('button', { name: '삭제' }));
    const confirm = within(mine).getByRole('button', { name: '삭제 확인' });
    fireEvent.click(confirm);
    expect(confirm).toBeDisabled();
    fireEvent.click(confirm);
    expect(api.del).toHaveBeenCalledTimes(1);

    finish();
    await screen.findAllByRole('listitem');
  });
});
