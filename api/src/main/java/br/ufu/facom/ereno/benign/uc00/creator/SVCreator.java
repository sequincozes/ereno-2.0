package br.ufu.facom.ereno.benign.uc00.creator;

import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.benign.uc00.devices.MergingUnit;
import br.ufu.facom.ereno.messages.Sv;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class SVCreator implements MessageCreator {
    private float offset = 0;
    private final String[] payloadFiles;
    private final int stride;
    private MergingUnit mu; // This is the samambaia (sb) substation MU
    String[] columnsTitle = {
            "Time",
            "isbA", "isbB", "isbC",  // Current substation Samambaia
            "ismA", "ismB", "ismC",  // Current substation Serra da mesa
            "vsbA", "vsbB", "vsbC",  // Voltage Samambaia
            "vsmA", "vsmB", "vsmC"}; // Voltage substation Serra da mesa

    public SVCreator(String[] payloadFiles) {
        this(payloadFiles, 1);
    }

    /**
     * @param stride keep every Nth row from the .out files; 1 = full 4763 sps
     *               fidelity (legacy), N > 1 = decimated waveform.
     */
    public SVCreator(String[] payloadFiles, int stride) {
        this.payloadFiles = payloadFiles;
        this.stride = Math.max(1, stride);
    }

    @Override
    public void generate(IED ied, int numberOfSVMessages) {
        this.mu = (MergingUnit) ied;

        ArrayList<Float[]> allElectricalMeassures = parseElectricalMeasurementsFromCsv(payloadFiles, 1, columnsTitle);

        Logger.getLogger("SVCreator.generate()").info("Generating " + numberOfSVMessages
                + " SV messages (stride=" + stride + ").");
        Logger.getLogger("SVCreator.generate()").info(+allElectricalMeassures.size() + " lines available.");
        int countMessages = 0;
        while (mu.getMessages().size() < numberOfSVMessages) {
            for (int rowIdx = 0; rowIdx < allElectricalMeassures.size(); rowIdx += stride) {
                Float[] lines = allElectricalMeassures.get(rowIdx);
                countMessages = countMessages + 1;
                if (countMessages % 4763 == 0) {
                    offset = offset + 1;
                    Logger.getLogger(" ").info(+countMessages+" messages SV. Finished one electrical file: 4763 multiple: " + countMessages / 4763);
                }
                if (mu.getMessages().size() < numberOfSVMessages) {
                    mu.addMessage(new Sv(offset + lines[0], lines[1], lines[2], lines[3], lines[7], lines[8], lines[9]));
                } else {
                    Logger.getLogger("SVCreator.generate()").info(+mu.getMessages().size() + " SV messages generated.");
                    break;
                }
            }
            // After each full file pass, bump offset by the file's effective duration
            // so successive passes don't reuse identical timestamps. The legacy code
            // relied on the count%4763==0 increment, which only fires for stride=1.
            if (stride > 1 && mu.getMessages().size() < numberOfSVMessages) {
                offset = offset + 1;
            }
        }
    }

    protected ArrayList<Float[]> parseElectricalMeasurementsFromCsv(String[] files, int scale, String columns[]) {
        ArrayList<Float[]> formatedCSVFile = new ArrayList<>();

        for (String file : files) {
            try {
                File myObj = new File(file);
                try (Scanner myReader = new Scanner(myObj)) {
                    myReader.nextLine(); // Skip blank line
                    while (myReader.hasNextLine()) {
                        String data = myReader.nextLine();
                        if (data.length() > 1) {
                            while (data.charAt(0) == ' ') {
                                data = data.substring(1, data.length());
                            }

                            while (data.trim().contains("  ")) {
                                data = data.replace("  ", " ");
                            }

                            data = data.replace(" ", ",");

                            StringTokenizer stringTokenizer = new StringTokenizer(data, ",", true);
                            int t = 0;
                            Float[] tokenLine = new Float[columns.length];
                            while (stringTokenizer.hasMoreTokens()) {
                                t++;
                                String next = stringTokenizer.nextToken();
                                if (!next.contains(",")) {
                                    float feature = Float.valueOf(next) * scale;
                                    int column = ((t + 1) / 2) - 1;
                                    if (mu.getInitialTimestamp()>0) {
                                        if (columns[column].equalsIgnoreCase("Time")) {
                                            feature = feature + mu.getInitialTimestamp();
                                        }
                                    }
                                    tokenLine[column] = feature;
                                }
                            }
                            formatedCSVFile.add(tokenLine);
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                System.out.println("Erro: " + e.getLocalizedMessage());
            }
        }
        return formatedCSVFile;
    }

}
