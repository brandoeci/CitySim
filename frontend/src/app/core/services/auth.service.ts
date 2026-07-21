import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap, tap } from 'rxjs';
import { AuthRequest, AuthResponse } from '../models/auth.model';

const TOKEN_KEY = 'citysim_token';
const USERNAME_KEY = 'citysim_username';
const ZONE_KEY = 'citysim_zone';
const ROOM_KEY = 'citysim_room';

interface RoomJoinResponse {
  token: string;
  roomCode: string;
}

export interface RoomSummary {
  code: string;
  name: string;
  players: number;
  maxPlayers: number;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiBase = '/api/auth';
  private readonly roomsBase = '/api/rooms';

  readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly username = signal<string | null>(localStorage.getItem(USERNAME_KEY));
  readonly zoneId = signal<string | null>(localStorage.getItem(ZONE_KEY));
  readonly roomCode = signal<string | null>(localStorage.getItem(ROOM_KEY));

  readonly isAuthenticated = computed(() => this.token() !== null);
  readonly inRoom = computed(() => this.roomCode() !== null);

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

  /** Crea la sala y se une de una vez: necesita su propio token con el roomCode. */
  createRoom(name: string): Observable<RoomJoinResponse> {
    return this.http.post<{ code: string }>(this.roomsBase, { name }).pipe(
      switchMap(room => this.joinRoom(room.code))
    );
  }

  /** Salas existentes (para poder unirse por nombre en vez de tener que saber el código). */
  listRooms(): Observable<RoomSummary[]> {
    return this.http.get<RoomSummary[]>(this.roomsBase);
  }

  joinRoom(code: string): Observable<RoomJoinResponse> {
    return this.http.post<RoomJoinResponse>(`${this.roomsBase}/${code}/join`, {}).pipe(
      tap(res => this.persistRoom(res))
    );
  }

  leaveRoom(): void {
    const code = this.roomCode();
    if (code) {
      this.http.post(`${this.roomsBase}/${code}/leave`, {}).subscribe({ next: () => {}, error: () => {} });
    }
    localStorage.removeItem(ROOM_KEY);
    this.roomCode.set(null);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ZONE_KEY);
    localStorage.removeItem(ROOM_KEY);
    this.token.set(null);
    this.username.set(null);
    this.zoneId.set(null);
    this.roomCode.set(null);
  }

  private persistSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USERNAME_KEY, res.username);
    localStorage.setItem(ZONE_KEY, res.zoneId);
    this.token.set(res.token);
    this.username.set(res.username);
    this.zoneId.set(res.zoneId);
  }

  private persistRoom(res: RoomJoinResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(ROOM_KEY, res.roomCode);
    this.token.set(res.token);
    this.roomCode.set(res.roomCode);
  }
}
