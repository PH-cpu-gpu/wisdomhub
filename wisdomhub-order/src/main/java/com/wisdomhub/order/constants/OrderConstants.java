package com.wisdomhub.order.constants;

/** 订单、商品、支付方式与流水状态。数值与 26-order.sql 的注释一一对应 */
public final class OrderConstants {

    private OrderConstants() {
    }

    /** 订单状态：0 待支付 1 已支付 2 已取消 3 退款中 4 已退款 */
    public static final int PENDING = 0;
    public static final int PAID = 1;
    public static final int CANCELED = 2;
    public static final int REFUNDING = 3;
    public static final int REFUNDED = 4;

    /** 商品类型：1 专栏 */
    public static final int ITEM_COLUMN = 1;

    /** 支付方式：1 模拟支付 2 微信支付 3 支付宝支付。*/
    public static final int PAY_TYPE_MOCK = 1;
    public static final int PAY_TYPE_WECHAT = 2;
    public static final int PAY_TYPE_ALIPAY = 3;

    /** 支付流水状态：0 待支付 1 成功 2 失败 3 已关闭 */
    public static final int PAYMENT_PENDING = 0;
    public static final int PAYMENT_SUCCESS = 1;
    public static final int PAYMENT_FAILED = 2;
    public static final int PAYMENT_CLOSED = 3;

    /** 退款状态：0 无 1 处理中 2 成功 3 失败 */
    public static final int REFUND_NONE = 0;
}
