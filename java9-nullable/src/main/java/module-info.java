/**
 * Small module providing the javax nullable annotations in a Java 9 module.
 * <p>
 * This allows Java 9 modules to be added to jlink even if using nullable annotations.
 */
module com.athaydes.nullable {
    exports javax.annotation;
}
