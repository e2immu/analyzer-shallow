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

import java.nio.Buffer;
import java.nio.CharBuffer;

public class JavaNio {

    final static String PACKAGE_NAME = "java.nio";

    /*
     Note: is an abstract class
     */
    @Container
    interface Buffer$ {

        @Fluent
        Buffer reset();

        @Fluent
        Buffer flip();

        @Fluent
        Buffer limit(int limit);

        @Fluent
        Buffer mark();

        @Fluent
        Buffer rewind();

        @Fluent
        Buffer clear();

        /*
         @Dependent!!
         */
        @NotModified
        Buffer slice();
    }

    /*
     Concrete implementation of Buffer, not @Container, cannot be @Independent anymore since Buffer is not
     */
    interface CharBuffer$ {

        @Fluent
        @Modified
        CharBuffer get(@Modified char[] dst);

        @Independent(absent = true)
        char[] array();
    }


    interface ByteBuffer$ {
        @Independent(absent = true) // dependent!
        byte[] array();
    }
}
