import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CityMapData } from '../models/city-map.model';
import { Observable, catchError, shareReplay, tap, throwError } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class MapService {
  private readonly apiBase = '/api';
  private mapCache$?: Observable<CityMapData>;

  readonly mapData = signal<CityMapData | null>(null);

  constructor(private http: HttpClient) {}

  loadMap(): Observable<CityMapData> {
    if (!this.mapCache$) {
      this.mapCache$ = this.http.get<CityMapData>(`${this.apiBase}/map`).pipe(
        tap(data => this.mapData.set(data)),
        catchError(err => {
          this.mapCache$ = undefined; // limpia la cache para permitir reintento
          return throwError(() => err);
        }),
        shareReplay(1)
      );
    }
    return this.mapCache$;
  }
}
