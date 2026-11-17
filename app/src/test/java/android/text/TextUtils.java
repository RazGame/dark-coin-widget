package android.text;

/** JVM boundary used only by the SDK exception constructors exercised in BoundaryTest. */
public final class TextUtils {
    private TextUtils() {}

    public static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }
}
