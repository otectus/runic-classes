package com.otectus.runicclasses.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.data.ClassDefinition;
import com.otectus.runicclasses.data.ClassDefinitionValidator;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.KitDispatcher;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicclasses.service.ClassHelper;
import com.otectus.runicclasses.service.ClassReconciler;
import com.otectus.runicclasses.service.RepickService;
import com.otectus.runicclasses.service.StarterBagService;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import io.github.edwinmindcraft.origins.api.OriginsAPI;
import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.github.edwinmindcraft.origins.common.OriginsCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /runicclasses}: {@code info} for anyone about themselves; everything that changes state, or
 * looks at someone else, is permission level 2. The Class Orb is the player path for re-picking;
 * {@code choose} forces the same flow without an item, for testing and support.
 */
public final class RCCommands {

    private static final SuggestionProvider<CommandSourceStack> CLASSES = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (ClassDefinition def : ClassDataManager.server().byId().values()) ids.add(def.path());
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private RCCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(ClassIds.MOD_ID)
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("choose").requires(s -> s.hasPermission(2))
                        .executes(ctx -> choose(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> choose(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                        .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "class"))))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("bag").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> bag(ctx, EntityArgument.getPlayer(ctx, "player"), null, false))
                                        .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                                .executes(ctx -> bag(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "class"), false))
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> bag(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "class"), true)))))))
                .then(Commands.literal("validate").requires(s -> s.hasPermission(2)).executes(RCCommands::validate))
                .then(Commands.literal("diagnostics").requires(s -> s.hasPermission(2)).executes(RCCommands::diagnostics)));
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ClassState state = RCCapabilities.get(player);
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.no_origins"));
            return 0;
        }
        ClassHelper.Selection selection = ClassHelper.selection(player);
        CommandSourceStack source = ctx.getSource();
        Component name = player.getDisplayName();
        source.sendSuccess(() -> Component.translatable("commands.runicclasses.info.header", name,
                Component.literal(selection.kind().name().toLowerCase(java.util.Locale.ROOT))), false);
        source.sendSuccess(() -> Component.translatable("commands.runicclasses.info.selected",
                String.valueOf(selection.origin())), false);
        if (state.hasAppliedClass()) {
            source.sendSuccess(() -> Component.translatable("commands.runicclasses.info.applied", state.appliedClass,
                    state.appliedPrimary, "+" + state.grantPrimary, state.appliedSecondary, "+" + state.grantSecondary,
                    state.appliedCapBonus), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.runicclasses.info.none"), false);
        }
        Component bag = Component.translatable(state.starterBagGranted
                ? "commands.runicclasses.info.bag.granted" : "commands.runicclasses.info.bag.pending");
        source.sendSuccess(() -> Component.translatable("commands.runicclasses.info.bag", bag,
                state.firstClass.isEmpty() ? "-" : state.firstClass), false);
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        RepickService.Result result = RepickService.repick(player, false);
        if (!result.ok()) {
            ctx.getSource().sendFailure(result.refusal());
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.choose.success", player.getDisplayName()), true);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String classPath) {
        ResourceLocation id = classPath.contains(":") ? ResourceLocation.tryParse(classPath) : ClassIds.id(classPath);
        ClassDefinition def = id == null ? null : ClassDataManager.server().byId(id);
        OriginLayer layer = OriginsAPI.getLayersRegistry().get(ClassIds.CLASS_LAYER.location());
        if (def == null || layer == null || !layer.contains(def.origin())) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.set.unknown", classPath));
            return 0;
        }
        IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
        if (container == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.no_origins"));
            return 0;
        }
        boolean hadAll = container.hadAllOrigins();
        container.setOrigin(ClassIds.CLASS_LAYER, ClassIds.originKey(def.origin()));
        container.onChosen(ClassIds.originKey(def.origin()), hadAll);
        OriginsCommon.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), container.getSynchronizationPacket());
        container.synchronize();
        ClassReconciler.reconcile(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.set.success",
                player.getDisplayName(), def.id().toString()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (!RepickService.clear(player, true)) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.no_origins"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.reset.success", player.getDisplayName()), true);
        return 1;
    }

    private static int bag(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String classPath, boolean force)
            throws CommandSyntaxException {
        ClassState state = RCCapabilities.get(player);
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.no_origins"));
            return 0;
        }
        ClassDefinition def;
        if (classPath == null) {
            ClassHelper.Selection selection = ClassHelper.selection(player);
            def = selection.definition();
        } else {
            ResourceLocation id = classPath.contains(":") ? ResourceLocation.tryParse(classPath) : ClassIds.id(classPath);
            def = id == null ? null : ClassDataManager.server().byId(id);
        }
        if (def == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.set.unknown", String.valueOf(classPath)));
            return 0;
        }
        if (state.starterBagGranted && !force) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.bag.already", player.getDisplayName()));
            return 0;
        }
        int given = StarterBagService.give(player, def);
        if (given == 0) {
            ctx.getSource().sendFailure(Component.translatable("commands.runicclasses.bag.nothing", def.id().toString()));
            return 0;
        }
        if (!state.starterBagGranted) {
            state.starterBagGranted = true;
            state.starterBagClass = def.id().toString();
            if (state.firstClass.isEmpty()) state.firstClass = def.id().toString();
        }
        ClassDefinition finalDef = def;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.bag.success",
                player.getDisplayName(), finalDef.id().toString()), true);
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> ctx) {
        var snapshot = ClassDataManager.server();
        List<ClassDefinitionValidator.Problem> problems = new ArrayList<>(ClassDataManager.lastProblems());
        problems.addAll(ClassDefinitionValidator.validateOrigins(snapshot.byId()));
        if (problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.validate.ok", snapshot.byId().size()), false);
            return 1;
        }
        for (ClassDefinitionValidator.Problem problem : problems) {
            ChatFormatting colour = problem.severity() == ClassDefinitionValidator.Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW;
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.validate.problem", problem.toString())
                    .withStyle(colour), false);
        }
        return problems.size();
    }

    private static int diagnostics(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.runicclasses.diagnostics",
                ClassDataManager.server().byId().size(), RCPerks.all().size(), RCPowers.all().size(),
                KitDispatcher.onlineMemberCount(), ClassHelper.memoCount() + CombatMemory.size()), false);
        return 1;
    }
}
