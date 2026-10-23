package com.fraudshield.backend.batch;

import java.nio.file.Path;
import java.util.List;

public record BatchRunResult(int processed,int alerts,long durationMillis,Integer sampleMatched,List<String> sampleMismatches,
                             Path decisionsOutput,Path alertsOutput,Path fraudScoresOutput){
    public BatchRunResult{sampleMismatches=List.copyOf(sampleMismatches);}
}
