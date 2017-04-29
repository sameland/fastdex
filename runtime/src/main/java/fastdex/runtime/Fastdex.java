package fastdex.runtime;

import android.content.Context;

/**
 * Created by tong on 17/4/29.
 */
public class Fastdex {
    public static final String LOG_TAG = Fastdex.class.getSimpleName();

    public static class FastdexHolder {
        private static final Fastdex INSTANCE = new Fastdex();
    }

    public static Fastdex getInstance() {
        return FastdexHolder.INSTANCE;
    }

    public void init(Context context) {

    }
}
