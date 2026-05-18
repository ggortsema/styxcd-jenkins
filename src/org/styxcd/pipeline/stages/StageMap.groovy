package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["cloud_workflow:initialize"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowInitialize(steps, featureFlags) }
    map["cloud_workflow:gradle_build"] = { new org.styxcd.pipeline.stages.stagesimpl.GradleBuild(steps, featureFlags) }
    map["cloud_workflow:cleanup"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowCleanup(steps, featureFlags) }

    return map
}
