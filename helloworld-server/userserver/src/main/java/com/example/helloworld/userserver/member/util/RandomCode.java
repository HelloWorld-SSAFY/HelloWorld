package com.example.helloworld.userserver.member.util;

import java.security.SecureRandom;

public final class RandomCode {
    private static final SecureRandom R = new SecureRandom();
    // 혼동 문자 제거: I, L, O, 0, 1
    private static final char[] ALPH = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private RandomCode() {}

    public static String base32(int len) {
        char[] out = new char[len];
        for (int i=0;i<len;i++) out[i] = ALPH[R.nextInt(ALPH.length)];
        return new String(out);
    }
}
