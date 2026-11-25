package br.ufu.facom.ereno.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.CSVWritter;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.messages.Goose;

/**
 * Manages saving and loading of benign datasets.
 * Benign data files are named: {randomSeed}_{faultProbability}%fault_benign_data.{ext}
 */
public class BenignDataManager {

    private static final Logger LOGGER = Logger.getLogger(BenignDataManager.class.getName());

    /**
     * Generates the filename for benign data based on current config.
     * Format: {randomSeed}_{faultProbability}%fault_benign_data.{ext}
     */
    public static String generateBenignDataFilename(String format) {
        Long seedBoxed = ConfigLoader.getSeed();
        long seed = (seedBoxed != null) ? seedBoxed : System.nanoTime();
        int faultProb = ConfigLoader.benignData.faultProbability;
        String ext = "csv".equalsIgnoreCase(format) ? "csv" : "arff";
        return seed + "_" + faultProb + "%fault_benign_data." + ext;
    }

    /**
     * Gets the full path for saving benign data.
     */
    public static String getBenignDataPath(String format) {
        String dir = ConfigLoader.benignData.benignDataDir;
        String filename = generateBenignDataFilename(format);
        return dir + File.separator + filename;
    }

    /**
     * Derive the SV sidecar path from a benign dataset path. Strips the
     * trailing extension (csv/arff) and appends ".sv.csv". Format-agnostic:
     * both csv and arff benign outputs share a single sidecar.
     */
    public static String getSvSidecarPath(String benignDataPath) {
        int dot = benignDataPath.lastIndexOf('.');
        String base = (dot > 0) ? benignDataPath.substring(0, dot) : benignDataPath;
        return base + ".sv.csv";
    }

    /**
     * Load the SV sidecar (raw {@link br.ufu.facom.ereno.messages.Sv}
     * samples) that sits next to a benign dataset. Returns an empty list
     * if the sidecar is missing, so callers can decide to fall back to
     * snapshot mode for back-compat with pre-sidecar benign files.
     *
     * <p>Returned as {@code ArrayList<EthernetFrame>} to match the
     * signature of {@link CSVWritter#processDataset} /
     * {@code ARFFWritter.processDataset}.</p>
     */
    public static ArrayList<br.ufu.facom.ereno.messages.EthernetFrame> loadSvSidecar(String benignDataPath) throws IOException {
        ArrayList<br.ufu.facom.ereno.messages.EthernetFrame> svs = new ArrayList<>();
        String sidecarPath = getSvSidecarPath(benignDataPath);
        File sidecar = new File(sidecarPath);
        if (!sidecar.exists()) {
            LOGGER.info("SV sidecar not found at: " + sidecarPath);
            return svs;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(sidecar))) {
            String header = br.readLine(); // skip header
            if (header == null) return svs;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length < 7) continue;
                try {
                    float time = Float.parseFloat(p[0].trim());
                    float iA = Float.parseFloat(p[1].trim());
                    float iB = Float.parseFloat(p[2].trim());
                    float iC = Float.parseFloat(p[3].trim());
                    float vA = Float.parseFloat(p[4].trim());
                    float vB = Float.parseFloat(p[5].trim());
                    float vC = Float.parseFloat(p[6].trim());
                    svs.add(new br.ufu.facom.ereno.messages.Sv(time, iA, iB, iC, vA, vB, vC));
                } catch (NumberFormatException ignored) {
                    // skip malformed row
                }
            }
        }
        LOGGER.info("Loaded SV sidecar with " + svs.size() + " raw samples from: " + sidecarPath);
        return svs;
    }

    /**
     * Saves benign GOOSE messages to a file in the configured directory.
     * Creates the directory if it doesn't exist.
     */
    public static void saveBenignData(ArrayList<Goose> messages, String format) throws IOException {
        String dirPath = ConfigLoader.benignData.benignDataDir;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
            LOGGER.info("Created benign data directory: " + dirPath);
        }

        String filepath = getBenignDataPath(format);
        boolean csvMode = "csv".equalsIgnoreCase(format);

        LOGGER.info("Saving " + messages.size() + " benign messages to: " + filepath);

        if (csvMode) {
            saveBenignDataCsv(messages, filepath);
        } else {
            saveBenignDataArff(messages, filepath);
        }

        LOGGER.info("Benign data saved successfully.");
    }

    /**
     * Saves benign data in CSV format.
     */
    private static void saveBenignDataCsv(ArrayList<Goose> messages, String filepath) throws IOException {
        CSVWritter.startWriting(filepath);
        CSVWritter.writeDefaultHeader();
        
        Goose prev = null;
        for (Goose gm : messages) {
            if (prev != null) {
                String gooseString = gm.asCSVFull();
                String gooseConsistency = br.ufu.facom.ereno.featureEngineering.IntermessageCorrelation.getConsistencyFeaturesAsCSV(gm, prev);
                double e2eLatency = gm.getE2ELatencyMs();
                double receivedTimestamp = gm.getSubscriberRxTs() != null ? gm.getSubscriberRxTs() : gm.getTimestamp();
                String line = gooseString + "," + gooseConsistency + "," + e2eLatency + "," + receivedTimestamp + "," + gm.getLabel();
                CSVWritter.writeLine(line);
            }
            prev = gm.copy();
        }
        
        CSVWritter.finishWriting();
    }

    /**
     * Saves benign data in ARFF format.
     */
    private static void saveBenignDataArff(ArrayList<Goose> messages, String filepath) throws IOException {
        GSVDatasetWriter.startWriting(filepath);
        GSVDatasetWriter.write("@relation benign_data");
        GSVDatasetWriter.writeGooseMessagesToFile(messages, true);
        GSVDatasetWriter.finishWriting();
    }

    /**
     * Loads benign GOOSE messages from a file.
     * Supports both CSV and ARFF formats (detects by extension).
     */
    public static LegitimateProtectionIED loadBenignData(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists()) {
            throw new FileNotFoundException("Benign data file not found: " + filepath);
        }

        LOGGER.info("Loading benign data from: " + filepath);

        LegitimateProtectionIED ied = new LegitimateProtectionIED();
        ArrayList<Goose> messages;

        if (filepath.toLowerCase().endsWith(".csv")) {
            messages = loadBenignDataCsv(filepath);
        } else if (filepath.toLowerCase().endsWith(".arff")) {
            messages = loadBenignDataArff(filepath);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Use .csv or .arff");
        }

        ied.setMessages(messages);
        LOGGER.info("Loaded " + messages.size() + " benign messages.");
        return ied;
    }

    /**
     * Loads benign data from CSV format.
     */
    private static ArrayList<Goose> loadBenignDataCsv(String filepath) throws IOException {
        ArrayList<Goose> messages = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            boolean headerSkipped = false;
            
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                // Skip header line
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                // Parse CSV line into Goose message
                Goose goose = parseGooseFromCsv(line);
                if (goose != null) {
                    messages.add(goose);
                }
            }
        }
        
        return messages;
    }

    /**
     * Loads benign data from ARFF format.
     */
    private static ArrayList<Goose> loadBenignDataArff(String filepath) throws IOException {
        ArrayList<Goose> messages = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            boolean inData = false;
            
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("%")) continue;
                
                if (line.toLowerCase().startsWith("@data")) {
                    inData = true;
                    continue;
                }
                
                if (inData) {
                    // Parse ARFF data line into Goose message
                    Goose goose = parseGooseFromCsv(line);  // Same format as CSV
                    if (goose != null) {
                        messages.add(goose);
                    }
                }
            }
        }
        
        return messages;
    }

    /**
     * Number of leading SV-related columns prepended to each row by the
     * 54-column writers ({@link br.ufu.facom.ereno.dataExtractors.CSVWritter}
     * and {@link br.ufu.facom.ereno.dataExtractors.ARFFWritter}). Layout:
     * Time, isbA, isbB, isbC, vsbA, vsbB, vsbC,
     * isb{A,B,C}RmsValue, vsb{A,B,C}RmsValue,
     * isb{A,B,C}TrapAreaSum, vsb{A,B,C}TrapAreaSum.
     */
    private static final int SV_PREFIX_COLUMNS = 19;

    /**
     * Parses a Goose message from a CSV/ARFF data line. Supports two
     * schemas:
     * <ul>
     *   <li><b>34-column "narrow" GOOSE-only schema</b> emitted by the
     *       legacy {@code DatasetWriter.writeGooseMessagesToFile} path:
     *       {@code t, GooseTimestamp, SqNum, StNum, cbStatus, ..., label}.</li>
     *   <li><b>54-column "wide" SV+GOOSE schema</b> emitted by
     *       {@code CSVWritter.processDataset} /
     *       {@code ARFFWritter.processDataset}: the same GOOSE fields
     *       prefixed with 19 SV-derived columns (see
     *       {@link #SV_PREFIX_COLUMNS}). When wide rows are detected the
     *       SV prefix is stashed verbatim onto the returned
     *       {@link Goose#setSvPrefixCsv(String)} so the consuming action
     *       can re-emit it without ever touching the .out source files.</li>
     * </ul>
     */
    private static Goose parseGooseFromCsv(String line) {
        try {
            String[] parts = line.split(",");
            // Detect the wide (54-col) schema: skip the SV prefix.
            int offset = (parts.length >= SV_PREFIX_COLUMNS + 29) ? SV_PREFIX_COLUMNS : 0;
            if (parts.length - offset < 29) return null;

            // Extract fields based on writeGooseMessagesToFile() output order.
            double t = Double.parseDouble(parts[offset].trim());
            double timestamp = Double.parseDouble(parts[offset + 1].trim());
            int sqNum = Integer.parseInt(parts[offset + 2].trim());
            int stNum = Integer.parseInt(parts[offset + 3].trim());
            int cbStatus = Integer.parseInt(parts[offset + 4].trim());
            // Label is always the last field.
            String label = parts[parts.length - 1].trim();

            Goose goose = new Goose(cbStatus, stNum, sqNum, timestamp, t, label);

            // Wide schema: keep the SV prefix attached so the consuming
            // action can re-emit it without re-running the MergingUnit.
            if (offset == SV_PREFIX_COLUMNS) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < SV_PREFIX_COLUMNS; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(parts[i].trim());
                }
                goose.setSvPrefixCsv(sb.toString());

                // The {@link Goose} nominal/protocol fields (ethDst, ethSrc,
                // gocbRef, etc.) are class-level statics initialised by
                // {@link Goose#fromECF()} from {@code ConfigLoader.gooseFlow}
                // / {@code ConfigLoader.setupIED}. The benign action loads
                // an ECF that populates those, but downstream actions
                // (CreateAttackDatasetAction, TrainModelAction) do not, so
                // the constructor we just invoked silently overwrote the
                // statics with {@code null}. The wide ARFF row carries the
                // authoritative values; restore them here so subsequent
                // serialisation via {@link Goose#asCSVFull()} emits real
                // tokens rather than the literal string "null", which Weka
                // rejects as an undeclared nominal value.
                //
                // Layout of the 22-column GOOSE block written by
                // {@link Goose#asCSVFull()} (offsets relative to {@code offset}):
                //   0:t  1:timestamp  2:SqNum  3:StNum  4:cbStatus  5:frameLen
                //   6:ethDst  7:ethSrc  8:ethType  9:gooseTimeAllowedtoLive
                //   10:gooseAppid  11:gooseLen  12:TPID  13:gocbRef  14:datSet
                //   15:goID  16:test  17:confRev  18:ndsCom  19:numDatSetEntries
                //   20:APDUSize  21:protocol
                if (parts.length - offset >= 22) {
                    restoreGooseStaticsFromRow(parts, offset);
                }
            }

            // e2eLatency / receivedTimestamp live near the end of the row in
            // the wide schema (delay, e2eLatency, receivedTimestamp, label).
            int gooseFieldCount = parts.length - offset;
            int e2eLatencyIndex = gooseFieldCount >= 35 ? parts.length - 3 : parts.length - 2;
            int receivedTimestampIndex = gooseFieldCount >= 35 ? parts.length - 2 : -1;
            if (e2eLatencyIndex >= 0) {
                try {
                    double e2eLatency = Double.parseDouble(parts[e2eLatencyIndex].trim());
                    goose.setPublisherTxTs(timestamp);
                    if (receivedTimestampIndex >= 0) {
                        double receivedTimestamp = Double.parseDouble(parts[receivedTimestampIndex].trim());
                        goose.setSubscriberRxTs(receivedTimestamp);
                    } else {
                        goose.setSubscriberRxTs(timestamp + (e2eLatency / 1000.0));
                    }
                } catch (NumberFormatException ignored) {
                    // Older files or non-e2e schema: keep default/fallback behavior
                }
            }

            return goose;
        } catch (Exception e) {
            LOGGER.warning("Failed to parse line: " + line + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Configuration block, deserialized by Gson from an action JSON file's
     * {@code electricalSources} object. Mirrors the JSON shape:
     *
     * <pre>
     * "electricalSources": {
     *   "files":     [ "...\\SILVIO_r00001_01.out", "...\\SILVIO_r00002_01.out" ],
     *   "directory": "C:/path/to/electrical-sources/resistencia10",
     *   "pattern":   "*.out",
     *   "limit":     0
     * }
     * </pre>
     *
     * Either {@code files} or {@code directory} must be set; if both are set,
     * {@code files} wins.
     */
    public static class ElectricalSourcesConfig {
        public List<String> files;
        public String directory;
        public String pattern;
        public int limit;
    }

    /**
     * Resolve electrical-source ".out" files declared in an action config
     * into an array of absolute paths suitable for
     * {@link br.ufu.facom.ereno.benign.uc00.devices.MergingUnit#MergingUnit(String[])}.
     * Files are returned in lexicographic order so generation is
     * reproducible across runs.
     */
    public static String[] resolveElectricalSources(ElectricalSourcesConfig config) throws IOException {
        if (config == null) {
            throw new IOException(
                    "electricalSources block is required to generate SV traffic. "
                            + "Add an 'electricalSources' object with either a 'files' array "
                            + "or a 'directory' (plus optional 'pattern' and 'limit') to your action config.");
        }

        ArrayList<String> resolved = new ArrayList<>();

        if (config.files != null && !config.files.isEmpty()) {
            for (String f : config.files) {
                if (f == null || f.trim().isEmpty()) continue;
                File file = new File(f);
                if (!file.exists()) {
                    throw new IOException("electricalSources.files entry not found: " + f);
                }
                resolved.add(file.getAbsolutePath());
            }
        } else if (config.directory != null && !config.directory.trim().isEmpty()) {
            Path dir = Paths.get(config.directory);
            if (!Files.isDirectory(dir)) {
                throw new IOException("electricalSources.directory is not a directory: " + config.directory);
            }
            String glob = (config.pattern == null || config.pattern.trim().isEmpty())
                    ? "*.out" : config.pattern;
            ArrayList<Path> paths = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) {
                        paths.add(p);
                    }
                }
            }
            Collections.sort(paths);
            for (Path p : paths) {
                resolved.add(p.toAbsolutePath().toString());
            }
        } else {
            throw new IOException(
                    "electricalSources block must declare either 'files' or 'directory'.");
        }

        if (resolved.isEmpty()) {
            throw new IOException(
                    "electricalSources resolved to zero files. Check 'files'/'directory'/'pattern'.");
        }

        if (config.limit > 0 && resolved.size() > config.limit) {
            resolved = new ArrayList<>(resolved.subList(0, config.limit));
        }

        final int finalCount = resolved.size();
        LOGGER.info(() -> "Resolved " + finalCount + " electrical-source files for SV generation.");
        return resolved.toArray(new String[0]);
    }

    /**
     * Lists all available benign data files in the configured directory.
     */
    public static ArrayList<String> listBenignDataFiles() {
        ArrayList<String> files = new ArrayList<>();
        String dirPath = ConfigLoader.benignData.benignDataDir;
        File dir = new File(dirPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return files;
        }
        
        File[] fileList = dir.listFiles((d, name) -> 
            name.endsWith("benign_data.csv") || name.endsWith("benign_data.arff"));
        
        if (fileList != null) {
            for (File f : fileList) {
                files.add(f.getName());
            }
        }
        
        return files;
    }

    /**
     * Restores the {@link Goose} static nominal/protocol fields from a wide
     * ARFF row's GOOSE block. Only assigns when the parsed token is a
     * non-empty value distinct from the literal {@code "null"} placeholder
     * that an earlier null-state {@link Goose#asCSVFull()} may have emitted.
     */
    private static void restoreGooseStaticsFromRow(String[] parts, int offset) {
        Goose.ethDst = pickValid(parts, offset + 6, Goose.ethDst);
        Goose.ethSrc = pickValid(parts, offset + 7, Goose.ethSrc);
        Goose.ethType = pickValid(parts, offset + 8, Goose.ethType);
        Goose.gooseAppid = pickValid(parts, offset + 10, Goose.gooseAppid);
        Goose.TPID = pickValid(parts, offset + 12, Goose.TPID);
        Goose.gocbRef = pickValid(parts, offset + 13, Goose.gocbRef);
        Goose.datSet = pickValid(parts, offset + 14, Goose.datSet);
        Goose.goID = pickValid(parts, offset + 15, Goose.goID);
        Goose.test = pickValid(parts, offset + 16, Goose.test);
        Goose.ndsCom = pickValid(parts, offset + 18, Goose.ndsCom);
        Goose.protocol = pickValid(parts, offset + 21, Goose.protocol);
    }

    private static String pickValid(String[] parts, int idx, String fallback) {
        if (idx < 0 || idx >= parts.length) return fallback;
        String v = parts[idx].trim();
        if (v.isEmpty() || v.equalsIgnoreCase("null") || v.equals("?")) return fallback;
        return v;
    }
}
