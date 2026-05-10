package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['EKSWorkflowInitialize'] = getStageParams(getStage, 'EKSWorkflowInitialize', yml, paramMap)

    if (yml?.teardown_env) {
        jsonOutput['ScaleDownECSFargateApplications'] = getStageParams(getStage, 'ScaleDownECSFargateApplications', yml, paramMap)
        jsonOutput['DestroyECSFargateCluster'] = getStageParams(getStage, 'DestroyECSFargateCluster', yml, paramMap)
    }

    if (yml?.build_env) {
        jsonOutput['BuildECSFargateCluster'] = getStageParams(getStage, 'BuildECSFargateCluster', yml, paramMap)
    }

    if (yml?.deploy_apps) {
        jsonOutput['DeployECSFargateApplications'] = getStageParams(getStage, 'DeployECSFargateApplications', yml, paramMap)
    }

    jsonOutput['EKSWorkflowCleanup@final'] = getStageParams(getStage, 'EKSWorkflowCleanup', yml, paramMap)

    return jsonOutput
}

private def getStageParams(getStage, String stageKey, yml, paramMap) {
    def stageFactory = getStage[stageKey]

    if (stageFactory == null) {
        throw new RuntimeException("No stage registered for key: ${stageKey}")
    }

    def stageLogic = stageFactory()

    return stageLogic.getParams(yml, paramMap)
}

private Map preprocessYml(yml) {
    def validateMap = [:]

    return validateMap
}
