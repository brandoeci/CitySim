import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';
import { AuthService, RoomSummary } from '../../core/services/auth.service';

/**
 * Pantalla minima entre login y city-view: crear una sala o unirse por
 * codigo. A proposito sin pulir (sin lista de salas, sin auto-refresh):
 * lo pidio explicitamente el usuario -- que funcione antes que sea bonita.
 */
@Component({
  selector: 'app-room-entry',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="room-screen">
      <div class="room-card">
        <span class="brand-title">CITY SIMULATOR</span>

        <div class="section">
          <label class="field-label">Crear una sala nueva</label>
          <input class="field" type="text" [(ngModel)]="nameInput"
                 placeholder="nombre de la sala" (keyup.enter)="create()" />
          <button class="submit-btn" (click)="create()" [disabled]="loading()">
            {{ loading() ? 'CREANDO...' : 'CREAR SALA' }}
          </button>
        </div>

        <div class="section">
          <label class="field-label">Unirse por código</label>
          <input class="field" type="text" [(ngModel)]="codeInput"
                 placeholder="codigo de la sala" (keyup.enter)="join()" />
          <button class="submit-btn" (click)="join()" [disabled]="loading()">
            {{ loading() ? 'UNIENDO...' : 'UNIRSE' }}
          </button>
        </div>

        <div class="error" *ngIf="error()">{{ error() }}</div>

        <div class="section">
          <div class="rooms-header">
            <label class="field-label">Salas abiertas</label>
            <button class="refresh-btn" (click)="refreshRooms()" title="Refrescar">↻</button>
          </div>
          <div class="rooms-list" *ngIf="rooms().length; else noRooms">
            <div class="room-row" *ngFor="let r of rooms()">
              <div class="room-info">
                <span class="room-name">{{ r.name }}</span>
                <span class="room-meta">{{ r.code }} · {{ r.players }}/{{ r.maxPlayers }}</span>
              </div>
              <button class="join-btn" (click)="quickJoin(r.code)"
                      [disabled]="loading() || r.players >= r.maxPlayers">
                {{ r.players >= r.maxPlayers ? 'LLENA' : 'UNIRSE' }}
              </button>
            </div>
          </div>
          <ng-template #noRooms>
            <div class="no-rooms">No hay salas abiertas todavía. Crea una arriba.</div>
          </ng-template>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .room-screen {
      position: fixed; inset: 0;
      display: flex; align-items: center; justify-content: center;
      background: #0d0f1e;
    }
    .room-card {
      display: flex; flex-direction: column; gap: 1.2rem;
      width: 340px; padding: 2.5rem 2rem;
      background: rgba(10, 12, 30, 0.95);
      border: 1px solid rgba(100, 120, 220, 0.3);
      border-radius: 8px;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      color: #e0e4ff;
    }
    .brand-title { font-size: 1.1rem; font-weight: 700; letter-spacing: 2px; color: #7b9fff; text-align: center; }
    .section { display: flex; flex-direction: column; gap: 0.5rem; }
    .field-label { font-size: 0.7rem; color: rgba(200,210,255,0.7); letter-spacing: 1px; }
    .field {
      padding: 0.6rem 0.8rem; border-radius: 5px;
      border: 1px solid rgba(100,120,220,0.3);
      background: rgba(255,255,255,0.04); color: #e0e4ff;
      font-family: inherit; font-size: 0.85rem; outline: none;
    }
    .submit-btn {
      padding: 0.6rem; border: none; border-radius: 5px;
      background: #1a936f; color: #fff;
      font-family: inherit; font-size: 0.72rem; font-weight: 700;
      letter-spacing: 1px; cursor: pointer;
    }
    .submit-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .error {
      padding: 0.5rem 0.7rem; border-radius: 5px;
      background: rgba(193,52,42,0.15); border: 1px solid rgba(193,52,42,0.4);
      color: #ff7b72; font-size: 0.7rem; text-align: center;
    }

    .rooms-header { display: flex; align-items: center; justify-content: space-between; }
    .refresh-btn {
      background: transparent; border: 1px solid rgba(123,159,255,0.3); border-radius: 4px;
      color: #7b9fff; font-size: 0.8rem; width: 1.6rem; height: 1.6rem; cursor: pointer; line-height: 1;
    }
    .refresh-btn:hover { background: rgba(123,159,255,0.1); }
    .rooms-list { display: flex; flex-direction: column; gap: 0.5rem; max-height: 9rem; overflow-y: auto; }
    .room-row {
      display: flex; align-items: center; justify-content: space-between; gap: 0.6rem;
      padding: 0.5rem 0.7rem; border-radius: 5px;
      background: rgba(255,255,255,0.03); border: 1px solid rgba(123,159,255,0.15);
    }
    .room-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .room-name { font-size: 0.78rem; color: #e0e4ff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .room-meta { font-size: 0.62rem; color: rgba(180,190,255,0.55); letter-spacing: 0.5px; }
    .join-btn {
      padding: 0.35rem 0.6rem; border: none; border-radius: 5px; flex-shrink: 0;
      background: #3d5af1; color: #fff; font-family: inherit; font-size: 0.62rem; font-weight: 700;
      letter-spacing: 1px; cursor: pointer;
    }
    .join-btn:disabled { opacity: 0.35; cursor: not-allowed; }
    .no-rooms {
      font-size: 0.7rem; color: rgba(180,190,255,0.5); text-align: center;
      padding: 0.6rem; font-style: italic;
    }
  `]
})
export class RoomEntryComponent implements OnInit, OnDestroy {
  readonly loading = signal(false);
  readonly error = signal('');
  readonly rooms = signal<RoomSummary[]>([]);

  nameInput = '';
  codeInput = '';

  private pollSub?: Subscription;

  constructor(private auth: AuthService) {}

  ngOnInit(): void {
    this.refreshRooms();
    this.pollSub = interval(4000).subscribe(() => this.refreshRooms());
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  refreshRooms(): void {
    this.auth.listRooms().subscribe({
      next: (rooms) => this.rooms.set(rooms),
      error: () => {}
    });
  }

  create(): void {
    const name = this.nameInput.trim();
    if (!name) { this.error.set('Ponle un nombre a la sala'); return; }

    this.loading.set(true);
    this.error.set('');
    this.auth.createRoom(name).subscribe({
      next: () => this.loading.set(false),
      error: (err) => { this.loading.set(false); this.error.set(this.mapError(err)); }
    });
  }

  join(): void {
    const code = this.codeInput.trim().toUpperCase();
    if (!code) { this.error.set('Escribe el codigo de la sala'); return; }
    this.joinByCode(code);
  }

  quickJoin(code: string): void {
    this.joinByCode(code);
  }

  private joinByCode(code: string): void {
    this.loading.set(true);
    this.error.set('');
    this.auth.joinRoom(code).subscribe({
      next: () => this.loading.set(false),
      error: (err) => { this.loading.set(false); this.error.set(this.mapError(err)); }
    });
  }

  private mapError(err: any): string {
    const backendMsg = err.error?.error;
    if (backendMsg) return backendMsg;
    if (err.status === 0) return 'No se pudo conectar con el servidor';
    return 'Error inesperado. Intenta de nuevo.';
  }
}
