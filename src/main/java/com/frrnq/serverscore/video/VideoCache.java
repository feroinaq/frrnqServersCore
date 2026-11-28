package com.frrnq.serverscore.video;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;

/** Used by the single playback worker before it opens a decoder. */
final class VideoCache {
    private static final long FILE_LIMIT = 128L << 20;
    private static final long CACHE_LIMIT = 512L << 20;

    static final class Failure extends IOException {
        Failure(String message) { super(message); }
    }

    static final class Transfer {
        volatile long bytes;
        volatile long total = -1;
        volatile Path file;
    }

    Path fetch(Path gameDirectory, URI source, Transfer transfer, BooleanSupplier cancelled) throws IOException, InterruptedException {
        Path videos = gameDirectory.toRealPath().resolve("frrnq_videos");
        Files.createDirectories(videos);
        if (!videos.equals(videos.toRealPath())) { throw new Failure("Video directory must be local."); }
        Path cache = videos.resolve("cache");
        Files.createDirectories(cache);
        if (!cache.equals(cache.toRealPath())) { throw new Failure("Video cache must be local."); }
        try (DirectoryStream<Path> parts = Files.newDirectoryStream(cache, "video-*.part")) {
            for (Path part : parts) { Files.deleteIfExists(part); }
        }

        String id = digest(source);
        Path file = cache.resolve(id + ".mp4"), sidecar = cache.resolve(id + ".properties");
        Properties entry = loadEntry(file, sidecar);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        URI url = source;
        for (int redirects = 0; redirects <= 5; redirects++) {
            checkTransfer(cancelled, deadline);
            HttpsURLConnection connection = PinnedVideoHttps.open(url);
            try {
                boolean validate = digest(url).equals(entry.getProperty("endpoint"));
                String etag = validate ? validator(entry.getProperty("etag")) : null;
                String modified = validate ? validator(entry.getProperty("modified")) : null;
                if (etag != null) { connection.setRequestProperty("If-None-Match", etag); }
                if (modified != null) { connection.setRequestProperty("If-Modified-Since", modified); }
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.length() > VideoDomains.MAX_URL_BYTES) {
                        throw new Failure("Invalid video redirect.");
                    }
                    try { url = VideoDomains.check(url.resolve(location).toASCIIString()); }
                    catch (IllegalArgumentException invalid) { throw new Failure("Video redirected to an unauthorized URL."); }
                    continue;
                }
                if (status == 304 && (etag != null || modified != null)) {
                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
                    trim(cache, 0, file);
                    return file;
                }
                if (status != 200) { throw new Failure("Video host returned HTTP " + status + "."); }
                String encoding = connection.getContentEncoding();
                if (encoding != null && !"identity".equalsIgnoreCase(encoding)) {
                    throw new Failure("Unsupported HTTP content encoding.");
                }
                long size = connection.getContentLengthLong();
                if (size > FILE_LIMIT || size == 0) { throw new Failure("Video must be at most 128 MiB."); }
                trim(cache, (size < 0 ? FILE_LIMIT : size) + 4096, null);
                transfer.total = size;
                Path part = Files.createTempFile(cache, "video-", ".part");
                try {
                    try (InputStream stream = connection.getInputStream(); OutputStream output = Files.newOutputStream(part)) {
                        byte[] block = new byte[32768];
                        int count;
                        while ((count = stream.read(block)) != -1) {
                            checkTransfer(cancelled, deadline);
                            long received = transfer.bytes + count;
                            if (received > FILE_LIMIT || (size >= 0 && received > size)) {
                                throw new Failure("Video is larger than expected.");
                            }
                            output.write(block, 0, count);
                            transfer.bytes = received;
                        }
                    }
                    checkTransfer(cancelled, deadline);
                    if (transfer.bytes == 0 || (size >= 0 && transfer.bytes != size)) {
                        throw new Failure("Incomplete video download.");
                    }
                    Files.deleteIfExists(sidecar);
                    try {
                        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException unsupported) {
                        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Properties next = new Properties();
                    next.setProperty("endpoint", digest(url));
                    next.setProperty("size", Long.toString(transfer.bytes));
                    saveValidator(next, "etag", connection.getHeaderField("ETag"));
                    saveValidator(next, "modified", connection.getHeaderField("Last-Modified"));
                    try (OutputStream metadata = Files.newOutputStream(sidecar)) { next.store(metadata, null); }
                    trim(cache, 0, file);
                    return file;
                } finally {
                    Files.deleteIfExists(part);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new Failure("Too many video redirects.");
    }

    private static void checkTransfer(BooleanSupplier cancelled, long deadline) throws IOException, InterruptedException {
        if (cancelled.getAsBoolean()) { throw new InterruptedException(); }
        if (System.nanoTime() - deadline >= 0) { throw new Failure("Video download timed out."); }
    }

    private static Properties loadEntry(Path file, Path sidecar) throws IOException {
        Properties entry = new Properties();
        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)
                && Files.size(file) > 0 && Files.size(file) <= FILE_LIMIT && Files.size(sidecar) <= 4096) {
            try (InputStream stream = Files.newInputStream(sidecar)) {
                entry.load(stream);
                if (!Long.toString(Files.size(file)).equals(entry.getProperty("size"))) { entry.clear(); }
            } catch (IllegalArgumentException invalid) { entry.clear(); }
        }
        return entry;
    }

    private static String validator(String value) {
        return value == null || value.isEmpty() || value.length() > 1024
                || value.chars().anyMatch(c -> c < 32 || c >= 127) ? null : value;
    }

    private static void saveValidator(Properties entry, String key, String value) {
        value = validator(value);
        if (value != null) { entry.setProperty(key, value); }
    }

    private static void trim(Path cache, long reserve, Path keep) throws IOException {
        List<Path> files = new ArrayList<>();
        long total = reserve;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(cache, "*.mp4")) {
            for (Path file : entries) {
                if (file.getFileName().toString().matches("[a-f0-9]{64}\\.mp4")
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    total += Files.size(file);
                    Path metadata = metadata(file);
                    if (Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) { total += Files.size(metadata); }
                    if (!file.equals(keep)) { files.add(file); }
                }
            }
        }
        if (total <= CACHE_LIMIT) { return; }
        java.util.Map<Path, FileTime> times = new java.util.HashMap<>();
        for (Path file : files) { times.put(file, Files.getLastModifiedTime(file)); }
        files.sort(Comparator.comparing(times::get));
        for (Path file : files) {
            total -= Files.size(file);
            Files.delete(file);
            Path metadata = metadata(file);
            if (Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) { total -= Files.size(metadata); }
            Files.deleteIfExists(metadata);
            if (total <= CACHE_LIMIT) { return; }
        }
        throw new Failure("Video cache limit exceeded.");
    }

    private static Path metadata(Path file) {
        return file.resolveSibling(file.getFileName().toString().replace(".mp4", ".properties"));
    }

    private static String digest(URI uri) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(uri.toASCIIString().getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
