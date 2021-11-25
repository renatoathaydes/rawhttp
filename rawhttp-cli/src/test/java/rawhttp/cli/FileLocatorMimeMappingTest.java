package rawhttp.cli;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileLocatorMimeMappingTest {

    private final FileLocator fileLocator = new FileLocator(new File("."), CliServerRouter.mimeByFileExtension);

    public static Collection<String[]> data() {
        return Arrays.asList(new String[][]{
                {"a.html", "text/html"},
                {"b.txt", "text/plain"},
                {"cd.json", "application/json"},
                {".js", "application/javascript"},
                {"..xml", "application/xml"},
                {"e.jpg", "image/jpeg"},
                {"f.jpeg", "image/jpeg"},
                {"gg.gif", "image/gif"},
                {"h.png", "image/png"},
                {"i.tif", "image/tiff"},
                {".tiff", "image/tiff"},
                {"long-name121312312312.ico", "image/x-icon"},
                {".pdf", "application/pdf"},
                {".css", "text/css"},

                // unknown
                {"", "application/octet-stream"},
                {".", "application/octet-stream"},
                {"..", "application/octet-stream"},
                {"hello.", "application/octet-stream"},
                {".bin", "application/octet-stream"},
                {"whatever", "application/octet-stream"},
                {"whatever.com", "application/octet-stream"},
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void correctMimeTypeIsSelectedFromFileExtension(String resourceName, String expectedMimeType) {
        String mimeType = fileLocator.mimeTypeOf(resourceName);
        assertEquals(mimeType, expectedMimeType);
    }

}
