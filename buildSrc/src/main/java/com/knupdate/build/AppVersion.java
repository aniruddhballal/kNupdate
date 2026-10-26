package com.knupdate.build;

import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Properties;

/** Version policy is independent of Git. Reserve once before configuring an APK build. */
public final class AppVersion {
    private final int major;
    private final int minor;
    private final int patch;
    private final int code;

    private AppVersion(int major, int minor, int patch, int code) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.code = code;
    }

    public String getName() { return major + "." + minor + "." + patch; }
    public int getCode() { return code; }

    public static AppVersion read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static AppVersion parse(String content) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(content));
        String name = properties.getProperty("version", "").trim();
        if (!name.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("version.properties must contain version=MAJOR.MINOR.PATCH");
        }
        String[] parts = name.split("\\.");
        try {
            int code = Integer.parseInt(properties.getProperty("versionCode", "").trim());
            if (code < 1 || code > 2_100_000_000) throw new NumberFormatException();
            return new AppVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), code);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Version components must fit integers; versionCode must be 1..2100000000", error);
        }
    }

    public AppVersion next(String bump) {
        int nextCode = Math.addExact(code, 1);
        if (nextCode > 2_100_000_000) throw new IllegalArgumentException("Android versionCode limit reached");
        switch (bump.toLowerCase(Locale.ROOT)) {
            case "major": return new AppVersion(Math.addExact(major, 1), 0, 0, nextCode);
            case "minor": return new AppVersion(major, Math.addExact(minor, 1), 0, nextCode);
            case "patch": return new AppVersion(major, minor, Math.addExact(patch, 1), nextCode);
            default: throw new IllegalArgumentException("versionBump must be patch, minor, or major");
        }
    }

    public static boolean buildsAppArtifact(String requestedTask) {
        String task = requestedTask.substring(requestedTask.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
        if (task.contains("androidtest") || task.contains("unittest") || task.contains("lint")) return false;
        if (task.endsWith("resources") || task.endsWith("assets") || task.contains("classes") || task.endsWith("jar") || task.endsWith("aar")) return false;
        if (task.equals("build") || task.equals("buildneeded") || task.equals("builddependents")) return true;
        return task.startsWith("assemble") || task.startsWith("bundle") || task.startsWith("package") ||
            task.startsWith("install");
    }

    public static AppVersion reserve(Path file, String bump) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path lockPath = absolute.resolveSibling(absolute.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            AppVersion next = read(absolute).next(bump);
            Path temporary = Files.createTempFile(absolute.getParent(), ".version-", ".tmp");
            try {
                String content = "# Single source of truth for Android metadata and the in-app version label.\n" +
                    "version=" + next.getName() + "\nversionCode=" + next.getCode() + "\n";
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
            return next;
        }
    }
}
