package org.styxcd.pipeline.stages.stagesimpl

class DeployECSFargateApplications implements Serializable {

    def steps

    public DeployECSFargateApplications(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'deploy ecs fargate applications'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def awsRegion = 'us-east-1'
        def clusterName = 'styxcd-ecs-fargate-dev'
        def serviceName = 'sample-service'

        steps.echo "deploying ecs fargate applications"

        steps.withCredentials([
            steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            steps.echo "registering task definition"

            steps.sh '''
                aws ecs register-task-definition                   --cli-input-json file://ecs-task-definition.json                   --region ''' + awsRegion + '''
            '''

            steps.echo "creating or updating ecs service"

            steps.sh '''
                aws ecs update-service                   --cluster ''' + clusterName + '''                   --service ''' + serviceName + '''                   --force-new-deployment                   --region ''' + awsRegion + ''' || true
            '''

            steps.echo "waiting for service stability"

            steps.sh '''
                aws ecs wait services-stable                   --cluster ''' + clusterName + '''                   --services ''' + serviceName + '''                   --region ''' + awsRegion + '''
            '''

            steps.echo "ecs fargate deployment complete"
        }
    }
}
