package br.ufu.facom.ereno.attacks.uc09.devices;

import java.util.logging.Logger;

import br.ufu.facom.ereno.attacks.uc09.creator.OrientedGrayHoleCreatorC;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.util.Labels;

/** Configurable oriented grayhole victim IED (uc09) — see OrientedGrayHoleCreatorC. */
public class OrientedGrayHoleIEDC extends ProtectionIED {

    private final ProtectionIED legitimateIED;
    private final AttackConfig attackConfig;

    public OrientedGrayHoleIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(Labels.LABELS[11]); // "oriented_grayhole"
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("OrientedGrayHoleIEDC").info(
                "Feeding oriented grayhole IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        new OrientedGrayHoleCreatorC(legitimateIED.copyMessages(), attackConfig).generate(this, numberOfMessages);
    }
}
