/*
 *  * Copyright © Wynntils - 2019.
 */

package com.wynntils.modules.utilities.overlays.inventories;

import com.wynntils.core.events.custom.GuiOverlapEvent;
import com.wynntils.core.framework.enums.SelectedIdentification;
import com.wynntils.core.framework.enums.SpellType;
import com.wynntils.core.framework.interfaces.Listener;
import com.wynntils.core.utils.ItemUtils;
import com.wynntils.core.utils.StringUtils;
import com.wynntils.core.utils.helpers.RainbowText;
import com.wynntils.core.utils.reference.EmeraldSymbols;
import com.wynntils.modules.utilities.configs.UtilitiesConfig;
import com.wynntils.webapi.WebManager;
import com.wynntils.webapi.profiles.item.IdentificationOrderer;
import com.wynntils.webapi.profiles.item.ItemGuessProfile;
import com.wynntils.webapi.profiles.item.ItemProfile;
import com.wynntils.webapi.profiles.item.enums.MajorIdentification;
import com.wynntils.webapi.profiles.item.objects.IdentificationContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.math.Fraction;
import org.lwjgl.input.Keyboard;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static net.minecraft.util.text.TextFormatting.*;

public class ItemIdentificationOverlay implements Listener {

    private final static Pattern ITEM_QUALITY = Pattern.compile("(?<Quality>Normal|Unique|Rare|Legendary|Fabled|Mythic|Set) Item(?: \\[(?<Rolls>\\d+)])?(?: \\[[0-9,]+" + EmeraldSymbols.E + "])?");
    private final static Pattern ID_PATTERN = Pattern.compile("(^\\+?(?<Value>-?\\d+)(?: to \\+?(?<UpperValue>-?\\d+))?(?<Suffix>%|/\\ds| tier)?\\*{0,3} (?<ID>[a-zA-Z 0-9]+))");
    private final static Pattern MARKET_PRICE = Pattern.compile(" - (?<Quantity>\\d x )?(?<Value>(?:,?\\d{1,3})+)" + EmeraldSymbols.E);

    public static final DecimalFormat decimalFormat = new DecimalFormat("#,###,###,###");

    @SubscribeEvent
    public void onChest(GuiOverlapEvent.ChestOverlap.DrawScreen e) {
        if (e.getGui().getSlotUnderMouse() == null || !e.getGui().getSlotUnderMouse().getHasStack()) return;

        replaceLore(e.getGui().getSlotUnderMouse().getStack());
    }

    @SubscribeEvent
    public void onInventory(GuiOverlapEvent.InventoryOverlap.DrawScreen e) {
        if (e.getGui().getSlotUnderMouse() == null || !e.getGui().getSlotUnderMouse().getHasStack()) return;

        replaceLore(e.getGui().getSlotUnderMouse().getStack());
    }

    @SubscribeEvent
    public void onHorse(GuiOverlapEvent.HorseOverlap.DrawScreen e) {
        if (e.getGui().getSlotUnderMouse() == null || !e.getGui().getSlotUnderMouse().getHasStack()) return;

        replaceLore(e.getGui().getSlotUnderMouse().getStack());
    }

    private static void replaceLore(ItemStack stack) {
        if (!stack.hasDisplayName() || !stack.hasTagCompound()) return;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt.hasKey("wynntilsIgnore")) return;

        String itemName = StringUtils.normalizeBadString(getTextWithoutFormattingCodes(stack.getDisplayName()));

        // Check if unidentified item.
        if (itemName.contains("Unidentified")) {
            // Add possible identifications
            nbt.setBoolean("wynntilsIgnore", true);
            addItemGuesses(stack);
            return;
        }

        // check if item is a valid item if not ignore it
        if (!nbt.hasKey("wynntils") && WebManager.getItems().get(itemName) == null) {
            nbt.setBoolean("wynntilsIgnore", true);
            return;
        }

        NBTTagCompound wynntils = generateData(stack);

        // perfect name
        if (wynntils.hasKey("isPerfect")) {
            stack.setStackDisplayName(RainbowText.makeRainbow("Perfect " + wynntils.getString("originName"), false));
        }

        // update only if should update, this is decided on generateDate
        if (!wynntils.getBoolean("shouldUpdate")) return;

        wynntils.setBoolean("shouldUpdate", false);

        // objects
        SelectedIdentification idType = SelectedIdentification.valueOf(wynntils.getString("currentType"));
        ItemProfile item = WebManager.getItems().get(wynntils.getString("originName"));

        List<String> newLore = new ArrayList<>();

        // generating id lores
        HashMap<String, String> idLore = new HashMap<>();

        double cumRelative = 0;
        int nonFixedIDs = 0;
        boolean hadAnyIDs = false;
        boolean hasInvalidIDs = false;

        if (wynntils.hasKey("ids")) {
            NBTTagCompound ids = wynntils.getCompoundTag("ids");
            for (String idName : ids.getKeySet()) {
                if (!item.getStatuses().containsKey(idName)) continue;
                hadAnyIDs = true;

                IdentificationContainer id = item.getStatuses().get(idName);
                int currentValue = ids.getInteger(idName);

                idLore.put(idName, getIDLore(id, currentValue, idName, idType));

                if (!id.isFixed() && id.getBaseValue() != 0) {
                    double relativeValue = id.getRelativeValue(currentValue);
                    if (relativeValue > 1 || relativeValue < 0) {
                        hasInvalidIDs = true;
                        relativeValue = MathHelper.clamp(relativeValue, 0, 1);
                    }
                    cumRelative += relativeValue;
                    ++nonFixedIDs;
                }
            }
        }


        // copying some parts of the old lore (stops on ids, powder or quality)
        boolean ignoreNext = false;
        for (String oldLore : ItemUtils.getLore(stack)) {
            if (ignoreNext) {
                ignoreNext = false;
                continue;
            }

            String rawLore = getTextWithoutFormattingCodes(oldLore);
            // market stuff
            if (rawLore.contains("Price:")) {
                ignoreNext = true;

                NBTTagCompound market = wynntils.getCompoundTag("marketInfo");

                newLore.add(GOLD + "Price:");
                String mLore = GOLD + " - " + GRAY;
                if (market.hasKey("quantity")) {
                    mLore += market.getInteger("quantity") + " x ";
                }

                int[] money = calculateMoneyAmount(market.getInteger("price"));
                String price = "";
                if (money[3] != 0) price += money[3] + "stx ";
                if (money[2] != 0) price += money[2] + EmeraldSymbols.LE + " ";
                if (money[1] != 0) price += money[1] + EmeraldSymbols.BLOCKS + " ";
                if (money[0] != 0) price += money[0] + EmeraldSymbols.EMERALDS + " ";

                price = price.substring(0, price.length() - 1);

                mLore += "" + WHITE + decimalFormat.format(market.getInteger("price")) + EmeraldSymbols.EMERALDS;
                mLore += DARK_GRAY + " (" + price + ")";

                newLore.add(mLore);
                continue;
            }

            // stop on id if the item has ids
            if (idLore.size() > 0) {
                if (rawLore.startsWith("+") || rawLore.startsWith("-")) break;

                newLore.add(oldLore);
                continue;
            }

            // stop on powders if the item has powders
            if (wynntils.hasKey("powderSlots") && oldLore.contains("] Powder Slots")) {
                break;
            }

            // stop on quality if there's no other
            Matcher m = ITEM_QUALITY.matcher(rawLore);
            if (m.matches()) break;

            newLore.add(oldLore);
        }

        // add id lores
        if (idLore.size() > 0) {
            newLore.addAll(IdentificationOrderer.INSTANCE.order(idLore,
                    UtilitiesConfig.INSTANCE.addItemIdentificationSpacing));

            newLore.add(" ");
        }

        // major ids
        if (item.getMajorIds().size() > 0) {
            for (MajorIdentification majorId : item.getMajorIds()) {
                Stream.of(StringUtils.wrapTextBySize(majorId.asLore(), 150)).forEach(c -> newLore.add(DARK_AQUA + c));
            }
            newLore.add(" ");
        }

        // powder lore
        if (wynntils.hasKey("powderSlots")) newLore.add(wynntils.getString("powderSlots"));

        // quality lore
        String quality = item.getTier().asLore();

        // adds reroll amount if the item is not identified
        if (!item.isIdentified()) {
            int rollAmount = (wynntils.hasKey("rerollAmount") ? wynntils.getInteger("rerollAmount") : 0) + 1;
            if (rollAmount != 0) quality += " [" + rollAmount + "] ";

            quality +=
                    GREEN + "["
                            + decimalFormat.format(item.getTier().getRerollPrice(item.getRequirements().getLevel(), rollAmount))
                            + EmeraldSymbols.E + "]";
        }

        newLore.add(quality);
        if (item.getRestriction() != null) newLore.add(RED + "Untradable Item");

        //merchant & dungeon purchase offers
        if (wynntils.hasKey("purchaseInfo")) {
            newLore.add(" ");
            newLore.add(GOLD + "Price:");

            NBTTagList purchaseInfo = wynntils.getTagList("purchaseInfo", 8 /* means NBTTagString */);
            for (NBTBase nbtBase : purchaseInfo) {
                newLore.add(((NBTTagString) nbtBase).getString());
            }
        }

        // item lore
        if (!item.getLore().isEmpty()) {
            if(wynntils.hasKey("purchaseInfo")) newLore.add(" ");

            Stream.of(StringUtils.wrapTextBySize(item.getLore(), 150)).forEach(c -> newLore.add(DARK_GRAY + c));
        }

        // special displayname
        String specialDisplay = "";
        if (hadAnyIDs && nonFixedIDs != 0) {
            if (idType == SelectedIdentification.PERCENTAGES) {
                double mean = (cumRelative * 100) / nonFixedIDs;

                // perfect item
                if (mean >= 100 && !item.isIdentified()) wynntils.setBoolean("isPerfect", true);

                if (mean >= 97d) specialDisplay += AQUA;
                else if (mean >= 80d) specialDisplay += GREEN;
                else if (mean >= 30) specialDisplay += YELLOW;
                else specialDisplay += RED;

                specialDisplay += " [" + (hasInvalidIDs ? "~" : Integer.toString((int) mean)) + "%]";
            }
        }

        stack.setStackDisplayName(item.getTier().getColor() + item.getDisplayName() + specialDisplay);

        // applying lore
        NBTTagCompound compound = nbt.getCompoundTag("display");
        NBTTagList list = new NBTTagList();

        newLore.forEach(c -> list.appendTag(new NBTTagString(c)));

        compound.setTag("Lore", list);

        nbt.setTag("wynntils", wynntils);
        nbt.setTag("display", compound);
    }

    private static void addItemGuesses(ItemStack stack) {
        String name = StringUtils.normalizeBadString(stack.getDisplayName());
        String itemType = getTextWithoutFormattingCodes(name).split(" ", 3)[1];
        String level = null;

        List<String> lore = ItemUtils.getLore(stack);

        for (String aLore : lore) {
            if (aLore.contains("Lv. Range")) {
                level = getTextWithoutFormattingCodes(aLore).replace("- Lv. Range: ", "");
                break;
            }
        }

        if (itemType == null || level == null) return;

        ItemGuessProfile igp = WebManager.getItemGuesses().get(level);
        if (igp == null) return;

        HashMap<String, String> rarityMap = igp.getItems().get(itemType);
        if (rarityMap == null) return;

        String items = null;
        String color = null;

        if (name.startsWith(AQUA.toString())) {
            items = rarityMap.get("Legendary");
            color = AQUA.toString();
        } else if (name.startsWith(LIGHT_PURPLE.toString())) {
            items = rarityMap.get("Rare");
            color = LIGHT_PURPLE.toString();
        } else if (name.startsWith(YELLOW.toString())) {
            items = rarityMap.get("Unique");
            color = YELLOW.toString();
        } else if (name.startsWith(DARK_PURPLE.toString())) {
            items = rarityMap.get("Mythic");
            color = DARK_PURPLE.toString();
        } else if (name.startsWith(RED.toString())) {
            items = rarityMap.get("Fabled");
            color = RED.toString();
        } else if (name.startsWith(GREEN.toString())) {
            items = rarityMap.get("Set");
            color = GREEN.toString();
        }

        if (items == null) return;

        ItemUtils.getLoreTag(stack).appendTag(new NBTTagString(GREEN + "- " + GRAY + "Possibilities: " + color + items));
    }

    private static String getIDLore(IdentificationContainer id, int currentValue, String idName, SelectedIdentification idType) {
        String lore = (currentValue < 0 ? RED.toString() : currentValue > 0 ? GREEN + "+" : GRAY.toString()) + currentValue + id.getType().getInGame() + " " + GRAY + id.getAsLongName(idName);
        int baseValue = id.getBaseValue();
        if (id.isFixed() || baseValue == 0) return lore;

        int min = id.getMin();
        int max = id.getMax();

        String suffix;

        switch (idType) {
            case MIN_MAX:  // [min, max]
                if (baseValue < 0) suffix = DARK_RED + "[" + RED + "" + min + ", " + max + DARK_RED + "]";
                else suffix = DARK_GREEN + "[" + GREEN + "" + min + ", " + max + DARK_GREEN + "]";
                break;

            case UPGRADE_CHANCES:  // ⇧% ⇩% ★%
                IdentificationContainer.ReidentificationChances chances = id.getChances(currentValue);
                double increasePct = chances.increase.getNumerator() * 100D / chances.increase.getDenominator();
                double decreasePct = chances.decrease.getNumerator() * 100D / chances.decrease.getDenominator();
                double perfectPct = id.getPerfectChance().multiplyBy(Fraction.getFraction(100, 1)).doubleValue();
                suffix = String.format(
                    AQUA + "\u21E7%.0f%% " + RED + "\u21E9%.0f%% " + GOLD + "\u2605%.1f%%",
                    increasePct, decreasePct, perfectPct
                );
                break;

            default:  // [id%]
                double value = id.getRelativeValue(currentValue) * 100;
                if (value >= 97d) suffix = AQUA.toString();
                else if (value >= 80d) suffix = GREEN.toString();
                else if (value >= 30) suffix = YELLOW.toString();
                else suffix = RED.toString();
                suffix += "[" + (value > 100 || value < 0 ? "~" : Integer.toString((int) value)) + "%]";
        }

        return lore + " " + suffix;
    }

    private static NBTTagCompound generateData(ItemStack stack) {
        SelectedIdentification idType;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) idType = SelectedIdentification.MIN_MAX;
        else if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) idType = SelectedIdentification.UPGRADE_CHANCES;
        else idType = SelectedIdentification.PERCENTAGES;

        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("wynntils")) {
            NBTTagCompound compound = stack.getTagCompound().getCompoundTag("wynntils");

            // check for updates
            if (!compound.getString("currentType").equals(idType.toString())) {
                compound.setBoolean("shouldUpdate", true);
                compound.setString("currentType", idType.toString());

                stack.getTagCompound().setTag("wynntils", compound);
            }

            return compound;
        }

        NBTTagCompound mainTag = new NBTTagCompound();

        {  // main data
            mainTag.setString("originName", StringUtils.normalizeBadString(getTextWithoutFormattingCodes(stack.getDisplayName())));  // this replace allow market items to be scanned
            mainTag.setString("currentType", idType.toString());
            mainTag.setBoolean("shouldUpdate", true);
        }

        NBTTagCompound idTag = new NBTTagCompound();
        NBTTagList purchaseInfo = new NBTTagList();
        {  // lore data
            for (String loreLine : ItemUtils.getLore(stack)) {
                String lColor = getTextWithoutFormattingCodes(loreLine);

                // ids
                { Matcher idMatcher = ID_PATTERN.matcher(lColor);
                    if (idMatcher.find()) {
                        String idName = idMatcher.group("ID");
                        boolean isRaw = idMatcher.group("Suffix") == null;

                        SpellType spell = SpellType.getSpell(idName);
                        if (spell != null) idName = idName.replaceAll(spell.getRegex().pattern(), spell.getShortName());

                        String shortIdName = toShortIdName(idName, isRaw);
                        idTag.setInteger(shortIdName, Integer.parseInt(idMatcher.group("Value")));
                        continue;
                    }
                }

                // rerolls
                { Matcher rerollMatcher = ITEM_QUALITY.matcher(lColor);
                    if (rerollMatcher.find()) {
                        if (rerollMatcher.group("Rolls") == null) continue;

                        mainTag.setInteger("rerollAmount", Integer.parseInt(rerollMatcher.group("Rolls")));
                        continue;
                    }
                }

                // powders
                if (lColor.contains("] Powder Slots")) mainTag.setString("powderSlots", loreLine);

                //dungeon and merchant prices
                if (lColor.startsWith(" - ✔") || lColor.startsWith(" - ✖")) {
                    purchaseInfo.appendTag(new NBTTagString(loreLine));
                    continue;
                }

                // market
                { Matcher market = MARKET_PRICE.matcher(lColor);
                    if (!market.find()) continue;

                    NBTTagCompound marketTag = new NBTTagCompound();

                    if (market.group("Quantity") != null)
                        marketTag.setInteger("quantity", Integer.parseInt(market.group("Quantity").replace(",", "")));

                    marketTag.setInteger("price", Integer.parseInt(market.group("Value").replace(",", "")));

                    mainTag.setTag("marketInfo", marketTag);
                }

            }

            if (idTag.getSize() > 0) mainTag.setTag("ids", idTag);
            if(purchaseInfo.tagCount() > 0) mainTag.setTag("purchaseInfo", purchaseInfo);
        }

        // update compound
        NBTTagCompound stackCompound = stack.getTagCompound();
        stackCompound.setTag("wynntils", mainTag);

        stack.setTagCompound(stackCompound);

        return mainTag;
    }

    private static String toShortIdName(String longIdName, boolean raw) {
        String[] splitName = longIdName.split(" ");
        StringBuilder result = new StringBuilder(raw ? "raw" : "");
        for (String r : splitName) {
            if (r.startsWith("[")) continue;  // ignore ids
            result.append(Character.toUpperCase(r.charAt(0))).append(r.substring(1).toLowerCase(Locale.ROOT));
        }

        if (result.length() == 0) return "";
        result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
        return result.toString();
    }

    /**
     * Calculates the amount of emeralds, emerald blocks and liquid emeralds in the player inventory
     *
     * @param money the amount of money to process
     * @return an array with the values in the respective order of emeralds[0], emerald blocks[1], liquid emeralds[2], stx[3]
     */
    private static int[] calculateMoneyAmount(int money) {
        return new int[] { money % 64, (money / 64) % 64, (money / 4096) % 64, money / (64 * 4096) };
    }

}
