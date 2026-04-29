package org.styxcd.pipeline.stages.stagesimpl

class EKSWorkflowBuildImages implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowBuildImages(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'build images and push to ECS'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]
        stageSpecificMap['TEST_VALUE'] = "IT WORKED"

        def yml = params['YML']
        steps.echo "here is yml"
        steps.echo "${yml}"


        steps.echo "in build and push images"

        steps.withCredentials([
                steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def awsRegion = 'us-east-1'
            def awsAccountId = '359546647832'

            def backendRepoUrl = 'https://github.com/ggortsema/johnny-johnny.git'
            def frontendRepoUrl = 'https://github.com/ggortsema/johnny-johnny-ui.git'

            def backendDir = 'johnny-johnny'
            def frontendDir = 'johnny-johnny-ui'

            def ecrNamespace = 'johnny-johnny'
            def backendEcrRepo = "${ecrNamespace}/johnny-johnny-backend"
            def frontendEcrRepo = "${ecrNamespace}/johnny-johnny-ui"

            def ecrRegistry = "${awsAccountId}.dkr.ecr.${awsRegion}.amazonaws.com"
            def backendImage = "${ecrRegistry}/${backendEcrRepo}:latest"
            def frontendImage = "${ecrRegistry}/${frontendEcrRepo}:latest"

            def frontendApiUrl = 'http://api.johnny-johnny.mycroftai.org'

            def identity = steps.sh(
                    script: 'aws sts get-caller-identity',
                    returnStdout: true
            ).trim()
            steps.echo "AWS Identity: ${identity}"

            def backendRepoStatus = steps.sh(
                    script: "aws ecr describe-repositories --region ${awsRegion} --repository-names ${backendEcrRepo}",
                    returnStatus: true
            )
            steps.echo "Backend ECR repo status: ${backendRepoStatus}"

            if (backendRepoStatus != 0) {
                def createBackendRepoStatus = steps.sh(
                        script: "aws ecr create-repository --region ${awsRegion} --repository-name ${backendEcrRepo}",
                        returnStatus: true
                )
                steps.echo "Create backend ECR repo status: ${createBackendRepoStatus}"

                if (createBackendRepoStatus != 0) {
                    steps.error "Failed to create backend ECR repo with status: ${createBackendRepoStatus}"
                }
            }

            def frontendRepoStatus = steps.sh(
                    script: "aws ecr describe-repositories --region ${awsRegion} --repository-names ${frontendEcrRepo}",
                    returnStatus: true
            )
            steps.echo "Frontend ECR repo status: ${frontendRepoStatus}"

            if (frontendRepoStatus != 0) {
                def createFrontendRepoStatus = steps.sh(
                        script: "aws ecr create-repository --region ${awsRegion} --repository-name ${frontendEcrRepo}",
                        returnStatus: true
                )
                steps.echo "Create frontend ECR repo status: ${createFrontendRepoStatus}"

                if (createFrontendRepoStatus != 0) {
                    steps.error "Failed to create frontend ECR repo with status: ${createFrontendRepoStatus}"
                }
            }

            def ecrLoginStatus = steps.sh(
                    script: "aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${ecrRegistry}",
                    returnStatus: true
            )
            steps.echo "ECR login status: ${ecrLoginStatus}"

            if (ecrLoginStatus != 0) {
                steps.error "Failed to login to ECR with status: ${ecrLoginStatus}"
            }

            steps.sh(script: "rm -rf ${backendDir}", returnStatus: true)
            steps.sh(script: "rm -rf ${frontendDir}", returnStatus: true)

            def cloneBackendStatus = steps.sh(
                    script: "git clone ${backendRepoUrl}",
                    returnStatus: true
            )
            steps.echo "Clone backend repo status: ${cloneBackendStatus}"

            if (cloneBackendStatus != 0) {
                steps.error "Failed to clone backend repo with status: ${cloneBackendStatus}"
            }

            def cloneFrontendStatus = steps.sh(
                    script: "git clone ${frontendRepoUrl}",
                    returnStatus: true
            )
            steps.echo "Clone frontend repo status: ${cloneFrontendStatus}"

            if (cloneFrontendStatus != 0) {
                steps.error "Failed to clone frontend repo with status: ${cloneFrontendStatus}"
            }

            def javaVersionOutput = steps.sh(
                    script: 'java -version 2>&1',
                    returnStdout: true
            ).trim()
            steps.echo "Java version before backend build:\n${javaVersionOutput}"

            def mavenVersionOutput = steps.sh(
                    script: 'mvn -version',
                    returnStdout: true
            ).trim()
            steps.echo "Maven version before backend build:\n${mavenVersionOutput}"

            def backendBuildStatus = steps.sh(
                    script: "cd ${backendDir} && export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto && export PATH=\$JAVA_HOME/bin:\$PATH && mvn clean package -DskipTests",
                    returnStatus: true
            )
            steps.echo "Backend Maven build status: ${backendBuildStatus}"

            if (backendBuildStatus != 0) {
                steps.error "Backend Maven build failed with status: ${backendBuildStatus}"
            }

            def backendDockerBuildStatus = steps.sh(
                    script: "cd ${backendDir}/chat-api && docker build -t ${backendImage} .",
                    returnStatus: true
            )
            steps.echo "Backend Docker build status: ${backendDockerBuildStatus}"

            if (backendDockerBuildStatus != 0) {
                steps.error "Backend Docker build failed with status: ${backendDockerBuildStatus}"
            }

            def backendPushStatus = steps.sh(
                    script: "docker push ${backendImage}",
                    returnStatus: true
            )
            steps.echo "Backend Docker push status: ${backendPushStatus}"

            if (backendPushStatus != 0) {
                steps.error "Backend Docker push failed with status: ${backendPushStatus}"
            }

            def frontendDockerBuildStatus = steps.sh(
                    script: "cd ${frontendDir} && docker build --build-arg NEXT_PUBLIC_API_URL=${frontendApiUrl} -t ${frontendImage} .",
                    returnStatus: true
            )
            steps.echo "Frontend Docker build status: ${frontendDockerBuildStatus}"

            if (frontendDockerBuildStatus != 0) {
                steps.error "Frontend Docker build failed with status: ${frontendDockerBuildStatus}"
            }

            def frontendPushStatus = steps.sh(
                    script: "docker push ${frontendImage}",
                    returnStatus: true
            )
            steps.echo "Frontend Docker push status: ${frontendPushStatus}"

            if (frontendPushStatus != 0) {
                steps.error "Frontend Docker push failed with status: ${frontendPushStatus}"
            }

            steps.echo "Johnny backend and frontend images built and pushed successfully."
        }






    }
}
