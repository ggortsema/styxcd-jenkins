package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["CloudWorkflowInitialize"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowInitialize(steps, featureFlags) }
    map["GradleBuild"] = { new org.styxcd.pipeline.stages.stagesimpl.GradleBuild(steps, featureFlags) }
    map["CloudWorkflowCleanup"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowCleanup(steps, featureFlags) }
    map["GkeCreateNamespace"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeCreateNamespace(steps, featureFlags) }
    map["GkeDeployApplication"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeDeployApplication(steps, featureFlags) }
    map["GkeCreateIngress"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeCreateIngress(steps, featureFlags) }
    map["GkeConfigureDns"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeConfigureDns(steps, featureFlags) }
    map["GkeValidateService"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeValidateService(steps, featureFlags) }
    map["GkeValidateDeployment"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeValidateDeployment(steps, featureFlags) }

    return map
}
