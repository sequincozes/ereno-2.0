package br.ufu.facom.ereno.api;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * What a client (the ERENO UI) sends to POST /api/runs: everything needed to
 * generate one dataset, benign traffic first and the attack dataset on top.
 * Field names follow the core's action configs so the planner can copy them
 * across without renaming.
 */
public class RunSpec {

    public String name;
    /** Required: every run is reproducible from its spec. */
    public Long randomSeed;
    public Output output = new Output();
    public Benign benign = new Benign();
    public SetupIED setupIED = new SetupIED();
    public GooseFlow gooseFlow = new GooseFlow();
    public DatasetStructure datasetStructure = new DatasetStructure();
    public List<Attack> attacks = new ArrayList<>();

    public static class Output {
        /** "csv" or "arff". */
        public String format = "csv";
    }

    public static class Benign {
        public Integer numberOfMessages;
        public Integer faultProbability;
        /** DECIMATED or UPSTREAM_FAITHFUL. */
        public String svStrategy = "DECIMATED";
        public Integer svSamplesPerGoose = 80;
        /** Folder name under electrical-sources/, e.g. "res10". */
        public String electricalSources = "res10";
    }

    public static class SetupIED {
        public String iedName;
        public String gocbRef;
        public String datSet;
        public String minTime;
        public String maxTime;
        public String timestamp;
        public String stNum;
        public String sqNum;
    }

    public static class GooseFlow {
        public String goID;
        public String ethSrc;
        public String ethDst;
        public String ethType;
        public String gooseAppid;
        public String TPID;
        public boolean ndsCom;
        public boolean test;
        public boolean cbstatus;
    }

    public static class DatasetStructure {
        public Integer messagesPerSegment;
        public Integer benignMessagesPerSegment = 0;
        public boolean includeBenignSegment = true;
        public boolean shuffleSegments = false;
        public boolean binaryClassification = false;
    }

    public static class Attack {
        /** The full attack config, as in config/attacks/*.json (must carry attackType). */
        public JsonObject config;
    }
}
