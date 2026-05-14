/*
 *
 *  EndSectors  Non-Commercial License
 *  (c) 2025 Endixon
 *
 *  Permission is granted to use, copy, and
 *  modify this software **only** for personal
 *  or educational purposes.
 *
 *   Commercial use, redistribution, claiming
 *  this work as your own, or copying code
 *  without explicit permission is strictly
 *  prohibited.
 *
 *  Visit https://github.com/Endixon/EndSectors
 *  for more info.
 *
 */

package pl.endixon.sectors.proxy.user.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import pl.endixon.sectors.common.Common;
import pl.endixon.sectors.proxy.util.LoggerUtil;

/*
 *
 *  EndSectors  Non-Commercial License
 *  (c) 2025 Endixon
 *
 *  Permission is granted to use, copy, and
 *  modify this software **only** for personal
 *  or educational purposes.
 *
 *   Commercial use, redistribution, claiming
 *  this work as your own, or copying code
 *  without explicit permission is strictly
 *  prohibited.
 *
 *  Visit https://github.com/Endixon/EndSectors
 *  for more info.
 *
 */

public class UserProfileCache {

    private static final String KEY_PREFIX = "user:";
    private static final String FIELD_SECTOR = "sectorName";
    private static final String VALUE_UNKNOWN = "unknown";


    public Optional<String> getSectorName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }

        try {
            for (String suffix : redisNameSuffixes(playerName.trim())) {
                final String key = buildKey(suffix);
                final Map<String, String> data = Common.getInstance().getRedisManager().hgetAll(key);

                if (data == null || data.isEmpty()) {
                    continue;
                }

                final String sector = data.get(FIELD_SECTOR);

                if (sector == null || sector.isBlank() || sector.trim().equalsIgnoreCase(VALUE_UNKNOWN)) {
                    continue;
                }

                return Optional.of(sector.trim());
            }
            return Optional.empty();
        } catch (Exception exception) {
            LoggerUtil.info("[CRITICAL] Failed to retrieve sector data for " + playerName + ": " + exception.getMessage());
            return Optional.empty();
        }
    }



    public void setSectorName(String playerName, String sectorName) {
        if (playerName == null || playerName.isBlank() || sectorName == null || sectorName.isBlank()) {
            return;
        }

        final String sanitizedSector = sectorName.trim();

        if (sanitizedSector.equalsIgnoreCase(VALUE_UNKNOWN)) {
            return;
        }

        try {
            String trimmed = playerName.trim();
            for (String suffix : redisNameSuffixes(trimmed)) {
                final String key = buildKey(suffix);
                final Map<String, String> data = Common.getInstance().getRedisManager().hgetAll(key);
                if (data != null && !data.isEmpty()) {
                    Common.getInstance().getRedisManager().hset(key, Map.of(FIELD_SECTOR, sanitizedSector));
                    return;
                }
            }
            Common.getInstance().getRedisManager().hset(buildKey(trimmed), Map.of(FIELD_SECTOR, sanitizedSector));
        } catch (Exception exception) {
            LoggerUtil.info("[CRITICAL] Failed to persist sector '" + sanitizedSector + "' for " + playerName + ": " + exception.getMessage());
        }
    }

    private static List<String> redisNameSuffixes(String name) {
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(name);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.equals(name)) {
            ordered.add(lower);
        }
        return new ArrayList<>(ordered);
    }

    private String buildKey(String playerName) {
        return KEY_PREFIX + playerName;
    }
}