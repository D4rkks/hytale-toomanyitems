package dev.lass;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.lass.commands.RecipesCommand;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends JavaPlugin {

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static final Map<String, Map<String, BenchRequirement[]>> recipeRegistries = new Object2ObjectOpenHashMap<>();
    public static final Map<String, List<CraftingRecipe>> recipes = new Object2ObjectOpenHashMap<>();

    private static Main instance;
    private java.util.concurrent.ScheduledExecutorService scheduler;

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static Main getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        super.setup();
        this.getCommandRegistry().registerCommand(new RecipesCommand());


        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, Main::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeLoad);
        this.getEventRegistry().register(RemovedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeRemove);

        scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.ScheduledFuture<java.lang.Void> future = (java.util.concurrent.ScheduledFuture<java.lang.Void>) (java.util.concurrent.ScheduledFuture<?>) scheduler
                .scheduleAtFixedRate(() -> {
                    try {
                        dev.lass.ui.TooManyItemsHud.ACTIVE_HUDS.values().forEach(hud -> {
                            try {
                                hud.refresh();
                            } catch (Exception ignored) {
                            }
                        });
                    } catch (Exception ignored) {
                    }
                }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

        this.getTaskRegistry().registerTask(future);
    }

    private static void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = new HashMap<>(event.getAssetMap().getAssetMap());
    }

    private static void onRecipeLoad(
            LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        for (CraftingRecipe recipe : event.getLoadedAssets().values()) {
            for (MaterialQuantity output : recipe.getOutputs()) {
                if (recipeRegistries.containsKey(output.getItemId())) {
                    recipeRegistries.get(output.getItemId()).remove(recipe.getId());
                }
            }

            if (recipe.getBenchRequirement() != null) {
                for (MaterialQuantity output : recipe.getOutputs()) {
                    if (!recipeRegistries.containsKey(output.getItemId())) {
                        recipeRegistries.put(output.getItemId(), new HashMap<>());
                    }
                    recipeRegistries.get(output.getItemId()).put(recipe.getId(), recipe.getBenchRequirement());

                    if (!recipes.containsKey(output.getItemId())) {
                        recipes.put(output.getItemId(), new ArrayList<>());
                    }
                    recipes.get(output.getItemId()).add(recipe);
                }
            }
        }
    }

    private static void onRecipeRemove(
            RemovedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        for (String key : recipeRegistries.keySet()) {
            for (String removedAsset : event.getRemovedAssets()) {
                recipeRegistries.get(key).remove(removedAsset);
            }
        }
        for (String key : recipes.keySet()) {
            recipes.get(key).removeIf(r -> event.getRemovedAssets().contains(r.getId()));
        }
    }
}
