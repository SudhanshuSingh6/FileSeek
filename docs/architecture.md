# FileSeek — Architecture Reference

A technical deep-dive into component design, data structures, binary format,
threading model, and key design decisions.

---

## Table of Contents

1. [Package Structure and Layer Boundaries](#1-package-structure-and-layer-boundaries)
2. [Startup Flow](#2-startup-flow)
3. [Indexing Pipeline](#3-indexing-pipeline)
4. [Search Pipeline](#4-search-pipeline)
5. [Core Data Structures](#5-core-data-structures)
6. [Tokenization](#6-tokenization)
7. [BM25 Ranking](#7-bm25-ranking)
8. [Binary Persistence Format](#8-binary-persistence-format)
9. [Threading Model](#9-threading-model)
10. [Incremental Indexing](#10-incremental-indexing)
11. [Filesystem Watcher](#11-filesystem-watcher)
12. [File Locking](#12-file-locking)
13. [Cross-Platform Path Handling](#13-cross-platform-path-handling)
14. [CLI Architecture](#14-cli-architecture)
15. [Key Design Decisions](#15-key-design-decisions)
16. [Known Limitations and Fix Paths](#16-known-limitations-and-fix-paths)

---

## 1. Package Structure and Layer Boundaries

```
com.fileseek
├── cli/            Entry points — commands, display. Depends on everything below.
│   └── display/    ProgressBar, Spinner. No dependencies.
├── config/         AppConfig, ConfigManager, FirstRunSetup. Depends on util/.
├── scanner/        File traversal and parsing. Depends on index/, util/.
├── index/          IndexManager, DocumentStore, InvertedIndex. Depends on model/, storage/, util/.
├── model/          FileMetadata, Posting, SearchResult, QueryOptions. No dependencies.
├── search/         Search algorithms, scoring. Depends on index/, model/, util/.
├── storage/        Binary serialization. Depends on index/, model/, config/.
└── util/           Tokenizer, StopWords, PathUtils, SearchHistory, AppContext. No dependencies.
```

**Layer rule:** Lower packages must not import from higher packages.
`index/` must never import from `cli/`. `util/` must never import from anything.

The `AppContext` class in `util/` holds the global `verbose` flag. This avoids
the layering violation that would occur if `IndexManager` or `SearchEngine`
imported `FileSeekCommand` from `cli/`.

**`IndexManager` as the sole mediator:**
`DocumentStore` and `InvertedIndex` never reference each other.
`IndexManager` is the only class that coordinates them. This enables:
- Independent unit testing of each store
- Independent serialization of each store
- Clean document removal without coupling

---

## 2. Startup Flow

```
java -jar fileseek.jar <subcommand> [args]
         │
         ▼
FileSeekApplication.main()
  │
  ├── isFirstRun()? → no config.json exists
  │     YES → FirstRunSetup.run()
  │             ├── discover top folders in home directory
  │             ├── prompt folder selection
  │             ├── prompt indexing scope (quick / full)
  │             └── ConfigManager.save(AppConfig)
  │
  ├── else → validateConfig()
  │             └── warn about watched directories that no longer exist on disk
  │                 (non-fatal — removable drives may be temporarily disconnected)
  │
  └── CommandLine(new FileSeekCommand()).execute(args)
        └── Picocli routes to matching subcommand
              └── subcommand.call() → returns Integer exit code
```

**Exit code conventions:**

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Operational failure — no results, operation cancelled |
| `2` | Usage error — bad path, missing index, invalid argument |

**Bypass commands:** `--help` and `--version` skip first-run setup entirely.

---

## 3. Indexing Pipeline

```
fileseek add ~/Projects
         │
         ▼
AddCommand.call()
  │
  ├── PathUtils.expand(path)          tilde expansion, normalize, toAbsolutePath
  ├── ConfigManager.load()            read config.json
  ├── config.addWatchedDirectory()    update config
  ├── ConfigManager.save()            persist config
  │
  ├── IndexLock.acquire()             CREATE_NEW atomic lock, store PID
  │
  ├── DirectoryScanner.countIndexableFiles()   pass 1: count for progress bar
  │
  ├── IndexManager.load()             deserialize fileseek.idx → DocumentStore + InvertedIndex
  │
  └── IndexManager.indexDirectory()
        │
        └── DirectoryScanner.scan()
              │
              ├── Pass 1: removeDeletedDocuments(root)
              │     └── for each indexed doc under root:
              │           Files.exists() → false → IndexManager.removeDocument()
              │
              └── Pass 2: collectFiles() [single-threaded walk]
                    │   Files.walkFileTree(root, Set.of(), ...)
                    │     preVisitDirectory: skip ignored + hidden dirs
                    │     visitFile:
                    │       isSupported()?      no  → skip
                    │       isIndexed(path)?    yes → compare lastModified
                    │                                  match    → skip
                    │                                  mismatch → removeDocument, mark UPDATED
                    │                           no  → mark NEW
                    │
                    └── indexInParallel(fileEntries) [ExecutorService, N=CPUs]
                          per file (worker thread):
                            ├── isLargeFile(ext, size)?
                            │     YES → tokenizeFilename only  → indexDocument (metadata-only)
                            │     NO  → FileParser.parse()
                            │             ├── .pdf  → PdfParser  (PDFBox Loader.loadPDF)
                            │             └── text  → TextParser (UTF-8, ISO-8859-1 fallback)
                            │           Tokenizer.tokenize(content)
                            │           + Tokenizer.tokenizeFilename(filename)
                            │           → IndexManager.indexDocument(metadata, tokens)
                            │               ├── metadata.setTokenCount(tokens.size())
                            │               ├── DocumentStore.addDocument(metadata)
                            │               │     AtomicInteger.getAndIncrement() → docId
                            │               └── InvertedIndex.addPosting(term, docId, position)
                            │                     synchronized(postingList) { append }
                            └── ScanResult.increment*() [AtomicInteger — thread-safe]
```

---

## 4. Search Pipeline

```
fileseek search "redis caching" --ext .java
         │
         ▼
SearchCommand.call()
  │
  ├── IndexManager.load()
  ├── buildOptions() → QueryOptions (query, flags, parsed filters)
  │
  └── SearchEngine.search(QueryOptions)
        │
        ├── QueryParser.parse(rawQuery)
        │     starts+ends with " ?  → phrase mode (tokenizePhrase — keeps stop words)
        │     else                  → keyword mode (tokenize — removes stop words)
        │
        ├── route(query, options) → Map<Integer, Double> scores
        │     options.isRegex()   → RegexSearch.search(rawQuery)
        │     query.isPhrase()    → phraseSearch(terms)
        │     options.isFuzzy()   → FuzzySearch.search(terms)
        │     options.isPrefix()  → PrefixSearch.search(terms)
        │     default             → keywordSearch(terms)
        │
        ├── build SearchResult list from scores + DocumentStore
        │
        ├── passesFilters(meta, options)
        │     --ext             meta.extension == filterExt
        │     --min-size        meta.sizeBytes >= minSizeBytes
        │     --modified-after  meta.lastModified >= epochCutoff
        │
        ├── Collections.sort(results)    Comparable<SearchResult> → score desc
        │
        └── SnippetExtractor.extract(meta, terms)  [top 10 results only]
              re-read file → find first term occurrence → 100-char context window
              → ANSI highlight matched terms
```

### Search Strategy Detail

**Keyword search:**
```
for each term in query:
    postings = InvertedIndex.getPostings(term)           O(1)
    df       = postings.size()
    idf      = log((N - df + 0.5) / (df + 0.5) + 1)
    for each posting (docId, positions):
        tf        = freq × (k1+1) / (freq + k1 × (1 - b + b × docLen/avgLen))
        termScore = idf × tf
        if fileName.contains(term): termScore × 3.0      filename boost
        scores[docId] += termScore
```

**Phrase search:**
```
Step 1 — candidate filtering (cheap):
    candidates = ∩ {docIds containing term_i for all terms}

Step 2 — positional verification (per candidate):
    positionSets[i] = HashSet(positions of term_i in this doc)
    for startPos in positionSets[0]:
        match = true
        for i in 1..terms.length:
            if startPos+i ∉ positionSets[i]: match = false; break
        if match: return true
```

**Fuzzy search:**
```
for each queryTerm:
    for each indexTerm:
        if |len(indexTerm) - len(queryTerm)| > 2: skip   length pre-filter
        dist = boundedLevenshtein(queryTerm, indexTerm, maxDist=2)
        if dist <= 2:
            multiplier = [1.0, 0.75, 0.50][dist]
            score += BM25score × multiplier
```

**Prefix search:**
```
for each prefix in queryTerms:
    matches = {term ∈ index | term.startsWith(prefix)}
    for each match:
        coverageBoost = prefix.length / match.length
        score += BM25score × coverageBoost
```

**Regex search:**
```
pattern = Pattern.compile(rawQuery, CASE_INSENSITIVE)
for each term in InvertedIndex.getAllTerms():    O(T)
    if pattern.matches(term):
        score += BM25score for all postings of this term
```

---

## 5. Core Data Structures

### DocumentStore

```
ConcurrentHashMap<Integer, FileMetadata>  store      docId → metadata
ConcurrentHashMap<String,  Integer>       pathIndex  path  → docId
AtomicInteger                             nextDocId  monotonically increasing
```

`addDocument()` is effectively atomic: `getAndIncrement()` for docId is
compare-and-swap (lock-free). Both map puts are on `ConcurrentHashMap` (safe
under concurrent access). No explicit locking needed.

`FileMetadata` fields:

| Field | Type | Description |
|-------|------|-------------|
| `docId` | int | Assigned by `DocumentStore` |
| `path` | String | Absolute, normalized OS path |
| `fileName` | String | Last component of path |
| `extension` | String | Lowercase, including dot |
| `sizeBytes` | long | File size at index time |
| `lastModified` | long | Epoch millis at index time |
| `indexedAt` | long | When this entry was created |
| `tokenCount` | int | Token count for BM25 length normalization |

### InvertedIndex

```
ConcurrentHashMap<String, List<Posting>>  index
```

Each `List<Posting>` is a `Collections.synchronizedList(new ArrayList<>())`.

`addPosting(term, docId, position)`:
```
postings = index.computeIfAbsent(term, k → synchronizedList(new ArrayList<>()))
synchronized(postings):
    find existing Posting for docId → addPosition(position)
    OR create new Posting(docId) → addPosition(position) → postings.add()
```

`Posting` fields:

| Field | Type | Description |
|-------|------|-------------|
| `docId` | int | References `DocumentStore` |
| `positions` | List\<Integer\> | Absolute token positions in document |

`frequency()` is `positions.size()` — how many times the term appears.

### QueryOptions (Builder Pattern)

```java
QueryOptions.builder("redis caching")
    .fuzzy(true)
    .filterExt(".java")
    .modifiedAfterEpoch(cutoff)
    .build()
```

All fields are set at construction — `QueryOptions` is immutable. The builder
pattern prevents an 8-parameter constructor and makes optional fields
self-documenting at the call site.

---

## 6. Tokenization

Four-step pipeline. **Must be applied identically at index time and query time.**
Any divergence causes silent search failures.

```
Input text
    │
    ▼  Step 1: lowercase
    │  "Spring BOOT" → "spring boot"
    │
    ▼  Step 2: split on [^a-z0-9]+
    │  "hello-world_test" → ["hello", "world", "test"]
    │
    ▼  Step 3: remove empty tokens
    │  Artefacts from consecutive delimiters
    │
    ▼  Step 4: remove stop words  (SKIPPED in phrase mode)
    │  HashSet<String> from stopwords.txt — O(1) lookup
    │
    ▼
Token list
```

**Three modes:**

| Mode | Method | Stop words | Use case |
|------|--------|------------|----------|
| Standard | `tokenize()` | Removed | Content indexing, keyword queries |
| Phrase | `tokenizePhrase()` | Kept | Phrase indexing and phrase queries |
| Filename | `tokenizeFilename()` | Kept | Filename indexing (extension stripped first) |

**Why phrase mode keeps stop words:**
Searching `"lord of the rings"` in standard mode produces `["lord", "rings"]`.
Phrase search checks position N for "lord" and N+1 for "rings" — but "of" and
"the" were stripped at index time, so "rings" lands at position 1 after "lord"
at position 0. This coincidentally works for many cases.

However, querying `"of the"` (two stop words) produces an empty term list and
returns no results regardless of document content. This is a documented limitation.
The complete fix requires indexing stop words with positions but excluding them
from single-term lookups — a more complex posting format.

---

## 7. BM25 Ranking

**Formula:**
```
score(q, d) = Σ  IDF(t) × TF(t, d)
             t∈q

IDF(t) = log((N - df(t) + 0.5) / (df(t) + 0.5) + 1)

TF(t, d) = freq(t,d) × (k1 + 1)
           ───────────────────────────────────────────
           freq(t,d) + k1 × (1 - b + b × |d| / avgdl)
```

**Parameters (standard defaults):**

| Parameter | Value | Effect |
|-----------|-------|--------|
| `k1` | 1.5 | TF saturation rate. Range 1.2–2.0. |
| `b` | 0.75 | Length normalization. 0=none, 1=full. |

**BM25 vs TF-IDF:**
TF-IDF: score grows linearly with `freq` — 100 occurrences → 100× score vs 1 occurrence.
BM25: score saturates — 100 occurrences → ~3× score vs 1 occurrence at k1=1.5.

**Score multipliers applied after BM25:**

| Condition | Multiplier |
|-----------|-----------|
| Term in filename | ×3.0 |
| Confirmed phrase match | ×2.0 |
| Fuzzy distance = 1 | ×0.75 |
| Fuzzy distance = 2 | ×0.50 |
| Prefix coverage | `prefix.len / term.len` |

**Average document length:**
Stored per-document as `tokenCount` in `FileMetadata`. `BM25Scorer` calls
`DocumentStore.averageDocumentLength()` once per search and caches the result
for the duration of the request. `invalidateCache()` is available if the store
changes mid-request (not currently used but available for future use).

---

## 8. Binary Persistence Format

### File Layout

```
~/.fileseek/index/fileseek.idx
────────────────────────────────────────────────────────
[GZIP stream]
  [4 bytes]  magic   = 0x46534558  ("FSEX")
  [4 bytes]  version = 2

  ── Document Store ──────────────────────────────────
  [4 bytes]  document count

  per document:
    [4 bytes]  docId
    [4 bytes]  path length (bytes)
    [N bytes]  path (UTF-8)
    [4 bytes]  fileName length
    [N bytes]  fileName (UTF-8)
    [4 bytes]  extension length
    [N bytes]  extension (UTF-8)
    [8 bytes]  sizeBytes (long)
    [8 bytes]  lastModified (long, epoch millis)
    [8 bytes]  indexedAt (long, epoch millis)
    [4 bytes]  tokenCount (int)

  ── Inverted Index ──────────────────────────────────
  [4 bytes]  term count

  per term:
    [4 bytes]  term length (bytes)
    [N bytes]  term (UTF-8)
    [4 bytes]  posting count

    per posting:
      [4 bytes]  docId
      [4 bytes]  position count
      [4 bytes]  delta₁      (= position₁)
      [4 bytes]  delta₂      (= position₂ - position₁)
      [4 bytes]  delta₃      (= position₃ - position₂)
      ...
────────────────────────────────────────────────────────
```

### Design Choices

**Why custom binary, not JSON:**
JSON serialization of a 187,000-term index with millions of position entries
is hundreds of megabytes and takes seconds to parse. Binary with
`DataOutputStream`/`DataInputStream` is compact and parsed at memory speed.

**Why not Java's `ObjectOutputStream`:**
Slow, bloated output, brittle across JVM versions. A field rename or addition
breaks deserialization of existing files without explicit migration logic.
`DataOutputStream` gives complete schema control.

**Why not Protocol Buffers/Avro:**
Adds code generation and an external dependency. The custom format is simpler
to understand, debug, and evolve at this scale.

**String encoding:**
`DataOutputStream.writeUTF()` has a 65,535-byte limit per string. Deep
directory paths or long filenames could exceed this. FileSeek uses a custom
4-byte-length + raw-bytes encoding that handles any length.

**Delta encoding:**
Positions within a document are monotonically increasing. Storing deltas
(differences between consecutive positions) instead of absolute values ensures
most position deltas are small integers (1–50 for typical documents). Small
integers compress significantly better under GZIP.

Serialization: `[3, 18, 45]` → write `[3, 15, 27]`
Deserialization: read `[3, 15, 27]` → accumulate `[3, 3+15=18, 18+27=45]`

**GZIP wrapping:**
Provides compression (60–80% reduction for typical indexes) and a built-in
CRC32 checksum. Any corrupted byte causes `GZIPInputStream` to throw
`IOException` on read — corruption detection at no extra cost.

**Atomic writes:**
```
IndexSerializer.serialize():
  1. Write everything to fileseek.idx.tmp
  2. Files.move(tmp, idx, ATOMIC_MOVE, REPLACE_EXISTING)
```
On POSIX systems, rename within the same filesystem is atomic — a single
directory-entry update. A crash mid-write leaves `.tmp` incomplete but the
live `.idx` untouched. Without this, a crash mid-write corrupts the index.

**Version header:**
The 8-byte header (magic + version) serves three purposes:
1. File type identification — immediately distinguishes FileSeek indexes from other binary files
2. Version enforcement — version mismatch triggers a clear rebuild message instead of silent wrong data
3. Debugging — `xxd fileseek.idx | head -1` immediately shows whether the file is valid

**Version history:**

| Version | Change |
|---------|--------|
| 1 | Initial format |
| 2 | Added `tokenCount` field per document (required for BM25) |

### Corruption Recovery

`CorruptionChecker.isCorrupted(indexFile)`:
1. Open file as `GZIPInputStream` — fails immediately on bad CRC
2. Read 4 bytes — compare against magic `0x46534558`
3. Read 4 bytes — compare against current version

If any check fails, `IndexManager.load()` deletes the corrupted file, clears
the in-memory index, and prints an actionable message. The tool is immediately
usable with an empty index.

---

## 9. Threading Model

### Indexing Phase

```
Main thread                     Worker threads (N = availableProcessors())
──────────────                  ──────────────────────────────────────────
walk directory tree  ──files──► parse file content         (I/O + CPU)
collect FileEntry list          tokenize content            (CPU)
                                IndexManager.indexDocument  (synchronized)
                                  └── DocumentStore.addDocument    (CAS)
                                  └── InvertedIndex.addPosting     (synchronized per list)
                                ScanResult.increment*               (AtomicInteger)
```

**Thread safety mechanisms:**

| Operation | Mechanism | Reason |
|-----------|-----------|--------|
| `docId` assignment | `AtomicInteger.getAndIncrement()` | Lock-free compare-and-swap |
| `store.put(docId, meta)` | `ConcurrentHashMap` | Concurrent bucket locking |
| `pathIndex.put(path, docId)` | `ConcurrentHashMap` | Concurrent bucket locking |
| `index.computeIfAbsent(term, …)` | `ConcurrentHashMap` | Atomic create-if-absent |
| Posting list mutation | `synchronized(postingList)` | Fine-grained list lock |
| `ScanResult` counters | `AtomicInteger` | Lock-free increment |

Fine-grained locking on posting lists (rather than on `this` or on the entire
`ConcurrentHashMap`) ensures two threads working on different terms never
block each other. Only two threads writing to the same term's posting list
serialize — rare in practice for a large vocabulary.

### WatchService Thread

`fileseek watch` blocks on `watchService.take()` on the main thread.
There is no secondary thread — the watch loop is the main thread's work.

The shutdown hook registered via `Runtime.getRuntime().addShutdownHook()` runs
on a JVM-managed thread when `Ctrl+C` (SIGINT) is received. It stops the
watcher, saves the index, and releases the lock before the JVM exits.

### Spinner Thread

`Spinner` runs on a daemon thread. Daemon threads are killed automatically
when all non-daemon threads exit — the spinner never prevents JVM shutdown.
`stop()` sets `running = false` and calls `thread.join(timeoutMs)` to wait
for the thread to notice the flag and exit cleanly before clearing the line.

---

## 10. Incremental Indexing

### Change Detection

```
For each file encountered during walk:

  1. isSupported(file)?          no  → skip
  2. isIndexed(absolutePath)?    no  → index as new file
                                 yes → compare lastModified:
     file.lastModified <= stored.lastModified → unchanged → skip
     file.lastModified >  stored.lastModified → modified  → removeDocument + re-index
```

`lastModified` is retrieved from `BasicFileAttributes` provided by
`Files.walkFileTree` — a free read from the directory entry without opening
the file. The comparison is a single integer operation per file.

### Deleted File Cleanup

Runs before the parallel indexing pass:

```java
List<String> toRemove = documentStore.getAllDocuments().stream()
    .filter(meta -> PathUtils.isUnder(Path.of(meta.getPath()), normalizedRoot))
    .filter(meta -> !Files.exists(Path.of(meta.getPath())))
    .map(FileMetadata::getPath)
    .collect(Collectors.toList());

toRemove.forEach(indexManager::removeDocument);
```

Paths are collected before removal to avoid `ConcurrentModificationException`
on the underlying map. `PathUtils.isUnder()` uses `Path.startsWith()` (not
`String.startsWith()`) for OS-correct path comparison.

### Document Removal

```
IndexManager.removeDocument(path):
  1. pathIndex.get(path) → docId        O(1)
  2. store.remove(docId)                O(1)
  3. pathIndex.remove(path)             O(1)
  4. for each term in index:            O(T × avg_postings)
       postingList.removeIf(p → p.docId() == docId)
       if postingList.isEmpty(): index.remove(term)
```

Step 4 is O(T) — iterates all terms. For a 187,000-term index this takes
a few milliseconds. Production systems use a delete bitset applied lazily
at query time (Lucene's approach) to avoid this cost. For FileSeek's scale,
eager removal is simpler and correct.

---

## 11. Filesystem Watcher

### Recursive Registration Problem

`WatchService` watches one directory at a time. Registering `~/Projects`
delivers events for files directly inside `~/Projects` but not inside
`~/Projects/src/main/java`. FileSeek walks the entire tree at startup and
registers every subdirectory individually.

```
WatchKey → Path   (keyToDir map)
~/Projects           → key_1
~/Projects/src       → key_2
~/Projects/src/main  → key_3
...
```

When a new directory is created, the `ENTRY_CREATE` event is received for its
parent's `WatchKey`. FileSeek detects it's a directory and registers it:

```java
if (kind == ENTRY_CREATE && Files.isDirectory(file)) {
    registerDirectory(file);
}
```

This ensures new subdirectories (created by `git checkout`, `mkdir`, etc.)
are watched immediately.

### Event Processing Loop

```
watchService.take()          blocks until any event arrives
key.pollEvents()             drain all events for this WatchKey (batch)
  for each event:
    resolve: dir.resolve(event.context()) → full path
    ENTRY_CREATE → if dir: register; else: reindexFile()
    ENTRY_MODIFY → reindexFile()
    ENTRY_DELETE → removeDocument()
    OVERFLOW     → ignore (events dropped; next manual add catches up)
if indexChanged: indexManager.save()     save once per batch
key.reset()      must be called or no further events delivered
  returns false → directory deleted → remove from keyToDir
```

**Save batching:** The index is saved once after processing all events in a
single `WatchKey`'s batch, not after each individual event. If 10 files are
modified in a `git checkout`, the index is saved once — not 10 times.

---

## 12. File Locking

```
acquire():
  Files.writeString(LOCK_FILE, pid, CREATE_NEW)
    SUCCESS → lock acquired
    FileAlreadyExistsException → read stored PID
      ProcessHandle.of(storedPid).isPresent()?
        true  → live process → print error, return false
        false → stale lock   → delete file, retry acquire()

release():
  Files.deleteIfExists(LOCK_FILE)
```

`CREATE_NEW` maps to `open(O_CREAT | O_EXCL)` at the OS level — atomic.
No window between "check if exists" and "create" where another process can
sneak in.

The lock file stores the PID as a plain string so `ProcessHandle.of(pid)` can
verify liveness. If the owning process is gone (crashed, killed), the lock is
stale and cleaned up automatically. This prevents the common "crash leaves
stale lock, tool refuses to run" failure mode.

**Lock scope:** Only `fileseek add`, `fileseek remove`, and `fileseek watch`
acquire the lock — commands that write the index. `fileseek search`,
`fileseek stats`, `fileseek history`, and `fileseek config` read without locking.
Two concurrent search commands are safe: `IndexDeserializer` reads the file
fully before using any data, and the writer uses atomic rename, so readers
see either the complete old file or the complete new file, never a partial state.

---

## 13. Cross-Platform Path Handling

### The Problem with Strings

On Windows, paths use `\` as separator: `C:\Users\user\projects\file.txt`.
On Unix, paths use `/`: `/home/user/projects/file.txt`.

`String.startsWith()` does literal comparison. A path stored as
`C:\Users\user\projects\file.txt` will not match a prefix of
`C:/Users/user/projects` even though they refer to the same location.

### The Fix: Path Objects Throughout

```java
// Wrong — breaks on Windows
meta.getPath().startsWith("/home/user/projects")

// Correct — OS-aware
PathUtils.isUnder(Path.of(meta.getPath()), rootPath)
```

`Path.startsWith(Path)` compares path components, not raw strings.
`Path.of()` normalizes separators for the current OS. Trailing slashes,
double separators, and mixed separators are all handled correctly.

### PathUtils.expand()

```java
public static Path expand(String rawPath) {
    if (rawPath == null || rawPath.isBlank())
        throw new IllegalArgumentException("Path must not be blank");
    String expanded = rawPath.startsWith("~")
        ? System.getProperty("user.home") + rawPath.substring(1)
        : rawPath;
    return Path.of(expanded).toAbsolutePath().normalize();
}
```

`.toAbsolutePath()` converts relative paths using the JVM working directory.
`.normalize()` resolves `..` and `.` components so paths stored in the index
are always in canonical form — essential for the string-equality comparison
in `DocumentStore.containsPath()`.

### FileMetadata.getFolderPath()

```java
// Before (broken on Windows):
int sep = path.lastIndexOf('/');

// After (correct):
Path parent = Path.of(path).getParent();
return (parent != null) ? parent.toString() : path;
```

`Path.getParent()` handles all separator conventions, trailing separators,
network paths (`\\server\share`), and root paths (no parent).

---

## 14. CLI Architecture

### Command Registration

```java
@Command(subcommands = {
    SearchCommand.class,
    AddCommand.class,
    RemoveCommand.class,
    ConfigCommand.class,
    ResetCommand.class,
    WatchCommand.class,
    HistoryCommand.class,
    StatsCommand.class,
    picocli.AutoComplete.GenerateCompletion.class,
    CommandLine.HelpCommand.class
})
```

`picocli.AutoComplete.GenerateCompletion` introspects the compiled annotation
metadata to generate a bash/zsh completion script. This is why the Picocli
annotation processor (`picocli-codegen`) must be configured in `pom.xml` —
it generates the metadata that `GenerateCompletion` reads.

### Annotation Processing

`picocli-codegen` runs at compile time (not runtime). It reads every
`@Command`, `@Option`, and `@Parameters` annotation and generates:
- Reflection config for GraalVM native images
- Shell completion metadata
- Compile-time option type validation

Without it, Picocli falls back to runtime reflection. Shell completion still
works but GraalVM native image support requires additional manual configuration.

### Verbose Flag Propagation

```java
// FileSeekCommand — declared with ScopeType.INHERIT
@Option(names = {"-v", "--verbose"}, scope = CommandLine.ScopeType.INHERIT)
public boolean verboseOpt = false;

@Override
public void run() {
    AppContext.verbose = verboseOpt;   // push to global context before subcommand runs
    // ...
}
```

`ScopeType.INHERIT` makes the option available on all subcommands.
`AppContext.verbose` (in `util/`) is the global read point — lower packages
read it without importing from `cli/`.

### Maven Shade Plugin

Two critical transformers:

**`ManifestResourceTransformer`:** Sets `Main-Class` in `MANIFEST.MF` so
`java -jar fileseek.jar` works without specifying the class name.

**`ServicesResourceTransformer`:** PDFBox uses `ServiceLoader` to discover
font providers and image readers registered in `META-INF/services/` files.
Multiple jars have entries in the same service files. Without this transformer,
shading overwrites them — only one provider survives. With it, all entries
are merged — all providers load correctly. Without this, PDFBox silently
fails to parse certain PDF fonts.

---

## 15. Key Design Decisions

### Custom Inverted Index vs Lucene

Using Lucene would reduce the project to library integration. The interesting
engineering — inverted index construction, positional posting lists, BM25
implementation, binary serialization, delta encoding, thread safety — would
disappear. The custom implementation makes every data structure decision
deliberate and explainable.

**Scalability trade-off:** This implementation handles ~100K documents on a
single machine. Lucene handles billions. For a personal developer tool, the
simpler implementation is correct.

### In-Memory Index vs Memory-Mapped Files

The entire index loads into Java heap on startup. For 25,000 files with 187,000
unique terms, this is ~50–100 MB of heap — acceptable with the default 512 MB
wrapper flags.

**The alternative (memory-mapped files):** `MappedByteBuffer` maps the index
file into virtual memory. The OS loads pages on demand — only accessed posting
lists use physical RAM. Requires a fundamentally different on-disk layout
(term → byte offset index for random access). This is how Lucene handles
billion-document indexes on 4 GB machines.

**Why not done here:** At the target scale, the in-memory approach is faster
(sequential load once vs. random page faults per query) and simpler to implement.

### BM25 over TF-IDF

TF-IDF is simpler but has a saturation problem — score grows linearly with
term frequency. BM25 saturates TF and normalizes for document length.
Both are one formula. BM25 is the industry standard. The implementation
cost was one additional `tokenCount` field and a version bump. Worth it.

### Version Bump on Format Change

When `tokenCount` was added (for BM25 document length normalization), the
binary format version bumped from 1 to 2. Old indexes are unreadable and the
user must rebuild. The alternative (field-tagged format like Protocol Buffers)
allows schema evolution without rebuild but adds complexity.

**Trade-off accepted:** Rebuilds take 4–6 seconds. Format changes happen once
or twice in the project's lifetime. The simplicity of the positional binary
format outweighs the rare rebuild cost.

### Phrase Search and Stop Words

Stop words are removed from the inverted index (never stored). Phrase search
must reconstruct consecutive positions from the token stream that was indexed
with stop words removed. For most phrases this works correctly because positions
jump when stop words are skipped, preserving relative adjacency for non-stop
words. Phrases that are entirely stop words (`"of the"`) return empty results.

The complete fix (index stop word positions, exclude from single-term lookup)
requires a more complex posting format. Documented limitation; not implemented.

---

## 16. Known Limitations and Fix Paths

| Limitation | Root cause | Fix |
|------------|------------|-----|
| Regex is token-level | Index stores tokens, not raw text | Dual mode: token-level (fast) + file-reread for top-N (accurate) |
| PDF needs text layer | PDFBox extracts text, not pixels | Integrate Tesseract OCR via `tess4j` |
| Stop words break some phrase queries | Stop words not indexed | Index with positions, exclude from single-term queries |
| No camelCase splitting | Tokenizer splits on non-alphanumeric only | Insert spaces at case transitions before lowercasing |
| No stemming | Not implemented | Integrate Snowball/Porter stemmer from `lucene-analyzers` |
| English stop words only | Hardcoded list | Per-language stop word files + language detection |
| Fuzzy O(T) complexity | Full index scan | Trigram index for candidate pre-filtering |
| Snippets re-read files | Raw text not stored in index | Store first N bytes per document in index |
| In-memory index | Random-access posting lookup pattern | Memory-mapped files with on-disk B-tree |
| WatchService polling on Windows | OS API limitation | JNA bindings to `ReadDirectoryChangesW` |
| Version → full rebuild | Fixed-position binary format | Field-tagged format (Protocol Buffers-style) |
| Single-user | Local file, no server | Search daemon + client CLI over socket |
| No proximity search | Phrase requires strict adjacency | `--near N` flag: `startPos + i ± slack` |
| BM25 params untuned | No annotated test collection | Grid-search k1/b with relevance-judged query set |
| No persistent result cache | Cache invalidation complexity | LRU in-memory cache cleared on index write |