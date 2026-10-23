import { AlertPriority, AlertStatus, Decision, ModelStatus, ProposalStatus, Channel, TransactionStatus } from './common';
import { CardView } from './cardholder';

export interface AlertSummary {
  alert_id: string;
  txn_id: string;
  amount: string;
  alert_priority: AlertPriority;
  status: AlertStatus;
  created_at: string;
  sla_due_at: string;
  sla_breached: boolean;
  claimed_by: string | null;
  masked_card_number?: string;
  reason_codes?: string[];
}

export interface FeaturesUsed {
  velocity_10m: number | null;
  velocity_24h?: number | null;
  approved_today: string | null;
  avg_amount_30d: string | null;
  speed_kmph: number | null;
  new_merchant_flag: boolean | null;
  distance_from_home_km?: number | null;
  card_age_days?: number | null;
}

export interface TopFactor {
  feature: string;
  explanation: string;
  contribution?: number;
}

export interface ManagerProposal {
  proposal_id: string;
  alert_id: string;
  proposed_by: string;
  proposed_at: string;
  proposed_resolution: 'FALSE_POSITIVE';
  comment: string;
  status: ProposalStatus;
  checked_by: string | null;
  checked_at: string | null;
  checker_comment: string | null;
}

export interface AlertEvidence {
  alert: AlertSummary;
  transaction: {
    txn_id: string;
    card_id: string;
    masked_card_number: string;
    txn_ts: string;
    amount: string;
    currency: string;
    merchant_id: string;
    merchant_name: string;
    channel: Channel;
    txn_city: string;
    txn_country: string;
    status: TransactionStatus;
  };
  card: CardView;
  rule_decision: Decision;
  decision: Decision;
  reason_codes: string[];
  rule_risk_score: number | null;
  features_used: FeaturesUsed;
  fraud_score: number | null;
  top_factors: TopFactor[];
  model_status: ModelStatus;
  model_version: string | null;
  second_detector_score?: number | null;
  ai_narrative?: string | null;
  proposal: ManagerProposal | null;
}

export interface AlertResolutionRequest {
  resolution: 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE';
  comment: string;
}

export interface AlertResolutionResponse {
  alert: AlertSummary;
  proposal: ManagerProposal | null;
}
