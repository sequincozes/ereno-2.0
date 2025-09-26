package br.ufu.facom.ereno.dataExtractors;

import br.ufu.facom.ereno.featureEngineering.IntermessageCorrelation;
import br.ufu.facom.ereno.featureEngineering.ProtocolCorrelation;
import br.ufu.facom.ereno.featureEngineering.SVCycle;
import br.ufu.facom.ereno.messages.EthernetFrame;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.messages.Sv;

import java.io.*;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class CSVWritter {
    public static String[] label = {
            "normal", "random_replay", "inverse_replay", "masquerade_fake_fault",
            "masquerade_fake_normal", "injection", "high_StNum",
            "poisoned_high_rate", "grayhole"
    };

    private static final int BUFFER_SIZE = 8192;
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 10000;

    private static BufferedWriter bw;
    private static BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static ExecutorService processingPool = Executors.newFixedThreadPool(NUM_THREADS);
    private static volatile boolean processingDone = false;

    private static final Logger logger = Logger.getLogger("ThreadedCSVWriter");

    public static void processDataset(PriorityQueue<EthernetFrame> stationBusMessages,
                                      ArrayList<EthernetFrame> processBusMessages) throws IOException, InterruptedException {
        writeDefaultHeader();

        Goose previousGoose = null;

        // Start the writer thread
        Thread writerThread = new Thread(() -> {
            try {
                while (!processingDone || !writeQueue.isEmpty()) {
                    String line = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (line != null) {
                        bw.write(line);
                    }
                }
                bw.flush();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        writerThread.start();

        while (!stationBusMessages.isEmpty()) {
            Goose goose = (Goose) stationBusMessages.poll();
            Goose prev = previousGoose;

            if (prev != null) {
                processingPool.submit(() -> {
                    try {
                        Sv sv = ProtocolCorrelation.getCorrespondingSVFrame(processBusMessages, goose);
                        SVCycle cycle = ProtocolCorrelation.getCorrespondingSVFrameCycle(processBusMessages, goose, 80);
                        String gooseConsistency = IntermessageCorrelation.getConsistencyFeaturesAsCSV(goose, prev);

                        String csvLine = String.format("%s,%s,%s,%s,%.6f,%s%n",
                                sv.asCsv(),
                                cycle.asCsv(),
                                goose.asCSVFull(),
                                gooseConsistency,
                                goose.getTimestamp() - sv.getTime(),
                                goose.getLabel());

                        writeQueue.put(csvLine);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            previousGoose = goose.copy();
        }

        processingPool.shutdown();
        processingPool.awaitTermination(10, TimeUnit.MINUTES);
        processingDone = true;
        writerThread.join();
    }

    public static void startWriting(String filename) throws IOException {
        File fout = new File(filename);
        if (!fout.exists()) {
            fout.getParentFile().mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(fout, false);
        bw = new BufferedWriter(new OutputStreamWriter(fos), BUFFER_SIZE);
    }

    public static void writeDefaultHeader() throws IOException {
        String header = String.join(",", new String[]{
                "Time", "isbA", "isbB", "isbC", "vsbA", "vsbB", "vsbC",
                "isbARmsValue", "isbBRmsValue", "isbCRmsValue", "vsbARmsValue", "vsbBRmsValue", "vsbCRmsValue",
                "isbATrapAreaSum", "isbBTrapAreaSum", "isbCTrapAreaSum", "vsbATrapAreaSum", "vsbBTrapAreaSum", "vsbCTrapAreaSum",
                "t", "GooseTimestamp", "SqNum", "StNum", "cbStatus", "frameLen", "ethDst", "ethSrc", "ethType",
                "gooseTimeAllowedtoLive", "gooseAppid", "gooseLen", "TPID", "gocbRef", "datSet", "goID", "test", "confRev", "ndsCom",
                "numDatSetEntries", "APDUSize", "protocol", "stDiff", "sqDiff", "gooseLengthDiff", "cbStatusDiff",
                "apduSizeDiff", "frameLengthDiff", "timestampDiff", "tDiff", "timeFromLastChange", "delay", "class"
        });
        bw.write(header);
        bw.newLine();
    }

    public static void finishWriting() throws IOException {
        bw.close();
    }
}
