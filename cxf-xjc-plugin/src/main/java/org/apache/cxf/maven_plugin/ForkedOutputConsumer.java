/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.maven_plugin;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Reads the stdout/stderr of the forked XJC process and passes the messages written by
 * {@link XJCBuildContext} on to the build context.
 */
class ForkedOutputConsumer implements StreamConsumer {
    private final BuildContext buildContext;
    private final Log log;

    ForkedOutputConsumer(BuildContext buildContext, Log log) {
        this.buildContext = buildContext;
        this.log = log;
    }

    @Override
    public void consumeLine(String line) {
        if (log.isDebugEnabled()) {
            log.debug(line);
        }
        XJCBuildContext.Message msg = XJCBuildContext.parseMessage(line);
        if (msg != null) {
            int severity = msg.severity == BuildContext.SEVERITY_ERROR
                ? BuildContext.SEVERITY_ERROR : BuildContext.SEVERITY_WARNING;
            buildContext.addMessage(msg.file, msg.line, msg.column, msg.text, severity, null);
        }
    }
}
