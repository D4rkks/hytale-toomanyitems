package dev.lass.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.lass.Main;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.List;
import java.lang.reflect.Method;

public class TooManyItemsGui extends InteractiveCustomUIPage<SearchGuiData> {

    public enum ViewMode {
        LIST,
        DETAIL
    }

    public static final String LAYOUT = "Pages/TooManyItems_Gui.ui";
    public static final String DETAIL_LAYOUT = "Pages/TooManyItems_ItemDetail.ui";

    private String searchQuery = "";
    private String selectedBench = "all";
    private ViewMode currentView = ViewMode.LIST;
    private String selectedItemId = null;
    private String pinnedItemId = null;
    protected final PlayerRef playerRef;
    protected final Player player;

    public TooManyItemsGui(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime,
            String defaultSearchQuery) {
        super(playerRef, lifetime, SearchGuiData.CODEC);
        this.player = player;
        this.playerRef = playerRef;
        this.searchQuery = defaultSearchQuery;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store) {

        if (currentView == ViewMode.DETAIL) {
            this.buildDetailView(ref, cmd, evt, store);
        } else {
            cmd.append(LAYOUT);
            cmd.set("#SearchInput.Value", this.searchQuery);

            evt.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    "#SearchInput",
                    new EventData().append("@SearchQuery", "#SearchInput.Value"),
                    false);

            this.buildList(ref, cmd, evt, store);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull SearchGuiData data) {
        super.handleDataEvent(ref, store, data);

        boolean refresh = false;

        if (data.backToList != null && "true".equals(data.backToList)) {
            this.currentView = ViewMode.LIST;
            this.selectedItemId = null;
            this.rebuild();
            return;
        }

        if (data.pin != null) {
            try {
                if (data.pin.equals(pinnedItemId)) {
                    TooManyItemsHud hud = new TooManyItemsHud(player, playerRef, ref, store, "HIDDEN");
                    hud.show();
                    pinnedItemId = null;
                    this.rebuild();
                } else {
                    TooManyItemsHud hud = new TooManyItemsHud(player, playerRef, ref, store, data.pin);
                    hud.show();
                    pinnedItemId = data.pin;
                    this.rebuild();
                }
            } catch (Exception e) {
                System.out.println("TMI ERROR opening HUD: " + e.getMessage());
            }
            return;
        }

        if (data.item != null) {
            this.selectedItemId = data.item;
            this.currentView = ViewMode.DETAIL;
            this.rebuild();
            return;
        }

        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            refresh = true;
        }

        if (data.selectedBench != null) {
            this.selectedBench = data.selectedBench;
            refresh = true;
        }

        if (refresh) {
            UICommandBuilder cmdUpdate = new UICommandBuilder();
            UIEventBuilder evtUpdate = new UIEventBuilder();
            this.buildList(ref, cmdUpdate, evtUpdate, store);
            this.sendUpdate(cmdUpdate, evtUpdate, false);
        }
    }

    private void buildDetailView(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {

        cmd.append(DETAIL_LAYOUT);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BackButton",
                new EventData().append("BackToList", "true"),
                false);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PinButton",
                new EventData().append("Pin", selectedItemId),
                false);

        if (selectedItemId != null && selectedItemId.equals(pinnedItemId)) {
            cmd.set("#PinText.Text", "Unpin");
        } else {
            cmd.set("#PinText.Text", "Pin");
        }

        if (selectedItemId == null || !Main.ITEMS.containsKey(selectedItemId)) {
            return;
        }

        Item item = Main.ITEMS.get(selectedItemId);

        cmd.set("#SelectedItemIcon.ItemId", selectedItemId);
        cmd.set("#SelectedItemName.TextSpans", Message.translation(item.getTranslationKey()));

        if (Main.recipes.containsKey(selectedItemId)) {
            List<CraftingRecipe> recipeList = Main.recipes.get(selectedItemId);
            if (!recipeList.isEmpty()) {
                CraftingRecipe recipe = recipeList.get(0);

                if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
                    String benchId = recipe.getBenchRequirement()[0].id;
                    String normalized = normalize(benchId);

                    if (benchId != null) {
                        cmd.set("#WorkbenchIcon.ItemId", getBenchIcon(normalized));
                        cmd.set("#WorkbenchName.TextSpans", Message.raw(format(normalized)));
                    }
                }

                cmd.clear("#IngredientsGrid");
                int ingredientIdx = 0;
                for (MaterialQuantity input : recipe.getInput()) {
                    cmd.append("#IngredientsGrid", "Pages/TooManyItems_IngredientItem.ui");
                    String ingredientEl = "#IngredientsGrid[" + ingredientIdx + "]";

                    String ingredientId = input.getItemId();
                    String resourceTypeId = getTagFromQuantity(input);

                    if (ingredientId != null) {
                        cmd.set(ingredientEl + " #IngredientIcon.ItemId", ingredientId);
                    } else if (resourceTypeId != null) {
                        cmd.set(ingredientEl + " #IngredientIcon.ItemId", resolveIconForTag(resourceTypeId));
                    }

                    MessageHelper.ML quantityMsg = MessageHelper.multiLine();
                    quantityMsg.append(Message.raw(input.getQuantity() + "x "));

                    if (ingredientId != null) {
                        if (Main.ITEMS.containsKey(ingredientId)) {
                            quantityMsg.append(Message.translation(Main.ITEMS.get(ingredientId).getTranslationKey()));
                        } else {
                            quantityMsg.append(Message.raw(ingredientId));
                        }
                    } else if (resourceTypeId != null) {
                        quantityMsg.append(Message.raw(formatTagName(resourceTypeId)));
                    } else {
                        quantityMsg.append(Message.raw("Unknown"));
                    }
                    cmd.set(ingredientEl + " #IngredientQuantity.TextSpans", quantityMsg.build());

                    MessageHelper.ML tooltip = MessageHelper.multiLine();
                    Message nameMsg;
                    if (ingredientId != null && Main.ITEMS.containsKey(ingredientId)) {
                        nameMsg = Message.translation(Main.ITEMS.get(ingredientId).getTranslationKey());
                    } else if (resourceTypeId != null) {
                        nameMsg = Message.raw(formatTagName(resourceTypeId));
                    } else {
                        nameMsg = Message.raw("Unknown Ingredient");
                    }

                    tooltip.append(Message.raw(input.getQuantity() + "x ").bold(true))
                            .append(nameMsg.bold(true))
                            .nl();
                    cmd.set(ingredientEl + ".TooltipTextSpans", tooltip.build());

                    ingredientIdx++;
                }
            }
        }
    }

    private void buildList(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt,
            @Nonnull ComponentAccessor<EntityStore> access) {

        HashMap<String, Item> itemList = new HashMap<>(Main.ITEMS);
        Map<String, List<ItemData>> groups = new LinkedHashMap<>();
        Set<String> benchesFound = new TreeSet<>();
        Map<String, String> benchIcons = new HashMap<>();

        for (Map.Entry<String, Item> entry : itemList.entrySet()) {
            Item item = entry.getValue();
            String id = entry.getKey();
            if (item == null)
                continue;

            String localized = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getTranslationKey());
            String name = localized != null ? localized : item.getTranslationKey();
            if (!searchQuery.isEmpty() && !name.toLowerCase().contains(searchQuery)
                    && !id.toLowerCase().contains(searchQuery))
                continue;

            if (Main.recipeRegistries.containsKey(id)) {
                Map<String, BenchRequirement[]> recipes = Main.recipeRegistries.get(id);
                Set<String> addedBenches = new HashSet<>();

                for (BenchRequirement[] reqs : recipes.values()) {
                    if (reqs.length > 0) {
                        for (BenchRequirement req : reqs) {
                            String rawBench = req.id;
                            int tier = req.requiredTierLevel;
                            String key = normalize(rawBench);
                            if (key == null)
                                continue;

                            if (addedBenches.contains(key))
                                continue;
                            addedBenches.add(key);

                            benchesFound.add(key);
                            benchIcons.putIfAbsent(key, id);

                            if (!this.selectedBench.equals("all") && !this.selectedBench.equals(key))
                                continue;

                            groups.computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(new ItemData(id, item, tier, rawBench));
                        }
                    }
                }
            } else {
                String key = "chest";
                benchesFound.add(key);
                if (this.selectedBench.equals("all") || this.selectedBench.equals(key)) {
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(new ItemData(id, item, 0, "chest"));
                }
            }
        }

        cmd.clear("#SidebarScroll");
        int index = 0;
        appendSidebar(cmd, evt, "all", "All Items", "Barrel", index++);

        List<String> sortedBenches = new ArrayList<>(benchesFound);
        sortedBenches.remove("chest");
        sortedBenches.remove("bench");
        sortedBenches.remove("todo");
        sortedBenches.remove("");

        sortedBenches.sort((a, b) -> {
            String aName = format(a).toLowerCase();
            String bName = format(b).toLowerCase();

            if (a.equals("inventory") || a.equals("fieldcraft"))
                return 1;
            if (b.equals("inventory") || b.equals("fieldcraft"))
                return -1;
            if (a.equalsIgnoreCase("workbench"))
                return 1;
            if (b.equalsIgnoreCase("workbench"))
                return -1;

            boolean aHasWorkbench = aName.contains("workbench");
            boolean bHasWorkbench = bName.contains("workbench");
            boolean aHasBench = aName.contains("bench") && !aHasWorkbench;
            boolean bHasBench = bName.contains("bench") && !bHasWorkbench;

            if (aHasWorkbench && !bHasWorkbench)
                return -1;
            if (!aHasWorkbench && bHasWorkbench)
                return 1;
            if (aHasWorkbench && bHasWorkbench) {
                return bName.length() - aName.length();
            }

            if (aHasBench && !bHasBench)
                return -1;
            if (!aHasBench && bHasBench)
                return 1;
            if (aHasBench && bHasBench) {
                return bName.length() - aName.length();
            }

            return bName.length() - aName.length();
        });

        for (String key : sortedBenches) {
            String icon = getBenchIcon(key);
            appendSidebar(cmd, evt, key, format(key), icon, index++);
        }

        cmd.clear("#SubcommandCards");
        int cardIdx = 0;
        List<String> sortedGroups = new ArrayList<>(groups.keySet());
        sortedGroups.sort(Comparator.naturalOrder());

        for (String key : sortedGroups) {
            List<ItemData> items = groups.get(key);
            cmd.append("#SubcommandCards", "Pages/TooManyItems_CategoryHeader.ui");
            String head = "#SubcommandCards[" + cardIdx++ + "]";
            cmd.set(head + " #BenchName.Text", format(key));
            cmd.set(head + " #BenchIcon.ItemId", items.get(0).rawBench);

            int currentTier = -1;
            int col = 0;
            int rowIdx = -1;

            items.sort(Comparator.comparingInt((ItemData d) -> d.tier)
                    .thenComparing(Comparator.comparingInt((ItemData d) -> d.materialPriority).reversed())
                    .thenComparingInt(d -> d.category)
                    .thenComparing(d -> d.id));

            for (ItemData data : items) {
                if (data.tier != currentTier) {
                    currentTier = data.tier;
                    col = 0;
                    cmd.append("#SubcommandCards", "Pages/TooManyItems_TierHeader.ui");
                    String tierHead = "#SubcommandCards[" + cardIdx++ + "]";
                    cmd.set(tierHead + " #TierName.Text", currentTier == 0 ? "Starter" : "Tier " + currentTier);
                }

                if (col == 0 || col >= 5) {
                    col = 0;
                    cmd.append("#SubcommandCards", "Pages/TooManyItems_ItemRow.ui");
                    rowIdx = cardIdx++;
                }

                String row = "#SubcommandCards[" + rowIdx + "]";
                cmd.append(row, "Pages/TooManyItems_SearchItemIcon.ui");
                String itemEl = row + "[" + col + "]";

                cmd.set(itemEl + " #ItemIcon.ItemId", data.id);
                cmd.set(itemEl + " #ItemName.TextSpans", Message.translation(data.item.getTranslationKey()));

                MessageHelper.ML tooltip = MessageHelper.multiLine();
                tooltip.append(Message.translation(data.item.getTranslationKey()).bold(true)).nl();
                tooltip.append(Message.raw("ID: " + data.id).color("#888888")).nl();

                if (Main.recipes.containsKey(data.id)) {
                    List<CraftingRecipe> recipeList = Main.recipes.get(data.id);
                    if (recipeList != null && !recipeList.isEmpty()) {
                        tooltip.separator();
                        tooltip.append(Message.raw("Requires:").color("#93844c").bold(true)).nl();

                        int count = 0;
                        for (CraftingRecipe r : recipeList) {
                            if (count >= 2) {
                                tooltip.append(Message.raw("... and more")).nl();
                                break;
                            }
                            count++;

                            for (MaterialQuantity input : r.getInput()) {
                                String iId = input.getItemId();
                                if (iId != null && Main.ITEMS.containsKey(iId)) {
                                    tooltip.append(Message.raw(" - " + input.getQuantity() + "x "))
                                            .append(Message
                                                    .translation(Main.ITEMS.get(iId).getTranslationKey()))
                                            .nl();
                                } else {
                                    String tag = getTagFromQuantity(input);
                                    if (tag != null) {
                                        tooltip.append(
                                                Message.raw(" - " + input.getQuantity() + "x " + formatTagName(tag)))
                                                .nl();
                                    } else {
                                        tooltip.append(Message.raw(
                                                " - " + input.getQuantity() + "x " + (iId != null ? iId : "Unknown")))
                                                .nl();
                                    }
                                }
                            }
                            if (count < recipeList.size()) {
                                tooltip.nl();
                            }
                        }
                    }
                }

                cmd.set(itemEl + ".TooltipTextSpans", tooltip.build());

                evt.addEventBinding(CustomUIEventBindingType.Activating, itemEl,
                        new EventData().append("Item", data.id), false);

                col++;
            }
        }
    }

    private void appendSidebar(UICommandBuilder cmd, UIEventBuilder evt, String id, String name, String icon, int idx) {
        cmd.append("#SidebarScroll", "Pages/TooManyItems_BenchSidebarIcon.ui");
        String sel = "#SidebarScroll[" + idx + "]";
        cmd.set(sel + " #SidebarLabel.Text", name);
        cmd.set(sel + " #SidebarIcon.ItemId", icon);

        evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
                new EventData().append("SelectedBench", id), false);
    }

    private String normalize(String raw) {
        if (raw == null)
            return "chest";
        String n = raw.toLowerCase().replace("_", "").replace("bench", "");
        if (n.equals("alchemy"))
            return "alchemy";
        if (n.equals("arcane"))
            return "arcane";
        if (n.equals("armor") || n.equals("armory"))
            return "armor";
        if (n.equals("builders") || n.equals("builder") || n.equals("architect") || n.equals("architects"))
            return "builder";
        if (n.equals("campfire"))
            return "campfire";
        if (n.equals("cooking"))
            return "cooking";
        if (n.equals("farming"))
            return "farming";
        if (n.equals("furniture") || n.equals("furnituremisc"))
            return "furniture";
        if (n.equals("salvage"))
            return "salvage";
        if (n.equals("tannery"))
            return "tannery";
        if (n.equals("weapon"))
            return "weapon";
        if (n.equals("loom") || n.equals("weaver"))
            return null;
        if (n.equals("work"))
            return "workbench";
        if (n.equals("fieldcraft"))
            return "inventory";
        return n.isEmpty() ? "bench" : n;
    }

    private String format(String key) {
        switch (key) {
            case "alchemy":
                return "Alchemist's Workbench";
            case "arcane":
                return "Arcanist's Workbench";
            case "armor":
                return "Armorer's Workbench";
            case "builder":
                return "Builder's Workbench";
            case "campfire":
                return "Campfire";
            case "cooking":
                return "Chef's Stove";
            case "farming":
                return "Farmer's Workbench";
            case "furniture":
                return "Furniture Bench";
            case "salvage":
                return "Salvager's Bench";
            case "tannery":
                return "Tanning Rack";
            case "weapon":
                return "Blacksmith's Anvil";
            case "workbench":
                return "Workbench";
            case "furnace":
                return "Furnace";
            case "inventory":
                return "Inventory";
            case "chest":
                return "General";
            default:
                return key.substring(0, 1).toUpperCase() + key.substring(1) + " Bench";
        }
    }

    private MessageHelper.ML addTooltipLine(MessageHelper.ML tooltip, String key, int value) {
        return this.addTooltipLine(tooltip, key, value + "");
    }

    private MessageHelper.ML addTooltipLine(MessageHelper.ML tooltip, String key, double value) {
        return this.addTooltipLine(tooltip, key, value + "");
    }

    private MessageHelper.ML addTooltipLine(MessageHelper.ML tooltip, String key, String value) {
        return tooltip.append(Message.raw(key).color("#93844c").bold(true)).append(Message.raw(value)).nl();
    }

    private MessageHelper.ML addTooltipLine(MessageHelper.ML tooltip, String key, Message value) {
        return tooltip.append(Message.raw(key).color("#93844c").bold(true)).append(value).nl();
    }

    private Message formatBoolean(boolean value) {
        return value ? Message.raw("Yes").color(Color.GREEN) : Message.raw("No").color(Color.RED);
    }

    private String getBenchIcon(String key) {
        String keyLower = key.toLowerCase();

        for (String itemId : Main.ITEMS.keySet()) {
            String itemLower = itemId.toLowerCase();
            if (itemLower.startsWith("bench_") && itemLower.contains(keyLower)) {
                return itemId;
            }
        }

        for (String itemId : Main.ITEMS.keySet()) {
            String itemLower = itemId.toLowerCase();
            if (itemLower.contains(keyLower) &&
                    (itemLower.contains("bench") || itemLower.contains("workbench") ||
                            itemLower.contains("stove") || itemLower.contains("furnace") ||
                            itemLower.contains("campfire") || itemLower.contains("anvil") ||
                            itemLower.contains("rack"))) {
                return itemId;
            }
        }

        switch (key) {
            case "all":
                return "Barrel";
            case "inventory":
            case "fieldcraft":
                return "Furniture_Crude_Chest_Small";
            case "weapon":
                for (String itemId : Main.ITEMS.keySet()) {
                    if (itemId.toLowerCase().contains("anvil")) {
                        return itemId;
                    }
                }
                return "Barrel";
            default:
                return "Barrel";
        }
    }

    private static int getCategory(String id) {
        String n = id.toLowerCase();
        if (n.contains("backpack") || n.contains("quiver") || n.contains("helipack"))
            return 1;

        if (n.contains("helm") || n.contains("helmet") || n.contains("cap"))
            return 2;
        if (n.contains("cuirass") || n.contains("chest") || n.contains("shirt") || n.contains("armor"))
            return 3;
        if (n.contains("gauntlet") || n.contains("glove") || n.contains("hand"))
            return 4;
        if (n.contains("greave") || n.contains("legging") || n.contains("leg") || n.contains("pant"))
            return 5;
        if (n.contains("boot") || n.contains("shoe") || n.contains("foot"))
            return 6;

        if (n.contains("shield"))
            return 7;

        if (n.contains("sword") || n.contains("blade") || n.contains("longsword") || n.contains("scimitar")
                || n.contains("rapier"))
            return 8;
        if (n.contains("axe") || n.contains("club") || n.contains("mace") || n.contains("hammer")
                || n.contains("dagger") || n.contains("spear") || n.contains("halberd") || n.contains("staff")
                || n.contains("scepter"))
            return 9;
        if (n.contains("pickaxe") || n.contains("sickle") || n.contains("hoe") || n.contains("shovel")
                || n.contains("knife"))
            return 10;

        if (n.contains("bow") || n.contains("crossbow"))
            return 11;

        if (n.contains("ingot") || n.contains("ore") || n.contains("dust") || n.contains("gem") || n.contains("shard")
                || n.contains("crystal"))
            return 12;

        if (n.contains("block") || n.contains("stone") || n.contains("wood") || n.contains("plank")
                || n.contains("slab") || n.contains("stair"))
            return 13;

        return 99;
    }

    private static int getMaterialPriority(String id) {
        String n = id.toLowerCase();
        if (n.contains("adamantite"))
            return 10;
        if (n.contains("prisma"))
            return 9;
        if (n.contains("onyxium"))
            return 8;
        if (n.contains("mithril"))
            return 7;
        if (n.contains("cobalt"))
            return 6;
        if (n.contains("thorium"))
            return 5;
        if (n.contains("gold"))
            return 4;
        if (n.contains("silver"))
            return 3;
        if (n.contains("iron"))
            return 2;
        if (n.contains("copper"))
            return 1;
        return 0;
    }

    private String getTagFromQuantity(MaterialQuantity input) {
        if (input.getItemId() != null)
            return null;
        try {
            java.lang.reflect.Field field = input.getClass().getDeclaredField("resourceTypeId");
            field.setAccessible(true);
            return (String) field.get(input);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveIconForTag(String tag) {
        if (tag == null)
            return "Barrel";

        String randomItem = pickRandomItemForTag(tag);
        if (randomItem != null) {
            return randomItem;
        }

        String t = tag.toLowerCase();

        if (t.contains("fruit"))
            return "Plant_Fruit_Apple";
        if (t.contains("flower"))
            return "Plant_Flower_Common_Yellow2";
        if (t.contains("vegetable"))
            return "Plant_Crop_Carrot_Item";
        if (t.contains("mushroom"))
            return "Plant_Crop_Mushroom_Cap_Brown";
        if (t.contains("meat"))
            return "Food_Wildmeat_Raw";
        if (t.contains("fuel") || t.contains("coal"))
            return "Ingredient_Charcoal";
        if (t.contains("milk"))
            return "Container_Bucket_State_Filled_Milk";
        if (t.contains("plank"))
            return "Wood_Hardwood_Planks";
        if (t.contains("log") || t.contains("trunk") || t.contains("wood"))
            return "Wood_Oak_Trunk";
        if (t.contains("stick"))
            return "Stick";
        if (t.contains("stone") || t.contains("rock") || t.contains("rubble"))
            return "Rock_Stone";
        if (t.contains("plant") || t.contains("fiber"))
            return "Plant_Fiber";
        if (t.contains("ingot") && t.contains("iron"))
            return "Ingot_Iron";
        if (t.contains("ingot") && t.contains("copper"))
            return "Ingot_Copper";
        if (t.contains("ingot") && t.contains("gold"))
            return "Ingot_Gold";
        if (t.contains("leather") || t.contains("hide"))
            return "Leather";
        if (t.contains("cloth") || t.contains("fabric"))
            return "Cloth_Linen";
        if (t.contains("string") || t.contains("thread"))
            return "String";
        if (t.contains("feather"))
            return "Feather";
        if (t.contains("bone"))
            return "Bone";

        return "Barrel";
    }

    private String pickRandomItemForTag(String tag) {
        if (tag == null)
            return null;

        String tagLower = tag.toLowerCase();
        List<String> matchingItems = new ArrayList<>();
        List<String> preferredItems = new ArrayList<>();

        for (Map.Entry<String, Item> entry : Main.ITEMS.entrySet()) {
            String itemId = entry.getKey().toLowerCase();
            if (itemId.contains(tagLower)) {
                if ((tagLower.equals("rock") || tagLower.equals("stone") || tagLower.equals("rubble"))
                        && (itemId.contains("ore") || itemId.contains("crystal") || itemId.contains("gem")
                                || itemId.contains("ingot"))) {
                    continue;
                }

                matchingItems.add(entry.getKey());

                if ((tagLower.equals("rock") || tagLower.equals("stone") || tagLower.equals("rubble"))
                        && (itemId.contains("cobble") || itemId.contains("stone") || itemId.contains("rock"))) {
                    preferredItems.add(entry.getKey());
                }
            }
        }

        List<String> sourceList = preferredItems.isEmpty() ? matchingItems : preferredItems;
        if (sourceList.isEmpty())
            return null;

        int index = Math.abs(tag.hashCode()) % sourceList.size();
        return sourceList.get(index);
    }

    private boolean hasTag(Item item, String tag) {
        try {
            Method m = item.getClass().getMethod("getResourceTypes");
            Object result = m.invoke(item);
            if (result != null && result.getClass().isArray()) {
                Object[] resourceTypes = (Object[]) result;
                for (Object rt : resourceTypes) {
                    try {
                        Method getId = rt.getClass().getMethod("getId");
                        String id = (String) getId.invoke(rt);
                        if (id != null && id.equalsIgnoreCase(tag)) {
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private String formatTagName(String tag) {
        if (tag == null)
            return "Unknown";
        String displayName = tag.replace("_", " ");
        if (displayName.length() > 0) {
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
        }
        return displayName + " (Any Variation)";
    }

    private static class ItemData {
        String id;
        Item item;
        int tier;
        String rawBench;
        int category;
        int materialPriority;

        ItemData(String i, Item it, int t, String rb) {
            id = i;
            item = it;
            tier = t;
            rawBench = rb;
            category = getCategory(i);
            materialPriority = getMaterialPriority(i);
        }
    }
}
