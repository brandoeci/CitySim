/// <reference lib="webworker" />

import { CarState, SimulationFrame } from '../../../core/models/car-state.model';
import { CityMapData, RoadSegment } from '../../../core/models/city-map.model';
import { District } from '../../../core/models/district.model';
import { TrafficLight } from '../../../core/models/traffic-light.model';

let canvas: OffscreenCanvas | null = null;
let ctx: OffscreenCanvasRenderingContext2D | null = null;
let staticLayer: ImageBitmap | null = null;
let mapData: CityMapData | null = null;
let carMap = new Map<string, CarState>();
let animationPending = false;
let worldToCanvas = 1;
let offsetX = 0;
let offsetY = 0;
let viewScale = 1;
let viewPanX = 0;
let viewPanY = 0;

let districts: District[] = [];
let myDistrictIndex = -1;
let blockedEdges: Record<string, string> = {};
let lights: TrafficLight[] = [];
let targetEdge: string | null = null;

const DISTRICT_COLORS = [
  '#3d5af1', '#1a936f', '#c1342a', '#e0a80d', '#8b5cf6', '#0891b2'
];

const LIGHT_COLORS: Record<string, string> = {
  GREEN:  '#22c55e',
  YELLOW: '#eab308',
  RED:    '#ef4444'
};

const LIGHT_MIN_SCALE = 2.5;

function districtColor(index: number): string {
  return DISTRICT_COLORS[index % DISTRICT_COLORS.length];
}

addEventListener('message', ({ data }) => {
  switch (data.type) {
    case 'init':
      canvas = data.canvas as OffscreenCanvas;
      ctx = canvas.getContext('2d')!;
      break;
    case 'map':
      mapData = data.map as CityMapData;
      buildStaticLayer();
      scheduleRender();
      break;
    case 'districts':
      districts = (data.districts ?? []) as District[];
      myDistrictIndex = data.myDistrictIndex ?? -1;
      buildStaticLayer();
      scheduleRender();
      break;
    case 'blocked':
      blockedEdges = data.blocked ?? {};
      buildStaticLayer();
      scheduleRender();
      break;
    case 'lights':
      lights = (data.lights ?? []) as TrafficLight[];
      scheduleRender();
      break;
    case 'target':
      targetEdge = data.targetEdge ?? null;
      scheduleRender();
      break;
    case 'frame':
      applyFrame(data.frame as SimulationFrame);
      scheduleRender();
      break;
    case 'resize':
      if (canvas) {
        canvas.width = data.width;
        canvas.height = data.height;
        buildStaticLayer();
        scheduleRender();
      }
      break;
    case 'clear':
      carMap.clear();
      scheduleRender();
      break;
    case 'pan':
      viewPanX = data.panX;
      viewPanY = data.panY;
      viewScale = data.scale;
      buildStaticLayer();
      scheduleRender();
      break;
  }
});

function applyFrame(frame: SimulationFrame): void {
  for (const delta of frame.deltas) {
    if (delta.status === 'ARRIVED') {
      carMap.delete(delta.carId);
    } else {
      carMap.set(delta.carId, {
        carId: delta.carId,
        x: delta.x,
        y: delta.y,
        heading: delta.heading,
        status: delta.status,
        color: delta.color
      });
    }
  }
  for (const id of frame.removedCarIds) {
    carMap.delete(id);
  }
}

function scheduleRender(): void {
  if (!animationPending) {
    animationPending = true;
    setTimeout(render, 0);
  }
}

function isBlocked(seg: RoadSegment): boolean {
  return seg.id in blockedEdges || (seg.id + '_R') in blockedEdges;
}

function findSegment(edgeId: string): RoadSegment | null {
  if (!mapData) return null;
  const base = edgeId.endsWith('_R') ? edgeId.slice(0, -2) : edgeId;
  for (const s of mapData.highways) if (s.id === base) return s;
  for (const s of mapData.streets)  if (s.id === base) return s;
  return null;
}

function districtBounds(d: District): { x: number, y: number, w: number, h: number } | null {
  if (!mapData) return null;
  const zoneIds = new Set(d.zoneIds);
  const zones = mapData.zones.filter(z => zoneIds.has(z.id));
  if (zones.length === 0) return null;

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const z of zones) {
    minX = Math.min(minX, z.x);
    minY = Math.min(minY, z.y);
    maxX = Math.max(maxX, z.x + z.w);
    maxY = Math.max(maxY, z.y + z.h);
  }
  return { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
}

function drawSegments(sCtx: OffscreenCanvasRenderingContext2D,
                      segs: RoadSegment[], color: string, worldWidth: number): void {
  sCtx.strokeStyle = color;
  sCtx.lineWidth = Math.max(1, worldWidth * worldToCanvas);
  sCtx.beginPath();
  for (const s of segs) {
    if (isBlocked(s)) continue;
    sCtx.moveTo(worldX(s.x1), worldY(s.y1));
    sCtx.lineTo(worldX(s.x2), worldY(s.y2));
  }
  sCtx.stroke();
}

function drawBlocked(sCtx: OffscreenCanvasRenderingContext2D,
                     segs: RoadSegment[], worldWidth: number): void {
  const blocked = segs.filter(isBlocked);
  if (blocked.length === 0) return;

  sCtx.strokeStyle = '#c1342a';
  sCtx.lineWidth = Math.max(2, worldWidth * worldToCanvas);
  sCtx.beginPath();
  for (const s of blocked) {
    sCtx.moveTo(worldX(s.x1), worldY(s.y1));
    sCtx.lineTo(worldX(s.x2), worldY(s.y2));
  }
  sCtx.stroke();

  sCtx.strokeStyle = '#ff7b72';
  sCtx.lineWidth = 2;
  sCtx.beginPath();
  for (const s of blocked) {
    const mx = worldX((s.x1 + s.x2) / 2);
    const my = worldY((s.y1 + s.y2) / 2);
    const r = Math.max(3, worldToCanvas * 2);
    sCtx.moveTo(mx - r, my - r); sCtx.lineTo(mx + r, my + r);
    sCtx.moveTo(mx + r, my - r); sCtx.lineTo(mx - r, my + r);
  }
  sCtx.stroke();
}

function buildStaticLayer(): void {
  if (!canvas || !ctx || !mapData) return;

  const w = canvas.width;
  const h = canvas.height;

  computeTransform(w, h);

  const offscreen = new OffscreenCanvas(w, h);
  const sCtx = offscreen.getContext('2d')!;

  sCtx.fillStyle = '#1a1a2e';
  sCtx.fillRect(0, 0, w, h);

  for (const d of districts) {
    const b = districtBounds(d);
    if (!b) continue;

    const x1 = worldX(b.x);
    const y1 = worldY(b.y);
    const dw = b.w * worldToCanvas;
    const dh = b.h * worldToCanvas;
    const color = districtColor(d.index);
    const isMine = d.index === myDistrictIndex;

    sCtx.globalAlpha = isMine ? 0.38 : 0.20;
    sCtx.fillStyle = color;
    sCtx.fillRect(x1, y1, dw, dh);

    sCtx.globalAlpha = isMine ? 1.0 : 0.6;
    sCtx.strokeStyle = color;
    sCtx.lineWidth = isMine ? 5 : 2;
    sCtx.strokeRect(x1, y1, dw, dh);

    sCtx.globalAlpha = 1.0;
  }

  drawSegments(sCtx, mapData.streets, '#2d3561', 2.6);
  drawSegments(sCtx, mapData.highways, '#4a5080', 4.6);

  drawBlocked(sCtx, mapData.streets, 2.6);
  drawBlocked(sCtx, mapData.highways, 4.6);

  staticLayer = offscreen.transferToImageBitmap();
}

/**
 * La via que este administrador debe cerrar para responder al evento.
 * Va en la capa dinamica porque parpadea: es lo que guia la accion del usuario.
 */
function drawTarget(c: OffscreenCanvasRenderingContext2D): void {
  if (!targetEdge) return;
  const s = findSegment(targetEdge);
  if (!s) return;

  // Parpadeo suave (periodo de 1.2 s)
  const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);

  const x1 = worldX(s.x1), y1 = worldY(s.y1);
  const x2 = worldX(s.x2), y2 = worldY(s.y2);

  // Halo exterior
  c.globalAlpha = 0.25 * pulse;
  c.strokeStyle = '#e0a80d';
  c.lineWidth = Math.max(8, 12 * worldToCanvas);
  c.lineCap = 'round';
  c.beginPath();
  c.moveTo(x1, y1); c.lineTo(x2, y2);
  c.stroke();

  // Trazo naranja
  c.globalAlpha = pulse;
  c.strokeStyle = '#f59e0b';
  c.lineWidth = Math.max(3, 5 * worldToCanvas);
  c.beginPath();
  c.moveTo(x1, y1); c.lineTo(x2, y2);
  c.stroke();

  // Marcador en el centro
  const mx = (x1 + x2) / 2;
  const my = (y1 + y2) / 2;
  const r = Math.max(6, worldToCanvas * 4);

  c.globalAlpha = 1.0;
  c.fillStyle = '#f59e0b';
  c.beginPath();
  c.arc(mx, my, r, 0, Math.PI * 2);
  c.fill();

  c.strokeStyle = '#0d0f1e';
  c.lineWidth = 2;
  c.stroke();

  // Signo de exclamacion
  c.fillStyle = '#0d0f1e';
  c.font = `700 ${Math.max(9, r * 1.3)}px monospace`;
  c.textAlign = 'center';
  c.textBaseline = 'middle';
  c.fillText('!', mx, my + 1);

  c.globalAlpha = 1.0;
  c.lineCap = 'butt';
}

/**
 * Cuatro luces por cruce: dos para el eje horizontal (este y oeste) y dos para
 * el vertical (norte y sur). Los ejes estan siempre en oposicion, asi que si
 * las horizontales estan verdes, las verticales estan rojas.
 */
function drawLights(c: OffscreenCanvasRenderingContext2D): void {
  if (viewScale < LIGHT_MIN_SCALE || lights.length === 0) return;

  const r = Math.max(2, worldToCanvas * 0.8);
  const d = Math.max(5, worldToCanvas * 2.2);   // separacion desde el centro

  for (const l of lights) {
    const cx = worldX(l.x);
    const cy = worldY(l.y);

    const hColor = LIGHT_COLORS[l.horizontalState] ?? '#94a3b8';
    const vColor = LIGHT_COLORS[l.verticalState] ?? '#94a3b8';

    // Eje horizontal: luces a izquierda y derecha del cruce
    dot(c, cx - d, cy, r, hColor);
    dot(c, cx + d, cy, r, hColor);

    // Eje vertical: luces arriba y abajo
    dot(c, cx, cy - d, r, vColor);
    dot(c, cx, cy + d, r, vColor);
  }

  c.globalAlpha = 1.0;
}

function dot(c: OffscreenCanvasRenderingContext2D,
             x: number, y: number, r: number, color: string): void {
  c.globalAlpha = 0.3;
  c.fillStyle = color;
  c.beginPath();
  c.arc(x, y, r * 1.9, 0, Math.PI * 2);
  c.fill();

  c.globalAlpha = 1.0;
  c.fillStyle = color;
  c.beginPath();
  c.arc(x, y, r, 0, Math.PI * 2);
  c.fill();

  c.strokeStyle = 'rgba(10,12,30,0.85)';
  c.lineWidth = 1;
  c.stroke();
}

function render(): void {
  animationPending = false;
  if (!canvas || !ctx || !mapData) return;

  const w = canvas.width;
  const h = canvas.height;

  ctx.clearRect(0, 0, w, h);
  if (staticLayer) {
    ctx.drawImage(staticLayer, 0, 0);
  } else {
    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(0, 0, w, h);
  }

  drawTarget(ctx);
  drawLights(ctx);

  const carSize = Math.max(1.5, worldToCanvas * 0.9);
  for (const car of carMap.values()) {
    drawCar(ctx, car, carSize);
  }

  // El objetivo parpadea: hay que repintar aunque no lleguen frames.
  if (targetEdge && !animationPending) {
    animationPending = true;
    setTimeout(render, 60);
  }
}

function drawCar(c: OffscreenCanvasRenderingContext2D, car: CarState, size: number): void {
  const cx = worldX(car.x);
  const cy = worldY(car.y);

  c.save();
  c.translate(cx, cy);
  c.rotate(car.heading);

  c.fillStyle = car.color;
  c.globalAlpha = car.status === 'WAITING_LIGHT' ? 0.6 :
                  car.status === 'WAITING_TRAFFIC' ? 0.7 : 1.0;
  c.fillRect(-size, -size * 0.5, size * 2, size);

  c.fillStyle = '#ffffff';
  c.globalAlpha = 0.9;
  c.fillRect(size * 0.7, -size * 0.15, size * 0.3, size * 0.3);

  c.globalAlpha = 1.0;
  c.restore();
}

function computeTransform(w: number, h: number): void {
  if (!mapData) return;
  const scaleX = w / mapData.width;
  const scaleY = h / mapData.height;
  worldToCanvas = Math.min(scaleX, scaleY) * viewScale;
  offsetX = (w - mapData.width * worldToCanvas) / 2 + viewPanX;
  offsetY = (h - mapData.height * worldToCanvas) / 2 + viewPanY;
}

function worldX(wx: number): number {
  return wx * worldToCanvas + offsetX;
}

function worldY(wy: number): number {
  return wy * worldToCanvas + offsetY;
}
