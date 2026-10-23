export type UserRole = 'CARDHOLDER' | 'FRAUD_ANALYST' | 'FRAUD_RISK_MANAGER';

export interface Identity {
  user_id: string;
  username: string;
  roles: UserRole[];
}

export interface LoginRequest {
  username: string;
  password?: string;
}

export interface RegisterRequest {
  username: string;
  password?: string;
  fullName: string;
  email: string;
  mobile: string;
  last4Digits: string;
}

export interface AuthResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  user: Identity;
}
