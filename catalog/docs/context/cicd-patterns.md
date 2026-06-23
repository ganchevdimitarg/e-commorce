# CI/CD patterns

## Pipeline-as-code
`Jenkinsfile` at the repo root, declarative syntax, `pipeline { agent any }`.
One pipeline covers all modules; per-module work is scoped via `-pl catalog -am`.

## Tools
JDK 25 (`jdk-25` Jenkins tool name). Maven via the repo `./mvnw` wrapper — never a
Jenkins-managed `mvn` installation.

## Test selection
Tests are tagged `@Tag("unit")` / `@Tag("integration")`; select with `-Dgroups=unit` or
`-Dgroups=integration`. There is **no Failsafe plugin** — both groups run under Surefire.
Reports live in `**/surefire-reports/*.xml`.

## Coverage gate is in Maven
`jacoco:check` is bound to the `verify` phase (BUNDLE line >= 80%, package
`com.concordeu.catalog.service.*` line = 100%). `./mvnw verify` fails on regression.
The Jenkins `jacoco(...)` step only publishes the trend — it does not gate.
Run the gate on a full `verify` (whole suite) so combined coverage is measured.

## Stages
Checkout --> build --> unit test --> verify (full suite incl. Testcontainers; `jacoco:check`
gate) --> publish coverage --> Docker build --> push --> deploy.

## Testcontainers in CI
Docker socket mount (`-v /var/run/docker.sock`) on the Jenkins agent.
Set `TESTCONTAINERS_RYUK_DISABLED=true` to avoid permission issues with the Ryuk
container in CI environments.

## Docker tagging
`<service>:<git-sha>` — never `:latest` in deployments. The `GIT_SHA` is captured at
checkout via `git rev-parse --short HEAD` and stored in `env.GIT_SHA`.

## Branch strategy
PR branches: build + test only (Docker push and deploy stages are skipped).
`main`: full pipeline including deploy to dev.

## Credentials
Via Jenkins credentials store:
- `docker-registry-url` — Docker registry URL
- `docker-registry-creds` — username/password for Docker registry login
- Kubernetes config (when deploy stage is wired up)

## Post actions
- **Always:** archive JUnit XML (`**/surefire-reports/*.xml`) and JaCoCo exec files
- **Failure:** Slack/email notification (placeholder — configure webhook)
- **Cleanup:** remove local Docker image to reclaim disk space
