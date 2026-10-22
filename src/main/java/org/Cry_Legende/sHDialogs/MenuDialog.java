package org.Cry_Legende.sHDialogs;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le dialogue « Menu SkyHand » (clé skyhand:menu) que le bootstrap enregistre dans le registre des dialogues.
 * <p>
 * Comme il vit dans le registre, ses boutons ne peuvent pas être des rappels Java : un bouton de commande envoie au serveur
 * la clé shdialogs:menu/&lt;id&gt; (voir {@link SHDialogs}), qui retrouve alors la commande dans config.yml. Un bouton « url »
 * ouvre directement l'adresse côté client.
 */
final class MenuDialog {

    static final Key KEY = Key.key("skyhand", "menu");
    static final TypedKey<Dialog> TYPED_KEY = TypedKey.create(RegistryKey.DIALOG, KEY);

    private static final int BODY_WIDTH = 300;

    private MenuDialog() {
    }

    static void fill(DialogRegistryEntry.Builder builder, MenuConfig menu) {
        List<DialogBody> body = new ArrayList<>();
        if (!menu.text().isEmpty()) {
            body.add(DialogBody.plainMessage(Texts.lines(menu.text()), BODY_WIDTH));
        }
        DialogBase base = DialogBase.builder(Texts.legacy(menu.title()))
                .externalTitle(Texts.legacy(menu.buttonTitle()))
                .canCloseWithEscape(true)
                .pause(false)
                // le client referme le menu dès le clic : la commande lancée ouvre ensuite son propre écran
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();

        List<ActionButton> buttons = new ArrayList<>();
        for (MenuConfig.Button button : menu.buttons()) {
            ActionButton.Builder b = ActionButton.builder(Texts.legacy(button.label())).width(menu.buttonWidth());
            if (!button.tooltip().isEmpty()) {
                b.tooltip(Texts.lines(button.tooltip()));
            }
            b.action(button.isUrl()
                    ? DialogAction.staticAction(ClickEvent.openUrl(button.url()))
                    : DialogAction.customClick(button.clickKey(), null));
            buttons.add(b.build());
        }
        ActionButton exit = ActionButton.builder(Texts.legacy(menu.closeLabel())).width(menu.buttonWidth()).build();
        builder.base(base).type(DialogType.multiAction(buttons).columns(menu.columns()).exitAction(exit).build());
    }
}
