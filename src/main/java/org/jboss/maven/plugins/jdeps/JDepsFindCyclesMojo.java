package org.jboss.maven.plugins.jdeps;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 *
 */
@Mojo(
    name = "jdeps-find-cycles",
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class JDepsFindCyclesMojo extends AbstractJDepsMojo {
    public void execute() throws MojoExecutionException, MojoFailureException {
        final ToolProvider tp = getJDepsToolProvider();
        final ArrayList<String> args = new ArrayList<>();
        addToolArgs(args);
        final PrintStream errorStream = new PrintStream(new LoggingOutputStream(getLog()));
        AnalysisStream as = new AnalysisStream(getLog());
        final PrintStream outputStream = new PrintStream(as);
        int result = tp.run(
            outputStream,
            errorStream,
            args.toArray(NO_STRINGS)
        );
        if (result != 0) {
            throw new MojoFailureException("Dependency analysis failed");
        }
        // now search for cycles
        HashMap<String, Set<String>> dependencies = as.getDependencies();
        HashSet<String> noCycles = new HashSet<>();
        HashSet<String> visited = new LinkedHashSet<>();
        HashSet<HashSet<String>> cycleSets = new HashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        int cnt = 0;
        for (String src : dependencies.keySet()) {
            cnt += findCycles(src, noCycles, cycleSets, visited, path, dependencies);
        }
        getLog().info("Found " + cnt + " unique cyclic path(s)");
    }

    private int findCycles(final String src, final HashSet<String> noCycles, final HashSet<HashSet<String>> cycleSets, final HashSet<String> visited, final ArrayDeque<String> path, final HashMap<String, Set<String>> dependencies) {
        if (noCycles.contains(src)) {
            return 0;
        }
        getLog().debug("Searching for cycles from " + src);
        if (! visited.add(src)) {
            if (path.peekLast().equals(src)) {
                return 0;
            }
            StringBuilder b = new StringBuilder();
            b.append("Cycle path:\n\t");
            Iterator<String> it = path.iterator();
            HashSet<String> cycleSet = new HashSet<>();
            // search for this item
            String item;
            do {
                item = it.next();
            } while (! src.equals(item));
            b.append(item).append(" ->\n\t");
            cycleSet.add(item);
            while (it.hasNext()) {
                item = it.next();
                b.append(item).append(" ->\n\t");
                cycleSet.add(item);
            }
            b.append(src);
            if (cycleSets.add(cycleSet)) {
                getLog().info(b);
                return 1;
            } else {
                // duplicate cycle
                return 0;
            }
        } else try {
            path.addLast(src);
            int res = 0;
            for (String dst : dependencies.getOrDefault(src, Collections.emptySet())) {
                getLog().debug("Searching for cycles from " + src + " -> " + dst);
                res += findCycles(dst, noCycles, cycleSets, visited, path, dependencies);
            }
            if (res == 0) noCycles.add(src);
            return res;
        } finally {
            path.removeLast();
            visited.remove(src);
        }
    }

    static class AnalysisStream extends LoggingOutputStream {
        private static final Pattern pattern = Pattern.compile("^\\s+([^\\s]+)\\s+->\\s+([^\\s]+)\\s+([^\\s]+)$");

        private final HashMap<String, Set<String>> dependencies = new HashMap<>();

        public AnalysisStream(final Log log) {
            super(log);
        }

        public void write(final String str) {
            Matcher matcher = pattern.matcher(str);
            if (matcher.matches()) {
                String src = matcher.group(1);
                String dst = matcher.group(2);
                dependencies.computeIfAbsent(src, s -> new HashSet<>()).add(dst);
                log.debug("Registered " + src + " -> " + dst);
            }
        }

        public HashMap<String, Set<String>> getDependencies() {
            return dependencies;
        }
    }
}
