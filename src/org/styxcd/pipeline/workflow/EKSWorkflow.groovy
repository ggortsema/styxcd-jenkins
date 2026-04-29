package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['EKSWorkflowInitialize'] = getStage['EKSWorkflowInitialize'].getParams(yml, paramMap)
    //jsonOutput['EKSWorkflowClusterTeardown'] = getStage['EKSWorkflowClusterTeardown'].getParams(yml, paramMap)
    //jsonOutput['EKSWorkflowClusterBuild'] = getStage['EKSWorkflowClusterBuild'].getParams(yml, paramMap)
    //jsonOutput['EKSWorkflowLoadBalancingController'] = getStage['EKSWorkflowLoadBalancingController'].getParams(yml, paramMap)
    jsonOutput['EKSWorkflowDeployImages'] = getStage['EKSWorkflowDeployImages'].getParams(yml, paramMap)
    jsonOutput['EKSWorkflowCleanup@final'] = getStage['EKSWorkflowCleanup'].getParams(yml, paramMap)



    return jsonOutput
}

private Map preprocessYml(yml) {
    def validateMap = [:]

    return validateMap
}
