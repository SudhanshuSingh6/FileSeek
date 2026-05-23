# FileSeek

A fast, installable CLI file indexing and full-text search engine built in Java.
FileSeek indexes your local directories once and searches across filenames and
file content in milliseconds — with ranked results, phrase matching, fuzzy search,
prefix search, regex search, and live index updates.

```
fileseek search "redis caching"

Found 4 results for "redis caching" (8ms)

[1] backend-guide.md
    /home/user/projects/backend-guide.md
    .md · 12.4 KB · modified 2d ago · score 4.2341
    "...Redis caching improves redirect performance significantly..."

[2] SpringConfig.java
    /home/user/projects/src/SpringConfig.java
    .java · 3.1 KB · modified 5d ago · score 2.8812
    "...configure Redis caching with TTL and eviction policy..."
```

---

## Stack

| Component    | Technology                  |
|--------------|-----------------------------|
| Language     | Java 17                     |
| CLI          | Picocli 4.7                 |
| PDF parsing  | Apache PDFBox 3.0           |
| Build tool   | Maven 3.8+                  |
| Index format | Custom binary (GZIP, delta) |
| Ranking      | BM25                        |

---

## Requirements

- Java 17 or higher
- Maven 3.8+ *(build only — not required after installation)*

---

## Installation

```bash
git clone https://github.com/you/fileseek.git
cd fileseek
bash scripts/install.sh
```

The install script:
- Builds the fat jar with Maven
- Installs it to `~/.fileseek/bin/fileseek.jar`
- Creates a wrapper at `/usr/local/bin/fileseek` (system-wide) or `~/bin/fileseek` (user-only)
- Generates and sources a shell completion script automatically

**Manual install:**

```bash
mvn package -DskipTests
java -jar target/fileseek.jar --help
```

**Override JVM memory for large directories:**

```bash
FILESEEK_OPTS="-Xmx2g" fileseek add ~/LargeDirectory
```

Default JVM flags in the wrapper: `-Xmx512m -Xms64m -XX:+UseG1GC`

---

## Quick Start

```bash
# Index a directory
fileseek add ~/Projects

# Search
fileseek search "redis caching"

# Watch for live changes
fileseek watch
```

---

## Commands

### `fileseek add <path>`

Index a directory. Safe to run multiple times — unchanged files are skipped,
modified files are re-indexed, deleted files are removed automatically.

```bash
fileseek add ~/Projects
fileseek add ~/Documents/Notes
fileseek add .                    # current directory
```

**What happens on each run:**

1. Counts indexable files and renders a progress bar with percentage
2. Loads the existing index from disk
3. Removes indexed documents whose files no longer exist
4. Scans and indexes files in parallel (one thread per CPU core)
5. Skips unchanged files, re-indexes modified ones
6. Saves the updated index atomically via temp-file rename

```
Counting files... 24,821 files found
Indexing...
[██████████████████████████████] 100%  |  24,821 files  |  4.31s
  24,503 new  |  142 updated  |  38 removed  |  0 errors
Saving index... done (0.18s)

Index: 24,821 documents  |  187,432 unique terms
```

---

### `fileseek search <query>`

Search indexed files by content and filename.

```bash
fileseek search "redis caching"                   # keyword search
fileseek search '"spring boot"'                   # phrase search
fileseek search "sprng" --fuzzy                   # typo-tolerant
fileseek search "dock" --prefix                   # prefix / autocomplete
fileseek search "spring.*boot" --regex            # regex (token-level)
fileseek search "redis" --ext .java               # filter by extension
fileseek search "docker" --min-size 1MB           # filter by file size
fileseek search "spring" --modified-after 7d      # filter by recency
```

**Search flags:**

| Flag | Description |
|------|-------------|
| *(none)* | Keyword search — BM25 ranked, stop words removed |
| `--fuzzy` | Typo-tolerant — Levenshtein distance ≤ 2 |
| `--prefix` | Prefix / autocomplete matching |
| `--regex` | Token-level regular expression |
| `--ext <.ext>` | Filter by file extension, e.g. `.java` |
| `--min-size <n>` | Filter by minimum file size: `1MB`, `500KB`, `2GB` |
| `--modified-after <d>` | Filter by modification recency: `7d`, `24h` |

> **Phrase search:** Wrap in double quotes and escape for your shell:
> `fileseek search '"spring boot"'`
>
> **Regex:** Pattern matched against individual indexed tokens, not raw file
> content. `spring.*boot` matches a single token, not `spring` and `boot` as
> separate words. See [docs/architecture.md](docs/architecture.md) for details.

---

### `fileseek watch`

Monitor indexed directories for filesystem changes and update the index live.
Runs in the foreground — press `Ctrl+C` to stop cleanly.

```bash
fileseek watch

Loaded index — 24,821 documents
Watching: /home/user/projects
[10:23:45] Indexed:  RedisConfig.java
[10:24:02] Updated:  application.yml
[10:24:18] Removed:  OldService.java
[10:25:01] Directory added: /home/user/projects/new-module
```

> **Windows note:** WatchService may fall back to polling (1–10s latency) on
> some configurations. Correctness is not affected — only update latency.

---

### `fileseek remove <path>`

Remove a directory and all its documents from the index.

```bash
fileseek remove ~/Downloads
```

---

### `fileseek stats`

Display index statistics — document counts, unique terms, extension breakdown,
most common terms, and largest indexed files.

```bash
fileseek stats

FileSeek Index Statistics
=========================

Overview
  Documents             24,821
  Unique terms         187,432
  Total tokens       8,423,091
  Avg doc length           339 tokens

Index File
  Location    ~/.fileseek/index/fileseek.idx
  Size on disk              8.3 MB

Extension Breakdown
  .java          12,451  (50.2%)
  .md             4,832  (19.5%)
  .txt            3,201  (12.9%)
  .json           2,104   (8.5%)
  .xml            1,233   (5.0%)

Top 10 Terms by Document Frequency
  public               12,451 docs
  import               11,832 docs
  return                9,204 docs
  ...

Largest Files
  14.2 MB   /projects/data/large-dataset.json
   8.1 MB   /projects/src/generated.java
```

---

### `fileseek history`

Show recent search queries.

```bash
fileseek history             # last 20 searches
fileseek history -n 50       # last 50 searches
fileseek history --clear     # clear all history
```

---

### `fileseek config`

Display current configuration and index status.

```bash
fileseek config

Paths
  Config : ~/.fileseek/config.json
  Index  : ~/.fileseek/index/

Watched Directories
  /home/user/projects
  /home/user/documents

Rules
  Ignored dirs : [.git, node_modules, target, build, dist, .idea]
  Extensions   : [.txt, .md, .java, .json, .xml, .yml, .properties, .pdf]
  Max text size: 15 MB
  Max PDF size :  5 MB

Index Status
  Documents :  24,821
  Terms     : 187,432
  File size :    8.3 MB
```

---

### `fileseek reset`

Delete all configuration, index data, and search history.

```bash
fileseek reset          # prompts for confirmation
fileseek reset --yes    # skip confirmation prompt
```

---

## Global Flags

| Flag | Description |
|------|-------------|
| `--help`, `-h` | Show help for any command |
| `--version`, `-V` | Show version |
| `--verbose`, `-v` | Show query tokens, score breakdowns, index load time |

`--verbose` is inherited by all subcommands:

```bash
fileseek search "redis" --verbose

  [verbose] Index loaded in 42ms — 24,821 documents, 187,432 terms
  [verbose] Raw query   : "redis"
  [verbose] Mode        : keyword
  [verbose] Candidates  : 34 documents
  [verbose] After filters: 34 documents
  [verbose] Duration    : 8ms
```

---

## Shell Tab Completion

Tab completion is configured automatically by the install script:

```bash
fileseek sea<TAB>          →  fileseek search
fileseek search --<TAB>    →  --fuzzy  --prefix  --regex  --ext  --min-size  --modified-after
```

**Manual setup:**

```bash
fileseek generate-completion > ~/.fileseek/completion.sh
echo 'source ~/.fileseek/completion.sh' >> ~/.zshrc   # or ~/.bashrc
source ~/.zshrc
```

---

## Supported File Types

| Category | Extensions |
|----------|------------|
| Text | `.txt` `.md` `.java` `.json` `.xml` `.yml` `.yaml` `.properties` |
| PDF | `.pdf` *(text layer only — scanned image PDFs are not supported)* |

**Large file handling:** Files above the size threshold are indexed by filename
only. They appear in filename searches but content is not indexed.

| Type | Default threshold |
|------|-------------------|
| Text | 15 MB |
| PDF  | 5 MB  |

Thresholds are configurable in `~/.fileseek/config.json`.

---

## Configuration

`~/.fileseek/config.json`:

```json
{
  "watchedDirectories":  ["/home/user/projects"],
  "ignoredDirectories":  [".git", "node_modules", "target", "build", "dist", ".idea"],
  "supportedExtensions": [".txt", ".md", ".java", ".json", ".xml", ".yml", ".properties", ".pdf"],
  "maxTextFileSizeBytes": 15728640,
  "maxPdfFileSizeBytes":   5242880
}
```

**All FileSeek files:**

| Path | Purpose |
|------|---------|
| `~/.fileseek/config.json` | Configuration |
| `~/.fileseek/index/fileseek.idx` | Binary index |
| `~/.fileseek/index/fileseek.lock` | Process lock (present only during writes) |
| `~/.fileseek/history.txt` | Search history (tab-separated: timestamp, query) |
| `~/.fileseek/completion.sh` | Shell tab completion script |

---

## Search Ranking

Results are ranked using **BM25** — the industry standard used by Elasticsearch
and Lucene.

**Why not TF-IDF:** TF-IDF grows linearly with term frequency — a document
mentioning `redis` 100 times scores 100× higher than one mentioning it once,
which is not useful. BM25 saturates term frequency so additional occurrences
add diminishing score.

**Score components:**

| Component | Effect |
|-----------|--------|
| BM25 IDF | Rare terms score higher than common terms |
| BM25 TF | Saturates — prevents runaway frequency scores |
| Length normalization | Short documents score higher for the same frequency |
| Filename boost (×3) | Term in filename ranks above content-only match |
| Phrase boost (×2) | Confirmed phrase match ranks above loose match |
| Fuzzy penalty | Distance 1 → ×0.75 · Distance 2 → ×0.50 |
| Prefix coverage | `prefix.length / term.length` multiplier |

---

## Incremental Indexing

Every `fileseek add` run is incremental — only changed files are processed:

| File state | Action |
|------------|--------|
| New | Indexed |
| Unchanged (`lastModified` matches) | Skipped |
| Modified (`lastModified` changed) | Removed and re-indexed |
| Deleted | Removed from index |

A `fileseek.lock` file containing the owning process PID prevents concurrent
writes. Stale locks from crashed processes are automatically cleaned up via
`ProcessHandle`.

---

## Performance

| Operation | Time |
|-----------|------|
| Initial index — 25K files | ~4–6s |
| Incremental rescan | ~0.5s |
| Keyword search | < 15ms |
| Phrase search | < 20ms |
| Fuzzy search | < 50ms |
| Regex search | < 30ms |
| Index file size — 25K files | ~8 MB |

Parallel indexing uses one thread per CPU core. On an 8-core machine expect
roughly 2–3× speedup over single-threaded.

---

## Running Tests

```bash
mvn test
```

19 test files across 6 packages:

| Package | Test files |
|---------|------------|
| `util` | `TokenizerTest` · `PathUtilsTest` · `SearchHistoryTest` |
| `index` | `DocumentStoreTest` · `InvertedIndexTest` · `IndexManagerTest` |
| `scanner` | `TextParserTest` · `FileParserTest` · `DirectoryScannerTest` |
| `storage` | `IndexSerializerTest` |
| `search` | `QueryParserTest` · `TfIdfScorerTest` · `BM25ScorerTest` · `SearchEngineTest` · `FuzzySearchTest` · `PrefixSearchTest` · `RegexSearchTest` · `SnippetExtractorTest` |
| `config` | `AppConfigTest` |

---

## Project Structure

```
fileseek/
├── src/
│   ├── main/java/com/fileseek/
│   │   ├── cli/              Search, Add, Remove, Config, Reset, Watch, History, Stats
│   │   │   └── display/      ProgressBar, Spinner
│   │   ├── config/           AppConfig, ConfigManager, FirstRunSetup
│   │   ├── scanner/          DirectoryScanner, FileParser, TextParser, PdfParser,
│   │   │                     FilesystemWatcher, ScanResult
│   │   ├── index/            IndexManager, DocumentStore, InvertedIndex
│   │   ├── model/            FileMetadata, Posting, SearchResult, QueryOptions
│   │   ├── search/           SearchEngine, QueryParser, BM25Scorer, TfIdfScorer,
│   │   │                     FuzzySearch, PrefixSearch, RegexSearch, SnippetExtractor
│   │   ├── storage/          IndexSerializer, IndexDeserializer, CorruptionChecker,
│   │   │                     IndexLock
│   │   ├── util/             Tokenizer, StopWords, PathUtils, SearchHistory, AppContext
│   │   └── FileSeekApplication.java
│   │
│   └── main/resources/
│       ├── banner.txt
│       └── stopwords.txt
│
├── docs/
│   └── architecture.md
├── scripts/
│   └── install.sh
├── README.md
└── pom.xml
```

---

## Documentation

| File | Contents |
|------|----------|
| `README.md` | Installation, commands, configuration, performance |
| `docs/architecture.md` | Component design, data flow, binary format spec, threading model, design decisions |

---

## Known Limitations

**Regex is token-level.** `spring.*boot` matches a single indexed token — not
`spring` and `boot` as separate words in a sentence.

**PDF requires a text layer.** Scanned image PDFs produce no content.

**WatchService on Windows** may use polling (1–10s latency) on some configurations.

**Single-user index.** Not designed for multi-user shared network filesystems.

**No stemming.** `"running"` does not find documents containing only `"run"`.

**English stop words only.** Non-English corpora are indexed without stop word
filtering.

---

## License

MIT