import React from 'react';

export function AppDataTable({ columns = [], rows = [], style }) {
  return (
    <div style={{ overflowX: 'auto', minWidth: 0, ...style }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>
        <thead>
          <tr>
            {columns.map((c) => (
              <th
                key={c.key}
                style={{
                  textAlign: c.numeric ? 'right' : 'left',
                  fontWeight: 500,
                  fontSize: 'var(--t10)',
                  letterSpacing: 'var(--ls-eyebrow)',
                  textTransform: 'uppercase',
                  color: 'var(--muted)',
                  padding: 'var(--s2) var(--s3)',
                  borderBottom: '1px solid var(--border)',
                  whiteSpace: 'nowrap'
                }}
              >
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <Row key={r.id != null ? r.id : i} row={r} columns={columns} last={i === rows.length - 1} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({ row, columns, last }) {
  const [hover, setHover] = React.useState(false);
  return (
    <tr onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}>
      {columns.map((c) => (
        <td
          key={c.key}
          style={{
            padding: '7px var(--s3)',
            borderBottom: last ? 'none' : '1px solid var(--border)',
            fontVariantNumeric: 'tabular-nums',
            whiteSpace: 'nowrap',
            textAlign: c.numeric ? 'right' : 'left',
            background: hover ? 'var(--raised)' : 'transparent',
            transition: 'background var(--dur-hover) var(--ease)'
          }}
        >
          {row[c.key]}
        </td>
      ))}
    </tr>
  );
}
