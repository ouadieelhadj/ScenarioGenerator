import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Scenario {
  id?: string;
  name: string;
  scheme: string;
  channel: string;
  family: string;
  operationType: string;
  processingMode: string;
  parameters?: Record<string, unknown>;
  seed?: number;
  version?: number;
  createdAt?: string;
}

export interface ExecutionResult {
  transactionId: string;
  mti: string;
  stan: string;
  rrn: string;
  responseCode: string;
  authCode: string;
  status: string;
  requestHex: string;
  responseHex: string;
  seed: number;
  warnings: string[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class ScenarioService {

  private readonly BASE = '/api/scenarios';

  constructor(private http: HttpClient) {}

  list(page = 0, size = 20): Observable<Page<Scenario>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);
    return this.http.get<Page<Scenario>>(this.BASE, { params });
  }

  get(id: string): Observable<Scenario> {
    return this.http.get<Scenario>(`${this.BASE}/${id}`);
  }

  create(scenario: Scenario): Observable<Scenario> {
    return this.http.post<Scenario>(this.BASE, scenario);
  }

  update(id: string, scenario: Scenario): Observable<Scenario> {
    return this.http.put<Scenario>(`${this.BASE}/${id}`, scenario);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/${id}`);
  }

  execute(id: string, channel = 'CIS_VISA'): Observable<ExecutionResult> {
    return this.http.post<ExecutionResult>(
      `${this.BASE}/${id}/execute`,
      null,
      { params: { channel } }
    );
  }
}
