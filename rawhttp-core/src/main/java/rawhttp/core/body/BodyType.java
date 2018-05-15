package rawhttp.core.body;

import java.io.IOException;
import java.util.List;
import rawhttp.core.IOFunction;

/**
 * Type of HTTP message body.
 * <p>
 * This is a closed type with only 3 possible implementations:
 * <ul>
 * <li>{@link ContentLength}</li>
 * <li>{@link Encoded}</li>
 * <li>{@link CloseTerminated}</li>
 * </ul>
 */
public abstract class BodyType {

    private BodyType(Object token) {
        // hidden, so only sub-types declared within this class can exist
    }

    /**
     * Use this body type, mapping each possible implementation into a value of type @{link T}.
     *
     * @param useContentLength
     * @param useEncoded
     * @param useCloseTerminated
     * @param <T>                type of returned Object
     * @return the value returned by the selected mapping function
     * @throws IOException
     */
    public final <T> T use(IOFunction<ContentLength, T> useContentLength,
                           IOFunction<Encoded, T> useEncoded,
                           IOFunction<CloseTerminated, T> useCloseTerminated) throws IOException {
        if (this instanceof ContentLength) {
            return useContentLength.apply((ContentLength) this);
        }
        if (this instanceof Encoded) {
            return useEncoded.apply((Encoded) this);
        }
        if (this instanceof CloseTerminated) {
            return useCloseTerminated.apply((CloseTerminated) this);
        }
        throw new IllegalStateException("Unknown body type: " + this);
    }

    public static final class ContentLength extends BodyType {
        private final long bodyLength;

        public ContentLength(long bodyLength) {
            super(null);
            this.bodyLength = bodyLength;
        }

        public long getBodyLength() {
            return bodyLength;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ContentLength that = (ContentLength) other;
            return bodyLength == that.bodyLength;
        }

        @Override
        public int hashCode() {
            return (int) (bodyLength ^ (bodyLength >>> 32));
        }

        @Override
        public String toString() {
            return "ContentLength{" +
                    "value=" + bodyLength +
                    '}';
        }
    }

    public static final class Encoded extends BodyType {
        private final List<String> encodings;

        public Encoded(List<String> encodings) {
            super(null);
            this.encodings = encodings;
        }

        public List<String> getEncodings() {
            return encodings;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Encoded encoded = (Encoded) other;
            return encodings.equals(encoded.encodings);
        }

        @Override
        public int hashCode() {
            return encodings.hashCode();
        }

        @Override
        public String toString() {
            return "Encoded{" +
                    "values=" + encodings +
                    '}';
        }
    }

    public static final class CloseTerminated extends BodyType {
        public static final CloseTerminated INSTANCE = new CloseTerminated();

        private CloseTerminated() {
            super(null);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return 32;
        }

        @Override
        public String toString() {
            return "CloseTerminated{}";
        }
    }
}
