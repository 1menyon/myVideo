package org.luckyjourney.authority;

import javax.servlet.http.HttpServletRequest;

/**
 * Author: menyon
 * CreateTime: 2022-06-13 12:44
 */
public class DefaultAuthorityVerify implements AuthorityVerify{


    @Override
    public Boolean authorityVerify(HttpServletRequest request,String[] permissions) {
        return true;
    }
}
