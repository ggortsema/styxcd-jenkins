package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['EKSWorkflowInitialize'] = getStage['EKSWorkflowInitialize'].getParams(yml, paramMap)
    jsonOutput['EKSWorkflowBody'] = getStage['EKSWorkflowBody'].getParams(yml, paramMap)
    jsonOutput['EKSWorkflowCleanup@final'] = getStage['EKSWorkflowCleanup'].getParams(yml, paramMap)

    return jsonOutput
}

private Map preprocessYml(yml) {
    def validateMap = [:]

    return validateMap
}
