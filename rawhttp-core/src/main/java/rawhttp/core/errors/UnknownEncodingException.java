package rawhttp.core.errors;

public class UnknownEncodingException extends RuntimeException {
    private final String encodingName;

    public UnknownEncodingException(String encodingName) {
        this.encodingName = encodingName;
    }

    public String getEncodingName() {
        return encodingName;
    }

    @Override
    public String getMessage() {
        return "Unknown encoding: " + encodingName;
    }
}
