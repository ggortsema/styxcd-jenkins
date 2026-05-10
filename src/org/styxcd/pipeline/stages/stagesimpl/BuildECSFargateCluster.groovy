package org.styxcd.pipeline.stages.stagesimpl

class BuildECSFargateCluster implements Serializable {

    def steps

    public BuildECSFargateCluster(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'build ecs fargate cluster'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def awsRegion = 'us-east-1'
        def clusterName = 'styxcd-ecs-fargate-dev'

        steps.echo "building ecs fargate cluster"

        steps.withCredentials([
            steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            def clusterStatus = steps.sh(
                script: "aws ecs describe-clusters --region ${awsRegion} --clusters ${clusterName} --query 'clusters[0].status' --output text 2>/dev/null || echo NOT_FOUND",
                returnStdout: true
            ).trim()

            if (clusterStatus == 'ACTIVE') {
                steps.echo "cluster already exists"
            } else {

                steps.echo "creating ecs cluster"

                steps.sh '''
                    aws ecs create-cluster                       --cluster-name ''' + clusterName + '''                       --region ''' + awsRegion + '''
                '''
            }

            steps.echo "ecs fargate cluster build complete"
        }
    }
}
