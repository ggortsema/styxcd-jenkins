package org.styxcd.pipeline.workflow

def getMap() {
    def map = [:]

    map["dummy_workflow"] = new org.styxcd.pipeline.workflow.DummyWorkflow()
    map["eks_workflow"] = new org.styxcd.pipeline.workflow.EKSWorkflow()
    map["cloud_workflow"] = new org.styxcd.pipeline.workflow.CloudWorkflow()

    return map
}
