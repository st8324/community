import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <section>
      <h1>찾을 수 없음</h1>
      <p>
        <Link to="/">목록으로</Link>
      </p>
    </section>
  );
}
