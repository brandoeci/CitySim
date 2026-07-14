import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { AuthRequest } from '../../core/models/auth.model';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-screen">
      <div class="login-card">
        <div class="brand">
          <span class="brand-title">CITY SIMULATOR</span>
          <span class="brand-sub">Space-Based Architecture</span>
        </div>

        <div class="tabs">
          <button class="tab" [class.active]="mode() === 'login'" (click)="setMode('login')">
            INICIAR SESIÓN
          </button>
          <button class="tab" [class.active]="mode() === 'register'" (click)="setMode('register')">
            REGISTRARSE
          </button>
        </div>

        <div class="form">
          <label class="field-label">Usuario</label>
          <input class="field" type="text" [(ngModel)]="usernameInput"
                 placeholder="tu usuario" (keyup.enter)="submit()" />

          <label class="field-label">Contraseña</label>
          <input class="field" type="password" [(ngModel)]="passwordInput"
                 placeholder="tu contraseña" (keyup.enter)="submit()" />

          <button class="submit-btn" (click)="submit()" [disabled]="loading()">
            {{ loading() ? 'PROCESANDO...' : (mode() === 'login' ? 'ENTRAR' : 'CREAR CUENTA') }}
          </button>

          <div class="error" *ngIf="error()">{{ error() }}</div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-screen {
      position: fixed; inset: 0;
      display: flex; align-items: center; justify-content: center;
      background: #0d0f1e;
    }
    .login-card {
      display: flex; flex-direction: column; gap: 1.5rem;
      width: 340px; padding: 2.5rem 2rem;
      background: rgba(10, 12, 30, 0.95);
      border: 1px solid rgba(100, 120, 220, 0.3);
      border-radius: 8px;
      backdrop-filter: blur(10px);
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      color: #e0e4ff;
    }
    .brand { display: flex; flex-direction: column; gap: 4px; align-items: center; }
    .brand-title { font-size: 1.3rem; font-weight: 700; letter-spacing: 3px; color: #7b9fff; }
    .brand-sub { font-size: 0.6rem; letter-spacing: 2px; color: rgba(140,160,255,0.6); }

    .tabs { display: flex; gap: 0.5rem; }
    .tab {
      flex: 1; padding: 0.5rem; border: 1px solid rgba(123,159,255,0.2);
      background: transparent; color: rgba(180,190,255,0.5);
      font-family: inherit; font-size: 0.62rem; font-weight: 600;
      letter-spacing: 1px; cursor: pointer; border-radius: 5px; transition: all 0.15s;
    }
    .tab.active { background: #3d5af1; color: #fff; border-color: #3d5af1; }
    .tab:hover:not(.active) { background: rgba(123,159,255,0.08); }

    .form { display: flex; flex-direction: column; gap: 0.6rem; }
    .field-label { font-size: 0.7rem; color: rgba(200,210,255,0.7); letter-spacing: 1px; }
    .field {
      padding: 0.6rem 0.8rem; border-radius: 5px;
      border: 1px solid rgba(100,120,220,0.3);
      background: rgba(255,255,255,0.04); color: #e0e4ff;
      font-family: inherit; font-size: 0.85rem; outline: none;
    }
    .field:focus { border-color: #5b7fff; }

    .submit-btn {
      margin-top: 0.6rem; padding: 0.7rem; border: none; border-radius: 5px;
      background: #1a936f; color: #fff;
      font-family: inherit; font-size: 0.75rem; font-weight: 700;
      letter-spacing: 1.5px; cursor: pointer; transition: all 0.15s;
    }
    .submit-btn:hover:not(:disabled) { background: #22b88a; }
    .submit-btn:disabled { opacity: 0.4; cursor: not-allowed; }

    .error {
      margin-top: 0.4rem; padding: 0.5rem 0.7rem; border-radius: 5px;
      background: rgba(193,52,42,0.15); border: 1px solid rgba(193,52,42,0.4);
      color: #ff7b72; font-size: 0.7rem; text-align: center;
    }
  `]
})
export class LoginComponent {
  readonly mode = signal<'login' | 'register'>('login');
  readonly loading = signal(false);
  readonly error = signal('');

  usernameInput = '';
  passwordInput = '';

  constructor(private auth: AuthService) {}

  setMode(m: 'login' | 'register'): void {
    this.mode.set(m);
    this.error.set('');
  }

  submit(): void {
    const username = this.usernameInput.trim();
    const password = this.passwordInput;

    if (!username || !password) {
      this.error.set('Usuario y contraseña son obligatorios');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    const req: AuthRequest = { username, password };
    const call = this.mode() === 'login' ? this.auth.login(req) : this.auth.register(req);

    call.subscribe({
      next: () => {
        this.loading.set(false);
        // El signal isAuthenticated cambia solo; app.ts reacciona y muestra la simulacion
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.mapError(err));
      }
    });
  }

  private mapError(err: any): string {
    const backendMsg = err.error?.error;
    if (backendMsg) return backendMsg;

    if (err.status === 401 || err.status === 403) {
      return 'Credenciales inválidas';
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor';
    }
    return 'Error inesperado. Intenta de nuevo.';
  }
}
