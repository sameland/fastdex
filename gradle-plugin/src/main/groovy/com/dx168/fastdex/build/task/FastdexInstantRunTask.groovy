package com.dx168.fastdex.build.task

import com.dx168.fastdex.build.variant.FastdexVariant
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by tong on 17/3/12.
 */
public class FastdexInstantRunTask extends DefaultTask {
    FastdexVariant fastdexVariant

    FastdexInstantRunTask() {
        group = 'fastdex'
    }

    @TaskAction
    void clean() {

    }
}
