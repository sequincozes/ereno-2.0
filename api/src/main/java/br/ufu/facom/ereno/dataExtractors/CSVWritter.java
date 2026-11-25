package br.ufu.facom.ereno.dataExtractors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.logging.Logger;

import br.ufu.facom.ereno.featureEngineering.IntermessageCorrelation;
import br.ufu.facom.ereno.featureEngineering.ProtocolCorrelation;
import br.ufu.facom.ereno.messages.EthernetFrame;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.messages.Sv;

/**
 * This extractor writes the generated messages to an CSV file.
 * It generates a GOOSE-oriented dataset (one sample per GOOSE).
 */
public class CSVWritter {
    private static final ThreadLocal<BufferedWriter> THREAD_WRITER = new ThreadLocal<>();
    

    public static void processDataset(PriorityQueue<EthernetFrame> stationBusMessages, ArrayList<EthernetFrame> processBusMessages) throws IOException {
        // Writing Header
        writeDefaultHeader();

        // Writing Messages
        Goose previousGoose = null;
//        for (EthernetFrame gooseFrame : stationBusMessages) {
//            Goose goose = (Goose) gooseFrame;

        // temporario
        int contador= 10; // REMOVER DEPOIS DOS TESTES
        Logger.getLogger("CSVWritter").info(stationBusMessages.size() + " mensagens na fila.");
        while (!stationBusMessages.isEmpty()) {
            Goose goose = (Goose) stationBusMessages.poll();
            if (previousGoose != null) { // skips the first message
                Sv sv = ProtocolCorrelation.getCorrespondingSVFrame(processBusMessages, goose);
                String svString = sv.asCsv();
                String cycleStrig = ProtocolCorrelation.getCorrespondingSVFrameCycle(processBusMessages, goose, 80).asCsv();
                // Persist the 19 SV-derived columns onto the Goose so any
                // downstream consumer (in-process pipelines, attack actions
                // re-reading the file) can emit the same SV row without
                // needing the .out source files.
                goose.setSvPrefixCsv(svString + "," + cycleStrig);
                String gooseString = goose.asCSVFull();

//                contador = contador - 1; // REMOVER DEPOIS DOS TESTES
//                if(contador==0){ // REMOVER DEPOIS DOS TESTES
//                break;// REMOVER DEPOIS DOS TESTES
//                } // REMOVER DEPOIS DOS TESTES
                String gooseConsistency = IntermessageCorrelation.getConsistencyFeaturesAsCSV(goose, previousGoose);

                double delay = goose.getTimestamp() - sv.getTime();
                double e2eLatency = goose.getE2ELatencyMs();
                double receivedTimestamp = goose.getSubscriberRxTs() != null ? goose.getSubscriberRxTs() : goose.getTimestamp();
                write(svString + "," + cycleStrig + "," + gooseString + "," + gooseConsistency + "," + delay + "," + e2eLatency + "," + receivedTimestamp + "," + goose.getLabel());
            }
            previousGoose = goose.copy();
        }


    }

    private static void write(String line) throws IOException {
        BufferedWriter writer = THREAD_WRITER.get();
        if (writer == null) {
            throw new IllegalStateException("CSV writer not initialized for current thread. Call startWriting first.");
        }
        writer.write(line);
        writer.newLine();
    }

    public static void startWriting(String filename) throws IOException {
        File fout = new File(filename);
        if (!fout.exists()) {
            fout.getParentFile().mkdirs();
            System.out.println("Directory created at: " + filename);
        }
        // overwrite by default (no append)
        FileOutputStream fos = new FileOutputStream(fout, false);
        THREAD_WRITER.set(new BufferedWriter(new OutputStreamWriter(fos)));
    }

    public static void writeLine(String line) throws IOException {
        write(line);
    }

    public static void writeDefaultHeader() throws IOException {
        String header = String.join(",", new String[] {
                "Time", "isbA", "isbB", "isbC", "vsbA", "vsbB", "vsbC",
                "isbARmsValue", "isbBRmsValue", "isbCRmsValue", "vsbARmsValue", "vsbBRmsValue", "vsbCRmsValue",
                "isbATrapAreaSum", "isbBTrapAreaSum", "isbCTrapAreaSum", "vsbATrapAreaSum", "vsbBTrapAreaSum", "vsbCTrapAreaSum",
                "t", "GooseTimestamp", "SqNum", "StNum", "cbStatus", "frameLen", "ethDst", "ethSrc", "ethType",
                "gooseTimeAllowedtoLive", "gooseAppid", "gooseLen", "TPID", "gocbRef", "datSet", "goID", "test", "confRev", "ndsCom",
                "numDatSetEntries", "APDUSize", "protocol", "stDiff", "sqDiff", "gooseLengthDiff", "cbStatusDiff",
                "apduSizeDiff", "frameLengthDiff", "timestampDiff", "tDiff", "timeFromLastChange", "delay", "e2eLatency", "receivedTimestamp", "class"
        });
        write(header);
    }

    public static void finishWriting() throws IOException {
        BufferedWriter writer = THREAD_WRITER.get();
        if (writer != null) {
            writer.close();
            THREAD_WRITER.remove();
        }
    }

    /**
     * Writer variant that emits the wide 54-column dataset using SV prefixes
     * already attached to each {@link Goose} (see
     * {@link Goose#getSvPrefixCsv()}). Avoids re-running the {@code MergingUnit}
     * when the SV stream has been persisted alongside GOOSE in a prior run.
     *
     * <p>Goose messages without an attached prefix (typically GOOSE created
     * by injection-style attacks like uc05/06/07) fall back to the prefix of
     * the time-nearest neighbor in {@code snapshotIndex}, so every emitted
     * row still carries 19 SV columns.</p>
     */
    public static void processDatasetWithAttachedSv(PriorityQueue<EthernetFrame> stationBusMessages,
                                                    java.util.List<Goose> snapshotIndex) throws IOException {
        writeDefaultHeader();
        SnapshotLookup lookup = new SnapshotLookup(snapshotIndex);

        Goose previousGoose = null;
        Logger.getLogger("CSVWritter").info(stationBusMessages.size() + " mensagens na fila (snapshot mode).");
        while (!stationBusMessages.isEmpty()) {
            Goose goose = (Goose) stationBusMessages.poll();
            if (previousGoose != null) {
                String svPrefix = goose.getSvPrefixCsv();
                if (svPrefix == null) {
                    svPrefix = lookup.findPrior(goose.getTimestamp());
                }
                String gooseString = goose.asCSVFull();
                String gooseConsistency = IntermessageCorrelation.getConsistencyFeaturesAsCSV(goose, previousGoose);
                double svTime = SnapshotLookup.parseTime(svPrefix);
                double delay = goose.getTimestamp() - svTime;
                double e2eLatency = goose.getE2ELatencyMs();
                double receivedTimestamp = goose.getSubscriberRxTs() != null ? goose.getSubscriberRxTs() : goose.getTimestamp();
                write(svPrefix + "," + gooseString + "," + gooseConsistency + "," + delay + "," + e2eLatency + "," + receivedTimestamp + "," + goose.getLabel());
            }
            previousGoose = goose.copy();
        }
    }

    /**
     * Sorted index over a list of GOOSE snapshots, supporting nearest-by-time
     * lookup. Shared between {@link CSVWritter} and {@link ARFFWritter}.
     */
    public static final class SnapshotLookup {
        private final double[] times;
        private final String[] prefixes;

        public SnapshotLookup(java.util.List<Goose> snapshots) {
            java.util.ArrayList<Goose> filtered = new java.util.ArrayList<>();
            for (Goose g : snapshots) {
                if (g.getSvPrefixCsv() != null) filtered.add(g);
            }
            filtered.sort(java.util.Comparator.comparingDouble(EthernetFrame::getTimestamp));
            this.times = new double[filtered.size()];
            this.prefixes = new String[filtered.size()];
            for (int i = 0; i < filtered.size(); i++) {
                this.times[i] = filtered.get(i).getTimestamp();
                this.prefixes[i] = filtered.get(i).getSvPrefixCsv();
            }
        }

        public String findPrior(double timestamp) {
            if (times.length == 0) {
                throw new IllegalStateException(
                        "No SV snapshots available to fall back on. The benign file was loaded "
                                + "without SV columns, or all loaded GOOSE lack a prefix.");
            }
            int idx = java.util.Arrays.binarySearch(times, timestamp);
            if (idx >= 0) {
                return idx > 0 ? prefixes[idx - 1] : prefixes[0];
            }
            int ins = -idx - 1;
            if (ins == 0) return prefixes[0]; // no prior; defensive fallback
            return prefixes[ins - 1];
        }

        /** Extract the leading "Time" column from a 19-cell SV prefix CSV. */
        public static double parseTime(String svPrefix) {
            int comma = svPrefix.indexOf(',');
            return Double.parseDouble((comma < 0 ? svPrefix : svPrefix.substring(0, comma)).trim());
        }
    }

}
