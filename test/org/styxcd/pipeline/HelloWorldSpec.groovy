package org.styxcd.pipeline

import spock.lang.Specification

class HelloWorldSpec extends Specification {

    def "hello world test proves Gradle and Spock are wired correctly"() {
        expect:
        "hello" + " world" == "hello world"
    }
}