#!/usr/bin/env bash
# Apply the shared Flyway history to Oracle (non-prod/prod). Local H2 is migrated
# by Spring Boot on startup. H2-only success is not schema acceptance (TASK-004).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

: "${ATLAS_DATASOURCE_URL:?Set ATLAS_DATASOURCE_URL to an Oracle 19c jdbc:oracle: URL}"
: "${ATLAS_DATASOURCE_USERNAME:?Set ATLAS_DATASOURCE_USERNAME}"
: "${ATLAS_DATASOURCE_PASSWORD:?Set ATLAS_DATASOURCE_PASSWORD}"

case "${ATLAS_DATASOURCE_URL}" in
  jdbc:oracle:*) ;;
  *)
    echo "ATLAS_DATASOURCE_URL must be jdbc:oracle:... (H2 is not schema acceptance)" >&2
    exit 1
    ;;
esac

exec ./mvnw -pl backend org.flywaydb:flyway-maven-plugin:migrate \
  -Dflyway.url="${ATLAS_DATASOURCE_URL}" \
  -Dflyway.user="${ATLAS_DATASOURCE_USERNAME}" \
  -Dflyway.password="${ATLAS_DATASOURCE_PASSWORD}" \
  -Dflyway.locations=filesystem:src/main/resources/db/migration
