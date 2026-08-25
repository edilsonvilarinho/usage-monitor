const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Normaliza o e-mail reportado pela conta sem transformar texto administrativo
 * arbitrario em identidade. O mesmo criterio atende o fallback de rotulo.
 */
export function normalizeAccountEmail(value: string | null | undefined): string | null {
  if (value == null) {
    return null;
  }

  const normalized = value.trim().toLowerCase();
  if (normalized === '' || !EMAIL_PATTERN.test(normalized)) {
    return null;
  }

  return normalized;
}
