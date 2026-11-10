package app.rshmap.shizuku;

interface IInjector {
    /** Reserved by Shizuku: stop the user-service process. */
    void destroy() = 16777114;

    boolean ping() = 1;

    /** Inject one MotionEvent made of the first [count] pointers. Fire-and-forget for lowest latency. */
    oneway void inject(int action, int count, in int[] ids, in float[] xs, in float[] ys, long downTime, long eventTime) = 2;
}
