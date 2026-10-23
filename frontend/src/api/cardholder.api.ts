import apiClient from './client';
import {
  CardView,
  CardholderTransaction,
  PendingStepUp,
  StepUpResponseRequest,
  StepUpResponseResult,
} from '../types/cardholder';
import { PaginatedResponse } from '../types/common';

export const cardholderApi = {
  getCards: async (params?: { page?: number; size?: number }): Promise<PaginatedResponse<CardView>> => {
    const response = await apiClient.get<PaginatedResponse<CardView>>('/v1/me/cards', { params });
    return response.data;
  },

  blockCard: async (cardId: string): Promise<CardView> => {
    const response = await apiClient.post<CardView>(`/v1/me/cards/${cardId}/block`, {});
    return response.data;
  },

  getTransactions: async (params?: {
    page?: number;
    size?: number;
    card_id?: string;
    from?: string;
    to?: string;
    status?: string;
  }): Promise<PaginatedResponse<CardholderTransaction>> => {
    const response = await apiClient.get<PaginatedResponse<CardholderTransaction>>('/v1/me/transactions', {
      params,
    });
    return response.data;
  },

  getTransaction: async (txnId: string): Promise<CardholderTransaction> => {
    const response = await apiClient.get<CardholderTransaction>(`/v1/me/transactions/${txnId}`);
    return response.data;
  },

  getStepUps: async (params?: { page?: number; size?: number }): Promise<PaginatedResponse<PendingStepUp>> => {
    const response = await apiClient.get<PaginatedResponse<PendingStepUp>>('/v1/me/step-ups', { params });
    return response.data;
  },

  respondToStepUp: async (
    stepUpId: string,
    data: StepUpResponseRequest
  ): Promise<StepUpResponseResult> => {
    const response = await apiClient.post<StepUpResponseResult>(
      `/v1/me/step-ups/${stepUpId}/responses`,
      data
    );
    return response.data;
  },
};
