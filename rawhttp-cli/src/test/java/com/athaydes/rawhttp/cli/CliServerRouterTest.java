package com.athaydes.rawhttp.cli;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CliServerRouterTest {

    private final String resourceName;
    private final String expectedMimeType;

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

    public CliServerRouterTest(String resourceName, String expectedMimeType) {
        this.resourceName = resourceName;
        this.expectedMimeType = expectedMimeType;
    }

    @Test
    public void correctMimeTypeIsSelectedFromFileExtension() {
        String mimeType = CliServerRouter.mimeTypeOf(resourceName);
        assertEquals(mimeType, expectedMimeType);
    }


}
