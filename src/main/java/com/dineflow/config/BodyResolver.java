package com.dineflow.config;

import com.dineflow.common.ApiException;
import com.dineflow.common.Body;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.*;

/** Đọc tham số @Body Map&lt;String, Object&gt; từ JSON hoặc form. */
@Component
@RequiredArgsConstructor
public class BodyResolver implements HandlerMethodArgumentResolver {
    private final ObjectMapper mapper;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Body.class) && Map.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        String type = req.getContentType() == null ? "" : req.getContentType().toLowerCase();
        if (type.contains("json")) {
            byte[] raw = req.getInputStream().readAllBytes();
            if (raw.length == 0) return new LinkedHashMap<String, Object>();
            try {
                Map<String, Object> m = mapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                return m == null ? new LinkedHashMap<String, Object>() : m;
            } catch (Exception e) {
                throw new ApiException(400, "Dữ liệu gửi lên không phải JSON hợp lệ.");
            }
        }
        // Form: key=value, key[]=a&key[]=b → danh sách
        Map<String, Object> m = new LinkedHashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (k.endsWith("[]")) m.put(k.substring(0, k.length() - 2), new ArrayList<>(Arrays.asList(v)));
            else m.put(k, v.length == 1 ? v[0] : new ArrayList<>(Arrays.asList(v)));
        });
        return m;
    }
}
