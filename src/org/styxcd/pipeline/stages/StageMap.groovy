package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["DummyWorkflowBody"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowBody(steps, featureFlags)

    return map
}
