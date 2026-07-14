import { Injectable, OnDestroy } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { SimulationFrame } from '../models/car-state.model';
import { TrafficLight } from '../models/traffic-light.model';
import { Observable, Subject } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private stomp = new RxStomp();
  private destroy$ = new Subject<void>();

  readonly connected$ = this.stomp.connected$;

  connect(): void {
    const config: RxStompConfig = {
      brokerURL: this.buildWsUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: () => {}
    };
    this.stomp.configure(config);
    this.stomp.activate();
  }

  frames$(): Observable<SimulationFrame> {
    return this.stomp.watch('/topic/cars').pipe(
      takeUntil(this.destroy$),
      map(msg => JSON.parse(msg.body) as SimulationFrame)
    );
  }

  /**
   * Avisa que el evento activo cambio. El detalle personalizado (mi objetivo,
   * si ya respondi) lo pide el EventService al REST, que sabe quien soy: el
   * broadcast va igual para todos y no puede saberlo.
   */
  events$(): Observable<unknown> {
    return this.stomp.watch('/topic/events').pipe(
      takeUntil(this.destroy$),
      map(msg => JSON.parse(msg.body))
    );
  }

  lights$(): Observable<TrafficLight[]> {
    return this.stomp.watch('/topic/lights').pipe(
      takeUntil(this.destroy$),
      map(msg => JSON.parse(msg.body) as TrafficLight[])
    );
  }

  disconnect(): void {
    this.stomp.deactivate();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }

  private buildWsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    return `${protocol}//${host}/ws/websocket`;
  }
}
