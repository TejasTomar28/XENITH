import apiClient, { setAuthToken } from './client';
import { AuthResponse, Identity, LoginRequest, RegisterRequest } from '../types/auth';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/v1/auth/login', credentials);
    if (response.data?.access_token) {
      setAuthToken(response.data.access_token);
    }
    return response.data;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    // Staff cannot self-register, but cardholders can register via mock/server
    const response = await apiClient.post<AuthResponse>('/v1/auth/register', data);
    if (response.data?.access_token) {
      setAuthToken(response.data.access_token);
    }
    return response.data;
  },

  getMe: async (): Promise<Identity> => {
    const response = await apiClient.get<Identity>('/v1/auth/me');
    return response.data;
  },

  logout: () => {
    setAuthToken(null);
  },
};
