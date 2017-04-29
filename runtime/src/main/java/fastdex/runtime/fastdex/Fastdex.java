package fastdex.runtime.fastdex;

import android.content.Context;
import java.io.File;
import fastdex.runtime.Constants;

/**
 * Created by tong on 17/4/29.
 */
public class Fastdex {
    public static final String LOG_TAG = Fastdex.class.getSimpleName();

    private static Fastdex instance;

    final File patchDirectory;
    final File tempDirectory;
    final File dexDirectory;
    final File resourceDirectory;

    public static Fastdex get(Context context) {
        if (instance == null) {
            synchronized (Fastdex.class) {
                if (instance == null) {
                    instance = new Fastdex(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private Context applicationContext;

    public Fastdex(Context applicationContext) {
        this.applicationContext = applicationContext;

        patchDirectory = new File(applicationContext.getFilesDir(), Constants.PATCH_DIR);
        tempDirectory = new File(patchDirectory,Constants.TEMP_DIR);
        dexDirectory = new File(patchDirectory,Constants.DEX_DIR);
        resourceDirectory = new File(patchDirectory,Constants.RES_DIR);
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public void onAttachBaseContext() {

    }
}
