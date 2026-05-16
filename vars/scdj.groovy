def call(body) {
    def pipelineParams = [:]
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

    def requestString = pipelineParams.styxcdRequest ?: params.STYXCD_REQUEST

    if (!requestString?.trim()) {
        error("Missing required STYXCD_REQUEST JSON string.")
    }

    def styxcdRequest = readJSON(text: requestString)

    def orchestratorUrl = styxcdRequest.orchestratorUrl ?: "http://orchestrator.styxcd.com"
    orchestratorUrl = orchestratorUrl.replaceAll('/+$', '')

    def executionId = styxcdRequest.executionId

    if (!executionId?.trim()) {
        error("Missing required field: executionId")
    }

    def callbackUrl = styxcdRequest.callbackUrl

    echo "Execution ID: ${executionId}"
    echo "Orchestrator URL: ${orchestratorUrl}"
    echo "Callback URL: ${callbackUrl ?: 'not provided'}"

    def featureFlags = new org.styxcd.pipeline.FeatureFlags(
            this,
            env.STYXCD_FEATURE_FLAGS ?: "",
            styxcdRequest.feature_flags
    )

    featureFlags.prettyPrint()

    def getStage = new org.styxcd.pipeline.stages.StageMap().getMap(this, featureFlags)

    def response = httpRequest(
            url: "${orchestratorUrl}/executions/${executionId}/plan",
            httpMode: 'GET',
            validResponseCodes: '200'
    )

    ralfJson = readJSON(text: response.content)

    if (!ralfJson || ralfJson.isEmpty()) {
        error("Execution plan was empty for executionId: ${executionId}")
    }
    echo "running this workflow: ${ralfJson}"

    def tryMap = [:]
    def finalMap = [:]

    ralfJson.each { key, value ->
        if (key.contains('@final')) {
            finalMap[key] = value
        } else {
            tryMap[key] = value
        }
    }

    def stageWrapper = new org.styxcd.pipeline.stages.StageWrapper(this)

    keyMaps['EXECUTION_ID'] = executionId
    keyMaps['CALLBACK_URL'] = callbackUrl
    keyMaps['ORCHESTRATOR_URL'] = orchestratorUrl
    stageWrapper.initializeBuildInformation(featureFlags.getEnabledFlags(), keyMaps)

    def normalizeStageName = { String key ->
        key.contains('@') ? key.substring(0, key.indexOf('@')) : key
    }

    def runStageMap = { Map stageMap ->
        stageMap.each { key, value ->
            def stageName = normalizeStageName(key as String)
            stageWrapper.run(value, keyMaps, getStage, stageName)
        }
    }

    try {
        runStageMap(tryMap)
    } finally {
        runStageMap(finalMap)
    }
}