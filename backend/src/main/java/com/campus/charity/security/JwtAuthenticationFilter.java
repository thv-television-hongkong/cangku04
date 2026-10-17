package com.campus.charity.security;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 认证过滤器：解析 Token 并写入 Spring Security 上下文，实现无状态认证。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final com.campus.charity.module.user.mapper.SysUserMapper users;
    private final com.campus.charity.module.user.service.CampusIdentityPolicy identities;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtProperties jwtProperties, com.campus.charity.module.user.mapper.SysUserMapper users, com.campus.charity.module.user.service.CampusIdentityPolicy identities) {
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.users=users;
        this.identities=identities;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(jwtProperties.getHeader());
        String token = jwtUtil.resolveToken(header);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtUtil.parseToken(token);
            if (claims != null) {
                LoginUser loginUser = jwtUtil.toLoginUser(claims);
                com.campus.charity.module.user.entity.SysUser current=users.selectById(loginUser.getUserId());
                if(current==null || current.getStatus()==null || current.getStatus()==0) {filterChain.doFilter(request,response);return;}
                // 冻结和角色调整立即生效，不能仅信任旧 JWT 中的权限。
                loginUser.setRole(current.getRole());loginUser.setStatus(current.getStatus());loginUser.setAuthStatus(identities.effectiveStatus(current));loginUser.setIdentityNo(current.getIdentityNo());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
