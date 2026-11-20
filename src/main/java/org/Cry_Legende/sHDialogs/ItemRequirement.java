package org.Cry_Legende.sHDialogs;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Objets qu'un bouton exige dans l'inventaire du joueur (clé exige.objets) : « MATERIAL:quantité » ou « #tag_minecraft:quantité »,
 * suivi de « lore » quand l'objet peut porter un lore.
 * <p>
 * Seuls comptent les objets normaux, sans aucune personnalisation (nom, lore, enchantement, données de plugin...) : un objet
 * renommé n'est ni compté ni retiré. Une ligne finie par « lore » accepte en plus un objet dont la seule personnalisation est
 * son lore (un objet de rituel donné par un plugin avec son explication, par exemple). Le contrôle et le retrait se font dans
 * le même appel, que l'appelant lance sur le scheduler du joueur.
 */
final class ItemRequirement {

    /** Quantité maximale d'une ligne : tout l'inventaire. */
    static final int MAX_AMOUNT = 36 * 64;
    /** Composants qu'un objet normal peut porter : l'usure d'un outil ne le personnalise pas. */
    private static final Set<DataComponentType> TOLERATED = Set.of(DataComponentTypes.DAMAGE, DataComponentTypes.REPAIR_COST);
    /** Réserve (0 à 35) et main secondaire (40) ; l'armure ne compte pas. */
    private static final int[] SLOTS = slots();

    private ItemRequirement() {
    }

    /**
     * Une ligne d'exigence : un objet précis ou un tag, une quantité, et si un lore est accepté.
     */
    record Item(String text, Material material, Tag<Material> tag, int amount, boolean lore) {

        boolean matches(Material type) {
            return material != null ? material == type : tag.isTagged(type);
        }
    }

    private static int[] slots() {
        int[] slots = new int[37];
        for (int i = 0; i < 36; i++) {
            slots[i] = i;
        }
        slots[36] = 40;
        return slots;
    }

    /**
     * Lit « MATERIAL:quantité », « minecraft:material:quantité » ou « #minecraft:tag:quantité » (quantité 1 si omise), suivi
     * de « lore » si l'objet peut porter un lore. Null, après l'avoir signalé, si la ligne est invalide.
     */
    static Item parse(String raw, Consumer<String> warn) {
        String text = raw == null ? "" : raw.trim();
        boolean lore = false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" lore")) {
            lore = true;
            text = text.substring(0, text.length() - 5).trim();
        }
        String name = text;
        int amount = 1;
        int colon = text.lastIndexOf(':');
        if (colon > 0 && colon < text.length() - 1 && text.substring(colon + 1).chars().allMatch(c -> c >= '0' && c <= '9')) {
            String digits = text.substring(colon + 1);
            if (digits.length() > 5) {
                warn.accept("quantité invalide dans exige.objets : " + text);
                return null;
            }
            amount = Integer.parseInt(digits);
            name = text.substring(0, colon);
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            warn.accept("quantité invalide dans exige.objets (de 1 à " + MAX_AMOUNT + ") : " + text);
            return null;
        }
        if (name.startsWith("#")) {
            NamespacedKey key = NamespacedKey.fromString(name.substring(1).toLowerCase(Locale.ROOT));
            Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
            if (tag == null && key != null) {
                tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
            }
            if (tag == null) {
                warn.accept("tag inconnu dans exige.objets : " + text);
                return null;
            }
            return new Item(text, null, tag, amount, lore);
        }
        Material material = name.isBlank() ? null : Material.matchMaterial(name, false);
        if (material == null || material.isAir() || !material.isItem()) {
            warn.accept("objet inconnu dans exige.objets : " + text);
            return null;
        }
        return new Item(text, material, null, amount, lore);
    }

    /**
     * Vrai si l'objet est normal : rien de plus que ce que sa matière porte d'origine (seule l'usure est tolérée).
     */
    static boolean isPlain(ItemStack stack) {
        return isPlain(stack, false);
    }

    /**
     * Vrai si l'objet est normal, son lore mis à part quand lore est vrai.
     */
    static boolean isPlain(ItemStack stack, boolean lore) {
        for (DataComponentType type : stack.getDataTypes()) {
            if (stack.isDataOverridden(type) && !TOLERATED.contains(type) && !(lore && type == DataComponentTypes.LORE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retire du joueur tous ces objets, ou rien : renvoie faux, sans avoir touché à l'inventaire, s'il en manque. Plusieurs
     * lignes qui visent la même matière ne comptent pas deux fois le même objet. À appeler sur le scheduler du joueur.
     */
    static boolean take(Player player, List<Item> items) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] stacks = new ItemStack[41];
        int[] free = new int[41];
        // normal : sans rien du tout ; avecLore : sans rien d'autre qu'un lore (compté seulement par une ligne « lore »)
        boolean[] normal = new boolean[41];
        for (int slot : SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0 && isPlain(stack, true)) {
                stacks[slot] = stack;
                free[slot] = stack.getAmount();
                normal[slot] = isPlain(stack, false);
            }
        }
        int[] taken = new int[41];
        for (Item item : items) {
            int missing = item.amount();
            for (int slot : SLOTS) {
                if (missing == 0) {
                    break;
                }
                if (free[slot] > taken[slot] && (normal[slot] || item.lore()) && item.matches(stacks[slot].getType())) {
                    int part = Math.min(missing, free[slot] - taken[slot]);
                    taken[slot] += part;
                    missing -= part;
                }
            }
            if (missing > 0) {
                return false;
            }
        }
        for (int slot : SLOTS) {
            if (taken[slot] > 0) {
                int rest = free[slot] - taken[slot];
                if (rest <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    ItemStack reduced = stacks[slot].clone();
                    reduced.setAmount(rest);
                    inventory.setItem(slot, reduced);
                }
            }
        }
        return true;
    }
}
