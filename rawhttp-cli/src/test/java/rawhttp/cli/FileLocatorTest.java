package rawhttp.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileLocatorTest {

    private final Path rootDir;
    private final FileLocator fileLocator;

    public FileLocatorTest() throws IOException {
        Map<String, String> mimeMapping = new HashMap<>(2);
        mimeMapping.put("json", "application/json");
        mimeMapping.put("xml", "text/xml");

        rootDir = Files.createTempDirectory(FileLocatorTest.class.getSimpleName());
        fileLocator = new FileLocator(rootDir.toFile(), mimeMapping);

        // create a few files for tests
        Files.write(rootDir.resolve("hello"), singletonList("Hello root"), CREATE);
        Files.createDirectories(rootDir.resolve("p1/p2"));
        Files.write(rootDir.resolve("p1/p2/hello"), singletonList("Hello p2"), CREATE);
        Files.write(rootDir.resolve("p1/hello.json"), singletonList("Hello JSON"), CREATE);
        Files.write(rootDir.resolve("p1/hello.xml"), singletonList("Hello XML"), CREATE);
    }

    @Test
    public void canFindFileExactMatch() {
        Optional<FileLocator.FileResult> result = fileLocator.find("hello", emptyList());
        assertTrue(result.isPresent());
        assertEquals(rootDir.resolve("hello").toFile(), result.get().file);
        assertEquals(singletonList("application/octet-stream"), result.get().contentTypeHeader.get("Content-Type"));
    }

    @Test
    public void canFindFileExactMatchInSubDirectory() {
        Optional<FileLocator.FileResult> result = fileLocator.find("p1/p2/hello", emptyList());
        assertTrue(result.isPresent());
        assertEquals(rootDir.resolve("p1/p2/hello").toFile(), result.get().file);
        assertEquals(singletonList("application/octet-stream"), result.get().contentTypeHeader.get("Content-Type"));
    }

    @Test
    public void canFindFileExactMatchWithExtensionInSubDirectory() {
        Optional<FileLocator.FileResult> result = fileLocator.find("p1/hello.json", emptyList());
        assertTrue(result.isPresent());
        assertEquals(rootDir.resolve("p1/hello.json").toFile(), result.get().file);
        assertEquals(singletonList("application/json"), result.get().contentTypeHeader.get("Content-Type"));

        result = fileLocator.find("p1/hello.xml", emptyList());
        assertTrue(result.isPresent());
        assertEquals(rootDir.resolve("p1/hello.xml").toFile(), result.get().file);
        assertEquals(singletonList("text/xml"), result.get().contentTypeHeader.get("Content-Type"));
    }

    @Test
    public void canFindFileWithNonExactMatch() {
        Optional<FileLocator.FileResult> result = fileLocator.find("p1/hello", emptyList());
        assertTrue(result.isPresent());

        // either JSON or XML file could be returned
        File jsonFile = rootDir.resolve("p1/hello.json").toFile();
        File xmlFile = rootDir.resolve("p1/hello.xml").toFile();
        File actualFile = result.get().file;

        if (actualFile.equals(jsonFile)) {
            assertEquals(singletonList("application/json"), result.get().contentTypeHeader.get("Content-Type"));
        } else if (actualFile.equals(xmlFile)) {
            assertEquals(singletonList("text/xml"), result.get().contentTypeHeader.get("Content-Type"));
        } else {
            fail("Found file is not as expected: " + actualFile);
        }
    }

    @Test
    public void canFindFileUsingAcceptHeader() {
        Optional<FileLocator.FileResult> result = fileLocator.find("p1/hello", singletonList("application/json"));
        assertTrue(result.isPresent());
        assertEquals(rootDir.resolve("p1/hello.json").toFile(), result.get().file);
        assertEquals(singletonList("application/json"), result.get().contentTypeHeader.get("Content-Type"));

        result = fileLocator.find("p1/hello", singletonList("text/xml"));
        assertTrue(result.isPresent());
        assertEquals(rootDir.resolve("p1/hello.xml").toFile(), result.get().file);
        assertEquals(singletonList("text/xml"), result.get().contentTypeHeader.get("Content-Type"));
    }

    @Test
    public void resourceIsReturnedIfAvailableEvenIfNotMatchingAcceptHeader() {
        Optional<FileLocator.FileResult> result = fileLocator.find("p1/hello", singletonList("image/gif"));
        assertTrue(result.isPresent());

        // either JSON or XML file could be returned
        File jsonFile = rootDir.resolve("p1/hello.json").toFile();
        File xmlFile = rootDir.resolve("p1/hello.xml").toFile();
        File actualFile = result.get().file;

        if (actualFile.equals(jsonFile)) {
            assertEquals(singletonList("application/json"), result.get().contentTypeHeader.get("Content-Type"));
        } else if (actualFile.equals(xmlFile)) {
            assertEquals(singletonList("text/xml"), result.get().contentTypeHeader.get("Content-Type"));
        } else {
            fail("Found file is not as expected: " + actualFile);
        }
    }

    @Test
    public void cannotFindFileThatDoesNotMatch() {
        Optional<FileLocator.FileResult> result = fileLocator.find("hello.json", emptyList());
        assertFalse(result.isPresent());

        result = fileLocator.find("p1/hello.json.json", emptyList());
        assertFalse(result.isPresent());

        result = fileLocator.find("p1/hello.xml.json", emptyList());
        assertFalse(result.isPresent());

        result = fileLocator.find("does/not/exist", emptyList());
        assertFalse(result.isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void canSplitupAcceptHeadersIntoPreferredMediaTypes() {
        List[] examples = new List[]{
                emptyList(), emptyList(),
                singletonList(""), emptyList(),
                singletonList("*/*"), singletonList("*/*"),
                asList("xml", "json"), asList("xml", "json"),
                singletonList("json; q=0.4, xml"), asList("xml", "json"),
                singletonList("json; q=0.4, xml; q=0.5"), asList("xml", "json"),
                singletonList("json; q=0.8, xml; q=0.1"), asList("json", "xml"),
                singletonList("json; q=0.4;x=1, xml;a=20;q=0.5"), asList("xml", "json"),
                singletonList("json; q=0.8, xml; q=0.1"), asList("json", "xml"),
                asList("text/plain; q=0.5, text/html",
                        "text/x-dvi; q=0.8, text/x-c"), asList("text/html", "text/x-c", "text/x-dvi", "text/plain"),
        };

        for (int i = 0; i < examples.length; i += 2) {
            List<String> accept = examples[i];
            List<String> expectedSortedAccepts = examples[i + 1];
            List<String> result = FileLocator.getSortedAcceptableMediaTypes(accept);
            assertEquals("Example index: " + (i / 2), expectedSortedAccepts, result);
        }
    }
}
