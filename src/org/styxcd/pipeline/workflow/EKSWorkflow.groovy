package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['DummyWorkflowInitialize'] = getStage['DummyWorkflowInitialize'].getParams(yml, paramMap)
    jsonOutput['DummyWorkflowBody'] = getStage['DummyWorkflowBody'].getParams(yml, paramMap)
    jsonOutput['DummyWorkflowCleanup@final'] = getStage['DummyWorkflowCleanup'].getParams(yml, paramMap)

    return jsonOutput
}

private Map preprocessYml(yml) {
    def validateMap = [:]

    return validateMap
}
