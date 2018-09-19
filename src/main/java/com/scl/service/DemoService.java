package com.scl.service;


import com.scl.annotation.SclService;

@SclService
public class DemoService implements IDemoService {

    public String query(String name) {

        return "hello "+name;
    }
}
