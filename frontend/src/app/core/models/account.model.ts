export interface Account {
  id: number;
  accountNumber: string;
  ownerName: string;
  balance: number;
  createdAt: string;
}

export interface CreateAccountRequest {
  ownerName: string;
  initialDeposit: number | null;
}
