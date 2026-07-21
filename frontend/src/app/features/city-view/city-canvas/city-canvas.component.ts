import {
  Component, OnDestroy, ElementRef, ViewChild,
  AfterViewInit, Input, OnChanges, SimpleChanges, effect, inject
} from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { CityMapData, RoadSegment } from '../../../core/models/city-map.model';
import { SimulationFrame } from '../../../core/models/car-state.model';
import { WebSocketService } from '../../../core/services/websocket.service';
import { SimulationService } from '../../../core/services/simulation.service';
import { DistrictService } from '../../../core/services/district.service';
import { ToolService } from '../../../core/services/tool.service';
import { EventService } from '../../../core/services/event.service';

@Component({
  selector: 'app-city-canvas',
  standalone: true,
  template: `
    <canvas #cityCanvas
      [style.cursor]="tools.activeTool() === 'none' ? 'grab' : 'crosshair'"
      style="display:block;width:100%;height:100%"
      (mousedown)="onMouseDown($event)"
      (mousemove)="onMouseMove($event)"
      (mouseup)="onMouseUp($event)"
      (mouseleave)="onMouseLeave()"
      (wheel)="onWheel($event)">
    </canvas>
  `
})
export class CityCanvasComponent implements AfterViewInit, OnDestroy, OnChanges {
  @ViewChild('cityCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;
  @Input() mapData: CityMapData | null = null;

  private worker!: Worker;
  private destroy$ = new Subject<void>();
  private resizeObserver?: ResizeObserver;

  private districtService = inject(DistrictService);
  private eventService = inject(EventService);
  readonly tools = inject(ToolService);

  private scale = 1;
  private panX = 0;
  private panY = 0;
  private canvasW = 0;
  private canvasH = 0;

  private dragging = false;
  private movedWhileDown = false;
  private lastMouseX = 0;
  private lastMouseY = 0;

  constructor(
    private wsService: WebSocketService,
    private simService: SimulationService
  ) {
    effect(() => {
      const districts = this.districtService.districts();
      const myIndex = this.districtService.myDistrictIndex();
      this.worker?.postMessage({ type: 'districts', districts, myDistrictIndex: myIndex });
    });

    effect(() => {
      const blocked = this.tools.blockedEdges();
      this.worker?.postMessage({ type: 'blocked', blocked });
    });

    effect(() => {
      const speedOverrides = this.tools.speedOverrides();
      this.worker?.postMessage({ type: 'speedOverrides', speedOverrides });
    });

    effect(() => {
      const shieldActiveUntil = this.tools.shieldActiveUntil();
      this.worker?.postMessage({ type: 'shield', activeUntil: shieldActiveUntil });
    });

    // El objetivo de este administrador dentro del evento activo.
    effect(() => {
      const objective = this.eventService.myObjective();
      this.worker?.postMessage({ type: 'objective', objective });
    });
  }

  ngAfterViewInit(): void {
    this.initWorker();
    this.subscribeToFrames();
    this.observeSize();
    this.tools.refresh();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['mapData'] && this.mapData && this.worker) {
      this.worker.postMessage({ type: 'map', map: this.mapData });
      if (this.mapData.blockedEdges) {
        this.tools.blockedEdges.set(this.mapData.blockedEdges);
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.resizeObserver?.disconnect();
    this.worker?.terminate();
  }

  private observeSize(): void {
    const canvas = this.canvasRef.nativeElement;
    this.resizeObserver = new ResizeObserver(entries => {
      const entry = entries[0];
      if (!entry) return;
      const width = Math.floor(entry.contentRect.width * devicePixelRatio);
      const height = Math.floor(entry.contentRect.height * devicePixelRatio);
      if (width > 0 && height > 0) {
        this.canvasW = width;
        this.canvasH = height;
        this.worker?.postMessage({ type: 'resize', width, height });
      }
    });
    this.resizeObserver.observe(canvas);
  }

  onMouseDown(e: MouseEvent): void {
    this.dragging = true;
    this.movedWhileDown = false;
    this.lastMouseX = e.clientX;
    this.lastMouseY = e.clientY;
  }

  onMouseMove(e: MouseEvent): void {
    if (!this.dragging) return;
    const dx = e.clientX - this.lastMouseX;
    const dy = e.clientY - this.lastMouseY;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) this.movedWhileDown = true;
    this.panX += dx * devicePixelRatio;
    this.panY += dy * devicePixelRatio;
    this.lastMouseX = e.clientX;
    this.lastMouseY = e.clientY;
    this.sendPan();
  }

  onMouseUp(e: MouseEvent): void {
    const wasDragging = this.dragging;
    this.dragging = false;
    if (!wasDragging || this.movedWhileDown) return;

    const tool = this.tools.activeTool();
    if (tool === 'close-road') {
      this.handleRoadClick(e);
    } else if (tool === 'force-green') {
      this.handleForceGreenClick(e);
    } else if (tool === 'green-wave') {
      this.handleGreenWaveClick(e);
    } else if (tool === 'speed-trap') {
      this.handleSpeedToolClick(e, 'speed-trap');
    } else if (tool === 'speed-boost') {
      this.handleSpeedToolClick(e, 'speed-boost');
    } else if (tool === 'traffic-bomb') {
      this.handleTrafficBombClick(e);
    }
  }

  onMouseLeave(): void {
    this.dragging = false;
  }

  onWheel(e: WheelEvent): void {
    e.preventDefault();
    if (!this.mapData) return;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const px = (e.clientX - rect.left) * devicePixelRatio;
    const py = (e.clientY - rect.top) * devicePixelRatio;

    // Punto del mundo bajo el cursor ANTES de cambiar el zoom.
    const worldBefore = this.canvasToWorld(px, py);

    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    this.scale = Math.max(0.3, Math.min(this.scale * delta, 20));

    if (worldBefore) {
      // Reajusta el pan para que ESE MISMO punto del mundo quede otra vez
      // bajo el cursor con el nuevo zoom -- si no, el zoom queda anclado al
      // centro del canvas y todo "se corre" respecto a donde estabas mirando.
      const scaleX = this.canvasW / this.mapData.width;
      const scaleY = this.canvasH / this.mapData.height;
      const worldToCanvas = Math.min(scaleX, scaleY) * this.scale;
      const baseOffsetX = (this.canvasW - this.mapData.width * worldToCanvas) / 2;
      const baseOffsetY = (this.canvasH - this.mapData.height * worldToCanvas) / 2;
      this.panX = px - worldBefore.x * worldToCanvas - baseOffsetX;
      this.panY = py - worldBefore.y * worldToCanvas - baseOffsetY;
    }

    this.sendPan();
  }

  private handleRoadClick(e: MouseEvent): void {
    if (!this.mapData) return;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();

    const px = (e.clientX - rect.left) * devicePixelRatio;
    const py = (e.clientY - rect.top) * devicePixelRatio;

    const world = this.canvasToWorld(px, py);
    if (!world) return;

    const seg = this.findNearestSegment(world.x, world.y);
    if (!seg) {
      this.tools.feedback.set('No hay ninguna via ahi cerca');
      return;
    }

    if (this.tools.isBlocked(seg.id)) {
      this.tools.openEdge(seg.id).subscribe({ next: () => {}, error: () => {} });
    } else {
      this.tools.closeEdge(seg.id).subscribe({
        next: (r: any) => {
          // Si esta era la via objetivo del evento, el aporte ya quedo registrado.
          if (r?.eventContribution) {
            this.tools.feedback.set('Objetivo cumplido: aporte registrado');
            this.eventService.refresh();
          }
        },
        error: () => {}
      });
    }
  }

  private handleTrafficBombClick(e: MouseEvent): void {
    if (!this.mapData) return;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();

    const px = (e.clientX - rect.left) * devicePixelRatio;
    const py = (e.clientY - rect.top) * devicePixelRatio;

    const world = this.canvasToWorld(px, py);
    if (!world) return;

    this.tools.trafficBomb(world.x, world.y).subscribe({
      next: () => this.worker?.postMessage({ type: 'incoming', x: world.x, y: world.y }),
      error: () => {}
    });
  }

  private handleSpeedToolClick(e: MouseEvent, tool: 'speed-trap' | 'speed-boost'): void {
    if (!this.mapData) return;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();

    const px = (e.clientX - rect.left) * devicePixelRatio;
    const py = (e.clientY - rect.top) * devicePixelRatio;

    const world = this.canvasToWorld(px, py);
    if (!world) return;

    const seg = this.findNearestSegment(world.x, world.y);
    if (!seg) {
      this.tools.feedback.set('No hay ninguna via ahi cerca');
      return;
    }

    const action$ = tool === 'speed-trap' ? this.tools.speedTrap(seg.id) : this.tools.speedBoost(seg.id);
    action$.subscribe({ next: () => {}, error: () => {} });
  }

  private handleForceGreenClick(e: MouseEvent): void {
    const hit = this.pickIntersection(e);
    if (!hit) return;
    this.tools.forceGreen(hit.id, hit.horizontal).subscribe({ next: () => {}, error: () => {} });
  }

  private handleGreenWaveClick(e: MouseEvent): void {
    const hit = this.pickIntersection(e);
    if (!hit) return;
    this.tools.greenWave(hit.id, hit.horizontal).subscribe({ next: () => {}, error: () => {} });
  }

  /** Cruce mas cercano al click y el eje que ese click sugiere (misma logica para forzar semaforo y ola verde). */
  private pickIntersection(e: MouseEvent): { id: string, horizontal: boolean } | null {
    if (!this.mapData) return null;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();

    const px = (e.clientX - rect.left) * devicePixelRatio;
    const py = (e.clientY - rect.top) * devicePixelRatio;

    const world = this.canvasToWorld(px, py);
    if (!world) return null;

    const node = this.findNearestIntersection(world.x, world.y);
    if (!node) {
      this.tools.feedback.set('No hay ningun cruce de avenidas ahi cerca');
      return null;
    }

    // Clickear a lo largo de la via horizontal fuerza el eje horizontal, y
    // viceversa -- sin necesitar un selector de eje aparte.
    const dx = Math.abs(world.x - node.x);
    const dy = Math.abs(world.y - node.y);
    return { id: node.id, horizontal: dx > dy };
  }

  /**
   * Cruces mayores: N_<fila>_<col> con fila y columna multiplo de 10 (mismo
   * convenio que MapFactory). No hace falta que el backend mande los nodos:
   * se generan los 400 candidatos localmente, igual que findNearestSegment
   * hace con las vias.
   */
  private findNearestIntersection(wx: number, wy: number): { id: string, x: number, y: number } | null {
    if (!this.mapData) return null;
    const MAX_DIST = 6;
    const BLOCK_SIZE = 10;
    const maxRow = Math.floor((this.mapData.gridHeight - 1) / 10) * 10;
    const maxCol = Math.floor((this.mapData.gridWidth - 1) / 10) * 10;

    let best: { id: string, x: number, y: number } | null = null;
    let bestDist = Infinity;

    for (let row = 0; row <= maxRow; row += 10) {
      for (let col = 0; col <= maxCol; col += 10) {
        const x = col * BLOCK_SIZE;
        const y = row * BLOCK_SIZE;
        const d = Math.hypot(wx - x, wy - y);
        if (d < bestDist) {
          bestDist = d;
          best = { id: `N_${row}_${col}`, x, y };
        }
      }
    }

    return (bestDist <= MAX_DIST) ? best : null;
  }

  private canvasToWorld(px: number, py: number): { x: number, y: number } | null {
    if (!this.mapData || this.canvasW === 0 || this.canvasH === 0) return null;

    const scaleX = this.canvasW / this.mapData.width;
    const scaleY = this.canvasH / this.mapData.height;
    const worldToCanvas = Math.min(scaleX, scaleY) * this.scale;
    const offsetX = (this.canvasW - this.mapData.width * worldToCanvas) / 2 + this.panX;
    const offsetY = (this.canvasH - this.mapData.height * worldToCanvas) / 2 + this.panY;

    return {
      x: (px - offsetX) / worldToCanvas,
      y: (py - offsetY) / worldToCanvas
    };
  }

  private findNearestSegment(wx: number, wy: number): RoadSegment | null {
    if (!this.mapData) return null;

    const all: RoadSegment[] = [...this.mapData.highways, ...this.mapData.streets];
    const MAX_DIST = 4;

    let best: RoadSegment | null = null;
    let bestDist = Infinity;

    for (const s of all) {
      const d = this.pointToSegmentDistance(wx, wy, s.x1, s.y1, s.x2, s.y2);
      if (d < bestDist) {
        bestDist = d;
        best = s;
      }
    }

    return (bestDist <= MAX_DIST) ? best : null;
  }

  private pointToSegmentDistance(px: number, py: number,
                                 ax: number, ay: number,
                                 bx: number, by: number): number {
    const dx = bx - ax;
    const dy = by - ay;
    const lenSq = dx * dx + dy * dy;
    if (lenSq === 0) return Math.hypot(px - ax, py - ay);

    let t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
    t = Math.max(0, Math.min(1, t));

    const cx = ax + t * dx;
    const cy = ay + t * dy;
    return Math.hypot(px - cx, py - cy);
  }

  resetView(): void {
    this.scale = 1;
    this.panX = 0;
    this.panY = 0;
    this.sendPan();
  }

  private initWorker(): void {
    const canvas = this.canvasRef.nativeElement;
    const offscreen = canvas.transferControlToOffscreen();

    this.worker = new Worker(new URL('./simulation.worker', import.meta.url), { type: 'module' });
    this.worker.postMessage({ type: 'init', canvas: offscreen }, [offscreen]);

    if (this.mapData) {
      this.worker.postMessage({ type: 'map', map: this.mapData });
    }

    const districts = this.districtService.districts();
    if (districts.length > 0) {
      this.worker.postMessage({
        type: 'districts',
        districts,
        myDistrictIndex: this.districtService.myDistrictIndex()
      });
    }
  }

private subscribeToFrames(): void {
    this.wsService.frames$().pipe(
      takeUntil(this.destroy$)
    ).subscribe((frame: SimulationFrame) => {
      this.simService.applyFrame(frame);
      this.worker?.postMessage({ type: 'frame', frame });
    });

    this.wsService.lights$().pipe(
      takeUntil(this.destroy$)
    ).subscribe((lights) => {
      this.worker?.postMessage({ type: 'lights', lights });
    });

    this.simService.clear$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.worker?.postMessage({ type: 'clear' });
    });
  }

  private sendPan(): void {
    this.worker?.postMessage({
      type: 'pan',
      panX: this.panX,
      panY: this.panY,
      scale: this.scale
    });
  }
}
