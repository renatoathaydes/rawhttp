package rawhttp.cli.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MediaTypeUtil {

    /**
     * Perform a simplified parsing of the Accept header's values, just enough to rank them from
     * most preferred to least preferred.
     *
     * @param accept Accept header values
     * @return ranked media types from most preferred to least preferred
     */
    public static List<String> getSortedAcceptableMediaTypes(List<String> accept) {
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

    private static final class Item implements Comparable<Item> {
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

}
