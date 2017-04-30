package fastdex.runtime.fastdex;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import fastdex.common.ShareConstants;
import fastdex.common.utils.FileUtils;
import fastdex.runtime.Constants;
import fastdex.runtime.FastdexRuntimeException;
import fastdex.runtime.loader.TinkerResourcePatcher;

/**
 * Created by tong on 17/4/29.
 */
public class Fastdex {
    public static final String LOG_TAG = Fastdex.class.getSimpleName();

    private static Fastdex instance;

    final RuntimeMetaInfo runtimeMetaInfo;
    final File patchDirectory;
    final File tempDirectory;
//    final File dexDirectory;
//    final File resourceDirectory;
    private boolean fastdexEnabled = true;

    public static Fastdex get(Context context) {
        if (instance == null) {
            synchronized (Fastdex.class) {
                if (instance == null) {
                    instance = new Fastdex(context);
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
//        dexDirectory = new File(patchDirectory,Constants.DEX_DIR);
//        resourceDirectory = new File(patchDirectory,Constants.RES_DIR);

        RuntimeMetaInfo metaInfo = RuntimeMetaInfo.load(this);
        RuntimeMetaInfo assetsMetaInfo = null;
        try {
            InputStream is = applicationContext.getAssets().open(ShareConstants.META_INFO_FILENAME);
            String assetsMetaInfoJson = new String(FileUtils.readStream(is));
            assetsMetaInfo = RuntimeMetaInfo.load(assetsMetaInfoJson);
            if (assetsMetaInfo == null) {
                throw new NullPointerException("AssetsMetaInfo can not be null!!!");
            }
            Log.d(Fastdex.LOG_TAG,"load meta-info from assets: \n" + assetsMetaInfoJson);
            if (metaInfo == null) {
                assetsMetaInfo.save(this);
                metaInfo = assetsMetaInfo;
                File metaInfoFile = new File(patchDirectory, ShareConstants.META_INFO_FILENAME);
                if (!FileUtils.isLegalFile(metaInfoFile)) {
                    throw new FastdexRuntimeException("save meta-info fail: " + metaInfoFile.getAbsolutePath());
                }
            }
            else if (!metaInfo.equals(assetsMetaInfo)) {
                File metaInfoFile = new File(patchDirectory, ShareConstants.META_INFO_FILENAME);
                String metaInfoJson = new String(FileUtils.readContents(metaInfoFile));
                Log.d(Fastdex.LOG_TAG,"load meta-info from files: \n" + metaInfoJson);
                Log.d(Fastdex.LOG_TAG,"meta-info content changed clean");

                FileUtils.cleanDir(patchDirectory);
                FileUtils.cleanDir(tempDirectory);
                assetsMetaInfo.save(this);
                metaInfo = assetsMetaInfo;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            fastdexEnabled = false;
            Log.d(LOG_TAG,"fastdex disabled: " + e.getMessage());
        }

        this.runtimeMetaInfo = metaInfo;
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public void onAttachBaseContext() {
        if (!fastdexEnabled) {
            return;
        }
        if (!TextUtils.isEmpty(runtimeMetaInfo.getPreparedPatchPath())) {
            if (!TextUtils.isEmpty(runtimeMetaInfo.getLastPatchPath())) {
                FileUtils.deleteDir(new File(runtimeMetaInfo.getLastPatchPath()));
            }
            File preparedPatchDir = new File(runtimeMetaInfo.getPreparedPatchPath());
            File patchDir = new File(patchDirectory,preparedPatchDir.getName());
            try {
                FileUtils.copyDir(preparedPatchDir,patchDir);
            } catch (IOException e) {
                throw new FastdexRuntimeException(e);
            }
            runtimeMetaInfo.setLastPatchPath(runtimeMetaInfo.getPatchPath());
            runtimeMetaInfo.setPreparedPatchPath(null);
            runtimeMetaInfo.save(this);
        }

        if (TextUtils.isEmpty(runtimeMetaInfo.getPatchPath())) {
            return;
        }

        final File dexDirectory = new File(new File(runtimeMetaInfo.getPatchPath()),Constants.DEX_DIR);
        final File resourceDirectory = new File(new File(runtimeMetaInfo.getPatchPath()),Constants.RES_DIR);

        File resourceApkFile = new File(resourceDirectory,Constants.RESOURCE_APK_FILE_NAME);
        if (FileUtils.isLegalFile(resourceApkFile)) {
            Log.d(LOG_TAG,"");
            TinkerResourcePatcher.monkeyPatchExistingResources(applicationContext,resourceApkFile);
        }
    }

    public File getPatchDirectory() {
        return patchDirectory;
    }

    public File getTempDirectory() {
        return tempDirectory;
    }

    public RuntimeMetaInfo getRuntimeMetaInfo() {
        return runtimeMetaInfo;
    }

    public boolean isFastdexEnabled() {
        return fastdexEnabled;
    }
}
