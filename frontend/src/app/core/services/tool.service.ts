import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export type ToolId = 'none' | 'close-road' | 'force-green' | 'speed-trap' | 'speed-boost'
  | 'green-wave' | 'traffic-bomb';

export interface SpeedOverrideView {
  edgeId: string;
  factor: number;
  expiresAtTick: number;
  placedBy: string;
}

@Injectable({ providedIn: 'root' })
export class ToolService {
  private http = inject(HttpClient);

  /** Herramienta seleccionada en la barra. */
  readonly activeTool = signal<ToolId>('none');

  /** Vias cerradas: edgeId -> username que la cerro. */
  readonly blockedEdges = signal<Record<string, string>>({});

  /** Vias con multiplicador de velocidad activo (REDUCTOR/TURBO): edgeId -> override. */
  readonly speedOverrides = signal<Record<string, SpeedOverrideView>>({});

  /** Ultimo mensaje de la herramienta (exito o error). */
  readonly feedback = signal<string>('');

  /** ESCUDO DE DISTRITO: epoch ms hasta el que el brillo dorado sigue activo (0 = inactivo). */
  readonly shieldActiveUntil = signal<number>(0);

  /** ESCUDO DE DISTRITO: epoch ms hasta el que el boton sigue en cooldown (0 = disponible). */
  readonly shieldCooldownUntil = signal<number>(0);

  selectTool(tool: ToolId): void {
    this.activeTool.set(this.activeTool() === tool ? 'none' : tool);
    this.feedback.set('');
  }

  closeEdge(edgeId: string): Observable<unknown> {
    return this.http.post('/api/tools/close-edge', { edgeId }).pipe(
      tap({
        next: () => {
          this.feedback.set('Via cerrada');
          this.refresh();
        },
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo cerrar')
      })
    );
  }

  openEdge(edgeId: string): Observable<unknown> {
    return this.http.post('/api/tools/open-edge', { edgeId }).pipe(
      tap({
        next: () => {
          this.feedback.set('Via reabierta');
          this.refresh();
        },
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo reabrir')
      })
    );
  }

  forceGreen(intersectionId: string, horizontal: boolean): Observable<unknown> {
    return this.http.post('/api/tools/force-green', { intersectionId, horizontal }).pipe(
      tap({
        next: () => this.feedback.set('Semáforo forzado'),
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo forzar el semáforo')
      })
    );
  }

  greenWave(intersectionId: string, horizontal: boolean): Observable<unknown> {
    return this.http.post('/api/tools/green-wave', { intersectionId, horizontal }).pipe(
      tap({
        next: () => this.feedback.set('Ola verde en marcha'),
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo lanzar la ola verde')
      })
    );
  }

  trafficBomb(targetX: number, targetY: number): Observable<{ carsSpawned: number }> {
    return this.http.post<{ carsSpawned: number }>('/api/tools/traffic-bomb', { targetX, targetY }).pipe(
      tap({
        next: (r) => this.feedback.set(`Lluvia de trafico: ${r.carsSpawned} carros en camino`),
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo lanzar la lluvia de trafico')
      })
    );
  }

  districtShield(): Observable<{ durationSeconds: number, cooldownSeconds: number }> {
    return this.http.post<{ durationSeconds: number, cooldownSeconds: number }>('/api/tools/district-shield', {}).pipe(
      tap({
        next: (r) => {
          this.feedback.set('Escudo de distrito activado');
          this.shieldActiveUntil.set(Date.now() + r.durationSeconds * 1000);
          this.shieldCooldownUntil.set(Date.now() + r.cooldownSeconds * 1000);
        },
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo activar el escudo')
      })
    );
  }

  speedTrap(edgeId: string): Observable<{ durationSeconds: number }> {
    return this.http.post<{ durationSeconds: number }>('/api/tools/speed-trap', { edgeId }).pipe(
      tap({
        next: (r) => { this.feedback.set('Reductor colocado'); this.refreshThenAt(r.durationSeconds); },
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo colocar el reductor')
      })
    );
  }

  speedBoost(edgeId: string): Observable<{ durationSeconds: number }> {
    return this.http.post<{ durationSeconds: number }>('/api/tools/speed-boost', { edgeId }).pipe(
      tap({
        next: (r) => { this.feedback.set('Turbo activado'); this.refreshThenAt(r.durationSeconds); },
        error: (e) => this.feedback.set(e.error?.error ?? 'No se pudo activar el turbo')
      })
    );
  }

  /** Refresca ahora (para ver el override recien puesto) y otra vez cuando expira, para quitarlo del mapa sin polling continuo. */
  private refreshThenAt(durationSeconds: number): void {
    this.refresh();
    setTimeout(() => this.refresh(), (durationSeconds + 1) * 1000);
  }

  refresh(): void {
    this.http.get<{ blocked: Record<string, string> }>('/api/tools/blocked-edges')
      .subscribe({
        next: (r) => this.blockedEdges.set(r.blocked ?? {}),
        error: () => {}
      });
    this.http.get<{ overrides: Record<string, SpeedOverrideView> }>('/api/tools/speed-overrides')
      .subscribe({
        next: (r) => this.speedOverrides.set(r.overrides ?? {}),
        error: () => {}
      });
  }

  isBlocked(edgeId: string): boolean {
    return edgeId in this.blockedEdges();
  }
}
