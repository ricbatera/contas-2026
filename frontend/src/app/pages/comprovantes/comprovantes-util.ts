import { ModalidadeComprovante, StatusComprovante } from '../../core/comprovante.service';

export const ROTULO_STATUS_COMPROVANTE: Record<StatusComprovante, string> = {
  PENDENTE: 'Aguardando aprovação',
  APROVADO: 'Aprovado',
  REJEITADO: 'Rejeitado',
};

export function rotuloModalidade(modalidade: ModalidadeComprovante, parcelas: number): string {
  if (modalidade === 'DEBITO') {
    return 'Débito';
  }
  return parcelas > 1 ? `Crédito em ${parcelas}x` : 'Crédito à vista';
}
