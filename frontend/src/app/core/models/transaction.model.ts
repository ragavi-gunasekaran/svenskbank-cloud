export type TransactionType = 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER';

export interface Transaction {
  id: number;
  type: TransactionType;
  fromAccountId: number | null;
  toAccountId: number | null;
  amount: number;
  createdAt: string;
}

/** Used for both deposit and withdraw. */
export interface AmountRequest {
  accountId: number;
  amount: number;
}

export interface TransferRequest {
  fromAccountId: number;
  toAccountId: number;
  amount: number;
}
