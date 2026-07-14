import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // No adjuntar token en las rutas de autenticacion (login/registro)
  if (req.url.includes('/api/auth/')) {
    return next(req);
  }

  const auth = inject(AuthService);
  const token = auth.token();

  const request = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      // El token ya no vale (expiro, o el backend se reinicio y lo rechaza).
      // Sin esto el usuario queda atrapado en "Cargando mapa" para siempre,
      // porque isAuthenticated sigue en true mientras haya token guardado.
      if (err.status === 401 || err.status === 403) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
