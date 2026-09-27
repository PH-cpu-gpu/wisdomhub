package com.wisdomhub.common.feign.config;

import feign.RetryableException;
import feign.Retryer;

// 不能直接配置为Bean, 否则连LLM调用也会重试
public class OnceRetryer implements Retryer {

    private final Retryer delegate = new Retryer.Default(200, 500, 2);

    @Override
    public void continueOrPropagate(RetryableException e) {
        delegate.continueOrPropagate(e);
    }

    @Override
    public Retryer clone() {
        return new OnceRetryer();
    }
}
