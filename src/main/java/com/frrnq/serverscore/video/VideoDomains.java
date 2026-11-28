package com.frrnq.serverscore.video;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

public final class VideoDomains {
    public static final int MAX_URL_BYTES = 2048;
    private static volatile Set<String> allowed = Collections.emptySet();

    private VideoDomains() {}

    public static void load(File configDirectory) {
        Configuration config = new Configuration(new File(configDirectory, "frrnqserverscore-video.cfg"));
        config.load();
        String[] domains = config.getStringList("allowedDomains", "video", new String[0],
                "Exact HTTPS hostnames, one per line. Configure on server and clients. Redirect hosts must also be listed.");
        Set<String> hosts = new HashSet<>();
        for (String entry : domains) {
            String host = entry.trim().toLowerCase(Locale.ROOT);
            if (validHost(host)) { hosts.add(host); }
        }
        allowed = Collections.unmodifiableSet(hosts);
        if (config.hasChanged()) { config.save(); }
    }

    private static boolean validHost(String host) {
        return host.length() <= 253 && host.matches("(?i)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{1,62}")
                && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")
                && !host.endsWith(".lan") && !host.endsWith(".home") && !host.endsWith(".arpa");
    }

    public static URI check(String input) {
        if (input.isEmpty() || input.length() > MAX_URL_BYTES || input.chars().anyMatch(c -> c <= 32 || c >= 127 || c == 92)) {
            throw new IllegalArgumentException("Use a valid HTTPS MP4 link.");
        }
        URI url;
        try {
            url = URI.create(input);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Use a valid HTTPS MP4 link.");
        }
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        String sourceHost = host;
        if (url.getScheme() == null && url.getRawAuthority() == null) {
            sourceHost = url.getRawPath().split("/", 2)[0].toLowerCase(Locale.ROOT);
        }
        if (isYouTubeHost(sourceHost)) {
            throw new IllegalArgumentException("YouTube \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f. \u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 \u043f\u0440\u044f\u043c\u0443\u044e HTTPS-\u0441\u0441\u044b\u043b\u043a\u0443 \u043d\u0430 MP4-\u0444\u0430\u0439\u043b");
        }
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getRawUserInfo() != null || url.getFragment() != null
                || (url.getPort() != -1 && url.getPort() != 443) || !validHost(host) || !allowed.contains(host)) {
            throw new IllegalArgumentException("Video URL must use HTTPS on an allowed external domain.");
        }
        return url;
    }

    private static boolean isYouTubeHost(String host) {
        if (host.endsWith(".")) { host = host.substring(0, host.length() - 1); }
        return host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtu.be") || host.endsWith(".youtu.be")
                || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com");
    }
}
