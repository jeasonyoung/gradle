/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.tooling.r22

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.ProjectConnection
import org.junit.Rule

@TargetGradleVersion(">=2.3")
class StandardStreamsCrossVersionSpec extends ToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()
    @Rule RedirectStdOutAndErr stdOutAndErr = new RedirectStdOutAndErr()

    def setup() {
        toolingApi.isEmbedded = false
    }

    def "logging is not sent to stderr/stdout if using custom output streams"() {
        String uuid = UUID.randomUUID().toString()
        file("build.gradle") << """
task log << {
    println "waiting-${uuid}"
    println "connecting to ${server.uri}"
    new URL("${server.uri}").text
    println "finished-${uuid}"
}
"""

        when:
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("log")
            build.run(resultHandler)
            server.waitFor()
            ConcurrentTestUtil.poll {
                // Need to poll, as logging output is delivered asynchronously to client
                assert output.toString().contains("waiting-" + uuid)
            }
            assert !output.toString().contains("finished-" + uuid)
            server.release()
            resultHandler.finished()
        }

        then:
        output.toString().contains("waiting-" + uuid)
        output.toString().contains("finished-" + uuid)
        !stdOutAndErr.stdOut.contains("waiting-" + uuid)
        !stdOutAndErr.stdOut.contains("finished-" + uuid)
    }
}
