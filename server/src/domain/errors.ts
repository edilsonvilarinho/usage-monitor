/**
 * Erros de dominio do servidor de time.
 *
 * Cada classe carrega um `code` estavel e o `errorHandler` HTTP mapeia a classe
 * para o status. Handlers nunca escrevem status na mao.
 */
export class DomainError extends Error {
  readonly code: string;

  constructor(message: string, code: string) {
    super(message);
    this.name = new.target.name;
    this.code = code;
  }
}

export class ValidationError extends DomainError {
  constructor(message: string) {
    super(message, 'validation_error');
  }
}

export class UnauthorizedError extends DomainError {
  constructor(message = 'Credencial de time ausente ou invalida.') {
    super(message, 'unauthorized');
  }
}

/**
 * Recurso pedido nao existe dentro do escopo da conta.
 *
 * Mesmo `code` que o handler de rota desconhecida ja usa: para o cliente, uma
 * sessao ausente e uma rota ausente sao o mesmo caso — nao ha detalhe a mostrar.
 */
export class NotFoundError extends DomainError {
  constructor(message: string) {
    super(message, 'not_found');
  }
}

export class ServiceUnavailableError extends DomainError {
  constructor(message: string) {
    super(message, 'service_unavailable');
  }
}
