package com.frrnq.serverscore.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** One loader for the client process, shared across playback sessions so JNI is loaded once. */
final class VideoLibraries {
    private static final String BACKEND = "com.frrnq.serverscore.video.FfmpegVideoDecoder";
    private static final String[] JARS = {
        "javacv-1.5.10.jar", "javacpp-1.5.10.jar", "javacpp-1.5.10-windows-x86_64.jar",
        "ffmpeg-6.1.1-1.5.10.jar", "ffmpeg-6.1.1-1.5.10-windows-x86_64.jar"
    };
    private Class<? extends VideoDecoder> backend;

    VideoDecoder create(Path directory) throws ReflectiveOperationException, IOException {
        if (backend == null) {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch");
            if (!os.startsWith("windows") || !("amd64".equals(arch) || "x86_64".equals(arch))) {
                throw new IOException("VideoPlayer requires Windows and a 64-bit Java installation.");
            }
            URL[] urls = new URL[JARS.length];
            for (int i = 0; i < JARS.length; i++) {
                Path jar = directory.resolve(JARS[i]);
                if (!Files.isRegularFile(jar)) { throw new IOException("Missing frrnq_video_libs/" + JARS[i]); }
                urls[i] = jar.toUri().toURL();
            }
            URLClassLoader loader = new URLClassLoader(urls, VideoLibraries.class.getClassLoader()) {
                @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!name.equals(BACKEND) && !name.startsWith("org.bytedeco.")) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> type = findLoadedClass(name);
                    if (type == null) { type = name.equals(BACKEND) ? loadBackend() : findClass(name); }
                    if (resolve) { resolveClass(type); }
                    return type;
                }

                private Class<?> loadBackend() throws ClassNotFoundException {
                    // LaunchWrapper's CodeSource can point to the individual class resource.
                    try (InputStream source = VideoLibraries.class.getResourceAsStream("FfmpegVideoDecoder.class")) {
                        if (source == null) { throw new ClassNotFoundException(BACKEND); }
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = source.read(buffer)) != -1) { bytes.write(buffer, 0, count); }
                        byte[] code = bytes.toByteArray();
                        return defineClass(BACKEND, code, 0, code.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(BACKEND, failure);
                    }
                }
            };
            try {
                backend = loader.loadClass(BACKEND).asSubclass(VideoDecoder.class);
            } catch (ClassNotFoundException | RuntimeException | LinkageError failure) {
                loader.close();
                throw failure;
            }
        }
        return backend.getDeclaredConstructor().newInstance();
    }
}
