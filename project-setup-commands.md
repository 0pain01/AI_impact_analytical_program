# 1. Infra (Postgres :5442 + RabbitMQ :5672/:15672)
cd infra && cp .env.example .env   # first time only
docker compose up -d

# 2. Backend services (each in its own terminal, from /services)
mvn spring-boot:run -pl api-core                    # :8080 — API/auth/RBAC
mvn spring-boot:run -pl connectors/connector-github  # :8081 — GitHub ingestion
mvn spring-boot:run -pl ingestion-writer             # :8082 — writes staged events
mvn spring-boot:run -pl connectors/connector-jira    # :8083 — Jira ingestion
mvn spring-boot:run -pl metrics-engine               # :8084 — DORA/metrics computation
mvn spring-boot:run -pl identity-service             # :8085 — contributor/team identity

# 3. Frontend
cd frontend && npm install && npm run dev            # :5173 — dashboard UI