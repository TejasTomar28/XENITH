import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SnackbarProvider } from 'notistack';
import { AlertDetail } from '../AlertDetail';
import * as authHooks from '../../../shared/hooks/useAuth';
import { analystApi } from '../../../api/analyst.api';

describe('AlertDetail Resolution Flow', () => {
  it('validates minimum 10 characters for resolution comment', async () => {
    // Mock authenticated user as analyst1
    vi.spyOn(authHooks, 'useAuth').mockReturnValue({
      user: { user_id: 'U2001', username: 'analyst1', roles: ['FRAUD_ANALYST'] },
      token: 'mock-token',
      isAuthenticated: true,
      isLoading: false,
      role: 'FRAUD_ANALYST',
      login: vi.fn(),
      logout: vi.fn(),
      hasRole: vi.fn().mockReturnValue(true),
    });

    // Mock alert evidence with IN_REVIEW and claimed by analyst1
    vi.spyOn(analystApi, 'getAlert').mockResolvedValue({
      alert: {
        alert_id: 'ALT-1002',
        txn_id: 'T200000051',
        amount: '85000.00',
        alert_priority: 'HIGH',
        status: 'IN_REVIEW',
        created_at: new Date().toISOString(),
        sla_due_at: new Date(Date.now() + 1000 * 60 * 20).toISOString(),
        sla_breached: false,
        claimed_by: 'analyst1',
      },
      transaction: {
        txn_id: 'T200000051',
        card_id: 'C1008',
        masked_card_number: 'XXXXXXXXXXXX5678',
        txn_ts: new Date().toISOString(),
        amount: '85000.00',
        currency: 'INR',
        merchant_id: 'M00999',
        merchant_name: 'Croma Electronics',
        channel: 'POS',
        txn_city: 'Delhi',
        txn_country: 'IN',
        status: 'BLOCKED',
      },
      card: {
        card_id: 'C1008',
        masked_card_number: 'XXXXXXXXXXXX5678',
        card_type: 'CREDIT',
        card_status: 'ACTIVE',
        expiry_date: '2027-06-30',
        daily_limit: '100000.00',
        international_enabled: false,
      },
      rule_decision: 'BLOCK',
      decision: 'BLOCK',
      reason_codes: ['MULTIPLE_RED_FLAGS'],
      rule_risk_score: 85,
      features_used: {
        velocity_10m: 4,
        approved_today: '45000.00',
        avg_amount_30d: '12400.00',
        speed_kmph: 840.5,
        new_merchant_flag: true,
      },
      fraud_score: 0.82,
      top_factors: [
        { feature: 'amount_to_avg30_ratio', explanation: 'Amount spike' },
      ],
      model_status: 'AVAILABLE',
      model_version: 'xgb-v1.2',
      proposal: null,
    });

    vi.spyOn(analystApi, 'getAlertHistory').mockResolvedValue({
      items: [],
      page: 0,
      size: 0,
      total: 0,
    });

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/analyst/alerts/ALT-1002']}>
            <Routes>
              <Route path="/analyst/alerts/:id" element={<AlertDetail />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>
      </QueryClientProvider>
    );

    // Wait for Close Alert button to appear
    const closeBtn = await screen.findByRole('button', { name: /Close Alert/i });
    expect(closeBtn).toBeInTheDocument();
    fireEvent.click(closeBtn);

    // Check dialog opens
    expect(screen.getByText(/Resolve & Close Fraud Alert/i)).toBeInTheDocument();

    const submitBtn = screen.getByTestId('submit-resolution-button');
    expect(submitBtn).toBeDisabled();

    // Type a short comment (< 10 chars)
    const commentInput = screen.getByTestId('resolution-comment-input');
    fireEvent.change(commentInput, { target: { value: 'Too short' } });

    expect(screen.getByText('9/10 characters')).toBeInTheDocument();
    expect(submitBtn).toBeDisabled();

    // Type a valid comment (>= 10 chars)
    fireEvent.change(commentInput, { target: { value: 'Customer confirmed transaction genuine.' } });
    expect(submitBtn).not.toBeDisabled();
  });
});
