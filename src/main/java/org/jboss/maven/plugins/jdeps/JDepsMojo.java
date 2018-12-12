/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.maven.plugins.jdeps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.spi.ToolProvider;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 */
@Mojo(
    name = "jdeps",
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class JDepsMojo extends AbstractJDepsMojo {

    @Parameter(property = "jdeps.outputFile")
    private File outputFile;

    @Parameter(property = "jdeps.dot.output")
    private File dotOutputDir;

    /**
     * Construct a new instance.
     */
    public JDepsMojo() {
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        final ToolProvider tp = getJDepsToolProvider();
        final ArrayList<String> args = new ArrayList<>();
        if (dotOutputDir != null) {
            args.add("--dot-output");
            args.add(dotOutputDir.toString());
        }
        addToolArgs(args);
        final PrintStream errorStream = new PrintStream(new LoggingOutputStream(getLog()));
        try (PrintStream outputStream = new PrintStream(new PassThruLoggingOutputStream(getLog(), outputFile != null ? new FileOutputStream(outputFile) : System.out, outputFile != null))) {
            int result = tp.run(
                outputStream,
                errorStream,
                args.toArray(NO_STRINGS)
            );
            errorStream.flush();
            outputStream.flush();
            if (result != 0) {
                throw new MojoFailureException("Dependency analysis failed");
            }
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
