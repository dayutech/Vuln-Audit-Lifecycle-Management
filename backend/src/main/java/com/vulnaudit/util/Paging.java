package com.vulnaudit.util;

/**
 * 分页参数收敛。
 *
 * <p>原先列表接口直接把外部传入的 page/size 交给 {@code PageRequest.of}，
 * {@code size=999999} 即可一次拉全表，存在明显的资源放大风险。
 * 此处统一收敛到安全区间。
 */
public final class Paging {

    /** 单页最大条数 */
    public static final int MAX_PAGE_SIZE = 500;

    private Paging() {
    }

    public static int page(int page) {
        return Math.max(page, 0);
    }

    public static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
