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

| Component        | Technology                    |
|------------------|-------------------------------|
| Language         | Java 17                       |
| CLI framework    | Picocli 4.7                   |
| PDF parsing      | Apache PDFBox 3.0             |
| Build tool       | Maven 3.8+                    |
| Index format     | Custom binary (GZIP, delta)   |
| Ranking          | BM25                          |

---

## Requirements

- Java 17 or higher
- Maven 3.8+ *(build only — not needed after installation)*

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
- Creates a wrapper script at `/usr/local/bin/fileseek` (system-wide)
  or `~/bin/fileseek` (user-only)
- Generates a shell completion script and sources it from your shell profile

**Manual install (no script):**

```bash
mvn package -DskipTests
java -jar target/fileseek.jar --help
```

**Override JVM memory:**

```bash
FILESEEK_OPTS="-Xmx2g" fileseek add ~/LargeDirectory
```

---

## Quick Start

```bash
# 1. Index a directory
fileseek add ~/Projects

# 2. Search
fileseek search "redis caching"

# 3. Watch for live changes
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

**What happens:**

1. Counts indexable files and shows a progress bar
2. Loads any existing index from disk
3. Scans the directory in parallel (one thread per CPU core)
4. Skips unchanged files, re-indexes modified ones, removes deleted ones
5. Saves the updated index atomically

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
fileseek search "spring" --modified-after 7d      # filter by age
fileseek search "redis" -v                        # verbose — shows scoring details
```

**Search flags:**

| Flag | Description |
|------|-------------|
| *(none)* | Keyword search — BM25 ranked, stop words removed |
| `--fuzzy` | Typo-tolerant — Levenshtein distance ≤ 2 |
| `--prefix` | Prefix / autocomplete matching |
| `--regex` | Token-level regular expression |
| `--ext <.ext>` | Filter results by file extension |
| `--min-size <size>` | Filter by minimum file size (`1MB`, `500KB`, `2GB`) |
| `--modified-after <dur>` | Filter by modification recency (`7d`, `24h`) |

> **Phrase search note:** Wrap your query in double quotes and escape them for
> your shell: `fileseek search '"spring boot"'`
>
> **Regex note:** Regex matches individual indexed tokens, not raw file content.
> `spring.*boot` matches a single token matching that pattern,
> not `spring` and `boot` as separate words across a sentence.

---

### `fileseek watch`

Monitor all indexed directories for filesystem changes and update the index
automatically. Runs in the foreground — press `Ctrl+C` to stop.

```bash
fileseek watch

Loaded index — 24,821 documents
Watching: /home/user/projects
[10:23:45] Indexed:  RedisConfig.java
[10:24:02] Updated:  application.yml
[10:24:18] Removed:  OldService.java
[10:25:01] Directory added: /home/user/projects/new-module
```

On `Ctrl+C`, the index is saved cleanly before exit.

> **Note:** On Linux and macOS, WatchService uses native inotify/kqueue events
> (low latency). On some Windows environments it may fall back to polling with
> higher latency. This does not affect correctness — only the delay between a
> file change and its appearance in the index.

---

### `fileseek remove <path>`

Remove a directory and all its documents from the index.

```bash
fileseek remove ~/Downloads
```

---

### `fileseek stats`

Display index statistics — document counts, term counts, largest files,
most common terms, and extension breakdown.

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
fileseek reset --yes    # skip confirmation
```

---

## Global Flags

| Flag | Description |
|------|-------------|
| `--help`, `-h` | Show help |
| `--version`, `-V` | Show version |
| `--verbose`, `-v` | Show internal details — query tokens, score breakdown, index load time |

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

After installation, tab completion is set up automatically:

```bash
fileseek sea<TAB>          →  fileseek search
fileseek search --<TAB>    →  --fuzzy  --prefix  --regex  --ext  --min-size  --modified-after
```

To set it up manually:

```bash
fileseek generate-completion > ~/.fileseek/completion.sh
echo 'source ~/.fileseek/completion.sh' >> ~/.zshrc   # or ~/.bashrc
source ~/.zshrc
```

---

## Supported File Types

| Category | Extensions |
|----------|------------|
| Text     | `.txt` `.md` `.java` `.json` `.xml` `.yml` `.yaml` `.properties` |
| PDF      | `.pdf` *(text layer only — scanned image PDFs are not supported)* |

**Large file handling:** Files above the configured size threshold are indexed
by filename only — they appear in filename searches but their content is not indexed.

| File type | Default threshold |
|-----------|-------------------|
| Text      | 15 MB             |
| PDF       | 5 MB              |

Thresholds are configurable in `~/.fileseek/config.json`.

---

## Configuration

All configuration lives in `~/.fileseek/config.json`:

```json
{
  "watchedDirectories": ["/home/user/projects"],
  "ignoredDirectories": [".git", "node_modules", "target", "build", "dist", ".idea"],
  "supportedExtensions": [".txt", ".md", ".java", ".json", ".xml", ".yml", ".properties", ".pdf"],
  "maxTextFileSizeBytes": 15728640,
  "maxPdfFileSizeBytes":   5242880
}
```

The index lives in `~/.fileseek/index/fileseek.idx`.

---

## Architecture

```
fileseek add ~/Projects
       │
       ▼
FileSeekApplication
  ├── ConfigManager.load()           read ~/.fileseek/config.json
  ├── IndexManager.load()            restore ~/.fileseek/index/fileseek.idx
  │     └── IndexDeserializer        GZIP binary → DocumentStore + InvertedIndex
  │
  └── DirectoryScanner.scan()
        │
        ├── Pass 1: removeDeletedDocuments()
        │     └── check each indexed path still exists on disk
        │
        └── Pass 2: parallel file indexing (ExecutorService, N=CPU cores)
              ├── FileParser          route by extension → TextParser / PdfParser
              ├── Tokenizer.tokenize()
              │     lowercase → split on non-alphanumeric → stop words → tokens
              └── IndexManager.indexDocument()
                    ├── DocumentStore.addDocument()   path → docId mapping
                    └── InvertedIndex.addPosting()    term → [(docId, [positions])]
                                                       positional, delta-encoded on disk
       │
       ▼
IndexManager.save()
  └── IndexSerializer
        ├── write to .tmp (atomic — crash-safe)
        ├── GZIP compress
        ├── delta-encode positions ([3,18,45] → [3,15,27])
        └── rename .tmp → fileseek.idx
```

```
fileseek search "redis caching"
       │
       ▼
SearchCommand
  └── SearchEngine.search(QueryOptions)
        ├── QueryParser.parse()       detect phrase / tokenize
        │
        ├── route():
        │     --regex   → RegexSearch    scan term set for pattern matches
        │     phrase    → PhraseSearch   intersect posting lists → positional check
        │     --fuzzy   → FuzzySearch    length filter → bounded Levenshtein
        │     --prefix  → PrefixSearch   prefix scan → coverage-ratio boost
        │     default   → keywordSearch  BM25 per term, filename boost ×3
        │
        ├── passesFilters()           --ext / --min-size / --modified-after
        ├── Collections.sort()        by score descending
        └── SnippetExtractor          re-read file → find match → extract context
```

---

## Indexing Details

### Inverted Index

Each indexed term maps to a posting list:

```
"redis" → [ (doc1, [3, 18, 45]), (doc2, [7]), (doc3, [0, 22]) ]
           │  └─ positions of "redis" in doc1
           └─ docId
```

Position data enables phrase search: to verify `"spring boot"` appears as
a phrase, the engine confirms a document has `spring` at position *N* and
`boot` at position *N+1*.

### Persistence Format

```
~/.fileseek/index/fileseek.idx
  [GZIP stream]
    [4B] magic  0x46534558
    [4B] version
    [4B] document count
      per document: docId, path, fileName, extension, size,
                    lastModified, indexedAt, tokenCount
    [4B] term count
      per term: term string, posting count
        per posting: docId, position count,
                     delta-encoded positions
```

Writes go to a `.tmp` file first, then renamed atomically — a crash during
write never corrupts the live index.

### Incremental Indexing

On every `fileseek add` run:

| File state | Action |
|------------|--------|
| New file | Indexed |
| Unchanged (`lastModified` matches) | Skipped |
| Modified (`lastModified` changed) | Old entry removed, re-indexed |
| Deleted | Removed from index |

### File Locking

A lock file at `~/.fileseek/index/fileseek.lock` prevents two FileSeek
processes from writing the index simultaneously. The lock stores the owning
process PID — stale locks from crashed processes are detected and removed
automatically.

---

## Search Ranking

Results are ranked using **BM25** — the industry standard used by Elasticsearch
and Lucene.

**Why BM25 over TF-IDF:**
TF-IDF grows linearly with term frequency — a document mentioning `redis` 100
times scores 100× higher than one mentioning it once. BM25 saturates term
frequency, so beyond a threshold, more occurrences add diminishing score.

**Score components:**

| Component | Effect |
|-----------|--------|
| BM25 IDF | Rare terms score higher than common terms |
| BM25 TF | Saturates — prevents runaway frequency scores |
| Length normalization | Short documents score higher for the same term frequency |
| Filename boost (×3) | Term in filename ranks above content-only match |
| Phrase boost (×2) | Confirmed phrase matches rank above loose keyword matches |
| Fuzzy distance penalty | Distance 1 → ×0.75, distance 2 → ×0.50 |
| Prefix coverage | `prefix.length / term.length` multiplier |

---

## Performance

Measured on a mid-range laptop (SSD, quad-core, 16 GB RAM):

| Operation | Time |
|-----------|------|
| Initial index — 25K files | ~4–6s |
| Incremental rescan — same directory | ~0.5s |
| Keyword search | < 15ms |
| Phrase search | < 20ms |
| Fuzzy search | < 50ms |
| Regex search | < 30ms |
| Index file size — 25K files | ~8 MB |

Parallel indexing uses one thread per CPU core — on an 8-core machine expect
roughly 2–3× speedup over single-threaded.

---

## Running Tests

```bash
mvn test
```

**Test coverage:**

| Package | Test file | Key scenarios |
|---------|-----------|---------------|
| `util` | `TokenizerTest` | All three modes, stop words, edge cases |
| `util` | `PathUtilsTest` | Cross-platform expansion, isUnder, parentOf |
| `util` | `SearchHistoryTest` | Append, read, ordering, isolation |
| `index` | `DocumentStoreTest` | CRUD, path lookup, restore, averageLength |
| `index` | `InvertedIndexTest` | Postings, positions, removal, concurrency |
| `index` | `IndexManagerTest` | Full pipeline, tokenCount, concurrent indexing |
| `scanner` | `TextParserTest` | UTF-8, ISO-8859-1 fallback, multiline |
| `scanner` | `FileParserTest` | Extension routing, unsupported types |
| `scanner` | `DirectoryScannerTest` | Incremental, parallel, ignored dirs, large files |
| `storage` | `IndexSerializerTest` | Roundtrip, delta encoding, tokenCount, corruption |
| `search` | `QueryParserTest` | Phrase detection, stop words, edge cases |
| `search` | `TfIdfScorerTest` | TF sublinearity, IDF rarity |
| `search` | `BM25ScorerTest` | Saturation, length normalization, fallback |
| `search` | `SearchEngineTest` | All modes, filters, ranking, BM25 saturation |
| `search` | `FuzzySearchTest` | Levenshtein unit tests, distance ranking |
| `search` | `PrefixSearchTest` | Term matching, coverage ranking, mode isolation |
| `search` | `RegexSearchTest` | Patterns, alternation, invalid regex, integration |
| `search` | `SnippetExtractorTest` | Context extraction, ellipsis, no newlines |
| `config` | `AppConfigTest` | Defaults, case-insensitive extensions, thresholds |

---

## Project Structure

```
fileseek/
├── src/
│   ├── main/java/com/fileseek/
│   │   ├── cli/              commands (Search, Add, Remove, Config, Reset,
│   │   │   │                           Watch, History, Stats)
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
├── scripts/
│   └── install.sh
├── docs/
│   └── architecture.md
├── README.md
└── pom.xml
```

---

## Known Limitations

**Regex is token-level.** `spring.*boot` matches a single indexed token matching
the pattern — it does not span multiple words. For cross-word regex, use
`--fuzzy` or `--prefix` instead.

**PDF requires a text layer.** Scanned PDFs (image-only) return empty content.
PDFBox extracts the text layer only.

**WatchService latency on Windows.** Java WatchService may use polling on some
Windows configurations, adding 1–10 seconds of latency before changes appear
in the index. Correctness is not affected.

**Index is single-user.** The file lock prevents concurrent writes but the index
is not designed for multi-user shared network filesystems.

---

## License

MIT