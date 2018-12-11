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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 */
@Mojo(
    name = "jdeps",
    defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class JDepsMojo extends AbstractMojo {
    private static final String[] NO_STRINGS = new String[0];

    @Parameter(defaultValue = "package", property = "jdeps.verbose")
    private String verbose;

    @Parameter(defaultValue = "archive", property = "jdeps.filter")
    private String filter;

    @Parameter(property = "jdeps.pattern")
    private String pattern;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter(property = "jdeps.multiRelease")
    private String multiRelease;

    @Parameter(defaultValue = "false", property = "jdeps.ignoreMissing")
    private boolean ignoreMissing;

    @Parameter(defaultValue = "false", property = "jdeps.transitive")
    private boolean transitive;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private String classesDir;

    /**
     * Construct a new instance.
     */
    public JDepsMojo() {
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        String verbose = this.verbose;
        if (verbose == null) verbose = "package";
        String filter = this.filter;
        if (filter == null) filter = "package";
        final Optional<ToolProvider> maybeJDeps = ToolProvider.findFirst("jdeps");
        if (! maybeJDeps.isPresent()) {
            throw new MojoFailureException("No 'jdeps' tool provider found");
        }
        final ToolProvider tp = maybeJDeps.get();

        final ArrayList<String> args = new ArrayList<>();
        if (verbose.equals("summary")) {
            args.add("-summary");
        } else {
            args.add("-verbose:" + verbose);
        }
        args.add("-filter:" + filter);
        if (pattern != null) {
            args.add("-filter");
            args.add(pattern);
        }
        if (multiRelease != null) {
            args.add("--multi-release");
            args.add(multiRelease);
        }
        if (ignoreMissing) {
            args.add("--ignore-missing-deps");
        }
        final MavenProject project = session.getCurrentProject();
        final Set<Artifact> artifacts = project.getArtifacts();
        if (transitive) {
            for (Artifact artifact : artifacts) {
                args.add(artifact.getFile().toString());
            }
        } else {
            final StringBuilder b = new StringBuilder();
            Iterator<Artifact> iterator = artifacts.iterator();
            if (iterator.hasNext()) {
                b.append(iterator.next().getFile());
                while (iterator.hasNext()) {
                    b.append(File.pathSeparatorChar);
                    b.append(iterator.next().getFile());
                }
                args.add("--class-path");
                args.add(b.toString());
            }
        }
        args.add(classesDir);
        final ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
        final PrintStream errorStream = new PrintStream(errorOutputStream);
        int result = tp.run(
            System.out,
            errorStream,
            args.toArray(NO_STRINGS)
        );
        errorStream.flush();
        final String errors = new String(errorOutputStream.toByteArray(), StandardCharsets.UTF_8);
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < errors.length(); i = errors.offsetByCodePoints(i, 1)) {
            final int cp = errors.codePointAt(i);
            if (cp == '\r') {
                // ignore
            } else if (cp == '\n') {
                getLog().error(b);
                b.setLength(0);
            } else {
                b.appendCodePoint(cp);
            }
        }
        if (b.length() > 0) {
            getLog().error(b);
        }
        if (result != 0) {
            throw new MojoFailureException("Dependency analysis failed");
        }
    }
}
