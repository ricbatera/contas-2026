import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BucketService } from './bucket.service';

describe('BucketService', () => {
  let http: HttpTestingController;
  let service: BucketService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(BucketService);
  });

  afterEach(() => http.verify());

  it('lists bucket consumption for the selected competence', () => {
    let result: unknown;
    service.listar(10, 2026).subscribe((buckets) => result = buckets);
    const request = http.expectOne((item) =>
      item.url === '/api/buckets' && item.params.get('mes') === '10' && item.params.get('ano') === '2026',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ data: [] });
    expect(result).toEqual([]);
  });

  it('creates, updates and deletes buckets', () => {
    const request = { subcategoriaId: 'subcategory-1', responsavelPessoaId: null, valorMensal: 200 };
    service.criar(request).subscribe();
    const creation = http.expectOne('/api/buckets');
    expect(creation.request.method).toBe('POST');
    expect(creation.request.body).toEqual(request);
    creation.flush({ data: {} });

    service.atualizar('bucket-1', request).subscribe();
    const update = http.expectOne('/api/buckets/bucket-1');
    expect(update.request.method).toBe('PUT');
    update.flush({ data: {} });

    service.excluir('bucket-1').subscribe();
    const deletion = http.expectOne('/api/buckets/bucket-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });
});
