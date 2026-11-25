# ERENO 2.0

ERENO generates synthetic IEC 61850 datasets (GOOSE + Sampled Values) with labelled cyberattacks, for research on intrusion detection in digital substations. This repository holds two modules:

| Module | What it is | Stack |
|---|---|---|
| [`api/`](api/) | The ERENO core (traffic and attack generation, Weka training/evaluation) plus the HTTP API the UI talks to | Java 11, Maven |
| [`app/`](app/) | ERENO UI: a wizard to configure a dataset, generate it and download it | React 19, Vite, Tailwind |

The core is forked from [sequincozes/ereno](https://github.com/sequincozes/ereno); all credit for it goes to its authors (see [`api/README.md`](api/README.md)).

## Run everything locally

Requirements: Java 11+ (JDK), Maven (or the bundled `api/mvnw`), Node.js 20+.

```powershell
./dev.ps1                  # Windows PowerShell
./dev.ps1 -ApiPort 8081    # if port 8080 is taken (e.g. by XAMPP/Apache)
```

```bash
./dev.sh                         # Linux / macOS / Git Bash
ERENO_API_PORT=8081 ./dev.sh
```

The script builds the backend when its sources changed, installs the UI dependencies on first use, starts the API, waits for it, then starts the UI. Open <http://localhost:5173>. `Ctrl+C` stops both.

Without an `app/.env`, the UI runs in local mode: no login, everything goes to the local backend. To enable Google login and the personal preset library, copy [`app/.env.example`](app/.env.example) to `app/.env` and fill in the Firebase settings.

## Run with Docker

Requirements: Docker with Compose v2. No Java or Node needed on the host.

```bash
docker compose up --build -d                                  # then open http://localhost:5173
GIT_COMMIT=$(git rev-parse HEAD) docker compose up --build -d # records the commit in each run's manifest
docker compose logs -f api                                    # backend log
docker compose down                                           # stop (runs are kept in the ereno-runs volume)
```

- **`api`** is the core plus the HTTP API.
  - `api/electrical-sources` is mounted read-only and `api/config` is mounted read/write.
  - Runs live in the `ereno-runs` volume.
  - Its port is not published to the host.
- **`ui`** is nginx serving the built UI. It also proxies `/api` to the backend and adds the API token on the way. The token never reaches the browser, and the backend rejects requests without it.
- The UI is published on `127.0.0.1:5173` only.

Optional settings go in a `.env` next to `docker-compose.yml` (see [`.env.example`](.env.example)):
- the token, the host/port of the UI, workers and `-Xmx` for each generation;
- Firebase settings, which are baked into the UI at build time.

Generation inside the container is slower on Windows/macOS because the electrical sources are read through a bind mount.

Run datasets are byte-identical across Windows, Linux and the image for the same spec and seed: every generation process writes `\n` line endings.

## How a dataset is generated

1. The UI walks through **IED → GOOSE flow → benign traffic → attacks → generate**. Its forms come from the backend's `GET /api/catalog`, which reads the core's own templates (`api/config/attacks/*.json`) and defaults (`api/config/actions/`). A new attack template therefore shows up in the UI without frontend changes.
2. **Generate** sends the whole configuration to `POST /api/runs`. The backend validates it and returns errors per field, which the UI shows on the right step.
3. A valid run is queued. The backend writes a `create_benign` and a `create_attack_dataset` action config, then runs each step as a child JVM of `ActionRunner`. One run executes at a time by default (`-Dereno.api.workers=N`).
4. Everything of a run stays in `api/runs/{id}/`:
   - `spec.json`, the exact request
   - `steps/`, the generated action configs
   - `run.log`
   - `benign/`, the benign traffic
   - `output/`, the labelled dataset
   - `manifest.json`, with the seed, the git commit and a SHA-256 for every file

   The same spec and seed give a byte-identical dataset. **Run again** in the UI regenerates it.

## Backend API (`java -jar api/target/ERENO-1.0-SNAPSHOT-uber.jar serve`, run from `api/`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | liveness |
| GET | `/api/catalog` | attack templates, electrical-source resolutions, defaults |
| POST | `/api/runs` | validate and queue a run (201, or 400 with `errors[{field, message}]`) |
| GET | `/api/runs` · `/api/runs/{id}` | list runs · status, spec, files and log tail |
| GET | `/api/runs/{id}/log` · `/api/runs/{id}/files/{path}` | full log · download a file |
| DELETE | `/api/runs/{id}` | cancel an active run, or delete a finished one |
| GET/PUT/DELETE | `/api/configs/...` | read and edit the core's JSON configs |
| GET | `/api/artifacts/...` | files under `api/target/` |

The server binds to `127.0.0.1` by default.
- To bind to another address, set `ERENO_API_TOKEN`; the UI then needs `VITE_API_TOKEN`.
- `ERENO_API_CORS_ORIGIN` sets the allowed UI origin (default `http://localhost:5173`).
- `-Dereno.runner.jvmArgs="-Xmx8g"` passes JVM options to the generation processes, for large datasets.

The core keeps working from the command line as before, e.g. `java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_quickstart.json` from `api/`.

## Tests

```bash
cd api && mvn test     # core + API, including a real small generation (RunEndToEndTest)
cd app && npm test     # run-spec builder (vitest); also: npm run lint, npm run build
```

## License

`LICENSE.MD` (MIT, from the original ERENO core) and `LICENSE` (Apache 2.0, from the UI) both exist for now. The group still has to settle on a single license for the merged repository.
