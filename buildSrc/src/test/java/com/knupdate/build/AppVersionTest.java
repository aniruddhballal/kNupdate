package com.knupdate.build;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AppVersionTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    private AppVersion version() throws Exception {
        return AppVersion.parse("version=1.4.9\nversionCode=42\n");
    }

    @Test public void patchBuildAdvancesBothValues() throws Exception {
        assertEquals("1.4.10", version().next("patch").getName());
        assertEquals(43, version().next("patch").getCode());
    }

    @Test public void minorAndMajorResetLowerComponents() throws Exception {
        assertEquals("1.5.0", version().next("minor").getName());
        assertEquals("2.0.0", version().next("major").getName());
        assertEquals(43, version().next("major").getCode());
    }

    @Test public void eachReservationPersistsExactlyOneNewBuild() throws Exception {
        Path file = directory.newFile("version.properties").toPath();
        Files.writeString(file, "version=1.1.1\nversionCode=1\n");
        assertEquals("1.1.2", AppVersion.reserve(file, "patch").getName());
        assertEquals("1.1.2", AppVersion.read(file).getName());
        assertEquals(2, AppVersion.read(file).getCode());
        assertEquals("1.2.0", AppVersion.reserve(file, "minor").getName());
        assertEquals(3, AppVersion.read(file).getCode());
    }

    @Test public void readingForSyncAndTestsDoesNotChangeTheVersionFile() throws Exception {
        Path file = directory.newFile("version.properties").toPath();
        String original = "version=1.1.1\nversionCode=1\n";
        Files.writeString(file, original);
        AppVersion.read(file);
        AppVersion.read(file);
        assertEquals(original, Files.readString(file));
    }

    @Test public void onlyAppArtifactRequestsConsumeVersions() {
        for (String task : new String[] {"assembleDebug", ":app:assembleRelease", ":app:bundleRelease", "installDebug", "build", ":app:packageDebug"}) {
            assertTrue(task, AppVersion.buildsAppArtifact(task));
        }
        for (String task : new String[] {"help", "tasks", "clean", "testDebugUnitTest", "lintDebug", "assembleDebugAndroidTest", "connectedDebugAndroidTest", "uninstallDebug", "packageDebugResources", "bundleDebugClassesToCompileJar"}) {
            assertFalse(task, AppVersion.buildsAppArtifact(task));
        }
    }

    @Test public void invalidBumpLeavesTheOriginalFileUntouched() throws Exception {
        Path file = directory.newFile("version.properties").toPath();
        String original = "version=1.1.1\nversionCode=1\n";
        Files.writeString(file, original);
        assertThrows(IllegalArgumentException.class, () -> AppVersion.reserve(file, "automatic"));
        assertEquals(original, Files.readString(file));
    }

    @Test public void invalidVersionsAndCodeOverflowAreRejected() throws Exception {
        for (String name : new String[] {"1.0", "01.2.3", "1.2.-1", "1.2.3-beta", ""}) {
            assertThrows(IllegalArgumentException.class, () -> AppVersion.parse("version=" + name + "\nversionCode=1\n"));
        }
        assertThrows(IllegalArgumentException.class, () -> AppVersion.parse("version=1.2.3\nversionCode=0\n"));
        assertThrows(IllegalArgumentException.class, () -> AppVersion.parse("version=1.2.3\nversionCode=2100000000\n").next("patch"));
    }
}
