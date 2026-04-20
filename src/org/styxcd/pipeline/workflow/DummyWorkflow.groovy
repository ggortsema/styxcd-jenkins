package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {
    def jsonOutput = [:]

    def paramMap = [:]
    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['DummyWorkflowBody'] = getStage['DummyWorkflowBody'].getParams(yml, paramMap)

    return jsonOutput
}

private Map preprocessYml(yml) {
    validateMap = [:]

    return validateMap
}
