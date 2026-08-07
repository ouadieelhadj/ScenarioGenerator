import { InjectionToken, Type } from '@angular/core';

export type ScreenLoader = (componentKey?: string) => Promise<Type<unknown> | null>;

export const SCREEN_LOADER = new InjectionToken<ScreenLoader>('SCREEN_LOADER');
