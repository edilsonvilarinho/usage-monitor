import { describe, expect, it } from 'vitest';
import { isAccountAllowedByLabel, parseKeyLabelEmails } from '../../src/domain/teamKeyLabel.js';

describe('parseKeyLabelEmails', () => {
  it('devolve conjunto vazio para rotulo ausente ou em branco', () => {
    expect(parseKeyLabelEmails(null).size).toBe(0);
    expect(parseKeyLabelEmails(undefined).size).toBe(0);
    expect(parseKeyLabelEmails('   ').size).toBe(0);
  });

  it('devolve conjunto vazio para rotulo sem e-mail nenhum', () => {
    expect(parseKeyLabelEmails('Helio - Comercial').size).toBe(0);
  });

  it('extrai o e-mail unico do rotulo', () => {
    expect([...parseKeyLabelEmails('helio.sales@informata.com.br')]).toEqual([
      'helio.sales@informata.com.br',
    ]);
  });

  it('normaliza caixa e espaco em volta', () => {
    expect([...parseKeyLabelEmails('  HELIO.Sales@Informata.COM.BR  ')]).toEqual([
      'helio.sales@informata.com.br',
    ]);
  });

  it('extrai varios e-mails com virgula, ponto e virgula e espaco', () => {
    const emails = parseKeyLabelEmails('a@empresa.com, b@empresa.com; c@empresa.com d@empresa.com');
    expect([...emails].sort()).toEqual([
      'a@empresa.com',
      'b@empresa.com',
      'c@empresa.com',
      'd@empresa.com',
    ]);
  });

  // Meio rotulo legivel ainda descreve meio time: o pedaco quebrado sai, o resto fica.
  it('ignora pedaco malformado no meio de validos', () => {
    const emails = parseKeyLabelEmails('a@empresa.com, nao-e-email, b@empresa.com');
    expect([...emails].sort()).toEqual(['a@empresa.com', 'b@empresa.com']);
  });

  it('nao repete o mesmo e-mail escrito duas vezes', () => {
    expect(parseKeyLabelEmails('a@empresa.com; A@EMPRESA.COM').size).toBe(1);
  });
});

describe('isAccountAllowedByLabel', () => {
  it('aceita quando o e-mail da conta esta no rotulo', () => {
    expect(isAccountAllowedByLabel('a@empresa.com', 'a@empresa.com')).toBe(true);
  });

  it('aceita ignorando caixa dos dois lados', () => {
    expect(isAccountAllowedByLabel('A@Empresa.com', 'a@EMPRESA.COM')).toBe(true);
  });

  it('recusa a conta que nao esta no rotulo', () => {
    expect(isAccountAllowedByLabel('a@empresa.com', 'pessoal@gmail.com')).toBe(false);
  });

  it('aceita a segunda conta de um rotulo com dois e-mails', () => {
    expect(isAccountAllowedByLabel('a@empresa.com, b@empresa.com', 'b@empresa.com')).toBe(true);
  });

  // Rotulo sem e-mail nao declara relacao nenhuma: nao ha o que conferir.
  it('aceita qualquer conta quando o rotulo nao traz e-mail', () => {
    expect(isAccountAllowedByLabel('Helio - Comercial', 'pessoal@gmail.com')).toBe(true);
    expect(isAccountAllowedByLabel('', 'pessoal@gmail.com')).toBe(true);
  });

  // Buraco assumido: cliente antigo nao reporta e-mail, e recusar derrubaria
  // instalacao que a mudanca nao pretende atingir.
  it('aceita quando o e-mail da conta e desconhecido', () => {
    expect(isAccountAllowedByLabel('a@empresa.com', null)).toBe(true);
    expect(isAccountAllowedByLabel('a@empresa.com', undefined)).toBe(true);
    expect(isAccountAllowedByLabel('a@empresa.com', 'nao-e-email')).toBe(true);
  });
});
