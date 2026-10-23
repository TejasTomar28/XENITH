import { CardStatus, CardType, Channel, StepUpStatus, TransactionStatus } from './common';

export interface CardView {
  card_id: string;
  masked_card_number: string;
  card_type: CardType;
  card_status: CardStatus;
  expiry_date: string;
  daily_limit: string;
  international_enabled: boolean;
}

export interface CardholderTransaction {
  txn_id: string;
  card_id: string;
  txn_ts: string;
  amount: string;
  currency: string;
  merchant_id: string;
  merchant_name: string;
  channel: Channel;
  txn_city: string;
  txn_country: string;
  status: TransactionStatus;
  display_reason: string;
}

export interface PendingStepUp {
  step_up_id: string;
  txn_id: string;
  card_id: string;
  amount: string;
  merchant_name: string;
  created_at: string;
  expires_at: string;
  status: StepUpStatus;
}

export interface StepUpResponseRequest {
  response: 'CONFIRM' | 'NOT_ME';
  otp: string;
}

export interface StepUpResponseResult {
  step_up_id: string;
  txn_id: string;
  status: TransactionStatus;
  card_status: CardStatus;
  display_reason: string;
}
