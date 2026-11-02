package rel.anon;

public class Factory {
    public Task create() {
        return new Task() {
            public void run() {
            }
        };
    }
}
