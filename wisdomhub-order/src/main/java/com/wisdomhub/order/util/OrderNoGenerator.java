package com.wisdomhub.order.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** 给用户看的订单号 / 支付单号：时间 + 6 位随机，不要用雪花那 19 位无意义长串 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int RANDOM_BOUND = 1_000_000;

    private OrderNoGenerator() {
    }

    public static String nextOrderNo() {
        return next("");
    }

    public static String nextPaymentNo() {
        return next("P");
    }

    public static String nextRefundNo() {
        return next("R");
    }

    private static String next(String prefix) {
        String time = LocalDateTime.now(ZoneOffset.UTC).format(FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(RANDOM_BOUND);
        return prefix + time + String.format(Locale.ROOT, "%06d", random);
    }
}
