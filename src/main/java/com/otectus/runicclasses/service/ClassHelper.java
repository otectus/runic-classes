package com.otectus.runicclasses.service;

import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.data.ClassDefinition;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which class a player is, two ways.
 *
 * <p>{@link #selection} reads the Origins container, the source of truth for what the player
 * <em>chose</em>. {@link #appliedClass} reads what RunicClasses has <em>granted</em>, which is what
 * every hot path (perk and Power visibility, cap hooks, effect dispatch) asks: it is memoised per
 * tick exactly like {@code RaceHelper.getRaceId}, one map per logical side, so a hundred perk checks
 * in one melee hit cost one capability resolve.
 */
public final class ClassHelper {

    /** What the class layer currently holds. */
    public enum Kind {
        /** No Origins container (mid-construction, or a fake player). Nothing to act on. */
        NO_CONTAINER,
        /** The layer is unchosen: first join before the class screen, or an emptied layer. */
        EMPTY,
        CLASSLESS,
        CLASS,
        /** An origin on the layer with no class definition: leave the player exactly as they are. */
        UNKNOWN
    }

    public record Selection(Kind kind, @Nullable ResourceLocation origin, @Nullable ClassDefinition definition) {

        public boolean isChosen() {
            return kind == Kind.CLASSLESS || kind == Kind.CLASS;
        }
    }

    private record Memo(long tick, String applied) {}

    private static final Map<UUID, Memo> SERVER_MEMO = new ConcurrentHashMap<>();
    private static final Map<UUID, Memo> CLIENT_MEMO = new ConcurrentHashMap<>();

    private ClassHelper() {}

    public static Selection selection(Player player) {
        IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
        if (container == null) return new Selection(Kind.NO_CONTAINER, null, null);
        ResourceKey<Origin> key;
        try {
            key = container.getOrigin(ClassIds.CLASS_LAYER);
        } catch (RuntimeException e) {
            return new Selection(Kind.NO_CONTAINER, null, null);
        }
        if (ClassIds.isEmpty(key)) return new Selection(Kind.EMPTY, null, null);
        ResourceLocation origin = key.location();
        ClassDefinition def = ClassDataManager.forLevel(player.level()).byOrigin(origin);
        if (def == null) return new Selection(Kind.UNKNOWN, origin, null);
        return new Selection(def.isClassless() ? Kind.CLASSLESS : Kind.CLASS, origin, def);
    }

    @Nullable
    public static ClassState state(@Nullable Player player) {
        return RCCapabilities.get(player);
    }

    /** The applied class's short name ({@code fighter}), or empty for none. Memoised per tick. */
    public static String appliedClass(@Nullable Player player) {
        if (player == null) return "";
        long tick = player.level().getGameTime();
        Map<UUID, Memo> memos = player.level().isClientSide() ? CLIENT_MEMO : SERVER_MEMO;
        Memo memo = memos.get(player.getUUID());
        if (memo != null && memo.tick() == tick) return memo.applied();
        ClassState state = RCCapabilities.get(player);
        String applied = state == null ? "" : state.appliedPath();
        memos.put(player.getUUID(), new Memo(tick, applied));
        return applied;
    }

    public static boolean isMember(@Nullable Player player, String classPath) {
        return !classPath.isEmpty() && classPath.equals(appliedClass(player));
    }

    /** Drop a player's memo (logout, clone, any change of the applied class). */
    public static void invalidate(UUID player) {
        SERVER_MEMO.remove(player);
        CLIENT_MEMO.remove(player);
    }

    public static void clearAll() {
        SERVER_MEMO.clear();
        CLIENT_MEMO.clear();
    }

    public static int memoCount() {
        return SERVER_MEMO.size() + CLIENT_MEMO.size();
    }
}
