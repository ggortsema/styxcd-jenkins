def call(body) {
    def pipelineParams = [:]
    def yml


    node {
        def configString = libraryResource "config.yml"
        config = readYaml text: configString
        echo "Current version: ${config.styxcd_jenkins.version}"
    }

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    echo "IN KBPM with ${pipelineParams}"
}