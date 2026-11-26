import { HttpErrorResponse } from '@angular/common/http';

/** The backend returns errors as {"error": "..."}; fall back to a friendly text. */
export function errorMessage(err: HttpErrorResponse): string {
  if (err.error && typeof err.error.error === 'string') {
    return err.error.error;
  }
  if (err.status === 0 || err.status >= 500) {
    return 'The service is not available right now. Please try again later.';
  }
  return 'Something went wrong. Please try again.';
}
