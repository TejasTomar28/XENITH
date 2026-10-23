import { EntityType } from './common';
import { AlertSummary, ManagerProposal } from './analyst';

export interface ManagerDashboardMetrics {
  from: string | null;
  to: string | null;
  transactions_by_decision: {
    APPROVE: number;
    STEP_UP: number;
    BLOCK: number;
  };
  block_rate: number;
  step_up_rate: number;
  open_alerts_by_priority: {
    HIGH: number;
    MEDIUM: number;
  };
  sla_breaches: number;
  false_positive_rate: number | null;
  top_reason_codes: {
    reason_code: string;
    count: number;
  }[];
  generated_at: string;
  total_transactions?: number;
}

export interface FalsePositiveProposalItem {
  proposal: ManagerProposal;
  alert: AlertSummary;
}

export interface ManagerProposalDecisionRequest {
  decision: 'APPROVE' | 'REJECT';
  comment: string;
}

export interface AuditEvent {
  audit_id: string;
  entity: EntityType;
  entity_id: string;
  actor: string;
  role: string;
  timestamp: string;
  action: string;
  from_status: string | null;
  to_status: string | null;
  comment: string | null;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
}

export interface RuleConfigurationSetting {
  key: string;
  value: unknown;
}

export interface RuleConfiguration {
  version: string;
  settings: RuleConfigurationSetting[];
  updated_at: string;
}

export interface RuleConfigurationUpdateRequest {
  expected_version: string;
  settings: RuleConfigurationSetting[];
  comment: string;
}
