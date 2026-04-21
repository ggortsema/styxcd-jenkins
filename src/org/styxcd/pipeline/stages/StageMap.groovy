package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["DummyWorkflowBody"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowBody(steps, featureFlags)
    map["DummyWorkflowInitialize"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowInitialize(steps, featureFlags)
    map["DummyWorkflowCleanup"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowCleanup(steps, featureFlags)

    return map
}
