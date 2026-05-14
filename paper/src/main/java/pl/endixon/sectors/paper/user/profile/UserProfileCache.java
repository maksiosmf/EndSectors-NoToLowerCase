/*
 *
 * EndSectors – Non-Commercial License
 * (c) 2025 Endixon
 *
 * Permission is granted to use, copy, and
 * modify this software **only** for personal
 * or educational purposes.
 *
 * Commercial use, redistribution, claiming
 * this work as your own, or copying code
 * without explicit permission is strictly
 * prohibited.
 *
 * Visit https://github.com/Endixon/EndSectors
 * for more info.
 *
 */

package pl.endixon.sectors.paper.user.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import pl.endixon.sectors.common.Common;
import pl.endixon.sectors.paper.util.LoggerUtil;

public final class UserProfileCache {

    private static final String PREFIX = "user:";
    private static final Map<String, UserProfile> LOCAL_CACHE = new ConcurrentHashMap<>();

    private UserProfileCache() {
    }

    private static String getKey(@NonNull String name) {
        return PREFIX + name;
    }

    /**
     * Redis keys use {@link UserProfile#getName()} casing. Lookup may receive Velocity's lowercase
     * username — try exact then legacy lowercase key (same player, Mojang-normalized name).
     */
    private static List<String> redisNameSuffixes(@NonNull String name) {
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(name);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.equals(name)) {
            ordered.add(lower);
        }
        return new ArrayList<>(ordered);
    }

    public static void save(@NonNull UserProfile user) {
        try {
            Common.getInstance().getRedisManager().hset(getKey(user.getName()), user.toRedisMap());
        } catch (Exception e) {
            LoggerUtil.error(String.format("[ProfileCache] Critical failure during save for user '%s': %s", user.getName(), e.getMessage()));
        }
    }

    public static long getRemoteVersion(@NonNull String name) {
        try {
            for (String suffix : redisNameSuffixes(name)) {
                String version = Common.getInstance().getRedisManager().hget(getKey(suffix), "dataVersion");
                if (version == null) {
                    continue;
                }
                return Long.parseLong(version);
            }
            return -1L;
        } catch (NumberFormatException e) {
            LoggerUtil.warn(String.format("[ProfileCache] Corrupted dataVersion for '%s'. Value is not a valid Long.", name));
            return 0L;
        } catch (Exception e) {
            LoggerUtil.error(String.format("[ProfileCache] Redis connection error during version check for '%s'", name));
            return -2L;
        }
    }

    public static Optional<Map<String, String>> load(@NonNull String name) {
        try {
            for (String suffix : redisNameSuffixes(name)) {
                Map<String, String> data = Common.getInstance().getRedisManager().hgetAll(getKey(suffix));
                if (data != null && !data.isEmpty()) {
                    return Optional.of(data);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            LoggerUtil.error(String.format("[ProfileCache] Critical failure during load for user '%s': %s", name, e.getMessage()));
            return Optional.empty();
        }
    }

    public static void warmup() {
        LoggerUtil.info("[ProfileCache] Starting database warmup...");
        long start = System.currentTimeMillis();

        try {
            List<String> keys = Common.getInstance().getRedisManager().getKeys(PREFIX + "*");

            if (keys == null || keys.isEmpty()) {
                LoggerUtil.info("[ProfileCache] Warmup aborted: No profile data found in Redis.");
                return;
            }

            keys.forEach(fullKey -> {
                String name = fullKey.substring(PREFIX.length());
                load(name).ifPresent(data -> addToCache(new UserProfile(data)));
            });

                LoggerUtil.info(String.format("[ProfileCache] Warmup completed. Loaded %d profiles in %dms.", LOCAL_CACHE.size(), (System.currentTimeMillis() - start)));
        } catch (Exception e) {
                LoggerUtil.error("[ProfileCache] Critical failure during database warmup: " + e.getMessage());
            }
    }

    public static void addToCache(@NonNull UserProfile profile) {
        LOCAL_CACHE.put(profile.getName(), profile);
        String lower = profile.getName().toLowerCase(Locale.ROOT);
        if (!lower.equals(profile.getName())) {
            LOCAL_CACHE.put(lower, profile);
        }
    }

    public static UserProfile getFromCache(@NonNull String name) {
        UserProfile hit = LOCAL_CACHE.get(name);
        if (hit != null) {
            return hit;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals(name) ? null : LOCAL_CACHE.get(lower);
    }

    public static void removeFromCache(@NonNull String name) {
        LOCAL_CACHE.remove(name);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.equals(name)) {
            LOCAL_CACHE.remove(lower);
        }
    }

    public static void clearCache() {
        LOCAL_CACHE.clear();
    }
}