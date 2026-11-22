import type { MerchantAddressV2 } from './merchant-onboarding.models';

export interface AddressSuggestion {
  id: string;
  label: string;
  address: MerchantAddressV2;
  attribution: string;
  credits?: { label: string; url: string }[];
}
export interface AddressPrediction { id: string; label: string; attribution: string; }
export type AddressSelectionStatus = 'IDLE' | 'SEARCHING' | 'PROPOSED' | 'NOT_FOUND' | 'UNAVAILABLE' | 'INVALID' | 'STALE';

/** Suggestions are transient. Only an explicit confirmation emits address fields.
 * Internal region/city/activity routing codes are deliberately outside this contract. */
export class MerchantAddressSelection {
  mode: 'MANUAL' | 'GOOGLE' = 'MANUAL';
  status: AddressSelectionStatus = 'IDLE';
  suggestions: AddressSuggestion[] = [];
  predictions: AddressPrediction[] = [];
  private generation = 0;
  private base = '';

  setMode(mode: 'MANUAL' | 'GOOGLE'): void {
    this.mode = mode;
    this.cancel();
  }
  cancel(): void {
    this.generation++;
    this.base = '';
    this.suggestions = [];
    this.predictions = [];
    this.status = 'IDLE';
  }
  begin(address: MerchantAddressV2): number {
    this.cancel();
    this.base = JSON.stringify(address);
    this.status = 'SEARCHING';
    return this.generation;
  }
  receive(generation: number, values: unknown): void {
    if (generation !== this.generation || this.mode !== 'GOOGLE') return;
    if (!Array.isArray(values) || values.length > 5 || !values.every(validSuggestion)) {
      this.suggestions = []; this.status = 'INVALID'; return;
    }
    if (new Set(values.map(v => v.id)).size !== values.length) {
      this.suggestions = []; this.status = 'INVALID'; return;
    }
    this.predictions = [];
    this.suggestions = values.map(v => ({ id: v.id, label: v.label, attribution: v.attribution,
      credits: v.credits?.map(c => ({ label: c.label, url: c.url })),
      address: { line1: v.address.line1, line2: v.address.line2, district: v.address.district,
        city: v.address.city, region: v.address.region, postalCode: v.address.postalCode, country: v.address.country } }));
    this.status = values.length ? 'PROPOSED' : 'NOT_FOUND';
  }
  receivePredictions(generation: number, values: unknown): void {
    if (generation !== this.generation || this.mode !== 'GOOGLE') return;
    this.suggestions = []; this.predictions = [];
    if (!Array.isArray(values) || values.length > 5 || !values.every(validPrediction)
      || new Set(values.map(v => v.id)).size !== values.length) { this.status = 'INVALID'; return; }
    this.predictions = values.map(v => ({ id: v.id, label: v.label, attribution: v.attribution }));
    this.status = values.length ? 'PROPOSED' : 'NOT_FOUND';
  }
  choose(id: string, current: MerchantAddressV2): number | null {
    if (this.mode !== 'GOOGLE' || this.status !== 'PROPOSED' || !this.predictions.some(v => v.id === id)) return null;
    if (JSON.stringify(current) !== this.base) { this.cancel(); this.status = 'STALE'; return null; }
    this.predictions = []; this.suggestions = []; this.status = 'SEARCHING';
    return ++this.generation;
  }
  unavailable(generation: number): void {
    if (generation !== this.generation || this.mode !== 'GOOGLE') return;
    this.suggestions = []; this.predictions = []; this.status = 'UNAVAILABLE';
  }
  confirm(id: string, current: MerchantAddressV2): MerchantAddressV2 | null {
    if (this.mode !== 'GOOGLE' || this.status !== 'PROPOSED') return null;
    if (JSON.stringify(current) !== this.base) {
      this.cancel(); this.status = 'STALE'; return null;
    }
    const value = this.suggestions.find(v => v.id === id);
    if (!value) return null;
    const address = structuredClone(value.address);
    this.setMode('MANUAL');
    return address;
  }
}

function text(value: unknown, max: number, required = false): boolean {
  return value === null ? !required : typeof value === 'string' && value.length <= max
    && (!required || value.trim().length > 0) && !/[\x00-\x1f\x7f]/.test(value);
}
export function validSuggestion(value: unknown): value is AddressSuggestion {
  if (!value || typeof value !== 'object') return false;
  const v = value as AddressSuggestion, a = v.address;
  return typeof v.id === 'string' && /^[A-Za-z0-9_-]{1,200}$/.test(v.id)
    && text(v.label, 500, true) && text(v.attribution, 300, true) && !!a
    && text(a.line1, 255, true) && text(a.line2, 255) && text(a.district, 120)
    && text(a.city, 120, true) && text(a.region, 120) && text(a.postalCode, 24)
    && typeof a.country === 'string' && /^[A-Z]{2}$/.test(a.country)
    && (v.credits === undefined || (Array.isArray(v.credits) && v.credits.length <= 10 && v.credits.every(c => {
      if (!c || !text(c.label, 300, true) || typeof c.url !== 'string' || c.url.length > 2048) return false;
      try { const uri = new URL(c.url); return uri.protocol === 'https:' && !!uri.hostname && !uri.username && !uri.password; }
      catch { return false; }
    })));
}
export function validPrediction(value: unknown): value is AddressPrediction {
  if (!value || typeof value !== 'object') return false;
  const v = value as AddressPrediction;
  return typeof v.id === 'string' && /^[A-Za-z0-9_-]{1,200}$/.test(v.id) && text(v.label, 500, true) && text(v.attribution, 300, true);
}
