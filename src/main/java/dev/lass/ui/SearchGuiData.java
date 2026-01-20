package dev.lass.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class SearchGuiData {
    public static final BuilderCodec<SearchGuiData> CODEC = BuilderCodec
            .<SearchGuiData>builder(SearchGuiData.class, SearchGuiData::new)
            .addField(new KeyedCodec<>("Item", Codec.STRING), (searchGuiData, s) -> searchGuiData.item = s,
                    searchGuiData -> searchGuiData.item)
            .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING),
                    (searchGuiData, s) -> searchGuiData.searchQuery = s, searchGuiData -> searchGuiData.searchQuery)
            .addField(new KeyedCodec<>("SelectedBench", Codec.STRING),
                    (searchGuiData, s) -> searchGuiData.selectedBench = s,
                    searchGuiData -> searchGuiData.selectedBench)
            .addField(new KeyedCodec<>("BackToList", Codec.STRING),
                    (searchGuiData, s) -> searchGuiData.backToList = s,
                    searchGuiData -> searchGuiData.backToList)
            .addField(new KeyedCodec<>("Pin", Codec.STRING),
                    (searchGuiData, s) -> searchGuiData.pin = s,
                    searchGuiData -> searchGuiData.pin)
            .build();

    public String item;
    public String searchQuery;
    public String selectedBench;
    public String backToList;
    public String pin;

    public SearchGuiData() {
    }
}
