package com.dx168.fastdex.build.task

import com.android.ddmlib.IDevice
import com.dx168.fastdex.build.util.GradleUtils
import com.dx168.fastdex.build.util.MetaInfo
import com.dx168.fastdex.build.variant.FastdexVariant
import fastdex.build.lib.fd.Communicator
import fastdex.build.lib.fd.ServiceCommunicator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by tong on 17/3/12.
 */
public class FastdexInstantRunTask extends DefaultTask {
    FastdexVariant fastdexVariant;
    IDevice device
    ServiceCommunicator serviceCommunicator
    String packageName

    FastdexInstantRunTask() {
        group = 'fastdex'
    }

    @TaskAction
    void instantRun() {
        packageName = GradleUtils.getPackageName(project.android.sourceSets.main.manifest.srcFile.absolutePath)
        serviceCommunicator = new ServiceCommunicator(packageName)
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


            if (fastdexVariantInstantRunTask == null) {
                project.logger.error("==fastdex ${runtimeMetaInfo.variantName} not found!")
                throw new IOException("")
            }
            else {
                project.logger.error("==fastdex instant run for ${runtimeMetaInfo.variantName}")
                fastdexVariantInstantRunTask.execute()
            }
        } catch (IOException e) {

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
        targetVariant.assemble.invoke()
        //卸载已存在app
        //安装app
        //启动第一个activity
    }
}
