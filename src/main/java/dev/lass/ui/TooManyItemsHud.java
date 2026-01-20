package dev.lass.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.lass.Main;

import javax.annotation.Nonnull;
import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.awt.Color;

public class TooManyItemsHud extends CustomUIHud {

    public static final String HUD_LAYOUT = "Pages/TooManyItems_PinnedHUD.ui";
    public static final String INGREDIENT_LAYOUT = "Pages/TooManyItems_PinnedIngredient.ui";

    private final String itemId;
    private final Player player;
    private final Ref<EntityStore> playerRefEcs;
    private final Store<EntityStore> entityStore;

    public static final java.util.Map<PlayerRef, TooManyItemsHud> ACTIVE_HUDS = java.util.Collections
            .synchronizedMap(new java.util.WeakHashMap<>());

    public TooManyItemsHud(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> playerRefEcs,
            @Nonnull Store<EntityStore> store, @Nonnull String itemId) {
        super(playerRef);
        this.player = player;
        this.playerRefEcs = playerRefEcs;
        this.entityStore = store;
        this.itemId = itemId;
        ACTIVE_HUDS.put(playerRef, this);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append(HUD_LAYOUT);

        if ("HIDDEN".equals(itemId)) {
            cmd.set("#PinnedRoot.Visible", false);
            return;
        }

        if (itemId == null || !Main.ITEMS.containsKey(itemId)) {
            return;
        }

        Item item = Main.ITEMS.get(itemId);
        cmd.set("#PinnedIcon.ItemId", itemId);
        cmd.set("#PinnedName.TextSpans", Message.translation(item.getTranslationKey()));

        updateProgress(cmd, entityStore, playerRefEcs);
    }

    public void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        updateProgress(cmd, entityStore, playerRefEcs);
        this.update(false, cmd);
    }

    public void updateProgress(UICommandBuilder cmd, Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (itemId == null || !Main.recipes.containsKey(itemId))
            return;

        List<CraftingRecipe> recipeList = Main.recipes.get(itemId);
        if (recipeList.isEmpty())
            return;

        CraftingRecipe recipe = recipeList.get(0);
        cmd.clear("#PinnedIngredients");

        int idx = 0;
        for (MaterialQuantity input : recipe.getInput()) {
            cmd.append("#PinnedIngredients", INGREDIENT_LAYOUT);
            String el = "#PinnedIngredients[" + idx + "]";

            int count = countItems(store, ref, input);
            boolean hasEnough = count >= input.getQuantity();

            Message nameMsg;
            String ingId = input.getItemId();
            if (ingId != null) {
                if (Main.ITEMS.containsKey(ingId)) {
                    nameMsg = Message.translation(Main.ITEMS.get(ingId).getTranslationKey());
                } else {
                    nameMsg = Message.raw(format(ingId));
                }
            } else {
                String tag = getTagFromQuantity(input);
                nameMsg = Message.raw(formatTagName(tag));
            }

            Message statusMsg = Message.join(
                    Message.raw(count + "/" + input.getQuantity() + " "),
                    nameMsg).color(hasEnough ? Color.GREEN : Color.RED);

            cmd.set(el + " #IngredientStatus.TextSpans", statusMsg);

            if (ingId != null) {
                cmd.set(el + " #IngredientIcon.ItemId", ingId);
            } else {
                String tag = getTagFromQuantity(input);
                cmd.set(el + " #IngredientIcon.ItemId", resolveIconForTag(tag));
            }
            idx++;
        }
    }

    private int countItems(Store<EntityStore> store, Ref<EntityStore> ref, MaterialQuantity input) {
        try {
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            if (inv == null)
                return 0;

            String ingId = input.getItemId();
            String resourceTypeId = input.getResourceTypeId();

            int total = 0;
            String[] containers = { "getCombinedEverything", "getStorage", "getHotbar", "getBackpack" };

            for (String methodName : containers) {
                try {
                    Method m = inv.getClass().getMethod(methodName);
                    Object container = m.invoke(inv);
                    if (container == null)
                        continue;

                    Method getCapacity = container.getClass().getMethod("getCapacity");
                    short capacity = (short) getCapacity.invoke(container);
                    Method getItemStack = container.getClass().getMethod("getItemStack", short.class);

                    int containerTotal = 0;
                    for (short i = 0; i < capacity; i++) {
                        Object stack = getItemStack.invoke(container, i);
                        if (stack == null)
                            continue;

                        Method isEmpty = stack.getClass().getMethod("isEmpty");
                        if ((boolean) isEmpty.invoke(stack))
                            continue;

                        Method getQty = stack.getClass().getMethod("getQuantity");
                        int qty = (int) getQty.invoke(stack);

                        if (ingId != null) {
                            Method getItemId = stack.getClass().getMethod("getItemId");
                            String id = ((String) getItemId.invoke(stack)).toLowerCase();
                            String target = ingId.toLowerCase();
                            if (!target.contains(":"))
                                target = "hytale:" + target;
                            if (!id.contains(":"))
                                id = "hytale:" + id;

                            if (id.equals(target)) {
                                containerTotal += qty;
                                continue;
                            }
                        }

                        if (resourceTypeId != null) {
                            try {
                                Method getItem = stack.getClass().getMethod("getItem");
                                Object item = getItem.invoke(stack);
                                if (item != null) {
                                    Method getResTypes = item.getClass().getMethod("getResourceTypes");
                                    Object[] resTypes = (Object[]) getResTypes.invoke(item);
                                    if (resTypes != null) {
                                        for (Object rt : resTypes) {
                                            Field idField = rt.getClass().getField("id");
                                            String typeId = (String) idField.get(rt);
                                            if (resourceTypeId.equalsIgnoreCase(typeId)) {
                                                containerTotal += qty;
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (containerTotal > 0) {
                        return containerTotal;
                    }
                } catch (Exception ignored) {
                }
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatTagName(String tag) {
        if (tag == null)
            return "Unknown";
        String displayName = tag.replace("_", " ");
        if (displayName.length() > 0) {
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
        }
        return displayName;
    }

    private String format(String key) {
        if (key == null)
            return "Unknown";
        if (key.contains(":"))
            key = key.substring(key.indexOf(":") + 1);
        String name = key.replace("_", " ");
        if (name.length() > 0) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }

    private String getTagFromQuantity(MaterialQuantity input) {
        try {
            Field field = input.getClass().getDeclaredField("resourceTypeId");
            field.setAccessible(true);
            return (String) field.get(input);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveIconForTag(String tag) {
        if (tag == null)
            return "Barrel";
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
        return "Barrel";
    }
}
