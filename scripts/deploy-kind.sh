#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cluster="${KIND_CLUSTER:-seatlock}"
context="kind-$cluster"
namespace=seatlock
docker build -t seatlock-core:local backend
docker build -t seatlock-web:local frontend
if ! kind get clusters | grep -Fxq "$cluster"; then
  kind create cluster --name "$cluster" --wait 90s
fi
kind load docker-image --name "$cluster" seatlock-core:local seatlock-web:local
kube() { kubectl --context "$context" --namespace "$namespace" "$@"; }
kubectl --context "$context" create namespace "$namespace" --dry-run=client -o yaml | kubectl --context "$context" apply -f -
if ! kube get secret seatlock-secrets >/dev/null 2>&1; then
  umask 077
  mkdir -p .local
  env_file="$PWD/.local/kind-${cluster}.env"
  if [[ ! -f "$env_file" ]]; then
    for key in DB_PASSWORD JWT_SECRET ADMIN_PASSWORD SPRING_RABBITMQ_PASSWORD; do
      printf '%s=%s\n' "$key" "$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
    done > "$env_file"
  fi
  kube create secret generic seatlock-secrets --from-env-file="$env_file"
fi
kube apply -f infra/k8s/dependencies.yaml
kube rollout status statefulset/db --timeout=180s
kube rollout status statefulset/rabbit --timeout=180s
kube rollout status deployment/redis --timeout=120s
kube apply -f infra/k8s/app.yaml
kube rollout status deployment/seatlock-api --timeout=180s
kube rollout status deployment/seatlock-worker --timeout=120s
kube rollout status deployment/seatlock-web --timeout=120s
printf 'Admin email: admin@seatlock.local. Read ADMIN_PASSWORD from .local/kind-%s.env.\n' "$cluster"
kube port-forward svc/seatlock-web 8080:8080
