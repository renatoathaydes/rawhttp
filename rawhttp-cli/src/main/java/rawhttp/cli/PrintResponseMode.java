package rawhttp.cli;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PrintResponseMode {
    RESPONSE, ALL, BODY, STATUS, STATS;

    static PrintResponseMode parseOption(String arg, String value) throws OptionsException {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OptionsException("Bad value for " + arg + " option.\n" +
                    "Acceptable values are " + Stream.of(values())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", ")));
        }
    }
}
