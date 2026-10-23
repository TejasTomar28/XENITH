package com.fraudshield.backend.dataset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImportSummary {
    private final Map<String, FileCount> files = new LinkedHashMap<>();

    void record(String file, int inserted, int skipped) {
        files.put(file, new FileCount(inserted, skipped, 0));
    }

    public Map<String, FileCount> files() { return Collections.unmodifiableMap(files); }
    public int inserted() { return files.values().stream().mapToInt(FileCount::inserted).sum(); }
    public int skipped() { return files.values().stream().mapToInt(FileCount::skipped).sum(); }
    public int rejected() { return files.values().stream().mapToInt(FileCount::rejected).sum(); }

    public record FileCount(int inserted, int skipped, int rejected) {}
}
