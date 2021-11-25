package rawhttp.cli;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CliServerRouterTest {
    @Test
    public void canTellIfFileIsModifiedSinceDate() {
        assertFalse(CliServerRouter.isModified(0L, "Sat, 29 Oct 1994 19:43:31 GMT"));

        assertTrue(CliServerRouter.isModified(
                Instant.parse("2007-12-03T10:15:30.00Z").toEpochMilli(),
                "Sat, 29 Oct 1994 19:43:31 GMT"));

        assertFalse(CliServerRouter.isModified(
                Instant.parse("1994-10-29T19:43:31.00Z").toEpochMilli(),
                "Sat, 29 Oct 1994 19:43:31 GMT"));

        assertTrue(CliServerRouter.isModified(
                Instant.parse("1994-10-29T19:43:31.01Z").toEpochMilli(),
                "Sat, 29 Oct 1994 19:43:31 GMT"));
    }
}
