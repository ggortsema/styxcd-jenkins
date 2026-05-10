package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['DummyWorkflowInitialize'] = getStageParams(getStage, 'DummyWorkflowInitialize', yml, paramMap)
    jsonOutput['DummyWorkflowBody'] = getStageParams(getStage, 'DummyWorkflowBody', yml, paramMap)
    jsonOutput['DummyWorkflowCleanup@final'] = getStageParams(getStage, 'DummyWorkflowCleanup', yml, paramMap)

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
