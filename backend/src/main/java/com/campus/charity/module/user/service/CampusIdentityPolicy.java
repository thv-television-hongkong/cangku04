package com.campus.charity.module.user.service;
import com.campus.charity.module.user.entity.SysUser;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
/** 展示和下单权限共用此策略；测试认证不等同于学校实名认证。 */
@Component @Getter
public class CampusIdentityPolicy {
    @Value("${campus.identity.test-mode:false}") private boolean testMode;
    public int effectiveStatus(Integer stored, boolean testOnly) {
        return testOnly && !testMode ? 0 : stored == null ? 0 : stored;
    }
    public int effectiveStatus(SysUser user) {
        return effectiveStatus(user.getAuthStatus(), Integer.valueOf(1).equals(user.getIdentityTestOnly()));
    }
}
