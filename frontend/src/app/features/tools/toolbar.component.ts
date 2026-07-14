import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToolService, ToolId } from '../../core/services/tool.service';
import { DistrictService } from '../../core/services/district.service';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toolbar">
      <div class="tb-header">HERRAMIENTAS</div>

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

      <div class="feedback" *ngIf="tools.feedback()">{{ tools.feedback() }}</div>
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
    .tb-header {
      font-size: 0.55rem; letter-spacing: 2px;
      color: rgba(140,160,255,0.6);
    }
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
export class ToolbarComponent {
  readonly tools = inject(ToolService);
  readonly districts = inject(DistrictService);

  select(tool: ToolId): void {
    this.tools.selectTool(tool);
  }

  blockedCount(): number {
    // Cada via se cierra en ambos sentidos, asi que se cuentan pares.
    return Math.ceil(Object.keys(this.tools.blockedEdges()).length / 2);
  }
}
