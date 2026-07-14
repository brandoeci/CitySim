import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export type ToolId = 'none' | 'close-road';

@Injectable({ providedIn: 'root' })
export class ToolService {
  private http = inject(HttpClient);

  /** Herramienta seleccionada en la barra. */
  readonly activeTool = signal<ToolId>('none');

  /** Vias cerradas: edgeId -> username que la cerro. */
  readonly blockedEdges = signal<Record<string, string>>({});

  /** Ultimo mensaje de la herramienta (exito o error). */
  readonly feedback = signal<string>('');

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

  refresh(): void {
    this.http.get<{ blocked: Record<string, string> }>('/api/tools/blocked-edges')
      .subscribe({
        next: (r) => this.blockedEdges.set(r.blocked ?? {}),
        error: () => {}
      });
  }

  isBlocked(edgeId: string): boolean {
    return edgeId in this.blockedEdges();
  }
}
