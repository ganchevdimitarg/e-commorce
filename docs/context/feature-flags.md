# Feature flags

Gate new behaviour behind a feature flag before full rollout:

- Simple on/off: `@ConditionalOnProperty(name = "features.new-pricing", havingValue = "true")`.
- Runtime toggles: inject a `FeatureFlagService` backed by Unleash or a Redis key.
- Remove a flag within one sprint of confirmed full rollout — never leave it permanently.
- Flag names: `features.<service>.<feature>` (e.g. `features.catalog.retry-v2`).
- Never gate with a hardcoded `if (ENV == "prod")` — use the flag service.
