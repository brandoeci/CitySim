import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { WebSocketService } from './websocket.service';
import { ActiveEvent } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class EventService {
  private http = inject(HttpClient);
  private ws = inject(WebSocketService);

  readonly activeEvent = signal<ActiveEvent | null>(null);

  /** La via que este administrador debe cerrar para aportar al evento. */
  readonly myTargetEdge = computed(() => this.activeEvent()?.myTargetEdge ?? null);

  /** Si ya cumplio su objetivo. */
  readonly iResponded = computed(() => this.activeEvent()?.iResponded ?? false);

  init(): void {
    this.refresh();
    // El broadcast del WebSocket avisa que algo cambio; el detalle personalizado
    // (mi objetivo, si ya respondi) se pide al REST, que sabe quien soy.
    this.ws.events$().subscribe(() => this.refresh());
  }

  /** Vuelve a pedir el evento activo con el objetivo propio ya resuelto. */
  refresh(): void {
    this.http.get<ActiveEvent>('/api/events/active').subscribe({
      next: (ev) => {
        if (!ev || !ev.eventId) {
          this.activeEvent.set(null);
          return;
        }
        this.activeEvent.set(ev);
      },
      error: () => this.activeEvent.set(null)
    });
  }
}
