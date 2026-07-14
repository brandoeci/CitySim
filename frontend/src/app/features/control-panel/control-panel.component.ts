import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SimulationService } from '../../core/services/simulation.service';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-control-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="panel">
      <div class="panel-header">
        <span class="title">CITY SIMULATOR</span>
        <span class="subtitle">Space-Based Architecture</span>
      </div>

      <div class="stats">
        <div class="stat">
          <span class="stat-value">{{ carCount() | number }}</span>
          <span class="stat-label">AUTOS</span>
        </div>
        <div class="stat">
          <span class="stat-value">{{ fps() }}</span>
          <span class="stat-label">FPS</span>
        </div>
        <div class="stat">
          <span class="stat-value">{{ tick() | number }}</span>
          <span class="stat-label">TICK</span>
        </div>
      </div>

      <div class="controls">
        <button class="btn btn-primary" (click)="startSim()" [disabled]="running()">
          ▶ INICIAR
        </button>
        <button class="btn btn-danger" (click)="stopSim()" [disabled]="!running()">
          ■ DETENER
        </button>
      </div>

      <div class="spawn-section">
        <label class="control-label">Autos a agregar: {{ carCountValue }}</label>
        <input type="range" min="10" max="5000" step="10"
               [(ngModel)]="carCountValue" class="slider" />
        <button class="btn btn-accent" (click)="spawnCars()">
          + AGREGAR {{ carCountValue }} AUTOS
        </button>
        <button class="btn btn-outline" (click)="removeAllCars()">
          ✕ LIMPIAR TODOS
        </button>
      </div>

      <div class="info">
        <div class="info-item">
          <span class="label">Estado:</span>
          <span class="value" [class.running]="running()">
            {{ running() ? 'CORRIENDO' : 'DETENIDO' }}
          </span>
        </div>
        <div class="info-item" *ngIf="lastMsg()">
          <span class="value msg">{{ lastMsg() }}</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .panel {
      display: flex; flex-direction: column; gap: 1.2rem;
      padding: 1.5rem;
      background: rgba(10, 12, 30, 0.95);
      border: 1px solid rgba(100, 120, 220, 0.3);
      border-radius: 8px; color: #e0e4ff;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      min-width: 260px; backdrop-filter: blur(10px);
    }
    .panel-header { display: flex; flex-direction: column; gap: 4px; }
    .title { font-size: 1.1rem; font-weight: 700; letter-spacing: 3px; color: #7b9fff; }
    .subtitle { font-size: 0.65rem; letter-spacing: 2px; color: rgba(140,160,255,0.6); }

    .stats { display: flex; gap: 1rem; }
    .stat { display: flex; flex-direction: column; align-items: center; flex: 1;
            background: rgba(255,255,255,0.04); border-radius: 6px; padding: 0.6rem 0.4rem; }
    .stat-value { font-size: 1.3rem; font-weight: 700; color: #7b9fff; }
    .stat-label { font-size: 0.55rem; letter-spacing: 2px; color: rgba(180,190,255,0.5); margin-top: 2px; }

    .controls { display: flex; gap: 0.6rem; }
    .spawn-section { display: flex; flex-direction: column; gap: 0.6rem; }
    .control-label { font-size: 0.75rem; color: rgba(200,210,255,0.7); letter-spacing: 1px; }
    .slider { width: 100%; accent-color: #5b7fff; cursor: pointer; }

    .btn {
      padding: 0.5rem 0.8rem; border: none; border-radius: 5px;
      font-family: inherit; font-size: 0.7rem; font-weight: 600;
      letter-spacing: 1.5px; cursor: pointer; transition: all 0.15s;
    }
    .btn:disabled { opacity: 0.3; cursor: not-allowed; }
    .btn-primary { background: #3d5af1; color: #fff; }
    .btn-primary:hover:not(:disabled) { background: #5b7fff; }
    .btn-danger  { background: #c1342a; color: #fff; }
    .btn-danger:hover:not(:disabled)  { background: #e84040; }
    .btn-accent  { background: #1a936f; color: #fff; }
    .btn-accent:hover:not(:disabled)  { background: #22b88a; }
    .btn-outline { background: transparent; color: #7b9fff;
                   border: 1px solid rgba(123,159,255,0.4); }
    .btn-outline:hover:not(:disabled) { background: rgba(123,159,255,0.1); }

    .info { font-size: 0.72rem; display: flex; flex-direction: column; gap: 4px; }
    .info-item { display: flex; gap: 0.5rem; align-items: center; }
    .label { color: rgba(180,190,255,0.5); }
    .value { color: #e0e4ff; }
    .value.running { color: #22b88a; }
    .value.msg { color: rgba(200,210,255,0.6); font-size: 0.65rem; }
  `]
})
export class ControlPanelComponent implements OnInit, OnDestroy {
  carCountValue = 100;
  readonly running  = signal(false);
  readonly carCount = signal(0);
  readonly fps      = signal(0);
  readonly tick     = signal(0);
  readonly lastMsg  = signal('');

  private pollSub?: Subscription;

  constructor(readonly simService: SimulationService) {}

  ngOnInit(): void {
    this.pollStatus();
    this.pollSub = interval(2000).subscribe(() => this.pollStatus());
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  private pollStatus(): void {
    this.simService.getStatus().subscribe({
      next: (s) => {
        this.running.set(s.running);
        this.carCount.set(s.totalCars);
        this.fps.set(Math.round(s.fps));
        this.tick.set(s.tick);
        this.simService.running.set(s.running);
        this.simService.carCount.set(s.totalCars);
        this.simService.fps.set(Math.round(s.fps));
      }
    });
  }

  startSim(): void {
    this.simService.start().subscribe({
      next: () => { this.running.set(true); this.simService.running.set(true); this.lastMsg.set('Simulación iniciada'); },
      error: (e) => this.lastMsg.set('Error: ' + e.message)
    });
  }

  stopSim(): void {
    this.simService.stop().subscribe({
      next: () => { this.running.set(false); this.simService.running.set(false); this.lastMsg.set('Simulación detenida'); },
      error: (e) => this.lastMsg.set('Error: ' + e.message)
    });
  }

  spawnCars(): void {
    this.simService.spawnCars(this.carCountValue).subscribe({
      next: (r) => {
        this.carCount.set(r.total);
        this.simService.carCount.set(r.total);
        this.lastMsg.set(`+${r.spawned} autos (total: ${r.total})`);
      },
      error: (e) => this.lastMsg.set('Error: ' + e.message)
    });
  }

  removeAllCars(): void {
    this.simService.removeAllCars().subscribe({
      next: (r) => {
        this.carCount.set(0);
        this.simService.carCount.set(0);
        this.simService.clearCars();
        this.lastMsg.set(`${r.removed} autos eliminados`);
      },
      error: (e) => this.lastMsg.set('Error: ' + e.message)
    });
  }
}
