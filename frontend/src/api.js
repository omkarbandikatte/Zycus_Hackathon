const API = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, { headers: { 'Content-Type': 'application/json' }, ...options })
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json()
}

export const getProducts = () => request('/products')
export const getPricingSuggestions = () => request('/pricing-suggestions')
export const getReorderSuggestions = () => request('/reorder-suggestions')
export const simulateOrder = id => request(`/products/${id}/orders`, { method: 'POST' })
export const decidePricing = (id, accept) => request(`/pricing-suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ accept }) })
export const decideReorder = (id, accept) => request(`/reorder-suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ accept }) })
export const getStrategy = () => request('/settings/strategy')
export const getSignalSettings = () => request('/settings/strategy/signals')
export const changeStrategy = strategy => request('/settings/strategy', { method: 'PATCH', body: JSON.stringify({ strategy }) })
export const askChat = message => request('/chat', { method: 'POST', body: JSON.stringify({ message }) })
export async function streamPricing(id, onToken, onSuggestion) {
  const response = await fetch(`${API}/products/${id}/suggest-pricing/stream`, { method: 'POST', headers: { Accept: 'text/event-stream' } })
  if (!response.ok || !response.body) throw new Error(`${response.status} ${response.statusText}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''
    for (const event of events) {
      const data = event.split('\n').find(line => line.startsWith('data:'))?.slice(5).trim()
      if (!data) continue
      if (event.includes('event: token')) onToken(data)
      if (event.includes('event: suggestion')) onSuggestion(JSON.parse(data))
    }
    if (done) break
  }
}