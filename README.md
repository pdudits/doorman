# Doorman

Doorman is a Kubernetes operator that scales deployments to zero when they receive no traffic, then scales them back up on the first incoming request. While the deployment is scaled down, Doorman registers itself as the service endpoint and holds incoming HTTP requests. Once the deployment is ready, it redirects the held requests with HTTP 307 and removes itself from the endpoint, allowing the real pods to serve traffic normally.

Traefik is currently the supported ingress controller. Doorman reads Traefik's Prometheus metrics to detect inactivity and uses Traefik's error page mechanism to intercept requests to scaled-down services.

## How it works

1. Doorman watches ScalingPolicy custom resources, which pair a service with a deployment.
2. When a service has received no requests for the configured idle timeout, Doorman scales the deployment to zero.
3. Doorman inserts itself into the service's Endpoints and EndpointSlice objects so that incoming requests are routed to it.
4. When a request arrives, Doorman scales the deployment back up and holds the request.
5. Once the deployment's pods become ready, Doorman removes itself from the endpoints and responds with HTTP 307 to all held requests, directing clients to retry against the real service.

## Prerequisites

- Kubernetes 1.30 or later (in theory tested on current releases)
- Traefik ingress controller with Prometheus metrics enabled (Traefik 3 recommended)

## Installation

Apply the CRD separately, then deploy with kustomize:

```
kubectl apply -f deploy/scalingpolicy-crd.yaml
kubectl apply -k deploy/
```

### Defining a scaling policy

```yaml
apiVersion: doorman.zeromagic.io/v1alpha1
kind: ScalingPolicy
metadata:
  name: my-app
  namespace: default
spec:
  serviceName: my-app
  deploymentName: my-app
```

## Build

### Common targets

| Command | Description |
|---|---|
| `mvn package` | Compile, test, and build the Docker image |
| `mvn deploy` | Build and push the Docker image to the registry |
| `mvn verify` | Run unit and integration tests (requires Docker) |
| `mvn license:format` | Apply Apache 2.0 license headers to all Java sources |

### Profiles

| Profile | Description |
|---|---|
| `shade` | Build a self-contained fat JAR (`doorman-shaded.jar`) in addition to the layered image |
| `multiplatform` | Build and push multi-architecture images for `linux/amd64` and `linux/arm64` |

Example: `mvn deploy -P multiplatform`

### Key build properties

| Property | Default | Description |
|---|---|---|
| `docker.base.image` | `azul/zulu-openjdk-alpine:25-latest` | Base image for the Docker build |
| `docker.image.name` | `docker.io/pdudits/doorman` | Image repository and name |
| `docker.platforms` | (empty, native) | Comma-separated platform list for buildx |

## Configuration reference

Doorman is configured via command-line flags or environment variables.

| Flag | Env var | Default | Description |
|---|---|---|---|
| `--kube-context` | `KUBE_CONTEXT` | (in-cluster) | Kubernetes context name; omit when running in-cluster |
| `--traefik-metrics-url` | `TRAEFIK_METRICS_URL` | | Direct URL to Traefik's Prometheus metrics endpoint |
| `--traefik-namespace` | `TRAEFIK_NAMESPACE` | | Namespace to discover Traefik pods (alternative to direct URL) |
| `--traefik-label-selector` | `TRAEFIK_LABEL_SELECTOR` | | Label selector for Traefik pods |
| `--traefik-metrics-port` | `TRAEFIK_METRICS_PORT` | `9100` | Port on which Traefik exposes metrics |
| `--idle-timeout` | `IDLE_TIMEOUT` | `5m` | Duration without traffic before scaling down |
| `--proxy-port` | `DOORMAN_PROXY_PORT` | `8080` | Port Doorman listens on for held requests |
| `--scale-up-timeout` | `SCALE_UP_TIMEOUT` | `60s` | Maximum time to wait for a deployment to become ready |
| `--propagation-delay` | `PROPAGATION_DELAY` | `2s` | Time to wait after removing Doorman from endpoints before redirecting held requests |

When running in-cluster, `POD_IP` is automatically injected by the Deployment via the Kubernetes downward API and does not need to be set manually.
