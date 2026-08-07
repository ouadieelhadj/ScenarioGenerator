import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterOutlet } from '@angular/router';
import { PORTAL_PRODUCT } from './core/product/product.config';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class AppComponent {
  private readonly title = inject(Title);
  private readonly product = inject(PORTAL_PRODUCT);

  constructor() {
    this.title.setTitle(this.product.brand);
  }
}
