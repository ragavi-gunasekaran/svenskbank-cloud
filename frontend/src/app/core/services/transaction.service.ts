import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AmountRequest, Transaction, TransferRequest } from '../models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/transactions';

  deposit(request: AmountRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.baseUrl}/deposit`, request);
  }

  withdraw(request: AmountRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.baseUrl}/withdraw`, request);
  }

  transfer(request: TransferRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.baseUrl}/transfer`, request);
  }

  getHistory(accountId: number): Observable<Transaction[]> {
    const params = new HttpParams().set('accountId', accountId);
    return this.http.get<Transaction[]>(this.baseUrl, { params });
  }
}
