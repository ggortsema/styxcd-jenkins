package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {

    def paramMap = [:]
    def jsonOutput = [:]
    def envList = ['dev', 'qa', 'stage', 'prod']

    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['CloudWorkflowInitialize'] = getStageParams(getStage, 'CloudWorkflowInitialize', yml, paramMap)

    yml.release.applications.spring.each {
        paramMap = [:]
        paramMap['APPHOST_NAME'] = it?.name
        paramMap['VALIDATE_MAP'] = preprocessYml(yml)

        if (it.build_tool == 'gradle') {
            jsonOutput["GradleBuild@${paramMap['APPHOST_NAME']}"] = getStageParams(getStage, 'GradleBuild', yml, paramMap)
        }

//        } else {
//            jsonOutput["MvnSonar@${paramMap['APPHOST_NAME']}"] = getStageParams(getStage, 'MvnSonar', yml, paramMap)
//        }
    }

    jsonOutput['CloudWorkflowCleanup@final'] = getStageParams(getStage, 'CloudWorkflowCleanup', yml, paramMap)

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

