def call(body) {
    def pipelineParams = [:]
    def yml


    node {
        def configString = libraryResource "config.yml"
        def config = readYaml text: configString
        echo "Current version: ${config.styxcd_jenkins.version}"
    }

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    echo "IN scdj with ${pipelineParams}"
}