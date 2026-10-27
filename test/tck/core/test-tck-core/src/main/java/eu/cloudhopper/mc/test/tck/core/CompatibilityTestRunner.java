package eu.cloudhopper.mc.test.tck.core;

/*-
 * #%L
 * test-tck-core - a library from the "Cloudhopper" project.
 * 
 * Dukehoff GmbH designates this particular file as subject to the "Classpath"
 * exception as provided in the LICENSE-EXCEPTIONS file that accompanies this code.
 * %%
 * Copyright (C) 2024 - 2026 Dukehoff GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
import eu.cloudhopper.mc.generator.api.GeneratorFeatureInfo;
import eu.cloudhopper.mc.test.tck.api.CompatibilityTest;
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TestContext;

import java.util.*;

public class CompatibilityTestRunner {

    public static void runWith(String generatorId, TestContext context) throws Exception {
        System.out.println("\uD83D\uDD0D Running TCK for generator: " + generatorId);

        GeneratorFeatureInfo info = GeneratorFeatureInfo.Loader.loadFeaturesFor(generatorId);
        if (info == null) {
            throw new IllegalStateException("Could not load generator features for: " + generatorId);
        }

        List<CompatibilityTest> availableTests = List.of(
                new PlainFunctionCompatibilityTest(),
                new DirectCallToAPIFunctionCompatibilityTest(),
                new MemoryFunctionCompatibilityTest(),
                new TimeoutFunctionCompatibilityTest(),
                new HttpFunctionCompatibilityTest(),
                new HttpGetPlayerCompatibilityTest(),
                new HttpRegisterPlayerCompatibilityTest(),
                new HttpUpdatePlayerCompatibilityTest(),
                new HttpDeletePlayerCompatibilityTest(),
                new HttpGetMatchCompatibilityTest(),
                new HttpSearchPlayersCompatibilityTest(),
                new ScheduledFunctionCompatibilityTest() // This should be last test to later give us a chance to delete the log group.
        // This way the log group will be deleted about 30s after the last call giving us a 30s window before it would be executed again.
        );

        List<CompatibilityTest> selectedTests = new ArrayList<>();
        Map<CompatibilityTest, List<RequiredFeature>> unsupportedTests = new HashMap<>();

        for (CompatibilityTest test : availableTests) {
            if (test instanceof FeatureAwareTest fat) {
                List<RequiredFeature> required = fat.requiredFeatures();
                if (supportsAllFeatures(required, info)) {
                    selectedTests.add(test);
                } else {
                    unsupportedTests.put(test, required);
                }
            } else {
                selectedTests.add(test); // fallback: always run
            }
        }

        List<TestResult> results = new ArrayList<>();

        // Best-effort pre-deploy cleanup: remove any leftovers a previous run may
        // have left behind (e.g. resources created before a mid-apply failure).
        // Only effective when the Terraform state survived; harmless otherwise.
        System.out.println("\n\uD83E\uDDF9 Pre-deploy cleanup (removing any leftovers from a previous run)...");
        try {
            context.cleanupTestFunctions();
        } catch (Exception preCleanup) {
            System.out.println("\u2139\uFE0F Nothing to pre-clean, or pre-cleanup failed (usually fine): " + preCleanup.getMessage());
        }

        try {
            System.out.println("\n\uD83D\uDE80 Deploying test functions...");
            context.deployTestFunctions();
            System.out.println("\u23F3 Waiting 20s for API Gateway to stabilize...");
            Thread.sleep(20000);

            for (CompatibilityTest test : selectedTests) {
                String name = test.getClass().getSimpleName();
                List<RequiredFeature> tested = test instanceof FeatureAwareTest fat ? fat.requiredFeatures() : List.of();
                System.out.println("\n\u25B6 Running test: " + name);
                try {
                    test.run(context);
                    System.out.println("\u2705 " + name + " PASSED");
                    results.add(new TestResult(name, true, null, tested));
                } catch (Throwable t) {
                    System.out.println("\u274C " + name + " FAILED");
                    t.printStackTrace();
                    results.add(new TestResult(name, false, t, tested));
                }
            }

            System.out.println("\u23F3 Waiting 20s for log streams to stabilize...");
            Thread.sleep(20000);
        } finally {
            // Always tear down, even if the deploy itself failed, so a failed
            // run does not leak cloud resources that block the next run.
            System.out.println("\n\uD83E\uDDF9 Cleaning up deployed functions...");
            try {
                context.cleanupTestFunctions();
            } catch (Exception cleanup) {
                System.err.println("\u26A0\uFE0F Cleanup failed - leftover resources may need to be removed manually: " + cleanup.getMessage());
                cleanup.printStackTrace();
            }
        }

        long passed = results.stream().filter(TestResult::passed).count();
        long failed = results.size() - passed;

        System.out.println("\n\uD83D\uDCCA Compatibility Summary:");
        System.out.printf("✅ Passed: %d\n", passed);
        System.out.printf("❌ Failed: %d\n", failed);
        System.out.printf("🔎 Skipped due to unsupported features: %d\n", unsupportedTests.size());

        if (!unsupportedTests.isEmpty()) {
            System.out.println("\n⏩ Skipped tests (unsupported features):");
            for (Map.Entry<CompatibilityTest, List<RequiredFeature>> entry : unsupportedTests.entrySet()) {
                System.out.println(" - " + entry.getKey().getClass().getSimpleName());
                for (RequiredFeature feat : entry.getValue()) {
                    System.out.println("    ↳ Requires: " + feat.annotationFqcn() + " → " + feat.requiredAttributes());
                }
            }
        }

        Map<String, Map<List<String>, List<String>>> featureTree = new HashMap<>();
        Map<String, Set<String>> failedTestsPerFeature = new HashMap<>();

        for (TestResult result : results) {
            for (RequiredFeature rf : result.testedFeatures()) {
                var featureKey = rf.annotationFqcn();
                var attrs = rf.requiredAttributes();

                if (result.passed()) {
                    featureTree
                            .computeIfAbsent(featureKey, k -> new HashMap<>())
                            .computeIfAbsent(attrs, k -> new ArrayList<>())
                            .add("✔ " + result.name());
                } else {
                    featureTree
                            .computeIfAbsent(featureKey, k -> new HashMap<>())
                            .computeIfAbsent(attrs, k -> new ArrayList<>())
                            .add("❌︎ " + result.name());
                }
            }
        }

        System.out.println("\n🧬 Feature Coverage:");
        for (var annotation : featureTree.entrySet()) {
            System.out.println("📌 " + annotation.getKey());
            for (var attrGroup : annotation.getValue().entrySet()) {
                System.out.println("  🔹 Attributes: " + attrGroup.getKey());
                for (String testName : attrGroup.getValue()) {
                    System.out.println("    " + testName); // testName includes ✔ or ✘ prefix
                }
            }
        }

        if (failed == 0) {
            System.out.println("\n\uD83C\uDF89 Certification PASSED — generator is compatible with all claimed features!");
        } else {
            System.out.println("\n❗ Certification FAILED — see above for failed tests.");
            // Fail the launcher (and with it the CI step) instead of only printing.
            throw new IllegalStateException(failed + " compatibility test(s) failed");
        }
    }

    private static boolean supportsAllFeatures(List<RequiredFeature> required, GeneratorFeatureInfo declared) {
        for (RequiredFeature rf : required) {
            boolean matched = declared.getFeatures().stream().anyMatch(supported
                    -> supported.getSupportedAnnotation().equals(rf.annotationFqcn())
                    && supported.getSupportedAttributes().containsAll(rf.requiredAttributes()));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    record TestResult(String name, boolean passed, Throwable error, List<RequiredFeature> testedFeatures) {

    }
}
