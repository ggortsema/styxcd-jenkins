package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)


    jsonOutput['EKSWorkflowInitialize'] = getStage['EKSWorkflowInitialize'].getParams(yml, paramMap)

    if(yml?.teardown_env) {
        //jsonOutput['EKSWorkflowClusterTeardown'] = getStage['EKSWorkflowClusterTeardown'].getParams(yml, paramMap)
        jsonOutput['ScaleDownECSFargateApplications'] = getStage['ScaleDownECSFargateApplications'].getParams(yml, paramMap)
        jsonOutput['DestroyECSFargateCluster'] = getStage['DestroyECSFargateCluster'].getParams(yml, paramMap)
    }
    if(yml?.build_env) {
        //jsonOutput['EKSWorkflowClusterBuild'] = getStage['EKSWorkflowClusterBuild'].getParams(yml, paramMap)
        //jsonOutput['EKSWorkflowLoadBalancingController'] = getStage['EKSWorkflowLoadBalancingController'].getParams(yml, paramMap)
        jsonOutput['BuildECSFargateCluster'] = getStage['BuildECSFargateCluster'].getParams(yml, paramMap)
    }
    if(yml?.depploy_apps) {
        //jsonOutput['EKSWorkflowBuildImages'] = getStage['EKSWorkflowBuildImages'].getParams(yml, paramMap)
        //jsonOutput['EKSWorkflowDeployImages'] = getStage['EKSWorkflowDeployImages'].getParams(yml, paramMap)
        jsonOutput['DeployECSFargateApplications'] = getStage['DeployECSFargateApplications'].getParams(yml, paramMap)
    }



    jsonOutput['EKSWorkflowCleanup@final'] = getStage['EKSWorkflowCleanup'].getParams(yml, paramMap)



    return jsonOutput
}

private Map preprocessYml(yml) {
    def validateMap = [:]

    return validateMap
}
