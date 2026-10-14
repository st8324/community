import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App.jsx';

describe('App (하네스 스모크)', () => {
  it('렌더링된다', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: '커뮤니티' })).toBeInTheDocument();
  });
});
