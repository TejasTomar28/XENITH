import { http, HttpResponse } from 'msw';
import { CardView, CardholderTransaction, PendingStepUp } from '../../types/cardholder';

export let mockCards: CardView[] = [
  {
    card_id: 'C1007',
    masked_card_number: 'XXXXXXXXXXXX1234',
    card_type: 'DEBIT',
    card_status: 'ACTIVE',
    expiry_date: '2028-12-31',
    daily_limit: '50000.00',
    international_enabled: true,
  },
  {
    card_id: 'C1008',
    masked_card_number: 'XXXXXXXXXXXX5678',
    card_type: 'CREDIT',
    card_status: 'ACTIVE',
    expiry_date: '2027-06-30',
    daily_limit: '100000.00',
    international_enabled: false,
  },
];

export let mockTransactions: CardholderTransaction[] = [
  {
    txn_id: 'T200000020',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
    amount: '15000.00',
    currency: 'INR',
    merchant_id: 'M00451',
    merchant_name: 'Amazon India',
    channel: 'ECOM',
    txn_city: 'Mumbai',
    txn_country: 'IN',
    status: 'STEP_UP_PENDING',
    display_reason: 'Verification in progress via OTP',
  },
  {
    txn_id: 'T200000019',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    amount: '1250.00',
    currency: 'INR',
    merchant_id: 'M00102',
    merchant_name: 'Swiggy',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000018',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(),
    amount: '3499.00',
    currency: 'INR',
    merchant_id: 'M00203',
    merchant_name: 'Reliance Retail',
    channel: 'POS',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000017',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 12).toISOString(),
    amount: '68500.00',
    currency: 'INR',
    merchant_id: 'M00999',
    merchant_name: 'Croma Electronics',
    channel: 'POS',
    txn_city: 'Delhi',
    txn_country: 'IN',
    status: 'BLOCKED',
    display_reason: 'Declined by bank security policy',
  },
  {
    txn_id: 'T200000016',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
    amount: '850.00',
    currency: 'INR',
    merchant_id: 'M00311',
    merchant_name: 'Zomato',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000015',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 28).toISOString(),
    amount: '4500.00',
    currency: 'INR',
    merchant_id: 'M00512',
    merchant_name: 'Shell Petrol Pump',
    channel: 'POS',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000014',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
    amount: '12999.00',
    currency: 'INR',
    merchant_id: 'M00452',
    merchant_name: 'Flipkart Internet',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000013',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 60).toISOString(),
    amount: '5000.00',
    currency: 'INR',
    merchant_id: 'M00781',
    merchant_name: 'Kaveri Bank ATM',
    channel: 'ATM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000012',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(),
    amount: '750.00',
    currency: 'INR',
    merchant_id: 'M00102',
    merchant_name: 'Swiggy',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000011',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 85).toISOString(),
    amount: '1850.00',
    currency: 'INR',
    merchant_id: 'M00677',
    merchant_name: 'Cafe Coffee Day',
    channel: 'CONTACTLESS',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000010',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 96).toISOString(),
    amount: '22400.00',
    currency: 'INR',
    merchant_id: 'M00888',
    merchant_name: 'MakeMyTrip India',
    channel: 'ECOM',
    txn_city: 'Delhi',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000009',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 110).toISOString(),
    amount: '350.00',
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
    txn_id: 'T200000008',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 120).toISOString(),
    amount: '95000.00',
    currency: 'INR',
    merchant_id: 'M00911',
    merchant_name: 'Tanishq Jewellers',
    channel: 'POS',
    txn_city: 'Mumbai',
    txn_country: 'IN',
    status: 'BLOCKED',
    display_reason: 'Declined by bank security policy',
  },
  {
    txn_id: 'T200000007',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 135).toISOString(),
    amount: '1500.00',
    currency: 'INR',
    merchant_id: 'M00444',
    merchant_name: 'BookMyShow',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000006',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 150).toISOString(),
    amount: '420.00',
    currency: 'INR',
    merchant_id: 'M00311',
    merchant_name: 'Zomato',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000005',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 165).toISOString(),
    amount: '18000.00',
    currency: 'INR',
    merchant_id: 'M00451',
    merchant_name: 'Amazon India',
    channel: 'ECOM',
    txn_city: 'Hyderabad',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000004',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 180).toISOString(),
    amount: '6200.00',
    currency: 'INR',
    merchant_id: 'M00203',
    merchant_name: 'Decathlon Sports',
    channel: 'POS',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000003',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 200).toISOString(),
    amount: '2000.00',
    currency: 'INR',
    merchant_id: 'M00512',
    merchant_name: 'Indian Oil Petrol',
    channel: 'POS',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000002',
    card_id: 'C1008',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 220).toISOString(),
    amount: '11500.00',
    currency: 'INR',
    merchant_id: 'M00888',
    merchant_name: 'Air India',
    channel: 'ECOM',
    txn_city: 'Chennai',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
  {
    txn_id: 'T200000001',
    card_id: 'C1007',
    txn_ts: new Date(Date.now() - 1000 * 60 * 60 * 240).toISOString(),
    amount: '999.00',
    currency: 'INR',
    merchant_id: 'M00452',
    merchant_name: 'Myntra Fashion',
    channel: 'ECOM',
    txn_city: 'Bangalore',
    txn_country: 'IN',
    status: 'APPROVED',
    display_reason: 'Approved',
  },
];

export let mockStepUps: PendingStepUp[] = [
  {
    step_up_id: 'SU-9001',
    txn_id: 'T200000020',
    card_id: 'C1007',
    amount: '15000.00',
    merchant_name: 'Amazon India',
    created_at: new Date(Date.now() - 1000 * 60 * 3).toISOString(),
    expires_at: new Date(Date.now() + 1000 * 60 * 12).toISOString(),
    status: 'PENDING',
  },
];

export const cardholderHandlers = [
  http.get('*/api/v1/me/cards', () => {
    return HttpResponse.json({
      items: mockCards,
      page: 0,
      size: 20,
      total: mockCards.length,
    });
  }),

  http.post('*/api/v1/me/cards/:card_id/block', ({ params }) => {
    const cardId = params.card_id as string;
    const card = mockCards.find((c) => c.card_id === cardId);
    if (!card) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Card not found' }),
        { status: 404 }
      );
    }
    card.card_status = 'BLOCKED';
    return HttpResponse.json(card);
  }),

  http.get('*/api/v1/me/transactions', ({ request }) => {
    const url = new URL(request.url);
    const cardId = url.searchParams.get('card_id');
    const status = url.searchParams.get('status');

    let filtered = [...mockTransactions];
    if (cardId) {
      filtered = filtered.filter((t) => t.card_id === cardId);
    }
    if (status) {
      filtered = filtered.filter((t) => t.status === status);
    }

    return HttpResponse.json({
      items: filtered,
      page: 0,
      size: filtered.length,
      total: filtered.length,
    });
  }),

  http.get('*/api/v1/me/transactions/:txn_id', ({ params }) => {
    const txn = mockTransactions.find((t) => t.txn_id === params.txn_id);
    if (!txn) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Transaction not found' }),
        { status: 404 }
      );
    }
    return HttpResponse.json(txn);
  }),

  http.get('*/api/v1/me/step-ups', () => {
    return HttpResponse.json({
      items: mockStepUps,
      page: 0,
      size: 20,
      total: mockStepUps.length,
    });
  }),

  http.post('*/api/v1/me/step-ups/:step_up_id/responses', async ({ params, request }) => {
    const stepUpId = params.step_up_id as string;
    const body = (await request.json()) as { response: 'CONFIRM' | 'NOT_ME'; otp: string };
    const stepUp = mockStepUps.find((s) => s.step_up_id === stepUpId);

    if (!stepUp) {
      return new HttpResponse(
        JSON.stringify({ code: 'NOT_FOUND', message: 'Step-up request not found or expired' }),
        { status: 404 }
      );
    }

    // Remove from pending step-ups
    mockStepUps = mockStepUps.filter((s) => s.step_up_id !== stepUpId);

    // Update transaction
    const txn = mockTransactions.find((t) => t.txn_id === stepUp.txn_id);

    if (body.response === 'CONFIRM') {
      if (txn) {
        txn.status = 'APPROVED';
        txn.display_reason = 'Transaction confirmed by cardholder';
      }
      return HttpResponse.json({
        step_up_id: stepUpId,
        txn_id: stepUp.txn_id,
        status: 'APPROVED',
        card_status: 'ACTIVE',
        display_reason: 'Transaction confirmed by cardholder',
      });
    } else {
      // NOT_ME: blocks transaction and card
      if (txn) {
        txn.status = 'BLOCKED';
        txn.display_reason = 'Declined by cardholder via NOT_ME';
      }
      const card = mockCards.find((c) => c.card_id === stepUp.card_id);
      if (card) {
        card.card_status = 'BLOCKED';
      }
      return HttpResponse.json({
        step_up_id: stepUpId,
        txn_id: stepUp.txn_id,
        status: 'BLOCKED',
        card_status: 'BLOCKED',
        display_reason: 'Transaction denied and card blocked permanently',
      });
    }
  }),
];
