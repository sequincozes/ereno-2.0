package br.ufu.facom.ereno.attacks.uc11.devices;

import br.ufu.facom.ereno.attacks.uc11.creator.ProgrammableCreatorC;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.util.Labels;

import java.util.logging.Logger;

/** Configurable programmable attack IED (uc11) — see ProgrammableCreatorC. */
public class ProgrammableIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public ProgrammableIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(Labels.LABELS[10]); // "programmable"
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("ProgrammableIEDC").info(
                "Feeding programmable IEDC with "
                        + legitimateIED.copyMessages().size() + " legitimate messages");
        ProgrammableCreatorC creator =
                new ProgrammableCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfMessages);
    }
}
