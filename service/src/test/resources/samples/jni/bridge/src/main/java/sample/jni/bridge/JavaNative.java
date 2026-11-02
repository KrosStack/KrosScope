package sample.jni.bridge;

public class JavaNative {
    public native int add(int left, int right);

    public int twice(int value) {
        return add(value, value);
    }
}
