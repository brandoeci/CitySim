import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ToolService } from './tool.service';

describe('ToolService', () => {
  let service: ToolService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ToolService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('toggles the active tool off when selected twice', () => {
    service.selectTool('force-green');
    expect(service.activeTool()).toBe('force-green');

    service.selectTool('force-green');
    expect(service.activeTool()).toBe('none');
  });

  it('switches directly between two different tools', () => {
    service.selectTool('force-green');
    service.selectTool('speed-trap');
    expect(service.activeTool()).toBe('speed-trap');
  });

  it('closeEdge sets success feedback and refreshes blocked edges', () => {
    service.closeEdge('E_1_1').subscribe();

    const closeReq = httpMock.expectOne('/api/tools/close-edge');
    expect(closeReq.request.method).toBe('POST');
    expect(closeReq.request.body).toEqual({ edgeId: 'E_1_1' });
    closeReq.flush({});

    expect(service.feedback()).toBe('Via cerrada');

    httpMock.expectOne('/api/tools/blocked-edges').flush({ blocked: { E_1_1: 'ana' } });
    httpMock.expectOne('/api/tools/speed-overrides').flush({ overrides: {} });

    expect(service.isBlocked('E_1_1')).toBe(true);
    expect(service.isBlocked('E_9_9')).toBe(false);
  });

  it('surfaces the backend error message when a tool call is rejected', () => {
    service.closeEdge('E_9_9').subscribe({ error: () => {} });

    httpMock.expectOne('/api/tools/close-edge')
      .flush({ error: 'No administras ese distrito' }, { status: 403, statusText: 'Forbidden' });

    expect(service.feedback()).toBe('No administras ese distrito');
  });

  it('districtShield stores the active and cooldown windows from the response', () => {
    const before = Date.now();
    service.districtShield().subscribe();

    httpMock.expectOne('/api/tools/district-shield')
      .flush({ durationSeconds: 20, cooldownSeconds: 90 });

    expect(service.feedback()).toBe('Escudo de distrito activado');
    expect(service.shieldActiveUntil()).toBeGreaterThanOrEqual(before + 20_000);
    expect(service.shieldCooldownUntil()).toBeGreaterThanOrEqual(before + 90_000);
  });
});
