package rawhttp.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class FileLocatorMimeMappingTest {

    private final String resourceName;
    private final String expectedMimeType;
    private final FileLocator fileLocator = new FileLocator(new File("."), CliServerRouter.mimeByFileExtension);

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
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

    public FileLocatorMimeMappingTest(String resourceName, String expectedMimeType) {
        this.resourceName = resourceName;
        this.expectedMimeType = expectedMimeType;
    }

    @Test
    public void correctMimeTypeIsSelectedFromFileExtension() {
        String mimeType = fileLocator.mimeTypeOf(resourceName);
        assertEquals(mimeType, expectedMimeType);
    }

}
