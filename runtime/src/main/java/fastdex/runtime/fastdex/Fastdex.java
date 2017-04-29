package fastdex.runtime.fastdex;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import fastdex.common.ShareConstants;
import fastdex.common.utils.FileUtils;
import fastdex.runtime.Constants;

/**
 * Created by tong on 17/4/29.
 */
public class Fastdex {
    public static final String LOG_TAG = Fastdex.class.getSimpleName();

    private static Fastdex instance;

    final RuntimeMetaInfo runtimeMetaInfo;
    final File patchDirectory;
    final File tempDirectory;
    final File dexDirectory;
    final File resourceDirectory;
    private boolean fastdexEnabled = true;

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

        RuntimeMetaInfo metaInfo = RuntimeMetaInfo.load(this);
        if (metaInfo == null) {
            try {
                InputStream is = applicationContext.getAssets().open(ShareConstants.META_INFO_FILENAME);
                File metaInfoFile = new File(patchDirectory, ShareConstants.META_INFO_FILENAME);
                FileUtils.write2file(FileUtils.readStream(is),metaInfoFile);
                metaInfo = RuntimeMetaInfo.load(this);
            } catch (Throwable e) {
                Log.d(LOG_TAG,"fastdex disabled: " + e.getMessage());
            }
        }
        this.runtimeMetaInfo = metaInfo;
        if (this.runtimeMetaInfo == null) {
            fastdexEnabled = false;

            FileUtils.cleanDir(patchDirectory);
            FileUtils.cleanDir(tempDirectory);
        }
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public void onAttachBaseContext() {
        FileUtils.cleanDir(tempDirectory);
    }

    public File getTempDirectory() {
        return tempDirectory;
    }

    public boolean isFastdexEnabled() {
        return fastdexEnabled;
    }
}
