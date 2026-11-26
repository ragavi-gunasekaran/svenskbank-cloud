import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Account } from '../../core/models/account.model';
import { Transaction } from '../../core/models/transaction.model';
import { AccountService } from '../../core/services/account.service';
import { TransactionService } from '../../core/services/transaction.service';
import { errorMessage } from '../../core/services/http-error';

@Component({
  selector: 'app-accounts',
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './accounts.html'
})
export class Accounts implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly selected = signal<Account | null>(null);
  protected readonly history = signal<Transaction[]>([]);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorText = signal<string | null>(null);
  protected readonly saving = signal(false);

  // form fields
  protected readonly ownerName = signal('');
  protected readonly initialDeposit = signal<number | null>(null);

  ngOnInit(): void {
    this.loadAccounts();
  }

  protected loadAccounts(): void {
    this.accountService.getAll().subscribe({
      next: (list) => this.accounts.set(list),
      error: (err: HttpErrorResponse) => this.errorText.set(errorMessage(err))
    });
  }

  protected createAccount(): void {
    this.successMessage.set(null);
    this.errorText.set(null);
    this.saving.set(true);

    this.accountService.create({ ownerName: this.ownerName(), initialDeposit: this.initialDeposit() }).subscribe({
      next: (account) => {
        this.successMessage.set(`Account ${account.accountNumber} was created for ${account.ownerName}.`);
        this.ownerName.set('');
        this.initialDeposit.set(null);
        this.saving.set(false);
        this.loadAccounts();
      },
      error: (err: HttpErrorResponse) => {
        this.errorText.set(errorMessage(err));
        this.saving.set(false);
      }
    });
  }

  protected showHistory(account: Account): void {
    this.selected.set(account);
    this.transactionService.getHistory(account.id).subscribe({
      next: (list) => this.history.set(list),
      error: (err: HttpErrorResponse) => this.errorText.set(errorMessage(err))
    });
  }

  /** Shows the account number instead of the internal id. */
  protected accountNumber(id: number | null): string {
    if (id === null) {
      return '-';
    }
    return this.accounts().find((a) => a.id === id)?.accountNumber ?? `#${id}`;
  }
}
