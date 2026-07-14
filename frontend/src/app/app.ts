import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CityViewComponent } from './features/city-view/city-view.component';
import { LoginComponent } from './features/auth/login.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, CityViewComponent, LoginComponent],
  template: `
    <app-login *ngIf="!auth.isAuthenticated()" />
    <app-city-view *ngIf="auth.isAuthenticated()" />
  `
})
export class App {
  readonly auth = inject(AuthService);
}
