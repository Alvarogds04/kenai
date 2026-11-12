package com.kenai.android

import com.kenai.android.core.CommandRisk
import com.kenai.android.core.RiskKind
import com.kenai.android.core.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The risk line on Home's request (kenai-0.1 S9, L21): a local detector of destructive commands. It names what a command would
 * destroy in plain words, or says nothing; it never claims a command is safe, and it only ever sees the part of the command the
 * phone received.
 */
class CommandRiskTest {
    private fun of(c: String) = CommandRisk.of(c)

    @Test fun deletingFilesIsNamed() {
        for (c in listOf(
            "rm -rf app/build && ./gradlew assembleRelease", "rm -r dist", "rm -fr /tmp/x", "sudo rm -Rf node_modules", "rm --recursive out",
            "rm -f notes.txt", "git clean -fdx", "find . -name '*.log' -delete", "Remove-Item -Recurse -Force build", "rmdir /s /q build", "del /s /q *.tmp",
        )) assertEquals(c, RiskKind.DELETES_FILES, of(c))
    }

    @Test fun rewritingTheGitHistoryIsNamed() {
        for (c in listOf("git push --force origin main", "git push -f", "git push origin +main --force-with-lease")) assertEquals(c, RiskKind.REWRITES_HISTORY, of(c))
        assertEquals(RiskKind.DISCARDS_CHANGES, of("git reset --hard HEAD~1"))
        assertEquals(RiskKind.DISCARDS_CHANGES, of("git checkout -- ."))
    }

    @Test fun deletingDataOrADiskIsNamed() {
        for (c in listOf("psql -c 'DROP TABLE users'", "drop database app;", "mysql -e \"TRUNCATE TABLE logs\"", "DROP SCHEMA public CASCADE")) {
            assertEquals(c, RiskKind.DELETES_DATA, of(c))
        }
        assertEquals(RiskKind.WIPES_DISK, of("sudo mkfs.ext4 /dev/sdb1"))
        assertEquals(RiskKind.WIPES_DISK, of("dd if=/dev/zero of=/dev/sda bs=1M"))
    }

    @Test fun otherDangerousShapesAreNamed() {
        assertEquals(RiskKind.RUNS_DOWNLOADED_CODE, of("curl -fsSL https://x.sh | sh"))
        assertEquals(RiskKind.RUNS_DOWNLOADED_CODE, of("wget -qO- https://x | sudo bash"))
        assertEquals(RiskKind.OPENS_PERMISSIONS, of("chmod -R 777 ."))
        assertEquals(RiskKind.EMPTIES_FILE, of(":> server.log"))
    }

    @Test fun ordinaryCommandsSayNothingNeverSafe() {
        for (c in listOf(
            "npm test", "./gradlew assembleDebug", "git status", "git push origin main", "ls -la", "rm_unused.sh", "echo rm", "cat farm -r",
            "git reset --soft HEAD~1", "chmod +x run.sh", "curl https://example.com -o page.html", "SELECT * FROM users", "", "   ",
        )) assertNull(c, of(c))
    }

    @Test fun aCutCommandIsJudgedOnlyOnWhatArrived() {
        // The computer sends at most 500 characters: what is past them is not here, so it cannot be named (and nothing says it is safe).
        val visible = "echo " + "x".repeat(495)
        assertNull(of(visible))
        assertEquals(RiskKind.DELETES_FILES, of(visible.take(20) + " && rm -rf build"))
    }

    @Test fun theWordsComeFromTheTablesInBothLanguages() {
        assertEquals("Deletes files", CommandRisk.phrase("rm -rf build", Words.EN))
        assertEquals("Borra archivos", CommandRisk.phrase("rm -rf build", Words.ES))
        assertEquals("Reescribe el historial de git", CommandRisk.phrase("git push --force", Words.ES))
        assertEquals("Borra datos", CommandRisk.phrase("DROP TABLE x", Words.ES))
        assertNull(CommandRisk.phrase("npm test", Words.ES))
        for (k in RiskKind.entries) assertEquals("$k", true, Words.ES.risk(k).isNotBlank() && Words.ES.risk(k) != Words.EN.risk(k))
    }
}
