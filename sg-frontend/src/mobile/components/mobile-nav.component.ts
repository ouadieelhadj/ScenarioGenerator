import { Component, DestroyRef, HostListener, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { IonContent, IonHeader, IonMenu, IonTitle, IonToolbar, MenuController } from '@ionic/angular/standalone';
import { MobileAuthService } from '../services/mobile-auth.service';
import { mobileLabel, mobileMenuSections } from '../services/mobile-presentation';

@Component({ selector: 'app-mobile-nav', standalone: true,
  imports: [RouterLink, RouterLinkActive, IonMenu, IonHeader, IonToolbar, IonTitle, IonContent], template: `
  <ion-menu menuId="merchant-main-menu" contentId="merchant-main-content" side="start" type="overlay"
    [disabled]="!auth.user()" aria-label="Menu principal" class="merchant-menu"
    (ionWillOpen)="opening()" (ionDidOpen)="opened()" (ionDidClose)="closed()">
    <ion-header><ion-toolbar>
      <ion-title>FuturPayment Merchant</ion-title>
      <button slot="end" type="button" class="menu-close" aria-label="Fermer le menu" (click)="close()">×</button>
    </ion-toolbar></ion-header>
    <ion-content>
      @if (auth.user(); as user) {
        <div class="menu-identity"><strong>{{user.login}}</strong><span>{{roleLabel(user.role)}}</span>
          @if (user.acquirerId) { <small>Acquéreur : {{user.acquirerId}}</small> }
        </div>
        <nav class="mobile-menu-links" aria-label="Navigation principale">
          <a routerLink="/home" routerLinkActive="selected" [routerLinkActiveOptions]="{exact:true}"
            ariaCurrentWhenActive="page" (click)="close()">Accueil</a>
          <a routerLink="/merchant/incidents" routerLinkActive="selected" ariaCurrentWhenActive="page" (click)="close()">Assistance · Incidents</a>
          @for (section of sections(); track section.id) {
            <details [attr.data-menu-section]="section.id" [open]="sectionActive(section.id)">
              <summary>{{section.label}}</summary>
              @for (item of section.items; track item.route) {
                <a [routerLink]="item.route" routerLinkActive="selected" [routerLinkActiveOptions]="{exact:item.exact}"
                  ariaCurrentWhenActive="page" (click)="close()">{{item.label}}</a>
              }
            </details>
          }
          <button type="button" class="menu-logout" (click)="logout()">Se déconnecter</button>
        </nav>
      }
    </ion-content>
  </ion-menu>` })
export class MobileNavComponent {
  readonly auth = inject(MobileAuthService);
  private readonly menu = inject(MenuController);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly sections = computed(() => mobileMenuSections(this.auth.user()));
  readonly roleLabel = mobileLabel;
  private closeRequested = false;
  private openingOrOpen = false;

  constructor() {
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(event => {
      if (event instanceof NavigationStart) void this.close();
    });
    effect(() => {
      // Any logout or identity/context change closes the previous user's drawer.
      this.auth.user();
      void this.close();
    });
  }
  sectionActive(id: string): boolean {
    return this.sections().find(section => section.id === id)?.items.some(item =>
      this.router.url === item.route || (!item.exact && this.router.url.startsWith(item.route + '/'))) || false;
  }
  opening(): void { this.closeRequested = false; this.openingOrOpen = true; }
  closed(): void { this.openingOrOpen = false; }
  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.openingOrOpen) { event.preventDefault(); void this.close(); }
  }
  opened(): void {
    // Ionic ignores close() while opening is animated. Honor a navigation/logout
    // that occurred during that interval as soon as opening completes.
    if (this.closeRequested) void this.close();
  }
  async close(): Promise<void> {
    this.closeRequested = true;
    await this.menu.close('merchant-main-menu');
  }
  async logout(): Promise<void> {
    this.auth.logout();
    await this.close();
    await this.router.navigateByUrl('/login', { replaceUrl: true });
  }
}
