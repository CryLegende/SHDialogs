package org.Cry_Legende.sHDialogs;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit le dialogue Paper d'un {@link DialogueConfig}. Même rendu que {@link MenuDialog} : textes gris, boutons d'une
 * couleur, infobulles structurées. Un bouton « url » ouvre l'adresse côté client (qui demande confirmation) ; un bouton de
 * commande renvoie au serveur la clé shdialogs:dialogue/&lt;dialogue&gt;/&lt;bouton&gt;, qui retrouve la commande dans config.yml.
 */
final class DialogueDialog {

    private static final int BODY_WIDTH = 300;

    private DialogueDialog() {
    }

    static Dialog create(DialogueConfig dialogue) {
        List<DialogBody> body = new ArrayList<>();
        if (!dialogue.text().isEmpty()) {
            body.add(DialogBody.plainMessage(Texts.lines(dialogue.text()), BODY_WIDTH));
        }
        DialogBase base = DialogBase.builder(Texts.legacy(dialogue.title()))
                .canCloseWithEscape(true)
                .pause(false)
                // le client referme le dialogue dès le clic : l'adresse s'ouvre (ou la commande se lance) par-dessus le jeu
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();

        List<ActionButton> buttons = new ArrayList<>();
        for (MenuConfig.Button button : dialogue.buttons()) {
            ActionButton.Builder b = ActionButton.builder(Texts.legacy(button.label())).width(dialogue.buttonWidth());
            if (!button.tooltip().isEmpty()) {
                b.tooltip(Texts.lines(button.tooltip()));
            }
            b.action(button.isUrl()
                    ? DialogAction.staticAction(ClickEvent.openUrl(button.url()))
                    : DialogAction.customClick(button.clickKey(dialogue.clickScope()), null));
            buttons.add(b.build());
        }
        ActionButton exit = ActionButton.builder(Texts.legacy(dialogue.closeLabel())).width(dialogue.buttonWidth()).build();
        DialogType type = DialogType.multiAction(buttons).columns(dialogue.columns()).exitAction(exit).build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }
}
