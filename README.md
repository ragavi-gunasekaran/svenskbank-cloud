# SvenskBank

A small web banking application built as microservices and deployed on Kubernetes.
Users can open accounts, deposit, withdraw and transfer money, and view the transaction history.

📄 **Full report** (description, architecture, benefits, challenges and security):
[docs/SvenskBank-Report.md](docs/SvenskBank-Report.md)

## Microservices

| Service | Technology | Responsibility |
|---|---|---|
| `frontend` | Angular + nginx | Web user interface; nginx also routes `/api/*` calls to the backend services (API gateway) |
| `account-service` | Java 21, Spring Boot | Accounts and balances – REST API `/api/accounts` |
| `transaction-service` | Java 21, Spring Boot | Deposits, withdrawals, transfers, history – REST API `/api/transactions`; calls account-service over REST |
| `postgres` | PostgreSQL 16 | Database with persistent storage (StatefulSet + PersistentVolumeClaim) |

Docker images: [hub.docker.com/u/gragavig](https://hub.docker.com/u/gragavig)

## Project structure

```
backend/account-service/       Spring Boot service (controller, service, repository, model, dto, exception)
backend/transaction-service/   Spring Boot service (+ client for calling account-service)
frontend/                      Angular app, nginx.conf, Dockerfile
k8s/                           Kubernetes manifests
docs/                          Project report
```

## Run on Kubernetes (minikube)

```bash
minikube start --cpus=4 --memory=4g
minikube addons enable ingress
minikube addons enable metrics-server

kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/
kubectl -n svenskbank get pods        # wait until all pods are Running

minikube tunnel                       # keep running, then open http://127.0.0.1
```

Scale a single service independently:

```bash
kubectl -n svenskbank scale deployment transaction-service --replicas=4
```
