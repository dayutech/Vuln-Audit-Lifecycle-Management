package com.vulnaudit;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTest {
    @Test
    public void test() {
        System.out.println("BCRYPT HASH: " + new BCryptPasswordEncoder().encode("Admin@123"));
    }
}
