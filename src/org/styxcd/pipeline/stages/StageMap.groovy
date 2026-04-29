package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["DummyWorkflowBody"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowBody(steps, featureFlags)
    map["DummyWorkflowInitialize"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowInitialize(steps, featureFlags)
    map["DummyWorkflowCleanup"] = new org.styxcd.pipeline.stages.stagesimpl.DummyWorkflowCleanup(steps, featureFlags)
    map["EKSWorkflowClusterTeardown"] = new org.styxcd.pipeline.stages.stagesimpl.EKSWorkflowClusterTeardown(steps, featureFlags)
    map["EKSWorkflowInitialize"] = new org.styxcd.pipeline.stages.stagesimpl.EKSWorkflowInitialize(steps, featureFlags)
    map["EKSWorkflowCleanup"] = new org.styxcd.pipeline.stages.stagesimpl.EKSWorkflowCleanup(steps, featureFlags)
    map["EKSWorkflowClusterBuild"] = new org.styxcd.pipeline.stages.stagesimpl.EKSWorkflowClusterBuild(steps, featureFlags)
    map["GradleBuild"] = new org.styxcd.pipeline.stages.stagesimpl.GradleBuild(steps, featureFlags)
    map["CloudWorkflowCleanup"] = new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowCleanup(steps, featureFlags)
    map["CloudWorkflowInitialize"] = new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowInitialize(steps, featureFlags)
    map["EKSWorkflowLoadBalancingController"] = new org.styxcd.pipeline.stages.stagesimpl.EKSWorkflowLoadBalancingController(steps, featureFlags)









    return map
}
