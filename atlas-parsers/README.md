# atlas-parsers — Kotatsu parsers, vendored + Atlas adapter

## What this module is

Two parts in one pure-JVM module (`kotlin("jvm")`, JVM 17):

1. **Vendored Kotatsu parsers** (`org.koitharu.kotatsu.parsers`, `src/contract`
   + `src/main`) — the parser library from
   [Kotatsu-Redo/kotatsu-parsers-redo](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo),
   **HEAD `c429dceb0a8232dbffe150c6b94ad95fd7ef544a`** (2026-11-13, master).
   1,413 sources, of which 430 are marked `@Broken` upstream. GPL-3.0, see
   `LICENSE-kotatsu-parsers` (the root `LICENSE` is already GPL-3.0).
2. **Atlas adapter** (`com.atlas.parsers`) — the host side of the parser
   contract plus the mapping to the Atlas core `Source` contract:
   - `AtlasMangaLoaderContext` — OkHttp client, in-memory cookies/config,
     JVM image handling (`BufferedImage`). JS evaluation and WebView
     interception throw: only 19 parsers need JS and 16 need a WebView.
   - `KotatsuParserRegistry` — the source catalogue (broken sources hidden
     by default) and `newAdapter(name, context)`.
   - `KotatsuSourceAdapter` — a parser as an Atlas `Source` (`listBooks`,
     `searchBooks`, `listChapters`, `listPages`).
   - `KotatsuIds` — stateless book/chapter ids (no cache to go stale).

## Why vendored instead of a JitPack dependency

The Redo app consumes the parsers as
`com.github.Kotatsu-Redo:kotatsu-parsers-redo:<commit>` from JitPack, and
Atlas already proxies JitPack through its local mirror — but on this VM the
JitPack fetch times out (150 s, mirror upstream timeout is 120 s) and direct
access to jitpack.io is blocked. Vendoring is the robust option: the tree
compiles offline from the mirror, Atlas can patch a parser, and updates are
a deliberate copy + regenerate (below). If JitPack becomes reachable later,
switching back is a one-line dependency change plus deleting the vendored
sources.

## Layout

```
atlas-parsers/
├── gen_registry.py          # regenerates the parser registry (see below)
├── LICENSE-kotatsu-parsers   # upstream GPL-3.0 text
├── README.md                # this file
└── src/
    ├── contract/kotlin/org/koitharu/kotatsu/parsers/   # stable host<->parser API
    ├── main/kotlin/org/koitharu/kotatsu/parsers/       # implementations
    │   ├── model/MangaParserSource.kt   # GENERATED — 1,413 sources
    │   ├── MangaParserFactory.kt        # GENERATED — newParser dispatch
    │   ├── core/ParsersBuildInfo.kt     # GENERATED — version/revision
    │   └── com/atlas/parsers/           # (see below) Atlas adapter
    ├── main/kotlin/com/atlas/parsers/   # AtlasMangaLoaderContext, registry,
    │                                    # adapter, ids
    ├── main/java/.../site/madara/en/theblank/   # 5 Java crypto helpers
    └── test/kotlin/com/atlas/parsers/   # registry / ids / adapter / context tests
```

## The generated registry (no KSP)

Upstream generates `MangaParserSource`, `MangaParserFactory` and
`ParsersBuildInfo` at compile time with a KSP processor. Atlas does not run
KSP (it would couple the build to a KSP version for every Kotlin upgrade),
so `gen_registry.py` replicates the processor's output from the
`@MangaSourceParser` annotations — including parser classes nested inside an
abstract base class (e.g. `LineWebtoonsParser.German`). The generated files
are committed, like any other source.

## How to add a parser

**Upstream (the normal way):** add the parser class, annotated
`@MangaSourceParser("NAME", "Title", "locale")`, to kotatsu-parsers-redo
(or fix one there), then update the vendored tree (below). Atlas picks it
up with no other code change.

**Local-only (a parser that does not exist upstream):** add the class under
`src/main/kotlin/org/koitharu/kotatsu/parsers/site/<group>/` (follow the
neighbouring parsers: extend `AbstractMangaParser` or `PagedMangaParser`,
one `MangaLoaderContext` constructor parameter), then re-run the generator.
It appears in the registry automatically.

## How to update the vendored tree

1. Clone the new upstream HEAD somewhere outside this repo (e.g.
   `~/workspace/kotatsu_parsers_src`).
2. Copy `src/contract` and `src/main` over this module's `src/`
   (keep `src/main/kotlin/com/atlas/parsers` and `src/test` — they are
   Atlas's own).
3. Update `UPSTREAM_VERSION` (commit time, epoch seconds) and
   `UPSTREAM_REVISION` (short hash, 10 chars) at the top of
   `gen_registry.py`, and the HEAD line at the top of this README.
4. Run `python3 atlas-parsers/gen_registry.py` — it prints the source
   count (expect it to grow; broken count moves both ways).
5. Run `:atlas-parsers:build` and fix any compile fallout (usually a
   changed parser API or a new dependency in the upstream catalog).

## Known limits of this step

- No network behaviour is tested here: the tests use a fake parser and a
  fake HTTP layer. Real parsers hit real sites and will break as sites
  change — that is upstream's maintenance burden, not Atlas's.
- The 19 JS-dependent and 16 WebView-dependent parsers throw
  `UnsupportedOperationException` from the JVM context until a JS engine
  (desktop) / WebView (Android) hook is supplied.
- Parsers are not yet wired into the Android UI source list, the desktop
  UI, or Mihon's download/reader pipeline — the adapter and registry are
  the seam for that work; see `KotatsuParserRegistry.newAdapter`.
- Mihon's extension system is untouched: parsers are additive.
