import { Component, OnInit, OnDestroy, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventService } from '../../core/services/event.service';
import { EVENT_LABELS } from '../../core/models/event.model';

@Component({
  selector: 'app-event-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="event-panel" *ngIf="ev() as e"
         [class.resolved]="e.status === 'RESOLVED'"
         [class.expired]="e.status === 'EXPIRED'">

      <div class="event-header">
        <span class="event-tag">EVENTO COLABORATIVO</span>
        <span class="event-type">{{ typeLabel() }}</span>
      </div>

      <p class="event-desc">{{ e.description }}</p>

      <div class="timer-row" *ngIf="e.status === 'ACTIVE'">
        <span class="timer-label">TIEMPO</span>
        <span class="timer-value" [class.urgent]="remaining() <= 15">
          {{ remaining() }}s
        </span>
      </div>

      <div class="progress-block">
        <div class="progress-info">
          <span>DISTRITOS QUE RESPONDIERON</span>
          <span>{{ e.totalActions }} / {{ e.requiredActions }}</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" [style.width.%]="e.progressPercent"></div>
        </div>
      </div>

      <div class="objective" *ngIf="e.status === 'ACTIVE'">
        <div class="obj-title">TU OBJETIVO</div>

        <div class="obj-done" *ngIf="e.iResponded">
          Cumplido. Esperando a los demás distritos.
        </div>

        <div class="obj-todo" *ngIf="!e.iResponded && e.myTargetEdge">
          Cierra la vía marcada en naranja en tu distrito.
          <span class="edge-id">{{ e.myTargetEdge }}</span>
        </div>

        <div class="obj-none" *ngIf="!e.iResponded && !e.myTargetEdge">
          No tienes objetivo en este evento.
        </div>
      </div>

      <div class="result resolved-msg" *ngIf="e.status === 'RESOLVED'">
        RESUELTO — la ciudad se coordinó a tiempo
      </div>
      <div class="result expired-msg" *ngIf="e.status === 'EXPIRED'">
        EXPIRÓ — no todos los distritos respondieron
      </div>
    </div>
  `,
  styles: [`
    .event-panel {
      display: flex; flex-direction: column; gap: 0.8rem;
      width: 330px; padding: 1.3rem 1.4rem;
      background: rgba(10, 12, 30, 0.95);
      border: 1px solid rgba(193, 52, 42, 0.5);
      border-radius: 8px; backdrop-filter: blur(10px);
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      color: #e0e4ff;
      box-shadow: 0 0 24px rgba(193,52,42,0.15);
    }
    .event-panel.resolved { border-color: rgba(26,147,111,0.6); box-shadow: 0 0 24px rgba(26,147,111,0.15); }
    .event-panel.expired  { border-color: rgba(120,120,140,0.4); box-shadow: none; opacity: 0.85; }

    .event-header { display: flex; flex-direction: column; gap: 3px; }
    .event-tag { font-size: 0.55rem; letter-spacing: 2px; color: rgba(255,123,114,0.8); }
    .event-type { font-size: 1.05rem; font-weight: 700; letter-spacing: 1px; color: #ff7b72; }
    .resolved .event-type { color: #22b88a; }
    .expired .event-type { color: rgba(180,190,255,0.6); }

    .event-desc { font-size: 0.72rem; line-height: 1.4; color: rgba(210,215,255,0.85); margin: 0; }

    .timer-row { display: flex; justify-content: space-between; align-items: baseline; }
    .timer-label { font-size: 0.6rem; letter-spacing: 2px; color: rgba(180,190,255,0.5); }
    .timer-value { font-size: 1.4rem; font-weight: 700; color: #7b9fff; }
    .timer-value.urgent { color: #ff7b72; animation: blink 1s steps(2) infinite; }
    @keyframes blink { 50% { opacity: 0.4; } }

    .progress-block { display: flex; flex-direction: column; gap: 5px; }
    .progress-info { display: flex; justify-content: space-between;
                     font-size: 0.58rem; letter-spacing: 1px; color: rgba(180,190,255,0.6); }
    .progress-track { width: 100%; height: 8px; border-radius: 4px;
                      background: rgba(255,255,255,0.06); overflow: hidden; }
    .progress-fill { height: 100%; border-radius: 4px;
                     background: linear-gradient(90deg, #3d5af1, #22b88a);
                     transition: width 0.4s ease; }

    .objective {
      display: flex; flex-direction: column; gap: 5px;
      padding: 0.6rem 0.7rem; border-radius: 6px;
      background: rgba(224,168,13,0.08);
      border: 1px solid rgba(224,168,13,0.3);
    }
    .obj-title { font-size: 0.55rem; letter-spacing: 2px; color: #e0a80d; }
    .obj-todo { font-size: 0.68rem; line-height: 1.4; color: rgba(240,220,180,0.9); }
    .edge-id { display: block; margin-top: 3px; font-size: 0.6rem; color: rgba(224,168,13,0.7); }
    .obj-done { font-size: 0.68rem; color: #22b88a; }
    .obj-none { font-size: 0.68rem; color: rgba(180,190,255,0.5); }

    .result { font-size: 0.72rem; font-weight: 700; text-align: center; padding: 0.4rem; letter-spacing: 1px; }
    .resolved-msg { color: #22b88a; }
    .expired-msg { color: rgba(180,190,255,0.6); }
  `]
})
export class EventPanelComponent implements OnInit, OnDestroy {
  private eventService = inject(EventService);

  readonly ev = this.eventService.activeEvent;
  readonly now = signal(Date.now());

  private ticker?: ReturnType<typeof setInterval>;

  readonly typeLabel = computed(() => {
    const e = this.ev();
    return e ? (EVENT_LABELS[e.type] ?? e.type) : '';
  });

  readonly remaining = computed(() => {
    const e = this.ev();
    if (!e || !e.startedAt) return e?.durationSeconds ?? 0;
    const start = new Date(e.startedAt).getTime();
    const elapsed = (this.now() - start) / 1000;
    return Math.max(0, Math.ceil(e.durationSeconds - elapsed));
  });

  ngOnInit(): void {
    this.eventService.init();
    this.ticker = setInterval(() => this.now.set(Date.now()), 1000);
  }

  ngOnDestroy(): void {
    if (this.ticker) clearInterval(this.ticker);
  }
}
