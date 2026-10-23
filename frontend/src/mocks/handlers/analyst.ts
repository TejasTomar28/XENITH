import { http, HttpResponse } from 'msw';
import { AlertEvidence, AlertSummary, ManagerProposal } from '../../types/analyst';
import { CardholderTransaction } from '../../types/cardholder';
import { toWireAlert, toWireEvidence, toWireProposal } from '../wire';

export let mockAlerts: AlertSummary[] = [
  {
    alert_id: 'ALT-1001',
    txn_id: 'T200000050',
    amount: '125000.00',
    alert_priority: 'HIGH',
    status: 'OPEN',
    created_at: new Date(Date.now() - 1000 * 60 * 10).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 20).toISOString(),
    sla_breached: false,
    claimed_by: null,
    masked_card_number: 'XXXXXXXXXXXX1234',
    reason_codes: ['IMPOSSIBLE_TRAVEL', 'AMOUNT_SPIKE'],
  },
  {
    alert_id: 'ALT-1002',
    txn_id: 'T200000051',
    amount: '85000.00',
    alert_priority: 'HIGH',
    status: 'IN_REVIEW',
    created_at: new Date(Date.now() - 1000 * 60 * 25).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 5).toISOString(),
    sla_breached: false,
    claimed_by: 'analyst1',
    masked_card_number: 'XXXXXXXXXXXX5678',
    reason_codes: ['MULTIPLE_RED_FLAGS', 'HIGH_RISK_GEO', 'ODD_HOUR_HIGH_VALUE'],
  },
  {
    alert_id: 'ALT-1003',
    txn_id: 'T200000052',
    amount: '95000.00',
    alert_priority: 'HIGH',
    status: 'OPEN',
    created_at: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
    sla_due_at: new Date(Date.now() - 1000 * 60 * 15).toISOString(), // Breached!
    sla_breached: true,
    claimed_by: null,
    masked_card_number: 'XXXXXXXXXXXX1234',
    reason_codes: ['VELOCITY_BURST'],
  },
  {
    alert_id: 'ALT-1004',
    txn_id: 'T200000053',
    amount: '28000.00',
    alert_priority: 'MEDIUM',
    status: 'OPEN',
    created_at: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 210).toISOString(),
    sla_breached: false,
    claimed_by: null,
    masked_card_number: 'XXXXXXXXXXXX5678',
    reason_codes: ['NEW_MERCHANT_HIGH_VALUE'],
  },
  {
    alert_id: 'ALT-1005',
    txn_id: 'T200000054',
    amount: '42000.00',
    alert_priority: 'MEDIUM',
    status: 'IN_REVIEW',
    created_at: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 180).toISOString(),
    sla_breached: false,
    claimed_by: 'analyst1',
    masked_card_number: 'XXXXXXXXXXXX1234',
    reason_codes: ['HIGH_RISK_MCC'],
  },
  {
    alert_id: 'ALT-1006',
    txn_id: 'T200000055',
    amount: '35000.00',
    alert_priority: 'MEDIUM',
    status: 'OPEN',
    created_at: new Date(Date.now() - 1000 * 60 * 270).toISOString(),
    sla_due_at: new Date(Date.now() - 1000 * 60 * 30).toISOString(), // Breached!
    sla_breached: true,
    claimed_by: null,
    masked_card_number: 'XXXXXXXXXXXX5678',
    reason_codes: ['STEP_UP_TIMEOUT'],
  },
  {
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
  {
    alert_id: 'ALT-1008',
    txn_id: 'T200000057',
    amount: '18500.00',
    alert_priority: 'MEDIUM',
    status: 'CONFIRMED_FRAUD',
    created_at: new Date(Date.now() - 1000 * 60 * 180).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 60).toISOString(),
    sla_breached: false,
    claimed_by: 'analyst1',
    masked_card_number: 'XXXXXXXXXXXX5678',
    reason_codes: ['STEP_UP_DENIED'],
  },
  {
    alert_id: 'ALT-1009',
    txn_id: 'T200000058',
    amount: '22000.00',
    alert_priority: 'MEDIUM',
    status: 'FALSE_POSITIVE',
    created_at: new Date(Date.now() - 1000 * 60 * 200).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 40).toISOString(),
    sla_breached: false,
    claimed_by: 'analyst1',
    masked_card_number: 'XXXXXXXXXXXX1234',
    reason_codes: ['NEW_MERCHANT_HIGH_VALUE'],
  },
  {
    alert_id: 'ALT-1010',
    txn_id: 'T200000059',
    amount: '19000.00',
    alert_priority: 'MEDIUM',
    status: 'OPEN',
    created_at: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
    sla_due_at: new Date(Date.now() + 1000 * 60 * 225).toISOString(),
    sla_breached: false,
    claimed_by: null,
    masked_card_number: 'XXXXXXXXXXXX5678',
    reason_codes: ['ODD_HOUR_HIGH_VALUE'],
  },
];

export let mockProposals: ManagerProposal[] = [
  {
    proposal_id: 'PRP-5001',
    alert_id: 'ALT-1007',
    proposed_by: 'analyst1',
    proposed_at: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
    proposed_resolution: 'FALSE_POSITIVE',
    comment: 'Cardholder contacted via registered phone; verified genuine high-value jewellery purchase.',
    status: 'PENDING',
    checked_by: null,
    checked_at: null,
    checker_comment: null,
  },
];

const evidenceFor = (alert: AlertSummary): AlertEvidence => ({
  alert,
  transaction: {
    txn_id: alert.txn_id,
    card_id: 'C1007',
    masked_card_number: alert.masked_card_number || 'XXXXXXXXXXXX1234',
    txn_ts: alert.created_at,
    amount: alert.amount,
    currency: 'INR',
    merchant_id: 'M00911',
    merchant_name: 'Tanishq Jewellers Flagship',
    channel: 'POS',
    txn_city: 'Mumbai',
    txn_country: 'IN',
    status: 'BLOCKED',
  },
  card: {
    card_id: 'C1007',
    masked_card_number: alert.masked_card_number || 'XXXXXXXXXXXX1234',
    card_type: 'DEBIT',
    card_status: 'ACTIVE',
    expiry_date: '2028-12-31',
    daily_limit: '200000.00',
    international_enabled: true,
  },
  rule_decision: 'BLOCK',
  decision: 'BLOCK',
  reason_codes: alert.reason_codes || ['AMOUNT_SPIKE'],
  rule_risk_score: 85,
  features_used: {
    velocity_10m: 4,
    velocity_24h: 8,
    approved_today: '45000.00',
    avg_amount_30d: '12400.00',
    speed_kmph: 840.5,
    new_merchant_flag: true,
    distance_from_home_km: 980.2,
    card_age_days: 420,
  },
  fraud_score: 0.82,
  top_factors: [
    {
      feature: 'amount_to_avg30_ratio',
      explanation: 'Amount is 10.08 times the card 30-day average of ₹12,400.00',
      contribution: 0.35,
    },
    {
      feature: 'speed_kmph',
      explanation: 'Physical velocity of 840.5 km/h exceeds 800 km/h threshold between cities',
      contribution: 0.28,
    },
    {
      feature: 'new_merchant_flag',
      explanation: 'First transaction with this merchant in over 90 days',
      contribution: 0.19,
    },
  ],
  model_status: 'AVAILABLE',
  model_version: 'xgb-v1.2',
  second_detector_score: 0.78,
  ai_narrative:
    'Model indicates high likelihood of account takeover or compromised credentials. The card exhibited an impossible travel speed between Bangalore and Mumbai within 45 minutes, accompanied by an order of magnitude amount spike at a new high-value merchant.',
  proposal: mockProposals.find((p) => p.alert_id === alert.alert_id) || null,
});

export const analystHandlers = [
  http.get('*/api/v1/analyst/alerts', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const priority = url.searchParams.get('priority');
    const claimedBy = url.searchParams.get('claimed_by');

    let filtered = [...mockAlerts];
    if (status) {
      filtered = filtered.filter((a) => a.status === status);
    }
    if (priority) {
      filtered = filtered.filter((a) => a.alert_priority === priority);
    }
    if (claimedBy === 'me') {
      filtered = filtered.filter((a) => a.claimed_by === 'analyst1');
    }

    // Sort: HIGH priority first, then sla_due_at ascending
    filtered.sort((a, b) => {
      if (a.alert_priority !== b.alert_priority) {
        return a.alert_priority === 'HIGH' ? -1 : 1;
      }
      return new Date(a.sla_due_at).getTime() - new Date(b.sla_due_at).getTime();
    });

    return HttpResponse.json({
      items: filtered.map(toWireAlert),
      page: 0,
      size: filtered.length,
      total: filtered.length,
    });
  }),

  http.post('*/api/v1/analyst/alerts/:alert_id/claim', ({ params }) => {
    const alertId = params.alert_id as string;
    const alert = mockAlerts.find((a) => a.alert_id === alertId);

    if (!alert) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Alert not found' }),
        { status: 404 }
      );
    }

    if (alert.claimed_by && alert.claimed_by !== 'analyst1') {
      return new HttpResponse(
        JSON.stringify({
          code: 'CONFLICT',
          message: `Already claimed by another analyst (${alert.claimed_by})`,
        }),
        { status: 409 }
      );
    }

    alert.status = 'IN_REVIEW';
    alert.claimed_by = 'analyst1';

    return HttpResponse.json(toWireAlert(alert));
  }),

  http.get('*/api/v1/analyst/alerts/:alert_id', ({ params }) => {
    const alertId = params.alert_id as string;
    const alert = mockAlerts.find((a) => a.alert_id === alertId);

    if (!alert) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Alert not found' }),
        { status: 404 }
      );
    }

    return HttpResponse.json(toWireEvidence(evidenceFor(alert)));
  }),

  http.get('*/api/v1/analyst/alerts/:alert_id/history', () => {
    const historyItems: CardholderTransaction[] = [
      {
        txn_id: 'T200000049',
        card_id: 'C1007',
        txn_ts: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
        amount: '2500.00',
        currency: 'INR',
        merchant_id: 'M00102',
        merchant_name: 'Swiggy',
        channel: 'POS',
        txn_city: 'Bangalore',
        txn_country: 'IN',
        status: 'APPROVED',
        display_reason: 'Approved',
      },
      {
        txn_id: 'T200000048',
        card_id: 'C1007',
        txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 6).toISOString(),
        amount: '4500.00',
        currency: 'INR',
        merchant_id: 'M00512',
        merchant_name: 'Shell Petrol',
        channel: 'POS',
        txn_city: 'Bangalore',
        txn_country: 'IN',
        status: 'APPROVED',
        display_reason: 'Approved',
      },
      {
        txn_id: 'T200000047',
        card_id: 'C1007',
        txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
        amount: '1420.00',
        currency: 'INR',
        merchant_id: 'M00203',
        merchant_name: 'Reliance Fresh',
        channel: 'POS',
        txn_city: 'Bangalore',
        txn_country: 'IN',
        status: 'APPROVED',
        display_reason: 'Approved',
      },
      {
        txn_id: 'T200000046',
        card_id: 'C1007',
        txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
        amount: '18500.00',
        currency: 'INR',
        merchant_id: 'M00451',
        merchant_name: 'Amazon India',
        channel: 'ECOM',
        txn_city: 'Bangalore',
        txn_country: 'IN',
        status: 'APPROVED',
        display_reason: 'Approved',
      },
      {
        txn_id: 'T200000045',
        card_id: 'C1007',
        txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(),
        amount: '350.00',
        currency: 'INR',
        merchant_id: 'M00311',
        merchant_name: 'Zomato',
        channel: 'ECOM',
        txn_city: 'Bangalore',
        txn_country: 'IN',
        status: 'APPROVED',
        display_reason: 'Approved',
      },
    ];

    return HttpResponse.json({
      items: historyItems,
      page: 0,
      size: historyItems.length,
      total: historyItems.length,
    });
  }),

  http.post('*/api/v1/analyst/alerts/:alert_id/resolutions', async ({ params, request }) => {
    const alertId = params.alert_id as string;
    const body = (await request.json()) as {
      resolution: 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE';
      comment: string;
    };

    const alert = mockAlerts.find((a) => a.alert_id === alertId);
    if (!alert) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Alert not found' }),
        { status: 404 }
      );
    }

    if (body.comment.trim().length < 10) {
      return new HttpResponse(
        JSON.stringify({
          code: 'VALIDATION_ERROR',
          message: 'Comment must be at least 10 characters long',
          field_errors: [{ field: 'comment', message: 'Minimum 10 characters required' }],
        }),
        { status: 422 }
      );
    }

    const amountNum = parseFloat(alert.amount);

    if (body.resolution === 'FALSE_POSITIVE' && amountNum > 100000) {
      // High-value False Positive (> ₹100,000) requires manager approval
      alert.status = 'PENDING_MANAGER_APPROVAL';
      const newProposal: ManagerProposal = {
        proposal_id: `PRP-${Math.floor(5000 + Math.random() * 5000)}`,
        alert_id: alert.alert_id,
        proposed_by: 'analyst1',
        proposed_at: new Date().toISOString(),
        proposed_resolution: 'FALSE_POSITIVE',
        comment: body.comment,
        status: 'PENDING',
        checked_by: null,
        checked_at: null,
        checker_comment: null,
      };
      mockProposals.push(newProposal);

      return HttpResponse.json({
        alert: toWireEvidence(evidenceFor(alert)),
        proposal: toWireProposal(newProposal),
      });
    }

    alert.status = body.resolution;

    return HttpResponse.json({
      alert: toWireEvidence(evidenceFor(alert)),
      proposal: null,
    });
  }),
];
