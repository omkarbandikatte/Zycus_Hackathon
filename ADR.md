# StockPulse Architecture Decision Record

## 1. Commerce boundary

### Context

Pricing, replenishment, persistence, and trigger handling have different responsibilities. Putting them in controllers would make HTTP and event-driven callers diverge.

### Options

1. Put all logic in controllers and event listeners.
2. Keep controllers thin and expose a commerce advisor service backed by strategy contracts.

### Decision

Use application services for orchestration and strategy interfaces for recommendation logic. Domain entities own valid state transitions; repositories own persistence.

### Tradeoffs

There are more classes up front, but strategies can be tested without HTTP and the async path reuses the same contract.

## 2. Unified strategy result

### Context

Every trigger needs both pricing and reorder recommendations, while each output has different validation and acceptance side effects.

### Options

1. One unified advisor result containing pricing and reorder outputs.
2. Separate pricing and reorder strategy contracts.

### Decision

Use separate typed contracts behind one orchestration service. This keeps output validation and fallbacks independent while allowing one trigger job to create both suggestions.

### Tradeoffs

Separate calls can cost more with an LLM, but a malformed pricing response cannot invalidate a valid reorder response.

## 3. Runtime strategy switching

### Context

The active recommendation implementation must change without code changes or a restart, and sprint 2 must be able to register `CompetitorAwareStrategy`.

### Options

1. Conditional branches in the controller.
2. A named strategy registry selected from configuration.

### Decision

Use a registry of Spring strategy beans selected by a runtime configuration property. Both HTTP and async callers ask the registry for the active implementation.

### Tradeoffs

Configuration errors need a clear fallback or startup validation. The registry adds a small indirection that pays for itself when new strategies arrive.

## 4. LLM resilience

### Context

Timeouts, quotas, malformed JSON, and absurd recommendations must not silently drop asynchronous work.

### Options

1. Return an error and leave no suggestion.
2. Validate the AI result and fall back to deterministic strategies.

### Decision

Treat the LLM as an optional advisor. Validate price bounds, quantities, and confidence, then use rule strategies for invalid or unavailable AI output. API keys come from environment variables.

### Tradeoffs

Rule output is less nuanced, but it preserves an actionable human checkpoint and makes local demos reliable.

## 5. Event-driven recommendation loop

### Context

Stock and order endpoints must respond immediately; recommendation generation must happen after a meaningful stock or velocity signal.

### Options

1. Scheduled polling.
2. Publish domain events and handle them asynchronously.

### Decision

Publish stock/order signal events and process them with an asynchronous advisor. Before persisting, check for an existing pending suggestion keyed by product, trigger reason, and type.

### Tradeoffs

Async work introduces eventual consistency and requires visible loading or refresh behavior in the console. It accurately models the agentic observe -> reason -> propose -> approve loop.

## 6. Extensibility and exclusions

Nullable `costPrice`, `marginFloor`, and `supplierId` are reserved for sprint 2. Competitor data, supplier APIs, automated purchase orders, customer storefront, cart, payment, and automatic price publishing are deliberately deferred so the first sprint protects the event loop and human approval checkpoint.

## Implementation map

The decisions above are implemented with `CommerceAdvisorService` as the shared application boundary. `PricingStrategy` and `ReorderStrategy` are separate typed contracts, with `RuleBasedPricingStrategy` and `RuleBasedReorderStrategy` as the deterministic baseline. `StrategyRegistry` selects the configured strategy for each request and can be changed without restart through `PATCH /settings/strategy`; its synchronized setter validates that a strategy supplies both typed implementations.

`ProductService` changes inventory transactionally and publishes `CommerceSignalEvent`; `AgenticRecommendationListener` consumes it asynchronously after commit in a new transaction, serializes the pending-suggestion check, and delegates back to `CommerceAdvisorService`. `Product`, `PricingSuggestion`, and `ReorderSuggestion` keep their legal state changes in entity methods. `LLMCommerceStrategy` is an optional adapter and falls back independently to the rule strategies when the provider is unavailable or returns invalid JSON.

The React console calls the API through `src/api.js`, polls the product and suggestion views, and exposes both acceptance paths plus a simulated order. The local profile uses H2 and the rule strategy, so the complete demo works without external credentials.

Acceptance methods are transactional at the advisor boundary: changing a suggestion status and applying its product price or inbound stock happen in the same unit of work. This preserves the human checkpoint without allowing a visible accepted suggestion whose product side effect was not persisted.

The optional `ai` strategy is now registered beside the `rules` strategy. It sends separate context-rich prompts for pricing and reorder decisions, extracts provider envelopes for Gemini, Groq, and Ollama, validates the typed JSON, and independently falls back to the matching rule strategy on timeout, provider errors, malformed JSON, or unsafe values. The default remains `rules`, keeping local startup deterministic.

The demand-spike multiplier is injected from `stockpulse.demand-spike-multiplier`, so the trigger threshold is configuration rather than a compiled constant.

## 7. Merchandising console presentation

### Context

The console must make operational risk scannable: KPIs first, then the attention queue, then enough product detail to explain and approve an action. It must also work on a narrow screen without hiding the workflow behind decorative navigation.

### Decision

Use a responsive dashboard with a dark navigation rail, five compact KPI cards, CSS-native stock health donut, inline SVG demand/inventory trends, an horizontally scrollable attention table, and a selected-product detail panel. `#F7F8FC` is the canvas, `#111827` the rail, `#6366F1` the product action accent, `#8B5CF6` the AI accent, and semantic red/amber/green status colors communicate risk.

### Tradeoffs

Charts are deliberately dependency-free and illustrative rather than a history-query feature; the API currently exposes current product state, so the detail timeline labels the signal sequence while the inventory line communicates the requested visual. The table preserves its minimum scan width and scrolls on small screens instead of collapsing important columns.

When a product matches multiple signals, the attention table gives `OUT OF STOCK` and `LOW STOCK` precedence over demand-spike labeling so the most urgent inventory risk remains the primary operator cue.

The dashboard derives its pending-review KPI from the combined pricing and reorder suggestion collections in `App.jsx`; this keeps the sidebar badge, notification badge, summary copy, and KPI card consistent with the API response.

The console's available ceiling features are implemented in the same React application: catalog cards support category filtering and margin display when `costPrice` is available, Inventory renders a stock heatmap alongside threshold comparisons, Product Detail renders an illustrative signal trend, and Suggestions consumes the pricing SSE token stream before displaying the final recommendation. Persisted price and event history remain deferred until history endpoints are added.
