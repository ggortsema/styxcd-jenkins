package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['CloudWorkflowInitialize'] = getStage['CloudWorkflowInitialize'].getParams(yml, paramMap)
    jsonOutput['GradleBuild'] = getStage['GradleBuild'].getParams(yml, paramMap)
    jsonOutput['CloudWorkflowCleanup@final'] = getStage['CloudWorkflowCleanup'].getParams(yml, paramMap)



    return jsonOutput
}

private Map preprocessYml(yml) {
    def validateMap = [:]

    return validateMap
}
