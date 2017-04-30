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
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Created by tong on 17/3/12.
 */
public class FastdexInstantRunTask extends DefaultTask {
    FastdexVariant fastdexVariant
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
            MetaInfo runtimeMetaInfo = serviceCommunicator.talkToService(device, new Communicator<MetaInfo>() {
                @Override
                public MetaInfo communicate(DataInputStream input, DataOutputStream output) throws IOException {
                    output.writeInt(ProtocolConstants.MESSAGE_PING)

                    MetaInfo runtimeMetaInfo = new MetaInfo()
                    boolean active = input.readBoolean()
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

                    output.writeUTF(ShareConstants.RESOURCE_APK_FILE_NAME)
                    byte[] bytes = FileUtils.readContents(resourcesApk)
                    output.writeInt(bytes.length)
                    output.writeBytes(bytes)

                    if (FileUtils.isLegalFile(mergedPatchDex)) {
                        output.writeUTF(ShareConstants.MERGED_PATCH_DEX)
                        bytes = FileUtils.readContents(mergedPatchDex)
                        output.writeInt(bytes.length)
                        output.writeBytes(bytes)
                    }
                    if (FileUtils.isLegalFile(patchDex)) {
                        output.writeUTF(ShareConstants.PATCH_DEX)
                        bytes = FileUtils.readContents(patchDex)
                        output.writeInt(bytes.length)
                        output.writeBytes(bytes)
                    }

                    output.writeInt(ProtocolConstants.UPDATE_MODE_WARM_SWAP)
                    output.writeBoolean(true)

                    return input.readBoolean()
                }
            })
            if (result) {
                project.logger.error("==fastdex instant run success.....")
                System.exit(0)
            }
        } catch (IOException e) {
            e.printStackTrace()

//            if (!"debug".equalsIgnoreCase(variant.buildType.name as String)) {
//                println "variant ${variant.name} is not debug, skip hack process."
//                return
//            } else if (!FreelineUtils.isEmpty(productFlavor) && !productFlavor.toString().equalsIgnoreCase(variant.flavorName)) {
//                println "variant ${variant.name} is not ${productFlavor}, skip hack process."
//                return
//            }
//
//            println "find variant ${variant.name} start hack process..."

            //TODO 选择一个variant
            normalRun("Debug")
        }
    }

    void normalRun(String targetVariantName) {
        def targetVariant = null
        project.android.applicationVariants.all { variant ->
            def variantName = variant.name.capitalize()

            if (variantName.equals(targetVariantName)) {
                targetVariant = variant
            }
        }

        project.logger.error("==fastdex normalRun ${targetVariantName}")
        //卸载已存在app
        //安装app
        //启动第一个activity
    }

    def generateResourceApk(File resourcesApk) {
        ZipOutputStream outputJarStream = null
        try {
            outputJarStream = new ZipOutputStream(new FileOutputStream(resourcesApk))

            Path resPath = new File(resDir).toPath()
            Files.walkFileTree(resPath,new SimpleFileVisitor<Path>(){
                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().endsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relativePath = resPath.relativize(file)
                    String entryName = "res/${relativePath.toString()}"
                    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                        entryName = entryName.replace("\\", "/");
                    }

                    ZipEntry e = new ZipEntry(entryName)
                    outputJarStream.putNextEntry(e)
                    byte[] bytes = FileUtils.readContents(file.toFile())
                    outputJarStream.write(bytes,0,bytes.length)
                    outputJarStream.closeEntry()
                    return FileVisitResult.CONTINUE
                }
            })

            Path assetsPath = new File(project.buildDir,"intermediates${File.separator}assets${File.separator}${fastdexVariant.androidVariant.dirName}").toPath()
            Files.walkFileTree(assetsPath,new SimpleFileVisitor<Path>(){
                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().endsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relativePath = assetsPath.relativize(file)
                    String entryName = "assets/${relativePath.toString()}"
                    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                        entryName = entryName.replace("\\", "/");
                    }

                    ZipEntry e = new ZipEntry(entryName)
                    outputJarStream.putNextEntry(e)
                    byte[] bytes = FileUtils.readContents(file.toFile())
                    outputJarStream.write(bytes,0,bytes.length)
                    outputJarStream.closeEntry()
                    return FileVisitResult.CONTINUE
                }
            })

            File resourceArsc = new File(project.buildDir,"intermediates${File.separator}res${File.separator}resources-debug.ap_")
            ZipEntry e = new ZipEntry("resources.arsc")
            outputJarStream.putNextEntry(e)
            byte[] bytes = FileUtils.readContents(resourceArsc)
            outputJarStream.write(bytes,0,bytes.length)
            outputJarStream.closeEntry()
        } finally {
            if (outputJarStream != null) {
                outputJarStream.close();
            }
        }
    }
}
