package rawhttp.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import rawhttp.core.RawHttpHeaders;

import static java.util.Collections.singletonList;

final class FileLocator {

    private static final Map<String, String> mimeByFileExtension;
    private static final List<String> ANY_CONTENT_TYPE = singletonList("*/*");

    static {
        Map<String, String> _mimeMapping = new HashMap<>(13);

        _mimeMapping.put("html", "text/html");
        _mimeMapping.put("txt", "text/plain");
        _mimeMapping.put("json", "application/json");
        _mimeMapping.put("js", "application/javascript");
        _mimeMapping.put("xml", "text/xml");
        _mimeMapping.put("jpg", "image/jpeg");
        _mimeMapping.put("jpeg", "image/jpeg");
        _mimeMapping.put("gif", "image/gif");
        _mimeMapping.put("png", "image/png");
        _mimeMapping.put("tif", "image/tiff");
        _mimeMapping.put("tiff", "image/tiff");
        _mimeMapping.put("ico", "image/x-icon");
        _mimeMapping.put("pdf", "application/pdf");
        _mimeMapping.put("css", "text/css");

        mimeByFileExtension = Collections.unmodifiableMap(_mimeMapping);
    }

    static final class FileResult {
        final File file;
        final RawHttpHeaders contentTypeHeader;

        private FileResult(File file, RawHttpHeaders contentTypeHeader) {
            this.file = file;
            this.contentTypeHeader = contentTypeHeader;
        }
    }

    private static final class PathData {
        final File dir;
        final String resourceName;

        PathData(File dir, String resourceName) {
            this.dir = dir;
            this.resourceName = resourceName;
        }
    }

    private final File rootDir;

    FileLocator(File rootDir) {
        this.rootDir = rootDir;
    }

    @SuppressWarnings("UnusedLabel")
    Optional<FileResult> find(String path, List<String> accept) {
        Optional<FileResult> result = findExactMatch(path);
        if (!result.isPresent()) {
            result = findWithExtension(path, accept);
        }
        return result;
    }

    // check if there's a resource with the same name, but an additional extension
    private Optional<FileResult> findWithExtension(String path, List<String> accept) {
        PathData pathData = getPathData(path);

        if (!pathData.dir.isDirectory()) {
            return Optional.empty();
        }

        // find files whose names start with the resource name + '.'
        String resourceNameDot = pathData.resourceName + '.';
        File[] candidateFiles = pathData.dir.listFiles(f -> f.getName().startsWith(resourceNameDot));

        if (candidateFiles == null || candidateFiles.length == 0) {
            return Optional.empty();
        }

        // if anything is acceptable, return the first possible match
        if (accept.isEmpty() || accept.equals(ANY_CONTENT_TYPE)) {
            return Optional.of(new FileResult(candidateFiles[0], contentTypeHeaderFor(candidateFiles[0].getName())));
        }

        // try to match resources with the best possible accepted content-type
        Map<String, File> fileByContentType = groupCandidatesByMediaType(resourceNameDot, candidateFiles);

        // return the best matching content-type file
        List<String> acceptableMediaTypes = getSortedAcceptableMediaTypes(accept);
        for (String acceptableMediaType : acceptableMediaTypes) {
            File bestCandidate = fileByContentType.get(acceptableMediaType);
            if (bestCandidate != null) {
                return Optional.of(new FileResult(bestCandidate, contentTypeHeaderWithValue(acceptableMediaType)));
            }
        }

        // no matching content-type found, return the first one
        return Optional.of(new FileResult(candidateFiles[0], contentTypeHeaderFor(candidateFiles[0].getName())));
    }

    private Map<String, File> groupCandidatesByMediaType(String resourceNameDot, File[] candidateFiles) {
        int extensionStartIndex = resourceNameDot.length();
        Map<String, File> fileByContentType = new HashMap<>(candidateFiles.length);

        for (File candidateFile : candidateFiles) {
            String extension = candidateFile.getName().substring(extensionStartIndex);
            String mime = mimeByFileExtension.get(extension);
            if (mime != null) {
                fileByContentType.put(mime, candidateFile);
            }
        }
        return fileByContentType;
    }

    private Optional<FileResult> findExactMatch(String path) {
        File exactMatch = new File(rootDir, path);
        if (exactMatch.isFile()) {
            return Optional.of(new FileResult(exactMatch, contentTypeHeaderFor(path)));
        }
        return Optional.empty();
    }

    /**
     * Perform a simplified parsing of the Accept header's values, just enough to rank them from
     * most preferred to least preferred.
     *
     * @param accept Accept header values
     * @return ranked media types from most preferred to least preferred
     */
    static List<String> getSortedAcceptableMediaTypes(List<String> accept) {
        class Item implements Comparable<Item> {
            private final String value;
            private final float quality;

            Item(String value, float quality) {
                this.value = value;
                this.quality = quality;
            }

            @Override
            public int compareTo(Item other) {
                return -Float.compare(this.quality, other.quality);
            }
        }

        List<Item> result = new ArrayList<>(accept.size() + 1);
        for (String acceptItem : accept) {
            String[] subItems = acceptItem.split(",");

            for (String subItem : subItems) {
                String[] itemPlusParams = subItem.split(";");
                String item = itemPlusParams[0].trim();
                if (item.isEmpty()) continue;

                float weight = 1.0f;

                // use the 'q' parameter if possible to set the weight
                for (int i = 1; i < itemPlusParams.length; i++) {
                    String[] param = itemPlusParams[i].split("=", 2);
                    String paramName = param[0].trim();
                    if (param.length == 2 && paramName.equalsIgnoreCase("q")) {
                        try {
                            weight = Float.parseFloat(param[1].trim());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                        break; // 'q' param found, break out
                    }
                }
                result.add(new Item(item, weight));
            }
        }
        return result.stream().sorted().map(i -> i.value).collect(Collectors.toList());
    }

    private static RawHttpHeaders contentTypeHeaderFor(String resourceName) {
        return contentTypeHeaderWithValue(mimeTypeOf(resourceName));
    }

    private static RawHttpHeaders contentTypeHeaderWithValue(String value) {
        return RawHttpHeaders.newBuilder()
                .with("Content-Type", value)
                .build();
    }

    static String mimeTypeOf(String resourceName) {
        int idx = resourceName.lastIndexOf('.');
        if (idx < 0 || idx == resourceName.length() - 1) {
            return "application/octet-stream";
        }
        String extension = resourceName.substring(idx + 1);
        return mimeByFileExtension.getOrDefault(extension, "application/octet-stream");
    }

    private PathData getPathData(String path) {
        int pathSeparatorIndex = path.lastIndexOf('/');
        String subDirName;
        String resourceName;
        if (pathSeparatorIndex >= 0 && pathSeparatorIndex < path.length() - 1) {
            subDirName = path.substring(0, pathSeparatorIndex);
            resourceName = path.substring(pathSeparatorIndex + 1);
        } else {
            subDirName = ".";
            resourceName = path;
        }

        return new PathData(new File(rootDir, subDirName), resourceName);
    }
}
