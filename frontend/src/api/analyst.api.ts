import apiClient from './client';
import {
  AlertEvidence,
  AlertResolutionRequest,
  AlertResolutionResponse,
  AlertSummary,
} from '../types/analyst';
import { CardholderTransaction } from '../types/cardholder';
import { PaginatedResponse } from '../types/common';

export const analystApi = {
  getAlerts: async (params?: {
    page?: number;
    size?: number;
    status?: string;
    priority?: string;
    claimed_by?: string;
    sla_breached?: boolean;
  }): Promise<PaginatedResponse<AlertSummary>> => {
    const response = await apiClient.get<PaginatedResponse<AlertSummary>>('/v1/analyst/alerts', {
      params,
    });
    return response.data;
  },

  claimAlert: async (alertId: string): Promise<AlertSummary> => {
    const response = await apiClient.post<AlertSummary>(`/v1/analyst/alerts/${alertId}/claim`, {});
    return response.data;
  },

  getAlert: async (alertId: string): Promise<AlertEvidence> => {
    const response = await apiClient.get<AlertEvidence>(`/v1/analyst/alerts/${alertId}`);
    return response.data;
  },

  getAlertHistory: async (
    alertId: string,
    params?: { page?: number; size?: number; from?: string; to?: string }
  ): Promise<PaginatedResponse<CardholderTransaction>> => {
    const response = await apiClient.get<PaginatedResponse<CardholderTransaction>>(
      `/v1/analyst/alerts/${alertId}/history`,
      { params }
    );
    return response.data;
  },

  resolveAlert: async (
    alertId: string,
    data: AlertResolutionRequest
  ): Promise<AlertResolutionResponse> => {
    const response = await apiClient.post<AlertResolutionResponse>(
      `/v1/analyst/alerts/${alertId}/resolutions`,
      data
    );
    return response.data;
  },
};
