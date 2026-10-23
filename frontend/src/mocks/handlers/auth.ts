import { http, HttpResponse } from 'msw';
import { AuthResponse, Identity } from '../../types/auth';

let currentUser: Identity = {
  user_id: 'U1001',
  username: 'cardholder1',
  roles: ['CARDHOLDER'],
};

export const authHandlers = [
  http.post('*/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username: string; password?: string };
    const username = body.username.toLowerCase();

    let roles: ('CARDHOLDER' | 'FRAUD_ANALYST' | 'FRAUD_RISK_MANAGER')[] = ['CARDHOLDER'];
    let userId = 'U1001';

    if (username.includes('analyst')) {
      roles = ['FRAUD_ANALYST'];
      userId = 'U2001';
    } else if (username.includes('manager')) {
      roles = ['FRAUD_RISK_MANAGER'];
      userId = 'U3001';
    }

    currentUser = {
      user_id: userId,
      username: body.username,
      roles,
    };

    const response: AuthResponse = {
      access_token: `mock-jwt-token-for-${username}`,
      token_type: 'Bearer',
      expires_in: 3600,
      user: currentUser,
    };

    return HttpResponse.json(response);
  }),

  http.post('*/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as any;
    currentUser = {
      user_id: `U${Math.floor(1000 + Math.random() * 9000)}`,
      username: body.username,
      roles: ['CARDHOLDER'],
    };

    const response: AuthResponse = {
      access_token: `mock-jwt-token-for-${body.username}`,
      token_type: 'Bearer',
      expires_in: 3600,
      user: currentUser,
    };

    return HttpResponse.json(response);
  }),

  http.get('*/api/v1/auth/me', () => {
    return HttpResponse.json(currentUser);
  }),
];
