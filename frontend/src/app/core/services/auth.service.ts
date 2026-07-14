import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthRequest, AuthResponse } from '../models/auth.model';

const TOKEN_KEY = 'citysim_token';
const USERNAME_KEY = 'citysim_username';
const ZONE_KEY = 'citysim_zone';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiBase = '/api/auth';

  readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly username = signal<string | null>(localStorage.getItem(USERNAME_KEY));
  readonly zoneId = signal<string | null>(localStorage.getItem(ZONE_KEY));

  readonly isAuthenticated = computed(() => this.token() !== null);

  constructor(private http: HttpClient) {}

  register(req: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiBase}/register`, req).pipe(
      tap(res => this.persistSession(res))
    );
  }

  login(req: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiBase}/login`, req).pipe(
      tap(res => this.persistSession(res))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ZONE_KEY);
    this.token.set(null);
    this.username.set(null);
    this.zoneId.set(null);
  }

  private persistSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USERNAME_KEY, res.username);
    localStorage.setItem(ZONE_KEY, res.zoneId);
    this.token.set(res.token);
    this.username.set(res.username);
    this.zoneId.set(res.zoneId);
  }
}
