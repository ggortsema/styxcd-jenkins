package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["CloudWorkflowInitialize"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowInitialize(steps, featureFlags) }
    map["GradleBuild"] = { new org.styxcd.pipeline.stages.stagesimpl.GradleBuild(steps, featureFlags) }
    map["CloudWorkflowCleanup"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowCleanup(steps, featureFlags) }

    return map
}
