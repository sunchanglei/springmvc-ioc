package com.scl.controller;

import com.scl.annotation.SclAutowired;
import com.scl.annotation.SclController;
import com.scl.annotation.SclRequestMapping;
import com.scl.annotation.SclRequestParam;
import com.scl.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * spring作用在类上的注解有@Component、@Responsity、@Service以及@Controller；
 * 而@Autowired和@Resource是用来修饰字段、构造函数或者设置方法，并做注入的;
 */
@SclController
@SclRequestMapping("demo")
public class DemoController {

    @SclAutowired
    private IDemoService demoService;

    @SclRequestMapping("query")
    public String query (HttpServletRequest request, HttpServletResponse response, @SclRequestParam("name") String name){

        try {
            response.getWriter().write(demoService.query(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
