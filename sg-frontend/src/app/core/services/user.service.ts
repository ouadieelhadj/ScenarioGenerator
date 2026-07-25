import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { User, CreateUserRequest } from '../models/admin.models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private ep = ENDPOINTS.users;

  findAll(): Observable<User[]> {
    return this.http.get<User[]>(url.orchestrator(this.ep.base));
  }

  create(req: CreateUserRequest): Observable<User> {
    return this.http.post<User>(url.orchestrator(this.ep.base), req);
  }

  update(id: number, req: CreateUserRequest): Observable<User> {
    return this.http.put<User>(url.orchestrator(this.ep.byId(id)), req);
  }

  toggle(id: number): Observable<unknown> {
    return this.http.put(url.orchestrator(this.ep.toggle(id)), {});
  }
}

