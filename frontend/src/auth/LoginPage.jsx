import { useNavigate } from 'react-router';
import { CredentialsForm } from './CredentialsForm.jsx';
import { useAuth } from './useAuth.js';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(credentials) {
    await login(credentials);
    navigate('/', { replace: true });
  }

  return (
    <CredentialsForm
      title="로그인"
      submitLabel="로그인"
      passwordAutoComplete="current-password"
      onSubmit={handleSubmit}
    />
  );
}
