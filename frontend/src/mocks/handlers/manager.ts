import { http, HttpResponse } from 'msw';
import {
  AuditEvent,
  FalsePositiveProposalItem,
  ManagerDashboardMetrics,
  RuleConfiguration,
} from '../../types/manager';
import { mockAlerts } from './analyst';
import { toWireAlert, toWireAudit, toWireEvidence, toWireProposal, toWireProposalListItem } from '../wire';

export let mockManagerProposalsList: FalsePositiveProposalItem[] = [
  {
    proposal: {
      proposal_id: 'PRP-5001',
      alert_id: 'ALT-1007',
      proposed_by: 'analyst1',
      proposed_at: new Date(Date.now() - 1000 * 60 * 120).toISOString(),
      proposed_resolution: 'FALSE_POSITIVE',
      comment:
        'Cardholder confirmed genuine diamond jewellery purchase at Tanishq Flagship. Verified via customer registered mobile OTP.',
      status: 'PENDING',
      checked_by: null,
      checked_at: null,
      checker_comment: null,
    },
    alert: mockAlerts[6] || {
      alert_id: 'ALT-1007',
      txn_id: 'T200000056',
      amount: '150000.00',
      alert_priority: 'HIGH',
      status: 'PENDING_MANAGER_APPROVAL',
      created_at: new Date(Date.now() - 1000 * 60 * 120).toISOString(),
      sla_due_at: new Date(Date.now() - 1000 * 60 * 90).toISOString(),
      sla_breached: false,
      claimed_by: 'analyst1',
      masked_card_number: 'XXXXXXXXXXXX1234',
      reason_codes: ['AMOUNT_SPIKE', 'HIGH_FRAUD_SCORE_MODEL'],
    },
  },
  {
    proposal: {
      proposal_id: 'PRP-5002',
      alert_id: 'ALT-1001',
      proposed_by: 'analyst2',
      proposed_at: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
      proposed_resolution: 'FALSE_POSITIVE',
      comment:
        'Customer booked international flight tickets for family vacation. Dual verification completed.',
      status: 'PENDING',
      checked_by: null,
      checked_at: null,
      checker_comment: null,
    },
    alert: {
      alert_id: 'ALT-1001',
      txn_id: 'T200000050',
      amount: '125000.00',
      alert_priority: 'HIGH',
      status: 'PENDING_MANAGER_APPROVAL',
      created_at: new Date(Date.now() - 1000 * 60 * 75).toISOString(),
      sla_due_at: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
      sla_breached: false,
      claimed_by: 'analyst2',
      masked_card_number: 'XXXXXXXXXXXX1234',
      reason_codes: ['AMOUNT_SPIKE', 'IMPOSSIBLE_TRAVEL'],
    },
  },
  {
    proposal: {
      proposal_id: 'PRP-5003',
      alert_id: 'ALT-1011',
      proposed_by: 'analyst3',
      proposed_at: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
      proposed_resolution: 'FALSE_POSITIVE',
      comment: 'Urgent hospital admission deposit payment. Verified with attending physician desk.',
      status: 'PENDING',
      checked_by: null,
      checked_at: null,
      checker_comment: null,
    },
    alert: {
      alert_id: 'ALT-1011',
      txn_id: 'T200000060',
      amount: '200000.00',
      alert_priority: 'HIGH',
      status: 'PENDING_MANAGER_APPROVAL',
      created_at: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
      sla_due_at: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
      sla_breached: false,
      claimed_by: 'analyst3',
      masked_card_number: 'XXXXXXXXXXXX5678',
      reason_codes: ['AMOUNT_SPIKE', 'HIGH_RISK_MCC'],
    },
  },
];

export let mockAuditEvents: AuditEvent[] = Array.from({ length: 20 }).map((_, idx) => ({
  audit_id: `AUD-${1000 + idx}`,
  entity: (['TRANSACTION', 'ALERT', 'CARD', 'CONFIGURATION'] as const)[idx % 4],
  entity_id: idx % 4 === 3 ? 'CONFIG-v1.0' : idx % 4 === 2 ? 'C1007' : idx % 4 === 1 ? 'ALT-1001' : 'T200000050',
  actor: idx % 3 === 0 ? 'system' : idx % 3 === 1 ? 'analyst1' : 'manager1',
  role: idx % 3 === 0 ? 'SYSTEM' : idx % 3 === 1 ? 'FRAUD_ANALYST' : 'FRAUD_RISK_MANAGER',
  timestamp: new Date(Date.now() - 1000 * 60 * 15 * (idx + 1)).toISOString(),
  action:
    idx % 4 === 3
      ? 'UPDATE_CONFIGURATION'
      : idx % 4 === 2
      ? 'CARD_STATUS_CHANGE'
      : idx % 4 === 1
      ? 'ALERT_TRANSITION'
      : 'EVALUATE_RULES',
  from_status: idx % 2 === 0 ? 'OPEN' : 'IN_REVIEW',
  to_status: idx % 2 === 0 ? 'IN_REVIEW' : 'CONFIRMED_FRAUD',
  comment: `Audit record event #${idx + 1} logged for compliance`,
  before: { status: 'PREV_STATE', index: idx },
  after: { status: 'NEW_STATE', index: idx },
}));

export let currentRuleConfig: RuleConfiguration = {
  version: 'v1.4.2',
  updated_at: new Date(Date.now() - 1000 * 60 * 60 * 24 * 3).toISOString(),
  settings: {
    velocity_window_minutes: 10, velocity_24h_window_hours: 24, velocity_burst_count: 5,
    high_velocity_min_count: 3, high_velocity_max_count: 4, amount_average_lookback_days: 30,
    amount_spike_multiplier: 5, amount_spike_minimum: 10000, amount_spike_no_history_minimum: 50000,
    impossible_travel_threshold_kmph: 800, impossible_travel_lookback_hours: 6,
    high_velocity_points: 25, amount_spike_points: 30, high_risk_geo_points: 25,
    high_risk_mcc_points: 15, odd_hour_points: 15, new_merchant_points: 20,
    high_risk_mccs: ['4829', '5967', '6051', '7995'], odd_hour_start_inclusive: 0,
    odd_hour_end_inclusive: 4, odd_hour_minimum: 20000, new_merchant_lookback_days: 90,
    new_merchant_minimum: 25000, multiple_red_flags_count: 3, alert_high_amount: 50000,
    alert_high_sla_minutes: 30, alert_medium_sla_minutes: 240, step_up_expiry_minutes: 15,
    maker_checker_limit: 100000, ai_step_up_threshold: 0.7, ai_block_threshold: 0.9,
  },
};

export const managerHandlers = [
  http.post('*/api/v1/manager/demo/authorisations', async ({ request }) => {
    const body = (await request.json()) as { txn_id: string; merchant_id: string; mcc: string };
    const block = body.merchant_id === 'MER_BLOCK';
    const stepUp = !block && ['4829', '5967', '6051', '7995'].includes(body.mcc);
    return HttpResponse.json({
      txn_id: body.txn_id,
      decision: block ? 'BLOCK' : stepUp ? 'STEP_UP' : 'APPROVE',
      reason_codes: block ? ['MERCHANT_BLACKLISTED'] : stepUp ? ['HIGH_RISK_MCC'] : [],
      rule_risk_score: block ? 100 : stepUp ? 15 : 0,
      status: block ? 'BLOCKED' : stepUp ? 'STEP_UP_PENDING' : 'APPROVED',
      alert_id: null,
      processed_at: new Date().toISOString(),
      fraud_score: null,
      top_factors: [],
      model_status: 'MODEL_UNAVAILABLE',
    });
  }),

  http.get('*/api/v1/manager/dashboard', () => {
    const dashboard: ManagerDashboardMetrics = {
      from: null,
      to: null,
      total_transactions: 1247,
      transactions_by_decision: {
        APPROVE: 1106,
        STEP_UP: 101,
        BLOCK: 40,
      },
      block_rate: 0.032, // 3.2%
      step_up_rate: 0.081, // 8.1%
      open_alerts_by_priority: {
        HIGH: 5,
        MEDIUM: 9,
      },
      sla_breaches: 3,
      false_positive_rate: 0.125, // 12.5%
      top_reason_codes: [
        { reason_code: 'IMPOSSIBLE_TRAVEL', count: 18 },
        { reason_code: 'AMOUNT_SPIKE', count: 14 },
        { reason_code: 'HIGH_RISK_GEO', count: 12 },
        { reason_code: 'NEW_MERCHANT_HIGH_VALUE', count: 9 },
        { reason_code: 'ODD_HOUR_HIGH_VALUE', count: 7 },
      ],
      generated_at: new Date().toISOString(),
    };

    return HttpResponse.json(dashboard);
  }),

  http.get('*/api/v1/manager/false-positive-proposals', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');

    let filtered = [...mockManagerProposalsList];
    if (status) {
      filtered = filtered.filter((p) => p.proposal.status === status);
    }

    return HttpResponse.json({
      items: filtered.map(toWireProposalListItem),
      page: 0,
      size: filtered.length,
      total: filtered.length,
    });
  }),

  http.get('*/api/v1/manager/false-positive-proposals/:proposal_id', ({ params }) => {
    const item = mockManagerProposalsList.find((p) => p.proposal.proposal_id === params.proposal_id);
    if (!item) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Proposal not found' }),
        { status: 404 }
      );
    }

    return HttpResponse.json({
      proposal: toWireProposal(item.proposal),
      evidence: toWireEvidence({
        alert: item.alert,
        transaction: {
          txn_id: item.alert.txn_id,
          card_id: 'C1007',
          masked_card_number: item.alert.masked_card_number || 'XXXXXXXXXXXX1234',
          txn_ts: item.alert.created_at,
          amount: item.alert.amount,
          currency: 'INR',
          merchant_id: 'M00911',
          merchant_name: 'Tanishq Jewellers',
          channel: 'POS',
          txn_city: 'Mumbai',
          txn_country: 'IN',
          status: 'BLOCKED',
        },
        card: {
          card_id: 'C1007',
          masked_card_number: item.alert.masked_card_number || 'XXXXXXXXXXXX1234',
          card_type: 'DEBIT',
          card_status: 'ACTIVE',
          expiry_date: '2028-12-31',
          daily_limit: '200000.00',
          international_enabled: true,
        },
        rule_decision: 'BLOCK',
        decision: 'BLOCK',
        reason_codes: item.alert.reason_codes || ['AMOUNT_SPIKE'],
        rule_risk_score: 80,
        features_used: {
          velocity_10m: 3,
          velocity_24h: 5,
          approved_today: '30000.00',
          avg_amount_30d: '15000.00',
          speed_kmph: 0.0,
          new_merchant_flag: true,
        },
        fraud_score: 0.75,
        top_factors: [
          { feature: 'amount_to_avg30_ratio', explanation: 'Amount is 10x the 30-day average' },
        ],
        model_status: 'AVAILABLE',
        model_version: 'xgb-v1.2',
        proposal: item.proposal,
      }),
    });
  }),

  http.post(
    '*/api/v1/manager/false-positive-proposals/:proposal_id/decisions',
    async ({ params, request }) => {
      const proposalId = params.proposal_id as string;
      const body = (await request.json()) as { decision: 'APPROVE' | 'REJECT'; comment: string };
      const item = mockManagerProposalsList.find((p) => p.proposal.proposal_id === proposalId);

      if (!item) {
        return new HttpResponse(
          JSON.stringify({ code: 'NOT_FOUND', message: 'Proposal not found' }),
          { status: 404 }
        );
      }

      // Check maker-checker rule: cannot approve own proposal
      if (item.proposal.proposed_by === 'manager1') {
        return new HttpResponse(
          JSON.stringify({
            code: 'FORBIDDEN',
            message: 'Maker-checker policy violation: You cannot approve or check your own proposal',
          }),
          { status: 403 }
        );
      }

      if (!body.comment || body.comment.trim() === '') {
        return new HttpResponse(
          JSON.stringify({
            code: 'VALIDATION_ERROR',
            message: 'Manager comment is required',
            field_errors: [{ field: 'comment', message: 'Comment cannot be blank' }],
          }),
          { status: 422 }
        );
      }

      item.proposal.status = body.decision === 'APPROVE' ? 'APPROVED' : 'REJECTED';
      item.proposal.checked_by = 'manager1';
      item.proposal.checked_at = new Date().toISOString();
      item.proposal.checker_comment = body.comment;

      item.alert.status = body.decision === 'APPROVE' ? 'FALSE_POSITIVE' : 'CONFIRMED_FRAUD';

      return HttpResponse.json({
        proposal: toWireProposal(item.proposal),
        alert: toWireAlert(item.alert),
      });
    }
  ),

  http.get('*/api/v1/manager/audit-trails/:entity_type/:entity_id', ({ params }) => {
    const entityType = params.entity_type as string;
    const entityId = params.entity_id as string;

    const filtered = mockAuditEvents.filter(
      (e) =>
        e.entity.toLowerCase() === entityType.toLowerCase() ||
        e.entity_id.toLowerCase() === entityId.toLowerCase()
    );

    return HttpResponse.json({
      items: (filtered.length > 0 ? filtered : mockAuditEvents).map(toWireAudit),
      page: 0,
      size: 20,
      total: mockAuditEvents.length,
    });
  }),

  http.get('*/api/v1/manager/audit-trails/:entity_type/:entity_id/export', () => {
    const csvContent =
      'audit_id,entity,entity_id,actor,role,timestamp,action,from_status,to_status,comment\n' +
      mockAuditEvents
        .map(
          (e) =>
            `${e.audit_id},${e.entity},${e.entity_id},${e.actor},${e.role},${e.timestamp},${e.action},${e.from_status || ''},${e.to_status || ''},"${e.comment || ''}"`
        )
        .join('\n');

    return new HttpResponse(csvContent, {
      headers: {
        'Content-Type': 'text/csv',
        'Content-Disposition': 'attachment; filename="audit_export.csv"',
      },
    });
  }),

  http.get('*/api/v1/manager/rule-configuration', () => {
    return HttpResponse.json(currentRuleConfig);
  }),

  http.put('*/api/v1/manager/rule-configuration', async ({ request }) => {
    const body = (await request.json()) as any;

    if (!body.comment || body.comment.trim() === '') {
      return new HttpResponse(
        JSON.stringify({
          code: 'VALIDATION_ERROR',
          message: 'Change comment is mandatory for rule configuration audits',
          field_errors: [{ field: 'comment', message: 'Comment cannot be blank' }],
        }),
        { status: 422 }
      );
    }

    currentRuleConfig = {
      version: `v1.${Math.floor(Math.random() * 99)}`,
      updated_at: new Date().toISOString(),
      settings: body.settings,
    };

    return HttpResponse.json(currentRuleConfig);
  }),
];
