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

    // La via que este administrador debe cerrar para responder al evento.
    effect(() => {
      const target = this.eventService.myTargetEdge();
      this.worker?.postMessage({ type: 'target', targetEdge: target });
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
    if (wasDragging && !this.movedWhileDown && this.tools.activeTool() === 'close-road') {
      this.handleRoadClick(e);
    }
  }

  onMouseLeave(): void {
    this.dragging = false;
  }

  onWheel(e: WheelEvent): void {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    this.scale = Math.max(0.3, Math.min(this.scale * delta, 20));
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
