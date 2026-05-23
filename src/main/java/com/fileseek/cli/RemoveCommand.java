package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.storage.IndexLock;
import com.fileseek.util.AppContext;
import com.fileseek.util.PathUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "remove",
        mixinStandardHelpOptions = true,
        description = "Remove a directory from the FileSeek index."
)
public class RemoveCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory path to remove.")
    private String path;

    @Override
    public Integer call() {
        String absolute;
        try {
            absolute = PathUtils.expand(path).toString();
        } catch (Exception e) {
            System.err.printf(
                    "[error] Invalid path: \"%s\".%n"
                            + "        Check the path and try again.%n", path);
            return 2;
        }

        AppConfig config = ConfigManager.load();
        boolean removed = config.removeWatchedDirectory(absolute);

        if (!removed) {
            System.err.printf(
                    "[error] \"%s\" is not in the index.%n"
                            + "        Run 'fileseek config' to see all indexed directories.%n",
                    absolute);
            return 1;
        }

        ConfigManager.save(config);

        IndexLock lock = new IndexLock();
        if (!lock.acquire()) return 1;

        try {
            IndexManager mgr = new IndexManager();
            mgr.load();

            int before = mgr.documentCount();

            mgr.getDocumentStore()
                    .getAllDocuments()
                    .stream()
                    .filter(m -> PathUtils.isUnder(
                            Path.of(m.getPath()),
                            Path.of(absolute)
                    ))
                    .map(m -> m.getPath())
                    .toList()
                    .forEach(mgr::removeDocument);
            int removed2 = before - mgr.documentCount();

            mgr.save();

            System.out.printf("Removed: %s%n", absolute);
            System.out.printf("         %,d documents removed from index.%n", removed2);

            if (AppContext.verbose) {
                System.out.printf("  [verbose] Index now contains %,d documents.%n",
                        mgr.documentCount());
            }
        } finally {
            lock.release();
        }

        return 0;
    }
}