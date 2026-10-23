import { AlertEvidence, AlertSummary, ManagerProposal } from '../types/analyst';
import { AuditEvent, FalsePositiveProposalItem } from '../types/manager';

// Serializers that turn mock view models into the flat snake_case payloads the
// Spring Boot API returns, so the api/ adapters are exercised identically in mock mode.

const lastFour = (masked?: string) => (masked ? masked.slice(-4) : null);

export const toWireAlert = (alert: AlertSummary) => ({
  alert_id: alert.alert_id,
  txn_id: alert.txn_id,
  amount: alert.amount,
  alert_priority: alert.alert_priority,
  status: alert.status,
  claimed_by: alert.claimed_by,
  sla_due_at: alert.sla_due_at,
  created_at: alert.created_at,
  sla_breached: alert.sla_breached,
  last_four: lastFour(alert.masked_card_number),
  reason_codes: alert.reason_codes || [],
});

export const toWireEvidence = (evidence: AlertEvidence) => ({
  ...toWireAlert(evidence.alert),
  closed_at: null,
  outcome_comment: null,
  card_id: evidence.transaction.card_id,
  txn_ts: evidence.transaction.txn_ts,
  currency: evidence.transaction.currency,
  merchant_id: evidence.transaction.merchant_id,
  merchant_name: evidence.transaction.merchant_name,
  channel: evidence.transaction.channel,
  txn_city: evidence.transaction.txn_city,
  txn_country: evidence.transaction.txn_country,
  transaction_status: evidence.transaction.status,
  rule_decision: evidence.rule_decision,
  original_decision: evidence.decision,
  card_type: evidence.card.card_type,
  card_status: evidence.card.card_status,
  expiry_date: evidence.card.expiry_date,
  daily_limit: evidence.card.daily_limit,
  international_enabled: evidence.card.international_enabled,
  velocity_10m: evidence.features_used.velocity_10m,
  velocity_24h: evidence.features_used.velocity_24h ?? null,
  approved_today: evidence.features_used.approved_today,
  avg_amount_30d: evidence.features_used.avg_amount_30d,
  speed_kmph: evidence.features_used.speed_kmph,
  new_merchant: evidence.features_used.new_merchant_flag,
  rule_risk_score: evidence.rule_risk_score,
  fraud_score: evidence.fraud_score,
  model_status: evidence.model_status,
  model_version: evidence.model_version,
  top_factors: evidence.top_factors,
  reason_codes: evidence.reason_codes,
});

export const toWireProposal = (proposal: ManagerProposal) => ({
  proposal_id: proposal.proposal_id,
  alert_id: proposal.alert_id,
  proposed_by: proposal.proposed_by,
  proposed_at: proposal.proposed_at,
  comment: proposal.comment,
  status: proposal.status,
  checked_by: proposal.checked_by,
  checked_at: proposal.checked_at,
  checker_comment: proposal.checker_comment,
});

export const toWireProposalListItem = ({ proposal, alert }: FalsePositiveProposalItem) => ({
  proposal_id: proposal.proposal_id,
  proposal_status: proposal.status,
  proposed_by: proposal.proposed_by,
  proposed_at: proposal.proposed_at,
  comment: proposal.comment,
  checked_by: proposal.checked_by,
  checked_at: proposal.checked_at,
  checker_comment: proposal.checker_comment,
  alert_id: alert.alert_id,
  alert_priority: alert.alert_priority,
  status: alert.status,
  txn_id: alert.txn_id,
  amount: alert.amount,
  last_four: lastFour(alert.masked_card_number),
  reason_codes: alert.reason_codes || [],
});

export const toWireAudit = (event: AuditEvent) => ({
  audit_id: event.audit_id,
  entity_type: event.entity,
  entity_id: event.entity_id,
  actor_id: event.actor,
  actor_role: event.role,
  occurred_at: event.timestamp,
  action: event.action,
  from_status: event.from_status,
  to_status: event.to_status,
  comment: event.comment,
  before_value: event.before,
  after_value: event.after,
});
