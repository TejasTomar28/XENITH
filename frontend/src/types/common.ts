export type CardStatus = 'ACTIVE' | 'BLOCKED' | 'EXPIRED';
export type CardType = 'DEBIT' | 'CREDIT';
export type Channel = 'POS' | 'ECOM' | 'ATM' | 'CONTACTLESS';

export type TransactionStatus =
  | 'RECEIVED'
  | 'APPROVED'
  | 'STEP_UP_PENDING'
  | 'BLOCKED';

export type Decision = 'APPROVE' | 'STEP_UP' | 'BLOCK';

export type AlertPriority = 'HIGH' | 'MEDIUM';

export type AlertStatus =
  | 'OPEN'
  | 'IN_REVIEW'
  | 'CONFIRMED_FRAUD'
  | 'FALSE_POSITIVE'
  | 'PENDING_MANAGER_APPROVAL';

export type ProposalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type StepUpStatus = 'PENDING' | 'APPROVED' | 'BLOCKED' | 'EXPIRED';

export type EntityType = 'TRANSACTION' | 'ALERT' | 'CARD' | 'CONFIGURATION';

export type ModelStatus = 'AVAILABLE' | 'MODEL_UNAVAILABLE' | 'NOT_EVALUATED';

export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  field_errors?: FieldError[];
  request_id?: string;
}
