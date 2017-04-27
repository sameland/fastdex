package com.dx168.fastdex.build.task

import com.android.ddmlib.IDevice
import com.android.tools.fd.common.ProtocolConstants
import com.dx168.fastdex.build.util.GradleUtils
import com.dx168.fastdex.build.util.MetaInfo
import com.github.typ0520.instantrun.client.Communicator
import com.github.typ0520.instantrun.client.ServiceCommunicator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by tong on 17/3/12.
 */
public class FastdexInstantRunTask extends DefaultTask {
    final List<FastdexVariantInstantRunTask> variantInstantRunTaskList = new ArrayList<>()
    //from FastdexConnectDeviceWithAdbTask
    IDevice device
    ServiceCommunicator serviceCommunicator

    FastdexInstantRunTask() {
        group = 'fastdex'
    }

    @TaskAction
    void instantRun() {
        String packageName = GradleUtils.getPackageName(project.android.sourceSets.main.manifest.srcFile.absolutePath)
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
        } catch (IOException e) {
            e.printStackTrace()
            //TODO 选择一个variant

        }
    }

    public void addVariantInstantRun(FastdexVariantInstantRunTask variantInstantRunTask) {
        variantInstantRunTaskList.add(variantInstantRunTask)
    }
}
