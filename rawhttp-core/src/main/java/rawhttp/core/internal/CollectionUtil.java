package rawhttp.core.internal;

import java.util.ArrayList;
import java.util.List;

public final class CollectionUtil {

    public static <T> List<T> append(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

}
