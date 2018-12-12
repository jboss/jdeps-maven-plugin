package org.jboss.maven.plugins.jdeps;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 *
 */
public abstract class AbstractJDepsMojo extends AbstractMojo {
    static final String[] NO_STRINGS = new String[0];

    @Parameter(defaultValue = "package", property = "jdeps.verbose")
    private String verbose;
    @Parameter(defaultValue = "archive", property = "jdeps.filter")
    private String filter;
    @Parameter(property = "jdeps.filter.pattern")
    private String filterPattern;
    @Parameter(property = "jdeps.limit.pattern")
    private String pattern;
    @Parameter(property = "jdeps.limit.packages")
    private String[] packages;
    @Parameter(property = "jdeps.limit.modules")
    private String[] modules;
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;
    @Parameter(property = "jdeps.multiRelease")
    private String multiRelease;
    @Parameter(defaultValue = "false", property = "jdeps.ignoreMissing")
    private boolean ignoreMissing;
    @Parameter(defaultValue = "false", property = "jdeps.transitive")
    private boolean transitive;
    @Parameter(property = "jdeps.apiOnly")
    private boolean apiOnly;
    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private String classesDir;

    protected static ToolProvider getJDepsToolProvider() throws MojoExecutionException {
        final Optional<ToolProvider> maybeJDeps = ToolProvider.findFirst("jdeps");
        if (! maybeJDeps.isPresent()) {
            throw new MojoExecutionException("No 'jdeps' tool provider found");
        }
        return maybeJDeps.get();
    }

    public abstract void execute() throws MojoExecutionException, MojoFailureException;

    protected void addToolArgs(final ArrayList<String> args) {
        String verbose = this.verbose;
        if (verbose == null) verbose = "package";
        String filter = this.filter;
        if (filter == null) filter = "package";
        if (verbose.equals("summary")) {
            args.add("-summary");
        } else {
            args.add("-verbose:" + verbose);
        }
        args.add("-filter:" + filter);
        if (pattern != null) {
            args.add("--regex");
            args.add(pattern);
        } else if (packages != null && packages.length > 0) {
            for (String pkg : packages) {
                args.add("--package");
                args.add(pkg);
            }
        } else if (modules != null && modules.length > 0) {
            for (String module : modules) {
                args.add("--require");
                args.add(module);
            }
        }
        if (filterPattern != null) {
            args.add("-filter");
            args.add(filterPattern);
        }
        if (pattern != null) {
            args.add("-include");
            args.add(pattern);
        }
        if (multiRelease != null) {
            args.add("--multi-release");
            args.add(multiRelease);
        }
        if (ignoreMissing) {
            args.add("--ignore-missing-deps");
        }
        if (apiOnly) {
            args.add("--api-only");
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
    }

    static abstract class LineOutputStream extends OutputStream {
        byte[] buffer;
        int index;

        public void write(final int b) throws IOException {
            if (b == '\n') {
                // flush buffer
                String str = new String(buffer, 0, index, StandardCharsets.UTF_8);
                write(str);
                index = 0;
            } else if (b == '\r') {
                // ignore
            } else {
                if (buffer == null) {
                    buffer = new byte[160];
                } else if (index == buffer.length) {
                    buffer = Arrays.copyOf(buffer, (buffer.length << 1) + buffer.length >> 1);
                }
                buffer[index++] = (byte) b;
            }
        }

        public abstract void write(final String str) throws IOException;
    }

    static class LoggingOutputStream extends LineOutputStream {
        final Log log;

        LoggingOutputStream(final Log log) {
            this.log = log;
        }

        public void write(final String str) throws IOException {
            if (str.regionMatches(true, 0, "error: ", 0, 7)) {
                log.error(str.substring(7));
            } else if (str.regionMatches(true, 0, "warning: ", 0, 9)) {
                log.warn(str.substring(9));
            } else {
                passThru(str);
            }
        }

        public void passThru(final String str) throws IOException {
            log.error(str);
        }
    }

    static class PassThruLoggingOutputStream extends LoggingOutputStream {
        static final byte[] LINE_SEP = System.lineSeparator().getBytes(StandardCharsets.UTF_8);

        private final OutputStream passThru;
        private final boolean close;

        PassThruLoggingOutputStream(final Log log, final OutputStream passThru, final boolean close) {
            super(log);
            this.passThru = passThru;
            this.close = close;
        }

        public void passThru(final String str) throws IOException {
            passThru.write(buffer, 0, index);
            passThru.write(LINE_SEP);
        }

        public void close() throws IOException {
            if (close) passThru.close();
        }
    }
}
