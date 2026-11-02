package rel.mixed;

public class JavaSupport extends JavaBase implements JavaListener {
    public static final int MAX = 5;
    public int limit;
    private String name = "";

    public JavaSupport() {
        super("base");
    }

    public JavaSupport(String name) {
        super(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static String shout(String text) {
        return text.toUpperCase();
    }

    public static String shout(int times) {
        return "x".repeat(times);
    }

    public void onDone(JavaCallback callback) {
        callback.call("done");
    }

    @Override
    public void listen(String event) {
    }

    @Override
    public String describe() {
        return "support";
    }
}
