package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.IOException;
import java.util.PriorityQueue;
import java.util.logging.Logger;

import com.google.gson.Gson;

import br.ufu.facom.ereno.SubstationNetwork;
import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.benign.uc00.devices.MergingUnit;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.ARFFWritter;
import br.ufu.facom.ereno.dataExtractors.CSVWritter;
import br.ufu.facom.ereno.messages.EthernetFrame;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.messages.Sv;
import br.ufu.facom.ereno.tracking.ExperimentTracker;
import br.ufu.facom.ereno.util.BenignDataManager;

/**
 * Action runner for creating benign datasets.
 */
public class CreateBenignAction {

    private static final Logger LOGGER = Logger.getLogger(CreateBenignAction.class.getName());

    public static class Config {
        public String action;
        public String description;
        public OutputConfig output;
        public GenerationConfig generation;
        public GooseFlowConfig gooseFlow;
        public SetupIEDConfig setupIED;
        public BenignDataManager.ElectricalSourcesConfig electricalSources;

        public static class OutputConfig {
            public String directory = "target/benign_data";
            public String[] formats = {"arff"};
            public String filenamePrefix;
            public boolean enableTracking = true;
            public String experimentId; // Optional: link to existing experiment
        }

        public static class GenerationConfig {
            public int numberOfMessages = 1000;
            public int faultProbability = 5;
            public String svStrategy = "DECIMATED";
            public int svSamplesPerGoose = 48;
        }

        public static class GooseFlowConfig {
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

        public static class SetupIEDConfig {
            public String iedName;
            public String gocbRef;
            public String datSet;
            public String minTime;
            public String maxTime;
            public String timestamp;
            public String stNum;
            public String sqNum;
        }
    }

    public static void execute(String configPath) throws IOException {
        LOGGER.info("=== Starting Create Benign Data Action ===");

        // Load and parse action config
        try (java.io.FileReader reader = new java.io.FileReader(configPath)) {
            Gson gson = new Gson();
            Config config = gson.fromJson(reader, Config.class);

        // Populate ConfigLoader for compatibility with existing code
        populateConfigLoader(config);

        // Create output directory
        File outDir = new File(config.output.directory);
        if (!outDir.exists()) {
            outDir.mkdirs();
            LOGGER.info(() -> "Created output directory: " + config.output.directory);
        }

        // Resolve electrical sources (.out files) used to feed the Merging Unit.
        // SamambaiaScenario used to hard-code these in InputFilesForSV; we now
        // accept them from the action config so each user can point at their
        // own dataset checkout.
        String[] electricalFiles = BenignDataManager.resolveElectricalSources(config.electricalSources);

        // ConfigLoader.gooseFlow.numberOfMessages is now ONLY the GOOSE count.
        // The SV count is derived per-strategy below. The legacy single-knob
        // overload (also gating ProtectionIED.addMessage) has been removed.
        ConfigLoader.gooseFlow.numberOfMessages = config.generation.numberOfMessages;
        ConfigLoader.benignData.faultProbability = config.generation.faultProbability;
        ConfigLoader.benignData.benignDataDir = config.output.directory;

        // Resolve SV strategy.
        String strategy = config.generation.svStrategy != null
                ? config.generation.svStrategy.trim().toUpperCase()
                : "DECIMATED";
        final int svSamplesPerGoose;
        final int svStride;
        if ("UPSTREAM_FAITHFUL".equals(strategy)) {
            svSamplesPerGoose = 4763;
            svStride = 1;
        } else {
            // DECIMATED (default). Anything else falls through with a warning.
            if (!"DECIMATED".equals(strategy)) {
                LOGGER.warning(() -> "Unknown svStrategy '" + config.generation.svStrategy
                        + "', falling back to DECIMATED.");
            }
            // Floor at 80: the per-GOOSE "cycle window" feature
            int requested = Math.max(1, config.generation.svSamplesPerGoose);
            if (requested < 80) {
                final int requestedFinal = requested;
                LOGGER.warning(() -> "svSamplesPerGoose=" + requestedFinal
                        + " is below the floor of 80 needed for valid cycle-window features; bumping to 80.");
                svSamplesPerGoose = 80;
            } else {
                svSamplesPerGoose = requested;
            }
            svStride = Math.max(1, 4763 / svSamplesPerGoose);
        }
        final int totalSv = config.generation.numberOfMessages * svSamplesPerGoose;
        LOGGER.info(() -> "SV strategy: " + strategy
                + " (svSamplesPerGoose=" + svSamplesPerGoose + ", stride=" + svStride
                + ", totalSv=" + totalSv + ")");

        // Build the substation network and devices.
        SubstationNetwork network = new SubstationNetwork();

        MergingUnit mu = new MergingUnit(electricalFiles);
        LOGGER.info(() -> "Generating SV stream from " + electricalFiles.length + " electrical-source files...");
        mu.runWithStride(totalSv, svStride);
        for (Sv sv : mu.getMessages()) {
            network.processBusMessages.add(sv);
        }
        LOGGER.info(() -> "Generated " + network.processBusMessages.size() + " SV messages on the process bus.");

        LegitimateProtectionIED ied = new LegitimateProtectionIED();
        ied.setSubstationNetwork(network);
        LOGGER.info(() -> "Generating " + config.generation.numberOfMessages + " benign GOOSE messages...");
        ied.run(config.generation.numberOfMessages);
        for (Goose goose : ied.copyMessages()) {
            network.stationBusMessages.add(goose);
        }
        LOGGER.info(() -> "Generated " + network.stationBusMessages.size() + " GOOSE messages on the station bus.");

        // Save in requested formats. processDataset() drains the priority
        // queue, so build a fresh copy for every format.
        String firstSavedPath = null;
        for (String format : config.output.formats) {
            String savedPath = writeBenignDataset(format, network);
            LOGGER.info(() -> "Saved " + format.toUpperCase() + " to: " + savedPath);
            if (firstSavedPath == null) firstSavedPath = savedPath;

            if (config.output.enableTracking) {
                trackBenignDataset(config, savedPath, ied.getNumberOfMessages(), configPath);
            }
        }

        // Persist the raw SV stream as a sidecar next to the benign output
        // so the attack action can recompute per-GOOSE 80-cycle windows from
        // real samples (legacy parity), instead of nearest-neighbor lookup
        // against the per-GOOSE prefix snapshot.
        if (firstSavedPath != null && !network.processBusMessages.isEmpty()) {
            String sidecarPath = BenignDataManager.getSvSidecarPath(firstSavedPath);
            br.ufu.facom.ereno.dataExtractors.SVSidecarWriter.write(sidecarPath, network.processBusMessages);
        }

            LOGGER.info("=== Create Benign Data Action Completed Successfully ===");
        }
    }

    /**
     * Writes a 54-column dataset (SV electrical features + GOOSE protocol
     * features + correlation/diff features) for one output format. Returns
     * the absolute path written.
     */
    private static String writeBenignDataset(String format, SubstationNetwork network) throws IOException {
        String savedPath = BenignDataManager.getBenignDataPath(format);
        // Ensure parent dir exists; getBenignDataPath() may include sub-folders.
        File parent = new File(savedPath).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        // Overwrite any previous file with the same name.
        File out = new File(savedPath);
        if (out.exists()) {
            out.delete();
        }

        // Drain-safe copy: the legacy writers poll() the priority queue.
        PriorityQueue<EthernetFrame> stationBusCopy = new PriorityQueue<>(network.stationBusMessages);

        if ("csv".equalsIgnoreCase(format)) {
            CSVWritter.startWriting(savedPath);
            CSVWritter.processDataset(stationBusCopy, network.processBusMessages);
            CSVWritter.finishWriting();
        } else if ("arff".equalsIgnoreCase(format)) {
            ARFFWritter.startWriting(savedPath);
            ARFFWritter.processDataset(stationBusCopy, network.processBusMessages);
            ARFFWritter.finishWriting();
        } else {
            throw new IOException("Unsupported output format for benign dataset: " + format
                    + " (supported: csv, arff)");
        }
        return savedPath;
    }
    
    private static void trackBenignDataset(Config config, String savedPath, 
                                           int numMessages, String configPath) {
        try {
            ExperimentTracker tracker = new ExperimentTracker();
            
            // Determine experiment ID
            String experimentId = config.output.experimentId;
            if (experimentId == null || experimentId.isEmpty()) {
                // Create a new standalone experiment
                experimentId = tracker.startExperiment(
                    "benign_dataset_creation",
                    "Create benign dataset with " + numMessages + " messages",
                    configPath,
                    "Standalone benign dataset creation"
                );
            }
            
            // Track the dataset
            String datasetId = tracker.trackBenignDataset(
                experimentId,
                savedPath,
                configPath,
                numMessages,
                "Benign dataset, fault probability: " + config.generation.faultProbability + "%"
            );
            
            LOGGER.info(() -> "Benign dataset tracked with ID: " + datasetId);
            
            // Complete experiment if we created it
            if (config.output.experimentId == null || config.output.experimentId.isEmpty()) {
                tracker.completeExperiment(experimentId);
            }
            
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to track benign dataset: " + e.getMessage());
            // Don't fail the action if tracking fails
        }
    }

    private static void populateConfigLoader(Config config) {
        // Populate gooseFlow
        if (config.gooseFlow != null) {
            ConfigLoader.gooseFlow.goID = config.gooseFlow.goID;
            ConfigLoader.gooseFlow.ethSrc = config.gooseFlow.ethSrc;
            ConfigLoader.gooseFlow.ethDst = config.gooseFlow.ethDst;
            ConfigLoader.gooseFlow.ethType = config.gooseFlow.ethType;
            ConfigLoader.gooseFlow.gooseAppid = config.gooseFlow.gooseAppid;
            ConfigLoader.gooseFlow.TPID = config.gooseFlow.TPID;
            ConfigLoader.gooseFlow.ndsCom = config.gooseFlow.ndsCom;
            ConfigLoader.gooseFlow.test = config.gooseFlow.test;
            ConfigLoader.gooseFlow.cbstatus = config.gooseFlow.cbstatus;
        }

        // Populate setupIED
        if (config.setupIED != null) {
            ConfigLoader.setupIED.iedName = config.setupIED.iedName;
            ConfigLoader.setupIED.gocbRef = config.setupIED.gocbRef;
            ConfigLoader.setupIED.datSet = config.setupIED.datSet;
            ConfigLoader.setupIED.minTime = config.setupIED.minTime;
            ConfigLoader.setupIED.maxTime = config.setupIED.maxTime;
            ConfigLoader.setupIED.timestamp = config.setupIED.timestamp;
            ConfigLoader.setupIED.stNum = config.setupIED.stNum;
            ConfigLoader.setupIED.sqNum = config.setupIED.sqNum;
        }
    }
}
