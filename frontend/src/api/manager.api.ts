import apiClient from './client';
import {
  AuditEvent,
  FalsePositiveProposalItem,
  ManagerDashboardMetrics,
  ManagerProposalDecisionRequest,
  RuleConfiguration,
  RuleConfigurationUpdateRequest,
} from '../types/manager';
import { AlertEvidence, AlertSummary, ManagerProposal } from '../types/analyst';
import { PaginatedResponse } from '../types/common';

export const managerApi = {
  getDashboard: async (params?: { from?: string; to?: string }): Promise<ManagerDashboardMetrics> => {
    const response = await apiClient.get<ManagerDashboardMetrics>('/v1/manager/dashboard', {
      params,
    });
    return response.data;
  },

  getFalsePositiveProposals: async (params?: {
    page?: number;
    size?: number;
    status?: string;
  }): Promise<PaginatedResponse<FalsePositiveProposalItem>> => {
    const response = await apiClient.get<PaginatedResponse<FalsePositiveProposalItem>>(
      '/v1/manager/false-positive-proposals',
      { params }
    );
    return response.data;
  },

  getFalsePositiveProposal: async (
    proposalId: string
  ): Promise<{ proposal: ManagerProposal; evidence: AlertEvidence }> => {
    const response = await apiClient.get<{ proposal: ManagerProposal; evidence: AlertEvidence }>(
      `/v1/manager/false-positive-proposals/${proposalId}`
    );
    return response.data;
  },

  decideFalsePositiveProposal: async (
    proposalId: string,
    data: ManagerProposalDecisionRequest
  ): Promise<{ proposal: ManagerProposal; alert: AlertSummary }> => {
    const response = await apiClient.post<{ proposal: ManagerProposal; alert: AlertSummary }>(
      `/v1/manager/false-positive-proposals/${proposalId}/decisions`,
      data
    );
    return response.data;
  },

  getAuditTrails: async (
    entityType: string,
    entityId: string,
    params?: { page?: number; size?: number }
  ): Promise<PaginatedResponse<AuditEvent>> => {
    const response = await apiClient.get<PaginatedResponse<AuditEvent>>(
      `/v1/manager/audit-trails/${entityType}/${entityId}`,
      { params }
    );
    return response.data;
  },

  exportAuditTrails: async (entityType: string, entityId: string): Promise<Blob> => {
    const response = await apiClient.get(
      `/v1/manager/audit-trails/${entityType}/${entityId}/export`,
      {
        responseType: 'blob',
      }
    );
    return response.data;
  },

  getRuleConfiguration: async (): Promise<RuleConfiguration> => {
    const response = await apiClient.get<RuleConfiguration>('/v1/manager/rule-configuration');
    return response.data;
  },

  updateRuleConfiguration: async (
    data: RuleConfigurationUpdateRequest
  ): Promise<RuleConfiguration> => {
    const response = await apiClient.put<RuleConfiguration>(
      '/v1/manager/rule-configuration',
      data
    );
    return response.data;
  },
};
