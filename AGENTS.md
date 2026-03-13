# Doorman

## Overview
The **Doorman** is a Kubernetes operator and network service designed for scaling applications to zero and holding their requests as they spin up.

It performs following duties:

1. watches for changes to relevant resources (deployments, service endpoints & slices, custom resource for definition of scaling)
2. collects metric from ingress controller, collecting number of requests per service (traefik is currently supported)
3. scales deployment to 0 replicas when it receives no traffic over configured period
4. registers itself as service endpoint for scaled down deployment
5. receives HTTP requests for scaled down services and holds it; then scales deployment back up
6. as soon as deployment is ready it unregisters itself as an endpoint and answert all held requests for the service with HTTP 307
7. the clients are now expected to speak to real service


<!-- BACKLOG.MD MCP GUIDELINES START -->

<CRITICAL_INSTRUCTION>

## BACKLOG WORKFLOW INSTRUCTIONS

This project uses Backlog.md MCP for all task and project management activities.

**CRITICAL GUIDANCE**

- If your client supports MCP resources, read `backlog://workflow/overview` to understand when and how to use Backlog for this project.
- If your client only supports tools or the above request fails, call `backlog.get_workflow_overview()` tool to load the tool-oriented overview (it lists the matching guide tools).

- **First time working here?** Read the overview resource IMMEDIATELY to learn the workflow
- **Already familiar?** You should have the overview cached ("## Backlog.md Overview (MCP)")
- **When to read it**: BEFORE creating tasks, or when you're unsure whether to track work

These guides cover:
- Decision framework for when to create tasks
- Search-first workflow to avoid duplicates
- Links to detailed guides for task creation, execution, and finalization
- MCP tools reference

You MUST read the overview resource to understand the complete workflow. The information is NOT summarized here.

</CRITICAL_INSTRUCTION>

<!-- BACKLOG.MD MCP GUIDELINES END -->


## Agent Skills

### When to use the `testing` skill
Invoke the **`testing` skill** at the start of any task that involves writing or modifying tests — including unit tests, integration tests, manual tests, or test helpers. The skill documents project-specific patterns such as accessor classes for package-private code, the `TestManual` suffix convention, and the `DoormanClusterExtension` for real-cluster tests.

### When to use the `context7` skill
Invoke the **`context7` skill** during implementation planning whenever the task introduces or extends usage of a third-party library (e.g. Fabric8, Testcontainers, avaje, Picocli). Use it to look up current API before writing code, not after hitting a compile error.

## Technology Stack

### Language & Runtime
*   **Java 21**: Leverages modern Java features.
*   **Virtual Threads**: Used to process requests by waiting for ready signal
*   **Pattern Matching for switch**: extensively used to model state transitions and strategies
*   **Records**: Used for immutable data carriers

### Key Libraries
*   **Fabric8 Kubernetes Client**: For interacting with the Kubernetes API (watching deployments, scaling, patching resources).
*   **Traefik**: The ingress controller. The Scaler relies on Traefik metrics to detect inactivity and integrates with Traefik's error page mechanism.
*   **Picocli**: For robust command-line argument parsing and configuration.
*   **Java HTTP Client**: Standard `java.net.http` client
*   **Sun HTTP Server**: Lightweight, built-in HTTP server used for the proxy, powered by virtual threads.
*   **avaje** set of libraries for lightweight compile-time injection and configuration

