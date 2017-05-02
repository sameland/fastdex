package com.dx168.fastdex.build.task

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.dx168.fastdex.build.util.FastdexRuntimeException
import com.dx168.fastdex.build.util.FastdexUtils
import com.dx168.fastdex.build.util.GradleUtils
import com.dx168.fastdex.build.util.MetaInfo
import com.dx168.fastdex.build.variant.FastdexVariant
import fastdex.build.lib.fd.Communicator
import fastdex.build.lib.fd.ServiceCommunicator
import fastdex.common.ShareConstants
import fastdex.common.fd.ProtocolConstants
import fastdex.common.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by tong on 17/3/12.
 */
public class FastdexInstantRunTask extends DefaultTask {
    FastdexVariant fastdexVariant
    File resourceApFile
    String resDir

    FastdexInstantRunTask() {
        group = 'fastdex'
    }

    private void waitForDevice(AndroidDebugBridge bridge) {
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 300) {
                throw new FastdexRuntimeException("Connect adb timeout!!")
            }
        }
    }

    IDevice preparedDevice() {
        AndroidDebugBridge.initIfNeeded(false)
        AndroidDebugBridge bridge =
                AndroidDebugBridge.createBridge("/Users/tong/Applications/android-sdk-macosx/platform-tools/adb", false)
        waitForDevice(bridge)
        IDevice[] devices = bridge.getDevices()
        IDevice device = null

        if (devices != null && devices.length > 0) {
            device = devices[0]
        }

        if (device == null) {
            throw new FastdexRuntimeException("Device not found!!")
        }

        if (devices.length > 1) {
            throw new FastdexRuntimeException("Find multiple devices!!")
        }
        project.logger.error("==fastdex device connected ${device.toString()}")
        return device
    }

    @TaskAction
    void instantRun() {
        IDevice device = preparedDevice()
        String packageName = GradleUtils.getPackageName(project.android.sourceSets.main.manifest.srcFile.absolutePath)
        ServiceCommunicator serviceCommunicator = new ServiceCommunicator(packageName)
        try {
            Boolean active = false
            MetaInfo runtimeMetaInfo = serviceCommunicator.talkToService(device, new Communicator<MetaInfo>() {
                @Override
                public MetaInfo communicate(DataInputStream input, DataOutputStream output) throws IOException {
                    output.writeInt(ProtocolConstants.MESSAGE_PING)

                    MetaInfo runtimeMetaInfo = new MetaInfo()
                    active = input.readBoolean()
                    runtimeMetaInfo.buildMillis = input.readLong()
                    runtimeMetaInfo.variantName = input.readUTF()
                    return runtimeMetaInfo
                }
            })
            project.logger.error("==fastdex receive: ${runtimeMetaInfo}")
            if (fastdexVariant.metaInfo.buildMillis != runtimeMetaInfo.buildMillis) {
                throw new IOException("buildMillis not equal")
            }
            if (!fastdexVariant.metaInfo.variantName.equals(runtimeMetaInfo.variantName)) {
                throw new IOException("variantName not equal")
            }

            File resourcesApk = new File(fastdexVariant.buildDir,ShareConstants.RESOURCE_APK_FILE_NAME)
            generateResourceApk(resourcesApk)
            File mergedPatchDex = FastdexUtils.getMergedPatchDex(fastdexVariant.project,fastdexVariant.variantName)
            File patchDex = FastdexUtils.getPatchDexFile(fastdexVariant.project,fastdexVariant.variantName)

            int changeCount = 1
            if (FileUtils.isLegalFile(mergedPatchDex)) {
                changeCount += 1
            }
            if (FileUtils.isLegalFile(patchDex)) {
                changeCount += 1
            }

            boolean result = serviceCommunicator.talkToService(device, new Communicator<Boolean>() {
                @Override
                public Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                    output.writeInt(ProtocolConstants.MESSAGE_PATCHES)
                    output.writeLong(0L)
                    output.writeInt(changeCount)

                    project.logger.error("==fastdex write ${ShareConstants.RESOURCE_APK_FILE_NAME}")
                    output.writeUTF(ShareConstants.RESOURCE_APK_FILE_NAME)
                    byte[] bytes = FileUtils.readContents(resourcesApk)
                    output.writeInt(bytes.length)
                    output.write(bytes)
                    if (FileUtils.isLegalFile(mergedPatchDex)) {
                        project.logger.error("==fastdex write ${mergedPatchDex}")
                        output.writeUTF(ShareConstants.MERGED_PATCH_DEX)
                        bytes = FileUtils.readContents(mergedPatchDex)
                        output.writeInt(bytes.length)
                        output.write(bytes)
                    }
                    if (FileUtils.isLegalFile(patchDex)) {
                        project.logger.error("==fastdex write ${patchDex}")
                        output.writeUTF(ShareConstants.PATCH_DEX)
                        bytes = FileUtils.readContents(patchDex)
                        output.writeInt(bytes.length)
                        output.write(bytes)
                    }

                    output.writeInt(ProtocolConstants.UPDATE_MODE_WARM_SWAP)
                    output.writeBoolean(true)

                    return input.readBoolean()
                }
            })
            if (result) {
                project.logger.error("==fastdex send patch data success.....")

                if (!active) {
                    startBootActivity(packageName)
                }
            }
            else {
                project.logger.error("==fastdex send patch data fail.....")
                normalRun(device,packageName,fastdexVariant.metaInfo.variantName)
            }
        } catch (IOException e) {
            if (fastdexVariant.configuration.debug) {
                e.printStackTrace()
            }
            //TODO 选择一个variant
            normalRun(device,packageName,"Debug")
        }
    }

    void normalRun(IDevice device,String packageName,String targetVariantName) {
        def targetVariant = null
        project.android.applicationVariants.all { variant ->
            def variantName = variant.name.capitalize()
            if (variantName.equals(targetVariantName)) {
                targetVariant = variant
            }
        }

        project.logger.error("==fastdex normal run ${targetVariantName}")
        //安装app
        File apkFile = targetVariant.outputs.first().getOutputFile()
        project.logger.error("==fastdex install apk cmd:\nadb install -r ${apkFile}")
        device.installPackage(apkFile.absolutePath,true)

        startBootActivity(packageName)
    }

    def startBootActivity(String packageName) {
        //启动第一个activity
        String bootActivityName = GradleUtils.getBootActivity(fastdexVariant.manifestPath)
        if (bootActivityName) {
            //$ adb shell am start -n "com.dx168.fastdex.sample/com.dx168.fastdex.sample.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
            def process = new ProcessBuilder(FastdexUtils.getAdbCmdPath(project),"shell","am","start","-n","\"${packageName}/${bootActivityName}\"","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER").start()
            int status = process.waitFor()
            try {
                process.destroy()
            } catch (Throwable e) {

            }

            String cmd = "adb shell am start -n \"${packageName}/${bootActivityName}\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
            if (fastdexVariant.configuration.debug) {
                project.logger.error("==fastdex start activity cmd:\n${cmd}")
            }
            if (status != 0) {
                throw new RuntimeException("==fastdex start activity fail: \n${cmd}")
            }
        }
    }

    def generateResourceApk(File resourcesApk) {
        long start = System.currentTimeMillis()
        File tempDir = new File(fastdexVariant.buildDir,"temp")
        FileUtils.cleanDir(tempDir)
        //File resourceAp = new File(project.buildDir,"intermediates${File.separator}res${File.separator}resources-debug.ap_")
        //java -cp {sdk.dir}/tools/lib/sdklib.jar com.android.sdklib.build.ApkBuilderMain resources.apk -z resources-debug.ap_
        String sdklibPath = new File(FastdexUtils.getSdkDirectory(project),"tools${File.separator}lib${File.separator}sdklib.jar").absolutePath

        def process = new ProcessBuilder(FastdexUtils.getJavaCmdPath(),"-cp",sdklibPath,"com.android.sdklib.build.ApkBuilderMain",resourcesApk.absolutePath,"-v","-u","-z",resourceApFile.absolutePath).start()
        int status = process.waitFor()
        try {
            process.destroy()
        } catch (Throwable e) {

        }

        String cmd = "java -cp ${sdklibPath} com.android.sdklib.build.ApkBuilderMain ${resourcesApk} -v -u -z ${resourceApFile}"
        if (fastdexVariant.configuration.debug) {
            project.logger.error("==fastdex create resources.apk cmd:\n${cmd}")
        }
        if (status != 0) {
            throw new RuntimeException("==fastdex generate resources.apk fail: \n${cmd}")
        }

        File assetsPath = fastdexVariant.androidVariant.getVariantData().getScope().getMergeAssetsOutputDir()
        List<String> assetFiles = getAssetFiles(assetsPath)
        if (assetFiles.isEmpty()) {
            return
        }
        File tempAssetsPath = new File(tempDir,"assets")
        FileUtils.copyDir(assetsPath,tempAssetsPath)

        String[] cmds = new String[assetFiles.size() + 4]
        cmds[0] = FastdexUtils.getAaptCmdPath(project)
        cmds[1] = "add"
        cmds[2] = "-f"
        cmds[3] = resourcesApk.absolutePath
        for (int i = 0; i < assetFiles.size(); i++) {
            cmds[4 + i] = "assets/${assetFiles.get(i)}";
        }

        ProcessBuilder aaptProcess = new ProcessBuilder(cmds)
        aaptProcess.directory(tempDir)
        process = aaptProcess.start()
        status = process.waitFor()
        try {
            process.destroy()
        } catch (Throwable e) {

        }

        cmd = cmds.join(" ")
        if (fastdexVariant.configuration.debug) {
            project.logger.error("==fastdex add asset files into resources.apk. cmd:\n${cmd}")
        }
        if (status != 0) {
            throw new RuntimeException("==fastdex add asset files into resources.apk fail. cmd:\n${cmd}")
        }
        long end = System.currentTimeMillis();
        fastdexVariant.project.logger.error("==fastdex generate resources.apk success: \n==${resourcesApk} use: ${end - start}ms")
    }

    List<String> getAssetFiles(File dir) {
        ArrayList<String> result = new ArrayList<>()
        if (dir == null || !FileUtils.dirExists(dir.getAbsolutePath())) {
            return result
        }
        if (dir.listFiles().length == 0) {
            return result
        }
        for (File file : dir.listFiles()) {
            if (file.isFile() && !file.getName().startsWith(".")) {
                result.add(file.getName())
            }
        }
        return result;
    }
}
