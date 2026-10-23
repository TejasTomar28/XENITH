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
import { alertEvidence, alertSummary, managerProposal } from './analyst.api';
import { PaginatedResponse } from '../types/common';

const proposalItem = (raw: any): FalsePositiveProposalItem => ({
  proposal: managerProposal(raw),
  alert: {
    alert_id: String(raw.alert_id),
    txn_id: raw.txn_id,
    amount: String(raw.amount),
    alert_priority: raw.alert_priority,
    status: raw.status,
    created_at: raw.proposed_at,
    sla_due_at: raw.proposed_at,
    sla_breached: false,
    claimed_by: null,
    masked_card_number: raw.last_four ? `XXXXXXXXXXXX${String(raw.last_four).trim()}` : undefined,
    reason_codes: raw.reason_codes || [],
  },
});

export const managerApi = {
  getDashboard: async (params?: { from?: string; to?: string }): Promise<ManagerDashboardMetrics> => {
    const response = await apiClient.get<ManagerDashboardMetrics>('/v1/manager/dashboard', {
      params,
    });
    // The backend omits zero-count keys from its grouped counts; fill them so totals never become NaN.
    const raw = response.data;
    const decisions = {
      APPROVE: 0,
      STEP_UP: 0,
      BLOCK: 0,
      ...(raw.transactions_by_decision as Partial<ManagerDashboardMetrics['transactions_by_decision']>),
    };
    return {
      ...raw,
      transactions_by_decision: decisions,
      open_alerts_by_priority: {
        HIGH: 0,
        MEDIUM: 0,
        ...(raw.open_alerts_by_priority as Partial<ManagerDashboardMetrics['open_alerts_by_priority']>),
      },
      top_reason_codes: raw.top_reason_codes || [],
      total_transactions: decisions.APPROVE + decisions.STEP_UP + decisions.BLOCK,
    };
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
    return { ...response.data, items: response.data.items.map(proposalItem) };
  },

  getFalsePositiveProposal: async (
    proposalId: string
  ): Promise<{ proposal: ManagerProposal; evidence: AlertEvidence }> => {
    const response = await apiClient.get<any>(`/v1/manager/false-positive-proposals/${proposalId}`);
    const proposal = managerProposal(response.data.proposal);
    return { proposal, evidence: { ...alertEvidence(response.data.evidence), proposal } };
  },

  decideFalsePositiveProposal: async (
    proposalId: string,
    data: ManagerProposalDecisionRequest
  ): Promise<{ proposal: ManagerProposal; alert: AlertSummary }> => {
    const response = await apiClient.post<any>(
      `/v1/manager/false-positive-proposals/${proposalId}/decisions`,
      data
    );
    return { proposal: managerProposal(response.data.proposal), alert: alertSummary(response.data.alert) };
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
    return {
      ...response.data,
      items: response.data.items.map((raw: any) => ({
        audit_id: String(raw.audit_id), entity: raw.entity_type, entity_id: raw.entity_id,
        actor: raw.actor_id, role: raw.actor_role, timestamp: raw.occurred_at,
        action: raw.action, from_status: raw.from_status, to_status: raw.to_status,
        comment: raw.comment, before: raw.before_value, after: raw.after_value,
      })),
    };
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
