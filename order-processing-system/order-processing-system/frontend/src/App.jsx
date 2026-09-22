import { useEffect, useState, useCallback, useRef } from 'react'

const API_BASE = 'http://localhost:8080/api'
const POLL_MS = 2000

const STATUS_META = {
  PENDING:       { label: 'Pending',       color: '#8a94a6' },
  PROCESSING:    { label: 'Processing',    color: '#e8b923' },
  RETRYING:      { label: 'Retrying',      color: '#e8842b' },
  COMPLETED:     { label: 'Completed',     color: '#3ecf6b' },
  FAILED:        { label: 'Failed',        color: '#e5484d' },
  DEAD_LETTERED: { label: 'Dead-lettered', color: '#8b3a3f' },
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(text || `Request failed: ${res.status}`)
  }
  return res.status === 204 ? null : res.json()
}

function StatusDot({ status }) {
  const meta = STATUS_META[status] || { label: status, color: '#666' }
  return (
    <span className="status">
      <span className="status-dot" style={{ background: meta.color }} />
      {meta.label}
    </span>
  )
}

function useInterval(callback, delay) {
  const savedCallback = useRef(callback)
  useEffect(() => { savedCallback.current = callback }, [callback])
  useEffect(() => {
    const id = setInterval(() => savedCallback.current(), delay)
    return () => clearInterval(id)
  }, [delay])
}

export default function App() {
  const [orders, setOrders] = useState([])
  const [inventory, setInventory] = useState([])
  const [deadLetters, setDeadLetters] = useState([])
  const [connError, setConnError] = useState(null)

  const [customerName, setCustomerName] = useState('')
  const [lines, setLines] = useState([{ productId: '', quantity: 1 }])
  const [submitting, setSubmitting] = useState(false)
  const [submitMsg, setSubmitMsg] = useState(null)

  const refresh = useCallback(async () => {
    try {
      const [o, inv, dlq] = await Promise.all([
        api('/orders'),
        api('/inventory'),
        api('/orders/dead-letter'),
      ])
      setOrders(o)
      setInventory(inv)
      setDeadLetters(dlq)
      setConnError(null)
    } catch (e) {
      setConnError(e.message)
    }
  }, [])

  useEffect(() => { refresh() }, [refresh])
  useInterval(refresh, POLL_MS)

  const addLine = () => setLines([...lines, { productId: '', quantity: 1 }])
  const removeLine = (idx) => setLines(lines.filter((_, i) => i !== idx))
  const updateLine = (idx, field, value) => {
    const next = [...lines]
    next[idx] = { ...next[idx], [field]: value }
    setLines(next)
  }

  const submitOrder = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setSubmitMsg(null)
    try {
      const payload = {
        customerName,
        items: lines
          .filter(l => l.productId !== '')
          .map(l => ({ productId: Number(l.productId), quantity: Number(l.quantity) })),
      }
      if (!payload.customerName || payload.items.length === 0) {
        throw new Error('Enter a customer name and at least one line item.')
      }
      const created = await api('/orders', { method: 'POST', body: JSON.stringify(payload) })
      setSubmitMsg({ type: 'ok', text: `Order #${created.id} accepted — processing now.` })
      setCustomerName('')
      setLines([{ productId: '', quantity: 1 }])
      refresh()
    } catch (err) {
      setSubmitMsg({ type: 'err', text: err.message })
    } finally {
      setSubmitting(false)
    }
  }

  const replay = async (id) => {
    try {
      await api(`/orders/${id}/replay`, { method: 'POST' })
      refresh()
    } catch (err) {
      setSubmitMsg({ type: 'err', text: err.message })
    }
  }

  const activeCount = orders.filter(o => o.status === 'PROCESSING' || o.status === 'RETRYING').length

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark" />
          <div>
            <h1>Order Ops</h1>
            <p>Live order processing &amp; inventory control</p>
          </div>
        </div>
        <div className="topbar-stats">
          <div className="stat">
            <span className="stat-value">{orders.length}</span>
            <span className="stat-label">orders</span>
          </div>
          <div className="stat">
            <span className="stat-value" style={{ color: '#e8b923' }}>{activeCount}</span>
            <span className="stat-label">in flight</span>
          </div>
          <div className="stat">
            <span className="stat-value" style={{ color: '#8b3a3f' }}>{deadLetters.length}</span>
            <span className="stat-label">dead-lettered</span>
          </div>
        </div>
      </header>

      {connError && (
        <div className="banner error">
          Can't reach the API at {API_BASE} — is the Spring Boot backend running? ({connError})
        </div>
      )}

      <main className="grid">
        <section className="panel form-panel">
          <h2>New order</h2>
          <form onSubmit={submitOrder}>
            <label className="field">
              <span>Customer name</span>
              <input
                value={customerName}
                onChange={e => setCustomerName(e.target.value)}
                placeholder="e.g. Priya Sharma"
              />
            </label>

            <div className="lines">
              <span className="lines-label">Line items</span>
              {lines.map((line, idx) => (
                <div className="line" key={idx}>
                  <select
                    value={line.productId}
                    onChange={e => updateLine(idx, 'productId', e.target.value)}
                  >
                    <option value="">Select product…</option>
                    {inventory.map(p => (
                      <option key={p.id} value={p.id}>
                        {p.sku} — {p.name} ({p.quantityAvailable} left)
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    min="1"
                    value={line.quantity}
                    onChange={e => updateLine(idx, 'quantity', e.target.value)}
                  />
                  {lines.length > 1 && (
                    <button type="button" className="icon-btn" onClick={() => removeLine(idx)}>×</button>
                  )}
                </div>
              ))}
              <button type="button" className="link-btn" onClick={addLine}>+ Add line item</button>
            </div>

            <button type="submit" className="primary-btn" disabled={submitting}>
              {submitting ? 'Submitting…' : 'Submit order'}
            </button>

            {submitMsg && (
              <p className={`form-msg ${submitMsg.type}`}>{submitMsg.text}</p>
            )}
          </form>
        </section>

        <section className="panel">
          <h2>Live orders</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Customer</th>
                  <th>Items</th>
                  <th>Attempts</th>
                  <th>Status</th>
                  <th>Last error</th>
                </tr>
              </thead>
              <tbody>
                {orders.map(o => (
                  <tr key={o.id}>
                    <td className="mono">#{o.id}</td>
                    <td>{o.customerName}</td>
                    <td className="mono">
                      {o.items.map(it => `${it.quantity}× P${it.productId}`).join(', ')}
                    </td>
                    <td className="mono">{o.attemptCount}</td>
                    <td><StatusDot status={o.status} /></td>
                    <td className="error-cell">{o.lastError || '—'}</td>
                  </tr>
                ))}
                {orders.length === 0 && (
                  <tr><td colSpan={6} className="empty">No orders yet — submit one on the left.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel">
          <h2>Inventory</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Product</th>
                  <th>In stock</th>
                  <th>Price</th>
                </tr>
              </thead>
              <tbody>
                {inventory.map(p => (
                  <tr key={p.id}>
                    <td className="mono">{p.sku}</td>
                    <td>{p.name}</td>
                    <td className="mono">
                      <span className={p.quantityAvailable === 0 ? 'stock-zero' : p.quantityAvailable < 10 ? 'stock-low' : ''}>
                        {p.quantityAvailable}
                      </span>
                    </td>
                    <td className="mono">${p.price.toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel">
          <h2>Dead-letter queue</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Reason</th>
                  <th>Attempts</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {deadLetters.map(d => (
                  <tr key={d.id}>
                    <td className="mono">#{d.originalOrderId}</td>
                    <td>{d.customerName}</td>
                    <td className="error-cell">{d.reason}</td>
                    <td className="mono">{d.attemptsMade}</td>
                    <td>
                      <button className="link-btn" onClick={() => replay(d.originalOrderId)}>
                        Replay
                      </button>
                    </td>
                  </tr>
                ))}
                {deadLetters.length === 0 && (
                  <tr><td colSpan={5} className="empty">Nothing dead-lettered. Good.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  )
}
