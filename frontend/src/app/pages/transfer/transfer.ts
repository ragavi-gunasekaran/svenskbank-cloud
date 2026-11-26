import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Account } from '../../core/models/account.model';
import { Transaction } from '../../core/models/transaction.model';
import { AccountService } from '../../core/services/account.service';
import { TransactionService } from '../../core/services/transaction.service';
import { errorMessage } from '../../core/services/http-error';

type Operation = 'deposit' | 'withdraw' | 'transfer';

@Component({
  selector: 'app-transfer',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './transfer.html',
  styleUrl: './transfer.css'
})
export class Transfer implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);

  protected readonly operations: { value: Operation; label: string }[] = [
    { value: 'transfer', label: 'Transfer' },
    { value: 'deposit', label: 'Deposit' },
    { value: 'withdraw', label: 'Withdraw' }
  ];

  protected readonly accounts = signal<Account[]>([]);
  protected readonly operation = signal<Operation>('transfer');
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorText = signal<string | null>(null);
  protected readonly saving = signal(false);

  // form fields
  protected readonly accountId = signal<number | null>(null);      // deposit / withdraw
  protected readonly fromAccountId = signal<number | null>(null);  // transfer
  protected readonly toAccountId = signal<number | null>(null);    // transfer
  protected readonly amount = signal<number | null>(null);

  ngOnInit(): void {
    this.loadAccounts();
  }

  protected selectOperation(op: Operation): void {
    this.operation.set(op);
    this.successMessage.set(null);
    this.errorText.set(null);
  }

  protected submit(): void {
    const amount = this.amount();
    if (amount === null) {
      return;
    }
    this.successMessage.set(null);
    this.errorText.set(null);
    this.saving.set(true);

    let request: Observable<Transaction>;
    switch (this.operation()) {
      case 'deposit':
        request = this.transactionService.deposit({ accountId: this.accountId()!, amount });
        break;
      case 'withdraw':
        request = this.transactionService.withdraw({ accountId: this.accountId()!, amount });
        break;
      default:
        request = this.transactionService.transfer({
          fromAccountId: this.fromAccountId()!,
          toAccountId: this.toAccountId()!,
          amount
        });
    }

    request.subscribe({
      next: (t) => {
        this.successMessage.set(`${t.type} of ${t.amount.toFixed(2)} kr completed.`);
        this.amount.set(null);
        this.saving.set(false);
        this.loadAccounts();
      },
      error: (err: HttpErrorResponse) => {
        this.errorText.set(errorMessage(err));
        this.saving.set(false);
      }
    });
  }

  private loadAccounts(): void {
    this.accountService.getAll().subscribe({
      next: (list) => this.accounts.set(list),
      error: (err: HttpErrorResponse) => this.errorText.set(errorMessage(err))
    });
  }
}
