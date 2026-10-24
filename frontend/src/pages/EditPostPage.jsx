import { Navigate, useNavigate, useParams } from 'react-router';
import { ApiError, api } from '../api/client.js';
import PostForm from '../components/PostForm.jsx';
import { toDisplayError } from '../errors.js';
import { useApiGet } from '../hooks/useApiGet.js';
import { parsePostId } from '../postId.js';
import { isPost } from '../shapes.js';
import NotFoundPage from './NotFoundPage.jsx';

/**
 * 글 수정 (POST-04). canEdit이 false이면 폼을 보이지 않고 상세로 보낸다.
 * 이는 표시용 힌트이고, 실제 허용은 PUT 요청 때 서버가 다시 확인한다 (SEC-07).
 */
export default function EditPostPage() {
  const { id } = useParams();
  const postId = parsePostId(id);
  const navigate = useNavigate();
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
  if (!data.canEdit) {
    return <Navigate to={`/posts/${postId}`} replace />;
  }

  async function handleSubmit(values) {
    await api.put(`/api/posts/${postId}`, values);
    navigate(`/posts/${postId}`, { replace: true });
  }

  return (
    <section>
      <h1>글 수정</h1>
      <PostForm
        initialTitle={data.title}
        initialContent={data.content}
        submitLabel="수정"
        onSubmit={handleSubmit}
      />
    </section>
  );
}
