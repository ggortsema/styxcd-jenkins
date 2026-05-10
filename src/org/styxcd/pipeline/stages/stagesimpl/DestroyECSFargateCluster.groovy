package org.styxcd.pipeline.stages.stagesimpl

class DestroyECSFargateCluster implements Serializable {

    def steps

    public DestroyECSFargateCluster(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'destroy ecs fargate cluster'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def awsRegion = 'us-east-1'
        def clusterName = 'styxcd-ecs-fargate-dev'

        steps.echo "destroying ecs fargate cluster"

        steps.withCredentials([
            steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            def services = steps.sh(
                script: "aws ecs list-services --cluster ${clusterName} --region ${awsRegion} --query 'serviceArns[*]' --output text",
                returnStdout: true
            ).trim().split()

            services.each { serviceArn ->

                steps.echo "deleting service ${serviceArn}"

                steps.sh '''
                    aws ecs delete-service                       --cluster ''' + clusterName + '''                       --service ''' + serviceArn + '''                       --force                       --region ''' + awsRegion + '''
                '''
            }

            steps.echo "deleting cluster"

            steps.sh '''
                aws ecs delete-cluster                   --cluster ''' + clusterName + '''                   --region ''' + awsRegion + '''
            '''

            steps.echo "ecs fargate cluster destroyed"
        }
    }
}
