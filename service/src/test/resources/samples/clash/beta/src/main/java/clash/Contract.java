package clash;

public interface Contract {
    void ping();

    abstract class Stub implements Contract {
    }
}
