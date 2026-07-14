import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CarState, SimulationFrame } from '../models/car-state.model';
import { Observable, Subject } from 'rxjs';

export interface StatusResponse {
  running: boolean;
  tick: number;
  totalCars: number;
  fps: number;
  ownedZones: number;
  localCars: number;
  instanceId: string;
}

export interface SpawnResponse {
  spawned: number;
  total: number;
}

@Injectable({ providedIn: 'root' })
export class SimulationService {
  private readonly apiBase = '/api/simulation';

  // Car state cache: carId -> CarState
  private carMap = new Map<string, CarState>();

  readonly carCount = signal(0);
  readonly fps      = signal(0);
  readonly running  = signal(false);
  readonly frameId  = signal(0);
  readonly clear$   = new Subject<void>();

  constructor(private http: HttpClient) {}

  applyFrame(frame: SimulationFrame): void {
    for (const delta of frame.deltas) {
      if (delta.status === 'ARRIVED') {
        this.carMap.delete(delta.carId);
      } else {
        this.carMap.set(delta.carId, {
          carId: delta.carId,
          x: delta.x,
          y: delta.y,
          heading: delta.heading,
          status: delta.status,
          color: delta.color
        });
      }
    }
    for (const id of frame.removedCarIds) {
      this.carMap.delete(id);
    }
    this.carCount.set(frame.totalCars);
    this.fps.set(Math.round(frame.fps));
    this.frameId.set(frame.frameId);
  }

  getCars(): CarState[] {
    return Array.from(this.carMap.values());
  }

  getCarMap(): Map<string, CarState> {
    return this.carMap;
  }

  clearCars(): void {
    this.carMap.clear();
    this.carCount.set(0);
    this.clear$.next();
  }

  start(): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.apiBase}/start`, {});
  }

  stop(): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.apiBase}/stop`, {});
  }

  spawnCars(count: number): Observable<SpawnResponse> {
    return this.http.post<SpawnResponse>(`${this.apiBase}/cars`, { count });
  }

  removeAllCars(): Observable<{ removed: number }> {
    return this.http.delete<{ removed: number }>(`${this.apiBase}/cars`);
  }

  getStatus(): Observable<StatusResponse> {
    return this.http.get<StatusResponse>(`${this.apiBase}/status`);
  }
}
