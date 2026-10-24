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

const ME = { id: 9, displayName: 'ne***@example.com' };
const EMAIL = 'new@example.com';
const PASSWORD = `pw-${crypto.randomUUID()}`;

function renderSignup() {
  return render(
    <MemoryRouter initialEntries={['/signup']}>
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
  fireEvent.click(screen.getByRole('button', { name: '회원가입' }));
}

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  onSessionExpired.mockReset();
  onSessionExpired.mockImplementation(() => () => {});
  api.get.mockRejectedValue(
    new ApiError({
      status: 401,
      code: 'UNAUTHORIZED',
      fields: [],
      message: '로그인이 필요합니다.',
    }),
  );
});

describe('회원가입 화면 (AUTH-01)', () => {
  it('제출하면 POST /api/auth/signup을 보내고, 성공하면 바로 로그인 상태로 목록에 간다', async () => {
    api.post.mockResolvedValue(ME);
    renderSignup();

    await fillAndSubmit();

    expect(await screen.findByText('ne***@example.com')).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/api/auth/signup', {
      email: EMAIL,
      password: PASSWORD,
      rememberMe: false,
    });
    expect(screen.getByRole('heading', { name: '커뮤니티' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
  });

  it('"로그인 상태 유지"를 체크하면 rememberMe: true로 보낸다', async () => {
    api.post.mockResolvedValue(ME);
    renderSignup();

    await fillAndSubmit({ remember: true });

    await screen.findByText('ne***@example.com');
    expect(api.post).toHaveBeenCalledWith('/api/auth/signup', {
      email: EMAIL,
      password: PASSWORD,
      rememberMe: true,
    });
  });

  it('이미 가입된 이메일(409)이면 서버 message를 보인다', async () => {
    api.post.mockRejectedValue(
      new ApiError({
        status: 409,
        code: 'EMAIL_ALREADY_USED',
        fields: [],
        message: '이미 사용 중인 이메일입니다.',
      }),
    );
    renderSignup();

    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 이메일입니다.');
    expect(screen.getByRole('heading', { name: '회원가입' })).toBeInTheDocument();
  });

  it('검증 실패(400)의 fields에 있는 입력칸만 오류로 표시한다', async () => {
    api.post.mockRejectedValue(
      new ApiError({
        status: 400,
        code: 'VALIDATION_FAILED',
        fields: ['email'],
        message: '입력값을 확인해 주세요.',
      }),
    );
    renderSignup();

    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요.');
    expect(screen.getByLabelText('이메일')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('비밀번호')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('이미 로그인한 상태로 /signup에 오면 목록으로 보낸다', async () => {
    api.get.mockReset();
    api.get.mockResolvedValue(ME);
    renderSignup();

    expect(await screen.findByRole('heading', { name: '커뮤니티' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '회원가입' })).not.toBeInTheDocument();
  });
});
