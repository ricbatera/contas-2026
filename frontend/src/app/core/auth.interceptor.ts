import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isAuthCall = req.url.includes('/api/auth/');

  return next(isAuthCall ? req : withToken(req, auth.accessToken)).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || isAuthCall || !auth.refreshToken) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((t) => next(withToken(req, t.accessToken))),
        catchError((e) => {
          auth.clear();
          router.navigate(['/login']);
          return throwError(() => e);
        }),
      );
    }),
  );
};
