def call(body) {
    def pipelineParams = [:]
    def yml

    echo "Reading configuration"



    node {
        echo "Reading configuration 2"

    }

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    echo "IN KBPM with ${pipelineParams}"
}