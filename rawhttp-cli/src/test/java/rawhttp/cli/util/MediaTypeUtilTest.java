package rawhttp.cli.util;

import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class MediaTypeUtilTest {

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
            List<String> result = MediaTypeUtil.getSortedAcceptableMediaTypes(accept);
            assertEquals("Example index: " + (i / 2), expectedSortedAccepts, result);
        }
    }
}
