package org.styxcd.pipeline.stages.stagesimpl

class ScaleDownECSFargateApplications implements Serializable {

    def steps

    public ScaleDownECSFargateApplications(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'scale down ecs fargate applications'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def awsRegion = 'us-east-1'
        def clusterName = 'styxcd-ecs-fargate-dev'

        steps.echo "scaling down ecs fargate applications"

        steps.withCredentials([
            steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            def services = steps.sh(
                script: "aws ecs list-services --cluster ${clusterName} --region ${awsRegion} --query 'serviceArns[*]' --output text",
                returnStdout: true
            ).trim().split()

            services.each { serviceArn ->

                steps.echo "scaling down ${serviceArn}"

                steps.sh '''
                    aws ecs update-service                       --cluster ''' + clusterName + '''                       --service ''' + serviceArn + '''                       --desired-count 0                       --region ''' + awsRegion + '''
                '''
            }

            steps.echo "ecs fargate applications scaled down"
        }
    }
}
