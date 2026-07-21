import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToolService, ToolId } from '../../core/services/tool.service';
import { DistrictService } from '../../core/services/district.service';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toolbar">
      <div class="tb-header-row">
        <span class="tb-header">HERRAMIENTAS</span>
        <button class="help-btn" (click)="showHelp.set(true)" title="¿Qué hace cada herramienta?">?</button>
      </div>

      <button class="tool"
              [class.active]="tools.activeTool() === 'close-road'"
              (click)="select('close-road')"
              title="Cerrar vía: haz clic en una calle de tu distrito">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M3 12h18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M6 6l12 12M18 6L6 18" stroke="#ff7b72" stroke-width="2.2" stroke-linecap="round"/>
        </svg>
        <span class="label">CERRAR VÍA</span>
      </button>

      <button class="tool"
              [class.active]="tools.activeTool() === 'force-green'"
              (click)="select('force-green')"
              title="Forzar semáforo: haz clic en un cruce de tu distrito">
        <svg viewBox="0 0 24 24" class="icon">
          <circle cx="12" cy="12" r="8" fill="#22c55e" stroke="none"/>
        </svg>
        <span class="label">FORZAR SEMÁFORO</span>
      </button>

      <button class="tool"
              [class.active]="tools.activeTool() === 'green-wave'"
              (click)="select('green-wave')"
              title="Ola verde: haz clic en un cruce de tu distrito">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M2 12c2-4 4-4 6 0s4 4 6 0 4-4 6 0" stroke="#22c55e"
                stroke-width="2.2" stroke-linecap="round" fill="none"/>
        </svg>
        <span class="label">OLA VERDE</span>
      </button>

      <button class="tool"
              [class.active]="tools.activeTool() === 'speed-trap'"
              (click)="select('speed-trap')"
              title="Reductor: haz clic en una vía de tu distrito">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M3 8l4-4h4l-4 4h4l-4 4H3zM13 8l4-4h4l-4 4h4l-4 4h-4z"
                fill="#eab308" stroke="none"/>
        </svg>
        <span class="label">REDUCTOR</span>
      </button>

      <button class="tool"
              [class.active]="tools.activeTool() === 'speed-boost'"
              (click)="select('speed-boost')"
              title="Turbo: haz clic en una vía de tu distrito">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M4 12h11M11 6l6 6-6 6" stroke="#38bdf8" stroke-width="2.4"
                stroke-linecap="round" stroke-linejoin="round" fill="none"/>
        </svg>
        <span class="label">TURBO</span>
      </button>

      <button class="tool"
              [disabled]="shieldOnCooldown()"
              (click)="activateShield()"
              title="Escudo de distrito: bloquea las vias frontera 20s (sin clic en el mapa)">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M12 2l8 3v6c0 5-3.5 8.5-8 11-4.5-2.5-8-6-8-11V5z"
                fill="#eab308" stroke="none"/>
        </svg>
        <span class="label">{{ shieldOnCooldown() ? 'ESCUDO (' + shieldCooldownRemaining() + 's)' : 'ESCUDO DE DISTRITO' }}</span>
      </button>

      <button class="tool"
              [class.active]="tools.activeTool() === 'traffic-bomb'"
              (click)="select('traffic-bomb')"
              title="Lluvia de tráfico: haz clic fuera de tu distrito">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M12 3l3 7h-2l2 5-6 6 1-7H8z" fill="#ef4444" stroke="none"/>
        </svg>
        <span class="label">LLUVIA DE TRÁFICO</span>
      </button>

      <div class="tb-info">
        <div class="info-row">
          <span class="k">Cerradas</span>
          <span class="v">{{ blockedCount() }}</span>
        </div>
        <div class="info-row">
          <span class="k">Admins</span>
          <span class="v">{{ districts.activeUsers() }} / {{ districts.maxUsers() }}</span>
        </div>
      </div>

      <div class="hint" *ngIf="tools.activeTool() === 'close-road'">
        Clic sobre una vía de tu distrito
      </div>
      <div class="hint" *ngIf="tools.activeTool() === 'force-green'">
        Clic sobre un cruce de avenidas de tu distrito
      </div>
      <div class="hint" *ngIf="tools.activeTool() === 'traffic-bomb'">
        Clic fuera de tu distrito: envia un tropel de carros hacia ahi (cooldown 60s)
      </div>
      <div class="hint" *ngIf="tools.activeTool() === 'green-wave'">
        Clic sobre un cruce de tu distrito: fuerza en verde toda la avenida
      </div>
      <div class="hint" *ngIf="tools.activeTool() === 'speed-trap'">
        Clic sobre una vía de tu distrito (reduce la velocidad 60s)
      </div>
      <div class="hint" *ngIf="tools.activeTool() === 'speed-boost'">
        Clic sobre una vía de tu distrito (duplica la velocidad 45s)
      </div>

      <div class="feedback" *ngIf="tools.feedback()">{{ tools.feedback() }}</div>
    </div>

    <div class="help-backdrop" *ngIf="showHelp()" (click)="showHelp.set(false)">
      <div class="help-modal" (click)="$event.stopPropagation()">
        <div class="help-header">
          <span>CATÁLOGO DE HERRAMIENTAS</span>
          <button class="close-btn" (click)="showHelp.set(false)">✕</button>
        </div>
        <div class="help-list">
          <div class="help-item" *ngFor="let h of helpItems">
            <div class="help-name" [style.color]="h.color">{{ h.name }}</div>
            <div class="help-desc">{{ h.desc }}</div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .toolbar {
      display: flex; flex-direction: column; gap: 0.7rem;
      padding: 1rem;
      background: rgba(10, 12, 30, 0.95);
      border: 1px solid rgba(100, 120, 220, 0.3);
      border-radius: 8px;
      backdrop-filter: blur(10px);
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      color: #e0e4ff;
      min-width: 190px;
    }
    .tb-header-row { display: flex; align-items: center; justify-content: space-between; }
    .tb-header {
      font-size: 0.55rem; letter-spacing: 2px;
      color: rgba(140,160,255,0.6);
    }
    .help-btn {
      width: 1.4rem; height: 1.4rem; border-radius: 50%;
      background: rgba(123,159,255,0.12); border: 1px solid rgba(123,159,255,0.4);
      color: #7b9fff; font-family: inherit; font-size: 0.7rem; font-weight: 700;
      cursor: pointer; line-height: 1;
    }
    .help-btn:hover { background: rgba(123,159,255,0.22); }

    .help-backdrop {
      position: fixed; inset: 0; z-index: 200;
      background: rgba(5,6,15,0.65); backdrop-filter: blur(2px);
      display: flex; align-items: center; justify-content: center;
    }
    .help-modal {
      width: min(560px, 90vw); max-height: 80vh; overflow-y: auto;
      background: rgba(12, 14, 30, 0.98);
      border: 1px solid rgba(100,120,220,0.35); border-radius: 10px;
      padding: 1.4rem 1.6rem;
      font-family: 'JetBrains Mono', 'Fira Code', monospace; color: #e0e4ff;
    }
    .help-header {
      display: flex; align-items: center; justify-content: space-between;
      font-size: 0.85rem; font-weight: 700; letter-spacing: 2px; color: #7b9fff;
      margin-bottom: 1rem;
    }
    .close-btn {
      background: transparent; border: none; color: rgba(200,210,255,0.6);
      font-size: 1rem; cursor: pointer; line-height: 1;
    }
    .close-btn:hover { color: #ff7b72; }
    .help-list { display: flex; flex-direction: column; gap: 0.9rem; }
    .help-item { border-left: 2px solid rgba(123,159,255,0.3); padding-left: 0.7rem; }
    .help-name { font-size: 0.72rem; font-weight: 700; letter-spacing: 1px; margin-bottom: 3px; }
    .help-desc { font-size: 0.68rem; line-height: 1.5; color: rgba(210,215,255,0.85); }
    .tool {
      display: flex; align-items: center; gap: 0.6rem;
      padding: 0.55rem 0.7rem;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(123,159,255,0.2);
      border-radius: 6px;
      color: #e0e4ff;
      font-family: inherit; font-size: 0.68rem; font-weight: 600;
      letter-spacing: 1px; cursor: pointer; transition: all 0.15s;
      text-align: left;
    }
    .tool:hover { background: rgba(123,159,255,0.1); }
    .tool:disabled { opacity: 0.45; cursor: not-allowed; }
    .tool:disabled:hover { background: rgba(255,255,255,0.04); }
    .tool.active {
      background: rgba(193,52,42,0.2);
      border-color: #c1342a;
      color: #ff7b72;
      box-shadow: 0 0 12px rgba(193,52,42,0.25);
    }
    .icon { width: 18px; height: 18px; flex-shrink: 0; fill: none; }
    .label { white-space: nowrap; }

    .tb-info {
      display: flex; flex-direction: column; gap: 3px;
      padding-top: 0.5rem;
      border-top: 1px solid rgba(100,120,220,0.2);
      font-size: 0.62rem;
    }
    .info-row { display: flex; justify-content: space-between; }
    .k { color: rgba(180,190,255,0.5); }
    .v { color: #7b9fff; font-weight: 700; }

    .hint {
      font-size: 0.58rem; line-height: 1.3;
      color: rgba(255,123,114,0.75);
      padding: 0.4rem 0.5rem;
      background: rgba(193,52,42,0.1);
      border-radius: 4px;
    }
    .feedback {
      font-size: 0.6rem; text-align: center;
      color: rgba(200,210,255,0.7);
    }
  `]
})
export class ToolbarComponent implements OnInit, OnDestroy {
  readonly tools = inject(ToolService);
  readonly districts = inject(DistrictService);

  readonly showHelp = signal(false);

  readonly helpItems = [
    { name: 'CERRAR VÍA', color: '#ff7b72',
      desc: 'Clic en una vía de tu distrito para cerrarla al tráfico (los carros la evitan y se re-rutean). Clic de nuevo sobre ella para reabrirla. Sin límite de tiempo ni cooldown.' },
    { name: 'FORZAR SEMÁFORO', color: '#22c55e',
      desc: 'Clic en un cruce de avenidas (donde se cruzan dos avenidas grandes) dentro de tu distrito. Fuerza ese eje en verde durante 30 segundos; después vuelve solo a su ciclo normal.' },
    { name: 'OLA VERDE', color: '#22c55e',
      desc: 'Clic en un cruce de tu distrito. Fuerza en verde, uno tras otro con 2s de diferencia, todos los cruces de esa misma avenida — como una ola que despeja el corredor completo.' },
    { name: 'REDUCTOR', color: '#eab308',
      desc: 'Clic en una vía de tu distrito. Reduce a la mitad la velocidad de los carros que pasen por ahí durante 60 segundos (franjas amarillas/negras).' },
    { name: 'TURBO', color: '#38bdf8',
      desc: 'Clic en una vía de tu distrito. Duplica la velocidad de los carros que pasen por ahí durante 45 segundos (brillo azul con flechas).' },
    { name: 'ESCUDO DE DISTRITO', color: '#eab308',
      desc: 'No necesita clic en el mapa: se activa directo con el botón. Bloquea 20 segundos todas las vías frontera de tu distrito, protegiéndote de tráfico externo. Cooldown de 90 segundos.' },
    { name: 'LLUVIA DE TRÁFICO', color: '#ef4444',
      desc: 'Clic FUERA de tu distrito, en el territorio de otro administrador. Envía 25 carros nuevos hacia ese punto para complicarle el tráfico. Cooldown de 60 segundos por usuario.' },
  ];

  private readonly now = signal(Date.now());
  private ticker?: ReturnType<typeof setInterval>;

  readonly shieldOnCooldown = computed(() => this.now() < this.tools.shieldCooldownUntil());
  readonly shieldCooldownRemaining = computed(() =>
    Math.max(0, Math.ceil((this.tools.shieldCooldownUntil() - this.now()) / 1000)));

  ngOnInit(): void {
    this.ticker = setInterval(() => this.now.set(Date.now()), 1000);
  }

  ngOnDestroy(): void {
    if (this.ticker) clearInterval(this.ticker);
  }

  select(tool: ToolId): void {
    this.tools.selectTool(tool);
  }

  activateShield(): void {
    this.tools.districtShield().subscribe({ next: () => {}, error: () => {} });
  }

  blockedCount(): number {
    // Cada via se cierra en ambos sentidos, asi que se cuentan pares.
    return Math.ceil(Object.keys(this.tools.blockedEdges()).length / 2);
  }
}
