package org.styxcd.pipeline.workflow

def getMap() {
    def map = [:]

    map["dummy_workflow"] = new org.styxcd.pipeline.workflow.DummyWorkflow()

    return map
}
