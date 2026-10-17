import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  const tokens = { accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 1 };

  it('stores tokens in localStorage when remember is true', () => {
    service.login({ email: 'x@y.com', senha: '1' }, true).subscribe();
    http.expectOne('/api/auth/login').flush({ data: tokens });
    expect(localStorage.getItem('accessToken')).toBe('a');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('stores tokens in sessionStorage when remember is false', () => {
    service.login({ email: 'x@y.com', senha: '1' }, false).subscribe();
    http.expectOne('/api/auth/login').flush({ data: tokens });
    expect(sessionStorage.getItem('accessToken')).toBe('a');
    expect(localStorage.getItem('accessToken')).toBeNull();
  });
});
