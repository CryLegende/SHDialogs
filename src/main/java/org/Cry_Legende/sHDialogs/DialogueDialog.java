package org.Cry_Legende.sHDialogs;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit le dialogue Paper d'un {@link DialogueConfig}. Même rendu que {@link MenuDialog} : titre en première ligne du
 * texte, textes gris, boutons d'une couleur, infobulles structurées. Un bouton « url » ouvre l'adresse côté client (qui demande
 * confirmation) ; un bouton de commande renvoie au serveur la clé shdialogs:dialogue/&lt;dialogue&gt;/&lt;bouton&gt;, qui retrouve
 * la commande dans config.yml ; un bouton « fermer » et le bouton du bas portent l'action commune de fermeture
 * ({@link DialogBlindness#closeAction()}), qui rend la vue au joueur.
 */
final class DialogueDialog {

    private static final int BODY_WIDTH = 300;

    private DialogueDialog() {
    }

    static Dialog create(DialogueConfig dialogue) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Texts.lines(dialogue.title()), BODY_WIDTH));
        if (!dialogue.text().isEmpty()) {
            body.add(DialogBody.plainMessage(Texts.lines(dialogue.text()), BODY_WIDTH));
        }
        // titre vide : l'icône d'avertissement du client reste seule sur sa ligne, le titre est la première ligne du corps
        DialogBase base = DialogBase.builder(Component.empty())
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
            if (button.isUrl()) {
                b.action(DialogAction.staticAction(ClickEvent.openUrl(button.url())));
            } else if (button.close()) {
                b.action(DialogBlindness.closeAction());
            } else {
                b.action(DialogAction.customClick(button.clickKey(dialogue.clickScope()), null));
            }
            buttons.add(b.build());
        }
        MultiActionType.Builder type = DialogType.multiAction(buttons).columns(dialogue.columns());
        // bouton-fermer vide : pas de bouton en bas, Échap ferme toujours le dialogue
        if (!dialogue.closeLabel().isBlank()) {
            // action commune de fermeture : Échap la déclenche aussi, le serveur rend la vue tout de suite
            type.exitAction(ActionButton.builder(Texts.legacy(dialogue.closeLabel())).width(dialogue.buttonWidth())
                    .action(DialogBlindness.closeAction()).build());
        }
        DialogType built = type.build();
        return Dialog.create(factory -> factory.empty().base(base).type(built));
    }
}
