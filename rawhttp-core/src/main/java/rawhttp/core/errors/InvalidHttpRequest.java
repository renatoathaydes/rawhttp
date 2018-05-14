package rawhttp.core.errors;

public class InvalidHttpRequest extends RuntimeException {

    private final int lineNumber;

    public InvalidHttpRequest(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return "InvalidHttpRequest{" +
                "message='" + getMessage() + "', " +
                "lineNumber=" + lineNumber +
                '}';
    }
}
