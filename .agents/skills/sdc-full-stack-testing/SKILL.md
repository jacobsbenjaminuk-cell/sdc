---
name: sdc-docker-golden-path
description: Build and run ONAP SDC locally, validate simulator login, catalog, onboarding, and Swagger with recordings, then stop the stack.
---

# SDC Docker golden-path testing

## Prerequisites
- Use the repo's configured Java/Maven environment and ONAP oparent Maven settings.
- Verify Docker is reachable, disk/memory capacity, and that ports 8080 and 8285 are available.
- Read the root and integration-tests Maven profiles before execution; start/stop profiles initialize and remove a multi-container stack.
- Record UTC timestamps at build start, start-sdc invocation, Maven completion, and first visibly rendered catalog shell. Report startup-only time separately from build-plus-start time; identify warm caches.

## Devin Secrets Needed
None for local simulator testing. Use the bundled Carlos Santana Designer quick link at `/login`; fixture identity is cs0008. Do not reuse these local fixture credentials for hosted environments.

## Build/start
1. `mvn -B clean install -DskipTests -Pdocker`
2. `mvn -B clean install -Pstart-sdc`

Maven settings mirrors do not redirect `wget` inside Dockerfiles. If Jetty downloads receive 429 from repo1.maven.org, verify the same artifact through the ONAP public repository and temporarily substitute only that URL in the backend, frontend, onboarding-backend, and simulator Dockerfiles. Preserve and restore any testing-only diff. A retry can use `mvn -B install -DskipTests -Pdocker`.

If login redirects to a blank `/sdc1`, compare `catalog-fe/src/main/webapp/index.html` to the deployed WAR index. The catalog must contain Angular `app-root`, not the onboarding `sdc-app` root. A serial frontend-only rebuild after the reactor has completed can repair a stale/incorrect package:

```sh
mvn -B clean install -pl catalog-fe -DskipTests -Pdocker
docker stop sdc-frontend-1
docker rm sdc-frontend-1
mvn -B -pl integration-tests -Pstart-sdc docker:start -Ddocker.filter=sdc-frontend
```

Verify actual container names before removal. Hard-reload the browser after replacement. Report that the original build was not sufficient; do not claim a pristine startup.

## UI assertions
- Maximize the browser and record the GUI portion.
- Open `http://localhost:8285/login`; choose Carlos Santana.
- Assert the dashboard has real navigation and content, not just a title or empty root.
- Click CATALOG; assert populated cards and Model/Status filters at `#!/catalog`.
- Click ONBOARD; assert Workspace and Onboard Catalog tabs at `#!/onboardVendor`. Switch both tabs and use the ONBOARD breadcrumb menu to return to CATALOG.
- Open `http://localhost:8080/swagger-ui/index.html`. The shell alone is insufficient: assert operations load and expand one GET operation.
- If `/sdc/openapi.json` fails, retain the visual error and confirm its status through browser navigation. It is a dynamic JAX-RS endpoint; skipped build-time Swagger generation alone does not establish the root cause.

## Diagnostics and cleanup
- Preserve Maven start logs and `/tmp/sdc-integration-tests/SDC/SDC-BE/` logs before removing containers.
- Log startup warnings separately: retries, duplicate upgrades, import conflicts, missing audit tables, and external integrations may not prevent Maven from reporting success.
- `mvn -B clean install -Pstop-sdc`; verify `docker ps` contains none of this run's containers.
- Restore temporary mirror substitutions and generated tracked files such as `asdctool/sdc-cassandra-init/version.sh`; verify `git status --short`.
- Final report must distinguish UI navigation coverage from creation/distribution workflows not exercised.
