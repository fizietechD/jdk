/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.evilprovider;

import java.security.*;
import java.security.spec.*;
import java.nio.ByteBuffer;

import javax.crypto.*;

public final class EvilHmacSHA1 extends MacSpi {
    private final Mac internalMac;

    public EvilHmacSHA1() throws GeneralSecurityException {
        internalMac = Mac.getInstance("HmacSHA1",
                System.getProperty("test.provider.name", "SunJCE"));
    }

    @Override
    protected byte[] engineDoFinal() {
        return internalMac.doFinal();
    }

    @Override
    protected int engineGetMacLength() {
        return internalMac.getMacLength();
    }

    @Override
    protected void engineInit(Key key, AlgorithmParameterSpec spec)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        SecretKey sKey;
        if (key instanceof SecretKey) {
            sKey = (SecretKey)key;
        } else {
            throw new IllegalArgumentException("Key must be a SecretKey");
        }

        byte[] sKeyEnc = sKey.getEncoded();
        int keyBits = sKeyEnc.length * 8;
        if (keyBits < 160) {
            throw new IllegalArgumentException("Key must be at least 160 bits");
        }

        // Pass through to init
        internalMac.init(key, spec);
    }

    @Override
    protected void engineReset() {
        internalMac.reset();
    }

    @Override
    protected void engineUpdate(byte input) {
        internalMac.update(input);
    }

    @Override
    protected void engineUpdate(byte[] input, int offset, int len) {
        internalMac.update(input, offset, len);
    }

    @Override
    protected void engineUpdate(ByteBuffer input) {
        internalMac.update(input);
    }
}
