package br.ufu.facom.ereno.benign.uc00.devices;

import br.ufu.facom.ereno.benign.uc00.creator.SVCreator;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.EthernetFrame;
import br.ufu.facom.ereno.messages.Sv;

import java.util.ArrayList;
import java.util.logging.Logger;

public class MergingUnit extends IED {
    protected ArrayList<Sv> messages;

    String[] payloadFiles;

    public MergingUnit(String[] payloadFiles) {
        this.messages = new ArrayList<>();
        this.payloadFiles = payloadFiles;
    }

    @Override
    public void run(int numberOfSVMessages) {
        runWithStride(numberOfSVMessages, 1);
    }

    /**
     * Generate {@code numberOfSVMessages} SV samples by reading every
     * {@code stride}-th row from the .out files. {@code stride == 1} matches
     * legacy behavior (4763 sps full fidelity). {@code stride > 1} decimates
     * the waveform proportionally; e.g. stride=99 yields ~48 sps.
     */
    public void runWithStride(int numberOfSVMessages, int stride) {
        SVCreator creator = new SVCreator(payloadFiles, stride);
        messageCreator = creator;
        Logger.getLogger("SVCreator").info("Initial Timestamp: "+ getInitialTimestamp()
                + ", stride=" + stride);
        creator.generate(this, numberOfSVMessages);
    }

    @Override
    public void addMessage(EthernetFrame message) {
        this.messages.add((Sv) message);
    }


    public ArrayList<Sv> getMessages() {
        return messages;
    }

    public void setMessages(ArrayList<Sv> messages) {
        this.messages = messages;
    }

    public void setPayloadFiles(String[] payloadFiles) {
        this.payloadFiles = payloadFiles;
    }
}
