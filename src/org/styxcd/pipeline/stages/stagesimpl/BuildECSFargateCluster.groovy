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

        def yml = params['YML']

        /*
         * First-pass defaults.
         *
         * Next refactor:
         * - Pull these from the test YAML.
         * - Then later pull them from the CloudWorkflow desired-state YAML.
         */
        def awsRegion = getValue(yml, 'aws_region', 'us-east-1')
        def clusterName = getValue(yml, 'cluster_name', 'styxcd-ecs-fargate-dev')

        def vpcId = getValue(yml, 'vpc_id', '')
        def subnetIds = getListValue(yml, 'subnet_ids')
        def securityGroupIds = getListValue(yml, 'security_group_ids')

        def taskExecutionRoleName = getValue(yml, 'task_execution_role_name', 'ecsTaskExecutionRole')
        def logGroupName = getValue(yml, 'log_group_name', "/ecs/${clusterName}")

        /*
         * Optional ALB/target group validation.
         * If target_group_arn is blank, this stage will skip target group checks.
         */
        def targetGroupArn = getValue(yml, 'target_group_arn', '')

        steps.echo "building ecs fargate cluster"
        steps.echo "awsRegion=${awsRegion}"
        steps.echo "clusterName=${clusterName}"

        steps.withCredentials([
            steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            validateAwsIdentity(awsRegion)

            createOrValidateCluster(awsRegion, clusterName)

            validateVpcIfProvided(awsRegion, vpcId)

            validateSubnetsIfProvided(awsRegion, subnetIds)

            validateSecurityGroupsIfProvided(awsRegion, securityGroupIds)

            validateTaskExecutionRole(taskExecutionRoleName)

            createOrValidateLogGroup(awsRegion, logGroupName)

            validateTargetGroupIfProvided(awsRegion, targetGroupArn)

            steps.echo "ecs fargate cluster build/preflight validation complete"
        }
    }

    private void validateAwsIdentity(String awsRegion) {

        steps.echo "validating aws identity"

        def identity = steps.sh(
            script: "aws sts get-caller-identity --region ${awsRegion}",
            returnStdout: true
        ).trim()

        if (!identity) {
            steps.error "Unable to validate AWS identity."
        }

        steps.echo "aws identity validated: ${identity}"
    }

    private void createOrValidateCluster(String awsRegion, String clusterName) {

        steps.echo "creating or validating ecs cluster: ${clusterName}"

        def clusterStatus = steps.sh(
            script: "aws ecs describe-clusters --region ${awsRegion} --clusters ${clusterName} --query 'clusters[0].status' --output text 2>/dev/null || echo NOT_FOUND",
            returnStdout: true
        ).trim()

        if (clusterStatus == 'ACTIVE') {
            steps.echo "ecs cluster already exists and is ACTIVE"
            return
        }

        if (clusterStatus != 'NOT_FOUND' && clusterStatus != 'None') {
            steps.error "ecs cluster exists but is not ACTIVE. cluster=${clusterName}, status=${clusterStatus}"
        }

        steps.echo "ecs cluster not found. creating: ${clusterName}"

        def createStatus = steps.sh(
            script: "aws ecs create-cluster --cluster-name ${clusterName} --region ${awsRegion}",
            returnStatus: true
        )

        if (createStatus != 0) {
            steps.error "failed to create ecs cluster. cluster=${clusterName}, status=${createStatus}"
        }

        steps.echo "ecs cluster created: ${clusterName}"
    }

    private void validateVpcIfProvided(String awsRegion, String vpcId) {

        if (!vpcId?.trim()) {
            steps.echo "vpc_id not provided. skipping vpc validation."
            return
        }

        steps.echo "validating vpc: ${vpcId}"

        def state = steps.sh(
            script: "aws ec2 describe-vpcs --region ${awsRegion} --vpc-ids ${vpcId} --query 'Vpcs[0].State' --output text 2>/dev/null || echo NOT_FOUND",
            returnStdout: true
        ).trim()

        if (state != 'available') {
            steps.error "vpc validation failed. vpc=${vpcId}, state=${state}"
        }

        steps.echo "vpc validated: ${vpcId}"
    }

    private void validateSubnetsIfProvided(String awsRegion, List subnetIds) {

        if (!subnetIds || subnetIds.isEmpty()) {
            steps.echo "subnet_ids not provided. skipping subnet validation."
            return
        }

        steps.echo "validating subnets: ${subnetIds.join(',')}"

        subnetIds.each { subnetId ->

            def state = steps.sh(
                script: "aws ec2 describe-subnets --region ${awsRegion} --subnet-ids ${subnetId} --query 'Subnets[0].State' --output text 2>/dev/null || echo NOT_FOUND",
                returnStdout: true
            ).trim()

            if (state != 'available') {
                steps.error "subnet validation failed. subnet=${subnetId}, state=${state}"
            }

            def availableIpCount = steps.sh(
                script: "aws ec2 describe-subnets --region ${awsRegion} --subnet-ids ${subnetId} --query 'Subnets[0].AvailableIpAddressCount' --output text",
                returnStdout: true
            ).trim()

            steps.echo "subnet validated: ${subnetId}, availableIpAddressCount=${availableIpCount}"
        }
    }

    private void validateSecurityGroupsIfProvided(String awsRegion, List securityGroupIds) {

        if (!securityGroupIds || securityGroupIds.isEmpty()) {
            steps.echo "security_group_ids not provided. skipping security group validation."
            return
        }

        steps.echo "validating security groups: ${securityGroupIds.join(',')}"

        securityGroupIds.each { securityGroupId ->

            def groupId = steps.sh(
                script: "aws ec2 describe-security-groups --region ${awsRegion} --group-ids ${securityGroupId} --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo NOT_FOUND",
                returnStdout: true
            ).trim()

            if (groupId != securityGroupId) {
                steps.error "security group validation failed. securityGroup=${securityGroupId}, result=${groupId}"
            }

            steps.echo "security group validated: ${securityGroupId}"
        }
    }

    private void validateTaskExecutionRole(String taskExecutionRoleName) {

        steps.echo "validating ecs task execution role: ${taskExecutionRoleName}"

        def roleArn = steps.sh(
            script: "aws iam get-role --role-name ${taskExecutionRoleName} --query 'Role.Arn' --output text 2>/dev/null || echo NOT_FOUND",
            returnStdout: true
        ).trim()

        if (roleArn == 'NOT_FOUND' || roleArn == 'None' || !roleArn) {
            steps.error "ecs task execution role not found: ${taskExecutionRoleName}. Create it or pass task_execution_role_name in the YAML."
        }

        steps.echo "ecs task execution role validated: ${roleArn}"
    }

    private void createOrValidateLogGroup(String awsRegion, String logGroupName) {

        steps.echo "creating or validating cloudwatch log group: ${logGroupName}"

        def existingLogGroup = steps.sh(
            script: "aws logs describe-log-groups --region ${awsRegion} --log-group-name-prefix '${logGroupName}' --query \"logGroups[?logGroupName=='${logGroupName}'].logGroupName | [0]\" --output text 2>/dev/null || echo NOT_FOUND",
            returnStdout: true
        ).trim()

        if (existingLogGroup == logGroupName) {
            steps.echo "cloudwatch log group already exists: ${logGroupName}"
            return
        }

        def createStatus = steps.sh(
            script: "aws logs create-log-group --region ${awsRegion} --log-group-name '${logGroupName}'",
            returnStatus: true
        )

        if (createStatus != 0) {
            steps.error "failed to create cloudwatch log group: ${logGroupName}. status=${createStatus}"
        }

        steps.echo "cloudwatch log group created: ${logGroupName}"
    }

    private void validateTargetGroupIfProvided(String awsRegion, String targetGroupArn) {

        if (!targetGroupArn?.trim()) {
            steps.echo "target_group_arn not provided. skipping target group validation."
            return
        }

        steps.echo "validating target group: ${targetGroupArn}"

        def targetType = steps.sh(
            script: "aws elbv2 describe-target-groups --region ${awsRegion} --target-group-arns '${targetGroupArn}' --query 'TargetGroups[0].TargetType' --output text 2>/dev/null || echo NOT_FOUND",
            returnStdout: true
        ).trim()

        if (targetType != 'ip') {
            steps.error "target group must use target type 'ip' for ECS Fargate awsvpc mode. targetGroup=${targetGroupArn}, targetType=${targetType}"
        }

        steps.echo "target group validated for ecs fargate: ${targetGroupArn}"
    }

    private String getValue(def yml, String key, String defaultValue) {

        try {
            if (yml != null && yml[key] != null && yml[key].toString().trim()) {
                return yml[key].toString().trim()
            }
        } catch (Exception ignored) {
            // Keep this helper defensive because the temporary test YAML shape may change.
        }

        return defaultValue
    }

    private List getListValue(def yml, String key) {

        try {
            if (yml == null || yml[key] == null) {
                return []
            }

            if (yml[key] instanceof List) {
                return yml[key].collect { it.toString().trim() }.findAll { it }
            }

            return yml[key].toString().split(',').collect { it.trim() }.findAll { it }

        } catch (Exception ignored) {
            return []
        }
    }
}
