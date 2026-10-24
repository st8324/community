import { useNavigate } from 'react-router';
import { api } from '../api/client.js';
import ConfirmDelete from './ConfirmDelete.jsx';

/** 글 삭제 (POST-05). 성공하면 목록으로 이동한다. */
export default function DeleteControl({ postId }) {
  const navigate = useNavigate();

  async function handleDelete() {
    await api.del(`/api/posts/${postId}`);
    navigate('/', { replace: true });
  }

  return <ConfirmDelete question="이 글을 삭제할까요?" onConfirm={handleDelete} />;
}
