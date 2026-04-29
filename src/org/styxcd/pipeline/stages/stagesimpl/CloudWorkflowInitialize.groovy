package org.styxcd.pipeline.stages.stagesimpl

import org.styxcd.pipeline.utility.MetricsUtil

class CloudWorkflowInitialize implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps
    MetricsUtil metricsUtil

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public CloudWorkflowInitialize(steps, featureFlags) {
        this.steps = steps
        this.metricsUtil = new org.styxcd.pipeline.utility.MetricsUtil(steps)
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'initialize'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

      keyMaps['BUILD_STATUS'] = 'SUCCESS'

      steps.echo "IN INIT INISDE METHOD"
      steps.cleanWs()

      def startTime = System.currentTimeMillis()

      metricsUtil.addStageToSplunkMap(script, "CloudWorkflowInitialize*", startTime, null, keyMaps)
      steps.echo "out of metrics util in init"

      params.each { entry ->
          steps.echo "Key: ${entry.key} Value: ${entry.value}"
      }

      def validateMap = params['VALIDATE_MAP']

      def yml = params['YML']
      steps.echo "here is yml"
      steps.echo "${yml}"

      //workflow specific intilization stuff goes here
      steps.echo "in cloud workflow init stage"

      def endTime = System.currentTimeMillis()
      metricsUtil.addStageToSplunkMap(script, "CloudWorkflowInitialize*", startTime, endTime, keyMaps)
    }
}
