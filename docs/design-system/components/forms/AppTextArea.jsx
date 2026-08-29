import React from 'react';

export function AppTextArea({ value, onChange, placeholder, label, hint, disabled = false, style, ...rest }) {
  const [focus, setFocus] = React.useState(false);
  const field = (
    <textarea
      value={value}
      placeholder={placeholder}
      disabled={disabled}
      onChange={onChange ? (e) => onChange(e.target.value) : undefined}
      onFocus={() => setFocus(true)}
      onBlur={() => setFocus(false)}
      style={{
        fontFamily: 'var(--sans)',
        fontSize: 'var(--t12)',
        background: 'var(--bg)',
        color: 'var(--fg)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        padding: '7px 9px',
        width: '100%',
        minHeight: 96,
        resize: 'vertical',
        outline: focus ? '2px solid var(--info)' : 'none',
        outlineOffset: '-1px',
        opacity: disabled ? .42 : 1,
        ...style
      }}
      {...rest}
    />
  );
  if (!label && !hint) return field;
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s1)', minWidth: 0 }}>
      {label ? <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: 'var(--ls-eyebrow)', textTransform: 'uppercase', color: 'var(--muted)' }}>{label}</span> : null}
      {field}
      {hint ? <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)' }}>{hint}</span> : null}
    </label>
  );
}
