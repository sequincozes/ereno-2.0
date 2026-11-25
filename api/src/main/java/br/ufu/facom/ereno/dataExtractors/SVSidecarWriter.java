package br.ufu.facom.ereno.dataExtractors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import br.ufu.facom.ereno.messages.EthernetFrame;
import br.ufu.facom.ereno.messages.Sv;

public final class SVSidecarWriter {

    private static final Logger LOGGER = Logger.getLogger(SVSidecarWriter.class.getName());
    public static final String HEADER = "Time,iA,iB,iC,vA,vB,vC";

    private SVSidecarWriter() {}

    public static void write(String path, ArrayList<? extends EthernetFrame> processBusMessages) throws IOException {
        File out = new File(path);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(out, false))) {
            w.write(HEADER);
            w.newLine();
            int count = 0;
            for (EthernetFrame frame : processBusMessages) {
                Sv sv = (Sv) frame;
                w.write(sv.getRawTime()
                        + "," + sv.getiA() + "," + sv.getiB() + "," + sv.getiC()
                        + "," + sv.getvA() + "," + sv.getvB() + "," + sv.getvC());
                w.newLine();
                count++;
            }
            final int finalCount = count;
            LOGGER.info(() -> "Wrote SV sidecar (" + finalCount + " samples) to: " + path);
        }
    }
}
