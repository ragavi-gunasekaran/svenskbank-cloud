import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { About } from './pages/about/about';
import { Accounts } from './pages/accounts/accounts';
import { Transfer } from './pages/transfer/transfer';

export const routes: Routes = [
  { path: '', component: Home, title: 'SvenskBank' },
  { path: 'about', component: About, title: 'About | SvenskBank' },
  { path: 'accounts', component: Accounts, title: 'Accounts | SvenskBank' },
  { path: 'transfer', component: Transfer, title: 'Payments | SvenskBank' },
  { path: '**', redirectTo: '' }
];
