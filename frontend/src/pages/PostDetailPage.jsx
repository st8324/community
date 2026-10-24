import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client.js';
import DateTime from '../components/DateTime.jsx';
import DeleteControl from '../components/DeleteControl.jsx';
import { toDisplayError } from '../errors.js';
import { useApiGet } from '../hooks/useApiGet.js';
import { parsePostId } from '../postId.js';
import { isPost } from '../shapes.js';
import NotFoundPage from './NotFoundPage.jsx';

/**
 * 게시글 상세 (POST-03). 제목·본문·표시명은 JSX 텍스트로만 넣고, 줄바꿈은 CSS(white-space: pre-wrap)로 보여 준다 (SEC-08).
 * 수정·삭제 버튼은 서버가 계산한 canEdit·canDelete가 true일 때만 보인다. 이는 표시용 힌트일 뿐이고
 * 실제 허용은 요청 시 서버가 다시 확인한다 (SEC-07).
 */
export default function PostDetailPage() {
  const { id } = useParams();
  const postId = parsePostId(id);
  const { loading, data, error } = useApiGet(postId === null ? null : `/api/posts/${postId}`);

  if (postId === null) {
    return <NotFoundPage />;
  }
  if (loading) {
    return <p role="status">글을 불러오는 중…</p>;
  }
  if (error instanceof ApiError && error.status === 404) {
    return <NotFoundPage />;
  }
  if (error || !isPost(data)) {
    return <p role="alert">{toDisplayError(error).message}</p>;
  }

  return (
    <article>
      <h1>{data.title}</h1>
      <p className="meta">
        <span>{data.authorName}</span> <DateTime value={data.createdAt} />
        {data.updatedAt !== null && (
          <span>
            {' '}
            (수정됨 <DateTime value={data.updatedAt} />)
          </span>
        )}
      </p>
      <p className="post-content">{data.content}</p>
      <p className="actions">
        <Link to="/">목록으로</Link>
        {data.canEdit && <Link to={`/posts/${postId}/edit`}>수정</Link>}
        {data.canDelete && <DeleteControl postId={postId} />}
      </p>
    </article>
  );
}
