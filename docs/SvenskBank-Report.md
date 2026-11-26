# SvenskBank – A Cloud-Native Banking Application on Kubernetes

**Course assignment – Cloud Computing**
**Author:** Ragavi Gunasekaran
**Date:** 2026-11-26

| Project links | |
|---|---|
| Source code and Kubernetes manifests | https://github.com/ragavi-gunasekaran/svenskbank-cloud |
| Docker images | https://hub.docker.com/u/gragavig |

---

## Table of contents

1. [Description of the software](#1-description-of-the-software)
2. [Software architecture design](#2-software-architecture-design)
3. [Benefits of the architecture](#3-benefits-of-the-architecture)
4. [Challenges and mitigations](#4-challenges-and-mitigations)
5. [Security discussion](#5-security-discussion)
6. [Verification and test evidence](#6-verification-and-test-evidence)
7. [How to deploy](#7-how-to-deploy)
8. [Conclusion](#8-conclusion)

---

## 1. Description of the software

**SvenskBank** is a small, web-based banking application. It lets a user manage bank
accounts and move money between them through a browser. The application is built as a
set of independent microservices that are packaged as Docker images, published on Docker
Hub and deployed on Kubernetes.

### 1.1 Functionality

| Feature | Description |
|---|---|
| Open an account | Create a new account for an account holder, with an optional first deposit. Each account gets a unique account number (e.g. `SB4698997105`). |
| View accounts | List all accounts with holder name and current balance. |
| Deposit | Add money to an account. |
| Withdraw | Take money out of an account. The balance can never become negative. |
| Transfer | Move money from one account to another. |
| Transaction history | Show all deposits, withdrawals and transfers for one account, newest first. |

### 1.2 User interface

The web interface uses a blue-and-yellow theme and has four pages:

| Page | Purpose |
|---|---|
| **Home** | Welcome page with short introduction and quick links. |
| **About** | Explains the purpose of SvenskBank and how it is built. |
| **Accounts** | Form to open an account, list of all accounts, and per-account history. |
| **Payments** | Tabs for *Transfer*, *Deposit* and *Withdraw*, plus a live balance table. |

### 1.3 Technology stack

| Layer | Technology |
|---|---|
| Frontend | Angular 22 (standalone components, signals), plain CSS |
| Web server / gateway | nginx (unprivileged image) |
| Backend services | Java 21, Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) |
| Database | PostgreSQL 16 |
| Containers | Docker, multi-stage builds, multi-architecture images (amd64 + arm64) |
| Image registry | Docker Hub (`gragavig/svenskbank-*`) |
| Orchestration | Kubernetes (tested on minikube, Kubernetes v1.37) with NGINX Ingress Controller and metrics-server |

### 1.4 Scaling assumptions – what to pretend

In its current form SvenskBank is a small demo application. A handful of users would not
need several replicas, autoscaling or separate services – a single server would be enough.
To make the architecture meaningful, the following situation should be **pretended**:

- SvenskBank is a national bank with about **2 million customers** using the web bank.
- **Transactions are far more frequent than account management.** Customers open an
  account once, but make payments and transfers every day. Transaction-service therefore
  needs much more capacity than account-service.
- **Load varies strongly over time.** On salary day (the 25th of every month) and before
  holidays, the number of payments can be 10–20 times higher than on a normal night. The
  system must scale out for the peak and scale in afterwards to save costs.
- **Availability matters.** Customers expect the bank to work 24/7, so updates must be
  possible without downtime, and a single crashed process must not stop the bank.
- **Different teams** develop the account and payment functions and want to release
  independently of each other.

Under these assumptions, independent scaling (HPA per service), multiple replicas, rolling
updates and the split into microservices are justified. The demo can be used to *show*
this behaviour, e.g. by scaling transaction-service to more replicas than account-service:

```bash
kubectl -n svenskbank scale deployment transaction-service --replicas=5
```

---

## 2. Software architecture design

### 2.1 Architecture overview

```
                         ┌──────────────────────────┐
                         │   User's web browser     │
                         └────────────┬─────────────┘
                                      │ HTTP
 ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
  Kubernetes cluster (namespace "svenskbank")
                                      ▼
                         ┌──────────────────────────┐
                         │  Ingress (NGINX)         │  single entry point
                         └────────────┬─────────────┘
                                      ▼
                         ┌──────────────────────────┐
                         │  frontend  (2–5 pods)    │  Angular app + nginx
                         │  • serves web pages      │  API gateway:
                         │  • /api/accounts  ───────┼──────────┐
                         │  • /api/transactions ────┼───┐      │
                         │  • blocks */balance (403)│   │      │
                         └──────────────────────────┘   │      │
                                                        ▼      ▼
              ┌──────────────────────────────┐   ┌──────────────────────────────┐
              │ transaction-service (2–5)    │   │ account-service (2–5 pods)   │
              │ Spring Boot, port 8082       │   │ Spring Boot, port 8081       │
              │ deposit / withdraw /         │──▶│ accounts & balances          │
              │ transfer / history           │REST│ PUT /api/accounts/{id}/balance│
              └──────────────┬───────────────┘   └──────────────┬───────────────┘
                             │ JDBC                             │ JDBC
                             ▼                                  ▼
              ┌───────────────────────────────────────────────────────────────┐
              │ postgres  (StatefulSet, 1 pod)                                │
              │   database "transactiondb"        database "accountdb"        │
              │   PersistentVolumeClaim 1 Gi  →  data survives restarts        │
              └───────────────────────────────────────────────────────────────┘
```

### 2.2 Components and responsibilities

| Component | Responsibility |
|---|---|
| **Web UI** | Presents pages to the user, validates form input, calls the REST APIs and shows results or error messages. |
| **API gateway / reverse proxy** | Single entry point for API calls from the browser. Routes `/api/accounts/**` to account management and `/api/transactions/**` to transaction processing. Blocks internal endpoints from the outside. |
| **Account management** | Owns accounts and balances. Creates accounts, generates account numbers, enforces the rule "balance may never be negative", and serialises concurrent balance updates. |
| **Transaction processing** | Executes deposits, withdrawals and transfers by asking account management to change balances, and stores a permanent record of each transaction. Coordinates transfers across two accounts and undoes a half-finished transfer if needed. |
| **Account client (integration)** | Encapsulates all communication from transaction processing to account management (REST calls, timeouts, error translation). |
| **Persistence** | Stores account data and transaction data durably. |
| **Ingress** | Exposes the application to the outside of the Kubernetes cluster. |
| **Autoscaling** | Adds or removes pods of each microservice independently, based on CPU load. |

### 2.3 Mapping: software components → microservices

| Software component | Implemented in microservice | Code location |
|---|---|---|
| Web UI | `frontend` | `frontend/src/app/` (pages, shared, core) |
| API gateway / reverse proxy | `frontend` (nginx) | `frontend/nginx.conf` |
| Account management | `account-service` | `backend/account-service/` |
| Transaction processing | `transaction-service` | `backend/transaction-service/` |
| Account client (integration) | `transaction-service` | `.../transaction/client/AccountClient.java` |
| Persistence – accounts | `postgres` (database `accountdb`) | `k8s/03-postgres.yaml` |
| Persistence – transactions | `postgres` (database `transactiondb`) | `k8s/02-postgres-init.yaml`, `k8s/03-postgres.yaml` |
| External access | Kubernetes Ingress | `k8s/07-ingress.yaml` |
| Autoscaling | Kubernetes HorizontalPodAutoscaler | `k8s/08-hpa.yaml` |

### 2.4 Internal structure of the microservices

Each backend service uses the same simple **layered architecture**, with one folder per layer:

| Layer (folder) | Responsibility |
|---|---|
| `controller/` | REST endpoints, HTTP status codes, request validation (`@Valid`) |
| `service/` | Business rules and transaction boundaries |
| `repository/` | Database access with Spring Data JPA |
| `model/` | JPA entities (database tables) |
| `dto/` | Request and response objects (Java records) of the REST API |
| `exception/` | Domain exceptions and a global handler that returns `{"error": "..."}` |
| `client/` | (transaction-service only) REST client to account-service |

The Angular frontend is organised the same way: `core/models` (data types), `core/services`
(HTTP calls), `shared/` (header, footer) and `pages/` (one folder per page).

### 2.5 REST APIs

**account-service** (port 8081)

| Method | Path | Description | Public? |
|---|---|---|---|
| GET | `/api/accounts` | List all accounts | Yes |
| GET | `/api/accounts/{id}` | Get one account | Yes |
| POST | `/api/accounts` | Create an account `{ownerName, initialDeposit}` | Yes |
| PUT | `/api/accounts/{id}/balance` | Add/remove money `{amount}` (negative = withdraw) | **No** – internal, blocked by gateway |

**transaction-service** (port 8082)

| Method | Path | Description |
|---|---|---|
| POST | `/api/transactions/deposit` | `{accountId, amount}` |
| POST | `/api/transactions/withdraw` | `{accountId, amount}` |
| POST | `/api/transactions/transfer` | `{fromAccountId, toAccountId, amount}` |
| GET | `/api/transactions?accountId={id}` | History of one account |

Both services also expose `/actuator/health/liveness` and `/actuator/health/readiness`
for Kubernetes health checks. Errors are always returned as JSON, e.g.
`{"error": "Insufficient funds on account 1"}`.

### 2.6 Kubernetes deployment design

| Kubernetes object | Purpose |
|---|---|
| `Namespace svenskbank` | Groups and isolates all application resources. |
| `Secret postgres-credentials` | Database username and password, injected as environment variables. |
| `ConfigMap postgres-init` | SQL that creates the second database (`transactiondb`) on first start. |
| `StatefulSet postgres` + `Service postgres` | One database pod with a stable identity and its own persistent volume. |
| `PersistentVolumeClaim data-postgres-0` (1 Gi) | Durable storage for the database files. |
| `Deployment` + `Service` per microservice | Stateless pods (2 replicas each) behind a stable internal DNS name. |
| `Ingress svenskbank` | Routes external HTTP traffic to the frontend. |
| `HorizontalPodAutoscaler` × 3 | Scales each microservice independently between 2 and 5 pods at 70 % CPU. |

Every application pod is configured with:

- **Startup, readiness and liveness probes** (Spring Boot Actuator / HTTP checks).
- **Resource requests and limits** (CPU and memory), needed for scheduling and autoscaling.
- **Security context**: `runAsNonRoot: true` and `allowPrivilegeEscalation: false`.
- An **init container** (`wait-for-postgres`) in the Java services, which waits until the
  database accepts connections before the application starts.

### 2.7 Architecture principles and cloud patterns used

| Principle / pattern | How it is used in SvenskBank |
|---|---|
| **Microservices** | The system is split by business capability: accounts, transactions and user interface. Each is built, deployed and scaled separately. |
| **Single responsibility / bounded context** | account-service is the only owner of balances; transaction-service never writes balances directly but asks account-service. |
| **Database per service** | Each service has its own logical database (`accountdb`, `transactiondb`). No service reads another service's tables. To save resources, both databases run on one PostgreSQL server. |
| **API Gateway (Gateway Routing)** | nginx in the frontend routes `/api/*` calls to the correct service and hides internal endpoints. The browser only talks to one origin, so no CORS configuration is needed. |
| **Service discovery** | Services find each other through Kubernetes DNS names (`account-service`, `postgres`), not IP addresses. |
| **Stateless services + horizontal scaling** | The Java services and the frontend keep no session state in memory, so any pod can serve any request, and pods can be added or removed freely (Deployments + HPA). |
| **Compensating transaction (Saga-style)** | A transfer is two separate calls (withdraw, then deposit). If the deposit fails, the withdrawal is reversed with a compensating call. |
| **Timeouts / fail fast** | The account client uses a 2 s connect and 5 s read timeout, and returns HTTP 503 instead of blocking when account-service is unavailable. |
| **Health Endpoint Monitoring** | Liveness, readiness and startup probes let Kubernetes restart broken pods and send traffic only to ready pods. |
| **Externalized configuration (12-factor)** | All environment-specific settings (database host, credentials, service URLs) come from environment variables, Secrets and ConfigMaps. The same image runs in every environment. |
| **Concurrency control** | Balance updates use a pessimistic database row lock, so that several replicas of account-service cannot overwrite each other's updates. |
| **Immutable infrastructure** | Each service is shipped as a versioned, immutable image (`:1.0`, `:1.1`), built with multi-stage Dockerfiles. A new version is rolled out by changing the image tag. |
| **Stateful workload with persistent storage** | The database runs as a StatefulSet with a PersistentVolumeClaim, so data outlives pods and cluster restarts. |
| **Self-healing** | Kubernetes automatically restarts failed containers and recreates deleted pods. |

### 2.8 How the requirements are fulfilled

| Requirement | Solution | Evidence |
|---|---|---|
| Deployable using Kubernetes | Manifests in `k8s/`, deployed with `kubectl apply -f k8s/` | All 7 pods `Running` (section 6) |
| At least two different types of microservices and a database | account-service, transaction-service, frontend + postgres | Section 2.3 |
| Each microservice has a REST API | Spring Boot REST controllers; frontend serves HTTP and proxies the APIs | Section 2.5, tests in 6.1 |
| Accessible from outside Kubernetes | Ingress (NGINX) → frontend | Browser via `minikube tunnel` at `http://127.0.0.1` |
| Each microservice independently horizontally scalable | Separate Deployment and HPA per service; stateless design | `kubectl scale` / HPA per service |
| Images on Docker Hub | `gragavig/svenskbank-{account-service, transaction-service, frontend}:1.1` (amd64 + arm64) | Pods pulled images from Docker Hub (6.3) |
| Database as a separate microservice | PostgreSQL StatefulSet with its own Service | `k8s/03-postgres.yaml` |
| Persistent storage across restarts | PersistentVolumeClaim (1 Gi) | Data survived pod deletion and full cluster restart (6.2) |
| Program a REST API and use one programmatically | Two Spring Boot REST services; transaction-service calls account-service with a REST client; the Angular app calls both APIs | Logs in 6.5 |
| Only own microservices (except database) | All three application services are written for this project. nginx is only the web server inside the own frontend service; the Ingress controller is Kubernetes infrastructure. | Source code in `backend/`, `frontend/` |
| Acknowledge scaling assumptions | Described in section 1.4 | – |

### 2.9 Business implications of the architecture decisions

| Decision | Business implication |
|---|---|
| **Split into account and transaction services** | Payment capacity can be increased without paying for extra capacity for account management. Two teams can deliver new features in parallel, which shortens time-to-market. The price is higher development and operations effort than for a single application. |
| **Independent horizontal scaling with autoscaling** | The bank pays for extra compute only during peaks (salary day) instead of for peak capacity all month. Customers get stable response times when load is high, which protects revenue and reputation. |
| **Multiple replicas and rolling updates** | New versions can be released during the day without a maintenance window, and one failing pod does not stop the service. Less downtime means fewer lost payments and fewer customer complaints. |
| **Database per service** | Teams can change their own data model without coordinating with other teams. On the other hand, reports that combine accounts and transactions need extra work (e.g. a separate reporting database), and consistency across services must be handled in code. |
| **Compensating transactions instead of distributed transactions** | Simpler and more scalable, but in rare failure cases a transfer can be left half-done. The bank needs monitoring and a reconciliation process – a real operational and regulatory cost for a financial company. |
| **One PostgreSQL server** | Low cost and simple operations for a small system, but it is a single point of failure. For a real bank a highly available, backed-up database (often a managed cloud service) is required, which increases cost. |
| **Kubernetes and containers** | No lock-in to a single cloud provider – the same manifests run on any Kubernetes cluster, which strengthens the bank's negotiating position and makes migration possible. It requires staff with Kubernetes knowledge. |
| **Security gaps (no login, no TLS) left open in the demo** | Acceptable for a study project only. For a real bank, authentication, encryption and audit logging are legal requirements (e.g. PSD2 strong customer authentication, GDPR); missing them would mean fines and loss of customer trust. |

---

## 3. Benefits of the architecture

1. **Independent scaling.** Transactions usually see more load than account creation.
   Each service has its own Deployment and autoscaler, so only the busy part is scaled,
   which saves resources.
2. **Independent deployment.** A change in the transaction logic only requires a new
   transaction-service image. The other services keep running unchanged.
3. **Fault isolation.** If transaction-service crashes, users can still view accounts.
   If account-service is down, transaction-service answers with a clear 503 error
   instead of hanging (timeouts).
4. **High availability of the stateless tier.** Every service runs at least two pods,
   so one pod can fail or be updated without downtime (rolling updates).
5. **Self-healing.** Probes and Kubernetes controllers automatically restart failed
   containers and recreate missing pods; this was observed during testing (section 6).
6. **Clear ownership of data.** Database-per-service means that each schema can change
   without breaking another service, and the rule "balances can only be changed by
   account-service" is enforced by the architecture itself.
7. **Portability.** Multi-architecture images and plain Kubernetes manifests run on any
   Kubernetes cluster (minikube, a cloud provider, Intel or ARM machines).
8. **Simple, maintainable code.** The same layered structure in every service makes the
   code easy to read, test and extend.
9. **Single entry point.** Only the Ingress is reachable from outside. All other services
   use internal `ClusterIP` services, which reduces the attack surface.

---

## 4. Challenges and mitigations

| # | Challenge | Impact | What has been done | What can be done further |
|---|---|---|---|---|
| 1 | **Data consistency across services.** A transfer spans two services and two databases; there is no distributed transaction. | If transaction-service crashes between "withdraw" and "deposit", money can be withdrawn but not deposited. If the compensation itself fails, the accounts become inconsistent. | Compensating action (refund) when the deposit fails; transaction record saved only after both steps succeeded. | Use the **Transactional Outbox** pattern with a message broker (e.g. Kafka/RabbitMQ), **idempotency keys** so retries are safe, and a periodic **reconciliation job**. |
| 2 | **Single database instance** is a single point of failure. | If the postgres pod or its node fails, the whole application is unavailable until it is recreated. | Persistent volume, so no data is lost; Kubernetes recreates the pod automatically. | Use a managed database (e.g. Amazon RDS, Cloud SQL) or a PostgreSQL operator with replication and automatic failover (e.g. CloudNativePG), plus regular **backups**. |
| 3 | **Startup ordering.** The Java services crashed and restarted when they started before the database was ready. | Noisy restarts, slower startup. | **Init container** `wait-for-postgres` added – after the change the services started with 0 restarts. | – |
| 4 | **nginx resolved service names only at startup.** In version 1.0, after a full cluster restart, the frontend restarted a few times until cluster DNS was ready. | Short delay before the frontend is available after a cluster restart. | **Fixed in version 1.1:** nginx reads the cluster DNS server at container start and resolves service names per request. Tested: with cluster DNS switched off, a new frontend pod started with 0 restarts. | – |
| 5 | **Network latency and partial failures** between services. | Slow or failing account-service can slow down transactions. | Timeouts (2 s / 5 s), clear 503 error, readiness probes. | Add **retries with back-off** and a **circuit breaker** (e.g. Resilience4j). |
| 6 | **Limited observability.** Logs are spread over many pods and there are no metrics dashboards. | Hard to find the cause of errors across several services. | Health probes; every business operation and every call between services is logged (without personal data such as names); nginx access logs; `kubectl logs`. | Metrics with **Prometheus/Grafana**, centralised logging (e.g. Loki or EFK), and **distributed tracing** with OpenTelemetry. |
| 7 | **Schema management.** Tables are created by Hibernate (`ddl-auto=update`). | Uncontrolled schema changes; not suitable for production. | Acceptable for a study project. | Versioned migrations with **Flyway** or Liquibase. |
| 8 | **Lock contention.** Pessimistic row locks serialise updates on the same account. | Many simultaneous transfers on one account will queue up. | Locks are short (one row, one transaction). | Optimistic locking with retry, or event-sourced balances for very high load. |
| 9 | **Operational complexity.** Four components, Kubernetes objects, images and a registry are more work than one monolith. | Higher learning curve and more things to maintain. | Consistent structure, small number of services, all configuration as code in `k8s/`. | Helm or Kustomize for packaging, and a **CI/CD pipeline** to build, scan and deploy automatically. |
| 10 | **Image size** of the Java services (~390 MB). | Slower pulls and scale-out. | Multi-stage builds (no build tools in runtime image). | Smaller base images (e.g. distroless or a `jlink` custom runtime). |

---

## 5. Security discussion

Security was considered at every layer. The table below lists the main threats, what has
already been implemented, and what should be added before a real bank could use the system.

### 5.1 Measures already implemented

| Area | Measure |
|---|---|
| **Network exposure** | Only the Ingress is reachable from outside. All services use internal `ClusterIP` Services. |
| **Internal endpoint protection** | The balance endpoint `PUT /api/accounts/{id}/balance` is blocked by the gateway – a test call from outside returned **HTTP 403**. |
| **Input validation** | Bean Validation on all requests (e.g. `amount ≥ 0.01`, max 2 decimals, name required, max 100 characters). Business rule: balance can never go negative. |
| **Error handling** | A global exception handler returns short JSON messages; stack traces and internal details are never sent to the client. |
| **No secrets in code or images** | The database password is not in the source code or Docker images. It is injected from a Kubernetes Secret at runtime. |
| **Container hardening** | All application containers run as **non-root** users (uid 1001 for Java, uid 101 for nginx), with `allowPrivilegeEscalation: false`. Runtime images contain no build tools (multi-stage builds). |
| **Resource limits** | CPU and memory limits on every pod prevent one service from exhausting the node (basic protection against resource-exhaustion attacks). |
| **Registry access** | Images are pushed with a personal access token with limited scope and expiry, instead of the account password. |

### 5.2 Identified risks and mitigations

| # | Risk | Description | Mitigation |
|---|---|---|---|
| S1 | **No authentication or authorisation** | Anyone who can reach the application can create accounts and move money between any accounts. This is the most serious gap. | Add login with **OpenID Connect / OAuth 2.0** (e.g. Keycloak or a cloud identity provider). Validate **JWT tokens** in every service (Spring Security resource server) and check that the user owns the account before any operation. |
| S2 | **No encryption in transit** | Traffic from the browser and between services is plain HTTP, so it could be read or changed on the network. | Enable **TLS at the Ingress** (e.g. cert-manager with Let's Encrypt). Use **mutual TLS** between services with a service mesh (Istio or Linkerd), and TLS for database connections. |
| S3 | **Internal API reachable inside the cluster** | The gateway blocks the balance endpoint from outside, but any pod inside the cluster could still call it. | Add **Kubernetes NetworkPolicies**: only transaction-service may call account-service's balance endpoint, and only the two Java services may connect to postgres. Add service-to-service authentication (mTLS identities or signed service tokens). |
| S4 | **Secret handling** | The Secret is stored as plain text in a YAML file, Kubernetes Secrets are only base64-encoded, and both services share one database user. | Use **Sealed Secrets, External Secrets or HashiCorp Vault**; enable **encryption at rest** for etcd; create **separate database users per service** with access only to their own database (least privilege). |
| S5 | **Further container hardening** | Containers could still write to their file system and keep default Linux capabilities. | Set `readOnlyRootFilesystem: true`, drop all capabilities, use the `RuntimeDefault` seccomp profile, and enforce the **Pod Security Standard "restricted"** on the namespace. |
| S6 | **Software supply chain** | Images are public on Docker Hub, base images may contain known vulnerabilities, and tags such as `:1.0` can be overwritten. | **Scan images** (e.g. Trivy or Docker Scout) in a CI pipeline, refer to images by **digest**, **sign images** (cosign) and verify signatures at deploy time, use a private registry for real systems. |
| S7 | **Abuse and denial of service** | Unlimited requests could overload the services or the database. | **Rate limiting** at the Ingress, request size limits, autoscaling (already present) and limits per user once authentication exists. |
| S8 | **Auditing** | Transactions are recorded, but not *who* performed them. | Store the authenticated user in each transaction and send security-relevant events to a central, tamper-resistant audit log. |
| S9 | **Data protection** | Personal data (account holder names) and financial data are stored unencrypted on the volume. | Encrypted storage classes / disk encryption, encrypted backups, and data retention rules (GDPR). |

---

## 6. Verification and test evidence

All tests were performed on the deployed Kubernetes cluster (minikube, Kubernetes v1.37).

### 6.1 Functional end-to-end test (through the gateway)

| Test | Result |
|---|---|
| Load home page | HTTP 200 |
| Create account "Anna Andersson" with 1000 kr | Created, account number `SB4698997105` |
| Create account "Erik Svensson" with 0 kr | Created, account number `SB9912766679` |
| Deposit 500 kr to Erik | Transaction `DEPOSIT` recorded |
| Transfer 300 kr from Anna to Erik | Transaction `TRANSFER` recorded |
| Withdraw 9999 kr from Anna | Rejected: `{"error":"Insufficient funds on account 1"}` |
| Call internal balance endpoint from outside | **HTTP 403** (blocked) |
| Final balances | Anna 700.00 kr, Erik 800.00 kr – correct |
| History for Erik | Transfer and deposit, newest first – correct |

### 6.2 Persistence tests

| Test | Result |
|---|---|
| Delete the database pod (`kubectl delete pod postgres-0`) | Kubernetes created a new pod (new UID). Both accounts (700.00 / 800.00) and both transactions were still in the database. |
| Stop and start the whole cluster (`minikube stop` / `minikube start`) | After restart all pods became ready, and the REST API returned the same accounts and balances. |

### 6.3 Images pulled from Docker Hub

All locally loaded copies of the application images were removed from the cluster. After
scaling the deployments up again, Kubernetes reported
`Successfully pulled image "gragavig/svenskbank-…:1.0"` for all three services, and the
application returned the stored data. The images are published for both `linux/amd64` and
`linux/arm64`.

### 6.4 Resilience observations

| Observation | Result |
|---|---|
| Java services started before the database (first deployment) | Containers restarted automatically until the DB was ready; fixed with an init container → 0 restarts afterwards. |
| Rolling update of the Java services | New pods became ready before the old pods were terminated (Kubernetes rolling update strategy). |
| Update from version 1.0 to 1.1 | Changing the image tag and running `kubectl apply` replaced all pods of the three services by rolling update; data was unaffected. |
| Frontend start while cluster DNS is unavailable (version 1.1) | Cluster DNS (CoreDNS) was scaled to 0 and a frontend pod deleted. The new pod started and stayed `Running` with 0 restarts. |

### 6.5 Log output – interaction between the microservices

A transfer of 50 kr from account 1 to account 2, followed by a withdrawal that is too large,
produces the following log lines (shortened):

```
frontend (nginx)       POST /api/transactions/transfer 201
transaction-service    INFO Transfer of 50 from account id=1 to account id=2
transaction-service    INFO Calling account-service: PUT /api/accounts/1/balance amount=-50
account-service        INFO Balance of account id=1 changed by -50 -> new balance 650.00
transaction-service    INFO Calling account-service: PUT /api/accounts/2/balance amount=50
account-service        INFO Balance of account id=2 changed by 50 -> new balance 850.00
transaction-service    INFO Saved TRANSFER transaction id=3

frontend (nginx)       POST /api/transactions/withdraw 400
transaction-service    INFO Withdrawal of 99999 from account id=2
transaction-service    INFO Calling account-service: PUT /api/accounts/2/balance amount=-99999
account-service        WARN Rejected balance change of -99999 on account id=2: insufficient funds
transaction-service    WARN account-service rejected the request: HTTP 400
```

This shows the full request path: browser → gateway → transaction-service → account-service
→ database, and how an error from one service is passed back to the user.

---

## 7. How to deploy

Prerequisites: a Kubernetes cluster with an NGINX Ingress Controller and metrics-server
(for example minikube), and `kubectl`.

```bash
# 0. Get the source code
git clone https://github.com/ragavi-gunasekaran/svenskbank-cloud.git
cd svenskbank-cloud

# 1. Start a local cluster (example with minikube)
minikube start -p svenskbank --driver=docker --cpus=4 --memory=4g
minikube -p svenskbank addons enable ingress
minikube -p svenskbank addons enable metrics-server

# 2. Deploy everything (images are pulled from Docker Hub)
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/

# 3. Wait until all pods are running
kubectl -n svenskbank get pods

# 4. Open the application in a browser
minikube -p svenskbank tunnel        # keep running, then open http://127.0.0.1
```

Scaling a single microservice manually:

```bash
kubectl -n svenskbank scale deployment transaction-service --replicas=4
```

Building and publishing the images (multi-architecture):

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t gragavig/svenskbank-account-service:1.1 --push backend/account-service
docker buildx build --platform linux/amd64,linux/arm64 \
  -t gragavig/svenskbank-transaction-service:1.1 --push backend/transaction-service
docker buildx build --platform linux/amd64,linux/arm64 \
  -t gragavig/svenskbank-frontend:1.1 --push frontend
```

### Project structure

```
svenskbank/
├── backend/
│   ├── account-service/        Spring Boot: controller, service, repository, model, dto, exception
│   └── transaction-service/    Spring Boot: + client (REST client to account-service)
├── frontend/                   Angular: core (models, services), shared (header, footer), pages
│   └── nginx.conf              gateway / reverse proxy configuration
├── k8s/                        Kubernetes manifests (00–08)
└── docs/                       this report
```

---

## 8. Conclusion

SvenskBank meets all requirements of the assignment: it consists of two Java
microservices, an Angular/nginx frontend and a PostgreSQL database, each running as its own
Kubernetes workload. Every service offers a REST API, the application is reachable from a
browser through an Ingress, every stateless service can be scaled independently, the images
are published on Docker Hub, and the data survives both pod and cluster restarts.

The design uses well-known cloud patterns – microservices, database per service, an API
gateway, health monitoring, externalised configuration and compensating transactions – and
keeps the code simple with a clear layered structure. The main challenges are data
consistency across services and the missing authentication and encryption. For each of
these, concrete mitigations have been identified, such as the outbox pattern, OpenID
Connect with JWT, TLS/mTLS, NetworkPolicies and proper secret management, which would be the
next steps to make the system ready for real banking use.
