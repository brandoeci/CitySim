import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CityCanvasComponent } from './city-canvas/city-canvas.component';
import { ControlPanelComponent } from '../control-panel/control-panel.component';
import { EventPanelComponent } from '../events/event-panel.component';
import { ToolbarComponent } from '../tools/toolbar.component';
import { MapService } from '../../core/services/map.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { DistrictService } from '../../core/services/district.service';
import { CityMapData } from '../../core/models/city-map.model';

@Component({
  selector: 'app-city-view',
  standalone: true,
  imports: [
    CommonModule,
    CityCanvasComponent,
    ControlPanelComponent,
    EventPanelComponent,
    ToolbarComponent
  ],
  template: `
    <div class="city-view">
      <div class="canvas-container">
        <app-city-canvas [mapData]="mapData()" />
      </div>
      <div class="toolbar-container">
        <app-toolbar />
      </div>
      <div class="panel-container">
        <app-control-panel />
      </div>
      <div class="event-container">
        <app-event-panel />
      </div>
      <div class="loading-overlay" *ngIf="loading()">
        <div class="loading-text">CARGANDO MAPA...</div>
        <div class="loading-sub">{{ loadingStatus() }}</div>
      </div>
    </div>
  `,
  styles: [`
    .city-view {
      position: relative;
      width: 100vw;
      height: 100vh;
      background: #0d0f1e;
      display: flex;
      overflow: hidden;
    }
    .canvas-container {
      flex: 1;
      position: relative;
      overflow: hidden;
    }
    .toolbar-container {
      position: absolute;
      top: 1.5rem;
      left: 1.5rem;
      z-index: 10;
    }
    .panel-container {
      position: absolute;
      top: 1.5rem;
      right: 1.5rem;
      z-index: 10;
    }
    .event-container {
      position: absolute;
      bottom: 1.5rem;
      left: 1.5rem;
      z-index: 10;
    }
    .loading-overlay {
      position: absolute;
      inset: 0;
      background: rgba(10,12,30,0.9);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      z-index: 100;
      gap: 0.8rem;
    }
    .loading-text {
      font-family: 'JetBrains Mono', monospace;
      font-size: 1.4rem;
      letter-spacing: 4px;
      color: #7b9fff;
      animation: pulse 1.5s ease-in-out infinite;
    }
    .loading-sub {
      font-family: monospace;
      font-size: 0.75rem;
      color: rgba(140,160,255,0.5);
      letter-spacing: 2px;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.4; }
    }
  `]
})
export class CityViewComponent implements OnInit {
  readonly mapData = signal<CityMapData | null>(null);
  readonly loading = signal(true);
  readonly loadingStatus = signal('Conectando al servidor...');

  constructor(
    private mapService: MapService,
    private wsService: WebSocketService,
    private districtService: DistrictService
  ) {}

  ngOnInit(): void {
    this.wsService.connect();
    this.districtService.start();
    this.loadMap();
  }

  private loadMap(): void {
    this.loadingStatus.set('Cargando geometría del mapa...');
    this.mapService.loadMap().subscribe({
      next: (data) => {
        this.mapData.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loadingStatus.set('Error al cargar el mapa. Reintentando...');
        setTimeout(() => this.loadMap(), 3000);
      }
    });
  }
}
