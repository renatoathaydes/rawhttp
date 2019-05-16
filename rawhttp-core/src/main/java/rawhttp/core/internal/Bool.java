package rawhttp.core.internal;

/**
 * Boolean box without synchronization.
 */
public final class Bool {
    private boolean value;

    public boolean compareAndSet(boolean toCompare, boolean toSet) {
        boolean result = value == toCompare;
        value = toSet;
        return result;
    }

    public boolean getAndSet(boolean toSet) {
        boolean result = value;
        value = toSet;
        return result;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean value) {
        this.value = value;
    }
}
