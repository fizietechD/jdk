/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7092744
 * @summary Tests for ambiguous methods
 * @run main/othervm Test7092744
 * @author Sergey Malenkov
 */

public class Test7092744 extends AbstractTest {

    public static void main(String[] args) {
        new Test7092744().test();
    }

    protected Object getObject() {
        return new Bean();
    }

    protected Object getAnotherObject() {
        Bean bean = new Bean();
        bean.setValue(99);
        return bean;
    }

    public static interface I<T extends Number> {

        T getValue();

        void setValue(T value);
    }

    public static class Bean implements I<Integer> {

        private Integer value;

        public Integer getValue() {
            return this.value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }
    }
}
