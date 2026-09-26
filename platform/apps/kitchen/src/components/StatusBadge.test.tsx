import { render, screen } from '@testing-library/react-native';
import '../i18n';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it('renders the translated label for the status', async () => {
    await render(<StatusBadge status="accepted" />);
    expect(screen.getByText('Em preparo')).toBeTruthy();
  });

  it('renders a different label per status', async () => {
    await render(<StatusBadge status="ready" />);
    expect(screen.getByText('Pronto')).toBeTruthy();
  });
});
