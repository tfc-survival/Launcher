package launcher.helper.function;

public interface ConsumerExc<A> {
    void apply(A v) throws Throwable;
}
