package org.styxcd.pipeline.workflow

import org.styxcd.pipeline.Constants

def createJsonStageList(yml, getStage) {

    def paramMap = [:]
    def jsonOutput = [:]
    def envList = ['dev', 'qa', 'stage', 'prod']


    paramMap['VALIDATE_MAP'] = preprocessYml(yml)

    jsonOutput['CloudWorkflowInitialize'] = getStage['CloudWorkflowInitialize'].getParams(yml, paramMap)

    yml.release.applications.spring.each {
        paramMap = [:]
        paramMap['APPHOST_NAME'] = it?.name

        if (it.build_tool == 'gradle') {
            jsonOutput["GradleBuild@${paramMap['APPHOST_NAME']}"] = getStage['GradleBuild'].getParams(yml, paramMap) }
//        } else {
//            jsonOutput["MvnSonar@${paramMap['APPHOST_NAME']}"] = getStage['MvnSonar'].getParams(yml, paramMap)
//        }
    }

    jsonOutput['CloudWorkflowCleanup@final'] = getStage['CloudWorkflowCleanup'].getParams(yml, paramMap)

    return jsonOutput
}

private Map preprocessYml(yml) {
    validateMap = [:]


    return validateMap
}

