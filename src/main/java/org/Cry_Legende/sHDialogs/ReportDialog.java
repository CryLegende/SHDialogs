package org.Cry_Legende.sHDialogs;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit le formulaire de /signaler. Un formulaire est recréé à chaque affichage avec un jeton neuf : le jeton voyage dans le
 * bouton (champ « token ») et ne vaut qu'une fois, voir {@link ReportService}.
 */
final class ReportDialog {

    static final Key SEND = Key.key("shdialogs", "signaler/envoyer");
    static final Key CANCEL = Key.key("shdialogs", "signaler/annuler");

    // noms des champs, relus à la réponse
    static final String FIELD_TARGET = "joueur";
    static final String FIELD_MOTIF = "motif";
    static final String FIELD_DETAILS = "details";
    static final String FIELD_TOKEN = "token";

    private ReportDialog() {
    }

    /**
     * Valeurs des champs à (r)afficher.
     */
    record Form(String target, String motif, String details) {
        static final Form EMPTY = new Form("", null, "");
    }

    static Dialog build(ReportSettings s, Form form, String error, String token) {
        List<DialogBody> body = new ArrayList<>();
        if (error != null && !error.isEmpty()) {
            body.add(DialogBody.plainMessage(Texts.legacy(error), s.width()));
        }
        if (!s.text().isEmpty()) {
            body.add(DialogBody.plainMessage(Texts.lines(s.text()), s.width()));
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(FIELD_TARGET, Texts.legacy(s.targetLabel()))
                .width(s.width())
                .maxLength(s.nameMax())
                .initial(cut(form.target(), s.nameMax()))
                .build());
        inputs.add(motifInput(s, form.motif()));
        inputs.add(DialogInput.text(FIELD_DETAILS, Texts.legacy(s.detailsLabel()))
                .width(s.width())
                .maxLength(s.detailsMax())
                .multiline(TextDialogInput.MultilineOptions.create(s.detailsLines(), s.detailsHeight()))
                .initial(cut(form.details(), s.detailsMax()))
                .build());

        // le jeton accompagne la réponse du bouton, à côté des valeurs des champs
        BinaryTagHolder additions = BinaryTagHolder.binaryTagHolder("{" + FIELD_TOKEN + ":\"" + token + "\"}");
        List<ActionButton> buttons = List.of(
                button(s.sendLabel(), s.sendTooltip(), s.width() / 2, DialogAction.customClick(SEND, additions)),
                button(s.cancelLabel(), s.cancelTooltip(), s.width() / 2, DialogAction.customClick(CANCEL, additions)));

        DialogBase base = DialogBase.builder(Texts.legacy(s.title()))
                .canCloseWithEscape(true)
                // l'écran reste affiché jusqu'à la réponse du serveur : nouvel écran avec l'erreur, ou fermeture
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        return Dialog.create(factory -> factory.empty().base(base).type(DialogType.multiAction(buttons).columns(2).build()));
    }

    private static SingleOptionDialogInput motifInput(ReportSettings s, String selected) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        boolean found = s.motif(selected) != null;
        for (int i = 0; i < s.motifs().size(); i++) {
            ReportSettings.Motif motif = s.motifs().get(i);
            boolean initial = found ? motif.id().equals(selected) : i == 0;
            entries.add(SingleOptionDialogInput.OptionEntry.create(motif.id(), Texts.legacy(motif.label()), initial));
        }
        return DialogInput.singleOption(FIELD_MOTIF, Texts.legacy(s.motifLabel()), entries).width(s.width()).build();
    }

    private static ActionButton button(String label, List<String> tooltip, int width, DialogAction action) {
        ActionButton.Builder builder = ActionButton.builder(Texts.legacy(label)).width(width).action(action);
        if (!tooltip.isEmpty()) {
            builder.tooltip(Texts.lines(tooltip));
        }
        return builder.build();
    }

    /**
     * Une valeur initiale plus longue que la limite du champ rend le dialogue invalide pour le client.
     */
    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
