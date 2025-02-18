/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyzer.shallow.aapi.java;

import org.e2immu.annotation.*;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.security.*;

public class JavaSecurity {
    final static String PACKAGE_NAME = "java.security";

    @Independent(hc = true)
    interface PrivilegedAction$<T> {
    }

    @ImmutableContainer
    @Independent
    interface Guard$ {
        // non-modifying, throws security exception
        void checkGuard(Object object);
    }

    @Independent
    class MessageDigest$ {
        static MessageDigest getInstance(String algorithm) { return null; }
        static MessageDigest getInstance(String algorithm, String provider) { return null; }
        static MessageDigest getInstance(String algorithm, @NotModified Provider provider) { return null; }
        @NotModified
        Provider getProvider() { return null; }
        void update(byte input) { }
        void update(@NotModified byte[] input, int offset, int len) { }
        void update(@NotModified byte[] input) { }
        void update(@Modified ByteBuffer input) { }
        byte[] digest() { return null; }
        int digest(@NotModified byte[] buf, int offset, int len) { return 0; }
        byte[] digest(@NotModified byte[] input) { return null; }
        static boolean isEqual(@NotModified byte[] digesta, @NotModified byte[] digestb) { return false; }
        void reset() { }
        @NotModified
        String getAlgorithm() { return null; }
        @NotModified
        int getDigestLength() { return 0; }
    }

    @Immutable(hc = true)
    interface SecureRandomParameters$ {}

    @Independent
    class SecureRandom$ {
        SecureRandom$() { }
        SecureRandom$(@NotModified byte[] seed) { }
        static SecureRandom getInstance(String algorithm) { return null; }
        static SecureRandom getInstance(String algorithm, String provider) { return null; }
        static SecureRandom getInstance(String algorithm, Provider provider) { return null; }
        static SecureRandom getInstance(String algorithm, SecureRandomParameters params) { return null; }

        static SecureRandom getInstance(String algorithm, SecureRandomParameters params, String provider) {
            return null;
        }

        static SecureRandom getInstance(String algorithm, SecureRandomParameters params, Provider provider) {
            return null;
        }

        @NotModified
        Provider getProvider() { return null; }
        @NotModified
        String getAlgorithm() { return null; }
        @NotModified
        SecureRandomParameters getParameters() { return null; }
        void setSeed(@NotModified byte[] seed) { }
        void setSeed(long seed) { }
        void nextBytes(byte[] bytes) { }
        void nextBytes(byte[] bytes, SecureRandomParameters params) { }
        static byte[] getSeed(int numBytes) { return null; }
        @NotModified
        byte[] generateSeed(int numBytes) { return null; }
        static SecureRandom getInstanceStrong() { return null; }
        void reseed() { }
        void reseed(SecureRandomParameters params) { }
    }

    static class AccessController$ {
        static <T> T doPrivileged(@Modified PrivilegedAction<T> action) { return null; }
        static <T> T doPrivileged(@Modified PrivilegedExceptionAction<T> action) { return null; }
    }
}
