import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section>
      <h2>찾을 수 없음</h2>
      <p>
        <Link to="/">목록으로</Link>
      </p>
    </section>
  );
}
