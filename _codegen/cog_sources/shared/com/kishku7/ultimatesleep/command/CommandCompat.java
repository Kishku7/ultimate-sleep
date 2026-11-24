package com.kishku7.ultimatesleep.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The command-tree pieces whose shape changed at 26.4 (cog-gated): the argument types, the
 * suggestion helper and provider wrapper, and node visibility. From 26.4 Mojang's command tree must
 * be serialisable, so raw Brigadier argument types and lambda suggestion providers are rejected and
 * a requires() lambda is not accepted at all; every guarded command re-checks its permission in the
 * executor, so dropping the lambda only changes what the client's command list shows.
 */
final class CommandCompat {

    private CommandCompat() {}

    //[[[cog
    // import cog, sys; sys.path.insert(0, codegen); import compat
    // if compat.ui264(ver):
    //     cog.outl("    static ArgumentType<String> word() { return net.minecraft.commands.arguments.StringArgument.word(); }")
    //     cog.outl("")
    //     cog.outl("    static ArgumentType<String> greedy() { return net.minecraft.commands.arguments.StringArgument.greedyString(); }")
    //     cog.outl("")
    //     cog.outl("    static String getString(CommandContext<?> ctx, String name) {")
    //     cog.outl("        return net.minecraft.commands.arguments.StringArgument.getString(ctx, name);")
    //     cog.outl("    }")
    //     cog.outl("")
    //     cog.outl("    static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {")
    //     cog.outl("        return net.minecraft.commands.suggestions.SuggestionContext.suggest(values, builder);")
    //     cog.outl("    }")
    //     cog.outl("")
    //     cog.outl("    @SuppressWarnings(\"unchecked\")")
    //     cog.outl("    static SuggestionProvider<CommandSourceStack> provider(SuggestionProvider<CommandSourceStack> p) {")
    //     cog.outl("        return net.minecraft.commands.suggestions.SuggestionProviders.serverOnly(")
    //     cog.outl("                (server, context, builder) -> p.getSuggestions(")
    //     cog.outl("                        (CommandContext<CommandSourceStack>) (CommandContext<?>) context, builder));")
    //     cog.outl("    }")
    //     cog.outl("")
    //     cog.outl("    static <T extends ArgumentBuilder<CommandSourceStack, T>> T requires(T node, Predicate<CommandSourceStack> p) {")
    //     cog.outl("        return node;")
    //     cog.outl("    }")
    // else:
    //     cog.outl("    static ArgumentType<String> word() { return com.mojang.brigadier.arguments.StringArgumentType.word(); }")
    //     cog.outl("")
    //     cog.outl("    static ArgumentType<String> greedy() { return com.mojang.brigadier.arguments.StringArgumentType.greedyString(); }")
    //     cog.outl("")
    //     cog.outl("    static String getString(CommandContext<?> ctx, String name) {")
    //     cog.outl("        return com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, name);")
    //     cog.outl("    }")
    //     cog.outl("")
    //     cog.outl("    static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {")
    //     cog.outl("        return net.minecraft.commands.SharedSuggestionProvider.suggest(values, builder);")
    //     cog.outl("    }")
    //     cog.outl("")
    //     cog.outl("    static SuggestionProvider<CommandSourceStack> provider(SuggestionProvider<CommandSourceStack> p) {")
    //     cog.outl("        return p;")
    //     cog.outl("    }")
    //     cog.outl("")
    //     cog.outl("    static <T extends ArgumentBuilder<CommandSourceStack, T>> T requires(T node, Predicate<CommandSourceStack> p) {")
    //     cog.outl("        return node.requires(p);")
    //     cog.outl("    }")
    //]]]
    //[[[end]]]
}
