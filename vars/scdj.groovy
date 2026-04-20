def call(body) {
    def pipelineParams = [:]
    def yml
    def getWorkflow = new org.styxcd.pipeline.workflow.WorkflowMap().getMap()
    def ralfJson
    def keyMaps = [:]
    def config


    node {
        def configString = libraryResource "config.yml"
        config = readYaml text: configString
        echo "Current version: ${config.styxcd_jenkins.version}"
    }

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    echo "IN scdj with ${pipelineParams}"

    if(pipelineParams.yml) {
        yml_string = pipelineParams.yml
        echo "HERE IS THE YML STRING WE ARE GETTING:"
        echo yml_string
        yml = readYaml text: yml_string

    }

    echo "Declarative Specification:\n\n${yml_string}"

    def featureFlags = new org.styxcd.pipeline.FeatureFlags(this, env.STYXCD_FEATURE_FLAGS, yml.feature_flags)
    featureFlags.prettyPrint()

    def workflowString = yml?.workflow ?: 'pcf_workflow'
    def workflow = getWorkflow[workflowString]
    if (!workflow) {
        error("there is no workflow defined for ${yml.workflow}.")
    }
    echo "workflow is ${yml.workflow}"

    //Here we call the workflow or at some point an external source to get the json list of stages and
    //parameters
    def getStage = new org.styxcd.pipeline.stages.StageMap().getMap(this, featureFlags)
    ralfJson = workflow.createJsonStageList(yml, getStage)
    echo "running this workflow: ${ralfJson}"
    def tryMap = [:]
    def finalMap = [:]

    //turn the JSON into maps and pull some pertinant information from the strings. We break things up into things
    //that can run normally and things that need to run in a finally block
    ralfJson.each { key, value ->
        def index = key.indexOf('@')
        def endString
        if (index != -1) {
            endString = key.substring(index)
        }
        if (endString && endString.contains('final')) {
            finalMap[key] = value
        } else {
            tryMap[key] = value
        }
    }

    //run the stages in order from the maps we created and then run the final stages in a finally block
    //for things like cleanup etc.

    def stageWrapper = new org.styxcd.pipeline.stages.StageWrapper(this)

    stageWrapper.initializeBuildInformation(featureFlags.getEnabledFlags(), keyMaps)

    try {
        tryMap.each { key, value ->
            def index = key.indexOf('@')

            if (index != -1) {
                key = key.substring(0, index)
            }
            stageWrapper.run(value, keyMaps, getStage, key)
        }
    } finally {
        finalMap.each { key, value ->
            def index = key.indexOf('@')

            if (index != -1) {
                key = key.substring(0, index)
            }
            stageWrapper.run(value, keyMaps, getStage, key)
        }
    }
}