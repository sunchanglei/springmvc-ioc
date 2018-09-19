package com.scl.servlet;

import com.scl.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet容器
 */
public class DispatchServlet extends HttpServlet {
    /** 上线文的配置 */
    private Properties contextConfig = new Properties();
    /** 扫描的类名 */
    private List<String> classNames = new ArrayList<String>();
    /** IOC容器 */
    private Map<String,Object> ioc = new HashMap<String, Object>();
    /** 业务处理 */
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req,resp);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    }
    @Override
    public void init(ServletConfig config) throws ServletException {

        // 1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2、通过解析配置文件的内容、扫描出所以相关类
        doScanner(contextConfig.getProperty("scanPackage"));
        // 3、将所有扫描出来的类进行实例化
        doInstance();
        // 4、将实例化好的bean进行依赖注入
        doAutowired();
        // 5、初始化HandlerMapping
        initHandlerMapping();
    }

    private void doLoadConfig(String location){

        System.out.println("DispatchServlet doLoadConfig start...........");
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(location.replace("classpath:",""));

        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is){
                try {
                    is.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String packageName){

        System.out.println("DispatchServlet doScanner start...........");

        // File.separator 系统文件分隔符 windows是\ ; linux 是/ ;
        // 在使用replaceAll时，如果替换的字符中包含'\'或者'$'符号可能会导致意想不到的结果。因为替换时使用了正则表达式，而'\'和'$'是正则中的关键字，替换会造成混淆
        URL url = this.getClass().getClassLoader().getResource(File.separator+packageName.replaceAll("\\.",Matcher.quoteReplacement(File.separator)));

        File classDir = new File(url.getFile());

        for (File file : classDir.listFiles()) {
            if (file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            } else {
                classNames.add(packageName + "." + file.getName().replace(".class",""));
            }
        }
    }

    private void doInstance(){

        System.out.println("DispatchServlet doInstance start...........");

        if (classNames.isEmpty()){
            return;
        }

        for (String className : classNames){
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(SclController.class)){
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                } else if (clazz.isAnnotationPresent(SclService.class)){
                    SclService sclService = clazz.getAnnotation(SclService.class);
                    // 1、优先使用自己定义的bean
                    String beanName = sclService.value().trim();
                    if("".equals(beanName)){
                        // 2、默认beanName首字母小写
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    // 3、如注入为接口，需要把实现类赋值给它
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(),instance);
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * SpringMVC的核心
     */
    private void initHandlerMapping() {

        System.out.println("DispatchServlet initHandlerMapping start...........");

        if(ioc.isEmpty()){ return;}

        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(SclController.class)){ continue; }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(SclRequestMapping.class)){
                baseUrl = clazz.getAnnotation(SclRequestMapping.class).value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods){
                if (!method.isAnnotationPresent(SclRequestMapping.class)){ continue; }
                SclRequestMapping sclRequestMapping = method.getAnnotation(SclRequestMapping.class);
                String url = "/"+baseUrl+"/"+sclRequestMapping.value().replaceAll("\\+","/");
                Pattern pattern = Pattern.compile(url);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("Mapping:"+url+" : "+method);
            }
        }
    }

    /**
     * 依赖注入 ：对属性进行赋值。
     */
    private void doAutowired() {

        System.out.println("DispatchServlet doAutowired start...........");

        if(ioc.isEmpty()){ return; }

        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            Field[] fields =entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(SclAutowired.class)){
                    continue;
                }

                SclAutowired sclAutowired = field.getAnnotation(SclAutowired.class);
                String beanName = sclAutowired.value().trim();
                if ("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                try { // 第一参数 实参；第二参 实例
                    field.setAccessible(true);//
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doDispatch(HttpServletRequest request,HttpServletResponse response){

        try {
            Handler handler = getHandler(request);
            if(handler == null){
                response.getWriter().write("404 not found");
                return;
            }
            Class<?>[] paramTypes = handler.method.getParameterTypes();
            Object[] paramValues = new Object[paramTypes.length];
            Map<String,String[]> paramsMap = request.getParameterMap();
            for (Map.Entry<String,String[]> param : paramsMap.entrySet()){
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","");
                if(!handler.paramIndexMapping.containsKey(param.getKey())){
                    continue;
                }
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index],value);
            }
            // 设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = request;
            int resIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[resIndex] = response;
            handler.method.invoke(handler.controller,paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 记录controller中RequestMapping和metod的关系
     */
    private class Handler {
        /** 保存方法对应的实例 */
        protected Object controller;
        protected Method method;
        protected Pattern pattern;
        /** 参数顺序 */
        protected Map<String,Integer> paramIndexMapping;

        protected Handler(Pattern pattern,Object controller,Method method){
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            this.paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method){

            // 提取方法中注解的参数
            Annotation[][] paramAnns = method.getParameterAnnotations();
            for (int i=0;i < paramAnns.length; i++){
                for (Annotation an : paramAnns[i]) {
                    if(an instanceof SclRequestParam){
                        String paramName = ((SclRequestParam)an).value().trim();
                        if(!"".equals(paramName)){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }
            // 提取方法中request和response中的参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i=0; i < paramsTypes.length; i++){
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }
    }

    private Handler getHandler(HttpServletRequest request){
        if(handlerMapping.isEmpty()){ return null;}
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath,"").replaceAll("\\+","");

        for(Handler handler : handlerMapping){
            try {
                Matcher matcher = handler.pattern.matcher(url);
                //如果没有匹配上进行下一匹配
                if(!matcher.matches()){
                    continue;
                }

                return handler;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }



    /**
     * 首字母小写
     * @param str
     * @return
     */
    private String lowerFirstCase(String str){

        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private Object convert(Class<?> type ,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }
}
