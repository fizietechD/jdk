/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8337506 8349254
 * @summary Verify command line arguments, including ones from
 *          "JDK_JAVA_OPTIONS" environment variables are not mapped
 *          with "best-fit" mappings on Windows
 * @requires (os.family == "windows")
 * @library /test/lib
 * @run junit DisableBestFitMappingTest
 */
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.stream.Stream;

import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

public class DisableBestFitMappingTest {
    private static final CharsetEncoder NATIVE_ENC =
            Charset.forName(System.getProperty("native.encoding")).newEncoder();
    private static final String REPLACEMENT =
            NATIVE_ENC.charset().decode(ByteBuffer.wrap(NATIVE_ENC.replacement())).toString();
    private static final String TEST_ENV_VAR = "JDK_JAVA_OPTIONS";
    private static final String TEST_PROP_KEY = "testProp";
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = -1;

    static Stream<Arguments> TEST_ARGS() {
        return Stream.of(
                Arguments.of("aa\uff02 \uff02bb", "aa" + REPLACEMENT + " " + REPLACEMENT + "bb"),
                Arguments.of("aa\uff01bb", "aa" + REPLACEMENT + "bb"),
                Arguments.of("aa\u221ebb", "aa" + REPLACEMENT + "bb")
        );
    }

    @ParameterizedTest
    @MethodSource("TEST_ARGS")
    void testCommandLineArgument(String arg, String expected) throws Exception {
        // Only execute if the arg cannot be encoded
        assumeFalse(NATIVE_ENC.canEncode(arg),
                "native.encoding (%s) can encode the argument '%s'. Test ignored."
                        .formatted(NATIVE_ENC.charset(), arg));

        var result = ProcessTools.executeTestJava(
                DisableBestFitMappingTest.class.getSimpleName(), expected, arg);
        result.asLines().forEach(System.out::println);
        assertEquals(EXIT_SUCCESS, result.getExitValue(),
                "Command line argument mapping failed");
    }

    @ParameterizedTest
    @MethodSource("TEST_ARGS")
    void testEnvironmentVariable(String propVal, String expected) throws Exception {
        // Only execute if the arg from the environment variable cannot be encoded
        assumeFalse(NATIVE_ENC.canEncode(propVal),
                "native.encoding (%s) can encode the argument '%s'. Test ignored."
                        .formatted(NATIVE_ENC.charset(), propVal));

        var pb = ProcessTools.createTestJavaProcessBuilder(
                DisableBestFitMappingTest.class.getSimpleName(), expected);
        pb.environment().put(TEST_ENV_VAR, "-D" + TEST_PROP_KEY + "=\"" + propVal + "\"");
        var result = ProcessTools.executeProcess(pb);
        result.asLines().forEach(System.out::println);
        assertEquals(EXIT_SUCCESS, result.getExitValue(),
                "Argument from JDK_JAVA_OPTIONS mapping failed");
    }

    public static void main(String... args) {
        var expected = args[0];
        var actual = args.length > 1 ? args[1] : System.getProperty(TEST_PROP_KEY);
        System.out.printf("expected: %s, actual: %s%n", expected, actual);
        System.exit(expected.equals(actual) ? EXIT_SUCCESS : EXIT_FAILURE);
    }
}
