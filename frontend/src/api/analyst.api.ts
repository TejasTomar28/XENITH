import apiClient from './client';
import {
  AlertEvidence,
  AlertResolutionRequest,
  AlertResolutionResponse,
  AlertSummary,
  ManagerProposal,
} from '../types/analyst';
import { CardholderTransaction } from '../types/cardholder';
import { PaginatedResponse } from '../types/common';

export const alertSummary = (raw: any): AlertSummary => ({
  alert_id: String(raw.alert_id),
  txn_id: String(raw.txn_id),
  amount: String(raw.amount),
  alert_priority: raw.alert_priority || raw.priority,
  status: raw.status,
  created_at: raw.created_at,
  sla_due_at: raw.sla_due_at,
  sla_breached: Boolean(raw.sla_breached),
  claimed_by: raw.claimed_by || null,
  masked_card_number: raw.last_four ? `XXXXXXXXXXXX${String(raw.last_four).trim()}` : undefined,
  reason_codes: raw.reason_codes || [],
});

export const managerProposal = (raw: any): ManagerProposal => ({
  proposal_id: String(raw.proposal_id),
  alert_id: String(raw.alert_id),
  proposed_by: raw.proposed_by,
  proposed_at: raw.proposed_at,
  proposed_resolution: 'FALSE_POSITIVE',
  comment: raw.comment,
  status: raw.proposal_status || raw.status,
  checked_by: raw.checked_by || null,
  checked_at: raw.checked_at || null,
  checker_comment: raw.checker_comment || null,
});

export const alertEvidence = (raw: any): AlertEvidence => ({
  alert: alertSummary(raw),
  transaction: {
    txn_id: raw.txn_id,
    card_id: raw.card_id,
    masked_card_number: `XXXXXXXXXXXX${String(raw.last_four || '').trim()}`,
    txn_ts: raw.txn_ts,
    amount: String(raw.amount),
    currency: raw.currency,
    merchant_id: raw.merchant_id,
    merchant_name: raw.merchant_name,
    channel: raw.channel,
    txn_city: raw.txn_city,
    txn_country: raw.txn_country,
    status: raw.transaction_status,
  },
  card: {
    card_id: raw.card_id,
    masked_card_number: `XXXXXXXXXXXX${String(raw.last_four || '').trim()}`,
    card_type: raw.card_type,
    card_status: raw.card_status,
    expiry_date: raw.expiry_date,
    daily_limit: String(raw.daily_limit),
    international_enabled: Boolean(raw.international_enabled),
  },
  rule_decision: raw.rule_decision,
  decision: raw.original_decision,
  reason_codes: raw.reason_codes || [],
  rule_risk_score: raw.rule_risk_score,
  features_used: {
    velocity_10m: raw.velocity_10m,
    velocity_24h: raw.velocity_24h,
    approved_today: raw.approved_today == null ? null : String(raw.approved_today),
    avg_amount_30d: raw.avg_amount_30d == null ? null : String(raw.avg_amount_30d),
    speed_kmph: raw.speed_kmph,
    new_merchant_flag: raw.new_merchant,
  },
  fraud_score: raw.fraud_score,
  top_factors: raw.top_factors || [],
  model_status: raw.model_status || 'NOT_EVALUATED',
  model_version: raw.model_version || null,
  proposal: null,
});

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
    return { ...response.data, items: response.data.items.map(alertSummary) };
  },

  claimAlert: async (alertId: string): Promise<AlertSummary> => {
    const response = await apiClient.post<AlertSummary>(`/v1/analyst/alerts/${alertId}/claim`, {});
    return alertSummary(response.data);
  },

  getAlert: async (alertId: string): Promise<AlertEvidence> => {
    const response = await apiClient.get<AlertEvidence>(`/v1/analyst/alerts/${alertId}`);
    return alertEvidence(response.data);
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
    const raw = response.data as any;
    return {
      alert: alertSummary(raw.alert),
      proposal: raw.proposal ? managerProposal(raw.proposal) : null,
    };
  },
};
