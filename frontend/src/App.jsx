import { useCallback, useEffect, useRef, useState } from 'react'
import { askChat, changeStrategy, decidePricing, decideReorder, getPricingSuggestions, getProducts, getReorderSuggestions, getSignalSettings, getStrategy, simulateOrder, streamPricing } from './api'

const money = value => `$${Number(value).toFixed(2)}`
const navItems = ['Dashboard', 'Products', 'Inventory', 'Suggestions', 'Activity', 'Settings']

export default function App() {
  const [products, setProducts] = useState([])
  const [pricing, setPricing] = useState([])
  const [reorders, setReorders] = useState([])
  const [selected, setSelected] = useState('PRD-003')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [working, setWorking] = useState('')
  const [activeNav, setActiveNav] = useState('Dashboard')
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [strategy, setStrategy] = useState('rules')
  const [demandSpikeMultiplier, setDemandSpikeMultiplier] = useState(3)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [theme, setTheme] = useState(() => {
    const saved = window.localStorage.getItem('stockpulse-theme')
    return saved || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem('stockpulse-theme', theme)
  }, [theme])

  useEffect(() => {
    const profileButtons = document.querySelectorAll('.profile, .sidebar-footer')
    if (!profileButtons.length) return undefined
    const toggleProfile = () => setProfileOpen(value => !value)
    profileButtons.forEach(profileButton => profileButton.addEventListener('click', toggleProfile))
    return () => profileButtons.forEach(profileButton => profileButton.removeEventListener('click', toggleProfile))
  }, [])

  const refresh = useCallback(async () => {
    try {
      setError('')
      const [productData, pricingData, reorderData, signalSettings] = await Promise.all([getProducts(), getPricingSuggestions(), getReorderSuggestions(), getSignalSettings()])
      setProducts(productData); setPricing(pricingData); setReorders(reorderData)
      setDemandSpikeMultiplier(signalSettings.demandSpikeMultiplier)
      if (!productData.some(product => product.id === selected) && productData[0]) setSelected(productData[0].id)
    } catch (cause) { setError(`The API is unavailable. Start Spring Boot on port 8080. (${cause.message})`) } finally { setLoading(false) }
  }, [selected])

  useEffect(() => { refresh(); const timer = setInterval(refresh, 5000); return () => clearInterval(timer) }, [refresh])
  async function run(action, id, accept) { setWorking(id); try { await action(id, accept); await refresh() } catch (cause) { setError(cause.message) } finally { setWorking('') } }
  async function order() { setWorking(selected); try { await simulateOrder(selected); await refresh() } catch (cause) { setError(cause.message) } finally { setWorking('') } }

  const lowStock = products.filter(product => product.stockLevel > 0 && product.stockLevel < product.reorderThreshold)
  const outOfStock = products.filter(product => product.stockLevel === 0)
  const spikes = products.filter(product => isDemandSpike(product, products, demandSpikeMultiplier))
  const pending = pricing.length + reorders.length
  const selectedProduct = products.find(product => product.id === selected) || products[0]
  const atRisk = products.reduce((sum, product) => sum + (product.stockLevel < product.reorderThreshold ? product.currentPrice * Math.max(product.reorderThreshold - product.stockLevel, 0) : 0), 0)

  return <div className="app-shell">
    <aside className={`sidebar ${mobileMenuOpen ? 'mobile-open' : ''}`}><button className="brand" onClick={() => { setActiveNav('Dashboard'); setMobileMenuOpen(false) }}><span className="brand-mark">S</span><strong>StockPulse</strong></button><p className="side-label">Workspace</p><nav>{navItems.map((item, index) => <button key={item} className={`nav-item ${activeNav === item ? 'active' : ''}`} onClick={() => { setActiveNav(item); setMobileMenuOpen(false) }}>{item}<span>{item === 'Suggestions' ? pending : ''}</span></button>)}</nav><div className="sidebar-footer"><span className="avatar">M</span><span><strong>Merchandising</strong><small>ShopStream</small></span><span className="dots">...</span></div></aside>
    {mobileMenuOpen && <button className="mobile-menu-backdrop" aria-label="Close navigation" onClick={() => setMobileMenuOpen(false)} />}
    <main className="dashboard"><header className="topbar"><div className="mobile-header"><button className="mobile-menu-button" aria-label="Open navigation" aria-expanded={mobileMenuOpen} onClick={() => setMobileMenuOpen(true)}><span /><span /><span /></button><div><p className="eyebrow">MONDAY, AUGUST 24, 2026</p><h1>{pageTitle(activeNav)}</h1><p className="subhead">{pageSubtitle(activeNav, pending)}</p></div><ThemeToggle theme={theme} setTheme={setTheme} /></div><div className="top-actions"><ThemeToggle theme={theme} setTheme={setTheme} /><div className="notification-wrap"><button className={`icon-button ${notificationsOpen ? 'open' : ''}`} aria-label="Notifications" aria-expanded={notificationsOpen} onClick={() => setNotificationsOpen(value => !value)}>&#128276;<span>{pending}</span></button>{notificationsOpen && <NotificationPopover pricing={pricing} reorders={reorders} pending={pending} onView={() => { setActiveNav('Suggestions'); setNotificationsOpen(false) }} />}</div><button className="profile">M <span>Merchandising</span> &#8964;</button></div></header>{error && <div className="error" role="alert">{error}</div>}
      {activeNav === 'Dashboard' && <Dashboard products={products} lowStock={lowStock} outOfStock={outOfStock} spikes={spikes} pending={pending} atRisk={atRisk} selectedProduct={selectedProduct} pricing={pricing} reorders={reorders} setSelected={setSelected} order={order} working={working} refresh={refresh} run={run} loading={loading} />}
      {activeNav === 'Products' && <ProductsPage products={products} selected={selected} setSelected={setSelected} order={order} working={working} />}
      {activeNav === 'Inventory' && <InventoryPage products={products} lowStock={lowStock} outOfStock={outOfStock} spikes={spikes} />}
      {activeNav === 'Suggestions' && <SuggestionsPage pricing={pricing} reorders={reorders} working={working} run={run} loading={loading} />}
      {activeNav === 'Activity' && <ActivityPage products={products} pricing={pricing} reorders={reorders} />}
      {activeNav === 'Settings' && <SettingsPage strategy={strategy} demandSpikeMultiplier={demandSpikeMultiplier} loadStrategy={async () => { try { const result = await getStrategy(); setStrategy(result.strategy) } catch (cause) { setError(cause.message) } }} setStrategy={async value => { try { const result = await changeStrategy(value); setStrategy(result.strategy) } catch (cause) { setError(cause.message) } }} />}
    </main>
    {profileOpen && <div className="profile-menu" role="menu"><strong>Merchandising</strong><small>ShopStream workspace</small><button role="menuitem" onClick={() => { setActiveNav('Settings'); setProfileOpen(false) }}>Open settings</button></div>}
    <ChatWidget />
  </div>
}

function isDemandSpike(product, catalog, multiplier) {
  const peers = catalog.filter(candidate => candidate.category === product.category && candidate.id !== product.id)
  const average = peers.reduce((sum, candidate) => sum + candidate.demandVelocity, 0) / peers.length
  return product.demandVelocity > 0 && product.demandVelocity > multiplier * average
}

function ThemeToggle({ theme, setTheme }) {
  const nextTheme = theme === 'dark' ? 'light' : 'dark'
  return <button className="theme-toggle" type="button" aria-label={`Switch to ${nextTheme} theme`} title={`Switch to ${nextTheme} theme`} onClick={() => setTheme(nextTheme)}><span className="theme-toggle-icon" aria-hidden="true">{theme === 'dark' ? '☀' : '☾'}</span><span className="theme-toggle-label">{theme === 'dark' ? 'Light' : 'Dark'}</span></button>
}

function pageTitle(page) { return page === 'Dashboard' ? <><span className="greeting-prefix">Good afternoon,</span> <em>Merchandising</em></> : page }
function pageSubtitle(page, pending) { return page === 'Dashboard' ? <>Your inventory advisor found <strong>{pending}</strong> actions needing you.</> : 'ShopStream commerce operations workspace' }
function Kpi({ label, value, detail, tone = 'neutral' }) { return <article className={`kpi ${tone}`}><div className="kpi-icon">{tone === 'critical' ? '!' : tone === 'ai' ? '+' : tone === 'warning' ? '~' : '#'}</div><small>{label}</small><strong>{value}</strong><span>{detail}</span></article> }
function Legend({ color, label, value }) { return <div className="legend-row"><span className={`dot ${color}`} />{label}<strong>{value}</strong></div> }
function NotificationPopover({ pricing, reorders, pending, onView }) {
  const items = [...pricing.map(item => ({ ...item, type: 'Pricing' })), ...reorders.map(item => ({ ...item, type: 'Reorder' }))].slice(0, 5)
  return <div className="notification-popover" role="dialog" aria-label="Notifications"><div className="notification-head"><strong>Notifications</strong><span>{pending} pending</span></div>{items.length ? <div className="notification-list">{items.map(item => <button className="notification-item" key={item.id} onClick={onView}><span className={`notification-icon ${item.type.toLowerCase()}`}>{item.type === 'Pricing' ? '$' : '+'}</span><span><strong>{item.product.name}</strong><small>{item.type} suggestion · {item.triggerReason}</small></span><span className="notification-arrow">&#8594;</span></button>)}</div> : <p className="notification-empty">You are all caught up. New inventory signals will appear here.</p>}{items.length > 0 && <button className="notification-footer" onClick={onView}>View all suggestions &#8594;</button>}</div>
}
function Dashboard({ products, lowStock, outOfStock, spikes, pending, atRisk, selectedProduct, pricing, reorders, setSelected, order, working, refresh, run, loading }) {
  const selectedPrice = pricing.find(item => item.product.id === selectedProduct?.id)
  const selectedReorder = reorders.find(item => item.product.id === selectedProduct?.id)
  return <><section className="kpis"><Kpi label="TOTAL SKUS" value={products.length} detail="Across your catalog" /><Kpi label="LOW STOCK" value={lowStock.length} detail={`${outOfStock.length} out of stock`} tone="critical" /><Kpi label="AI ALERTS" value={pending} detail="Awaiting review" tone="ai" /><Kpi label="DEMAND SPIKES" value={spikes.length} detail="Above baseline" tone="warning" /><Kpi label="AT RISK" value={`${money(atRisk / 1000)}k`} detail="Inventory value" /></section><section className="overview-grid"><article className="panel health-panel"><div className="panel-heading"><div><p className="eyebrow">CATALOG OVERVIEW</p><h2>Stock health</h2></div></div><div className="health-content"><div className="donut" style={{ '--low': `${(lowStock.length / Math.max(products.length, 1)) * 100}%`, '--out': `${(outOfStock.length / Math.max(products.length, 1)) * 100}%` }}><div><strong>{products.length ? Math.round(((products.length - lowStock.length - outOfStock.length) / products.length) * 100) : 0}%</strong><span>healthy</span></div></div><div className="legend"><Legend color="green" label="Healthy" value={products.length - lowStock.length - outOfStock.length} /><Legend color="amber" label="Low stock" value={lowStock.length} /><Legend color="red" label="Out of stock" value={outOfStock.length} /></div></div></article><article className="panel velocity-panel"><div className="panel-heading"><div><p className="eyebrow">LAST 24 HOURS</p><h2>Demand velocity</h2></div><span className="trend-up">+18.4% &#8593;</span></div><div className="chart"><svg viewBox="0 0 520 150" role="img" aria-label="Demand velocity trend"><path className="chart-fill" d="M0 124 C45 112 65 125 105 99 S168 62 204 88 S263 112 303 62 S362 79 400 47 S460 66 520 20" /><path className="chart-line" d="M0 124 C45 112 65 125 105 99 S168 62 204 88 S263 112 303 62 S362 79 400 47 S460 66 520 20" /></svg><div className="chart-axis"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>NOW</span></div></div></article></section><section className="panel page-panel"><div className="panel-heading"><div><p className="eyebrow critical-text">PRIORITY QUEUE</p><h2>AI attention required</h2></div><div className="queue-actions"><select value={selectedProduct?.id || ''} onChange={event => setSelected(event.target.value)} aria-label="Product for simulated sale">{products.map(product => <option key={product.id} value={product.id}>{product.sku}</option>)}</select><button className="secondary" onClick={order} disabled={!selectedProduct || Boolean(working)}>Simulate sale</button><button className="text-button" onClick={refresh}>Refresh &#8635;</button></div></div>{loading ? <p className="empty">Loading inventory...</p> : <ProductTable products={products.filter(product => product.stockLevel < product.reorderThreshold || pricing.some(item => item.product.id === product.id) || reorders.some(item => item.product.id === product.id))} pricing={pricing} reorders={reorders} />}</section>{selectedProduct && <section className="detail-layout"><ProductDetail product={selectedProduct} /><Recommendation price={selectedPrice} reorder={selectedReorder} working={working} run={run} /></section>}</>
}
function ProductsPage({ products, selected, setSelected, order, working }) { const [category, setCategory] = useState('ALL'); const visible = products.filter(product => category === 'ALL' || product.category === category); return <section className="panel page-panel"><PageHeading eyebrow="CATALOG" title="All products" detail={`${visible.length} of ${products.length} SKUs connected to the advisor`} /><div className="catalog-toolbar"><span>Category</span><select value={category} onChange={event => setCategory(event.target.value)} aria-label="Filter products by category"><option value="ALL">All categories</option><option value="ELECTRONICS">Electronics</option><option value="APPAREL">Apparel</option><option value="HOME">Home</option></select></div><div className="product-grid">{visible.map(product => <button className={`catalog-card ${selected === product.id ? 'selected' : ''}`} key={product.id} onClick={() => setSelected(product.id)}><span className="catalog-card-top"><span className="category-label">{product.category}</span><span className={`status-pill ${product.stockLevel === 0 ? 'critical' : product.stockLevel < product.reorderThreshold ? 'warning' : 'healthy'}`}>{product.status}</span></span><strong>{product.name}</strong><small>{product.sku}</small><span className="catalog-metrics"><b>{money(product.currentPrice)}</b><b>{product.stockLevel} <i>in stock</i></b><b>{product.demandVelocity}<i>/day</i></b></span><small className="margin-note">Margin: {product.costPrice ? money(product.currentPrice - product.costPrice) : 'cost pending'}</small></button>)}</div><div className="page-action"><button className="secondary" onClick={order} disabled={!selected || Boolean(working)}>Simulate sale for selected SKU</button></div></section> }
function InventoryPage({ products, lowStock, outOfStock, spikes }) { return <><section className="kpis"><Kpi label="HEALTHY" value={products.length - lowStock.length - outOfStock.length} detail="Ready to sell" /><Kpi label="LOW STOCK" value={lowStock.length} detail="Below threshold" tone="critical" /><Kpi label="OUT OF STOCK" value={outOfStock.length} detail="Needs replenishment" tone="critical" /><Kpi label="SPIKES" value={spikes.length} detail="Velocity signal" tone="warning" /></section><section className="panel page-panel"><PageHeading eyebrow="INVENTORY CONTROL" title="Stock risk matrix" detail="Every product compared with its reorder threshold" /><div className="heatmap">{products.map(product => <div className={`heat-cell ${product.stockLevel === 0 ? 'empty-stock' : product.stockLevel < product.reorderThreshold ? 'low-stock' : 'healthy-stock'}`} key={product.id} title={`${product.name}: ${product.stockLevel} units`}><strong>{product.stockLevel}</strong><small>{product.sku}</small></div>)}</div><div className="heatmap-legend"><span><i className="healthy-stock" /> Healthy</span><span><i className="low-stock" /> Low stock</span><span><i className="empty-stock" /> Out of stock</span></div><div className="risk-list">{products.map(product => <div className="risk-row" key={product.id}><div><strong>{product.name}</strong><small>{product.sku} · {product.category}</small></div><div className="risk-bar"><span style={{ width: `${Math.min(100, product.stockLevel / Math.max(product.reorderThreshold * 2, 1) * 100)}%` }} /></div><strong>{product.stockLevel} / {product.reorderThreshold}</strong><span className={`status-pill ${product.stockLevel === 0 ? 'critical' : product.stockLevel < product.reorderThreshold ? 'warning' : 'healthy'}`}>{product.stockLevel === 0 ? 'OUT OF STOCK' : product.stockLevel < product.reorderThreshold ? 'LOW STOCK' : 'HEALTHY'}</span></div>)}</div></section></> }
function SuggestionsPage({ pricing, reorders, working, run, loading }) { const [streaming, setStreaming] = useState(''); const [streamText, setStreamText] = useState(''); async function startStream(item) { setStreaming(item.id); setStreamText(''); try { await streamPricing(item.product.id, token => setStreamText(previous => previous + token), () => {}) } catch (cause) { setStreamText(cause.message) } finally { setStreaming('') } } return <section className="panel page-panel"><PageHeading eyebrow="HUMAN CHECKPOINT" title="Suggestions to review" detail="Recommendations never publish without merchandising approval" />{streamText && <div className="stream-output"><small>LIVE AI REASONING</small><p>{streamText}</p></div>}{loading ? <p className="empty">Loading suggestions...</p> : <div className="suggestion-list">{pricing.map(item => <SuggestionCard key={item.id} item={item} type="PRICE" working={working} run={run} stream={() => startStream(item)} streaming={streaming === item.id} />)}{reorders.map(item => <SuggestionCard key={item.id} item={item} type="REORDER" working={working} run={run} />)}{!pricing.length && !reorders.length && <p className="empty">No pending suggestions. The advisor is watching your catalog.</p>}</div>}</section> }
function ActivityPage({ products, pricing, reorders }) { return <section className="panel page-panel"><PageHeading eyebrow="EVENT LOG" title="Activity" detail="Recent inventory signals and advisor decisions" /><div className="activity-list">{products.slice(0, 8).map(product => <div className="activity-row" key={product.id}><span className="timeline-dot critical-dot" /><div><strong>{product.stockLevel < product.reorderThreshold ? 'Inventory low' : 'Catalog monitored'}</strong><p>{product.name} is at {product.stockLevel} units with velocity {product.demandVelocity}/day.</p></div><small>Today</small></div>)}{[...pricing, ...reorders].map(item => <div className="activity-row" key={item.id}><span className="timeline-dot ai-dot" /><div><strong>{item.triggerReason} recommendation</strong><p>{item.product.name} generated a {item.status.toLowerCase()} advisor suggestion.</p></div><small>Advisor</small></div>)}</div></section> }
function SettingsPage({ strategy, demandSpikeMultiplier, loadStrategy, setStrategy }) { useEffect(() => { loadStrategy() }, []); return <section className="panel page-panel settings-page"><PageHeading eyebrow="WORKSPACE CONFIGURATION" title="Settings" detail="Runtime controls for the commerce advisor" /><div className="settings-list"><div className="setting-row"><div><strong>Active recommendation strategy</strong><p>Switch recommendation logic immediately for new requests and event jobs.</p></div><select className="strategy-select" value={strategy} onChange={event => setStrategy(event.target.value)} aria-label="Active recommendation strategy"><option value="rules">Rules</option><option value="ai">AI</option></select></div><Setting label="Demand spike multiplier" value={`${demandSpikeMultiplier}x category average`} detail="A demand signal fires when velocity exceeds this configured ratio." /><Setting label="Approval policy" value="Human checkpoint required" detail="Pricing and reorder suggestions must be accepted before product state changes." /><Setting label="LLM provider" value="Configured by environment" detail="Provider credentials are read from environment variables and never stored in the UI." /></div></section> }
function Setting({ label, value, detail }) { return <div className="setting-row"><div><strong>{label}</strong><p>{detail}</p></div><span className="setting-value">{value}</span></div> }
function PageHeading({ eyebrow, title, detail }) { return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2><p>{detail}</p></div></div> }
function SuggestionCard({ item, type, working, run, stream, streaming }) { return <article className="suggestion-card"><div className="suggestion-card-head"><div><small>{type} SUGGESTION · {item.triggerReason}</small><h3>{item.product.name}</h3><p>{item.product.sku}</p></div><span className="status-pill ai-pill">{Math.round(item.confidence * 100)}% CONFIDENCE</span></div><div className="suggestion-value">{type === 'PRICE' ? <><span>{money(item.currentPrice)}</span><b>&#8594;</b><strong>{money(item.recommendedPrice)}</strong></> : <><span>{item.currentStock} units</span><b>&#8594;</b><strong>+{item.recommendedQuantity} units</strong></>}</div><p className="suggestion-reason">{item.reasoning}</p><div className="approval-actions">{stream && <button className="secondary" onClick={stream} disabled={streaming}>{streaming ? 'Streaming...' : 'Stream reasoning'}</button>}<button className="reject" onClick={() => run(type === 'PRICE' ? decidePricing : decideReorder, item.id, false)} disabled={working === item.id}>Reject</button><button onClick={() => run(type === 'PRICE' ? decidePricing : decideReorder, item.id, true)} disabled={working === item.id}>Accept</button></div></article> }
function ProductTable({ products, pricing, reorders }) { return <div className="attention-table"><div className="table-head"><span>PRODUCT</span><span>STOCK / THRESHOLD</span><span>VELOCITY</span><span>SIGNAL</span><span>AI ACTION</span></div>{products.slice(0, 8).map(product => <AttentionRow key={product.id} product={product} pricing={pricing} reorders={reorders} />)}</div> }
function AttentionRow({ product, pricing, reorders }) { const price = pricing.find(item => item.product.id === product.id); const reorder = reorders.find(item => item.product.id === product.id); const signal = product.stockLevel === 0 ? 'OUT OF STOCK' : product.stockLevel < product.reorderThreshold ? 'LOW STOCK' : product.demandVelocity > 10 ? 'DEMAND SPIKE' : 'HEALTHY'; return <div className="attention-row"><span className="product-cell"><span className="signal-dot" /><span><strong>{product.name}</strong><small>{product.sku}</small></span></span><span><strong>{product.stockLevel}</strong> / {product.reorderThreshold}</span><span><strong>{product.demandVelocity}</strong>/day</span><span><span className={`status-pill ${signal === 'DEMAND SPIKE' ? 'warning' : signal === 'HEALTHY' ? 'healthy' : 'critical'}`}>{signal}</span></span><span className="ai-action">{price ? `${price.direction} ${money(price.recommendedPrice)}` : reorder ? `Reorder ${reorder.recommendedQuantity}` : 'Watching'}</span></div> }
function ProductDetail({ product }) { return <article className="panel detail-panel"><div className="detail-head"><div><p className="eyebrow">PRODUCT DETAIL</p><h2>{product.name}</h2><p className="sku-large">{product.sku}</p></div><span className="status-pill critical">{product.status}</span></div><div className="detail-metrics"><div><small>CURRENT PRICE</small><strong>{money(product.currentPrice)}</strong></div><div><small>STOCK</small><strong>{product.stockLevel}</strong></div><div><small>VELOCITY</small><strong>{product.demandVelocity}<i>/day</i></strong></div></div><div className="detail-section history"><div className="section-title"><h3>Price history</h3><span>Last 7 signals</span></div><svg viewBox="0 0 650 120" role="img" aria-label="Price history"><path className="grid-line" d="M0 20H650 M0 60H650 M0 100H650" /><path className="inventory-line" d="M0 88 L100 82 L200 86 L300 62 L400 66 L510 45 L650 45" /><circle cx="650" cy="45" r="5" /></svg></div><div className="detail-section timeline"><div className="section-title"><h3>Event timeline</h3><span>Live</span></div><div className="timeline-list"><div><time>NOW</time><span className="timeline-dot" /><p>Inventory snapshot<small>Threshold = {product.reorderThreshold}</small></p></div><div><time>11:09</time><span className="timeline-dot ai-dot" /><p><strong>AI ADVISOR</strong><small>Watching for a meaningful signal</small></p></div><div><time>WAIT</time><span className="timeline-dot human-dot" /><p><strong>MERCHANDISING</strong><small>Approval remains required</small></p></div></div></div></article> }
function Recommendation({ price, reorder, working, run }) { return <aside className="panel recommendation-panel"><p className="eyebrow ai-text">AI ADVISOR</p><h2>Recommendation</h2>{price ? <SuggestionCard item={price} type="PRICE" working={working} run={run} /> : reorder ? <SuggestionCard item={reorder} type="REORDER" working={working} run={run} /> : <div className="watching">No pending recommendation<small>Signals are evaluated after stock changes and orders.</small></div>}</aside> }

function ChatWidget() {
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [messages, setMessages] = useState([{ role: 'assistant', text: 'Hi! Ask me about your products, pricing, reorder suggestions, or the active strategy.' }])
  const logRef = useRef(null)

  useEffect(() => { if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight }, [messages, open])

  async function send(event) {
    event.preventDefault()
    const text = input.trim()
    if (!text || sending) return
    setMessages(previous => [...previous, { role: 'user', text }])
    setInput('')
    setSending(true)
    try {
      const result = await askChat(text)
      setMessages(previous => [...previous, { role: 'assistant', text: result.reply }])
    } catch (cause) {
      setMessages(previous => [...previous, { role: 'assistant', text: `Sorry, the assistant is unavailable. (${cause.message})` }])
    } finally {
      setSending(false)
    }
  }

  return <div className="chat-widget">
    {open && <div className="chat-panel">
      <div className="chat-panel-head"><strong>StockPulse assistant</strong><button className="chat-close" aria-label="Close chat" onClick={() => setOpen(false)}>&times;</button></div>
      <div className="chat-log" ref={logRef}>{messages.map((message, index) => <div key={index} className={`chat-bubble ${message.role}`}>{message.text}</div>)}{sending && <div className="chat-bubble assistant chat-typing">Thinking...</div>}</div>
      <form className="chat-input-row" onSubmit={send}>
        <input value={input} onChange={event => setInput(event.target.value)} placeholder="Ask about products, pricing, reorders..." aria-label="Chat message" />
        <button type="submit" disabled={sending || !input.trim()}>Send</button>
      </form>
    </div>}
    <button className="chat-fab" aria-label={open ? 'Close assistant' : 'Open assistant'} onClick={() => setOpen(previous => !previous)}>{open ? '\u00d7' : '\uD83D\uDCAC'}</button>
  </div>
}
