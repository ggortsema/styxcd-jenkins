package org.styxcd.pipeline.stages.stagesimpl

class GkeCreateIngress implements Serializable {
    def steps

    public GkeCreateIngress(steps, featureFlags) {
        this.steps = steps
    }

    public void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]
        stageSpecificMap['TEST_VALUE'] = "IT WORKED"

        def yml = params['YML']

        steps.echo "here is yml"
        steps.echo "${yml}"

        steps.echo "----- STAGE PARAMS -----"
        params.each { key, value ->
            steps.echo "${key} = ${value}"
        }
        steps.echo "------------------------"

        steps.echo "Running ${this.class.simpleName}"

        // TODO remove this parsing bridge later and get values directly from orchestrator
        yml.release?.environments?."${params['LIFECYCLE']}"?.each { target ->
            if (target?.name == params['TARGET_NAME']) {

                params['CLUSTER_NAME'] = target?.platform?.cluster_name
                params['PROJECT_ID'] = target?.platform?.project_id
                params['LOCATION'] = target?.platform?.location
                params['LOCATION_TYPE'] = target?.platform?.location_type
                params['NAMESPACE'] = target?.platform?.namespace
                params['CREDENTIALS_ID'] = target?.platform?.credentials?.id
                params['INGRESS_ENABLED'] = target?.platform?.ingress?.enabled
                params['INGRESS_NAME'] = target?.platform?.ingress?.name
                params['INGRESS_CLASS_NAME'] = target?.platform?.ingress?.class_name

                def hostsList = []

                target?.platform?.ingress?.hosts?.each { hostRule ->
                    def routesList = []

                    hostRule?.routes?.each { route ->
                        routesList << [
                                path    : route?.path,
                                pathType: route?.path_type,
                                service : route?.service,
                                port    : route?.port
                        ]
                    }

                    hostsList << [
                            host  : hostRule?.host,
                            routes: routesList
                    ]
                }

                params['INGRESS_HOSTS'] = hostsList
            }
        }

        def clusterName = params['CLUSTER_NAME']
        def projectId = params['PROJECT_ID']
        def location = params['LOCATION']
        def locationType = params['LOCATION_TYPE']
        def namespace = params['NAMESPACE']
        def credentialsId = params['CREDENTIALS_ID']
        def locationFlag = locationType == 'regional' ? '--region' : '--zone'

        def ingressName = params['INGRESS_NAME']
        def ingressClassName = params['INGRESS_CLASS_NAME']
        def ingressHosts = params['INGRESS_HOSTS']

        def gcloudConfig = "${steps.env.WORKSPACE}/.gcloud"
        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"

        steps.sh(script: "mkdir -p ${gcloudConfig}", returnStdout: true).trim()
        steps.sh(script: "mkdir -p ${steps.env.WORKSPACE}/.kube", returnStdout: true).trim()

        def authResult = null
        def credentialsResult = null

        steps.withCredentials([
                [$class: 'FileBinding', credentialsId: credentialsId, variable: 'GCP_KEY_FILE']
        ]) {

            authResult = steps.sh(
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} gcloud auth activate-service-account --key-file=\"\$GCP_KEY_FILE\"",
                    returnStdout: true
            ).trim()

            steps.echo("authResult: ${authResult}")

            credentialsResult = steps.sh(
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} KUBECONFIG=${kubeConfig} gcloud container clusters get-credentials ${clusterName} ${locationFlag} ${location} --project ${projectId}",
                    returnStdout: true
            ).trim()

            steps.echo("credentialsResult: ${credentialsResult}")
        }

        def nodesResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get nodes",
                returnStdout: true
        ).trim()

        steps.echo("nodesResult:")
        steps.echo(nodesResult)

        def rulesBlock = ingressHosts.collect { hostRule ->

            def pathsBlock = hostRule.routes.collect { route -> """
        - path: ${route.path}
          pathType: ${route.pathType}
          backend:
            service:
              name: ${route.service}
              port:
                number: ${route.port}
""" }.join('')

            return """
    - host: ${hostRule.host}
      http:
        paths:
${pathsBlock}
"""
        }.join('')

        def manifest = """
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${ingressName}
  namespace: ${namespace}
  annotations:
    kubernetes.io/ingress.class: "${ingressClassName}"
spec:
  rules:
${rulesBlock}
""".stripIndent()

        steps.echo("manifest:")
        steps.echo(manifest)

        def manifestFile = "${steps.env.WORKSPACE}/${ingressName}-gke-ingress.yaml"

        steps.writeFile(
                file: manifestFile,
                text: manifest
        )

        def applyResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} apply -f ${manifestFile}",
                returnStdout: true
        ).trim()

        steps.echo("applyResult:")
        steps.echo(applyResult)
    }
}