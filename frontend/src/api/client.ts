import axios, { AxiosError } from 'axios';
import { ApiErrorResponse } from '../types/common';

const TOKEN_KEY = 'fraudshield_access_token';

let inMemoryToken: string | null = null;

export const setAuthToken = (token: string | null) => {
  inMemoryToken = token;
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
  }
};

export const getAuthToken = (): string | null => {
  if (inMemoryToken) return inMemoryToken;
  const stored = sessionStorage.getItem(TOKEN_KEY);
  if (stored) {
    inMemoryToken = stored;
    return stored;
  }
  return null;
};

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    if (error.response?.status === 401) {
      setAuthToken(null);
      // If we are not already on login page, redirect
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }

    // Enhance error with field errors if available
    if (error.response?.data) {
      const errData = error.response.data;
      const customError = new Error(errData.message || error.message);
      (customError as any).code = errData.code;
      (customError as any).field_errors = errData.field_errors;
      (customError as any).status = error.response.status;
      return Promise.reject(customError);
    }

    return Promise.reject(error);
  }
);

export default apiClient;
