package org.brylex.bench;

import org.openjdk.jmh.Main;

public final class BenchmarkRunner {

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Main.main(new String[]{"org.brylex.bench"});
        } else {
            Main.main(args);
        }
    }
}
