import { Link, useSearchParams } from 'react-router';
import DateTime from '../components/DateTime.jsx';
import { toDisplayError } from '../errors.js';
import { useApiGet } from '../hooks/useApiGet.js';
import { isPostList } from '../shapes.js';

/** `?page=`: 0 이상 정수가 아니면 0으로 본다 (02 문서 5.8절). 서버는 음수·문자를 400으로 거절하므로 요청 전에 정리한다. */
function parsePage(raw) {
  if (raw === null || !/^\d+$/.test(raw)) {
    return 0;
  }
  const page = Number(raw);
  return Number.isSafeInteger(page) ? page : 0;
}

/** 게시글 목록 (POST-02). 제목·작성자 표시명은 JSX 텍스트로만 넣는다 (SEC-08). */
export default function HomePage() {
  const [searchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const { loading, data, error } = useApiGet(`/api/posts?page=${page}`);

  return (
    <section>
      <h1>커뮤니티</h1>
      <PostList page={page} loading={loading} data={data} error={error} />
    </section>
  );
}

function PostList({ page, loading, data, error }) {
  if (loading) {
    return <p role="status">글을 불러오는 중…</p>;
  }
  if (error || !isPostList(data)) {
    return <p role="alert">{toDisplayError(error).message}</p>;
  }
  if (data.items.length === 0) {
    return (
      <>
        <p>아직 글이 없습니다.</p>
        {page > 0 && <Link to="/">첫 페이지로</Link>}
      </>
    );
  }
  return (
    <>
      <ul className="post-list">
        {data.items.map((post) => (
          <li key={post.id}>
            <Link to={`/posts/${post.id}`}>{post.title}</Link>
            <span className="meta">
              <span>{post.authorName}</span> <DateTime value={post.createdAt} />
            </span>
          </li>
        ))}
      </ul>
      <p className="pager">
        {page > 0 && <Link to={`/?page=${page - 1}`}>이전</Link>}
        <span>{`${page + 1} / ${data.totalPages}`}</span>
        {page + 1 < data.totalPages && <Link to={`/?page=${page + 1}`}>다음</Link>}
      </p>
    </>
  );
}
