import { useNavigate } from 'react-router';
import { api } from '../api/client.js';
import PostForm from '../components/PostForm.jsx';

/** 글 작성 (POST-01). 성공하면 새 글 상세로 이동한다. 응답에 글 번호가 없으면 이동하지 않고 오류로 본다. */
export default function NewPostPage() {
  const navigate = useNavigate();

  async function handleSubmit(values) {
    const created = await api.post('/api/posts', values);
    if (created === null || typeof created !== 'object' || !Number.isInteger(created.id)) {
      throw new Error('unexpected response');
    }
    navigate(`/posts/${created.id}`, { replace: true });
  }

  return (
    <section>
      <h1>글쓰기</h1>
      <PostForm submitLabel="등록" onSubmit={handleSubmit} />
    </section>
  );
}
