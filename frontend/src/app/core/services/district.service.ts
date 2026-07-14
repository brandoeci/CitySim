import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, interval } from 'rxjs';
import { District, PresenceState } from '../models/district.model';

/** Cada cuanto se renueva el heartbeat. El TTL del backend es 10s. */
const PING_INTERVAL_MS = 3000;

@Injectable({ providedIn: 'root' })
export class DistrictService {
  private http = inject(HttpClient);

  readonly districts = signal<District[]>([]);
  readonly myDistrictIndex = signal<number>(-1);
  readonly activeUsers = signal<number>(0);
  readonly maxUsers = signal<number>(6);

  /** El distrito que administra este usuario, o null. */
  readonly myDistrict = computed<District | null>(() => {
    const idx = this.myDistrictIndex();
    if (idx < 0) return null;
    return this.districts().find(d => d.index === idx) ?? null;
  });

  /** Mapa zoneId -> indice del distrito que la administra. */
  readonly zoneToDistrict = computed<Map<string, number>>(() => {
    const map = new Map<string, number>();
    for (const d of this.districts()) {
      for (const zoneId of d.zoneIds) {
        map.set(zoneId, d.index);
      }
    }
    return map;
  });

  private pingSub?: Subscription;

  /** Arranca el heartbeat. Mientras siga pingeando, el usuario cuenta como activo. */
  start(): void {
    if (this.pingSub) return;
    this.ping();
    this.pingSub = interval(PING_INTERVAL_MS).subscribe(() => this.ping());
  }

  /** Detiene el heartbeat y libera el distrito de inmediato. */
  stop(): void {
    this.pingSub?.unsubscribe();
    this.pingSub = undefined;
    this.http.post('/api/presence/leave', {}).subscribe({
      next: () => {},
      error: () => {}
    });
  }

  private ping(): void {
    this.http.post<PresenceState>('/api/presence/ping', {}).subscribe({
      next: (state) => {
        this.districts.set(state.districts ?? []);
        this.myDistrictIndex.set(state.myDistrictIndex ?? -1);
        this.activeUsers.set(state.activeUsers ?? 0);
        this.maxUsers.set(state.maxUsers ?? 6);
      },
      error: () => {
        // Si el ping falla no borramos el estado: puede ser un fallo puntual
        // de red y el TTL del backend aun no ha expirado.
      }
    });
  }
}
