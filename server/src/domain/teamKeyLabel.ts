import { normalizeAccountEmail } from './accountEmail.js';

/**
 * Separadores aceitos entre e-mails de um rotulo.
 *
 * Virgula e ponto e virgula sao o que quem administra digita ao listar duas
 * pessoas; o espaco entra junto porque um rotulo colado de planilha costuma
 * trazer os tres misturados.
 */
const LABEL_SEPARATORS = /[,;\s]+/;

/**
 * E-mails declarados no rotulo de uma chave de time.
 *
 * O rotulo sempre foi texto livre — normalmente o e-mail da pessoa, digitado por
 * quem administra — e o servidor nunca o leu. Ele passa a ser a **relacao do
 * time**: quem esta ali pode usar a chave, quem nao esta nao pode.
 *
 * Devolve **conjunto** e nao um e-mail so porque a mesma chave cobre a maquina
 * logada em duas contas da empresa (`maxAccounts` maior que 1), e com um e-mail
 * unico esse caso — que ja estava documentado e em uso — morreria.
 *
 * Conjunto vazio significa "rotulo sem e-mail nenhum" (nome de pessoa, setor, o
 * que for) e desliga a verificacao: e o que preserva o comportamento de quem
 * nunca usou e-mail no rotulo. Pedaco malformado no meio de validos e ignorado,
 * nao invalida o resto — meio rotulo legivel ainda descreve meio time.
 */
export function parseKeyLabelEmails(label: string | null | undefined): Set<string> {
  const emails = new Set<string>();
  if (label == null) {
    return emails;
  }

  for (const piece of label.split(LABEL_SEPARATORS)) {
    const normalized = normalizeAccountEmail(piece);
    if (normalized !== null) {
      emails.add(normalized);
    }
  }

  return emails;
}

/**
 * A conta pode usar uma chave com este rotulo?
 *
 * Duas recusas viram `true` de proposito, e as duas estao registradas:
 *
 *  - **rotulo sem e-mail**: nao ha relacao declarada a conferir, e inventar uma
 *    barraria instalacoes que rotulam a chave com o nome da pessoa.
 *  - **e-mail da conta desconhecido**: cliente anterior ao campo `accountEmail`
 *    nao reporta nada, e recusar ali derrubaria maquina que a mudanca nao
 *    pretende atingir. O buraco e assumido: quem quisesse burlar bastaria omitir
 *    o campo, e o modelo inteiro de chave de time e autodeclarado. Contra isso
 *    existe a lista de bloqueio, que e por `accountKey` e nao por e-mail.
 */
export function isAccountAllowedByLabel(
  label: string | null | undefined,
  accountEmail: string | null | undefined,
): boolean {
  const declared = parseKeyLabelEmails(label);
  if (declared.size === 0) {
    return true;
  }

  const normalized = normalizeAccountEmail(accountEmail);
  if (normalized === null) {
    return true;
  }

  return declared.has(normalized);
}
