package user;

import shared.Context;
import shared.Counter;

public class Caller {
    private Context held;
    private Counter counter;

    int flag() {
        return Context.FLAG + held.count() + held.name().length() + counter.getTotal();
    }
}
