package org.styxcd.pipeline.stages

def getMap(steps, featureFlags) {
    def map = [:]

    map["GradleBuild"] = { new org.styxcd.pipeline.stages.stagesimpl.GradleBuild(steps, featureFlags) }

    map["CloudWorkflowInitialize"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowInitialize(steps, featureFlags) }
    map["CloudWorkflowCleanup"] = { new org.styxcd.pipeline.stages.stagesimpl.CloudWorkflowCleanup(steps, featureFlags) }

    map["GkeCreateNamespace"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeCreateNamespace(steps, featureFlags) }
    map["GkeDeployApplication"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeDeployApplication(steps, featureFlags) }
    map["GkeCreateIngress"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeCreateIngress(steps, featureFlags) }
    map["GkeConfigureDns"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeConfigureDns(steps, featureFlags) }
    map["GkeValidateService"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeValidateService(steps, featureFlags) }
    map["GkeValidateDeployment"] = { new org.styxcd.pipeline.stages.stagesimpl.GkeValidateDeployment(steps, featureFlags) }

    map["EksInstallLoadBalancerController"] = { new org.styxcd.pipeline.stages.stagesimpl.EksInstallLoadBalancerController(steps, featureFlags) }
    map["EksCreateNamespace"] = { new org.styxcd.pipeline.stages.stagesimpl.EksCreateNamespace(steps, featureFlags) }
    map["EksDeployApplication"] = { new org.styxcd.pipeline.stages.stagesimpl.EksDeployApplication(steps, featureFlags) }
    map["EksCreateIngress"] = { new org.styxcd.pipeline.stages.stagesimpl.EksCreateIngress(steps, featureFlags) }
    map["EksConfigureDns"] = { new org.styxcd.pipeline.stages.stagesimpl.EksConfigureDns(steps, featureFlags) }
    map["EksValidateService"] = { new org.styxcd.pipeline.stages.stagesimpl.EksValidateService(steps, featureFlags) }
    map["EksValidateDeployment"] = { new org.styxcd.pipeline.stages.stagesimpl.EksValidateDeployment(steps, featureFlags) }


    map["EKSWorkflowClusterBuild"] = { new org.styxcd.pipeline.stages.stagesimpl.EKSWorkflowClusterBuild(steps, featureFlags) }




    return map
}
