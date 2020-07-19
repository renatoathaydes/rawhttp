package rawhttp.cli;

import rawhttp.cli.util.MediaTypeUtil;
import rawhttp.core.RawHttpHeaders;

import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Collections.singletonList;

final class FileLocator {

    private static final List<String> ANY_CONTENT_TYPE = singletonList("*/*");

    static final class FileResult {
        final File file;
        final RawHttpHeaders fileHttpHeaders;

        private FileResult(File file, RawHttpHeaders fileHttpHeaders) {
            this.file = file;
            this.fileHttpHeaders = fileHttpHeaders;
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
    private final Map<String, String> mimeByFileExtension;

    FileLocator(File rootDir, Map<String, String> mimeByFileExtension) {
        this.rootDir = rootDir;
        this.mimeByFileExtension = mimeByFileExtension;
    }

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
            return Optional.of(new FileResult(candidateFiles[0], fileHttpHeaders(candidateFiles[0])));
        }

        // try to match resources with the best possible accepted content-type
        Map<String, File> fileByContentType = groupCandidatesByMediaType(resourceNameDot, candidateFiles);

        // return the best matching content-type file
        List<String> acceptableMediaTypes = MediaTypeUtil.getSortedAcceptableMediaTypes(accept);
        for (String acceptableMediaType : acceptableMediaTypes) {
            File bestCandidate = fileByContentType.get(acceptableMediaType);
            if (bestCandidate != null) {
                return Optional.of(new FileResult(bestCandidate,
                        fileHttpHeaders(acceptableMediaType, bestCandidate.lastModified())));
            }
        }

        // no matching content-type found, return the first one
        return Optional.of(new FileResult(candidateFiles[0], fileHttpHeaders(candidateFiles[0])));
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
            return Optional.of(new FileResult(exactMatch, fileHttpHeaders(exactMatch)));
        }
        return Optional.empty();
    }

    private RawHttpHeaders fileHttpHeaders(File resource) {
        return fileHttpHeaders(mimeTypeOf(resource.getName()), resource.lastModified());
    }

    private static RawHttpHeaders fileHttpHeaders(String contentType, long lastModified) {
        return RawHttpHeaders.newBuilder()
                .with("Content-Type", contentType)
                .with("Last-Modified", toDateField(lastModified))
                .build();
    }

    private static String toDateField(long lastModified) {
        return RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneOffset.UTC));
    }

    String mimeTypeOf(String resourceName) {
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
