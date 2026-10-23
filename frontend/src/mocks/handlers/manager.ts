import { http, HttpResponse } from 'msw';
import {
  AuditEvent,
  FalsePositiveProposalItem,
  ManagerDashboardMetrics,
  RuleConfiguration,
} from '../../types/manager';
import { mockAlerts, mockProposals } from './analyst';

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
  settings: [
    { key: 'velocity_10m_burst_threshold', value: 5 },
    { key: 'velocity_10m_high_threshold', value: 3 },
    { key: 'velocity_10m_window_minutes', value: 10 },
    { key: 'velocity_24h_window_hours', value: 24 },
    { key: 'amount_spike_multiplier', value: 5 },
    { key: 'amount_spike_min_amount', value: 10000 },
    { key: 'amount_no_history_min_amount', value: 50000 },
    { key: 'impossible_travel_speed_kmph', value: 800 },
    { key: 'impossible_travel_window_hours', value: 6 },
    { key: 'odd_hours_start_hour', value: 0 },
    { key: 'odd_hours_end_hour', value: 5 },
    { key: 'odd_hours_min_amount', value: 20000 },
    { key: 'new_merchant_lookback_days', value: 90 },
    { key: 'new_merchant_min_amount', value: 25000 },
    { key: 'high_risk_mcc_list', value: ['4829', '5967', '6051', '7995'] },
    { key: 'multiple_red_flags_min_triggers', value: 3 },
    { key: 'step_up_expiry_minutes', value: 15 },
    { key: 'maker_checker_threshold_amount', value: 100000 },
    { key: 'ai_step_up_threshold', value: 0.7 },
    { key: 'ai_block_threshold', value: 0.9 },
  ],
};

export const managerHandlers = [
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
      items: filtered,
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
      proposal: item.proposal,
      evidence: {
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
      },
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
        proposal: item.proposal,
        alert: item.alert,
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
      items: filtered.length > 0 ? filtered : mockAuditEvents,
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
