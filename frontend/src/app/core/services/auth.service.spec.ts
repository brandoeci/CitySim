import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts unauthenticated when localStorage is empty', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.inRoom()).toBe(false);
  });

  it('persists the session and updates signals on login', () => {
    service.login({ username: 'ana', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'jwt-1', username: 'ana', zoneId: 'Z_1_1' });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe('ana');
    expect(service.zoneId()).toBe('Z_1_1');
    expect(localStorage.getItem('citysim_token')).toBe('jwt-1');
  });

  it('clears the session on logout', () => {
    service.login({ username: 'ana', password: 'secret123' }).subscribe();
    httpMock.expectOne('/api/auth/login')
      .flush({ token: 'jwt-1', username: 'ana', zoneId: 'Z_1_1' });

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBeNull();
    expect(service.zoneId()).toBeNull();
    expect(localStorage.getItem('citysim_token')).toBeNull();
  });

  it('createRoom creates the room and then joins it with the room-scoped token', () => {
    service.createRoom('Sala de prueba').subscribe();

    const createReq = httpMock.expectOne('/api/rooms');
    expect(createReq.request.method).toBe('POST');
    createReq.flush({ code: 'ABC123' });

    const joinReq = httpMock.expectOne('/api/rooms/ABC123/join');
    expect(joinReq.request.method).toBe('POST');
    joinReq.flush({ token: 'jwt-room', roomCode: 'ABC123' });

    expect(service.inRoom()).toBe(true);
    expect(service.roomCode()).toBe('ABC123');
    expect(localStorage.getItem('citysim_room')).toBe('ABC123');
    expect(localStorage.getItem('citysim_token')).toBe('jwt-room');
  });

  it('leaveRoom notifies the backend and clears the local room state', () => {
    service.joinRoom('XYZ999').subscribe();
    httpMock.expectOne('/api/rooms/XYZ999/join')
      .flush({ token: 'jwt-room', roomCode: 'XYZ999' });

    service.leaveRoom();

    httpMock.expectOne('/api/rooms/XYZ999/leave').flush({});
    expect(service.roomCode()).toBeNull();
    expect(localStorage.getItem('citysim_room')).toBeNull();
  });
});
