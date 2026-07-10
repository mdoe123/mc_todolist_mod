package com.todolistmod.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.todolistmod.store.ChecklistStore;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 为 {@code /todolist do|end|edit} 子命令的清单名参数提供 Tab 补全候选。
 * 候选来自 {@link ChecklistStore#loadAll()} 返回的 keySet（即清单 name 字段值）。
 */
public class ChecklistSuggestionProvider implements SuggestionProvider<FabricClientCommandSource> {
    public static final ChecklistSuggestionProvider INSTANCE = new ChecklistSuggestionProvider();

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> ctx,
                                                         SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>(ChecklistStore.loadAll().keySet());
        for (String name : names) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    }
}
