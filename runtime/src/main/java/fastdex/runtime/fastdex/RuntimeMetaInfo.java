package fastdex.runtime.fastdex;

import android.util.Log;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import fastdex.common.ShareConstants;
import fastdex.common.utils.FileUtils;
import fastdex.common.utils.SerializeUtils;
import fastdex.runtime.fd.Logging;

/**
 * Created by tong on 17/4/29.
 */
public class RuntimeMetaInfo {
    /**
     * 全量编译完成的时间
     */
    private long buildMillis;

    private String variantName;

    public long getBuildMillis() {
        return buildMillis;
    }

    public void setBuildMillis(long buildMillis) {
        this.buildMillis = buildMillis;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public void save(Fastdex fastdex) {
        File metaInfoFile = new File(fastdex.patchDirectory, ShareConstants.META_INFO_FILENAME);
        try {
            SerializeUtils.serializeTo(metaInfoFile,this);
        } catch (IOException e) {
            Log.e(Logging.LOG_TAG,e.getMessage());
        }
    }

    public static RuntimeMetaInfo load(Fastdex fastdex) {
        File metaInfoFile = new File(fastdex.patchDirectory, ShareConstants.META_INFO_FILENAME);
        try {
            return new Gson().fromJson(new String(FileUtils.readContents(metaInfoFile)),RuntimeMetaInfo.class);
        } catch (Throwable e) {
            Log.e(Logging.LOG_TAG,e.getMessage());
        }

        return null;
    }
}
