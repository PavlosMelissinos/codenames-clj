# AGENTS.md

## Project overview

Clojure monorepo: game logic + desktop UI at root, web UI in `projects/ui_web/`.

- `src/codenames_clj/core.clj` — game logic (grid generation, card reveal, state)
- `src/codenames_clj/ui.clj` — JavaFX (cljfx) desktop UI
- `projects/ui_web/` — Biff web app (HTMX + XTDB + Rum)

## Commands

```sh
# Web project (projects/ui_web/)
bb dev          # start dev server + nREPL (port 8080, nREPL on 7888)
bb format       # format code (cljfmt)
bb deploy       # push to production
```

No automated build/test/lint at root. The root `:test` alias in `deps.edn` references a nonexistent `kaocha.edn`.

## Architecture

`projects/ui_web/deps.edn` depends on root game logic via `:local/root "../../"`. Both projects share `codenames-clj.core`.

`resources/` is on root's classpath (`:paths ["src" "resources"]`). `config.edn` and word lists are loaded via `io/resource`.

The `codenames-clj.config` namespace does not exist as a `.clj` file. The qualified keywords (`:codenames-clj.config/rows`, etc.) come from `resources/config.edn` and are accessed via Clojure 1.11 `:as-alias` — no actual namespace is required.

## Web project dev flow

`on-save` (in `codenames-clj.ui.web`) runs on file changes: re-evaluates changed namespaces, regenerates Tailwind assets, and runs tests matching `codenames-clj.ui.web.test.*`.

Wipe the dev database: `rm -r projects/ui_web/storage/xtdb`. Reload fixtures from the REPL via `(add-fixtures)` in `codenames-clj.ui.web.repl`.

## Conventions

Format indentation: `submit-tx` uses `[[:inner 0]]` (configured in `projects/ui_web/cljfmt-indents.edn`).
